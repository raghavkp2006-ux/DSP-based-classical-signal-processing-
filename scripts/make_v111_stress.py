from pathlib import Path
import csv
import sys
import numpy as np
import soundfile as sf
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from make_ablation_testset import deterministic_noise

R=Path(__file__).resolve().parents[1]; OUT=R/'ablation_output'/'v111_stress'/'inputs'
OUT.mkdir(parents=True,exist_ok=True)
SR=16000; kinds=('white','brown_hum','tonal','bursty'); rows=[]
clean_files=sorted((R/'ablation_testset'/'clean').glob('*.wav'))

def active_tail(x,n):
    frame=round(.025*SR);hop=round(.010*SR)
    candidates=[]
    for st in range(0,max(1,len(x)-frame+1),hop):
        f=x[st:st+frame]
        if len(f)==frame and np.mean(f*f)>1e-4: candidates.append(f)
    if not candidates: raise RuntimeError('No speech-active frames')
    rng=np.random.default_rng(9173+len(x)); out=[]
    while sum(map(len,out))<n: out.append(candidates[int(rng.integers(len(candidates)))])
    y=np.concatenate(out)[:n]
    return y

for si,path in enumerate(clean_files):
    src,sr=sf.read(path,dtype='float32')
    src=np.asarray(src).reshape(-1)
    if sr!=SR: raise RuntimeError(f'unexpected sample rate: {sr}')
    base=np.tile(src,2)
    tail=active_tail(src,3*SR)
    continuous=np.concatenate([src,tail]).astype(np.float32)
    for scenario in ('step_up_4x_power','noise_last_2s','continuous_speech_final_3s'):
      speech=continuous if scenario.endswith('3s') else base
      for ni,kind in enumerate(kinds):
        raw=deterministic_noise(ni,len(speech),8100+si*100+ni).astype(np.float64)
        target=np.mean(speech.astype(np.float64)**2)
        noise=raw*np.sqrt(target/(np.mean(raw*raw)+1e-20))
        if scenario=='step_up_4x_power': noise[len(noise)//2:]*=2.0
        elif scenario=='noise_last_2s': noise[:-2*SR]=0
        name=f'{scenario}_{path.stem}_{kind}'
        clean_path=OUT/f'{name}_clean.wav'; noise_path=OUT/f'{name}_noise.wav'; noisy_path=OUT/f'{name}_noisy.wav'
        sf.write(clean_path,speech,SR,subtype='FLOAT');sf.write(noise_path,noise.astype(np.float32),SR,subtype='FLOAT');sf.write(noisy_path,(speech+noise).astype(np.float32),SR,subtype='FLOAT')
        rows.append({'name':name,'scenario':scenario,'source':path.name,'noise_type':kind,'clean':clean_path.name,'noise':noise_path.name,'noisy':noisy_path.name,'sample_rate':SR})
with (OUT/'metadata.csv').open('w',newline='',encoding='utf-8') as f:
    w=csv.DictWriter(f,fieldnames=rows[0].keys());w.writeheader();w.writerows(rows)
print(f'generated={len(rows)} scenarios=3 sources={len(clean_files)} noise_types={len(kinds)}')

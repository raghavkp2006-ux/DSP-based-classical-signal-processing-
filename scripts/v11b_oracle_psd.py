from pathlib import Path
import sys,csv,numpy as np,soundfile as sf
from scipy.signal import lfilter
R=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(R));from make_ablation_testset import deterministic_noise,read_clean
O=R/'ablation_output';D=R/'ablation_testset';dest=O/'v11b_oracle_psd';dest.mkdir(parents=True,exist_ok=True)
with (D/'metadata.csv').open(newline='',encoding='utf8') as f:metadata=list(csv.DictReader(f))
with (O/'stftfix_original/kotlin/kotlin_modes.csv').open(newline='',encoding='utf8') as f:modes=list(csv.DictReader(f))
summary=[]
for item,mode in zip(metadata,modes):
 if mode['mode']!='Classical DSP':continue
 file=item['noisy_path'].replace('\\','/').split('/')[-1];idx=int(item['file_id'].split('_')[1]);kind=('white','brown_hum','tonal','bursty').index(item['noise_type']);noisy,sr=sf.read(D/'noisy'/file);clean=read_clean(Path(item['source_clean_file']),sr,3.0)
 noise=deterministic_noise(kind,len(clean),3011448+idx*100+kind); scaled=noise*np.sqrt(np.mean(clean**2)/(np.mean(noise**2)+1e-12))/(10**(float(item['snr_db'])/20));preclip=clean+scaled
 recon=np.floor(np.clip(preclip,-1,1)*32768).clip(-32768,32767)/32768
 mismatch=float(np.max(np.abs(noisy-recon)));peak=float(np.max(np.abs(noisy)));n=lfilter([1,-.97],[1],scaled/peak);win=np.hanning(401)[:-1];starts=np.arange(0,len(n)-400+1,160)
 power=np.mean([abs(np.fft.rfft(n[i:i+400]*win,1024))**2 for i in starts],axis=0);psd=np.convolve(power,np.ones(5)/5,mode='same').astype(np.float32);np.savetxt(dest/(file+'.csv'),psd,fmt='%.9g')
 summary.append(dict(file=file,reconstruction_maxabs=mismatch,clipped_fraction=np.mean(np.abs(preclip)>1),true_psd_mean=np.mean(psd),frames=len(starts)))
 if mismatch>1/32768+1e-7:raise RuntimeError(f'Unable to reconstruct true noise {file}: {mismatch}')
print('true PSD files',len(summary),'max mix reconstruction',max(x['reconstruction_maxabs'] for x in summary),'clipped fractions',max(x['clipped_fraction'] for x in summary))
with (O/'v11b_oracle_reconstruction.csv').open('w',newline='',encoding='utf8') as f:w=csv.DictWriter(f,fieldnames=summary[0]);w.writeheader();w.writerows(summary)

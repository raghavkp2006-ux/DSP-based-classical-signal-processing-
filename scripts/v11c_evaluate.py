from pathlib import Path
import csv, sys
import numpy as np, soundfile as sf
from scipy.signal import lfilter
from pystoi import stoi
R=Path(__file__).resolve().parents[1]; O=R/'ablation_output'
sys.path.insert(0,str(R))
from make_ablation_testset import deterministic_noise, read_clean
LABEL=sys.argv[1] if len(sys.argv)>1 else 'baseline'
def read(p):
 x,sr=sf.read(str(p),dtype='float64');return (x.mean(axis=1) if x.ndim>1 else x),sr
def psd(x):
 z=lfilter([1,-.97],[1],x);win=np.hanning(401)[:-1];starts=np.arange(0,len(z)-400+1,160)
 p=np.mean([np.abs(np.fft.rfft(z[i:i+400]*win,1024))**2 for i in starts],axis=0)
 return np.convolve(p,np.ones(5)/5,mode='same')
for ds in ('original','padded'):
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded')
 meta=list(csv.DictReader((data/'metadata.csv').open(encoding='utf-8-sig')))
 modes=list(csv.DictReader((O/f'stftfix_{ds}'/'kotlin'/'kotlin_modes.csv').open(encoding='utf-8-sig')))
 mode_by_key={r['file_id']+'_'+r['noise_type']+'_'+({'-5':'-05dB','0':'+00dB','5':'+05dB','10':'+10dB','15':'+15dB'}[str(int(float(r['snr_db'])))]):r for r in modes}
 rows=[]
 for m in meta:
  key=m['file_id']+'_'+m['noise_type']+'_'+({'-5':'-05dB','0':'+00dB','5':'+05dB','10':'+10dB','15':'+15dB'}[str(int(float(m['snr_db'])))] )
  if mode_by_key[key]['mode']!='Classical DSP': continue
  name=Path(m['noisy_path']).name; clean,sr=read(data/'clean'/(m['file_id']+'.wav')); noisy,_=read(data/'noisy'/name)
  enhanced_path=O/'v11c_configs'/ds/'C3'/name if LABEL=='c3' else O/'v11b_factorial'/ds/'both_on'/name
  psd_path=O/'v11c_configs'/ds/'C3_psd'/(name+'.csv') if LABEL=='c3' else O/'v11b_factorial'/ds/'psd'/(name+'.csv')
  enhanced,_=read(enhanced_path)
  est=np.loadtxt(psd_path)
  source_index=int(m['file_id'].split('_')[1]); noise_index=('white','brown_hum','tonal','bursty').index(m['noise_type'])
  active=read_clean(Path(m['source_clean_file']),sr,3.0)
  active_power=np.mean(active**2)
  oracle_clean=np.pad(active,(round(3*sr),round(3*sr))) if ds=='padded' else active
  raw_noise=deterministic_noise(noise_index,len(oracle_clean),3011448+source_index*100+noise_index)
  raw_noise=raw_noise*np.sqrt(active_power/(np.mean(raw_noise**2)+1e-12))/(10**(int(float(m['snr_db']))/20))
  true=psd(raw_noise/np.max(np.abs(noisy)))
  ratio=float(np.mean(est/(true+1e-20)))
  sn, en=stoi(clean,noisy,sr),stoi(clean,noisy,sr,extended=True)
  so, eo=stoi(clean,enhanced,sr),stoi(clean,enhanced,sr,extended=True)
  rows.append({'dataset':ds,'file':name,'noise_type':m['noise_type'],'snr_db':int(float(m['snr_db'])),'noisy_stoi':sn,'output_stoi':so,'stoi_delta':so-sn,'noisy_estoi':en,'output_estoi':eo,'estoi_delta':eo-en,'psd_oracle_ratio':ratio,'mean_estimated_psd':float(np.mean(est)),'mean_oracle_psd':float(np.mean(true))})
 out=O/f'v11c_{LABEL}_{ds}.csv'
 with out.open('w',newline='',encoding='utf-8') as f:w=csv.DictWriter(f,fieldnames=rows[0].keys());w.writeheader();w.writerows(rows)
 print(ds,'n',len(rows),'noisy/output STOI',np.mean([r['noisy_stoi'] for r in rows]),np.mean([r['output_stoi'] for r in rows]),'ESTOI',np.mean([r['noisy_estoi'] for r in rows]),np.mean([r['output_estoi'] for r in rows]),'ratio median/mean',np.median([r['psd_oracle_ratio'] for r in rows]),np.mean([r['psd_oracle_ratio'] for r in rows]))

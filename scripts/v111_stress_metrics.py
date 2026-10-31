from pathlib import Path
import csv
import numpy as np
import soundfile as sf
from pystoi import stoi

R=Path(__file__).resolve().parents[1]; inp=R/'ablation_output/v111_stress/inputs'; exp=R/'ablation_output/v111_stress/exports'
meta=list(csv.DictReader((inp/'metadata.csv').open(encoding='utf-8-sig')))
psdrows={}
for r in csv.DictReader((exp/'psd.csv').open(encoding='utf-8-sig')):
 psdrows[(r['name'],r['config'])]=np.fromstring(r['values'],sep=';')

def oracle_timeline(noise):
 sr=16000; fl=400; hop=160; nfft=1024; win=.5-.5*np.cos(2*np.pi*np.arange(fl)/fl)
 frames=[]
 for st in range(0,len(noise)-fl+1,hop):
  frames.append(np.abs(np.fft.rfft(np.pad(noise[st:st+fl]*win,(0,nfft-fl))))**2)
 return np.asarray(frames)

def nonspeech_attenuation(clean,noisy,out):
 frame=400;hop=160; vals=[]
 for st in range(0,len(clean)-frame+1,hop):
  if np.mean(clean[st:st+frame]**2)<10**(-45/10):
   vals.append(10*np.log10((np.mean(out[st:st+frame]**2)+1e-20)/(np.mean(noisy[st:st+frame]**2)+1e-20)))
 return float(np.median(vals)) if vals else float('nan')

rows=[]
for m in meta:
 clean,_=sf.read(inp/m['clean'],dtype='float64');noise,_=sf.read(inp/m['noise'],dtype='float64');noisy,_=sf.read(inp/m['noisy'],dtype='float64')
 noise_pre=noise.copy();noise_pre[1:]-=.97*noise[:-1]
 oracle=oracle_timeline(noise_pre);s0=stoi(clean,noisy,16000);e0=stoi(clean,noisy,16000,extended=True)
 for config in ('legacy','current'):
  est=psdrows[(m['name'],config)]
  final_ratio=float(np.mean(est)/(np.mean(oracle[-1])+1e-30))
  oracle_power=np.mean(oracle,axis=1);active=oracle_power>max(float(oracle_power.max())*1e-6,1e-30)
  frame_ratios=float(np.mean(est))/(oracle_power[active]+1e-30)
  out,_=sf.read(exp/config/(m['name']+'.wav'),dtype='float64')
  rows.append({'name':m['name'],'scenario':m['scenario'],'source':m['source'],'noise_type':m['noise_type'],'config':config,'psd_oracle_ratio_final_frame':final_ratio,'per_frame_ratio_median':float(np.median(frame_ratios)),'per_frame_ratio_p10':float(np.quantile(frame_ratios,.1)),'per_frame_ratio_p90':float(np.quantile(frame_ratios,.9)),'noisy_stoi':s0,'output_stoi':stoi(clean,out,16000),'stoi_delta':stoi(clean,out,16000)-s0,'noisy_estoi':e0,'output_estoi':stoi(clean,out,16000,extended=True),'estoi_delta':stoi(clean,out,16000,extended=True)-e0,'median_nonspeech_attenuation_db':nonspeech_attenuation(clean,noisy,out)})
with (R/'ablation_output/v111_stress_per_file.csv').open('w',newline='',encoding='utf-8') as f:
 w=csv.DictWriter(f,fieldnames=rows[0].keys());w.writeheader();w.writerows(rows)
summary=[]
for scenario in sorted(set(r['scenario'] for r in rows)):
 for config in ('legacy','current'):
  q=[r for r in rows if r['scenario']==scenario and r['config']==config]
  summary.append({'scenario':scenario,'config':config,'n':len(q),'mean_psd_oracle_ratio_final_frame':float(np.mean([r['psd_oracle_ratio_final_frame'] for r in q])),'median_psd_oracle_ratio_final_frame':float(np.median([r['psd_oracle_ratio_final_frame'] for r in q])),'median_per_frame_ratio':float(np.median([r['per_frame_ratio_median'] for r in q])),'mean_stoi_delta':float(np.mean([r['stoi_delta'] for r in q])),'mean_estoi_delta':float(np.mean([r['estoi_delta'] for r in q])),'median_nonspeech_attenuation_db':float(np.nanmedian([r['median_nonspeech_attenuation_db'] for r in q]))})
with (R/'ablation_output/v111_stress_summary.csv').open('w',newline='',encoding='utf-8') as f:
 w=csv.DictWriter(f,fieldnames=summary[0].keys());w.writeheader();w.writerows(summary)
for r in summary: print(r)

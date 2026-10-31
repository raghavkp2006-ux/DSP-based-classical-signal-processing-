from pathlib import Path
import csv
import numpy as np
import soundfile as sf
from scipy import signal
from pystoi import stoi

R=Path(__file__).resolve().parents[1]; O=R/'ablation_output'/'v111_full_agent'

def sisdr(ref,y):
 n=min(len(ref),len(y));r=ref[:n]-np.mean(ref[:n]);e=y[:n]-np.mean(y[:n]);t=r*np.dot(e,r)/(np.dot(r,r)+1e-20);z=e-t
 return 10*np.log10((np.dot(t,t)+1e-30)/(np.dot(z,z)+1e-30))

def bootstrap_ci(v,seed):
 x=np.asarray(v,dtype=float); rng=np.random.default_rng(seed); ids=rng.integers(0,len(x),(10000,len(x)))
 vals=x[ids].mean(axis=1); return np.quantile(vals,.025),np.quantile(vals,.975)

per=[]; summary=[]; paired=[]
for ds in ('original','padded'):
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded')
 meta=list(csv.DictReader((data/'metadata.csv').open(encoding='utf-8-sig')))
 modes={}
 for cfg in ('C1','C3'):
  modes[cfg]=list(csv.DictReader((O/ds/cfg/'modes.csv').open(encoding='utf-8-sig')))
 for m in meta:
  name=Path(m['noisy_path']).name
  clean,sr=sf.read(data/'clean'/(m['file_id']+'.wav'),dtype='float64'); noisy,_=sf.read(data/'noisy'/name,dtype='float64')
  clean=np.asarray(clean).reshape(-1);noisy=np.asarray(noisy).reshape(-1);n=min(len(clean),len(noisy));clean=clean[:n];noisy=noisy[:n]
  frame=round(.025*sr);hop=round(.010*sr); nonspeech=[]
  for st in range(0,n-frame+1,hop):
   if np.mean(clean[st:st+frame]**2)<10**(-45/10):
    nonspeech.append((st,min(n,st+frame)))
  base_stoi=stoi(clean,noisy,sr);base_estoi=stoi(clean,noisy,sr,extended=True)
  sos=signal.butter(4,[300,3400],btype='bandpass',fs=sr,output='sos')
  clean_sb=signal.sosfiltfilt(sos,clean);noisy_sb=signal.sosfiltfilt(sos,noisy);base_si=sisdr(clean_sb,noisy_sb)
  for cfg in ('C1','C3'):
   mr=next(x for x in modes[cfg] if x['file_id']==m['file_id'] and x['noise_type']==m['noise_type'] and int(x['snr_db'])==int(m['snr_db']))
   y,_=sf.read(O/ds/cfg/name,dtype='float64');y=np.asarray(y).reshape(-1)[:n]
   ns_att=[10*np.log10((np.mean(y[a:b]**2)+1e-20)/(np.mean(noisy[a:b]**2)+1e-20)) for a,b in nonspeech if b>a]
   out_sb=signal.sosfiltfilt(sos,y)
   vals={'stoi_delta':stoi(clean,y,sr)-base_stoi,'estoi_delta':stoi(clean,y,sr,extended=True)-base_estoi,'speechband_sisdr_improvement_db':sisdr(clean_sb,out_sb)-base_si}
   per.append({'dataset':ds,'file':name,'file_id':m['file_id'],'noise_type':m['noise_type'],'snr_db':int(float(m['snr_db'])),'config':cfg,'route':mr['mode'],'noisy_stoi':base_stoi,'output_stoi':base_stoi+vals['stoi_delta'],'stoi_delta':vals['stoi_delta'],'noisy_estoi':base_estoi,'output_estoi':base_estoi+vals['estoi_delta'],'estoi_delta':vals['estoi_delta'],'noisy_speechband_sisdr_db':base_si,'speechband_sisdr_improvement_db':vals['speechband_sisdr_improvement_db'],'median_nonspeech_attenuation_db':float(np.median(ns_att)) if ns_att else float('nan'),'n_nonspeech_frames':len(nonspeech)})

def write_csv(name,rows):
 with (R/'ablation_output'/name).open('w',newline='',encoding='utf-8') as f:
  w=csv.DictWriter(f,fieldnames=rows[0].keys());w.writeheader();w.writerows(rows)

for di,ds in enumerate(('original','padded')):
 for ri,route in enumerate(('Light touch','Classical DSP','Full adaptive','OVERALL')):
  for ci_idx,cfg in enumerate(('C1','C3')):
   q=[x for x in per if x['dataset']==ds and x['config']==cfg and (route=='OVERALL' or x['route']==route)]
   if not q: continue
   row={'dataset':ds,'route':route,'config':cfg,'n':len(q)}
   for metric in ('stoi_delta','estoi_delta','speechband_sisdr_improvement_db'):
    v=[x[metric] for x in q];lo,hi=bootstrap_ci(v,2000+di*100+ri*10+ci_idx);row['mean_'+metric]=float(np.mean(v));row[metric+'_ci_low']=float(lo);row[metric+'_ci_high']=float(hi)
   att=[x['median_nonspeech_attenuation_db'] for x in q if np.isfinite(x['median_nonspeech_attenuation_db'])]
   row['median_nonspeech_attenuation_db']=float(np.median(att)) if att else float('nan')
   summary.append(row)
  q1=[x for x in per if x['dataset']==ds and x['config']=='C1' and (route=='OVERALL' or x['route']==route)]
  q3=[x for x in per if x['dataset']==ds and x['config']=='C3' and (route=='OVERALL' or x['route']==route)]
  a={x['file_id']+x['noise_type']+str(x['snr_db']):x for x in q1};b={x['file_id']+x['noise_type']+str(x['snr_db']):x for x in q3};keys=sorted(set(a)&set(b))
  if not keys: continue
  row={'dataset':ds,'route':route,'n':len(keys)}
  for metric in ('stoi_delta','estoi_delta','speechband_sisdr_improvement_db'):
   diff=np.array([a[k][metric]-b[k][metric] for k in keys]);lo,hi=bootstrap_ci(diff,3000+di*100+ri*10+{'stoi_delta':0,'estoi_delta':1,'speechband_sisdr_improvement_db':2}[metric]);row['mean_C1_minus_C3_'+metric]=float(diff.mean());row[metric+'_paired_ci_low']=float(lo);row[metric+'_paired_ci_high']=float(hi)
  att1=[a[k]['median_nonspeech_attenuation_db'] for k in keys if np.isfinite(a[k]['median_nonspeech_attenuation_db'])];att3=[b[k]['median_nonspeech_attenuation_db'] for k in keys if np.isfinite(b[k]['median_nonspeech_attenuation_db'])]
  row['C1_median_nonspeech_attenuation_db']=float(np.median(att1)) if att1 else float('nan');row['C3_median_nonspeech_attenuation_db']=float(np.median(att3)) if att3 else float('nan')
  paired.append(row)

write_csv('v111_full_agent_per_file.csv',per);write_csv('v111_full_agent_summary.csv',summary);write_csv('v111_full_agent_paired_ci.csv',paired)
for x in summary: print(x)
for x in paired:
 if x['route']=='OVERALL': print('PAIRED',x)

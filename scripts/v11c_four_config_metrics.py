from pathlib import Path
import csv
import numpy as np,soundfile as sf
from scipy import signal
from pystoi import stoi
R=Path(__file__).resolve().parents[1];O=R/'ablation_output'
def sisdr(ref,y):
 n=min(len(ref),len(y));r=ref[:n]-np.mean(ref[:n]);e=y[:n]-np.mean(y[:n]);t=r*np.dot(e,r)/(np.dot(r,r)+1e-20);z=e-t
 return 10*np.log10((np.dot(t,t)+1e-30)/(np.dot(z,z)+1e-30))
def write(name,rs):
 with (O/name).open('w',newline='',encoding='utf-8') as f:w=csv.DictWriter(f,fieldnames=rs[0]);w.writeheader();w.writerows(rs)
per=[];summ=[];pooled_attenuation={}
for ds in ('original','padded'):
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded')
 meta=list(csv.DictReader((data/'metadata.csv').open(encoding='utf-8-sig')))
 mode=list(csv.DictReader((O/f'stftfix_{ds}'/'kotlin'/'kotlin_modes.csv').open(encoding='utf-8-sig')))
 for i,m in enumerate(meta):
  if mode[i]['mode']!='Classical DSP':continue
  name=Path(m['noisy_path']).name;clean,sr=sf.read(data/'clean'/(m['file_id']+'.wav'),dtype='float64');noisy,_=sf.read(data/'noisy'/name,dtype='float64')
  if noisy.ndim>1:noisy=noisy.mean(axis=1)
  n=min(len(clean),len(noisy));clean=clean[:n];noisy=noisy[:n]
  labels=[];att=[];flen=round(.025*sr);hop=round(.010*sr)
  for st in range(0,n-flen+1,hop):
   speech=np.mean(clean[st:st+flen]**2)>=10**(-45/10);labels.append(speech)
  for cfg in ('C0','C1','C2','C3'):
   y,_=sf.read(O/'v11c_configs'/ds/cfg/name,dtype='float64');y=y[:n]
   s0=stoi(clean,noisy,sr);e0=stoi(clean,noisy,sr,extended=True);s1=stoi(clean,y,sr);e1=stoi(clean,y,sr,extended=True)
   si0=sisdr(clean,noisy);si1=sisdr(clean,y)
   sos=signal.butter(4,[300,3400],btype='bandpass',fs=sr,output='sos');sbi0=sisdr(signal.sosfiltfilt(sos,clean),signal.sosfiltfilt(sos,noisy));sbi1=sisdr(signal.sosfiltfilt(sos,clean),signal.sosfiltfilt(sos,y))
   for j,st in enumerate(range(0,n-flen+1,hop)):
    if not labels[j]:
     pn=np.mean(noisy[st:st+flen]**2);py=np.mean(y[st:st+flen]**2);att.append(10*np.log10((py+1e-20)/(pn+1e-20)))
   per.append({'dataset':ds,'file':name,'snr_db':int(float(m['snr_db'])),'noise_type':m['noise_type'],'config':cfg,'noisy_stoi':s0,'output_stoi':s1,'stoi_delta':s1-s0,'noisy_estoi':e0,'output_estoi':e1,'estoi_delta':e1-e0,'noisy_sisdr_db':si0,'output_sisdr_db':si1,'sisdr_improvement_db':si1-si0,'noisy_speechband_sisdr_db':sbi0,'output_speechband_sisdr_db':sbi1,'speechband_sisdr_improvement_db':sbi1-sbi0,'n_nonspeech_frames':sum(not q for q in labels),'median_nonspeech_attenuation_db':float(np.median(att)) if att else float('nan')})
   pooled_attenuation.setdefault((ds,cfg),[]).extend(att)
write('v11c_four_config_per_file.csv',per)
def ci(v,seed):
 x=np.asarray(v);idx=np.random.default_rng(seed).integers(0,len(x),(10000,len(x)));m=x[idx].mean(axis=1);return np.quantile(m,.025),np.quantile(m,.975)
for di,ds in enumerate(('original','padded')):
 for ci_idx,cfg in enumerate(('C0','C1','C2','C3')):
  q=[r for r in per if r['dataset']==ds and r['config']==cfg]
  stlo,sthi=ci([r['stoi_delta'] for r in q],1200+di*10+ci_idx);silo,sihi=ci([r['sisdr_improvement_db'] for r in q],1300+di*10+ci_idx);sblo,sbhi=ci([r['speechband_sisdr_improvement_db'] for r in q],1400+di*10+ci_idx)
  summ.append({'dataset':ds,'config':cfg,'n':len(q),'mean_stoi_delta':np.mean([r['stoi_delta'] for r in q]),'stoi_ci_low':stlo,'stoi_ci_high':sthi,'mean_estoi_delta':np.mean([r['estoi_delta'] for r in q]),'mean_full_sisdr_improvement_db':np.mean([r['sisdr_improvement_db'] for r in q]),'full_sisdr_ci_low':silo,'full_sisdr_ci_high':sihi,'mean_speechband_sisdr_improvement_db':np.mean([r['speechband_sisdr_improvement_db'] for r in q]),'speechband_sisdr_ci_low':sblo,'speechband_sisdr_ci_high':sbhi,'median_nonspeech_attenuation_db':float(np.median(pooled_attenuation[(ds,cfg)]))})
write('v11c_four_config_summary.csv',summ)
for x in summ:print(x)

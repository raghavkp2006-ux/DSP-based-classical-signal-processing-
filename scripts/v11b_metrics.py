import csv, math, sys
from pathlib import Path
import numpy as np, soundfile as sf
from pystoi import stoi
from scipy import signal
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'ablation_output'

def read(p):
 x,sr=sf.read(str(p),dtype='float64');
 if x.ndim>1:x=x.mean(axis=1)
 return x,sr

def writecsv(name,rows):
 if not rows:return
 p=OUT/name;p.parent.mkdir(parents=True,exist_ok=True)
 with p.open('w',newline='',encoding='utf-8') as f:
  w=csv.DictWriter(f,fieldnames=list(rows[0].keys()));w.writeheader();w.writerows(rows)

def score(ref,est,sr):
 n=min(len(ref),len(est)); ref=np.asarray(ref[:n]);est=np.asarray(est[:n])
 # pystoi handles level internally; input scale is peak-normalized by Kotlin loader, same for all arms.
 return float(stoi(ref,est,sr,extended=False)),float(stoi(ref,est,sr,extended=True))

def sisdr(ref,est):
 n=min(len(ref),len(est));r=ref[:n]-np.mean(ref[:n]);e=est[:n]-np.mean(est[:n]); a=np.dot(e,r)/(np.dot(r,r)+1e-20); t=a*r; z=e-t
 return float(10*np.log10((np.dot(t,t)+1e-20)/(np.dot(z,z)+1e-20)))

def speech_sisdr(ref,est,sr):
 sos=signal.butter(4,[300,3400],btype='bandpass',fs=sr,output='sos')
 n=min(len(ref),len(est)); return sisdr(signal.sosfiltfilt(sos,ref[:n]),signal.sosfiltfilt(sos,est[:n]))

def metadata(ds):
 base=ROOT/('ablation_testset' if ds=='original' else 'ablation_testset_padded')
 with (base/'metadata.csv').open(encoding='utf-8-sig') as f: rows=list(csv.DictReader(f))
 return base,{Path(r['noisy_path']).name:r for r in rows}

# Per-file actual production (Kotlin aligned) and noisy baseline
per=[]; byds={}
for ds in ('original','padded'):
 base,meta=metadata(ds); modes={r['file_id']+'_'+r['noise_type']+'_'+({'-5':'-05dB','0':'+00dB','5':'+05dB','10':'+10dB','15':'+15dB'}[str(int(float(r['snr_db'])))]):r for r in csv.DictReader((OUT/('stftfix_'+ds)/'kotlin'/'kotlin_modes.csv').open(encoding='utf-8-sig'))}
 rows=[]
 for filename,m in meta.items():
  clean,_=read(base/'clean'/(m['file_id']+'.wav')); noisy,sr=read(base/'noisy'/filename); actual,_=read(OUT/('stftfix_'+ds)/'kotlin'/filename)
  # align array lengths to actual output (loader trims to original duration, padding is in test input itself)
  a,b=score(clean,noisy,sr); c,d=score(clean,actual,sr)
  k=m['file_id']+'_'+m['noise_type']+'_'+({'-5':'-05dB','0':'+00dB','5':'+05dB','10':'+10dB','15':'+15dB'}[str(int(float(m['snr_db'])))] )
  mode=modes.get(k,{}).get('mode','unknown')
  row={'dataset':ds,'file':filename,'noise_type':m['noise_type'],'snr_db':int(float(m['snr_db'])),'route':mode,'noisy_stoi':a,'output_stoi':c,'stoi_delta':c-a,'noisy_estoi':b,'output_estoi':d,'estoi_delta':d-b,'speechband_sisdr_db':speech_sisdr(clean,actual,sr)}
  rows.append(row);per.append(row)
 byds[ds]=(base,meta,rows)
writecsv('v11b_per_file.csv',per)

# Classical factorial and oracle arms
fact=[];factfiles=[]
for ds in ('original','padded'):
 base,meta=metadata(ds); fdir=OUT/'v11b_factorial'/ds
 for filename,m in meta.items():
  p=fdir/'both_on'/filename
  if not p.exists():continue
  clean,_=read(base/'clean'/(m['file_id']+'.wav')); noisy,sr=read(base/'noisy'/filename); ns,ne=score(clean,noisy,sr)
  armnames=['off_off','off_on','wiener_off','wiener_on','subtraction_off','subtraction_on','both_off','both_on']
  if ds=='original':armnames+=['oracle_wiener_off','oracle_wiener_on','oracle_both_off','oracle_both_on']
  for arm in armnames:
   y,_=read(fdir/arm/filename); s,e=score(clean,y,sr)
   factfiles.append({'dataset':ds,'file':filename,'noise_type':m['noise_type'],'snr_db':int(float(m['snr_db'])),'arm':arm,'noisy_stoi':ns,'stoi':s,'stoi_delta_vs_noisy':s-ns,'estoi':e,'estoi_delta_vs_noisy':e-ne,'sisdr_vs_noisy_db':sisdr(noisy,y)})
  # off/off PCM separately
  y,_=read(fdir/'off_off_pcm'/filename);s,e=score(clean,y,sr);factfiles.append({'dataset':ds,'file':filename,'noise_type':m['noise_type'],'snr_db':int(float(m['snr_db'])),'arm':'off_off_pcm','noisy_stoi':ns,'stoi':s,'stoi_delta_vs_noisy':s-ns,'estoi':e,'estoi_delta_vs_noisy':e-ne,'sisdr_vs_noisy_db':sisdr(noisy,y)})
writecsv('v11b_factorial_per_file.csv',factfiles)
for ds in ('original','padded'):
 arms=sorted(set(r['arm'] for r in factfiles if r['dataset']==ds))
 for arm in arms:
  rs=[r for r in factfiles if r['dataset']==ds and r['arm']==arm]
  fact.append({'dataset':ds,'n':len(rs),'arm':arm,'mean_noisy_stoi':np.mean([r['noisy_stoi'] for r in rs]),'mean_stoi':np.mean([r['stoi'] for r in rs]),'mean_stoi_delta':np.mean([r['stoi_delta_vs_noisy'] for r in rs]),'mean_noisy_estoi':np.mean([r['estoi']-r['estoi_delta_vs_noisy'] for r in rs]),'mean_estoi':np.mean([r['estoi'] for r in rs]),'mean_estoi_delta':np.mean([r['estoi_delta_vs_noisy'] for r in rs]),'mean_sisdr_vs_noisy_db':np.mean([r['sisdr_vs_noisy_db'] for r in rs])})
writecsv('v11b_factorial_summary.csv',fact)

# PSD level ratios and speech contamination via >= -45dBFS 25ms clean-frame criterion
psdrows=[]
for filename in [r['file'] for r in factfiles if r['dataset']=='original' and r['arm']=='both_on']:
 base,meta=metadata('original'); m=meta[filename]; estp=OUT/'v11b_factorial'/'original'/'psd'/(filename+'.csv'); trp=OUT/'v11b_oracle_psd'/(filename+'.csv')
 if not (estp.exists() and trp.exists()):continue
 est=np.loadtxt(estp); tru=np.loadtxt(trp); ratio=np.mean(est/(tru+1e-20))
 clean,sr=read(base/'clean'/(m['file_id']+'.wav')); sr_hop=round(.010*sr); frame=round(.025*sr);starts=np.arange(0,len(clean)-frame+1,sr_hop); speech=np.array([np.mean(clean[i:i+frame]**2)>=10**(-45/10) for i in starts]);
 # Kotlin PSD frame indices are derived from pre-emphasized VAD; this is a proxy from true clean speech occupancy.
 vadp=OUT/'stftfix_sensitivity_original'/'kotlin_1'/(filename+'.vad')
 vad=np.array([c=='1' for c in vadp.read_text().strip()],dtype=bool) if vadp.exists() else np.zeros(len(speech),dtype=bool)
 n=min(len(vad),len(speech)); selected=~vad[:n]; selected_speech=selected & speech[:n]
 psdrows.append({'file':filename,'mean_bin_est_true_psd_ratio':ratio,'estimated_mean':np.mean(est),'true_mean':np.mean(tru),'selected_noise_frames':int(selected.sum()),'selected_frames_overlapping_clean_speech':int(selected_speech.sum()),'selected_speech_fraction':float(selected_speech.sum()/max(selected.sum(),1)),'first_selected_frame_is_clean_speech':bool(speech[np.flatnonzero(selected)[0]]) if selected.any() and np.flatnonzero(selected)[0]<len(speech) else 'n/a','vad_selection_says_speech_frames_are_excluded':True})
writecsv('v11b_oracle_psd_per_file.csv',psdrows)

# Group actual metrics by SNR and route and modulation per SNR. Separate modulation is computed for actual output & baseline.
modrows=[]
for ds,(base,meta,rows) in byds.items():
 for row in rows:
  clean,_=read(base/'clean'/(meta[row['file']]['file_id']+'.wav')); noisy,sr=read(base/'noisy'/row['file']); out,_=read(OUT/('stftfix_'+ds)/'kotlin'/row['file'])
  from stage_diagnostics import metrics
  m0=metrics(clean,noisy,sr)['modulation_2_10_hz'];m1=metrics(clean,out,sr)['modulation_2_10_hz']
  modrows.append({**row,'noisy_modulation':m0,'output_modulation':m1,'modulation_ratio':m1/m0 if m0>0 else float('nan')})
writecsv('v11b_modulation_per_file.csv',modrows)
summary=[]
for ds in ('original','padded'):
 for route in sorted(set(r['route'] for r in per)):
  for snr in [-5,0,5,10,15]:
   rs=[r for r in per if r['dataset']==ds and r['route']==route and r['snr_db']==snr]
   if not rs:continue
   summary.append({'dataset':ds,'route':route,'snr_db':snr,'n':len(rs),'mean_stoi_delta':np.mean([r['stoi_delta'] for r in rs]),'mean_estoi_delta':np.mean([r['estoi_delta'] for r in rs]),'mean_modulation_ratio':np.mean([r['modulation_ratio'] for r in modrows if r['dataset']==ds and r['route']==route and r['snr_db']==snr])})
writecsv('v11b_snr_summary.csv',summary)

# >=+10dB light counterfactual and brown hum no-brown paired counterfactual
cf=[];hum=[]
for ds,(base,meta,rows) in byds.items():
 for row in rows:
  if row['snr_db']>=10:
   p=OUT/'v11b_counterfactual'/ds/'light'/row['file']
   if p.exists():
    c,_=read(base/'clean'/(meta[row['file']]['file_id']+'.wav'));y,sr=read(p);s,e=score(c,y,sr)
    cf.append({'dataset':ds,'file':row['file'],'route':row['route'],'snr_db':row['snr_db'],'actual_stoi':row['output_stoi'],'light_stoi':s,'stoi_gain_light':s-row['output_stoi'],'actual_estoi':row['output_estoi'],'light_estoi':e,'estoi_gain_light':e-row['output_estoi']})
  if row['route']=='Full adaptive' and row['noise_type']=='brown_hum':
   p=OUT/'v11b_counterfactual'/ds/'no_brown'/row['file']
   if p.exists():
    c,_=read(base/'clean'/(meta[row['file']]['file_id']+'.wav')); y,sr=read(p);s,e=score(c,y,sr);a,_=read(OUT/('stftfix_'+ds)/'kotlin'/row['file']);clean,sr=read(base/'clean'/(meta[row['file']]['file_id']+'.wav'))
    hum.append({'dataset':ds,'file':row['file'],'snr_db':row['snr_db'],'brown_actual_stoi':row['output_stoi'],'no_brown_stoi':s,'delta_stoi':s-row['output_stoi'],'brown_actual_estoi':row['output_estoi'],'no_brown_estoi':e,'delta_estoi':e-row['output_estoi'],'brown_speechband_sisdr':speech_sisdr(clean,a,sr),'no_brown_speechband_sisdr':speech_sisdr(clean,y,sr),'brown_route_stoi':row['output_stoi']})
writecsv('v11b_light_counterfactual.csv',cf);writecsv('v11b_brown_hum_per_file.csv',hum)
print('per-file',len(per),'factorial',len(factfiles),'psd',len(psdrows),'light cf',len(cf),'brown',len(hum),'snr groups',len(summary))

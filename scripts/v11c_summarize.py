from pathlib import Path
import re
import csv
import numpy as np, soundfile as sf
from pystoi import stoi
R=Path(__file__).resolve().parents[1]; O=R/'ablation_output'
def rows(p):return list(csv.DictReader(p.open(encoding='utf-8-sig')))
def mean(r,k):return float(np.mean([float(x[k]) for x in r]))
def ci(values,seed):
 rng=np.random.default_rng(seed);x=np.asarray(values,float);idx=rng.integers(0,len(x),size=(10000,len(x)));m=x[idx].mean(axis=1);return np.quantile(m,.025),np.quantile(m,.975)
def write(p,rs):
 with p.open('w',newline='',encoding='utf-8') as f:w=csv.DictWriter(f,fieldnames=rs[0].keys());w.writeheader();w.writerows(rs)
summary=[];snrs=[];oracle_rows=[];identity=[]
for di,ds in enumerate(('original','padded')):
 base=rows(O/f'v11c_baseline_{ds}.csv'); after=rows(O/f'v11c_after_{ds}.csv')
 bm={r['file']:r for r in base};am={r['file']:r for r in after};assert bm.keys()==am.keys(); names=list(bm)
 bd=np.array([float(bm[n]['stoi_delta']) for n in names]);ad=np.array([float(am[n]['stoi_delta']) for n in names]);diff=ad-bd
 blo,bhi=ci(bd,8100+di);alo,ahi=ci(ad,9100+di);dlo,dhi=ci(diff,10100+di)
 summary.append({'dataset':ds,'n':len(names),'baseline_noisy_stoi':mean(base,'noisy_stoi'),'baseline_output_stoi':mean(base,'output_stoi'),'baseline_stoi_delta':mean(base,'stoi_delta'),'baseline_stoi_delta_ci_low':blo,'baseline_stoi_delta_ci_high':bhi,'after_noisy_stoi':mean(after,'noisy_stoi'),'after_output_stoi':mean(after,'output_stoi'),'after_stoi_delta':mean(after,'stoi_delta'),'after_stoi_delta_ci_low':alo,'after_stoi_delta_ci_high':ahi,'paired_gain_stoi':float(diff.mean()),'paired_gain_ci_low':dlo,'paired_gain_ci_high':dhi,'baseline_noisy_estoi':mean(base,'noisy_estoi'),'baseline_output_estoi':mean(base,'output_estoi'),'baseline_estoi_delta':mean(base,'estoi_delta'),'after_noisy_estoi':mean(after,'noisy_estoi'),'after_output_estoi':mean(after,'output_estoi'),'after_estoi_delta':mean(after,'estoi_delta'),'baseline_psd_ratio_median':float(np.median([float(x['psd_oracle_ratio']) for x in base])),'baseline_psd_ratio_mean':mean(base,'psd_oracle_ratio'),'after_psd_ratio_median':float(np.median([float(x['psd_oracle_ratio']) for x in after])),'after_psd_ratio_mean':mean(after,'psd_oracle_ratio')})
 for snr in sorted(set(int(x['snr_db']) for x in base)):
  b=[x for x in base if int(x['snr_db'])==snr];a=[x for x in after if int(x['snr_db'])==snr]
  change=np.mean([float(am[n]['output_stoi'])-float(bm[n]['output_stoi']) for n in names if int(bm[n]['snr_db'])==snr])
  snrs.append({'dataset':ds,'snr_db':snr,'n':len(b),'baseline_stoi_delta':mean(b,'stoi_delta'),'after_stoi_delta':mean(a,'stoi_delta'),'after_minus_baseline_output_stoi':float(change),'baseline_estoi_delta':mean(b,'estoi_delta'),'after_estoi_delta':mean(a,'estoi_delta')})
  for x in a:
   if int(x['snr_db'])>=10: pass
 # oracle arm only original, with same new spectral floors
 if ds=='original':
  data=R/'ablation_testset';
  for filename in names:
   m=am[filename];file_id=re.sub(r'_(white|brown_hum|tonal|bursty)_.+$','',filename);clean,sr=sf.read(data/'clean'/(file_id+'.wav'),dtype='float64'); noisy,_=sf.read(data/'noisy'/filename,dtype='float64');oracle,_=sf.read(O/'v11b_factorial/original/oracle_both_on'/filename,dtype='float64');new,_=sf.read(O/'v11b_factorial/original/both_on'/filename,dtype='float64')
   snew=stoi(clean,new,sr);soracle=stoi(clean,oracle,sr)
   oracle_rows.append({'dataset':ds,'file':filename,'noisy_stoi':float(m['noisy_stoi']),'new_stoi':snew,'oracle_stoi':soracle,'new_minus_oracle_stoi':snew-soracle,'new_estoi':stoi(clean,new,sr,extended=True),'oracle_estoi':stoi(clean,oracle,sr,extended=True)})
 # identity PCM: compare exported all-off signal to normalized noisy input, SI-SDR scale invariant
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded')
 for filename in names:
  inp,sr=sf.read(data/'noisy'/filename,dtype='float64'); out,_=sf.read(O/f'v11b_factorial/{ds}/off_off_pcm'/filename,dtype='float64'); inp=inp/(np.max(np.abs(inp))+1e-15)
  n=min(len(inp),len(out));ref=inp[:n]-np.mean(inp[:n]);est=out[:n]-np.mean(out[:n]);target=ref*np.dot(est,ref)/(np.dot(ref,ref)+1e-20);err=est-target;sisdr=10*np.log10((np.dot(target,target)+1e-30)/(np.dot(err,err)+1e-30))
  identity.append({'dataset':ds,'file':filename,'identity_sisdr_pcm_db':sisdr,'pass_60db':sisdr>=60.0})
write(O/'v11c_comparison_summary.csv',summary);write(O/'v11c_snr_buckets.csv',snrs);write(O/'v11c_oracle_comparison.csv',oracle_rows);write(O/'v11c_identity_check.csv',identity)
for x in summary:print(x['dataset'],'STOI Δ',x['after_stoi_delta'],f"CI[{x['after_stoi_delta_ci_low']:.5f},{x['after_stoi_delta_ci_high']:.5f}]",'paired gain',x['paired_gain_stoi'],f"CI[{x['paired_gain_ci_low']:.5f},{x['paired_gain_ci_high']:.5f}]",'ESTOI Δ',x['after_estoi_delta'],'PSD median/mean',x['after_psd_ratio_median'],x['after_psd_ratio_mean'])
for x in snrs:print('SNR',x)
print('identity min',min(x['identity_sisdr_pcm_db'] for x in identity),'max',max(x['identity_sisdr_pcm_db'] for x in identity),'fails',sum(not x['pass_60db'] for x in identity))
print('oracle mean new/oracle',mean(oracle_rows,'new_stoi'),mean(oracle_rows,'oracle_stoi'),'gap',mean(oracle_rows,'new_minus_oracle_stoi'))

from pathlib import Path
import csv,sys,numpy as np,soundfile as sf
R=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(R));from scripts.stage_diagnostics import metrics
D=R/'ablation_testset_padded';O=R/'ablation_output'
def read(p):
 with p.open(newline='',encoding='utf8') as f:return list(csv.DictReader(f))
def name(x):return x.replace('\\','/').split('/')[-1]
old={r['file']:r for r in read(O/'postfix_evaluation.csv') if r['dataset']=='padded' and r['arm']=='Kotlin aligned EQ (production)'}
new={r['file']:r for r in read(O/'stftfix_evaluation.csv') if r['dataset']=='padded' and r['arm']=='Kotlin aligned EQ (production)'}
rows=[]
for i,item in enumerate(read(D/'metadata.csv')):
 f=name(item['noisy_path']);c,sr=sf.read(D/'clean'/name(item['clean_path']));noisy,_=sf.read(D/'noisy'/f);noisy_rms=np.sqrt(np.mean(noisy**2));row={'file':f,'noise_type':item['noise_type'],'snr_db':item['snr_db'],'noisy_rms':noisy_rms}
 base=metrics(c,noisy,sr)['modulation_2_10_hz']
 for key,folder,entry in [('before','postfix_padded',old[f]),('after','stftfix_padded',new[f])]:
  y,_=sf.read(O/folder/'kotlin'/f);rms=np.sqrt(np.mean(y*y));gain=noisy_rms/rms;unmatched=float(entry['modulation_ratio']);matched=unmatched*gain*gain
  if i<2:
   direct=metrics(c,y*gain,sr)['modulation_2_10_hz']/base
   if not np.isclose(direct,matched,rtol=1e-9,atol=1e-10):raise AssertionError((direct,matched))
  row[key+'_output_rms']=rms;row[key+'_gain_to_noisy_rms']=gain;row[key+'_unmatched_ratio']=unmatched;row[key+'_matched_ratio']=matched
 rows.append(row)
with (O/'v11_modulation_matched.csv').open('w',newline='',encoding='utf8') as f:
 w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
for key in ('before','after'):
 for val in ('unmatched_ratio','matched_ratio','gain_to_noisy_rms'):
  x=np.array([r[key+'_'+val] for r in rows]);print(key,val,'mean',np.mean(x),'median',np.median(x),'min',np.min(x),'max',np.max(x))
print('files_rise',sum(r['after_matched_ratio']>r['before_matched_ratio'] for r in rows))

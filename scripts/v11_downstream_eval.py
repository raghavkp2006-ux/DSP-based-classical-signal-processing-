from pathlib import Path
import csv,sys,numpy as np,soundfile as sf
R=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(R));from self_eval import evaluate
D=R/'ablation_testset_padded';O=R/'ablation_output';B=O/'v11_downstream'
def rd(p):
 with p.open(newline='',encoding='utf8') as f:return list(csv.DictReader(f))
def nm(x):return x.replace('\\','/').split('/')[-1]
variants=['production','off_eq','off_dynamics','off_limiter','off_normalization','off_onnx','all_off','only_eq','only_dynamics','only_limiter','only_normalization','only_onnx']
rows=[]
for item,mode in zip(rd(D/'metadata.csv'),rd(O/'stftfix_padded/kotlin/kotlin_modes.csv')):
 name=nm(item['noisy_path']);clean,sr=sf.read(D/'clean'/nm(item['clean_path']));noisy,_=sf.read(D/'noisy'/name);r={'file':name,'mode':mode['mode'],'noise_type':item['noise_type'],'snr_db':item['snr_db'],'noisy':evaluate(clean,noisy,sr)['stoi']}
 for v in variants:
  x,_=sf.read(B/v/name);r[v]=evaluate(clean,x,sr)['stoi'];r[v+'_peak']=np.max(np.abs(x))
 rows.append(r)
with (O/'v11_downstream_stoi.csv').open('w',newline='',encoding='utf8') as f:
 w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
for mode in ('Classical DSP','Full adaptive','Light touch'):
 a=[r for r in rows if r['mode']==mode];print('\n',mode,'n',len(a))
 for k in ['noisy']+variants:
  print(k,'mean',np.mean([r[k] for r in a]),'difference_noisy',np.mean([r[k]-r['noisy'] for r in a]),'difference_production',np.mean([r[k]-r['production'] for r in a]))
for ds in ('original','padded'):
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded');orig=O/'v11_classical_original' if ds=='original' else O/'classical_ablation';m=rd(O/('stftfix_'+ds)/'kotlin/kotlin_modes.csv');a=[]
 for item,mode in zip(rd(data/'metadata.csv'),m):
  if mode['mode']!='Classical DSP':continue
  name=nm(item['noisy_path']);clean,sr=sf.read(data/'clean'/nm(item['clean_path']));noisy,_=sf.read(data/'noisy'/name);row={'dataset':ds,'file':name,'noisy':evaluate(clean,noisy,sr)['stoi']}
  for v in ['wiener_only','both']:
   y,_=sf.read(orig/v/name);row[v]=evaluate(clean,y,sr)['stoi']
  a.append(row)
 rows2=a if ds=='original' else rows2+a
 print('\nWIENER',ds,len(a),{v:np.mean([r[v] for r in a]) for v in ['noisy','wiener_only','both']},'both-wiener',np.mean([r['both']-r['wiener_only'] for r in a]))
with (O/'v11_wiener_classical.csv').open('w',newline='',encoding='utf8') as f:
 w=csv.DictWriter(f,fieldnames=rows2[0]);w.writeheader();w.writerows(rows2)

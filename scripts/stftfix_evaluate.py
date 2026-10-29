from pathlib import Path
import sys,csv
import numpy as np,soundfile as sf
R=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(R));sys.path.insert(0,str(R/'scripts'))
from self_eval import evaluate
from padded_sisdr_eval import sisdr
from alignment_postfix import scores,lagfun
from scripts.stage_diagnostics import metrics

def rd(p):
 with p.open(newline='',encoding='utf8') as f:return list(csv.DictReader(f))
def wr(p,rows):
 with p.open('w',newline='',encoding='utf8') as f:w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
def name(x):return x.replace('\\','/').split('/')[-1]
def m(a,k):
 z=np.array([float(x[k]) for x in a]);z=z[np.isfinite(z)];return float(z.mean()) if len(z) else float('nan')
rows=[];sw=[];forced=[]
old=rd(R/'ablation_output/postfix_evaluation.csv')
for ds in ('original','padded'):
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded');base=R/'ablation_output'/('stftfix_'+ds);sens=R/'ablation_output'/('stftfix_sensitivity_'+ds)
 for i,item in enumerate(rd(data/'metadata.csv')):
  f=name(item['noisy_path']);c,sr=sf.read(data/'clean'/name(item['clean_path']));n,_=sf.read(data/'noisy'/f);b=sisdr(c,n);bm=metrics(c,n,sr)
  for arm,folder in [('Python legacy EQ','python_legacy'),('Kotlin aligned EQ (production)','kotlin')]:
   y,_=sf.read(base/folder/f);lag,fullalign,interior=scores(c,n,y,sr);dm=metrics(c,y,sr)
   before=next(x for x in old if x['dataset']==ds and x['arm']==arm and x['file']==f)
   row=dict(dataset=ds,file=f,arm=arm,noisy_stoi=evaluate(c,n,sr)['stoi'],lag=lag,stoi=evaluate(c,y,sr)['stoi'],full_sisdr_improvement=sisdr(c,y)-b,lag_aligned_full_improvement=fullalign,interior_improvement=interior,modulation_ratio=dm['modulation_2_10_hz']/bm['modulation_2_10_hz'] if bm['modulation_2_10_hz']>0 else float('nan'),musical_kurtosis_ratio=dm['musical_power_kurtosis']/bm['musical_power_kurtosis'] if bm['musical_power_kurtosis']>0 else float('nan'),echo_acf=dm['echo_acf'])
   for k in ('stoi','full_sisdr_improvement','interior_improvement','modulation_ratio','musical_kurtosis_ratio','echo_acf'):row['before_'+k]=before[k]
   rows.append(row)
   for mult in (1,4):
    label='python' if folder=='python_legacy' else 'kotlin'; yy,_=sf.read(sens/(label+'_'+str(mult))/f);_,_,inter=scores(c,n,yy,sr)
    sw.append(dict(dataset=ds,file=f,arm=arm,multiplier=mult,stoi=evaluate(c,yy,sr)['stoi'],full_sisdr_improvement=sisdr(c,yy)-b,interior_improvement=inter))
  print(ds,i+1,flush=True)
 for cf in sorted((data/'clean').glob('*.wav')):
  c,sr=sf.read(cf)
  for mode in ('classical','full'):
   for arm,label in [('Python legacy EQ','python'),('Kotlin aligned EQ (production)','kotlin')]:
    y,_=sf.read(base/('forced_'+label+'_'+mode)/cf.name);lag=lagfun(c,y);a=max(0,-lag);b=min(len(c),len(y)-lag)
    forced.append(dict(dataset=ds,file=cf.name,mode=mode,arm=arm,lag=lag,full_sisdr=sisdr(c,y),lag_aligned_sisdr=sisdr(c[a:b],y[a+lag:b+lag])))
wr(R/'ablation_output/stftfix_evaluation.csv',rows);wr(R/'ablation_output/stftfix_hangover.csv',sw);wr(R/'ablation_output/stftfix_forced_clean.csv',forced)
for ds in ('original','padded'):
 for arm in ('Python legacy EQ','Kotlin aligned EQ (production)'):
  z=[x for x in rows if x['dataset']==ds and x['arm']==arm]
  print('MAIN',ds,arm,'noisy_stoi',m(z,'noisy_stoi'),*(f'{k}={m(z,"before_"+k):.5f}->{m(z,k):.5f}' for k in ('stoi','full_sisdr_improvement','interior_improvement','modulation_ratio','musical_kurtosis_ratio','echo_acf')))
  for mult in (1,4):
   z=[x for x in sw if x['dataset']==ds and x['arm']==arm and x['multiplier']==mult];print('SENS',ds,arm,mult,*(f'{k}={m(z,k):.5f}' for k in ('stoi','full_sisdr_improvement','interior_improvement')))
for x in forced:print('CLEAN',x)

from pathlib import Path
import sys,csv
import numpy as np,soundfile as sf
R=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(R));sys.path.insert(0,str(R/'scripts'))
from self_eval import evaluate
from padded_sisdr_eval import sisdr
from alignment_postfix import scores
from scripts.stage_diagnostics import metrics

def rd(p):
 with p.open(newline='',encoding='utf8') as f:return list(csv.DictReader(f))
def name(x):return x.replace('\\','/').split('/')[-1]
def wr(p,rows):
 with p.open('w',newline='',encoding='utf8') as f:w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
rows=[];sensitivity=[];before=[]
for ds in ('original','padded'):
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded');base=R/'ablation_output'/('postfix_'+ds);sens=R/'ablation_output'/('sensitivity_'+ds);old=R/'ablation_output' if ds=='original' else R/'ablation_output/padded'
 md=rd(data/'metadata.csv');modes=rd(base/'kotlin/kotlin_modes.csv')
 for i,(item,mode) in enumerate(zip(md,modes)):
  f=name(item['noisy_path']);c,sr=sf.read(data/'clean'/name(item['clean_path']));n,_=sf.read(data/'noisy'/f);baseline=sisdr(c,n); bm=metrics(c,n,sr)
  rows.append(dict(dataset=ds,file=f,mode=mode['mode'],arm='Noisy input',stoi=evaluate(c,n,sr)['stoi'],full_sisdr_improvement=0.0,lag_aligned_improvement=0.0,interior_improvement=0.0,modulation_ratio=1.0 if bm['modulation_2_10_hz']>0 else float('nan'),musical_kurtosis_ratio=1.0 if bm['musical_power_kurtosis']>0 else float('nan'),echo_acf=bm['echo_acf']))
  for arm,folder,oldfolder in [('Python legacy EQ','python_legacy','py_agent_regen'),('Kotlin legacy EQ','kotlin_legacy','kotlin_legacy'),('Kotlin aligned EQ (production)','kotlin','kotlin')]:
   y,_=sf.read(base/folder/f);lag,aligned,interior=scores(c,n,y,sr); d=metrics(c,y,sr);ratio=lambda k: d[k]/bm[k] if bm[k]>0 else float('nan')
   rows.append(dict(dataset=ds,file=f,mode=mode['mode'],arm=arm,stoi=evaluate(c,y,sr)['stoi'],full_sisdr_improvement=sisdr(c,y)-baseline,lag_aligned_improvement=aligned,interior_improvement=interior,modulation_ratio=ratio('modulation_2_10_hz'),musical_kurtosis_ratio=ratio('musical_power_kurtosis'),echo_acf=d['echo_acf']))
   oldy,_=sf.read(old/oldfolder/f);before.append(dict(dataset=ds,file=f,mode=mode['mode'],arm=arm,before=evaluate(c,oldy,sr)['stoi'],after=evaluate(c,y,sr)['stoi']))
  for m in range(1,5):
   pv=(sens/('python_'+str(m))/(f+'.vad')).read_text();kv=(sens/('kotlin_'+str(m))/(f+'.vad')).read_text();assert len(pv)==len(kv)
   mismatch=sum(a!=b for a,b in zip(pv,kv))
   for arm,folder in [('Python legacy EQ','python'),('Kotlin aligned EQ (production)','kotlin')]:
    y,_=sf.read(sens/(folder+'_'+str(m))/f);lag,aligned,interior=scores(c,n,y,sr)
    sensitivity.append(dict(dataset=ds,file=f,mode=mode['mode'],multiplier=m,arm=arm,stoi=evaluate(c,y,sr)['stoi'],full_sisdr_improvement=sisdr(c,y)-baseline,lag_aligned_improvement=aligned,interior_improvement=interior,vad_mismatch=mismatch,frames=len(pv)))
  print(ds,i+1,flush=True)
wr(R/'ablation_output/postfix_evaluation.csv',rows);wr(R/'ablation_output/hangover_sensitivity.csv',sensitivity);wr(R/'ablation_output/stoi_before_after.csv',before)
for ds in ('original','padded'):
 for arm in ('Noisy input','Python legacy EQ','Kotlin legacy EQ','Kotlin aligned EQ (production)'):
  a=[x for x in rows if x['dataset']==ds and x['arm']==arm]
  print('MAIN',ds,arm,*(f'{k}={np.nanmean([x[k] for x in a]):.4f} n={sum(np.isfinite(x[k]) for x in a)}' for k in ('stoi','full_sisdr_improvement','lag_aligned_improvement','interior_improvement','modulation_ratio','musical_kurtosis_ratio','echo_acf')))
 for m in range(1,5):
  for arm in ('Python legacy EQ','Kotlin aligned EQ (production)'):
   a=[x for x in sensitivity if x['dataset']==ds and x['arm']==arm and x['multiplier']==m]; print('SENS',ds,m,arm,*(f'{k}={np.mean([x[k] for x in a]):.4f}' for k in ('stoi','full_sisdr_improvement','interior_improvement')), 'mismatch',sum(x['vad_mismatch'] for x in a),'frames',sum(x['frames'] for x in a))
 for mode in ('Light touch','Classical DSP','Full adaptive'):
  for arm in ('Python legacy EQ','Kotlin aligned EQ (production)'):
   a=[x for x in before if x['dataset']==ds and x['arm']==arm and x['mode']==mode]
   if a:print('CHANGE',ds,mode,arm,'n',len(a),'before',np.mean([x['before'] for x in a]),'after',np.mean([x['after'] for x in a]))

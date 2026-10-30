from pathlib import Path
import sys,csv,numpy as np,soundfile as sf
R=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(R));from self_eval import evaluate
rows=[]
for ds in ('original','padded'):
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded');base=R/'ablation_output'/('sensitivity_'+ds); modes=list(csv.DictReader(open(R/'ablation_output'/('postfix_'+ds)/'kotlin/kotlin_modes.csv')))
 for item,mode in zip(csv.DictReader(open(data/'metadata.csv')),modes):
  name=item['noisy_path'].replace('\\','/').split('/')[-1];clean=item['clean_path'].replace('\\','/').split('/')[-1];c,sr=sf.read(data/'clean'/clean)
  for arm in ('python','kotlin'):
   before,_=sf.read(base/(arm+'_0')/name);after,_=sf.read(base/(arm+'_1')/name)
   a=evaluate(c,before,sr)['stoi'];b=evaluate(c,after,sr)['stoi'];rows.append(dict(dataset=ds,file=name,mode=mode['mode'],arm=arm,stoi_before_vad_fix=a,stoi_after_vad_fix=b,delta=b-a))
with (R/'ablation_output/vad_fix_isolated_stoi.csv').open('w',newline='',encoding='utf8') as f:w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
for ds in ('original','padded'):
 for mode in ('Light touch','Classical DSP','Full adaptive'):
  for arm in ('python','kotlin'):
   a=[x for x in rows if x['dataset']==ds and x['mode']==mode and x['arm']==arm]
   if a:print(ds,mode,arm,'n',len(a),'before',np.mean([x['stoi_before_vad_fix'] for x in a]),'after',np.mean([x['stoi_after_vad_fix'] for x in a]),'delta',np.mean([x['delta'] for x in a]))

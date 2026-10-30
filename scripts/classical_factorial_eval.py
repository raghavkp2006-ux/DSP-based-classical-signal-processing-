from pathlib import Path
import csv,sys
import numpy as np,soundfile as sf
R=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(R))
from self_eval import evaluate

def read(p):
 with p.open(newline='',encoding='utf8') as f:return list(csv.DictReader(f))
def name(x):return x.replace('\\','/').split('/')[-1]
def main():
 data=R/'ablation_testset_padded';base=R/'ablation_output/classical_ablation';meta=read(data/'metadata.csv');modes=read(R/'ablation_output/stftfix_padded/kotlin/kotlin_modes.csv');rows=[]
 for item,mode in zip(meta,modes):
  if mode['mode']!='Classical DSP':continue
  fn=name(item['noisy_path']);clean,sr=sf.read(data/'clean'/name(item['clean_path']));noisy,_=sf.read(data/'noisy'/fn)
  row=dict(file=fn,noisy=evaluate(clean,noisy,sr)['stoi'])
  for arm in ('subtraction_only','wiener_only','both','neither'):
   y,_=sf.read(base/arm/fn);row[arm]=evaluate(clean,y,sr)['stoi']
  rows.append(row)
 assert len(rows)==12
 with (R/'ablation_output/classical_factorial.csv').open('w',newline='',encoding='utf8') as f:
  w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
 for k in ('noisy','subtraction_only','wiener_only','both','neither'):print(k,np.mean([r[k] for r in rows]))
 for k in ('subtraction_only','wiener_only','both'):print(k,'minus neither',np.mean([r[k]-r['neither'] for r in rows]))
if __name__=='__main__':main()

from pathlib import Path
import csv
import dsp_pipeline as d
import soundfile as sf
R=Path(__file__).resolve().parent.parent
for ds in ('original','padded'):
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded');out=R/'ablation_output'/('postfix_'+ds)/'python_legacy';out.mkdir(parents=True,exist_ok=True)
 for i,row in enumerate(csv.DictReader((data/'metadata.csv').open(encoding='utf8'))):
  name=row['noisy_path'].replace('\\','/').split('/')[-1];res=d.run_pipeline(str(data/'noisy'/name),str(out/name),feedback_enabled=False)
  if res['params']['use_ml_postfilter'] and not res['metrics']['ml_postfilter_used']: raise RuntimeError('ML fallback '+name)
  print(ds,i+1,name,flush=True)


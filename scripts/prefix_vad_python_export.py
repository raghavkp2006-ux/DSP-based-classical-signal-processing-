from pathlib import Path
import sys,csv
R=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(R))
import dsp_pipeline as d
base_vad=d.voice_activity_detection
def legacy_vad(x,fp,hangover_frames=0):
 labels,e,z=base_vad(x,fp,0)
 for offset in range(1,int(hangover_frames)+1):labels[offset:] |= labels[:-offset]
 return labels,e,z
d.voice_activity_detection=legacy_vad
for ds in ('original','padded'):
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded');out=R/'ablation_output'/('sensitivity_'+ds)/'python_0';out.mkdir(parents=True,exist_ok=True)
 for i,item in enumerate(csv.DictReader((data/'metadata.csv').open(encoding='utf8'))):
  name=item['noisy_path'].replace('\\','/').split('/')[-1];res=d.run_pipeline(str(data/'noisy'/name),str(out/name),feedback_enabled=False)
  if res['params']['use_ml_postfilter'] and not res['metrics']['ml_postfilter_used']:raise RuntimeError('ML fallback '+name)
  print(ds,i+1,flush=True)

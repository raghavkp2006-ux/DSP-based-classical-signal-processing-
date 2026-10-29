from pathlib import Path
import sys,csv,shutil
R=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(R))
import dsp_pipeline as d
from agent_analyzer import analyze_audio
from decision_agent import decide
for ds in ('original','padded'):
 data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded');out=R/'ablation_output'/('stftfix_'+ds)/'python_legacy';out.mkdir(parents=True,exist_ok=True);sens=R/'ablation_output'/('stftfix_sensitivity_'+ds)
 for i,item in enumerate(csv.DictReader((data/'metadata.csv').open(encoding='utf8'))):
  name=item['noisy_path'].replace('\\','/').split('/')[-1];inp=data/'noisy'/name
  res=d.run_pipeline(str(inp),str(out/name),feedback_enabled=False)
  if res['params']['use_ml_postfilter'] and not res['metrics']['ml_postfilter_used']:raise RuntimeError('ML fallback '+name)
  for m in (1,4):
   dst=sens/('python_'+str(m));dst.mkdir(parents=True,exist_ok=True)
   if m==1 or not res['params']['vad_hangover_frames']:shutil.copyfile(out/name,dst/name)
   else:
    rr=d.run_pipeline(str(inp),str(dst/name),params={'vad_hangover_frames':res['params']['vad_hangover_frames']*m},feedback_enabled=False)
    if rr['params']['use_ml_postfilter'] and not rr['metrics']['ml_postfilter_used']:raise RuntimeError('ML fallback '+name)
  print(ds,i+1,flush=True)
 for cf in sorted((data/'clean').glob('*.wav')):
  for mode,decision in [('classical',decide(10,True,1)),('full',decide(0,False,1))]:
   dest=R/'ablation_output'/('stftfix_'+ds)/('forced_python_'+mode);dest.mkdir(parents=True,exist_ok=True)
   rr=d.run_pipeline(str(cf),str(dest/cf.name),use_agent=False,params=decision['params'],feedback_enabled=False)
   if rr['params']['use_ml_postfilter'] and not rr['metrics']['ml_postfilter_used']:raise RuntimeError('ML fallback clean '+cf.name)

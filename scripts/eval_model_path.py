"""Run the evaluation with observation-only logging of the Python ML path."""
from contextlib import ExitStack
from pathlib import Path
import csv,sys
from unittest.mock import patch
R=Path(__file__).resolve().parent.parent;sys.path.insert(0,str(R))
import dsp_pipeline as d
import ml_postfilter
try:
 import trained_ml_denoiser
except Exception:
 trained_ml_denoiser = None

class ModelPathCapture:
 def __enter__(self):
  self.path='not_used';self.calls=0;self._stack=ExitStack();trained=trained_ml_denoiser.apply_trained_denoise if trained_ml_denoiser is not None else None;stage=ml_postfilter.apply_ml_denoise
  def observe_trained(*args,**kwargs):
   result=trained(*args,**kwargs);self._trained_succeeded=True;return result
  def observe_stage(*args,**kwargs):
   self.calls+=1;self._trained_succeeded=False
   try:result=stage(*args,**kwargs)
   except Exception:self.path='failed';raise
   self.path='trained_model' if self._trained_succeeded else 'spectral_gating_fallback'
   return result
  if trained_ml_denoiser is not None:
   self._stack.enter_context(patch.object(trained_ml_denoiser,'apply_trained_denoise',observe_trained))
  self._stack.enter_context(patch.object(ml_postfilter,'apply_ml_denoise',observe_stage))
  return self
 def __exit__(self,*args):return self._stack.__exit__(*args)

def main():
 rows=[]
 for ds in ('original','padded'):
  data=R/('ablation_testset' if ds=='original' else 'ablation_testset_padded');out=R/'ablation_output'/'model_audit'/ds;out.mkdir(parents=True,exist_ok=True)
  with (data/'metadata.csv').open(encoding='utf8',newline='') as f:metadata=list(csv.DictReader(f))
  for i,item in enumerate(metadata,1):
   name=item['noisy_path'].replace('\\','/').split('/')[-1]
   with ModelPathCapture() as capture:
    result=d.run_pipeline(str(data/'noisy'/name),str(out/name),feedback_enabled=False)
   before=R/'ablation_output'/('stftfix_'+ds)/'python_legacy'/name
   same=(out/name).read_bytes()==before.read_bytes()
   rows.append(dict(dataset=ds,file=name,mode=result['agent_decision']['mode'],model_path=capture.path,stage_calls=capture.calls,identical_to_pre_audit=same))
   if not same:raise RuntimeError('Logging audit changed output: '+name)
   if result['params']['use_ml_postfilter'] and capture.path=='not_used':raise RuntimeError('ML stage did not run: '+name)
   print(ds,i,capture.path,name,flush=True)
 with (R/'ablation_output/python_model_path.csv').open('w',encoding='utf8',newline='') as f:
  w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
 from collections import Counter
 print('PATH_COUNTS',dict(Counter(r['model_path'] for r in rows)))
if __name__=='__main__':main()

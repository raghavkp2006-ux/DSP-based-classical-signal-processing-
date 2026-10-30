import tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import numpy as np,soundfile as sf
import dsp_pipeline as d
class FeedbackEqTest(unittest.TestCase):
 def test_retry_forwards_initial_eq_choice(self):
  with tempfile.TemporaryDirectory() as t:
   src=str(Path(t)/'in.wav');out=str(Path(t)/'out.wav');x=(.3*np.sin(np.arange(16000)*.1)).astype(np.float32);sf.write(src,x,16000)
   choice={'mode':'Light touch','rationale':'test','params':{'use_spectral_subtraction':False,'use_eq':False,'compression_ratio':1.0,'compression_makeup_db':0.0,'use_ml_postfilter':False}}
   for aligned in (False,True):
    with self.subTest(aligned=aligned),patch('decision_agent.decide',return_value=choice),patch.object(d,'run_pipeline',wraps=d.run_pipeline) as run:
     result=run(src,out,aligned_eq=aligned,feedback_enabled=True,feedback_snr_threshold=1e6)
     self.assertEqual(run.call_count,2);self.assertEqual(result['feedback']['retry_mode'],'Classical DSP')
     self.assertEqual(run.call_args_list[1].kwargs.get('aligned_eq',False),aligned)
 def test_disabled_feedback_never_retries(self):
  with tempfile.TemporaryDirectory() as t:
   src=str(Path(t)/'in.wav');out=str(Path(t)/'out.wav');sf.write(src,np.sin(np.arange(16000)*.1)*.3,16000)
   with patch.object(d,'run_pipeline',wraps=d.run_pipeline) as run:
    run(src,out,use_agent=False,params={'use_ml_postfilter':False},aligned_eq=True,feedback_enabled=False)
    self.assertEqual(run.call_count,1)
if __name__=='__main__':unittest.main()

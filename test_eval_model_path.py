import sys,types,unittest
from unittest.mock import patch
import numpy as np
import ml_postfilter,trained_ml_denoiser
from scripts.eval_model_path import ModelPathCapture
class ModelAuditTest(unittest.TestCase):
 def test_trained_path_records_without_changing_result(self):
  x=np.arange(10,dtype=np.float32)
  with patch.object(trained_ml_denoiser.DEFAULT_CHECKPOINT.__class__,'exists',return_value=True),patch.object(trained_ml_denoiser,'apply_trained_denoise',return_value=x+1):
   with ModelPathCapture() as capture:y=ml_postfilter.apply_ml_denoise(x,16000)
  self.assertEqual(capture.path,'trained_model');np.testing.assert_array_equal(y,x+1)
 def test_fallback_records_without_changing_result(self):
  x=np.arange(10,dtype=np.float32);fake=types.SimpleNamespace(reduce_noise=lambda y,**kwargs:y+2)
  with patch.object(trained_ml_denoiser.DEFAULT_CHECKPOINT.__class__,'exists',return_value=True),patch.object(trained_ml_denoiser,'apply_trained_denoise',side_effect=RuntimeError('fail')),patch.dict(sys.modules,{'noisereduce':fake}):
   with ModelPathCapture() as capture:y=ml_postfilter.apply_ml_denoise(x,16000)
  self.assertEqual(capture.path,'spectral_gating_fallback');np.testing.assert_array_equal(y,x+2)
 def test_not_used_without_stage_call(self):
  with ModelPathCapture() as capture:pass
  self.assertEqual(capture.path,'not_used')
if __name__=='__main__':unittest.main()

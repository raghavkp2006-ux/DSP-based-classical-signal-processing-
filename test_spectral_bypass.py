import unittest
import numpy as np
from dsp_pipeline import frame_params, spectral_subtract_wiener
class SpectralBypassTest(unittest.TestCase):
 def test_bypass_edges_and_tail(self):
  for n in (1,159,400,1601):
   x=np.sin(np.arange(n)*.11)*.3+.1; fp=frame_params(n,16000)
   np.testing.assert_allclose(spectral_subtract_wiener(x,fp,np.full(fp['fft_size']//2+1,.02),n,use_spectral_subtraction=False),x,rtol=0,atol=1/32768)
 def test_enabled_processing_is_not_bypass(self):
  x=np.sin(np.arange(1600)*.11)*.3+np.cos(np.arange(1600)*.017)*.1;fp=frame_params(len(x),16000)
  y=spectral_subtract_wiener(x,fp,np.full(fp['fft_size']//2+1,.02),len(x))
  self.assertGreater(np.max(np.abs(y-x)),1e-3)
if __name__=='__main__': unittest.main()

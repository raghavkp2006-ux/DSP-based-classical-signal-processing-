import unittest
from pathlib import Path
import numpy as np
from dsp_pipeline import frame_params,spectral_subtract_wiener
FIXTURE=Path('signalchain-android/app/src/test/resources/stft_speech_fixture.csv')
class StftBoundaryTest(unittest.TestCase):
 def test_identity_reconstructs_first_and_last_samples(self):
  for n in (1,159,1600,1601,4096):
   x=(np.sin(np.arange(n)*.11)*.3+np.cos(np.arange(n)*.017)*.1).astype(np.float32);fp=frame_params(n,16000)
   y=spectral_subtract_wiener(x,fp,np.full(fp['fft_size']//2+1,.02),n,alpha=0,beta=1,gain_smooth=1)
   np.testing.assert_allclose(y,x,rtol=0,atol=1e-6,err_msg=f'n={n}')
 def test_modified_spectrum_peak_is_bounded_for_speech_and_sample_one_case(self):
  speech=np.loadtxt(FIXTURE,dtype=np.float32);n=1600; sample=(np.sin(np.arange(n)*.11)*.3+np.cos(np.arange(n)*.017)*.1).astype(np.float32)
  for x in (speech,sample):
   fp=frame_params(len(x),16000);y=spectral_subtract_wiener(x,fp,np.full(fp['fft_size']//2+1,.02),len(x))
   self.assertLessEqual(np.max(np.abs(y)),2*np.max(np.abs(x)))
 def test_original_sample_one_case_and_python_kotlin_reference(self):
  root=FIXTURE.parent
  cases=[('speech',np.loadtxt(FIXTURE,dtype=np.float32),None),('sample',(np.sin(np.arange(1600)*.11)*.3+np.cos(np.arange(1600)*.017)*.1).astype(np.float32),None),('original_case',np.loadtxt(root/'stft_case_01_preemph.csv',dtype=np.float32),np.loadtxt(root/'stft_case_02_noise_psd.csv',dtype=np.float32))]
  for name,x,noise in cases:
   fp=frame_params(len(x),16000);noise=np.full(fp['fft_size']//2+1,.02,dtype=np.float32) if noise is None else noise
   y=spectral_subtract_wiener(x,fp,noise,len(x));np.testing.assert_allclose(y,np.loadtxt(root/f'stft_expected_{name}.csv'),rtol=0,atol=1e-5)
   self.assertLessEqual(np.max(np.abs(y)),2*np.max(np.abs(x)),name)
if __name__=='__main__':unittest.main()

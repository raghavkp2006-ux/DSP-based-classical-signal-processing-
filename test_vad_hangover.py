import unittest
from dsp_pipeline import apply_vad_hangover
class VadHangoverTest(unittest.TestCase):
 def test_fixed_length_and_overlapping_original_detections(self):
  source=[False,True,False,False,False,False,False,True,False,False,False,False]
  self.assertEqual(apply_vad_hangover(source,3).tolist(),[False,True,True,True,True,False,False,True,True,True,True,False])
  self.assertEqual(apply_vad_hangover(source,0).tolist(),source)
  self.assertEqual(apply_vad_hangover([False,True,True,False,False,False],2).tolist(),[False,True,True,True,True,False])
if __name__=='__main__':unittest.main()

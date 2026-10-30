package com.signalchain.app.dsp
import org.junit.Test
import org.junit.Assert.assertArrayEquals
import kotlin.math.*
class SpectralGoldenCaptureTest {
 @Test fun enabledProcessingIsNotBypass() {
  val x=FloatArray(1600){(sin(it*0.11)*0.3+cos(it*0.017)*0.1).toFloat()};val fp=FrameParams.create(x.size,16000)
  val y=SpectralSubtraction.apply(x,fp,FloatArray(fp.fftSize/2+1){0.02f})
  org.junit.Assert.assertTrue(x.indices.maxOf{abs(x[it]-y[it])}>1e-3)
 }
 @Test fun bypassPreservesInputIncludingEdgesAndTail() {
  for(n in listOf(1,159,400,1601)) { val x=FloatArray(n){(sin(it*0.11)*0.3+0.1).toFloat()}; val fp=FrameParams.create(n,16000)
   assertArrayEquals(x,SpectralSubtraction.apply(x,fp,FloatArray(fp.fftSize/2+1){0.02f},use=false),1f/32768f)
  }
 }
}

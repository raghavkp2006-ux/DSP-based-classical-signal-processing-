package com.signalchain.app.dsp
import org.junit.Test
import org.junit.Assert.assertArrayEquals
import kotlin.math.*
class SpectralGoldenCaptureTest {
 @Test fun enabledOutputMatchesPreFixGolden() {
  val x=FloatArray(1600){(sin(it*0.11)*0.3+cos(it*0.017)*0.1).toFloat()};val fp=FrameParams.create(x.size,16000)
  val y=SpectralSubtraction.apply(x,fp,FloatArray(fp.fftSize/2+1){0.02f})
  assertArrayEquals(floatArrayOf(0f,-9.784142f,-2.0549667f,0.3717591f,-0.38382718f,0.09185802f,0.062282804f,0.01728076f,0f),intArrayOf(0,1,2,20,159,400,800,1200,1599).map{y[it]}.toFloatArray(),1e-5f)
 }
 @Test fun bypassPreservesInputIncludingEdgesAndTail() {
  for(n in listOf(1,159,400,1601)) { val x=FloatArray(n){(sin(it*0.11)*0.3+0.1).toFloat()}; val fp=FrameParams.create(n,16000)
   assertArrayEquals(x,SpectralSubtraction.apply(x,fp,FloatArray(fp.fftSize/2+1){0.02f},use=false),1f/32768f)
  }
 }
}

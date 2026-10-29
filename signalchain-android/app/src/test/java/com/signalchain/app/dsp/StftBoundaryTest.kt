package com.signalchain.app.dsp
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.*
class StftBoundaryTest {
 @Test fun identityReconstructsEdgesAndTail() {
  for(n in listOf(1,159,1600,1601,4096)) {
   val x=FloatArray(n){(sin(it*.11)*.3+cos(it*.017)*.1).toFloat()};val fp=FrameParams.create(n,16000)
   val y=SpectralSubtraction.apply(x,fp,FloatArray(fp.fftSize/2+1){.02f},alpha=0.0,beta=1.0,smooth=1.0)
   assertArrayEquals("n=$n",x,y,1e-6f)
  }
 }
 @Test fun modifiedSpectrumPeakIsBounded() {
  val speech=javaClass.classLoader!!.getResourceAsStream("stft_speech_fixture.csv")!!.bufferedReader().readLines().map{it.toFloat()}.toFloatArray()
  val sample=FloatArray(1600){(sin(it*.11)*.3+cos(it*.017)*.1).toFloat()}
  for(x in listOf(speech,sample)) {
   val fp=FrameParams.create(x.size,16000);val y=SpectralSubtraction.apply(x,fp,FloatArray(fp.fftSize/2+1){.02f})
   assertTrue("modified spectrum peak ${y.maxOf{abs(it)}} vs input ${x.maxOf{abs(it)}}",y.maxOf{abs(it)}<=2*x.maxOf{abs(it)})
  }
 }
 @Test fun matchesPythonOnSpeechAndOriginalSampleOneCase() {
  fun load(name:String)=javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readLines().map{it.toFloat()}.toFloatArray()
  val cases=listOf("speech" to load("stft_speech_fixture.csv"),"sample" to FloatArray(1600){(sin(it*.11)*.3+cos(it*.017)*.1).toFloat()},"original_case" to load("stft_case_01_preemph.csv"))
  for((name,x) in cases) {
   val fp=FrameParams.create(x.size,16000);val noise=if(name=="original_case") load("stft_case_02_noise_psd.csv") else FloatArray(fp.fftSize/2+1){.02f}
   val y=SpectralSubtraction.apply(x,fp,noise);assertArrayEquals(name,load("stft_expected_${name}.csv"),y,1e-5f)
   assertTrue("$name bounded peak",y.maxOf{abs(it)}<=2*x.maxOf{abs(it)})
  }
 }
}

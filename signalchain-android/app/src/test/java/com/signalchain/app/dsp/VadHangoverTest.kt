package com.signalchain.app.dsp
import org.junit.Test
import org.junit.Assert.assertArrayEquals
class VadHangoverTest {
 @Test fun fixedLengthOnlyOriginalSpeechExtends() {
  val x=booleanArrayOf(false,true,false,false,false,false,false,true,false,false,false,false)
  assertArrayEquals(booleanArrayOf(false,true,true,true,true,false,false,true,true,true,true,false),Vad.applyHangover(x,3))
  assertArrayEquals(x,Vad.applyHangover(x,0))
  assertArrayEquals(booleanArrayOf(false,true,true,true,true,false),Vad.applyHangover(booleanArrayOf(false,true,true,false,false,false),2))
 }
}

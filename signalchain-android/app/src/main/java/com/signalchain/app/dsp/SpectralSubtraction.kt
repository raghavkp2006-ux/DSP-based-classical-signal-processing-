package com.signalchain.app.dsp
import kotlin.math.*
object SpectralSubtraction {
 const val MAX_OVER_SUBTRACTION_FACTOR = 1.0
 const val MIN_SPECTRAL_SUBTRACTION_GAIN = 0.30
 const val MIN_WIENER_GAIN = 0.30

 fun apply(x:FloatArray,fp:FrameParams,noise:FloatArray,n:Int=x.size,alpha:Double=1.2,beta:Double=.1,smooth:Double=.7,use:Boolean=true,useNewLimits:Boolean=false):FloatArray {
  require(n<=x.size)
  if(!use) return x.copyOf(n)
  val pad=fp.frameLen
  val frames=ceil((n+pad).toDouble()/fp.hopLen).toInt()+1
  val boundedAlpha=if(useNewLimits) alpha.coerceIn(0.0,MAX_OVER_SUBTRACTION_FACTOR) else alpha
  val powerFloor=if(useNewLimits) max(beta,MIN_SPECTRAL_SUBTRACTION_GAIN*MIN_SPECTRAL_SUBTRACTION_GAIN) else beta
  val wienerFloor=if(useNewLimits) MIN_WIENER_GAIN else .25
  val size=n+2*pad+fp.hopLen+fp.fftSize
  val out=DoubleArray(size);val weights=DoubleArray(size);var prev=DoubleArray(noise.size){1.0}
  for(i in 0 until frames) {
   val idx=i*fp.hopLen
   val frame=DoubleArray(fp.fftSize){k->
    val source=idx+k-pad
    if(k<fp.frameLen && source in 0 until n) x[source]*fp.win[k] else 0.0
   }
   val spectrum=Fft.rfft(frame)
   val clean=Array(spectrum.size){k->
    val m2=spectrum[k].re*spectrum[k].re+spectrum[k].im*spectrum[k].im
    val mc=max(m2-boundedAlpha*noise[k],powerFloor*m2)
    val post=max(m2/(noise[k]+1e-10)-1,0.0)
    val gain=max(smooth*prev[k]+(1-smooth)*post/(post+1),wienerFloor)
    prev[k]=gain
    val mag=sqrt(mc)*gain;val ph=atan2(spectrum[k].im,spectrum[k].re)
    Complex(mag*cos(ph),mag*sin(ph))
   }
   val reconstructed=Fft.irfft(clean,fp.fftSize)
   for(k in 0 until fp.frameLen){val position=idx+k;val w=fp.win[k];out[position]+=reconstructed[k]*w;weights[position]+=w*w}
  }
  return FloatArray(n){(out[pad+it]/max(weights[pad+it],1e-6)).toFloat()}
 }
}

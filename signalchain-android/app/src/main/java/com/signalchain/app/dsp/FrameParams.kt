package com.signalchain.app.dsp
import kotlin.math.*
data class FrameParams(val frameLen:Int,val hopLen:Int,val fftSize:Int,val numFrames:Int,val win:DoubleArray){companion object{fun create(n:Int,fs:Int):FrameParams{val fl=round(.025*fs).toInt();val hl=round(.010*fs).toInt();var f=1;while(f<fl*2)f=f shl 1;return FrameParams(fl,hl,f,max(1,floor((n-fl).toDouble()/hl).toInt()+1),DoubleArray(fl){Fft.hann(fl,it)})}}}

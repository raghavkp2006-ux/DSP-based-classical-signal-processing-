package com.signalchain.app.dsp
import kotlin.math.*
object NoisePsd{fun estimate(x:FloatArray,fp:FrameParams,s:BooleanArray):FloatArray{val p=DoubleArray(fp.fftSize/2+1);val ids=(0 until fp.numFrames).filter{!s[it]}.ifEmpty{0 until min(10,fp.numFrames)};for(i in ids){val a=DoubleArray(fp.fftSize){k->if(k<fp.frameLen)x.getOrElse(i*fp.hopLen+k){0f}*fp.win[k] else 0.0};Fft.rfft(a).forEachIndexed{k,c->p[k]+=c.re*c.re+c.im*c.im}};return FloatArray(p.size){i->((-2..2).sumOf{j->p.getOrElse(i+j){0.0}}/5.0/max(1,ids.count())).toFloat()}}}

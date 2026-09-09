package com.signalchain.app.dsp
import kotlin.math.*
object Eq{
 private fun fir(n:Int,cut:Double,high:Boolean=false):DoubleArray{val m=(n-1)/2;return DoubleArray(n){i->val k=i-m;val v=if(k==0)if(high)1-2*cut else 2*cut else sin(2*Math.PI*cut*k)/(Math.PI*k);v*(.54-.46*cos(2*Math.PI*i/(n-1))) } .let{if(high)DoubleArray(n){i->it[i]*if(i==m)1.0 else -1.0}else it}}
 private fun band(n:Int,l:Double,h:Double)=DoubleArray(n){i->fir(n,h)[i]-fir(n,l)[i]}
 private fun filter(x:FloatArray,b:DoubleArray)=FloatArray(x.size){i->(0..min(i,b.lastIndex)).sumOf{ x[i-it]*b[it] }.toFloat()}
 fun apply(x:FloatArray,fs:Int,gain:Double=1.0):FloatArray{if(gain<=0)return x.copyOf();val ny=fs/2.0;var o=x.copyOf();if(500/ny<1)o=FloatArray(o.size){o[it]+(gain*.6*filter(o,band(65,300/ny,500/ny))[it])};o=FloatArray(o.size){o[it]+(gain*.9*filter(o,band(65,1000/ny,min(2500/ny,.99)))[it])};if(2500/ny<.99)o=FloatArray(o.size){o[it]+(gain*.4*filter(o,band(65,2500/ny,min(4000/ny,.99)))[it])};return filter(filter(o,fir(129,80/ny,true)),fir(65,min(8000/ny,.99)))} }

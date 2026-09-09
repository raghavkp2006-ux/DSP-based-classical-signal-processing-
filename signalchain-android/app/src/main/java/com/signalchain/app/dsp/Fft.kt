package com.signalchain.app.dsp
import kotlin.math.*
data class Complex(val re:Double,val im:Double){operator fun plus(o:Complex)=Complex(re+o.re,im+o.im);operator fun minus(o:Complex)=Complex(re-o.re,im-o.im);operator fun times(o:Complex)=Complex(re*o.re-im*o.im,re*o.im+im*o.re)}
object Fft{
 fun transform(input:Array<Complex>,inverse:Boolean=false):Array<Complex>{val n=input.size;require(n>0&&n and(n-1)==0);val a=input.copyOf();var j=0;for(i in 1 until n){var b=n shr 1;while(j and b!=0){j=j xor b;b=b shr 1};j=j xor b;if(i<j){val t=a[i];a[i]=a[j];a[j]=t}};var len=2;while(len<=n){val ang=2*Math.PI/len*if(inverse)1 else -1;val wl=Complex(cos(ang),sin(ang));var i=0;while(i<n){var w=Complex(1.0,0.0);for(k in 0 until len/2){val u=a[i+k];val v=a[i+k+len/2]*w;a[i+k]=u+v;a[i+k+len/2]=u-v;w=w*wl};i+=len};len=len shl 1};if(inverse)for(i in a.indices)a[i]=Complex(a[i].re/n,a[i].im/n);return a}
 fun rfft(x:DoubleArray)=transform(Array(x.size){Complex(x[it],0.0)}).copyOfRange(0,x.size/2+1)
 fun irfft(x:Array<Complex>,n:Int):DoubleArray{val a=Array(n){Complex(0.0,0.0)};for(i in x.indices)a[i]=x[i];for(i in 1 until n/2)a[n-i]=Complex(x[i].re,-x[i].im);return transform(a,true).map{it.re}.toDoubleArray()}
 fun stft(x:FloatArray,nFft:Int=512,hopLength:Int=128):Array<Array<Pair<Float,Float>>>{val nf=max(1,if(x.size<=nFft)1 else(x.size-nFft+hopLength-1)/hopLength+1);return Array(nf){f->rfft(DoubleArray(nFft){k->if(f*hopLength+k<x.size)x[f*hopLength+k]*hann(nFft,k)else 0.0}).map{it.re.toFloat() to it.im.toFloat()}.toTypedArray()}}
 fun istft(s:Array<Array<Pair<Float,Float>>>,hopLength:Int=128):FloatArray{if(s.isEmpty())return FloatArray(0);val n=(s[0].size-1)*2;val o=DoubleArray((s.size-1)*hopLength+n);val w=DoubleArray(o.size);for(f in s.indices){val h=Array(s[f].size){Complex(s[f][it].first.toDouble(),s[f][it].second.toDouble())};val z=irfft(h,n);for(k in 0 until n){val q=hann(n,k);val p=f*hopLength+k;o[p]+=z[k]*q;w[p]+=q*q}};return FloatArray(o.size){(o[it]/(w[it]+1e-10)).toFloat()}}
 internal fun hann(n:Int,i:Int)=.5-.5*cos(2*Math.PI*i/n)
}

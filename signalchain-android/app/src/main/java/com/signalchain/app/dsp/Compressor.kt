package com.signalchain.app.dsp
import kotlin.math.*
data class LimitResult(val audio:FloatArray,val clipsPct:Double)
object Compressor{
 fun compress(x:FloatArray,fs:Int,ratio:Double=2.5,makeup:Double=20.0):FloatArray{if(ratio<=1&&makeup==0.0)return x.copyOf();val ac=exp(-1.0/(10.0*fs/1000));val rc=exp(-1.0/(120.0*fs/1000));val th=10.0.pow(-24.0/20);var g=1.0;val y=FloatArray(x.size);for(i in x.indices){val l=abs(x[i].toDouble());val t=if(l>th)th*(l/th).pow(1/ratio)/(l+1e-10)else 1.0;g=if(t<g)ac*g+(1-ac)*t else rc*g+(1-rc)*t;y[i]=(x[i]*g).toFloat()};val m=10.0.pow(makeup/20);for(i in y.indices)y[i]=(y[i]*m).toFloat();return y}
 fun normalizeAndLimit(x:FloatArray,raw:FloatArray):LimitResult{val peak=x.maxOfOrNull{abs(it)}?.toDouble()?:0.0;val y=FloatArray(x.size){(x[it]/(peak+1e-10)*.99).toFloat()};var clips=y.count{abs(it)>.98};for(i in y.indices)y[i]=(y[i]/max(1.0,abs(y[i].toDouble())/.98)).toFloat();val or=sqrt(raw.map{it.toDouble()*it}.average());val cr=sqrt(y.map{it.toDouble()*it}.average());if(cr>0){val gain=min(or/cr,10.0);for(i in y.indices)y[i]=(y[i]*gain).toFloat();val p=y.maxOfOrNull{abs(it)}?.toDouble()?:0.0;for(i in y.indices)y[i]=(y[i]/(p+1e-10)*.99).toFloat();clips+=y.count{abs(it)>.98}};return LimitResult(y,100.0*clips/max(1,x.size))}
 fun apply(samples:FloatArray)=compress(samples,16000)
}

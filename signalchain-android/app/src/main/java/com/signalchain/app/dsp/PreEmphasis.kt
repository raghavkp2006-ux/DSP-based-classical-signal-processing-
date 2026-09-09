package com.signalchain.app.dsp
object PreEmphasis{fun apply(x:FloatArray,c:Double=.97)=FloatArray(x.size){(x[it]-c*if(it==0)0.0 else x[it-1]).toFloat()};fun deEmphasize(x:FloatArray,c:Double=.97):FloatArray{val y=FloatArray(x.size);for(i in x.indices)y[i]=(x[i]+c*if(i==0)0.0 else y[i-1]).toFloat();return y}}

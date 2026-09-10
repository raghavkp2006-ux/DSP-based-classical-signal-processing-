package com.signalchain.app.dsp
object PreEmphasis{fun apply(x:FloatArray,c:Double=.97)=FloatArray(x.size){x[it]-c.toFloat()*if(it==0)0f else x[it-1]};fun deEmphasize(x:FloatArray,c:Double=.97):FloatArray{val y=FloatArray(x.size);for(i in x.indices)y[i]=x[i]+c.toFloat()*if(i==0)0f else y[i-1];return y}}

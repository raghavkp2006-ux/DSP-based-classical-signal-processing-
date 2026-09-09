package com.signalchain.app.dsp
import kotlin.math.*
data class VadResult(val isSpeech:BooleanArray,val frameEnergy:FloatArray,val frameZcr:FloatArray)
object Vad{
 fun detect(x:FloatArray,fp:FrameParams,hangover:Int=0):VadResult{val e=FloatArray(fp.numFrames);val z=FloatArray(fp.numFrames);for(i in 0 until fp.numFrames){val a=DoubleArray(fp.frameLen){k->x.getOrElse(i*fp.hopLen+k){0f}.toDouble()};e[i]=(a.sumOf{it*it}/fp.frameLen).toFloat();z[i]=(a.drop(1).indices.sumOf{abs(sign(a[it+1])-sign(a[it]))}/(2.0*fp.frameLen)).toFloat()};val nn=max(1,round(fp.numFrames*.2).toInt());val s=BooleanArray(fp.numFrames){e[it]>e.sorted().take(nn).average()*3&&z[it]<z.average()*2};if(s.count{!it}<round(fp.numFrames*.2).toInt()){java.util.Arrays.fill(s,true);e.indices.sortedBy{e[it]}.take(round(fp.numFrames*.2).toInt()).forEach{s[it]=false}};for(o in 1..hangover)for(i in o until s.size)s[i]=s[i]||s[i-o];return VadResult(s,e,z)}
 fun detectSpeechFrames(x:FloatArray,sampleRate:Int)=detect(x,FrameParams.create(x.size,sampleRate)).isSpeech
}

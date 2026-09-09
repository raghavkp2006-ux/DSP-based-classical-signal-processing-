package com.signalchain.app.ml
import ai.onnxruntime.*;import android.content.Context;import com.signalchain.app.dsp.Fft;import kotlin.math.*
class OnnxDenoiser(context:Context,assetPath:String="models/spectral_mask_denoiser.onnx"):AutoCloseable{
 private val env=OrtEnvironment.getEnvironment();private val session=env.createSession(context.assets.open(assetPath).readBytes())
 fun predictMask(x:Array<Array<FloatArray>>):Array<Array<FloatArray>>{val f=x.size;val t=x[0].size;val input=Array(1){Array(1){Array(f){FloatArray(t)}}};for(i in 0 until f)for(j in 0 until t)input[0][0][i][j]=x[i][j];OnnxTensor.createTensor(env,input).use{tensor->session.run(mapOf("log1p_magnitude" to tensor)).use{r->@Suppress("UNCHECKED_CAST")return (r[0].value as Array<Array<Array<FloatArray>>>)[0]}}}
 fun apply(audio:FloatArray,sampleRate:Int):FloatArray{val s=Fft.stft(audio,512,128);val mag=Array(s[0].size){k->FloatArray(s.size){j->ln(1+sqrt(s[j][k].first*s[j][k].first+s[j][k].second*s[j][k].second))}};val mask=predictMask(mag);val out=Array(s.size){j->Array(s[j].size){k->{val m=mask[k][j];s[j][k].first*m to s[j][k].second*m}}};return Fft.istft(out,128).copyOf(audio.size)}
 override fun close(){session.close();env.close()}
}

package com.signalchain.app.pipeline

import com.signalchain.app.dsp.FrameParams
import com.signalchain.app.dsp.PreEmphasis
import com.signalchain.app.dsp.SpectralSubtraction
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AllOffIdentityTest {
    @Test fun allOffIdentitySurvivesPcm16Export() {
        val fs=16000
        val input=FloatArray(fs){i -> (.18*sin(2*PI*211*i/fs)+.07*sin(2*PI*1733*i/fs)).toFloat()}
        val params=FrameParams.create(input.size,fs)
        val pre=PreEmphasis.apply(input)
        val bypass=SpectralSubtraction.apply(pre,params,FloatArray(params.fftSize/2+1),input.size,use=false)
        val output=PreEmphasis.deEmphasize(bypass)
        val pcm=FloatArray(output.size){i -> (kotlin.math.round(output[i]*32767).toInt().coerceIn(-32768,32767)/32768.0).toFloat()}
        val ref=input.map{it.toDouble()}.toDoubleArray();val y=pcm.map{it.toDouble()}.toDoubleArray()
        val meanRef=ref.average();val meanY=y.average()
        val r=DoubleArray(ref.size){ref[it]-meanRef};val e=DoubleArray(ref.size){y[it]-meanY}
        val dot=e.indices.sumOf{e[it]*r[it]};val scale=dot/(r.indices.sumOf{r[it]*r[it]}+1e-30)
        val target=DoubleArray(r.size){scale*r[it]};val err=DoubleArray(r.size){e[it]-target[it]}
        val sisdr=10*kotlin.math.log10((target.sumOf{it*it}+1e-30)/(err.sumOf{it*it}+1e-30))
        println("ALL_OFF_PCM16_SISDR_DB=$sisdr")
        assertTrue("All-off PCM16 identity must exceed 60 dB; was $sisdr dB",sisdr>=60.0)
    }
}

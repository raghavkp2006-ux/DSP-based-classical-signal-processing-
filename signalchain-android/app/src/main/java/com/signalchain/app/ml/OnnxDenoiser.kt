package com.signalchain.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.signalchain.app.dsp.Fft
import com.signalchain.app.util.UserFacingError
import java.io.InputStream
import kotlin.math.*

class OnnxDenoiser private constructor(modelBytes: ByteArray, @Suppress("UNUSED_PARAMETER") marker: Unit) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(1)
        options.setInterOpNumThreads(1)
        env.createSession(modelBytes, options)
    }

    constructor(context: Context, assetPath: String = "models/spectral_mask_denoiser.onnx") :
        this(context.assets.open(assetPath).use { it.readBytes() }, Unit)

    constructor(modelBytes: ByteArray) : this(modelBytes, Unit)

    constructor(modelStream: InputStream) : this(modelStream.use { it.readBytes() }, Unit)

    companion object {
        internal fun resampleLinear(x: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
            if (srcRate == dstRate) return x
            require(srcRate > 0 && dstRate > 0)
            val ratio = dstRate.toDouble() / srcRate.toDouble()
            val outLen = max(1, (x.size * ratio).toInt())
            return FloatArray(outLen) { i ->
                val srcPos = i / ratio
                val i0 = srcPos.toInt().coerceIn(0, x.size - 1)
                val i1 = (i0 + 1).coerceIn(0, x.size - 1)
                val frac = (srcPos - i0).toFloat()
                x[i0] * (1 - frac) + x[i1] * frac
            }
        }

        internal fun reflectPad(x: FloatArray, pad: Int): FloatArray {
            val n = x.size
            require(n > pad) { "Audio too short for centered STFT (need > $pad samples, got $n)" }
            return FloatArray(n + 2 * pad) { i ->
                when {
                    i < pad -> x[pad - i]
                    i >= pad + n -> x[n - 2 - (i - pad - n)]
                    else -> x[i - pad]
                }
            }
        }

        internal fun stftCentered(x: FloatArray, nFft: Int, hop: Int): Array<Array<Pair<Float, Float>>> {
            val padded = reflectPad(x, nFft / 2)
            val full = Fft.stft(padded, nFft, hop)
            val trueFrames = 1 + (padded.size - nFft) / hop
            return full.copyOfRange(0, trueFrames)
        }

        internal fun istftCentered(
            spectrum: Array<Array<Pair<Float, Float>>>, hop: Int, nFft: Int, originalLen: Int
        ): FloatArray {
            val padded = Fft.istft(spectrum, hop)
            val start = nFft / 2
            val end = (start + originalLen).coerceAtMost(padded.size)
            return padded.copyOfRange(start, end).let {
                if (it.size < originalLen) it + FloatArray(originalLen - it.size) else it
            }
        }
    }

    fun predictMask(x: Array<FloatArray>): Array<FloatArray> {
        val frequencies = x.size
        val frames = x[0].size
        val input: Array<Array<Array<FloatArray>>> = Array(1) { Array(1) { Array(frequencies) { FloatArray(frames) } } }
        for (i in 0 until frequencies) input[0][0][i] = x[i]
        OnnxTensor.createTensor(env, input).use { tensor ->
            session.run(mapOf("features" to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                return (result[0].value as Array<Array<Array<FloatArray>>>)[0][0]
            }
        }
    }

    fun apply(audio: FloatArray, sampleRate: Int): FloatArray {
        val resampled = resampleLinear(audio, sampleRate, 16_000)
        val spectrum = stftCentered(resampled, 512, 128)
        val magnitudes = Array(spectrum[0].size) { frequency ->
            FloatArray(spectrum.size) { frame ->
                ln(1 + sqrt(
                    spectrum[frame][frequency].first * spectrum[frame][frequency].first +
                        spectrum[frame][frequency].second * spectrum[frame][frequency].second
                ))
            }
        }
        val mask = predictMask(magnitudes)
        val filtered = Array(spectrum.size) { frame ->
            Array(spectrum[frame].size) { frequency ->
                val gain = mask[frequency][frame]
                spectrum[frame][frequency].first * gain to spectrum[frame][frequency].second * gain
            }
        }
        val denoised16k = istftCentered(filtered, 128, 512, resampled.size)
        return resampleLinear(denoised16k, 16_000, sampleRate)
    }

    override fun close() {
        session.close()
        env.close()
    }
}

internal data class MlPostFilterResult<T>(val value: T, val warning: String?)

internal object MlPostFilter {
    /** On failure, retain the already-enhanced DSP samples and surface the established ML warning. */
    fun <T> applyOrFallback(dspResult: T, applyFilter: () -> T): MlPostFilterResult<T> = try {
        MlPostFilterResult(applyFilter(), null)
    } catch (error: Exception) {
        Log.e("SignalChain", "ML post-filter failed, falling back to DSP-only output", error)
        MlPostFilterResult(dspResult, UserFacingError.ML_FALLBACK.userMessage)
    }
}

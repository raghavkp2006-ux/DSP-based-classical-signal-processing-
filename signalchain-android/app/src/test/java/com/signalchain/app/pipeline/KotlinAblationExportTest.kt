package com.signalchain.app.pipeline

import com.signalchain.app.audio.WavLoader
import com.signalchain.app.audio.WavWriter
import com.signalchain.app.ml.OnnxDenoiser
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class KotlinAblationExportTest {
    @Test fun exportAllConditions() {
        val env=System.getenv("SIGNALCHAIN_ABLATION_METADATA")
        assumeTrue(!env.isNullOrBlank())
        val metadata=File(env!!); val root=metadata.parentFile.parentFile
        val lines=metadata.readLines().filter { it.isNotBlank() }
        val headers=lines.first().split(','); val records=lines.drop(1)
        assertEquals("Expected 40 conditions",40,records.size)
        val bytes=javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use { it.readBytes() }
        val base=System.getenv("SIGNALCHAIN_ABLATION_OUTPUT_DIR")?.let { File(it) } ?: File(root,"ablation_output")
        val aligned=File(base,"kotlin").also { it.mkdirs() }
        val legacy=File(base,"kotlin_legacy").also { it.mkdirs() }
        val modes=File(aligned,"kotlin_modes.csv").printWriter()
        modes.use { writer ->
            writer.println("file_id,noise_type,snr_db,mode,snrDb_est,noiseStationarityCv,stationaryNoise,speechActivityRatio,warning")
            for ((index,line) in records.withIndex()) {
                val cells=line.split(','); require(cells.size==headers.size); val row=headers.zip(cells).toMap()
                val noisyName=row.getValue("noisy_path").replace('\\','/').substringAfterLast('/')
                val original=File(row.getValue("noisy_path")); val input=if(original.exists()) original else File(metadata.parentFile,"noisy/$noisyName")
                require(input.isFile) { "Missing $input" }
                val wav=WavLoader.load(input.absolutePath)
                run {
                    val a=PipelineCore.processForEvaluation(wav.samples,wav.sampleRate,{OnnxDenoiser(bytes)})
                    val b=PipelineCore.processForEvaluation(wav.samples,wav.sampleRate,{OnnxDenoiser(bytes)},legacyEq=true)
                    assertNull("Aligned fallback on $noisyName",a.warning); assertNull("Legacy fallback on $noisyName",b.warning)
                    assertEquals(wav.samples.size,a.enhancedSamples.size); assertEquals(wav.samples.size,b.enhancedSamples.size)
                    WavWriter.write(File(aligned,noisyName).absolutePath,a.enhancedSamples,wav.sampleRate)
                    WavWriter.write(File(legacy,noisyName).absolutePath,b.enhancedSamples,wav.sampleRate)
                    writer.println("${row.getValue("file_id")},${row.getValue("noise_type")},${row.getValue("snr_db")},${a.mode},${a.snrDb},${a.noiseStationarityCv},${a.stationaryNoise},${a.speechActivityRatio},${a.warning ?: ""}")
                    writer.flush()
                    println("EXPORT ${index+1}/${records.size} $noisyName mode=${a.mode}")
                }
            }
        }
    }
}

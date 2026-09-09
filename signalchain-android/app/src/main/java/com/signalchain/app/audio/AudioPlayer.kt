package com.signalchain.app.audio
import android.media.*;import java.io.File
class AudioPlayer{private var player:MediaPlayer?=null;fun play(file:File){player?.release();player=MediaPlayer().apply{setDataSource(file.absolutePath);prepare();start()}}fun release(){player?.release();player=null}}

import numpy as np
import soundfile as sf
import sounddevice as sd
Fs = 44100
t = np.linspace(0, 3, Fs*3)
# generate some noise with a sine wave
audio = 0.5 * np.sin(2 * np.pi * 440 * t) + 0.1 * np.random.randn(Fs*3)
sf.write('your_audio.wav', audio, Fs)
print('Dummy audio created: your_audio.wav')

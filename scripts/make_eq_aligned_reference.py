"""Independent scipy FIR/convolution fixtures for the aligned offline EQ.

Run from the repository root. This creates NEW fixtures; existing EQ golden
files are deliberately untouched. No Kotlin implementation is imported.
"""
from pathlib import Path
import csv
import numpy as np
from scipy.signal import firwin

ROOT = Path(__file__).resolve().parents[1]

def taps(base, sr):
    n = int(np.floor(base * sr / 16000 + .5))
    return n if n % 2 else n + 1

def kernels(sr, gain):
    n = taps(65, sr)
    for lo, hi, boost in ((300, 500, .6), (1000, 2500, .9), (2500, 4000, .4)):
        b = firwin(n, [lo, hi], pass_zero=False, fs=sr)
        b *= gain * boost
        b[n // 2] += 1
        yield b
    yield firwin(taps(129, sr), 80, pass_zero=False, fs=sr)
    if 8000 / (sr / 2) < .99:
        yield firwin(n, 8000, fs=sr)

def reference(x, sr, gain):
    # Complete convolution followed by group-delay compensation and edge crop
    # at EACH stage; float64 is independent of Kotlin's intermediate rounding.
    y = np.asarray(x, dtype=np.float64)
    for b in kernels(sr, gain):
        y = np.convolve(y, b, mode='full')[len(b)//2:len(b)//2+len(x)]
    return y

def main():
    dest = ROOT / 'signalchain-android/app/src/test/resources/eq_aligned_reference.csv'
    with dest.open('w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['rate', 'gain', 'case', 'input', 'expected', 'center_tap'])
        for sr in (16000, 44100, 48000):
            for gain in (.6, 1.):
                kernel = np.array([1.])
                for b in kernels(sr, gain):
                    kernel = np.convolve(kernel, b)
                for name in ('middle', 'end', 'mixed'):
                    x = np.zeros(2048, dtype=np.float32)
                    if name == 'middle': x[len(x)//2] = 1
                    elif name == 'end': x[-1] = 1
                    else:
                        i = np.arange(len(x))
                        x = (.6*np.sin(i*.371)+.3*np.cos(i*.071)).astype(np.float32)
                    y = reference(x, sr, gain)
                    writer.writerow([sr, gain, name, ';'.join(map(str,x)), ';'.join(format(v,'.12g') for v in y), format(kernel[len(kernel)//2],'.12g')])
    print(f'Generated 18 independent cases / 36864 samples: {dest}')

if __name__ == '__main__': main()

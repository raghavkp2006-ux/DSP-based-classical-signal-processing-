# Stage diagnostics

Run `python scripts/stage_diagnostics.py --self-test`, or supply `--clean`, `--noisy`, `--python`, `--kotlin`, and `--output` paths. Inputs must be equal-length mono audio at the same sample rate; PCM and float WAVs are read by soundfile. There are no pass/fail thresholds for actual recordings.

Noise-only frames have clean-reference short-time mean-square energy below -45 dBFS; other frames are classified as speech. Segment RMS, peak, full-scale clipping percentage and DC offset use samples covered by the selected frames; overlap near segment boundaries can put a sample in both groups.

The modulation value is the energy at 2–10 Hz of the noise-frame STFT-magnitude envelope, averaged over contiguous runs. Its ratio to the noisy input is a pumping proxy and is undefined if the baseline is zero or noise runs are too short.

The musical-noise value is Pearson kurtosis of linear STFT power over time, averaged across frequency bins in noise-only frames. The reported ratio divides this value by the noisy input's value; it is a statistical proxy and does not establish perceived musical noise.

The echo proxy is the largest normalized autocorrelation from 30–300 ms in a contiguous noise-only run. It is not a direct measurement of room reverberation and is undefined when there are no sufficiently long runs.

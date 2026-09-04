import subprocess
import imageio_ffmpeg

import sys

ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
cmd = [ffmpeg_exe, "-y", "-i", sys.argv[1], "your_audio.wav"]

result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
if result.returncode == 0:
    print("Converted successfully to your_audio.wav!")
else:
    print("Conversion failed!")
    print(result.stderr.decode("utf-8", errors="replace"))

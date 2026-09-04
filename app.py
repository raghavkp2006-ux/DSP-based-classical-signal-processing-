import os
import io
import base64
import uuid
import csv
from datetime import datetime, timezone
import numpy as np
from scipy import signal
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from flask import Flask, render_template, request, jsonify, send_from_directory
from werkzeug.utils import secure_filename

from dsp_pipeline import run_pipeline
from stt_module import transcribe

app = Flask(__name__)
app.config['UPLOAD_FOLDER'] = 'uploads'
app.config['OUTPUT_FOLDER'] = 'outputs'
app.config['MAX_CONTENT_LENGTH'] = 50 * 1024 * 1024  # 50 MB limit

os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)
os.makedirs(app.config['OUTPUT_FOLDER'], exist_ok=True)

def generate_plots_and_waveforms(res):
    audio_raw = res["audio_raw"]
    audio_final = res["audio_final"]
    Fs = res["Fs"]
    N = res["N"]
    fp = res["fp"]
    is_speech = res["is_speech"]
    frame_energy = res["frame_energy"]

    plots = {}
    def get_base64_image(fig):
        buf = io.BytesIO()
        fig.savefig(buf, format='png', bbox_inches='tight', dpi=110, facecolor='#0f1520')
        buf.seek(0)
        img_b64 = base64.b64encode(buf.read()).decode('utf-8')
        plt.close(fig)
        return f"data:image/png;base64,{img_b64}"

    plt.style.use('dark_background')

    # 1. Spectrogram Comparison
    fig_spec, axes_spec = plt.subplots(1, 2, figsize=(11, 3.2), facecolor='#0f1520')
    for ax, audio, title in zip(axes_spec, [audio_raw, audio_final], ["ORIGINAL Spectrogram", "ENHANCED Spectrogram"]):
        ax.set_facecolor('#0f1520')
        f_spec, t_spec, Sxx = signal.spectrogram(audio, Fs, window=('hann'), nperseg=256, noverlap=128, nfft=512)
        ax.pcolormesh(t_spec, f_spec / 1000, 10 * np.log10(Sxx + 1e-12), shading='gouraud', cmap='viridis', vmin=-80)
        ax.set_ylim([0, min(8, Fs / 2000)])
        ax.set_title(title, color='#e7ecf3', fontsize=11, fontweight='bold')
        ax.set_ylabel("Freq (kHz)", color='#8a97a8', fontsize=9)
        ax.set_xlabel("Time (s)", color='#8a97a8', fontsize=9)
        ax.tick_params(colors='#8a97a8')
    plt.tight_layout()
    plots['spectrogram'] = get_base64_image(fig_spec)

    # 2. PSD Comparison
    fig_psd, ax_psd = plt.subplots(figsize=(6, 3.2), facecolor='#0f1520')
    ax_psd.set_facecolor('#0f1520')
    f1, P1 = signal.welch(audio_raw, Fs, window='hann', nperseg=1024, noverlap=512, nfft=1024)
    f2, P2 = signal.welch(audio_final, Fs, window='hann', nperseg=1024, noverlap=512, nfft=1024)
    ax_psd.plot(f1, 10 * np.log10(P1 + 1e-12), color='#8a97a8', linewidth=1.2, label='Original')
    ax_psd.plot(f2, 10 * np.log10(P2 + 1e-12), color='#33e1d6', linewidth=1.5, label='Enhanced')
    ax_psd.set_xlim([0, min(8000, Fs / 2)])
    ax_psd.set_title("Power Spectral Density", color='#e7ecf3', fontsize=11, fontweight='bold')
    ax_psd.set_xlabel("Frequency (Hz)", color='#8a97a8', fontsize=9)
    ax_psd.set_ylabel("Power (dB/Hz)", color='#8a97a8', fontsize=9)
    ax_psd.tick_params(colors='#8a97a8')
    ax_psd.legend(facecolor='#131b28', edgecolor='#212b3a', labelcolor='#e7ecf3', fontsize=8)
    ax_psd.grid(True, color='#212b3a', linestyle='--', alpha=0.5)
    plt.tight_layout()
    plots['psd'] = get_base64_image(fig_psd)

    # 3. VAD Plot
    fig_vad, ax_vad = plt.subplots(figsize=(6, 3.2), facecolor='#0f1520')
    ax_vad.set_facecolor('#0f1520')
    frame_times = np.arange(fp["num_frames"]) * fp["hop_len"] / Fs
    ax_vad.fill_between(frame_times, is_speech.astype(float) * np.max(frame_energy), alpha=0.25, color='#33e1d6', label='Speech Region')
    ax_vad.plot(frame_times, frame_energy, color='#ffb454', linewidth=1, label='Frame Energy')
    ax_vad.set_title("Voice Activity Detection", color='#e7ecf3', fontsize=11, fontweight='bold')
    ax_vad.set_xlabel("Time (s)", color='#8a97a8', fontsize=9)
    ax_vad.set_ylabel("Energy", color='#8a97a8', fontsize=9)
    ax_vad.tick_params(colors='#8a97a8')
    ax_vad.legend(facecolor='#131b28', edgecolor='#212b3a', labelcolor='#e7ecf3', fontsize=8)
    ax_vad.grid(True, color='#212b3a', linestyle='--', alpha=0.5)
    plt.tight_layout()
    plots['vad'] = get_base64_image(fig_vad)

    # Downsampled waveform for interactive JS canvas
    downsample_factor = max(1, N // 1000)
    waveform_data = {
        "duration": float(N / Fs),
        "original": audio_raw[::downsample_factor].tolist(),
        "enhanced": audio_final[::downsample_factor].tolist()
    }

    return plots, waveform_data

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/process', methods=['POST'])
@app.route('/upload', methods=['POST'])
def process_endpoint():
    if 'file' not in request.files:
        return jsonify({'error': 'No file uploaded'}), 400
    file = request.files['file']
    if file.filename == '':
        return jsonify({'error': 'No file selected'}), 400
    if file:
        filename = secure_filename(file.filename)
        input_id = str(uuid.uuid4())
        ext = os.path.splitext(filename)[1]
        if not ext:
            ext = '.wav'
        in_name = f"{input_id}_in{ext}"
        out_name = f"{input_id}_out.wav"
        
        in_path = os.path.join(app.config['UPLOAD_FOLDER'], in_name)
        out_path = os.path.join(app.config['OUTPUT_FOLDER'], out_name)
        
        file.save(in_path)
        
        try:
            use_agent = request.form.get('use_agent', 'true').lower() == 'true'
            force_ml = request.form.get('use_ml_postfilter', 'false').lower() == 'true'
            use_stt = request.form.get('use_stt', 'false').lower() == 'true'
            res = run_pipeline(in_path, out_path,
                               params={'use_ml_postfilter': force_ml} if force_ml else None,
                               use_agent=use_agent)
            plots, waveform_data = generate_plots_and_waveforms(res)
            transcripts = None
            if use_stt:
                transcripts = {'raw': transcribe(in_path), 'enhanced': transcribe(out_path)}
            _append_eval_log(filename, res)
            return jsonify({
                'success': True,
                'stage_log': res['stage_log'],
                'metrics': res['metrics'],
                'waveform': waveform_data,
                'plots': plots,
                'input_url': f'/uploads/{in_name}',
                'output_url': f'/outputs/{out_name}',
                'filename': filename,
                'analysis': res['analysis'],
                'agent_decision': res['agent_decision'],
                'params': res['params'],
                'transcripts': transcripts,
            })
        except Exception as e:
            return jsonify({'error': str(e)}), 500

def _append_eval_log(filename, res):
    """Persist every agent decision for later ablation/evaluation analysis."""
    analysis = res.get('analysis') or {}
    decision = res.get('agent_decision') or {}
    row = {
        'timestamp_utc': datetime.now(timezone.utc).isoformat(), 'filename': filename,
        'snr_db': analysis.get('snr_db', res['metrics']['snr_before_db']),
        'noise_stationarity_cv': analysis.get('noise_stationarity_cv', ''),
        'speech_activity_ratio': analysis.get('speech_activity_ratio', ''),
        'decision': decision.get('mode', 'Manual'),
        'snr_after_db': res['metrics']['snr_after_db'],
        'snr_improvement_db': res['metrics']['snr_improvement_db'],
    }
    fields = list(row)
    exists = os.path.exists('eval_log.csv')
    with open('eval_log.csv', 'a', newline='', encoding='utf-8') as log:
        writer = csv.DictWriter(log, fieldnames=fields)
        if not exists:
            writer.writeheader()
        writer.writerow(row)

@app.route('/outputs/<filename>')
def serve_output(filename):
    return send_from_directory(app.config['OUTPUT_FOLDER'], filename)

@app.route('/uploads/<filename>')
def serve_upload(filename):
    return send_from_directory(app.config['UPLOAD_FOLDER'], filename)

if __name__ == '__main__':
    app.run(debug=True, port=5000)

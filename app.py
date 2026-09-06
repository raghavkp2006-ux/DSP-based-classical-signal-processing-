import os
import io
import base64
import uuid
import csv
import time
import glob
import logging
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeoutError
from datetime import datetime, timezone
from logging.handlers import RotatingFileHandler
from pathlib import Path

import numpy as np
from scipy import signal
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from flask import Flask, render_template, request, jsonify, send_from_directory
from flask_limiter import Limiter
from flask_limiter.util import get_remote_address
from werkzeug.utils import secure_filename

from dsp_pipeline import run_pipeline
from stt_module import transcribe

# ── App & config ──────────────────────────────────────────────────────────
app = Flask(__name__)
app.config['UPLOAD_FOLDER'] = 'uploads'
app.config['OUTPUT_FOLDER'] = 'outputs'
app.config['MAX_CONTENT_LENGTH'] = 50 * 1024 * 1024  # 50 MB limit

os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)
os.makedirs(app.config['OUTPUT_FOLDER'], exist_ok=True)

# ── Fix 2: File-type allow-list ───────────────────────────────────────────
ALLOWED_EXTENSIONS = {'.wav', '.mp3', '.m4a', '.ogg', '.flac', '.aac', '.mpeg'}
MAX_AUDIO_SECONDS = int(os.environ.get('MAX_AUDIO_SECONDS', '600'))  # 10 min default
PIPELINE_TIMEOUT_SECONDS = int(os.environ.get('PIPELINE_TIMEOUT_SECONDS', '60'))

# ── Fix 6: File retention ────────────────────────────────────────────────
FILE_RETENTION_SECONDS = int(os.environ.get('FILE_RETENTION_SECONDS', '3600'))  # 1 hour

# ── Fix 8: Structured logging ────────────────────────────────────────────
logger = logging.getLogger('signalchain')
logger.setLevel(logging.INFO)
_log_handler = RotatingFileHandler('signalchain.log', maxBytes=5_000_000, backupCount=3,
                                   encoding='utf-8')
_log_handler.setFormatter(logging.Formatter(
    '%(asctime)s [%(levelname)s] %(message)s', datefmt='%Y-%m-%dT%H:%M:%S'))
logger.addHandler(_log_handler)
# Also log to stdout for console visibility during development
_stream_handler = logging.StreamHandler()
_stream_handler.setFormatter(logging.Formatter(
    '%(asctime)s [%(levelname)s] %(message)s', datefmt='%Y-%m-%dT%H:%M:%S'))
logger.addHandler(_stream_handler)

# ── Fix 3: Rate limiting ─────────────────────────────────────────────────
_rate_limit = os.environ.get('PROCESS_RATE_LIMIT', '10 per minute')
limiter = Limiter(get_remote_address, app=app, default_limits=[],
                  storage_uri='memory://')


# ── Fix 13: Security headers ─────────────────────────────────────────────
@app.after_request
def _set_security_headers(response):
    response.headers['X-Content-Type-Options'] = 'nosniff'
    response.headers['X-Frame-Options'] = 'DENY'
    return response


# ── Fix 6: Cleanup routine ───────────────────────────────────────────────
def _cleanup_old_files():
    """Delete files in uploads/ and outputs/ older than FILE_RETENTION_SECONDS."""
    now = time.time()
    cleaned = 0
    for folder in (app.config['UPLOAD_FOLDER'], app.config['OUTPUT_FOLDER']):
        folder_path = Path(folder)
        if not folder_path.exists():
            continue
        for fpath in folder_path.iterdir():
            if fpath.is_file():
                try:
                    age = now - os.path.getmtime(fpath)
                    if age > FILE_RETENTION_SECONDS:
                        fpath.unlink()
                        cleaned += 1
                except OSError:
                    pass
    if cleaned:
        logger.info("Cleanup: removed %d file(s) older than %ds", cleaned,
                    FILE_RETENTION_SECONDS)


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
@limiter.limit(_rate_limit)
def process_endpoint():
    # Fix 6: Clean up old files on each request
    _cleanup_old_files()

    if 'file' not in request.files:
        return jsonify({'error': 'No file uploaded'}), 400
    file = request.files['file']
    if file.filename == '':
        return jsonify({'error': 'No file selected'}), 400
    if file:
        filename = secure_filename(file.filename)
        input_id = str(uuid.uuid4())
        ext = os.path.splitext(filename)[1].lower()
        if not ext:
            ext = '.wav'

        # Fix 2: Validate file extension against allow-list
        if ext not in ALLOWED_EXTENSIONS:
            logger.warning("Rejected upload %s: unsupported extension %s", input_id, ext)
            return jsonify({
                'error': f'Unsupported file type "{ext}". '
                         f'Allowed: {", ".join(sorted(ALLOWED_EXTENSIONS))}'
            }), 400

        in_name = f"{input_id}_in{ext}"
        out_name = f"{input_id}_out.wav"

        in_path = os.path.join(app.config['UPLOAD_FOLDER'], in_name)
        out_path = os.path.join(app.config['OUTPUT_FOLDER'], out_name)

        file.save(in_path)
        logger.info("Processing request %s (ext=%s, size=%d bytes)", input_id, ext,
                     os.path.getsize(in_path))

        try:
            use_agent = request.form.get('use_agent', 'true').lower() == 'true'
            force_ml = request.form.get('use_ml_postfilter', 'false').lower() == 'true'
            use_stt = request.form.get('use_stt', 'false').lower() == 'true'

            # Fix 2: Run pipeline with wall-clock timeout (Windows-compatible)
            def _run():
                return run_pipeline(in_path, out_path,
                                    params={'use_ml_postfilter': force_ml} if force_ml else None,
                                    use_agent=use_agent,
                                    feedback_enabled=True,
                                    use_stt_feedback=use_stt)

            with ThreadPoolExecutor(max_workers=1) as executor:
                future = executor.submit(_run)
                try:
                    res = future.result(timeout=PIPELINE_TIMEOUT_SECONDS)
                except FuturesTimeoutError:
                    logger.error("Pipeline timeout for request %s after %ds",
                                 input_id, PIPELINE_TIMEOUT_SECONDS)
                    return jsonify({
                        'error': f'Processing timed out after {PIPELINE_TIMEOUT_SECONDS}s. '
                                 'Try a shorter audio clip.',
                        'error_id': input_id[:8]
                    }), 504

            # Fix 2: Check decoded audio duration against cap
            duration_sec = res['N'] / res['Fs']
            if duration_sec > MAX_AUDIO_SECONDS:
                logger.warning("Rejected request %s: duration %.1fs exceeds %ds",
                               input_id, duration_sec, MAX_AUDIO_SECONDS)
                return jsonify({
                    'error': f'Audio exceeds the {MAX_AUDIO_SECONDS // 60} minute limit '
                             f'({duration_sec:.0f}s decoded).'
                }), 422

            plots, waveform_data = generate_plots_and_waveforms(res)
            transcripts = None
            if use_stt:
                transcripts = {'raw': transcribe(in_path), 'enhanced': transcribe(out_path)}

            # Fix 9: Log with input_id, not raw filename
            _append_eval_log(input_id, res)

            logger.info("Completed request %s: SNR %.1f -> %.1f dB, duration %.1fs",
                        input_id, res['metrics']['snr_before_db'],
                        res['metrics']['snr_after_db'], duration_sec)

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
                'feedback': res.get('feedback'),
                'transcripts': transcripts,
            })
        except ValueError as ve:
            # Specific pipeline errors (e.g. "Audio is too short")
            logger.warning("Validation error for request %s: %s", input_id, ve)
            return jsonify({'error': str(ve)}), 422
        # Fix 7: Stop leaking raw exception text to the client
        except Exception:
            error_id = uuid.uuid4().hex[:8]
            logger.exception("process_endpoint failed [%s]", error_id)
            return jsonify({
                'error': 'Something went wrong processing this file.',
                'error_id': error_id
            }), 500


def _append_eval_log(input_id, res):
    """Persist every agent decision for later ablation/evaluation analysis.

    Fix 9: Stores the generated UUID (input_id) instead of the raw user
    filename to avoid CSV-injection risk and reduce user-identifying data
    on disk.
    """
    analysis = res.get('analysis') or {}
    decision = res.get('agent_decision') or {}
    feedback = res.get('feedback') or {}
    fields = [
        'timestamp_utc', 'request_id', 'snr_db', 'noise_stationarity_cv',
        'speech_activity_ratio', 'decision', 'attempt', 'self_corrected',
        'snr_after_db', 'snr_improvement_db'
    ]
    log_file = 'eval_log.csv'
    if os.path.exists(log_file):
        try:
            with open(log_file, 'r', encoding='utf-8') as f:
                first_line = f.readline()
            if 'self_corrected' not in first_line or 'request_id' not in first_line:
                old_rows = []
                with open(log_file, 'r', encoding='utf-8') as f:
                    reader = csv.DictReader(f)
                    for r in reader:
                        r.setdefault('attempt', 'kept')
                        r.setdefault('self_corrected', False)
                        # Migrate old 'filename' column to 'request_id'
                        if 'request_id' not in r and 'filename' in r:
                            r['request_id'] = r.pop('filename', '')
                        old_rows.append(r)
                with open(log_file, 'w', newline='', encoding='utf-8') as f:
                    writer = csv.DictWriter(f, fieldnames=fields)
                    writer.writeheader()
                    for r in old_rows:
                        writer.writerow({k: r.get(k, '') for k in fields})
        except Exception:
            pass

    row = {
        'timestamp_utc': datetime.now(timezone.utc).isoformat(),
        'request_id': input_id,
        'snr_db': analysis.get('snr_db', res['metrics']['snr_before_db']),
        'noise_stationarity_cv': analysis.get('noise_stationarity_cv', ''),
        'speech_activity_ratio': analysis.get('speech_activity_ratio', ''),
        'decision': decision.get('mode', 'Manual'), 'attempt': 'kept',
        'self_corrected': feedback.get('corrected', False),
        'snr_after_db': res['metrics']['snr_after_db'],
        'snr_improvement_db': res['metrics']['snr_improvement_db'],
    }
    exists = os.path.exists(log_file)
    with open(log_file, 'a', newline='', encoding='utf-8') as log:
        writer = csv.DictWriter(log, fieldnames=fields)
        if not exists:
            writer.writeheader()
        writer.writerow(row)
        for index, attempt in enumerate(feedback.get('attempts', []), 1):
            att_metrics = attempt.get('metrics') or {}
            attempt_row = dict(
                row,
                attempt=f'attempt_{index}',
                decision=attempt.get('mode', row['decision']),
                snr_after_db=att_metrics.get('snr_after_db', row['snr_after_db']),
                snr_improvement_db=att_metrics.get('snr_improvement_db', row['snr_improvement_db'])
            )
            writer.writerow(attempt_row)

@app.route('/outputs/<filename>')
def serve_output(filename):
    return send_from_directory(app.config['OUTPUT_FOLDER'], filename)

@app.route('/uploads/<filename>')
def serve_upload(filename):
    return send_from_directory(app.config['UPLOAD_FOLDER'], filename)

# ── Fix 1: Debug mode via env var ─────────────────────────────────────────
if __name__ == '__main__':
    debug_mode = os.environ.get("FLASK_DEBUG", "0") == "1"
    app.run(debug=debug_mode, host="127.0.0.1",
            port=int(os.environ.get("PORT", 5000)),
            threaded=True)

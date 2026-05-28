"""Train a TinyConv wake-word model for "Hey Kai" and export as TFLite."""

import argparse
import io
import math
import os
import random
import struct
import tarfile
import tempfile
import wave
from pathlib import Path

import numpy as np
import requests
from scipy.signal import get_window

# ── Constants ──────────────────────────────────────────────────────────
SAMPLE_RATE = 16000
WINDOW_SIZE_MS = 30
WINDOW_STEP_MS = 20
NUM_MFCC = 13
NUM_FRAMES = 49  # ~1 second @ 20ms stride
INPUT_SHAPE = (NUM_FRAMES, NUM_MFCC)
NUM_CLASSES = 2  # kai, other

SPEECH_COMMANDS_URL = (
    "https://storage.googleapis.com/download.tensorflow.org/data/"
    "speech_commands_v0.02.tar.gz"
)
TARGET_WORDS = frozenset({
    "yes", "no", "up", "down", "left", "right",
    "on", "off", "stop", "go",
})

SEED = 42
random.seed(SEED)
np.random.seed(SEED)

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"


# ── MFCC (NumPy implementation) ────────────────────────────────────────

def preemphasis(x, coeff=0.97):
    return np.append(x[0], x[1:] - coeff * x[:-1])


def framesig(x, frame_len, frame_step, winfunc=lambda x: np.ones(x)):
    slen = len(x)
    frame_len = int(round(frame_len))
    frame_step = int(round(frame_step))
    if slen <= frame_len:
        numframes = 1
    else:
        numframes = 1 + int(math.ceil((1.0 * slen - frame_len) / frame_step))
    padlen = int((numframes - 1) * frame_step + frame_len)
    zeros = np.zeros((padlen - slen,))
    padsignal = np.concatenate((x, zeros))
    indices = np.tile(np.arange(0, frame_len), (numframes, 1)) + np.tile(
        np.arange(0, numframes * frame_step, frame_step), (frame_len, 1)
    ).T
    frames = padsignal[indices.astype(np.int32)]
    win = np.tile(winfunc(frame_len), (numframes, 1))
    return frames * win


def mfcc(signal, samplerate=SAMPLE_RATE, numcep=NUM_MFCC,
         nfft=512, nfilt=40, lowfreq=0, highfreq=None):
    highfreq = highfreq or samplerate / 2
    signal = preemphasis(signal)
    frames = framesig(signal, samplerate * WINDOW_SIZE_MS / 1000,
                      samplerate * WINDOW_STEP_MS / 1000,
                      winfunc=lambda x: np.hamming(x))
    pspec = np.abs(np.fft.rfft(frames, nfft)) ** 2
    fb = _mel_filterbank(nfilt, nfft, samplerate, lowfreq, highfreq)
    feat = np.dot(pspec, fb.T)
    feat = np.where(feat == 0, np.finfo(float).eps, feat)
    feat = np.log(feat)
    feat = _dct(feat, type=2, axis=1, norm="ortho")[:, :numcep]
    return feat


def _mel_filterbank(nfilt, nfft, sr, lowfreq, highfreq):
    lowmel = 2595 * np.log10(1 + lowfreq / 700)
    highmel = 2595 * np.log10(1 + highfreq / 700)
    melpoints = np.linspace(lowmel, highmel, nfilt + 2)
    hz = 700 * (10 ** (melpoints / 2595) - 1)
    bin = np.floor((nfft + 1) * hz / sr).astype(int)
    fbank = np.zeros((nfilt, int(nfft // 2 + 1)))
    for j in range(nfilt):
        for i in range(bin[j], bin[j + 1]):
            fbank[j, i] = (i - bin[j]) / (bin[j + 1] - bin[j])
        for i in range(bin[j + 1], bin[j + 2]):
            fbank[j, i] = (bin[j + 2] - i) / (bin[j + 2] - bin[j + 1])
    return fbank


def _dct(x, type=2, axis=-1, norm=None):
    n = x.shape[axis]
    N = np.arange(n, dtype=np.float64)
    k = N.reshape((n, 1))
    M = np.cos(np.pi * k * (N + 0.5) / n)
    if norm == "ortho":
        M *= np.sqrt(2.0 / n)
        M[0] /= np.sqrt(2)
    return np.tensordot(x, M, axes=(axis, 0))


def extract_mfcc(audio_16k: np.ndarray) -> np.ndarray:
    """Return (NUM_FRAMES, NUM_MFCC) features, zero-padded if too short."""
    feat = mfcc(audio_16k)
    if feat.shape[0] < NUM_FRAMES:
        pad = np.zeros((NUM_FRAMES - feat.shape[0], NUM_MFCC))
        feat = np.vstack((feat, pad))
    else:
        feat = feat[:NUM_FRAMES]
    return feat.astype(np.float32)


# ── Audio generation via Edge-TTS ──────────────────────────────────────

async def synthesize(text: str, voice: str = "en-US-JennyNeural",
                     rate: str = "+0%") -> np.ndarray:
    import edge_tts
    from pydub import AudioSegment
    communicate = edge_tts.Communicate(text, voice=voice, rate=rate)
    mp3_bytes = b""
    async for chunk in communicate.stream():
        if chunk["type"] == "audio":
            mp3_bytes += chunk["data"]
    if not mp3_bytes:
        raise RuntimeError("No audio received")
    seg = AudioSegment.from_mp3(io.BytesIO(mp3_bytes))
    seg = seg.set_frame_rate(SAMPLE_RATE).set_channels(1)
    raw = seg.raw_data
    audio = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
    return audio


def _resample(audio, orig_sr, target_sr):
    ratio = target_sr / orig_sr
    new_len = int(len(audio) * ratio)
    return np.interp(
        np.linspace(0, len(audio) - 1, new_len),
        np.arange(len(audio)),
        audio,
    ).astype(np.float32)


def _stretch(audio: np.ndarray, factor: float) -> np.ndarray:
    """Simple time-stretch via linear interpolation."""
    new_len = int(len(audio) / factor)
    return np.interp(
        np.linspace(0, len(audio) - 1, new_len),
        np.arange(len(audio)),
        audio,
    ).astype(np.float32)


def _add_noise(audio: np.ndarray, level=0.005):
    noise = np.random.randn(len(audio)).astype(np.float32) * level
    return audio + noise


def _shift_pitch(audio: np.ndarray, sr: int, semitones: float) -> np.ndarray:
    """Shift pitch by resampling + restoring original length."""
    ratio = 2 ** (semitones / 12)
    stretched = _stretch(audio, ratio)
    if len(stretched) > len(audio):
        return stretched[:len(audio)]
    return np.pad(stretched, (0, len(audio) - len(stretched)))


def _truncate_to_1sec(audio: np.ndarray) -> np.ndarray:
    target = SAMPLE_RATE
    if len(audio) > target:
        return audio[:target]
    return np.pad(audio, (0, target - len(audio)))


def _ensure_ffmpeg():
    from pydub.utils import which
    if which("ffmpeg") is not None:
        return
    ff_dir = r"C:\Users\zethk\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1.1-full_build\bin"
    ff_exe = os.path.join(ff_dir, "ffmpeg.exe")
    os.environ["PATH"] = ff_dir + os.pathsep + os.environ.get("PATH", "")
    if os.path.exists(ff_exe):
        from pydub import AudioSegment
        AudioSegment.converter = ff_exe
        return
    env_val = os.environ.get("FFMPEG_BINARY")
    if env_val and os.path.exists(env_val):
        from pydub import AudioSegment
        AudioSegment.converter = env_val

_ensure_ffmpeg()

import warnings
warnings.filterwarnings("ignore", category=RuntimeWarning, module="pydub")

async def generate_kai_samples(count: int) -> list[np.ndarray]:
    from pydub import AudioSegment
    voices = [
        "en-US-JennyNeural",
        "en-US-GuyNeural",
        "en-US-AriaNeural",
        "en-GB-SoniaNeural",
        "en-GB-RyanNeural",
    ]
    rates = ["+0%", "+10%", "-10%", "+15%", "-15%"]
    phrases = ["kai", "hey kai", "kai kai"]
    samples = []
    for i in range(count):
        phrase = random.choice(phrases)
        voice = random.choice(voices)
        rate = random.choice(rates)
        try:
            audio = await synthesize(phrase, voice=voice, rate=rate)
        except Exception as e:
            print(f"  TTS failed ({phrase}/{voice}/{rate}): {e}")
            continue
        audio = _truncate_to_1sec(audio)
        # Apply augmentation
        if random.random() < 0.5:
            audio = _shift_pitch(audio, SAMPLE_RATE, random.uniform(-2, 2))
        if random.random() < 0.3:
            audio = _add_noise(audio, random.uniform(0.002, 0.01))
        if random.random() < 0.3:
            audio = _stretch(audio, random.uniform(0.85, 1.15))
        audio = _truncate_to_1sec(audio)
        samples.append(audio)
        if (i + 1) % 25 == 0:
            print(f"  Generated {i + 1}/{count} kai samples")
    return samples


# ── Speech Commands dataset ────────────────────────────────────────────

def download_speech_commands(dest_dir: str):
    """Download and extract Speech Commands v0.02, filter to TARGET_WORDS."""
    dest = Path(dest_dir)
    if dest.exists() and any(dest.iterdir()):
        print(f"  Speech Commands already at {dest}")
        return
    dest.mkdir(parents=True, exist_ok=True)
    print("  Downloading Speech Commands v0.02 (~1.4 GB)...")
    r = requests.get(SPEECH_COMMANDS_URL, stream=True, timeout=600)
    r.raise_for_status()
    total = int(r.headers.get("content-length", 0))
    downloaded = 0
    chunk_size = 8192
    buf = io.BytesIO()
    for chunk in r.iter_content(chunk_size=chunk_size):
        if chunk:
            buf.write(chunk)
            downloaded += len(chunk)
            if total > 0 and downloaded % (chunk_size * 128) == 0:
                pct = downloaded / total * 100
                print(f"\r  Downloaded {downloaded // (1024*1024)} MB / {total // (1024*1024)} MB ({pct:.0f}%)", end="")
    print(f"\r  Downloaded {downloaded // (1024*1024)} MB — extracting ...")
    buf.seek(0)
    with tarfile.open(fileobj=buf) as tar:
        tar.extractall(path=dest)
    # Remove non-target-word directories
    for p in dest.iterdir():
        if p.is_dir() and p.name not in TARGET_WORDS and p.name != "_background_noise_":
            import shutil
            shutil.rmtree(p)
    total_mb = sum(f.stat().st_size for f in dest.rglob("*") if f.is_file()) // (1024*1024)
    print(f"  Extracted to {dest} ({total_mb} MB)")


def load_other_samples(sc_dir: str, max_per_word: int = 200) -> list[np.ndarray]:
    sc = Path(sc_dir)
    samples = []
    for word_dir in sorted(sc.iterdir()):
        if not word_dir.is_dir() or word_dir.name.startswith("_"):
            continue
        files = list(word_dir.iterdir())
        random.shuffle(files)
        for f in files[:max_per_word]:
            try:
                with wave.open(str(f), "rb") as wf:
                    sr = wf.getframerate()
                    frames = wf.readframes(wf.getnframes())
                    audio = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
                if sr != SAMPLE_RATE:
                    audio = _resample(audio, sr, SAMPLE_RATE)
                audio = _truncate_to_1sec(audio)
                samples.append(audio)
            except Exception:
                continue
    print(f"  Loaded {len(samples)} 'other' samples")
    return samples


# ── Model (Keras) ──────────────────────────────────────────────────────

def build_model(mean_std: tuple | None = None):
    import tensorflow as tf
    norm_layer = tf.keras.layers.Normalization(
        axis=-1, mean=mean_std[0] if mean_std else None,
        variance=mean_std[1] if mean_std else None,
    )
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=INPUT_SHAPE, name="mfcc_input"),
        norm_layer,
        tf.keras.layers.Reshape((NUM_FRAMES, NUM_MFCC, 1)),
        tf.keras.layers.Conv2D(8, (10, 8), activation="relu", padding="same"),
        tf.keras.layers.MaxPooling2D((2, 2)),
        tf.keras.layers.Conv2D(16, (10, 4), activation="relu", padding="same"),
        tf.keras.layers.MaxPooling2D((2, 2)),
        tf.keras.layers.Flatten(),
        tf.keras.layers.Dense(32, activation="relu"),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(NUM_CLASSES, activation="softmax", name="output"),
    ])
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model


# ── Main ───────────────────────────────────────────────────────────────

async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sc-dir", default=str(Path(tempfile.gettempdir()) / "speech_commands"),
                        help="Speech Commands dataset directory")
    parser.add_argument("--kai-count", type=int, default=300,
                        help="Number of TTS-generated kai samples")
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--output", default="hey_kai.tflite",
                        help="Output TFLite model path")
    args = parser.parse_args()

    import tensorflow as tf
    tf.get_logger().setLevel("ERROR")

    if os.path.exists(args.output):
        print(f"Model already exists at {args.output}, skipping training")
        print(f"  Copy to: androidApp\\src\\main\\assets\\hey_kai.tflite")
        return

    print("=" * 50)
    print("Training 'Hey Kai' Wake Word Model")
    print("=" * 50)

    # Step 1: Generate kai samples
    print("\n[1] Generating kai training samples via Edge-TTS ...")
    kai_samples = await generate_kai_samples(args.kai_count)
    print(f"  Generated {len(kai_samples)} kai samples")

    # Step 2: Download / load Speech Commands
    print("\n[2] Loading Speech Commands dataset ...")
    download_speech_commands(args.sc_dir)
    other_samples = load_other_samples(args.sc_dir)

    # Step 3: Extract MFCC features
    print("\n[3] Extracting MFCC features ...")
    X_kai = np.array([extract_mfcc(s) for s in kai_samples])
    X_other = np.array([extract_mfcc(s) for s in other_samples])
    X = np.vstack((X_kai, X_other))
    y = np.hstack((np.ones(len(X_kai), dtype=np.int32),
                   np.zeros(len(X_other), dtype=np.int32)))

    # Shuffle
    idx = np.random.permutation(len(X))
    X, y = X[idx], y[idx]

    # Split (normalize inside model — no manual normalization needed)
    split = int(0.8 * len(X))
    X_train, X_test = X[:split], X[split:]
    y_train, y_test = y[:split], y[split:]
    print(f"  Train: {len(X_train)}, Test: {len(X_test)}")

    # Step 4: Train
    print(f"\n[4] Training model ({args.epochs} epochs) ...")
    # Compute mean/variance from training set for Normalization layer
    norm_mean = X_train.mean(axis=(0, 1), keepdims=False)  # shape (13,)
    norm_var = X_train.var(axis=(0, 1), keepdims=False) + 1e-8
    model = build_model(mean_std=(norm_mean, norm_var))
    model.summary()
    history = model.fit(
        X_train, y_train,
        validation_data=(X_test, y_test),
        epochs=args.epochs,
        batch_size=32,
        verbose=2,
    )
    val_acc = max(history.history["val_accuracy"])
    print(f"  Best validation accuracy: {val_acc:.3f}")

    # Step 5: Convert to TFLite
    print(f"\n[5] Converting to TFLite ...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.TFLITE_BUILTINS_INT8,
    ]
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = lambda: (
        {"mfcc_input": X_train[i:i + 1]} for i in range(min(100, len(X_train)))
    )
    tflite_model = converter.convert()
    out_path = args.output
    with open(out_path, "wb") as f:
        f.write(tflite_model)
    print(f"  Saved: {out_path} ({len(tflite_model) / 1024:.1f} KB)")

    # Step 6: Verify
    print(f"\n[6] Verifying TFLite model ...")
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    print(f"  Input:  {input_details[0]['shape']} ({input_details[0]['dtype']})")
    print(f"  Output: {output_details[0]['shape']} ({output_details[0]['dtype']})")

    # Run a quick sanity check
    test_input = X_test[0:1]
    interpreter.set_tensor(input_details[0]["index"], test_input)
    interpreter.invoke()
    pred = interpreter.get_tensor(output_details[0]["index"])
    print(f"  Sample prediction: kai={pred[0][1]:.4f}, other={pred[0][0]:.4f}")
    print(f"  Expected: {'kai' if y_test[0] == 1 else 'other'}")
    print("\n✅ Done!")


if __name__ == "__main__":
    import asyncio
    asyncio.run(main())

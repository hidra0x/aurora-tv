"""Generate an original calm cinema loop (not the attached copyrighted track).

Mood reference: slow ~62 BPM, warm pads, soft electric-piano arpeggio,
C major / A minor colour, wide but gentle reverb.
"""

import subprocess
from pathlib import Path

import numpy as np

SR = 44100
BPM = 62.0
BEAT = 60.0 / BPM
BAR = 4 * BEAT
BARS = 16
LOOP_SECONDS = BARS * BAR
TAIL_SECONDS = 3.0
TOTAL_SECONDS = LOOP_SECONDS + TAIL_SECONDS
N = int(TOTAL_SECONDS * SR)
LOOP_N = int(LOOP_SECONDS * SR)
rng = np.random.RandomState(20261103)


def midi(note: int) -> float:
    return 440.0 * 2.0 ** ((note - 69) / 12.0)


def envelope(t: np.ndarray, attack: float, release: float, length: float) -> np.ndarray:
    attack_env = np.clip(t / max(attack, 1e-4), 0.0, 1.0)
    release_env = np.clip((length - t) / max(release, 1e-4), 0.0, 1.0)
    return attack_env * release_env


def pad_note(freq: float, length: float, amp: float, detune: float = 0.0) -> np.ndarray:
    t = np.arange(int(length * SR)) / SR
    phase = rng.uniform(0, 2 * np.pi, 4)
    f = freq * (1.0 + detune)
    tone = (
        np.sin(2 * np.pi * f * t + phase[0])
        + 0.26 * np.sin(2 * np.pi * f * 2 * t + phase[1])
        + 0.10 * np.sin(2 * np.pi * f * 3 * t + phase[2])
        + 0.035 * np.sin(2 * np.pi * f * 4 * t + phase[3])
    )
    # Slow breathing movement.
    tone *= 0.85 + 0.15 * np.sin(2 * np.pi * 0.055 * t + phase[1])
    tone *= envelope(t, 1.6, 2.2, length)
    return tone * amp


def pluck_note(freq: float, length: float, amp: float) -> np.ndarray:
    t = np.arange(int(length * SR)) / SR
    tone = (
        np.sin(2 * np.pi * freq * t)
        + 0.18 * np.sin(2 * np.pi * freq * 2 * t)
        + 0.05 * np.sin(2 * np.pi * freq * 3 * t)
    )
    env = (1.0 - np.exp(-t / 0.008)) * np.exp(-t / 0.85)
    return tone * env * amp


def bass_note(freq: float, length: float, amp: float) -> np.ndarray:
    t = np.arange(int(length * SR)) / SR
    tone = np.sin(2 * np.pi * freq * t) + 0.12 * np.sin(2 * np.pi * freq * 2 * t)
    tone *= envelope(t, 0.08, 1.2, length)
    return tone * amp


def add_at(buffer: np.ndarray, start: int, signal: np.ndarray, left: float, right: float) -> None:
    end = min(start + len(signal), buffer.shape[0])
    if end <= start:
        return
    part = signal[: end - start]
    buffer[start:end, 0] += part * left
    buffer[start:end, 1] += part * right


def reverb(signal: np.ndarray, seed: int, decay: float = 0.55, wet: float = 0.42) -> np.ndarray:
    length = int(2.0 * SR)
    local_rng = np.random.RandomState(seed)
    impulse = local_rng.randn(length)
    # Smooth the noise so the tail is soft, not hissy.
    kernel = np.ones(36) / 36.0
    impulse = np.convolve(impulse, kernel, mode="same")
    times = np.arange(length) / SR
    impulse *= np.exp(-times / decay)
    impulse[: int(0.02 * SR)] *= np.linspace(0, 1, int(0.02 * SR))
    impulse /= np.max(np.abs(impulse)) or 1.0

    n = 1 << (len(signal) + len(impulse) - 1).bit_length()
    spectrum = np.fft.rfft(signal, n)
    tail_spectrum = np.fft.rfft(impulse, n)
    tail = np.fft.irfft(spectrum * tail_spectrum, n)[: len(signal)]
    return signal + tail * wet


# Chord progression: Cmaj7 | Am7 | Fmaj7 | G6, two bars each, twice.
progression = [
    ([60, 64, 67, 71], 36, 0.00),  # Cmaj7, C2
    ([57, 60, 64, 67], 45, 0.25),  # Am7, A2
    ([53, 57, 60, 64], 41, 0.50),  # Fmaj7, F2
    ([55, 59, 62, 64], 43, 0.75),  # G6, G2
]

mix = np.zeros((N, 2), dtype=np.float64)

for cycle in range(2):
    for chord_index, (chord, bass, _) in enumerate(progression):
        start_beat = (cycle * 8 + chord_index * 2) * 4
        start = int(start_beat * BEAT * SR)
        length = BAR * 2
        samples = int(length * SR)

        # Warm pad: chord tones, slightly detuned in each channel.
        for note_index, note in enumerate(chord):
            freq = midi(note)
            amp = 0.055 / (1.0 + note_index * 0.18)
            pan = 0.5 + 0.12 * np.sin(note_index * 1.7 + chord_index)
            left = pad_note(freq, length, amp, detune=-0.0012)
            right = pad_note(freq, length, amp, detune=0.0012)
            add_at(mix, start, left, 1.0 - pan * 0.55, pan * 0.55)
            add_at(mix, start, right, pan * 0.55, 1.0 - pan * 0.55)

        # Airy high shimmer, very quiet.
        shimmer_freq = midi(chord[0] + 24)
        add_at(mix, start, pad_note(shimmer_freq, length, 0.012, detune=0.002), 0.35, 0.65)

        # Airy upper pad.
        add_at(mix, start, pad_note(midi(chord[0] + 24), length, 0.045, detune=0.003), 0.38, 0.62)
        add_at(mix, start, pad_note(midi(chord[2] + 24), length, 0.028, detune=-0.003), 0.62, 0.38)
        add_at(mix, start, pad_note(midi(chord[1] + 36), length, 0.012, detune=0.004), 0.5, 0.5)

        # Bass root, sustained through the chord.
        add_at(mix, start, bass_note(midi(bass), length, 0.055), 0.48, 0.52)

        # Soft electric-piano arpeggio: one note per beat.
        pattern = [0, 2, 1, 3, 2, 1, 3, 0]
        for step in range(8):
            note = chord[pattern[step]] + 12
            note_start = start + int(step * BEAT * SR)
            velocity = 0.16 + 0.05 * np.sin(step * 1.3 + chord_index)
            panned = 0.5 + 0.22 * np.sin(step * 0.9 + chord_index * 1.4)
            tone = pluck_note(midi(note), 2.4, velocity)
            add_at(mix, note_start, tone, 1.0 - panned * 0.5, panned * 0.5)
            if step % 2 == 0:
                high = pluck_note(midi(note + 12), 1.8, velocity * 0.55)
                add_at(mix, note_start, high, panned * 0.5, 1.0 - panned * 0.5)

        # A single soft bell at the top of the chord.
        bell_note = chord[(chord_index + cycle) % 4] + 24
        add_at(mix, start, pluck_note(midi(bell_note), 3.0, 0.07), 0.5, 0.5)

        # Sparse music-box sparkle an octave above the bell.
        sparkle = chord[(chord_index + 2) % 4] + 36
        add_at(
            mix,
            start + int(BAR * SR),
            pluck_note(midi(sparkle), 2.4, 0.022),
            0.58,
            0.42,
        )

# Gentle stereo reverb.
mix[:, 0] = reverb(mix[:, 0], seed=11, decay=0.5, wet=0.40)
mix[:, 1] = reverb(mix[:, 1], seed=29, decay=0.5, wet=0.44)

# Fold the tail back into the loop so it repeats without a gap.
tail = mix[LOOP_N:].copy()
mix = mix[:LOOP_N]
if len(tail):
    overlap = min(len(tail), LOOP_N)
    mix[-overlap:] += tail[:overlap]
    mix[: max(0, len(tail) - overlap)] += tail[overlap:]

# Short equal-power crossfade at the loop seam.
fade = int(0.035 * SR)
if fade > 0:
    ramp = np.linspace(0.0, 1.0, fade)[:, None]
    end = mix[-fade:].copy()
    start = mix[:fade].copy()
    mix[:fade] = start * ramp + end * (1.0 - ramp)
    mix = mix[:-fade]

def brighten(signal: np.ndarray, sample_rate: int) -> np.ndarray:
    n = signal.shape[0]
    spectrum = np.fft.rfft(signal, axis=0)
    freqs = np.fft.rfftfreq(n, 1 / sample_rate)
    presence = 1.0 + 0.55 * np.exp(
        -((np.log(np.maximum(freqs, 1.0)) - np.log(2600.0)) ** 2) / (2 * 0.95 ** 2)
    )
    presence *= np.where(freqs < 95.0, 0.72, 1.0)
    return np.fft.irfft(spectrum * presence[:, None], n, axis=0)


mix = brighten(mix, SR)
mix -= mix.mean(axis=0, keepdims=True)
peak = float(np.max(np.abs(mix)))
if peak > 0:
    mix *= 0.74 / peak
rms = float(np.sqrt(np.mean(mix ** 2)))
print("loop seconds", round(len(mix) / SR, 3))
print("peak", round(float(np.max(np.abs(mix))), 3), "rms", round(rms, 4), "db", round(20 * np.log10(rms or 1e-9), 1))

target = Path(r"C:\AuroraTV\app\src\main\res\raw\cinema_ambient.mp3")
import imageio_ffmpeg

ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
subprocess.run(
    [
        ffmpeg,
        "-y",
        "-hide_banner",
        "-f",
        "f32le",
        "-ar",
        str(SR),
        "-ac",
        "2",
        "-i",
        "pipe:0",
        "-c:a",
        "libmp3lame",
        "-b:a",
        "160k",
        str(target),
    ],
    input=mix.astype("<f4").tobytes(),
    check=True,
)
print("wrote", target, target.stat().st_size)

probe = subprocess.run(
    [ffmpeg, "-hide_banner", "-i", str(target), "-af", "volumedetect", "-f", "null", "-"],
    capture_output=True,
    text=True,
    encoding="utf-8",
    errors="replace",
)
for line in probe.stderr.splitlines():
    if "mean_volume" in line or "max_volume" in line:
        print(line)

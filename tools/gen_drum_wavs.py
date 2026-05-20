#!/usr/bin/env python3
"""Generate drum sample WAVs with multi-layered synthesis.

Each drum is built from: attack transient + body resonance(s) + noise/snare layer + tail.
Mono 16-bit 44.1 kHz output.
"""
import math
import os
import random
import struct
import sys
import wave

SR = 44100
OUTDIR = sys.argv[1] if len(sys.argv) > 1 else "."
random.seed(42)


def write_wav(name: str, samples):
    path = os.path.join(OUTDIR, name)
    # Apply linear fade-out over the last 20ms so the WAV ends at zero and
    # there's no click when SoundPool finishes playback.
    fade_n = min(len(samples), int(0.020 * SR))
    samples = list(samples)
    for i in range(fade_n):
        idx = len(samples) - fade_n + i
        samples[idx] *= 1.0 - (i / fade_n)
    # normalise to about -1 dBFS so we don't clip
    peak = max((abs(s) for s in samples), default=1.0) or 1.0
    gain = 0.95 / peak
    pcm = bytearray()
    for s in samples:
        v = s * gain
        # soft saturation for warmth
        v = math.tanh(v * 1.05)
        if v > 1.0:
            v = 1.0
        elif v < -1.0:
            v = -1.0
        pcm += struct.pack("<h", int(v * 32000))
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(bytes(pcm))


def env_ad(n: int, attack_ms: float, decay_tau_ms: float, sustain: float = 0.0, sustain_ms: float = 0.0):
    """Attack-decay-sustain envelope.

    attack_ms: linear rise time
    decay_tau_ms: exp decay time constant (after attack)
    sustain: floor level (0..1) the decay aims for
    sustain_ms: extra time held at sustain (with continued exp decay) — set 0 to disable
    """
    out = [0.0] * n
    a = max(1, int(attack_ms * SR / 1000))
    for i in range(min(a, n)):
        out[i] = i / a
    tau = max(1.0, decay_tau_ms * SR / 1000)
    for i in range(a, n):
        v = sustain + (1.0 - sustain) * math.exp(-(i - a) / tau)
        out[i] = v
    return out


def sine_fm(freq: float, n: int, fm_decay_ms: float = 0.0, fm_depth: float = 0.0):
    out = []
    phase = 0.0
    tau = max(1.0, fm_decay_ms * SR / 1000) if fm_decay_ms > 0 else 0.0
    for i in range(n):
        f = freq + (fm_depth * math.exp(-i / tau) if tau > 0 else 0.0)
        phase += 2 * math.pi * f / SR
        out.append(math.sin(phase))
    return out


def white_noise(n: int):
    return [random.uniform(-1, 1) for _ in range(n)]


def pink_noise(n: int):
    """Pink-ish noise via Voss-McCartney algorithm (3 rows)."""
    rows = [0.0, 0.0, 0.0]
    out = []
    for i in range(n):
        # update row k where i has trailing bit at position k
        k = 0
        tmp = i + 1
        while tmp & 1 == 0:
            k += 1
            tmp >>= 1
            if k >= len(rows):
                break
        if k < len(rows):
            rows[k] = random.uniform(-1, 1)
        out.append(sum(rows) / len(rows))
    return out


def bandpass(x, low_alpha: float, high_alpha: float):
    """Simple band-pass: lowpass(highpass(x))."""
    return lowpass(highpass(x, high_alpha), low_alpha)


def lowpass(x, alpha: float):
    """1-pole IIR: y[i] = y[i-1] + alpha*(x[i] - y[i-1])"""
    y = []
    prev = 0.0
    for v in x:
        prev = prev + alpha * (v - prev)
        y.append(prev)
    return y


def highpass(x, alpha: float):
    """1-pole high-pass."""
    y = []
    prev_x = 0.0
    prev_y = 0.0
    for v in x:
        cur = alpha * (prev_y + v - prev_x)
        y.append(cur)
        prev_x, prev_y = v, cur
    return y


def resonator(x, freq: float, q: float):
    """Resonant peak filter (biquad band-pass approximation)."""
    w0 = 2 * math.pi * freq / SR
    cos_w0 = math.cos(w0)
    sin_w0 = math.sin(w0)
    alpha = sin_w0 / (2 * q)
    b0 = alpha
    b1 = 0.0
    b2 = -alpha
    a0 = 1 + alpha
    a1 = -2 * cos_w0
    a2 = 1 - alpha
    # normalise
    b0 /= a0
    b1 /= a0
    b2 /= a0
    a1 /= a0
    a2 /= a0
    y = []
    x1 = x2 = 0.0
    y1 = y2 = 0.0
    for v in x:
        cur = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        y.append(cur)
        x2, x1 = x1, v
        y2, y1 = y1, cur
    return y


def mul(*lists):
    n = max(len(l) for l in lists)
    out = [1.0] * n
    for l in lists:
        for i in range(n):
            out[i] *= l[i] if i < len(l) else 0.0
    return out


def add(*lists):
    n = max(len(l) for l in lists)
    out = [0.0] * n
    for l in lists:
        for i, v in enumerate(l):
            out[i] += v
    return out


def scale(xs, k: float):
    return [v * k for v in xs]


# ─── KICK ──────────────────────────────────────────────────────────
def kick():
    """Punchy kick: instant click + pitched body sweep + long sub-bass tail."""
    n = int(0.55 * SR)
    body = sine_fm(45, n, fm_decay_ms=45, fm_depth=80)
    body = mul(body, env_ad(n, 0, 180))
    sub = sine_fm(40, n)
    sub = mul(sub, env_ad(n, 0, 280))
    click_n = int(0.005 * SR)
    click = mul(white_noise(click_n), env_ad(click_n, 0, 1.5))
    click = lowpass(click, 0.5)
    full = add(scale(body, 1.0), scale(sub, 0.45), scale(click, 0.8))
    return full


# ─── SNARE ─────────────────────────────────────────────────────────
def snare():
    """Layered: tone (200Hz) + snare wires (filtered noise) + body resonance.
    Long wire tail blends consecutive hits."""
    n = int(0.45 * SR)
    tone = sine_fm(200, n, fm_decay_ms=10, fm_depth=80)
    tone = mul(tone, env_ad(n, 0, 100))

    wires = pink_noise(n)
    wires = highpass(wires, 0.85)
    wires = add(scale(wires, 0.7), scale(resonator(wires, 4000, 5), 0.5))
    wires = mul(wires, env_ad(n, 0, 150))

    body = white_noise(n)
    body = resonator(body, 180, 8)
    body = mul(body, env_ad(n, 0, 60))

    return add(scale(tone, 0.6), scale(wires, 0.9), scale(body, 0.4))


# ─── HI-HAT (closed) ───────────────────────────────────────────────
def hihat_closed():
    """Bright. Long enough to fully blend across 16th-notes at 60-150 BPM."""
    n = int(0.28 * SR)
    base = white_noise(n)
    base = highpass(base, 0.92)
    shimmer = add(
        scale(resonator(base, 8000, 30), 0.5),
        scale(resonator(base, 12000, 25), 0.4),
        scale(resonator(base, 6000, 20), 0.3),
    )
    out = add(scale(base, 0.5), scale(shimmer, 1.0))
    return mul(out, env_ad(n, 0, 110))


# ─── HI-HAT (open) ─────────────────────────────────────────────────
def hihat_open():
    """Longer decay, same flavour."""
    n = int(0.40 * SR)
    base = white_noise(n)
    base = highpass(base, 0.92)
    shimmer = add(
        scale(resonator(base, 8000, 30), 0.5),
        scale(resonator(base, 12000, 25), 0.4),
        scale(resonator(base, 6000, 20), 0.3),
    )
    out = add(scale(base, 0.5), scale(shimmer, 1.0))
    return mul(out, env_ad(n, 1, 150))


# ─── CRASH CYMBAL ──────────────────────────────────────────────────
def crash():
    """Wide-spectrum noise with multiple resonant peaks + long decay."""
    n = int(1.6 * SR)
    base = white_noise(n)
    base = highpass(base, 0.6)
    shimmer = add(
        scale(resonator(base, 3500, 8), 0.7),
        scale(resonator(base, 5500, 10), 0.6),
        scale(resonator(base, 8000, 14), 0.5),
        scale(resonator(base, 11000, 16), 0.4),
    )
    out = add(scale(base, 0.4), scale(shimmer, 1.0))
    env_short = env_ad(n, 1, 80)
    env_long = env_ad(n, 1, 600)
    env = [0.6 * a + 0.5 * b for a, b in zip(env_short, env_long)]
    return mul(out, env)


# ─── RIDE CYMBAL ───────────────────────────────────────────────────
def ride():
    """Pingy bell + ring."""
    n = int(0.9 * SR)
    bell_tone = sine_fm(2400, n)
    bell_partial = sine_fm(3300, n)
    bell_env = env_ad(n, 1, 350)
    bell = add(scale(bell_tone, 0.6), scale(bell_partial, 0.4))
    bell = mul(bell, bell_env)

    base = white_noise(n)
    base = highpass(base, 0.85)
    shimmer = add(
        scale(resonator(base, 5000, 15), 0.6),
        scale(resonator(base, 8000, 20), 0.5),
    )
    ring = add(scale(base, 0.3), scale(shimmer, 1.0))
    ring = mul(ring, env_ad(n, 1, 250))

    return add(scale(bell, 1.0), scale(ring, 0.5))


# ─── TOMS ──────────────────────────────────────────────────────────
def tom(freq: float):
    """Pitched body with fast pitch envelope + long resonant tail."""
    n = int(0.60 * SR)
    body = sine_fm(freq, n, fm_decay_ms=30, fm_depth=freq * 0.5)
    body = mul(body, env_ad(n, 0, 180))
    overtone = sine_fm(freq * 1.5, n)
    overtone = mul(overtone, env_ad(n, 0, 100))
    click_n = int(0.005 * SR)
    click = mul(white_noise(click_n), env_ad(click_n, 0, 1.5))
    click = bandpass(click, 0.4, 0.5)
    return add(scale(body, 1.0), scale(overtone, 0.25), scale(click, 0.3))


# ─── CLICKS ────────────────────────────────────────────────────────
def click(freq: float):
    """Tight wood-block-ish click."""
    n = int(0.025 * SR)
    body = sine_fm(freq, n)
    body = mul(body, env_ad(n, 0.2, 4))
    noise = white_noise(int(0.003 * SR))
    noise = lowpass(noise, 0.3)
    return add(scale(body, 0.7), scale(noise, 0.3))


os.makedirs(OUTDIR, exist_ok=True)
write_wav("kick.wav", kick())
write_wav("snare.wav", snare())
write_wav("hihat_closed.wav", hihat_closed())
write_wav("hihat_open.wav", hihat_open())
write_wav("crash.wav", crash())
write_wav("ride.wav", ride())
write_wav("tom_hi.wav", tom(260))
write_wav("tom_mid.wav", tom(170))
write_wav("tom_floor.wav", tom(95))
write_wav("click_high.wav", click(1800))
write_wav("click_low.wav", click(900))
print("Wrote 11 WAVs to", OUTDIR)

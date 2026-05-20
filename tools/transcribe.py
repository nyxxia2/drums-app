#!/usr/bin/env python3
"""
Heuristic drum transcription from a YouTube URL.

Usage:
    python3 transcribe.py "https://www.youtube.com/watch?v=hTWKbfoikeg"
    python3 transcribe.py "https://www.youtube.com/watch?v=hTWKbfoikeg" --bars 16

Output:
    A Kotlin Song(...) snippet on stdout that you can paste into
    app/src/main/java/ph/nextbank/drums/data/samples/SampleSongs.kt

How it works (best-effort, not perfect):
    1. yt-dlp downloads the audio.
    2. librosa.beat.beat_track detects tempo + beat times.
    3. librosa.onset.onset_detect finds drum-hit candidates.
    4. Each onset's frequency content is classified:
         <200 Hz dominant → kick
         200-1000 Hz dominant with broadband transient → snare
         >4000 Hz dominant → hi-hat closed
    5. Hits are quantised to the nearest 16th-note slot relative to the
       detected tempo and a chosen N-bar window from the loudest section
       of the song.
    6. Bar patterns are formatted as Kotlin DrumToken lists.

Limitations:
    - Snare / kick classification is shaky on songs with lots of overlap.
    - Cymbals and toms are not detected (treated as hi-hat).
    - Tempo detection wobbles on songs with prominent intros / breakdowns.
    - This produces a *starting point* — manually clean up the output.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

try:
    import numpy as np
    import librosa
    import soundfile  # noqa: F401  (librosa lazy-imports it)
except ImportError as e:
    print(f"Missing dep: {e}. Install with:", file=sys.stderr)
    print("    python3 -m pip install --user yt-dlp librosa numpy soundfile", file=sys.stderr)
    sys.exit(1)


# ───────── 1. YouTube → wav ─────────────────────────────────────────────
def download_audio(url: str, outdir: Path) -> Path:
    """Use yt-dlp to fetch the audio track as a 22kHz mono WAV."""
    out_template = str(outdir / "audio.%(ext)s")
    cmd = [
        "yt-dlp",
        "--no-playlist",
        "--no-warnings",
        "-q",
        "-x",
        "--audio-format", "wav",
        "--postprocessor-args", "ffmpeg:-ar 22050 -ac 1",
        "-o", out_template,
        url,
    ]
    try:
        subprocess.run(cmd, check=True)
    except FileNotFoundError:
        sys.exit("yt-dlp not on PATH. Install: python3 -m pip install --user yt-dlp")
    wav = outdir / "audio.wav"
    if not wav.exists():
        sys.exit(f"yt-dlp ran but no audio.wav at {wav}")
    return wav


# ───────── 2. Tempo + beat grid ─────────────────────────────────────────
def detect_tempo_and_beats(y: np.ndarray, sr: int) -> tuple[float, np.ndarray]:
    tempo, beat_frames = librosa.beat.beat_track(y=y, sr=sr, units="frames")
    if hasattr(tempo, "__len__"):
        tempo = float(tempo[0])
    beat_times = librosa.frames_to_time(beat_frames, sr=sr)
    return float(tempo), beat_times


# ───────── 3. Onsets per frequency band (multi-label) ──────────────────
def detect_hits(y: np.ndarray, sr: int) -> list[tuple[float, str]]:
    """Per-band onset detection. A single moment in time can emit multiple
    drum labels (e.g. kick + hi-hat hitting together).

    Returns a sorted list of (time_sec, label) with label ∈ {k, s, h}.
    """
    hop = 512

    # HPSS: separate the percussive component from the harmonic one (vocals,
    # bass, guitar). Onset detection on y_p alone reduces false kick detections
    # from sustained bass-guitar notes and tonal guitar strums leaking into
    # the lower bands.
    y_h, y_p = librosa.effects.hpss(y, margin=2.0)

    # librosa.onset.onset_strength_multi gives one onset-strength envelope per
    # mel-band group. Using 64 mel bands at 22.05 kHz:
    #   slice(0, 6)   ≈   0 –  250 Hz  → kick
    #   slice(10, 40) ≈ 350 –  2.5 kHz → snare body
    #   slice(42, 64) ≈   3 – 11   kHz → hi-hat / cymbals
    onset_envs = librosa.onset.onset_strength_multi(
        y=y_p,
        sr=sr,
        hop_length=hop,
        n_mels=64,
        channels=[slice(0, 6), slice(10, 40), slice(42, 64)],
    )

    # Per-band peak-pick parameters tuned for typical rock/pop drum density.
    # delta = required prominence above local mean; bigger = fewer false peaks.
    # wait = min frames between two detected peaks in the same band.
    BAND_PARAMS = [
        # (label, pre_max, post_max, pre_avg, post_avg, delta, wait_frames)
        ("k", 5, 5, 15, 15, 0.50, 10),  # ~2-4 hits/s
        ("s", 5, 5, 15, 15, 0.65, 10),  # ~2/bar typical, don't double-fire
        ("h", 3, 3,  5,  5, 0.22,  3),  # every 8th or 16th — lower delta
    ]

    band_hits: list[list[tuple[float, float]]] = []  # per-band [(time, strength), ...]
    for i, (label, pre_max, post_max, pre_avg, post_avg, delta, wait) in enumerate(BAND_PARAMS):
        peaks = librosa.util.peak_pick(
            onset_envs[i],
            pre_max=pre_max,
            post_max=post_max,
            pre_avg=pre_avg,
            post_avg=post_avg,
            delta=delta,
            wait=wait,
        )
        times = librosa.frames_to_time(peaks, sr=sr, hop_length=hop)
        strengths = onset_envs[i][peaks]
        band_hits.append([(float(t), float(s)) for t, s in zip(times, strengths)])

    # Suppress weak bystander detections: if a kick and a snare fire within
    # 30ms of each other, the broadband transient of one drum often triggers
    # the other's band. Keep only the band whose detection is significantly
    # stronger (≥ 1.3× the other's mean strength).
    K_HITS = band_hits[0]
    S_HITS = band_hits[1]
    H_HITS = band_hits[2]
    TOL = 0.030  # 30 ms

    def suppress(a: list[tuple[float, float]], b: list[tuple[float, float]]) -> list[tuple[float, float]]:
        """Remove peaks in `a` that have a stronger neighbour in `b` within TOL."""
        if not b:
            return a
        b_arr = np.array([(t, s) for t, s in b])
        out = []
        for t, s in a:
            mask = np.abs(b_arr[:, 0] - t) <= TOL
            if mask.any():
                neighbour_max = float(b_arr[mask, 1].max())
                if neighbour_max > 1.3 * s:
                    continue   # this peak is the bystander
            out.append((t, s))
        return out

    K_HITS = suppress(K_HITS, S_HITS)        # weak kicks shadowed by snare
    S_HITS = suppress(S_HITS, band_hits[0])  # weak snares shadowed by kick
    # Don't suppress hi-hats — they legitimately ride on top of k/s.

    hits: list[tuple[float, str]] = []
    for t, _ in K_HITS: hits.append((t, "k"))
    for t, _ in S_HITS: hits.append((t, "s"))
    for t, _ in H_HITS: hits.append((t, "h"))
    hits.sort()
    return hits


# ───────── 4. Pick a stable N-bar window ───────────────────────────────
def pick_window(
    beat_times: np.ndarray,
    hits: list[tuple[float, str]],
    bars: int,
) -> tuple[float, float]:
    """Return (start_sec, end_sec) of the N-bar window with the most drum
    activity. Picks by raw onset count rather than RMS, so we land on a
    section where the drum pattern is actually playing (not just a loud
    guitar/vocal moment with no drums).
    """
    beats_per_bar = 4
    if len(beat_times) < bars * beats_per_bar + 1:
        return float(beat_times[0]), float(beat_times[-1])

    hit_times = np.array([t for t, _ in hits])
    window_beats = bars * beats_per_bar

    best_i = 0
    best_count = -1
    for i in range(len(beat_times) - window_beats):
        t0 = beat_times[i]
        t1 = beat_times[i + window_beats]
        count = int(((hit_times >= t0) & (hit_times < t1)).sum())
        if count > best_count:
            best_count = count
            best_i = i
    return float(beat_times[best_i]), float(beat_times[best_i + window_beats])


# ───────── 5. Quantise hits to 16th-note slots ─────────────────────────
def quantise_to_grid(
    hits: list[tuple[float, str]],
    window_start: float,
    window_end: float,
    tempo: float,
    bars: int,
) -> list[list[list[str]]]:
    """Place hits onto a bars×16 grid. Returns nested lists of drum codes."""
    slots_per_bar = 16
    total_slots = bars * slots_per_bar
    bar_duration = (60.0 / tempo) * 4  # 4 beats per bar
    slot_duration = bar_duration / slots_per_bar

    grid: list[list[list[str]]] = [
        [[] for _ in range(slots_per_bar)] for _ in range(bars)
    ]
    for t, label in hits:
        if t < window_start or t >= window_end:
            continue
        rel = t - window_start
        slot_idx = int(round(rel / slot_duration))
        if 0 <= slot_idx < total_slots:
            bar = slot_idx // slots_per_bar
            slot_in_bar = slot_idx % slots_per_bar
            if label not in grid[bar][slot_in_bar]:
                grid[bar][slot_in_bar].append(label)
    return grid


# ───────── 6. Format as Kotlin Song(...) snippet ───────────────────────
CODE_TO_KOTLIN = {"k": "KICK", "s": "SNARE", "h": "HIHAT_CLOSED"}


def format_kotlin(
    grid: list[list[list[str]]],
    title: str,
    artist: str,
    bpm: int,
    cover_initials: str,
    video_id: str | None,
) -> str:
    lines: list[str] = []
    lines.append("private val IMPORTED_BARS = listOf(")
    for bar in grid:
        line_parts = []
        for slot in bar:
            if not slot:
                line_parts.append("e")
            elif len(slot) == 1:
                line_parts.append(f"hit({CODE_TO_KOTLIN[slot[0]]})")
            else:
                tokens = ", ".join(CODE_TO_KOTLIN[c] for c in slot)
                line_parts.append(f"hit({tokens})")
        lines.append("    " + ", ".join(line_parts) + ",")
    lines.append(")")
    lines.append("")
    lines.append("Song(")
    safe_id = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")
    lines.append(f'    id = "{safe_id}",')
    lines.append(f'    title = "{title}",')
    lines.append(f'    artist = "{artist}",')
    lines.append(f"    bpm = {bpm},")
    lines.append("    timeSig = 4 to 4,")
    lines.append("    bars = IMPORTED_BARS,")
    lines.append(f'    coverInitials = "{cover_initials}",')
    lines.append("    importedFrom = ImportSource.BUNDLED,")
    lines.append("    lastPlayed = null,")
    if video_id:
        lines.append(f'    youtubeVideoId = "{video_id}",')
    lines.append("),")
    return "\n".join(lines)


def extract_video_id(url: str) -> str | None:
    m = re.search(r"(?:v=|youtu\.be/|/embed/)([A-Za-z0-9_-]{11})", url)
    return m.group(1) if m else None


# ───────── Main ─────────────────────────────────────────────────────────
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    ap.add_argument("url", help="YouTube URL")
    ap.add_argument("--title", help="Song title (default: prompt)", default=None)
    ap.add_argument("--artist", help="Artist (default: prompt)", default=None)
    ap.add_argument("--bars", type=int, default=8, help="Number of bars to extract (default 8)")
    ap.add_argument("--initials", help="Cover initials, 2 chars", default=None)
    args = ap.parse_args()

    title = args.title or input("Title: ").strip() or "Untitled"
    artist = args.artist or input("Artist: ").strip() or "Unknown"
    initials = args.initials or (artist[:2].upper() if artist else "??")

    with tempfile.TemporaryDirectory() as td:
        td_path = Path(td)
        print(f"[1/5] Downloading audio from {args.url} ...", file=sys.stderr)
        wav_path = download_audio(args.url, td_path)
        print(f"[2/5] Loading {wav_path.name} ...", file=sys.stderr)
        y, sr = librosa.load(str(wav_path), sr=22050, mono=True)
        print(f"      {len(y)/sr:.1f}s @ {sr} Hz", file=sys.stderr)

        print("[3/5] Detecting tempo + beat grid ...", file=sys.stderr)
        tempo, beat_times = detect_tempo_and_beats(y, sr)
        bpm = int(round(tempo))
        print(f"      tempo ≈ {bpm} BPM, {len(beat_times)} beats", file=sys.stderr)

        print("[4/5] Per-band onset detection (k / s / h) ...", file=sys.stderr)
        hits = detect_hits(y, sr)
        print(f"      {len(hits)} hits detected ({sum(1 for _, l in hits if l == 'k')} k, "
              f"{sum(1 for _, l in hits if l == 's')} s, "
              f"{sum(1 for _, l in hits if l == 'h')} h)", file=sys.stderr)

        print(f"[5/5] Picking densest {args.bars}-bar window + quantising ...", file=sys.stderr)
        w_start, w_end = pick_window(beat_times, hits, args.bars)
        print(f"      window: {w_start:.1f}s → {w_end:.1f}s", file=sys.stderr)
        grid = quantise_to_grid(hits, w_start, w_end, tempo, args.bars)

    snippet = format_kotlin(
        grid=grid,
        title=title,
        artist=artist,
        bpm=bpm,
        cover_initials=initials,
        video_id=extract_video_id(args.url),
    )
    print(snippet)
    return 0


if __name__ == "__main__":
    sys.exit(main())

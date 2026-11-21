#!/usr/bin/env python3
"""
Génère par synthèse tous les sons de l'application (aucun enregistrement externe, donc libres de droits).

Usage :  python3 tools/make_sounds.py app/src/main/res/raw
Dépendances : numpy, scipy, ffmpeg (encodeur libvorbis).

Les fichiers produits peuvent être remplacés par de vrais enregistrements : il suffit de garder les mêmes noms.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 32000
rng = np.random.default_rng(7)


# ----------------------------------------------------------------------------- outils

def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def bandpass(x, lo, hi, order=2):
    sos = signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def lowpass(x, f, order=2):
    sos = signal.butter(order, f, btype="low", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, f, order=2):
    sos = signal.butter(order, f, btype="high", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def env_ad(n, attack, decay_tau):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-np.maximum(t - attack, 0) / decay_tau)


def reverb(x, size=1.2, mix=0.25, damp=3000):
    n = int(size * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / SR * (6.9 / size))
    ir = lowpass(ir, damp)
    ir /= np.sqrt(np.sum(ir ** 2))
    wet = signal.fftconvolve(x, ir)[: len(x)]
    return (1 - mix) * x + mix * wet


def place(buf, snd, start):
    i = int(start * SR)
    if i >= len(buf):
        return
    end = min(len(buf), i + len(snd))
    buf[i:end] += snd[: end - i]


def normalize(x, peak=0.8):
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


def fade(x, fin=0.003, fout=0.02):
    x = x.copy()
    a, b = int(fin * SR), int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def save(path, x, peak=0.8, quality=4):
    x = normalize(x, peak)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        tmp = f.name
    wavfile.write(tmp, SR, (x * 32767).astype(np.int16))
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", str(quality), path],
        check=True,
    )
    os.remove(tmp)
    print(f"  {os.path.basename(path):24s} {len(x) / SR:5.2f}s  {os.path.getsize(path) // 1024} Ko")


# ----------------------------------------------------------------------------- briques sonores

def wood_knock(f0=780, dur=0.25, bright=1.0):
    """Petit coup sur du bois : quelques modes amortis + un clic filtré."""
    t = t_axis(dur)
    ratios = [1.0, 1.83, 2.95, 4.1]
    taus = [0.045, 0.028, 0.016, 0.009]
    amps = [1.0, 0.55 * bright, 0.3 * bright, 0.15 * bright]
    x = sum(a * np.sin(2 * np.pi * f0 * r * t + rng.uniform(0, 6)) * np.exp(-t / tau)
            for r, tau, a in zip(ratios, taus, amps))
    click = bandpass(rng.standard_normal(len(t)), 1500, 6000) * np.exp(-t / 0.004) * 0.6
    return fade(x + click, 0.0005)


def marimba(f, dur=1.6, amp=1.0):
    t = t_axis(dur)
    x = (np.sin(2 * np.pi * f * t) * np.exp(-t / 0.55)
         + 0.25 * np.sin(2 * np.pi * f * 3.93 * t) * np.exp(-t / 0.12)
         + 0.08 * np.sin(2 * np.pi * f * 9.2 * t) * np.exp(-t / 0.03))
    x *= np.clip(t / 0.003, 0, 1)
    return fade(x * amp, 0.0005, 0.2)


def chime(f, dur=3.0, amp=1.0):
    t = t_axis(dur)
    partials = [(1.0, 1.0, 1.6), (2.76, 0.4, 0.9), (5.4, 0.2, 0.5), (8.9, 0.08, 0.25)]
    x = sum(a * np.sin(2 * np.pi * f * r * t) * np.exp(-t / tau) for r, a, tau in partials)
    x *= np.clip(t / 0.002, 0, 1)
    return fade(x * amp, 0.0005, 0.3)


def rustle(dur=0.5, density=900, lo=1800, hi=7000):
    """Froissement de feuilles : micro-craquements de bruit filtré, enveloppe en cloche."""
    n = int(dur * SR)
    x = np.zeros(n)
    count = int(density * dur)
    shape = np.sin(np.linspace(0, np.pi, n)) ** 1.5
    for _ in range(count):
        pos = rng.integers(0, n - 200)
        if rng.random() > shape[pos]:
            continue
        ln = rng.integers(20, 160)
        g = rng.standard_normal(ln) * np.exp(-np.arange(ln) / (ln / 4)) * rng.uniform(0.2, 1.0)
        x[pos:pos + ln] += g
    x = bandpass(x, lo, hi)
    x += 0.15 * bandpass(rng.standard_normal(n), 2500, 6000) * shape
    return fade(x, 0.005, 0.05)


def bird_phrase(base=3200, notes=4):
    """Petite phrase d'oiseau : glissandos rapides avec un peu de vibrato."""
    parts = []
    for _ in range(notes):
        d = rng.uniform(0.06, 0.16)
        t = t_axis(d)
        f_start = base * rng.uniform(0.85, 1.3)
        f_end = f_start * rng.uniform(0.6, 1.25)
        f = np.linspace(f_start, f_end, len(t)) + 120 * np.sin(2 * np.pi * rng.uniform(25, 45) * t)
        ph = 2 * np.pi * np.cumsum(f) / SR
        e = np.sin(np.linspace(0, np.pi, len(t))) ** 2
        parts.append(np.sin(ph) * e * rng.uniform(0.6, 1.0))
        parts.append(np.zeros(int(rng.uniform(0.03, 0.12) * SR)))
    return np.concatenate(parts)


def dove_call():
    """Roucoulement doux et lointain (tourterelle)."""
    out = []
    for f, d in [(520, 0.35), (600, 0.55), (520, 0.3), (480, 0.6)]:
        t = t_axis(d)
        ff = f * (1 + 0.04 * np.sin(np.pi * t / d))
        ph = 2 * np.pi * np.cumsum(ff) / SR
        e = np.sin(np.linspace(0, np.pi, len(t))) ** 1.5
        out.append((np.sin(ph) + 0.25 * np.sin(2 * ph)) * e)
        out.append(np.zeros(int(0.12 * SR)))
    return lowpass(np.concatenate(out), 1800)


def owl_call():
    out = []
    for f, d, gap in [(390, 0.45, 0.5), (370, 0.25, 0.12), (380, 0.9, 0.0)]:
        t = t_axis(d)
        ff = f * (1 - 0.06 * t / d)
        ph = 2 * np.pi * np.cumsum(ff) / SR
        e = np.sin(np.linspace(0, np.pi, len(t))) ** 1.2
        out.append((np.sin(ph) + 0.15 * np.sin(2 * ph)) * e)
        out.append(np.zeros(int(gap * SR)))
    return lowpass(np.concatenate(out), 1200)


def make_loop(render, dur, xfade=4.0):
    """Rend dur+xfade secondes puis fond la fin dans le début : boucle sans couture."""
    x = render(dur + xfade)
    n, m = int(dur * SR), int(xfade * SR)
    head, tail = x[:m].copy(), x[n:n + m]
    w = np.linspace(0, 1, m)
    out = x[:n].copy()
    out[:m] = head * np.sqrt(w) + tail * np.sqrt(1 - w)
    return out


def wind(total, strength=1.0):
    n = int(total * SR)
    t = np.arange(n) / SR
    pink = np.cumsum(rng.standard_normal(n))
    pink = highpass(pink, 40)
    gust = 0.55 + 0.45 * np.sin(2 * np.pi * t / 13.0 + 1) * np.sin(2 * np.pi * t / 7.3)
    leaves = bandpass(rng.standard_normal(n), 2500, 6500) * np.clip(gust - 0.55, 0, 1) ** 2 * 0.6
    w = lowpass(pink, 500) * gust
    w = normalize(w, 1.0) * 0.5 + normalize(leaves, 1.0) * 0.12
    return w * strength


def stream(total):
    n = int(total * SR)
    base = bandpass(rng.standard_normal(n), 300, 1800) * 0.3
    for _ in range(int(total * 22)):
        d = rng.uniform(0.01, 0.04)
        t = t_axis(d)
        f0 = rng.uniform(500, 1400)
        f = f0 * (1 + 1.5 * t / d)
        blip = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.linspace(0, np.pi, len(t)))
        place(base, blip * rng.uniform(0.05, 0.2), rng.uniform(0, total - d))
    return lowpass(base, 2500)


# ----------------------------------------------------------------------------- sons finaux

def build(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    p = lambda name: os.path.join(out_dir, name)

    print("Effets :")
    save(p("sfx_tap.ogg"), wood_knock(950, 0.18, bright=0.5), peak=0.5)
    save(p("sfx_select.ogg"), wood_knock(820, 0.22), peak=0.65)
    save(p("sfx_deselect.ogg"), wood_knock(600, 0.2, bright=0.4), peak=0.5)

    # Bâtons qui retombent : trois petits chocs de bois
    x = np.zeros(int(0.7 * SR))
    for i, (d, f, a) in enumerate([(0.0, 700, 1.0), (0.11, 820, 0.6), (0.19, 640, 0.4), (0.26, 900, 0.25)]):
        place(x, wood_knock(f, 0.25) * a, d)
    save(p("sfx_remove_wood.ogg"), reverb(x, 0.6, 0.2), peak=0.7)

    save(p("sfx_remove_leaf.ogg"), reverb(rustle(0.6), 0.5, 0.15), peak=0.6)

    # Galets : petits « clic » minéraux
    x = np.zeros(int(0.5 * SR))
    for d, f, a in [(0.0, 2300, 1.0), (0.07, 2900, 0.5), (0.12, 2600, 0.25)]:
        t = t_axis(0.12)
        s = sum(np.sin(2 * np.pi * f * r * t) * np.exp(-t / tau) * g
                for r, tau, g in [(1, 0.02, 1), (1.7, 0.012, 0.5), (2.6, 0.006, 0.3)])
        thump = np.sin(2 * np.pi * 160 * t) * np.exp(-t / 0.02) * 0.4
        place(x, (s + thump) * a, d)
    save(p("sfx_remove_stone.ogg"), reverb(x, 0.5, 0.15), peak=0.6)

    # Pop mousseux (glands, champignons)
    t = t_axis(0.3)
    f = 260 * np.exp(-t / 0.08) + 110
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_ad(len(t), 0.004, 0.07)
    s += lowpass(rng.standard_normal(len(t)), 1200) * env_ad(len(t), 0.001, 0.02) * 0.3
    x = np.zeros(int(0.5 * SR))
    place(x, s, 0)
    place(x, s * 0.4, 0.09)
    save(p("sfx_remove_soft.ogg"), reverb(x, 0.4, 0.15), peak=0.7)

    # Partage (Grundy) : petit glissement de mousse
    x = rustle(0.35, density=500, lo=600, hi=3000)
    place(x, wood_knock(520, 0.2, 0.3) * 0.5, 0.25 - 0.0)
    save(p("sfx_split.ogg"), reverb(x, 0.4, 0.2), peak=0.6)

    # Refus : coup sourd et doux
    t = t_axis(0.35)
    s = np.sin(2 * np.pi * 120 * t) * env_ad(len(t), 0.005, 0.08)
    s += np.sin(2 * np.pi * 95 * t) * env_ad(len(t), 0.06, 0.09) * 0.6
    save(p("sfx_invalid.ogg"), fade(s), peak=0.5)

    # Changement de tour : une note de marimba très douce
    save(p("sfx_turn.ogg"), reverb(marimba(587.33, 1.2), 1.0, 0.25), peak=0.35)

    # Victoire : arpège pentatonique de marimba + carillon
    notes = [392.0, 440.0, 523.25, 587.33, 659.25, 783.99]
    x = np.zeros(int(3.8 * SR))
    for i, f in enumerate(notes):
        place(x, marimba(f, 1.8, 0.9), i * 0.13)
    for f in [523.25, 659.25, 783.99]:
        place(x, marimba(f, 2.2, 0.6), 0.95)
    for i, f in enumerate([1567.98, 2093.0, 1760.0, 2349.3]):
        place(x, chime(f, 2.5, 0.12), 1.0 + i * 0.21)
    save(p("sfx_win.ogg"), reverb(x, 1.8, 0.3), peak=0.75)

    print("Ambiances :")

    def day(total):
        n = int(total * SR)
        mix = wind(total, 0.8) + normalize(stream(total), 0.18)
        birds = np.zeros(n)
        tt = 1.5
        while tt < total - 3:
            if rng.random() < 0.75:
                ph = bird_phrase(base=rng.uniform(2600, 4200), notes=rng.integers(3, 7))
                place(birds, ph * rng.uniform(0.25, 0.6), tt)
            else:
                place(birds, dove_call() * 0.5, tt)
            tt += rng.uniform(3.5, 8.0)
        birds = reverb(birds, 1.6, 0.4, damp=5000)
        return mix + birds * 0.22

    save(p("ambience_day.ogg"), make_loop(day, 48.0), peak=0.6, quality=3)

    def night(total):
        n = int(total * SR)
        t = np.arange(n) / SR
        mix = wind(total, 0.6)
        # Grillons : trains d'impulsions modulées
        crick = np.zeros(n)
        for f, rate, phase in [(4300, 0.9, 0.0), (4700, 1.15, 0.4), (3900, 0.7, 0.8)]:
            carrier = np.sin(2 * np.pi * f * t)
            pulses = (np.sin(2 * np.pi * 30 * t) > 0.3).astype(float)
            pulses = lowpass(pulses, 200)
            chirp_env = (np.sin(2 * np.pi * rate * t + phase * 6) > 0.2).astype(float)
            chirp_env = lowpass(chirp_env, 20)
            crick += carrier * pulses * chirp_env * 0.06
        mix += reverb(crick, 0.8, 0.3)
        tt = 6.0
        while tt < total - 4:
            place(mix, reverb(owl_call(), 2.0, 0.5) * 0.12, tt)
            tt += rng.uniform(14, 22)
        return mix

    save(p("ambience_night.ogg"), make_loop(night, 48.0), peak=0.55, quality=3)


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw")

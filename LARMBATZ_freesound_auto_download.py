#!/usr/bin/env python3
"""Import local WAVs and download/categorize Freesound loops through official API v2.

No scraping is performed. Search uses an API key; downloads use an OAuth2
access token. CC0 is the default. CC BY is only included with --allow-cc-by.
Loops are analyzed dry, then EQ/compressor are applied to the library copy by
default before it is stored by category/BPM/key. Local source files are copied,
not modified. Use --effects none to keep the audio unprocessed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import shutil
import sqlite3
import ssl
import struct
import sys
import tempfile
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

API_ROOT = "https://freesound.org/apiv2"
SEARCH_URL = f"{API_ROOT}/search/"
DOWNLOAD_URL = f"{API_ROOT}/sounds/{{sound_id}}/download/"
API_HOST = "freesound.org"
CC0 = "Creative Commons 0"
CC_BY = "Attribution"
MAX_FILE_BYTES = 32 * 1024 * 1024
DEFAULT_LIBRARY_BYTES = 256 * 1024 * 1024
MAX_JSON_BYTES = 4 * 1024 * 1024
TIMEOUT_SECONDS = 30
MAX_REDIRECTS = 5
USER_AGENT = "LaermbatzLoopLibrary/1.0 (official Freesound API client)"

# Category directory, query, and cues mirror the Android app's six mixer roles.
CATEGORIES: dict[str, dict[str, Any]] = {
    "KICK": {
        "label": "Kick",
        "query": "techno kick loop",
        "cues": {"kick", "kickdrum", "bassdrum"},
    },
    "BASSLINE": {
        "label": "Bassline",
        "query": "techno bassline loop",
        "cues": {"bass", "bassline", "subbass"},
    },
    "HI_HAT": {
        "label": "Hi-Hat",
        "query": "techno hi hat loop",
        "cues": {"hihat", "hat"},
    },
    "PERCUSSION": {
        "label": "Percussion",
        "query": "techno percussion loop",
        "cues": {"percussion", "perc", "shaker", "clap", "tom", "schlagzeug", "trommel"},
    },
    "SYNTH": {
        "label": "Synth",
        "query": "techno synth loop",
        "cues": {"synth", "synthesizer", "lead", "pad", "rave", "stab"},
    },
    "EFFECTS": {
        "label": "Effekte",
        "query": "techno sound effect loop",
        "cues": {"fx", "effect", "effects", "impact", "riser", "noise", "effekt", "effekte"},
    },
}

TOKEN_RE = re.compile(r"[\W_]+", re.UNICODE)
WHITESPACE_RE = re.compile(r"\s+")


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Disable urllib's automatic redirect handling; downloads handle redirects safely."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # type: ignore[override]
        return None


TLS_CONTEXT = ssl.create_default_context()
OPENER = urllib.request.build_opener(
    NoRedirectHandler(),
    urllib.request.HTTPSHandler(context=TLS_CONTEXT),
)


def normalize_words(text: str) -> set[str]:
    text = unicodedata.normalize("NFKD", text.casefold())
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return {word for word in TOKEN_RE.split(text) if word}


def infer_category(title: str, tags: list[str] | str = "", filename: str = "") -> str | None:
    """Infer category from title/tags/filename, with bass and hats before broad cues."""
    tag_text = " ".join(tags) if isinstance(tags, list) else tags
    combined = f"{title} {tag_text} {filename}"
    words = normalize_words(combined)
    padded = f" {' '.join(words)} "
    if words & CATEGORIES["BASSLINE"]["cues"] or " low end " in padded:
        return "BASSLINE"
    if words & CATEGORIES["HI_HAT"]["cues"] or " open hat " in padded:
        return "HI_HAT"
    if words & CATEGORIES["KICK"]["cues"]:
        return "KICK"
    if words & CATEGORIES["SYNTH"]["cues"]:
        return "SYNTH"
    if words & CATEGORIES["PERCUSSION"]["cues"]:
        return "PERCUSSION"
    if words & CATEGORIES["EFFECTS"]["cues"]:
        return "EFFECTS"
    return None


def category_key_from_value(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    if value in CATEGORIES or value == "UNCATEGORIZED":
        return value
    normalized = value.strip().casefold()
    for key, definition in CATEGORIES.items():
        if normalized == definition["label"].casefold():
            return key
    if normalized in {"uncategorized", "nicht einsortiert", "unknown"}:
        return "UNCATEGORIZED"
    return None


def require_https_url(url: str) -> urllib.parse.ParseResult:
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme.lower() != "https" or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError("Nur HTTPS-URLs ohne eingebettete Zugangsdaten sind erlaubt.")
    return parsed


def retry_delay(error: urllib.error.HTTPError, attempt: int) -> float:
    raw = error.headers.get("Retry-After", "") if error.headers else ""
    try:
        return min(60.0, max(1.0, float(raw)))
    except (TypeError, ValueError):
        return min(30.0, 2.0 ** attempt)


def request_json(url: str, api_key: str) -> dict[str, Any]:
    """Call one official API endpoint; never follow a search redirect automatically."""
    parsed = require_https_url(url)
    if parsed.hostname.lower() != API_HOST:
        raise ValueError("API-Anfragen sind nur an freesound.org erlaubt.")
    request = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Token {api_key}",
            "Accept": "application/json",
            "User-Agent": USER_AGENT,
        },
        method="GET",
    )
    for attempt in range(4):
        try:
            with OPENER.open(request, timeout=TIMEOUT_SECONDS) as response:
                body = response.read(MAX_JSON_BYTES + 1)
                if len(body) > MAX_JSON_BYTES:
                    raise RuntimeError("Freesound-Antwort überschreitet das 4-MiB-Limit.")
                value = json.loads(body.decode("utf-8"))
                if not isinstance(value, dict):
                    raise RuntimeError("Unerwartetes Freesound-JSON-Format.")
                return value
        except urllib.error.HTTPError as error:
            try:
                if error.code in {429, 500, 502, 503, 504} and attempt < 3:
                    time.sleep(retry_delay(error, attempt))
                    continue
                raise RuntimeError(f"Freesound-Suche fehlgeschlagen (HTTP {error.code}).") from None
            finally:
                error.close()
        except (urllib.error.URLError, TimeoutError) as error:
            if attempt < 3:
                time.sleep(min(30.0, 2.0 ** attempt))
                continue
            raise RuntimeError(f"Netzwerkfehler bei Freesound: {error.reason if isinstance(error, urllib.error.URLError) else error}") from None
    raise RuntimeError("Freesound-Suche nach mehreren Versuchen fehlgeschlagen.")


def search_sounds(api_key: str, query: str, allow_cc_by: bool) -> list[dict[str, Any]]:
    filter_value = "type:wav"
    if not allow_cc_by:
        filter_value += f' license:"{CC0}"'
    params = urllib.parse.urlencode(
        {
            "query": query,
            "filter": filter_value,
            "fields": "id,name,username,license,type,duration,filesize,url,tags",
            "page_size": "25",
        }
    )
    result = request_json(f"{SEARCH_URL}?{params}", api_key)
    raw_results = result.get("results", [])
    if not isinstance(raw_results, list):
        return []
    allowed_licenses = {CC0, CC_BY} if allow_cc_by else {CC0}
    return [
        sound for sound in raw_results
        if isinstance(sound, dict)
        and str(sound.get("type", "")).casefold() == "wav"
        and sound.get("license") in allowed_licenses
        and isinstance(sound.get("id"), int)
        and sound["id"] > 0
    ]


def safe_stem(name: str, sound_id: int) -> str:
    stem = name.strip()
    if stem.lower().endswith(".wav"):
        stem = stem[:-4]
    stem = re.sub(r"[^A-Za-z0-9._-]+", "_", stem).strip("._- ")[:80]
    return f"freesound_{sound_id}_{stem or 'sound'}.wav"


def download_to_temp(sound_id: int, access_token: str, destination: Path, max_bytes: int) -> int:
    """Download with bounded streaming; bearer auth is never sent to a CDN redirect host."""
    current_url = DOWNLOAD_URL.format(sound_id=sound_id)
    visited: set[str] = set()
    for redirect_count in range(MAX_REDIRECTS + 1):
        parsed = require_https_url(current_url)
        if current_url in visited:
            raise RuntimeError("Freesound-Download enthielt eine Redirect-Schleife.")
        visited.add(current_url)
        headers = {"Accept": "audio/wav, application/octet-stream, */*", "User-Agent": USER_AGENT}
        if parsed.hostname.lower() == API_HOST and parsed.path.startswith("/apiv2/"):
            headers["Authorization"] = f"Bearer {access_token}"
        request = urllib.request.Request(current_url, headers=headers, method="GET")
        try:
            response = OPENER.open(request, timeout=TIMEOUT_SECONDS)
        except urllib.error.HTTPError as error:
            try:
                if error.code in {301, 302, 303, 307, 308}:
                    location = error.headers.get("Location") if error.headers else None
                    if not location or redirect_count >= MAX_REDIRECTS:
                        raise RuntimeError("Freesound-Download-Redirect ungültig oder Limit überschritten.") from None
                    current_url = urllib.parse.urljoin(current_url, location)
                    continue
                if error.code in {429, 500, 502, 503, 504}:
                    raise RuntimeError(f"Download momentan nicht verfügbar (HTTP {error.code}); später erneut versuchen.") from None
                raise RuntimeError(f"Freesound-Download fehlgeschlagen (HTTP {error.code}).") from None
            finally:
                error.close()

        with response:
            content_type = (response.headers.get("Content-Type") or "").split(";", 1)[0].strip().lower()
            if content_type in {"text/html", "application/json"}:
                raise RuntimeError("Freesound lieferte statt Audio eine HTML-/JSON-Antwort.")
            raw_length = response.headers.get("Content-Length")
            if raw_length:
                try:
                    if int(raw_length) > max_bytes:
                        raise RuntimeError("Datei überschreitet das Größenlimit.")
                except ValueError:
                    pass
            total = 0
            with destination.open("wb") as out:
                while True:
                    chunk = response.read(64 * 1024)
                    if not chunk:
                        break
                    total += len(chunk)
                    if total > max_bytes:
                        raise RuntimeError("Download überschreitet das Größenlimit.")
                    out.write(chunk)
            return total
    raise RuntimeError("Freesound-Download-Redirect-Limit überschritten.")


def validate_wav(path: Path) -> dict[str, int]:
    """Validate RIFF/WAVE, supported audio format, chunk bounds, and frame alignment."""
    size = path.stat().st_size
    if size < 44 or size > MAX_FILE_BYTES:
        raise ValueError("WAV-Größe ist ungültig oder überschreitet 32 MiB.")
    with path.open("rb") as stream:
        header = stream.read(12)
        if len(header) != 12 or header[:4] != b"RIFF" or header[8:12] != b"WAVE":
            raise ValueError("Datei besitzt keinen unterstützten RIFF/WAVE-Header.")
        riff_end = 8 + int.from_bytes(header[4:8], "little")
        if riff_end < 12 or riff_end > size:
            raise ValueError("RIFF-Länge passt nicht zur Dateigröße.")
        fmt: bytes | None = None
        data_size: int | None = None
        data_offset: int | None = None
        position = 12
        while position + 8 <= riff_end:
            stream.seek(position)
            chunk_header = stream.read(8)
            if len(chunk_header) != 8:
                break
            chunk_id = chunk_header[:4]
            chunk_size = int.from_bytes(chunk_header[4:8], "little")
            chunk_data_start = position + 8
            chunk_end = chunk_data_start + chunk_size
            if chunk_end > riff_end:
                raise ValueError("WAV enthält einen abgeschnittenen Chunk.")
            if chunk_id == b"fmt ":
                if chunk_size < 16 or chunk_size > 1024 * 1024:
                    raise ValueError("WAV-Format-Chunk ist ungültig.")
                stream.seek(chunk_data_start)
                fmt = stream.read(min(chunk_size, 64))
            elif chunk_id == b"data":
                data_size = chunk_size
                data_offset = chunk_data_start
            position = chunk_end + (chunk_size & 1)
        if fmt is None or data_size is None or data_offset is None or data_size <= 0:
            raise ValueError("WAV benötigt gültige fmt- und data-Chunks.")

    format_code = int.from_bytes(fmt[0:2], "little")
    channels = int.from_bytes(fmt[2:4], "little")
    sample_rate = int.from_bytes(fmt[4:8], "little")
    block_align = int.from_bytes(fmt[12:14], "little")
    bits_per_sample = int.from_bytes(fmt[14:16], "little")
    if format_code == 0xFFFE:  # WAVE_FORMAT_EXTENSIBLE: inspect subformat GUID code.
        if len(fmt) < 40:
            raise ValueError("WAVE_FORMAT_EXTENSIBLE ist unvollständig.")
        format_code = int.from_bytes(fmt[24:26], "little")
    if channels not in {1, 2} or not 8_000 <= sample_rate <= 192_000:
        raise ValueError("Unterstützt werden Mono/Stereo und 8–192 kHz.")
    supported = (format_code == 1 and bits_per_sample in {16, 24, 32}) or (format_code == 3 and bits_per_sample == 32)
    if not supported:
        raise ValueError("Unterstützt werden PCM 16/24/32 Bit oder Float 32 Bit.")
    expected_align = channels * ((bits_per_sample + 7) // 8)
    if block_align != expected_align or data_size % block_align:
        raise ValueError("WAV-Blockausrichtung oder Datenlänge ist ungültig.")
    return {
        "channels": channels,
        "sample_rate": sample_rate,
        "bits_per_sample": bits_per_sample,
        "_format_code": format_code,
        "_data_offset": data_offset,
        "_data_size": data_size,
    }


def require_numpy():
    try:
        import numpy as np
    except ImportError as error:
        raise RuntimeError("Für BPM-/Tonartanalyse und EQ/Kompressor wird NumPy benötigt: python3 -m pip install numpy") from error
    return np


def decode_channel_samples(path: Path, wav_info: dict[str, int]):
    """Decode validated PCM/float WAV data to normalized frame-major float32 channels."""
    np = require_numpy()
    with path.open("rb") as stream:
        stream.seek(wav_info["_data_offset"])
        raw = stream.read(wav_info["_data_size"])
    channels = wav_info["channels"]
    bits = wav_info["bits_per_sample"]
    fmt = wav_info["_format_code"]

    if fmt == 1 and bits == 16:
        samples = np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0
    elif fmt == 1 and bits == 24:
        bytes3 = np.frombuffer(raw, dtype=np.uint8).reshape(-1, 3).astype(np.int32)
        values = bytes3[:, 0] | (bytes3[:, 1] << 8) | (bytes3[:, 2] << 16)
        values = (values ^ 0x800000) - 0x800000
        samples = values.astype(np.float32) / 8388608.0
    elif fmt == 1 and bits == 32:
        samples = np.frombuffer(raw, dtype="<i4").astype(np.float32) / 2147483648.0
    elif fmt == 3 and bits == 32:
        samples = np.frombuffer(raw, dtype="<f4").astype(np.float32, copy=True)
        np.nan_to_num(samples, copy=False, nan=0.0, posinf=0.0, neginf=0.0)
        np.clip(samples, -1.0, 1.0, out=samples)
    else:
        raise ValueError("Nicht unterstütztes WAV-Sampleformat für Audioanalyse.")

    return samples.reshape(-1, channels), wav_info["sample_rate"]


def decode_mono_samples(path: Path, wav_info: dict[str, int]):
    channels, sample_rate = decode_channel_samples(path, wav_info)
    np = require_numpy()
    return channels.mean(axis=1, dtype=np.float32), sample_rate


def apply_eq_wola(samples, sample_rate: int, np):
    """Gentle three-band spectral EQ using windowed overlap-add, preserving stereo."""
    frame_size, hop = 4096, 2048
    window = np.hanning(frame_size).astype(np.float32)
    frequencies = np.fft.rfftfreq(frame_size, d=1.0 / sample_rate)
    safe_frequencies = np.maximum(frequencies, 1.0)
    low = 1.0 / (1.0 + (frequencies / 180.0) ** 4)
    mid = np.exp(-0.5 * (np.log(safe_frequencies / 550.0) / 0.72) ** 2)
    high = 1.0 / (1.0 + (7000.0 / safe_frequencies) ** 4)
    gain_db = 1.25 * low - 1.10 * mid + 0.80 * high
    gain = np.power(10.0, gain_db / 20.0).astype(np.float32)

    padding = frame_size // 2
    padded = np.pad(samples, ((padding, padding), (0, 0)))
    frame_count = max(1, int(np.ceil(len(padded) / hop)))
    output_length = (frame_count - 1) * hop + frame_size
    output = np.zeros((output_length, samples.shape[1]), dtype=np.float32)
    normalization = np.zeros(output_length, dtype=np.float32)
    window_squared = window * window

    for frame_index in range(frame_count):
        start = frame_index * hop
        frame = np.zeros((frame_size, samples.shape[1]), dtype=np.float32)
        available = min(frame_size, len(padded) - start)
        if available > 0:
            frame[:available] = padded[start : start + available]
        spectrum = np.fft.rfft(frame * window[:, None], axis=0)
        spectrum *= gain[:, None]
        filtered = np.fft.irfft(spectrum, n=frame_size, axis=0).astype(np.float32)
        end = start + frame_size
        output[start:end] += filtered * window[:, None]
        normalization[start:end] += window_squared

    np.divide(output, normalization[:, None], out=output, where=normalization[:, None] > 1e-10)
    return output[padding : padding + len(samples)]


def apply_compressor(samples, sample_rate: int, np) -> tuple[Any, dict[str, float]]:
    """Stereo-linked, RMS-based soft compressor with 10 ms gain blocks."""
    threshold_db, ratio = -12.0, 2.5
    attack_seconds, release_seconds, makeup_db = 0.010, 0.120, 1.0
    block_size = max(1, int(round(sample_rate * 0.010)))
    block_count = int(np.ceil(len(samples) / block_size))
    padded = np.zeros((block_count * block_size, samples.shape[1]), dtype=np.float32)
    padded[: len(samples)] = samples
    blocks = padded.reshape(block_count, block_size, samples.shape[1])
    rms = np.sqrt(np.mean(blocks.astype(np.float64) ** 2, axis=(1, 2)))
    level_db = 20.0 * np.log10(np.maximum(rms, 1e-8))
    over_db = np.maximum(level_db - threshold_db, 0.0)
    reduction_db = over_db * (1.0 - 1.0 / ratio)
    target_gain = np.power(10.0, -reduction_db / 20.0)

    block_seconds = block_size / float(sample_rate)
    attack_alpha = float(np.exp(-block_seconds / attack_seconds))
    release_alpha = float(np.exp(-block_seconds / release_seconds))
    smoothed_gain = np.empty_like(target_gain)
    smoothed_gain[0] = target_gain[0]
    for index in range(1, len(target_gain)):
        alpha = attack_alpha if target_gain[index] < smoothed_gain[index - 1] else release_alpha
        smoothed_gain[index] = alpha * smoothed_gain[index - 1] + (1.0 - alpha) * target_gain[index]

    sample_gain = np.repeat(smoothed_gain, block_size)[: len(samples)]
    output = samples * sample_gain[:, None].astype(np.float32)
    output *= np.float32(10.0 ** (makeup_db / 20.0))
    return output, {
        "threshold_dbfs": threshold_db,
        "ratio": ratio,
        "attack_ms": attack_seconds * 1000.0,
        "release_ms": release_seconds * 1000.0,
        "makeup_db": makeup_db,
        "max_gain_reduction_db": float(np.max(reduction_db)) if len(reduction_db) else 0.0,
    }


def write_processed_wav(path: Path, samples, sample_rate: int, wav_info: dict[str, int], np) -> None:
    format_code = wav_info["_format_code"]
    bits = wav_info["bits_per_sample"]
    channels = samples.shape[1]
    clean = np.nan_to_num(samples, copy=True, nan=0.0, posinf=0.0, neginf=0.0).astype(np.float64)
    if format_code == 3 and bits == 32:
        payload = np.clip(clean, -1.0, 1.0).astype("<f4").tobytes()
    elif format_code == 1 and bits == 16:
        values = np.rint(np.clip(clean, -1.0, 1.0 - 1.0 / 32768.0) * 32768.0).astype("<i2")
        payload = values.tobytes()
    elif format_code == 1 and bits == 24:
        values = np.rint(np.clip(clean, -1.0, 1.0 - 1.0 / 8388608.0) * 8388608.0).astype(np.int32)
        unsigned = values.reshape(-1).astype(np.uint32)
        packed = np.empty((len(unsigned), 3), dtype=np.uint8)
        packed[:, 0] = unsigned & 0xFF
        packed[:, 1] = (unsigned >> 8) & 0xFF
        packed[:, 2] = (unsigned >> 16) & 0xFF
        payload = packed.tobytes()
    elif format_code == 1 and bits == 32:
        values = np.rint(np.clip(clean, -1.0, 1.0 - 1.0 / 2147483648.0) * 2147483648.0).astype("<i4")
        payload = values.tobytes()
    else:
        raise ValueError("Effektverarbeitung unterstützt PCM 16/24/32 Bit und Float 32 Bit.")

    block_align = channels * ((bits + 7) // 8)
    byte_rate = sample_rate * block_align
    with path.open("wb") as stream:
        stream.write(struct.pack("<4sI4s", b"RIFF", 36 + len(payload), b"WAVE"))
        stream.write(struct.pack("<4sIHHIIHH", b"fmt ", 16, format_code, channels, sample_rate, byte_rate, block_align, bits))
        stream.write(struct.pack("<4sI", b"data", len(payload)))
        stream.write(payload)


def apply_effects_to_wav(path: Path, wav_info: dict[str, int], effects: list[str]) -> tuple[dict[str, Any], dict[str, int]]:
    """Apply selected effects to a temporary/library copy and atomically replace it."""
    if not effects:
        return {"enabled": False, "chain": []}, wav_info
    np = require_numpy()
    samples, sample_rate = decode_channel_samples(path, wav_info)
    input_peak = float(np.max(np.abs(samples))) if samples.size else 0.0
    details: dict[str, Any] = {}
    if "eq" in effects:
        samples = apply_eq_wola(samples, sample_rate, np)
        details["eq"] = {
            "type": "gentle_3_band_wola",
            "low_shelf_db": 1.25,
            "mid_dip_db": -1.10,
            "high_shelf_db": 0.80,
        }
    if "compressor" in effects:
        samples, compressor_details = apply_compressor(samples, sample_rate, np)
        details["compressor"] = compressor_details

    peak_before_limiter = float(np.max(np.abs(samples))) if samples.size else 0.0
    limiter_gain = min(1.0, 0.98 / peak_before_limiter) if peak_before_limiter > 0.98 else 1.0
    if limiter_gain < 1.0:
        samples *= np.float32(limiter_gain)
    output_peak = float(np.max(np.abs(samples))) if samples.size else 0.0

    fd, temp_name = tempfile.mkstemp(prefix=f".{path.stem}-fx-", suffix=".wav", dir=path.parent)
    os.close(fd)
    temporary = Path(temp_name)
    try:
        write_processed_wav(temporary, samples, sample_rate, wav_info, np)
        processed_info = validate_wav(temporary)
        os.replace(temporary, path)
        processed_info = validate_wav(path)
    finally:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass

    effect_metadata = {
        "enabled": True,
        "chain": list(effects),
        "details": details,
        "input_peak_dbfs": round(20.0 * np.log10(max(input_peak, 1e-8)), 2),
        "output_peak_dbfs": round(20.0 * np.log10(max(output_peak, 1e-8)), 2),
        "peak_limiter_gain_db": round(20.0 * np.log10(max(limiter_gain, 1e-8)), 2),
        "sample_rate_hz": sample_rate,
        "channels": int(samples.shape[1]),
    }
    return effect_metadata, processed_info


def resample_for_analysis(samples, sample_rate: int, np):
    """Linearly downsample to at most 22.05 kHz to bound FFT cost."""
    target_rate = min(sample_rate, 22_050)
    if target_rate == sample_rate or len(samples) < 2:
        return samples.astype(np.float32, copy=False), sample_rate
    target_length = max(1, int(round(len(samples) * target_rate / sample_rate)))
    positions = np.arange(target_length, dtype=np.float64) * (sample_rate / target_rate)
    left = np.floor(positions).astype(np.int64)
    right = np.minimum(left + 1, len(samples) - 1)
    fraction = (positions - left).astype(np.float32)
    result = samples[left] * (1.0 - fraction) + samples[right] * fraction
    return result.astype(np.float32, copy=False), target_rate


def detect_tempo(samples, sample_rate: int, target_bpm: float, np) -> dict[str, Any]:
    """RMS-onset/autocorrelation tempo estimate, mirroring the Android MVP's method."""
    minimum_bpm, maximum_bpm, minimum_confidence = 60.0, 180.0, 0.18
    duration = len(samples) / float(sample_rate)
    frames_per_window = max(1, int(round(sample_rate / 100.0)))
    window_count = int(np.ceil(len(samples) / frames_per_window))
    detected_bpm = None
    confidence = 0.0
    ambiguous = False

    if window_count >= 100:
        padded = np.pad(samples, (0, window_count * frames_per_window - len(samples)))
        blocks = padded.reshape(window_count, frames_per_window)
        rms = np.sqrt(np.mean(blocks * blocks, axis=1))
        novelty = np.zeros(window_count, dtype=np.float64)
        novelty[1:] = np.maximum(0.0, np.diff(rms))
        novelty = np.maximum(0.0, novelty - novelty.mean())
        if float(np.dot(novelty, novelty)) > 1e-10:
            fft_length = 1 << (2 * window_count - 1).bit_length()
            spectrum = np.fft.rfft(novelty, n=fft_length)
            autocorrelation = np.fft.irfft(spectrum * np.conjugate(spectrum), n=fft_length)[:window_count]
            window_seconds = frames_per_window / float(sample_rate)
            min_lag = max(1, int(round(60.0 / (maximum_bpm * window_seconds))))
            max_lag = min(window_count - 1, int(round(60.0 / (minimum_bpm * window_seconds))))
            squared_prefix = np.concatenate(([0.0], np.cumsum(novelty * novelty)))
            scores: dict[int, float] = {}
            for lag in range(min_lag, max_lag + 1):
                if (window_count - lag) / lag < 3.0:
                    continue
                left_energy = squared_prefix[-1] - squared_prefix[lag]
                right_energy = squared_prefix[window_count - lag]
                denominator = float(np.sqrt(left_energy * right_energy))
                scores[lag] = float(np.clip(autocorrelation[lag] / denominator, 0.0, 1.0)) if denominator > 1e-12 else 0.0
            if scores:
                best_lag = max(scores, key=scores.get)
                best_score = scores[best_lag]
                if best_score >= minimum_confidence:
                    near_best = [lag for lag, score in scores.items() if score >= best_score * 0.98 and score >= minimum_confidence]
                    selected_lag = min(near_best) if near_best else best_lag
                    faster_lag = int(round(selected_lag / 2.0))
                    faster_score = scores.get(faster_lag, 0.0)
                    if faster_score >= max(minimum_confidence, best_score * 0.25):
                        selected_lag = faster_lag
                        ambiguous = True
                    confidence = scores[selected_lag]
                    slower_lag = selected_lag * 2
                    ambiguous = ambiguous or (
                        slower_lag in scores and scores[slower_lag] >= best_score * 0.25
                    ) or (
                        faster_lag in scores and scores[faster_lag] >= best_score * 0.25
                    )
                    left = scores.get(selected_lag - 1, confidence)
                    center = confidence
                    right = scores.get(selected_lag + 1, confidence)
                    curve = left - 2.0 * center + right
                    offset = float(np.clip(0.5 * (left - right) / curve, -0.5, 0.5)) if abs(curve) > 1e-12 else 0.0
                    lag_float = max(1e-6, selected_lag + offset)
                    estimate = 60.0 / (lag_float * window_seconds)
                    if minimum_bpm * 0.98 <= estimate < minimum_bpm:
                        estimate = minimum_bpm
                    if maximum_bpm < estimate <= maximum_bpm * 1.02:
                        estimate = maximum_bpm
                    if minimum_bpm <= estimate <= maximum_bpm:
                        detected_bpm = float(estimate)

    bar_tempo = detected_bpm if detected_bpm is not None else target_bpm
    bars = max(1, min(64, int(round(duration * bar_tempo / 240.0))))
    return {
        "bpm": round(detected_bpm, 2) if detected_bpm is not None else None,
        "alternate_bpm": round(detected_bpm / 2.0, 2) if detected_bpm is not None and ambiguous else None,
        "tempo_confidence": round(confidence, 4),
        "tempo_ambiguous": ambiguous,
        "tempo_fallback_used": detected_bpm is None,
        "estimated_bars": bars,
        "duration_seconds": round(duration, 3),
        "fallback_target_bpm": target_bpm if detected_bpm is None else None,
    }


def detect_key(samples, sample_rate: int, np) -> dict[str, Any]:
    """Estimate musical key by FFT chroma and rotated Krumhansl-Schmuckler profiles."""
    key_names = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
    profiles = {
        "major": np.array([6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88]),
        "minor": np.array([6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17]),
    }
    fft_size, hop = 8192, 4096
    if len(samples) == 0:
        return {"key": None, "key_confidence": 0.0, "key_margin": 0.0}
    window = np.hanning(fft_size).astype(np.float32)
    frequencies = np.fft.rfftfreq(fft_size, d=1.0 / sample_rate)
    mask = (frequencies >= 55.0) & (frequencies <= min(5_000.0, sample_rate / 2.0))
    selected_freqs = frequencies[mask]
    if len(selected_freqs) == 0:
        return {"key": None, "key_confidence": 0.0, "key_margin": 0.0}
    midi = np.rint(69.0 + 12.0 * np.log2(selected_freqs / 440.0)).astype(np.int32)
    pitch_classes = midi % 12
    weights = 1.0 / np.sqrt(np.maximum(selected_freqs, 1.0))
    chroma = np.zeros(12, dtype=np.float64)
    starts = list(range(0, max(1, len(samples) - fft_size + 1), hop))
    if len(samples) > fft_size:
        last_start = len(samples) - fft_size
        if starts[-1] != last_start:
            starts.append(last_start)
    for start in starts:
        frame = np.zeros(fft_size, dtype=np.float32)
        section = samples[start : start + fft_size]
        frame[: len(section)] = section
        frame -= frame.mean()
        magnitudes = np.abs(np.fft.rfft(frame * window))[mask]
        chroma += np.bincount(pitch_classes, weights=magnitudes * weights, minlength=12)
    if float(chroma.sum()) <= 1e-10:
        return {"key": None, "key_confidence": 0.0, "key_margin": 0.0}

    observed = np.log1p(chroma)
    observed -= observed.mean()
    observed_norm = float(np.linalg.norm(observed))
    if observed_norm <= 1e-10:
        return {"key": None, "key_confidence": 0.0, "key_margin": 0.0}
    candidates: list[tuple[float, str]] = []
    for scale, profile in profiles.items():
        centered = profile - profile.mean()
        profile_norm = float(np.linalg.norm(centered))
        for tonic, key_name in enumerate(key_names):
            rotated = np.roll(centered, tonic)
            score = float(np.dot(observed, rotated) / (observed_norm * profile_norm))
            candidates.append((score, f"{key_name} {scale}"))
    candidates.sort(reverse=True)
    best_score, best_key = candidates[0]
    second_score = candidates[1][0]
    margin = best_score - second_score
    # Percussion/noise often has no reliable key: preserve uncertainty as Unknown.
    chroma_probabilities = chroma / chroma.sum()
    entropy = -float(np.sum(chroma_probabilities * np.log(np.maximum(chroma_probabilities, 1e-12)))) / float(np.log(12.0))
    tonality = max(0.0, min(1.0, 1.0 - entropy))
    accepted_key = best_key if best_score >= 0.12 and margin >= 0.008 and tonality >= 0.12 else None
    return {
        "key": accepted_key,
        "key_confidence": round(max(0.0, best_score), 4),
        "key_margin": round(max(0.0, margin), 4),
        "tonality": round(tonality, 4),
        "key_ambiguous": accepted_key is not None and margin < 0.02,
        "key_best_candidate": best_key if accepted_key is None else None,
    }


def analyze_wav(path: Path, wav_info: dict[str, int], target_bpm: float) -> dict[str, Any]:
    np = require_numpy()
    samples, source_rate = decode_mono_samples(path, wav_info)
    samples, analysis_rate = resample_for_analysis(samples, source_rate, np)
    result = detect_tempo(samples, analysis_rate, target_bpm, np)
    result.update(detect_key(samples, analysis_rate, np))
    result["analysis_sample_rate"] = analysis_rate
    result["analyzer"] = "NumPy RMS-onset/autocorrelation + FFT chroma/Krumhansl-Schmuckler"
    return result


def sorted_loop_directory(root: Path, category: str, analysis: dict[str, Any]) -> Path:
    category_label = CATEGORIES[category]["label"] if category in CATEGORIES else "Nicht einsortiert"
    bpm = analysis.get("bpm")
    bpm_label = f"BPM_{int(round(float(bpm)))}" if bpm is not None else "BPM_Unbekannt"
    key = analysis.get("key")
    key_label = "Key_Unbekannt" if not key else "Key_" + str(key).replace("#", "sharp").replace(" ", "_")
    return root / category_label / bpm_label / key_label


def sidecar_for(wav: Path) -> Path:
    return wav.with_suffix(".json")


def write_json_atomic(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(value, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        os.replace(temp_name, path)
    finally:
        try:
            os.unlink(temp_name)
        except FileNotFoundError:
            pass


def read_metadata(wav: Path) -> dict[str, Any]:
    try:
        value = json.loads(sidecar_for(wav).read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else {}
    except (OSError, UnicodeDecodeError, json.JSONDecodeError):
        return {}


def open_loop_database(database_path: Path) -> sqlite3.Connection:
    """Create/open the local searchable index; audio bytes remain in WAV files."""
    database_path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(database_path, timeout=15.0)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    connection.execute("PRAGMA busy_timeout = 15000")
    connection.execute("PRAGMA journal_mode = WAL")
    connection.executescript("""
        CREATE TABLE IF NOT EXISTS loops (
            id INTEGER PRIMARY KEY,
            path TEXT NOT NULL UNIQUE,
            sha256 TEXT,
            provider TEXT,
            sound_id INTEGER,
            name TEXT NOT NULL,
            category TEXT,
            category_label TEXT,
            bpm REAL,
            key_name TEXT,
            license TEXT,
            attribution TEXT,
            original_url TEXT,
            username TEXT,
            duration_seconds REAL,
            sample_rate_hz INTEGER,
            channels INTEGER,
            bits_per_sample INTEGER,
            tags_json TEXT NOT NULL DEFAULT '[]',
            analysis_json TEXT NOT NULL DEFAULT '{}',
            effects_json TEXT NOT NULL DEFAULT '{}',
            metadata_json TEXT NOT NULL DEFAULT '{}',
            indexed_at_utc TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_loops_category ON loops(category);
        CREATE INDEX IF NOT EXISTS idx_loops_bpm ON loops(bpm);
        CREATE INDEX IF NOT EXISTS idx_loops_key ON loops(key_name);
        CREATE INDEX IF NOT EXISTS idx_loops_provider_sound ON loops(provider, sound_id);
    """)
    return connection


def upsert_loop(connection: sqlite3.Connection, library_root: Path, wav: Path, metadata: dict[str, Any]) -> None:
    """Insert/update one loop index row. Store a relative path so the library can move."""
    analysis = metadata.get("analysis") if isinstance(metadata.get("analysis"), dict) else {}
    wav_info = metadata.get("wav") if isinstance(metadata.get("wav"), dict) else {}
    tags = metadata.get("tags", [])
    if not isinstance(tags, list):
        tags = str(tags).split()
    sound_id = metadata.get("sound_id")
    try:
        sound_id = int(sound_id) if sound_id is not None else None
    except (TypeError, ValueError):
        sound_id = None
    try:
        bpm = float(analysis["bpm"]) if analysis.get("bpm") is not None else None
    except (TypeError, ValueError):
        bpm = None
    duration = metadata.get("duration_seconds")
    try:
        duration = float(duration) if duration is not None else None
    except (TypeError, ValueError):
        duration = None
    if duration is None:
        try:
            duration_info = wav_info if "_data_size" in wav_info else validate_wav(wav)
            bytes_per_frame = int(duration_info["channels"]) * ((int(duration_info["bits_per_sample"]) + 7) // 8)
            duration = int(duration_info["_data_size"]) / bytes_per_frame / int(duration_info["sample_rate"])
        except (KeyError, OSError, TypeError, ValueError, ZeroDivisionError):
            duration = None
    relative_path = wav.resolve().relative_to(library_root.resolve()).as_posix()
    connection.execute("""
        INSERT INTO loops (
            path, sha256, provider, sound_id, name, category, category_label,
            bpm, key_name, license, attribution, original_url, username,
            duration_seconds, sample_rate_hz, channels, bits_per_sample,
            tags_json, analysis_json, effects_json, metadata_json, indexed_at_utc
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(path) DO UPDATE SET
            sha256=excluded.sha256, provider=excluded.provider, sound_id=excluded.sound_id,
            name=excluded.name, category=excluded.category, category_label=excluded.category_label,
            bpm=excluded.bpm, key_name=excluded.key_name, license=excluded.license,
            attribution=excluded.attribution, original_url=excluded.original_url,
            username=excluded.username, duration_seconds=excluded.duration_seconds,
            sample_rate_hz=excluded.sample_rate_hz, channels=excluded.channels,
            bits_per_sample=excluded.bits_per_sample, tags_json=excluded.tags_json,
            analysis_json=excluded.analysis_json, effects_json=excluded.effects_json,
            metadata_json=excluded.metadata_json, indexed_at_utc=excluded.indexed_at_utc
    """, (
        relative_path,
        metadata.get("sha256") or sha256_file(wav),
        metadata.get("provider"),
        sound_id,
        str(metadata.get("name") or wav.stem),
        metadata.get("category"),
        metadata.get("category_label"),
        bpm,
        analysis.get("key"),
        metadata.get("license"),
        metadata.get("attribution"),
        metadata.get("original_url"),
        metadata.get("username"),
        duration,
        wav_info.get("sample_rate"),
        wav_info.get("channels"),
        wav_info.get("bits_per_sample"),
        json.dumps(tags, ensure_ascii=False),
        json.dumps(analysis, ensure_ascii=False),
        json.dumps(metadata.get("effects", {}), ensure_ascii=False),
        json.dumps(metadata, ensure_ascii=False),
        datetime.now(timezone.utc).isoformat(),
    ))


def sync_loop_database(connection: sqlite3.Connection, library_root: Path) -> int:
    """Repair the index from the authoritative WAV/sidecar library after moves or interrupted runs."""
    root = library_root.resolve()
    files = sorted(path for path in root.rglob("*.wav") if path.is_file())
    live_paths = {path.resolve().relative_to(root).as_posix() for path in files}
    with connection:
        for wav in files:
            upsert_loop(connection, root, wav, read_metadata(wav))
        indexed_paths = {row[0] for row in connection.execute("SELECT path FROM loops")}
        stale = indexed_paths - live_paths
        if stale:
            connection.executemany("DELETE FROM loops WHERE path = ?", ((path,) for path in stale))
    return len(files)


def reclassify_existing(root: Path, target_bpm: float, effects: list[str]) -> int:
    """Analyze script-managed WAVs and sort them by role, estimated BPM, and key."""
    changed = 0
    labels = {definition["label"]: key for key, definition in CATEGORIES.items()}
    labels["Nicht einsortiert"] = "UNCATEGORIZED"
    for wav in list(root.rglob("*.wav")):
        metadata = read_metadata(wav)
        if not sidecar_for(wav).exists() and not wav.name.startswith("freesound_"):
            continue
        title = str(metadata.get("name") or wav.stem)
        tags = metadata.get("tags", [])
        if not isinstance(tags, list):
            tags = str(tags).split()
        inferred = infer_category(title, tags, wav.name)
        manual = metadata.get("category_manually_set") is True
        stored_category = category_key_from_value(metadata.get("category"))
        if manual and stored_category in {*CATEGORIES.keys(), "UNCATEGORIZED"}:
            category = str(stored_category)
        elif inferred:
            category = inferred
        elif stored_category in {*CATEGORIES.keys(), "UNCATEGORIZED"}:
            category = str(stored_category)
        else:
            category = labels.get(wav.relative_to(root).parts[0], "UNCATEGORIZED")

        analysis = metadata.get("analysis")
        applied_effects = metadata.get("effects_applied", [])
        if not isinstance(applied_effects, list):
            applied_effects = []
        needs_effects = bool(effects) and not applied_effects
        wav_info: dict[str, int] | None = None
        if not isinstance(analysis, dict) or needs_effects:
            wav_info = validate_wav(wav)
        if not isinstance(analysis, dict):
            assert wav_info is not None
            analysis = analyze_wav(wav, wav_info, target_bpm)
            metadata["analysis"] = analysis
            metadata["wav"] = {key: value for key, value in wav_info.items() if not key.startswith("_")}
            changed += 1

        if needs_effects:
            assert wav_info is not None
            effect_info, wav_info = apply_effects_to_wav(wav, wav_info, effects)
            metadata["effects"] = effect_info
            metadata["effects_applied"] = list(effects)
            metadata["wav"] = {key: value for key, value in wav_info.items() if not key.startswith("_")}
            changed += 1
        elif "effects_applied" not in metadata:
            metadata["effects"] = {"enabled": False, "chain": []}
            metadata["effects_applied"] = []

        if not manual and inferred:
            metadata["category_source"] = "automatic"
        metadata["category"] = category
        metadata["category_label"] = CATEGORIES[category]["label"] if category in CATEGORIES else "Nicht einsortiert"
        metadata.setdefault("category_manually_set", False)
        target_dir = sorted_loop_directory(root, category, analysis)
        target_wav = target_dir / wav.name
        if target_wav != wav and target_wav.exists():
            continue
        if target_wav != wav:
            target_dir.mkdir(parents=True, exist_ok=True)
            old_sidecar = sidecar_for(wav)
            shutil.move(str(wav), str(target_wav))
            if old_sidecar.exists():
                shutil.move(str(old_sidecar), str(sidecar_for(target_wav)))
            wav = target_wav
            changed += 1
        write_json_atomic(sidecar_for(wav), metadata)
    return changed


def existing_count(category_dir: Path) -> int:
    return sum(1 for p in category_dir.rglob("*.wav") if p.is_file())


def library_size(root: Path) -> int:
    return sum(p.stat().st_size for p in root.rglob("*.wav") if p.is_file())


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def existing_hashes(root: Path) -> set[str]:
    hashes: set[str] = set()
    for wav in root.rglob("*.wav"):
        metadata = read_metadata(wav)
        known_hash = metadata.get("sha256")
        if isinstance(known_hash, str) and re.fullmatch(r"[0-9a-fA-F]{64}", known_hash):
            hashes.add(known_hash.lower())
        else:
            try:
                hashes.add(sha256_file(wav))
            except OSError:
                continue
    return hashes


def safe_local_filename(original_stem: str, digest: str) -> str:
    stem = re.sub(r"[^A-Za-z0-9._-]+", "_", original_stem.strip()).strip("._- ")[:72]
    return f"local_{digest[:12]}_{stem or 'loop'}.wav"


def read_source_sidecar(source: Path) -> dict[str, Any]:
    try:
        value = json.loads(source.with_suffix(".json").read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else {}
    except (OSError, UnicodeDecodeError, json.JSONDecodeError):
        return {}


def import_local_wavs(
    import_dir: Path,
    root: Path,
    target_bpm: float,
    max_total_bytes: int,
    effects: list[str],
    database: sqlite3.Connection | None,
    dry_run: bool = False,
) -> tuple[int, int, int]:
    """Copy local WAVs into the analyzed library; never accesses any provider website."""
    source_root = import_dir.expanduser().resolve()
    if not source_root.is_dir():
        raise RuntimeError(f"Lokaler Importordner existiert nicht: {source_root}")
    root = root.expanduser().resolve()
    try:
        source_root.relative_to(root)
        raise RuntimeError("Der Importordner darf nicht innerhalb der Zielbibliothek liegen.")
    except ValueError:
        pass

    known = existing_hashes(root)
    imported = skipped = errors = 0
    sources = sorted(p for p in source_root.rglob("*") if p.is_file() and p.suffix.casefold() == ".wav")
    for source in sources:
        destination: Path | None = None
        try:
            if source.is_relative_to(root):
                skipped += 1
                continue
            digest = sha256_file(source)
            if digest in known:
                skipped += 1
                print(f"  bereits vorhanden, übersprungen: {source.name}")
                continue
            wav_info = validate_wav(source)
            analysis = analyze_wav(source, wav_info, target_bpm)
            source_meta = read_source_sidecar(source)
            title = str(source_meta.get("name") or source.stem)
            tags = source_meta.get("tags", [])
            if not isinstance(tags, list):
                tags = str(tags).split()
            manual = source_meta.get("category_manually_set") is True
            supplied_category = category_key_from_value(source_meta.get("category"))
            inferred = infer_category(title, tags, source.name)
            parent_category = category_key_from_value(source.parent.name)
            if manual and supplied_category:
                category = supplied_category
                category_source = "manual_override"
            elif inferred:
                category = inferred
                category_source = "automatic_name_or_tags"
            elif supplied_category:
                category = supplied_category
                category_source = "source_metadata"
            elif parent_category:
                category = parent_category
                category_source = "source_folder"
            else:
                category = "UNCATEGORIZED"
                category_source = "uncategorized"

            target_dir = sorted_loop_directory(root, category, analysis)
            filename = safe_local_filename(source.stem, digest)
            destination = target_dir / filename
            if dry_run:
                bpm_text = f"{analysis['bpm']} BPM" if analysis["bpm"] is not None else "BPM unbekannt"
                chain_text = ",".join(effects) if effects else "keine Effekte"
                print(f"  [dry-run Import] {source.name} → {destination.relative_to(root)} · {bpm_text} · {analysis['key'] or 'Tonart unbekannt'} · FX: {chain_text}")
                known.add(digest)
                imported += 1
                continue
            if library_size(root) + source.stat().st_size > max_total_bytes:
                print(f"  Speicherlimit erreicht; übersprungen: {source.name}", file=sys.stderr)
                skipped += 1
                continue
            target_dir.mkdir(parents=True, exist_ok=True)
            fd, temp_name = tempfile.mkstemp(prefix=".local-import-", suffix=".part", dir=target_dir)
            os.close(fd)
            temporary = Path(temp_name)
            try:
                shutil.copy2(source, temporary)
                if sha256_file(temporary) != digest:
                    raise RuntimeError("Quelldatei änderte sich während des Imports.")
                if effects:
                    effect_info, wav_info = apply_effects_to_wav(temporary, wav_info, effects)
                else:
                    effect_info = {"enabled": False, "chain": []}
                metadata = dict(source_meta)
                metadata.update({
                    "provider": str(source_meta.get("provider") or "Local import"),
                    "source_file_name": source.name,
                    "local_imported_at_utc": datetime.now(timezone.utc).isoformat(),
                    "sha256": digest,
                    "name": title,
                    "category": category,
                    "category_label": CATEGORIES[category]["label"] if category in CATEGORIES else "Nicht einsortiert",
                    "category_source": category_source,
                    "category_manually_set": manual,
                    "tags": tags,
                    "wav": {key: value for key, value in wav_info.items() if not key.startswith("_")},
                    "analysis": analysis,
                    "effects": effect_info,
                    "effects_applied": list(effects),
                })
                # Publish provenance first. A crash before WAV rename leaves only an ignored orphan sidecar;
                # a crash after the rename can never leave an imported file without its source metadata.
                write_json_atomic(sidecar_for(destination), metadata)
                os.replace(temporary, destination)
            finally:
                try:
                    temporary.unlink()
                except FileNotFoundError:
                    pass

            if database is not None:
                with database:
                    upsert_loop(database, root, destination, metadata)
            known.add(digest)
            imported += 1
            bpm_text = f"{analysis['bpm']} BPM" if analysis["bpm"] is not None else "BPM unbekannt"
            print(f"  importiert: {destination.relative_to(root)} · {bpm_text} · {analysis['key'] or 'Tonart unbekannt'}")
        except Exception as error:
            if destination is not None and not destination.exists():
                try:
                    sidecar_for(destination).unlink()
                except FileNotFoundError:
                    pass
            errors += 1
            print(f"  Importfehler bei {source.name}: {error}", file=sys.stderr)
    return imported, skipped, errors


def all_sound_ids(root: Path) -> set[int]:
    ids: set[int] = set()
    for wav in root.rglob("*.wav"):
        match = re.match(r"freesound_(\d+)_", wav.name)
        if match:
            ids.add(int(match.group(1)))
        meta_id = read_metadata(wav).get("sound_id")
        try:
            if meta_id is not None:
                ids.add(int(meta_id))
        except (TypeError, ValueError):
            pass
    return ids


def allowed_license(sound: dict[str, Any], allow_cc_by: bool) -> bool:
    license_name = sound.get("license")
    return license_name == CC0 or (allow_cc_by and license_name == CC_BY)


def process(args: argparse.Namespace) -> int:
    api_key = ""
    access_token = ""
    database: sqlite3.Connection | None = None
    if not args.local_only:
        if not args.confirm_api_terms:
            raise RuntimeError("Vor dem Freesound-Zugriff bitte die aktuellen API-Bedingungen prüfen und --confirm-api-terms setzen.")
        api_key = os.environ.get("FREESOUND_API_KEY", "").strip()
        if not api_key:
            raise RuntimeError("FREESOUND_API_KEY fehlt (API-Key nur als Umgebungsvariable setzen).")
        access_token = os.environ.get("FREESOUND_ACCESS_TOKEN", "").strip()
        if not args.dry_run and not access_token:
            raise RuntimeError("Für Freesound-Downloads fehlt FREESOUND_ACCESS_TOKEN (OAuth2-Bearer-Token).")
    if not args.dry_run or args.import_dir is not None:
        require_numpy()

    root: Path = args.output.expanduser().resolve()
    if not args.dry_run:
        root.mkdir(parents=True, exist_ok=True)
        database_path = args.database.expanduser().resolve() if args.database else root / "loops.sqlite3"
        database = open_loop_database(database_path)
        relabeled = reclassify_existing(root, args.target_bpm, args.effects)
        if relabeled:
            print(f"Vorhandene Bibliothek: {relabeled} Analyse-/Sortierkorrekturen.")
        indexed_count = sync_loop_database(database, root)
        print(f"SQLite-Index: {indexed_count} Loop(s) synchronisiert · {database_path}")

    if args.import_dir is not None:
        print("Lokaler WAV-Import:", args.import_dir.expanduser().resolve())
        imported, skipped, errors = import_local_wavs(
            args.import_dir,
            root,
            args.target_bpm,
            args.max_total_bytes,
            args.effects,
            database,
            dry_run=args.dry_run,
        )
        print(f"Lokaler Import: {imported} übernommen/vorgemerkt, {skipped} übersprungen, {errors} Fehler.")
    if args.local_only:
        if database is not None:
            database.close()
        return 0

    used_ids = all_sound_ids(root)
    allowed = {CC0, CC_BY} if args.allow_cc_by else {CC0}
    print("Lizenzfilter:", "CC0 + CC BY (Opt-in; Credits werden gespeichert)" if args.allow_cc_by else "nur CC0")
    print("Speicherort:", root)

    for key, definition in CATEGORIES.items():
        category_dir = root / definition["label"]
        have = existing_count(category_dir)
        needed = max(0, args.loops_per_category - have)
        if not needed:
            print(f"{definition['label']}: bereits {have} Loop(s), übersprungen.")
            continue

        print(f"{definition['label']}: suche {needed} Loop(s) …")
        results = search_sounds(api_key, definition["query"], args.allow_cc_by)
        downloaded_here = 0
        for sound in results:
            if downloaded_here >= needed:
                break
            sound_id = int(sound["id"])
            if sound_id in used_ids or not allowed_license(sound, args.allow_cc_by):
                continue
            filesize = sound.get("filesize")
            if filesize is not None:
                try:
                    if int(filesize) < 0 or int(filesize) > MAX_FILE_BYTES:
                        continue
                except (TypeError, ValueError):
                    continue
            inferred = infer_category(str(sound.get("name", "")), sound.get("tags", []))
            # Avoid putting an obviously different role into the requested category.
            if inferred is not None and inferred != key:
                continue
            assigned_category = inferred or key
            assigned_dir = root / CATEGORIES[assigned_category]["label"]
            if existing_count(assigned_dir) >= args.loops_per_category and assigned_category == key:
                break
            title = str(sound.get("name", "Freesound sound"))
            username = str(sound.get("username", ""))
            source_url = str(sound.get("url") or f"https://freesound.org/sounds/{sound_id}/")
            filename = safe_stem(title, sound_id)
            final_wav = assigned_dir / filename
            if final_wav.exists():
                used_ids.add(sound_id)
                continue
            if args.dry_run:
                chain_text = ",".join(args.effects) if args.effects else "keine Effekte"
                print(f"  [dry-run] {assigned_category}: {title} · {sound.get('license')} · ID {sound_id} · FX: {chain_text}")
                used_ids.add(sound_id)
                downloaded_here += 1
                continue

            current_total = library_size(root)
            expected_size = int(filesize) if filesize is not None else MAX_FILE_BYTES
            if current_total + expected_size > args.max_total_bytes:
                print("  Speicherlimit erreicht; keine weiteren Downloads.", file=sys.stderr)
                break
            assigned_dir.mkdir(parents=True, exist_ok=True)
            fd, temp_name = tempfile.mkstemp(prefix=f".freesound-{sound_id}-", suffix=".part", dir=assigned_dir)
            os.close(fd)
            temp_wav = Path(temp_name)
            sidecar_written = False
            try:
                written = download_to_temp(sound_id, access_token, temp_wav, MAX_FILE_BYTES)
                if written == 0 or (filesize and written != int(filesize)):
                    raise RuntimeError("Downloadgröße stimmt nicht mit den Freesound-Metadaten überein.")
                wav_info = validate_wav(temp_wav)
                # BPM/key come from the dry source so processing does not bias sorting.
                analysis = analyze_wav(temp_wav, wav_info, args.target_bpm)
                if args.effects:
                    effect_info, wav_info = apply_effects_to_wav(temp_wav, wav_info, args.effects)
                else:
                    effect_info = {"enabled": False, "chain": []}
                processed_size = temp_wav.stat().st_size
                final_dir = sorted_loop_directory(root, assigned_category, analysis)
                final_dir.mkdir(parents=True, exist_ok=True)
                final_wav = final_dir / filename
                if final_wav.exists():
                    raise RuntimeError("Zieldatei existiert bereits; Download wurde nicht überschrieben.")
                if library_size(root) + processed_size > args.max_total_bytes:
                    raise RuntimeError("Lokales Gesamtlimit wurde während des Downloads überschritten.")
                attribution = f"{title} — {username} — {source_url} ({sound.get('license')})"
                metadata = {
                    "provider": "Freesound",
                    "sound_id": sound_id,
                    "name": title,
                    "username": username,
                    "license": sound.get("license"),
                    "original_url": source_url,
                    "downloaded_at_utc": datetime.now(timezone.utc).isoformat(),
                    "category": assigned_category,
                    "category_label": CATEGORIES[assigned_category]["label"],
                    "category_source": "automatic" if inferred else "query_fallback",
                    "category_manually_set": False,
                    "tags": sound.get("tags", []) if isinstance(sound.get("tags", []), list) else [],
                    "duration_seconds": sound.get("duration"),
                    "filesize_bytes": processed_size,
                    "wav": {field: value for field, value in wav_info.items() if not field.startswith("_")},
                    "analysis": analysis,
                    "effects": effect_info,
                    "effects_applied": list(args.effects),
                    "attribution": attribution if sound.get("license") == CC_BY else None,
                }
                # Persist provenance first. A crash before the WAV rename leaves an ignored
                # sidecar; a crash after it cannot expose audio without its license/source data.
                write_json_atomic(sidecar_for(final_wav), metadata)
                sidecar_written = True
                os.replace(temp_wav, final_wav)
            except Exception as error:
                if sidecar_written and not final_wav.exists():
                    try:
                        sidecar_for(final_wav).unlink()
                    except FileNotFoundError:
                        pass
                print(f"  Freesound-Loop {sound_id} übersprungen: {error}", file=sys.stderr)
                continue
            finally:
                try:
                    temp_wav.unlink()
                except FileNotFoundError:
                    pass

            if database is not None:
                with database:
                    upsert_loop(database, root, final_wav, metadata)
            used_ids.add(sound_id)
            downloaded_here += 1
            bpm_label = f"{analysis['bpm']} BPM" if analysis["bpm"] is not None else "BPM unbekannt"
            if analysis["tempo_ambiguous"] and analysis["alternate_bpm"] is not None:
                bpm_label += f" (mehrdeutig; Alternative {analysis['alternate_bpm']} BPM)"
            key_label = analysis["key"] or "Tonart unbekannt"
            if analysis.get("key_ambiguous"):
                key_label += " (mehrdeutig)"
            fx_label = ",".join(args.effects) if args.effects else "ohne FX"
            print(f"  gespeichert: {final_wav.relative_to(root)} · {bpm_label} · {key_label} · FX {fx_label} · {sound.get('license')}")
            if sound.get("license") == CC_BY:
                print(f"  CREDIT: {attribution}")
            time.sleep(args.delay)

        if downloaded_here < needed:
            print(f"  Nur {downloaded_here} von {needed} angefragten Loops gefunden.")
        if args.delay:
            time.sleep(args.delay)

    if database is not None:
        database.close()
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Importiert lokale WAV-Loops und/oder nutzt Freesound API v2; EQ und Kompressor werden standardmäßig auf Bibliothekskopien angewendet."
    )
    parser.add_argument("--output", type=Path, default=Path("./laermbatz_loops"), help="Zielbibliothek (Standard: ./laermbatz_loops)")
    parser.add_argument("--database", type=Path, help="SQLite-Indexdatei (Standard: <output>/loops.sqlite3)")
    parser.add_argument("--import-dir", type=Path, help="Lokaler Ordner mit manuell gespeicherten WAVs, z. B. Browser-Downloads")
    parser.add_argument("--local-only", action="store_true", help="Nur --import-dir analysieren/importieren; keine Freesound-Anfragen stellen")
    parser.add_argument("--loops-per-category", type=int, default=1, help="Zielanzahl je Kategorie, 1–10 (Standard: 1)")
    parser.add_argument("--max-total-mib", type=int, default=DEFAULT_LIBRARY_BYTES // (1024 * 1024), help="Maximaler lokaler Gesamtspeicher in MiB (Standard: 256)")
    parser.add_argument("--target-bpm", type=float, default=128.0, help="BPM für Takt-Fallback bei unsicherer Analyse (80–160, Standard: 128)")
    parser.add_argument("--effects", default="eq,compressor", help="Automatische FX-Kette in Reihenfolge: eq,compressor (Standard); none deaktiviert sie")
    parser.add_argument("--delay", type=float, default=1.0, help="Pause zwischen Kategorien/Downloads in Sekunden (Standard: 1)")
    parser.add_argument("--allow-cc-by", action="store_true", help="CC BY zusätzlich zu CC0 erlauben; Credits werden als JSON gespeichert und ausgegeben")
    parser.add_argument("--dry-run", action="store_true", help="Lokale Importe und Freesound-Treffer nur anzeigen, nichts kopieren/herunterladen")
    parser.add_argument("--confirm-api-terms", action="store_true", help="Bestätigt, dass die aktuellen Freesound-API-Bedingungen geprüft wurden")
    args = parser.parse_args()
    if not 1 <= args.loops_per_category <= 10:
        parser.error("--loops-per-category muss zwischen 1 und 10 liegen.")
    if not 1 <= args.max_total_mib <= 4096:
        parser.error("--max-total-mib muss zwischen 1 und 4096 liegen.")
    if not 80.0 <= args.target_bpm <= 160.0:
        parser.error("--target-bpm muss zwischen 80 und 160 liegen.")
    if not math.isfinite(args.delay) or args.delay < 0:
        parser.error("--delay muss eine endliche, nicht negative Zahl sein.")
    effect_names = {"eq", "compressor"}
    raw_effects = args.effects.strip().casefold()
    if raw_effects in {"none", "off", "keine"}:
        args.effects = []
    else:
        parsed_effects = [item.strip().casefold() for item in raw_effects.split(",") if item.strip()]
        if not parsed_effects or any(item not in effect_names for item in parsed_effects):
            parser.error("--effects akzeptiert eq,compressor oder none.")
        if len(parsed_effects) != len(set(parsed_effects)):
            parser.error("Jeder Effekt darf in --effects nur einmal vorkommen.")
        args.effects = parsed_effects
    if args.local_only and args.import_dir is None:
        parser.error("--local-only benötigt zusätzlich --import-dir.")
    args.max_total_bytes = args.max_total_mib * 1024 * 1024
    return args


def main() -> int:
    try:
        return process(parse_args())
    except KeyboardInterrupt:
        print("Abgebrochen.", file=sys.stderr)
        return 130
    except Exception as error:
        print(f"Fehler: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

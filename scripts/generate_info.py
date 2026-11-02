#!/usr/bin/env python3
"""Generate Aurora TV info-channel episodes.

Pipeline:
  episodes.json + screenshots
    -> Russian TTS (Windows SAPI, Microsoft Irina)
    -> 1920x1080 frames (Pillow)
    -> H.264/AAC MP4 segments (ffmpeg)
    -> one MP4 per episode + info/index.json

Templates: trailer, tutorial, whatsnew.
"""

from __future__ import annotations

import argparse
import json
import math
import subprocess
import sys
import wave
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps

try:
    import imageio_ffmpeg
    FFMPEG = Path(imageio_ffmpeg.get_ffmpeg_exe())
except Exception:
    FFMPEG = Path("ffmpeg")

ROOT = Path(__file__).resolve().parents[1]
INFO = ROOT / "info"
SCREENS = INFO / "screens"
AUDIO = INFO / "audio"
BUILD = INFO / "build"
FRAMES = INFO / "frames"
FONTS = Path("C:/Windows/Fonts")

W, H = 1920, 1080
FPS = 30
BG_TOP = (7, 9, 12)
BG_BOTTOM = (17, 24, 34)
SURFACE = (17, 23, 32)
ACCENT = (139, 213, 255)
TEXT = (242, 245, 249)
DIM = (147, 160, 180)
FAINT = (95, 107, 124)
WHITE = (255, 255, 255)


def rgb(value: tuple[int, int, int]) -> tuple[int, int, int]:
    return value


def font(size: int, weight: str = "regular") -> ImageFont.FreeTypeFont:
    names = {
        "light": "segoeuil.ttf",
        "regular": "segoeui.ttf",
        "bold": "segoeuib.ttf",
    }
    path = FONTS / names.get(weight, "segoeui.ttf")
    if not path.exists():
        path = FONTS / "arial.ttf"
    return ImageFont.truetype(str(path), size)


def gradient() -> Image.Image:
    image = Image.new("RGB", (W, H), BG_TOP)
    draw = ImageDraw.Draw(image)
    for y in range(H):
        t = y / (H - 1)
        color = tuple(
            int(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3)
        )
        draw.line((0, y, W, y), fill=color)

    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse((-420, -360, 1050, 720), fill=(*ACCENT, 24))
    glow_draw.ellipse((1250, 700, 2200, 1500), fill=(90, 130, 255, 14))
    glow = glow.filter(ImageFilter.GaussianBlur(150))
    return Image.alpha_composite(image.convert("RGBA"), glow).convert("RGB")


def text_width(draw: ImageDraw.ImageDraw, text: str, f: ImageFont.FreeTypeFont) -> int:
    return int(draw.textlength(text, font=f))


def wrap(draw: ImageDraw.ImageDraw, text: str, f: ImageFont.FreeTypeFont, max_width: int) -> list[str]:
    lines: list[str] = []
    for paragraph in text.split("\n"):
        if not paragraph:
            lines.append("")
            continue
        words = paragraph.split()
        current = ""
        for word in words:
            candidate = f"{current} {word}".strip()
            if text_width(draw, candidate, f) <= max_width:
                current = candidate
            else:
                if current:
                    lines.append(current)
                current = word
        if current:
            lines.append(current)
    return lines


def draw_lines(
    draw: ImageDraw.ImageDraw,
    lines: list[str],
    f: ImageFont.FreeTypeFont,
    fill: tuple[int, int, int],
    x: int,
    y: int,
    line_height: int,
) -> int:
    for line in lines:
        draw.text((x, y), line, font=f, fill=fill)
        y += line_height
    return y


def draw_logo(draw: ImageDraw.ImageDraw, x: int, y: int, size: int = 28) -> None:
    f = font(size, "light")
    draw.text((x, y), "AURORA", font=f, fill=TEXT)
    logo_width = text_width(draw, "AURORA", f)
    badge_x = x + logo_width + int(size * 0.45)
    badge_y = y + int(size * 0.2)
    badge_w = int(size * 1.45)
    badge_h = int(size * 0.9)
    draw.rounded_rectangle(
        (badge_x, badge_y, badge_x + badge_w, badge_y + badge_h),
        radius=max(4, int(size * 0.18)),
        fill=ACCENT,
    )
    badge_font = font(max(10, int(size * 0.48)), "bold")
    draw.text(
        (badge_x + badge_w / 2, badge_y + badge_h / 2),
        "TV",
        font=badge_font,
        fill=(7, 16, 24),
        anchor="mm",
    )


def draw_tag(draw: ImageDraw.ImageDraw, text: str, x: int, y: int, color: tuple[int, int, int] = ACCENT) -> None:
    f = font(20, "bold")
    padding_x, padding_y = 14, 8
    width = text_width(draw, text, f) + padding_x * 2
    height = 36
    draw.rounded_rectangle(
        (x, y, x + width, y + height),
        radius=8,
        fill=(24, 34, 46),
        outline=color,
        width=1,
    )
    draw.text((x + padding_x, y + padding_y), text, font=f, fill=color)


def title_frame(scene: dict, template: str, episode: dict) -> Image.Image:
    image = gradient()
    draw = ImageDraw.Draw(image)
    draw_logo(draw, 80, 58, 30)
    draw_tag(draw, f"AURORA TV {episode.get('version', '1.4')}", W - 310, 56, DIM)

    title = scene.get("title", "")
    subtitle = scene.get("subtitle", "")
    version = scene.get("version", "")

    title_size = 124 if len(title) <= 11 else 98 if len(title) <= 17 else 78
    title_font = font(title_size, "bold")
    title_lines = wrap(draw, title, title_font, W - 180)
    y = 300
    if version:
        badge_font = font(30, "bold")
        draw.rounded_rectangle((80, 210, 80 + text_width(draw, version, badge_font) + 44, 272), radius=10, fill=ACCENT)
        draw.text((102, 222), version, font=badge_font, fill=(7, 16, 24))
        y = 330
    y = draw_lines(draw, title_lines, title_font, TEXT, 80, y, int(title_size * 1.02))
    y += 18
    draw.rectangle((80, y, 260, y + 5), fill=ACCENT)
    y += 34
    if subtitle:
        subtitle_font = font(38, "light")
        subtitle_lines = wrap(draw, subtitle, subtitle_font, W - 220)
        y = draw_lines(draw, subtitle_lines, subtitle_font, DIM, 80, y, 52)

    footer_font = font(20, "regular")
    draw.text((80, H - 80), "AURORA TV  •  ANDROID TV", font=footer_font, fill=FAINT)

    if template == "trailer":
        overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        od = ImageDraw.Draw(overlay)
        od.polygon([(W - 520, 0), (W, 0), (W, 420), (W - 300, 260)], fill=(*ACCENT, 18))
        od.polygon([(W - 340, 0), (W, 0), (W, 220), (W - 180, 150)], fill=(*ACCENT, 12))
        image = Image.alpha_composite(image.convert("RGBA"), overlay).convert("RGB")
    return image


def load_screenshot(name: str, focus: list[int] | None, zoom: bool) -> Image.Image:
    path = SCREENS / name
    image = Image.open(path).convert("RGB")
    if focus and zoom:
        x, y, w, h = focus
        pad = 90
        box = (max(0, x - pad), max(0, y - pad), min(image.width, x + w + pad), min(image.height, y + h + pad))
        image = image.crop(box)
    return ImageOps.fit(image, (W, H), Image.Resampling.LANCZOS)


def screenshot_frame(scene: dict, template: str, episode: dict) -> Image.Image:
    image = load_screenshot(scene.get("file", "home.png"), scene.get("focus"), bool(scene.get("zoom")))
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    # Solid top bar hides the screenshot's own header and the duplicate logo.
    od.rectangle((0, 0, W, 128), fill=(4, 7, 10, 248))
    # Bottom panel keeps captions readable over any screenshot.
    for y in range(H - 350, H - 300):
        t = (y - (H - 350)) / 50
        od.line((0, y, W, y), fill=(4, 7, 10, int(80 + 165 * t)))
    od.rectangle((0, H - 300, W, H), fill=(4, 7, 10, 245))
    od.rectangle((0, H - 302, W, H - 298), fill=(*ACCENT, 120))

    focus = scene.get("focus")
    if focus and not scene.get("zoom"):
        x, y, w, h = focus
        dim = Image.new("RGBA", (W, H), (0, 0, 0, 120))
        dd = ImageDraw.Draw(dim)
        dd.rectangle((x, y, x + w, y + h), fill=(0, 0, 0, 0))
        overlay = Image.alpha_composite(overlay, dim)
        od = ImageDraw.Draw(overlay)
        od.rounded_rectangle((x - 8, y - 8, x + w + 8, y + h + 8), radius=14, outline=ACCENT, width=5)

    image = Image.alpha_composite(image.convert("RGBA"), overlay).convert("RGB")
    draw = ImageDraw.Draw(image)
    draw_logo(draw, 64, 46, 26)
    draw_tag(draw, f"AURORA TV {episode.get('version', '1.4')}", W - 300, 46, DIM)

    caption = scene.get("caption", "")
    title = scene.get("title", "")
    step = scene.get("step")
    x_text = 190 if step else 80
    if step:
        cx, cy = 112, H - 166
        draw.ellipse((cx - 44, cy - 44, cx + 44, cy + 44), fill=ACCENT)
        step_font = font(34, "bold")
        draw.text((cx, cy - 2), str(step), font=step_font, fill=(7, 16, 24), anchor="mm")

    if title:
        title_font = font(36 if template != "trailer" else 48, "bold")
        draw.text((x_text, H - 218), title, font=title_font, fill=TEXT)
        y = H - 168
    else:
        y = H - 190
    if caption:
        caption_font = font(28 if template != "trailer" else 32, "regular")
        lines = wrap(draw, caption, caption_font, W - x_text - 100)
        draw_lines(draw, lines[:2], caption_font, DIM, x_text, y, 38)
    return image


def tutorial_frame(scene: dict, episode: dict) -> Image.Image:
    image = gradient()
    draw = ImageDraw.Draw(image)
    draw_logo(draw, 80, 58, 30)
    draw_tag(draw, f"AURORA TV {episode.get('version', '1.4')}", W - 310, 56, DIM)

    step = scene.get("step")
    if step is not None:
        cx, cy = 150, 290
        draw.ellipse((cx - 54, cy - 54, cx + 54, cy + 54), fill=ACCENT)
        step_font = font(40, "bold")
        draw.text((cx, cy - 2), str(step), font=step_font, fill=(7, 16, 24), anchor="mm")

    title = scene.get("title", "")
    title_font = font(48, "bold")
    title_lines = wrap(draw, title, title_font, 600)
    y = 380
    y = draw_lines(draw, title_lines, title_font, TEXT, 80, y, 56)
    draw.rectangle((80, y + 8, 240, y + 13), fill=ACCENT)
    y += 46

    caption = scene.get("caption", "")
    if caption:
        caption_font = font(27, "regular")
        caption_lines = wrap(draw, caption, caption_font, 590)
        draw_lines(draw, caption_lines, caption_font, DIM, 80, y, 38)

    card_x, card_y, card_w, card_h = 760, 220, 1080, 608
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle(
        (card_x - 14, card_y - 14, card_x + card_w + 14, card_y + card_h + 14),
        radius=18,
        fill=(0, 0, 0, 130),
    )
    image = Image.alpha_composite(image.convert("RGBA"), shadow).convert("RGB")
    draw = ImageDraw.Draw(image)
    shot = load_screenshot(scene.get("file", "home.png"), scene.get("focus"), bool(scene.get("zoom")))
    shot = ImageOps.fit(shot, (card_w, card_h), Image.Resampling.LANCZOS)
    image.paste(shot, (card_x, card_y))
    draw.rounded_rectangle(
        (card_x - 2, card_y - 2, card_x + card_w + 2, card_y + card_h + 2),
        radius=16,
        outline=ACCENT,
        width=3,
    )
    footer_font = font(20, "regular")
    draw.text((80, H - 80), "AURORA TV  •  ANDROID TV", font=footer_font, fill=FAINT)
    return image


def feature_frame(scene: dict, episode: dict) -> Image.Image:
    image = gradient()
    draw = ImageDraw.Draw(image)
    draw_logo(draw, 80, 58, 30)
    draw_tag(draw, f"AURORA TV {episode.get('version', '1.4')}", W - 310, 56, DIM)

    title = scene.get("title", "")
    title_font = font(62, "bold")
    draw.text((80, 220), title, font=title_font, fill=TEXT)
    draw.rectangle((80, 310, 250, 315), fill=ACCENT)

    bullets = scene.get("bullets", [])
    bullet_font = font(31, "regular")
    y = 380
    for bullet in bullets:
        draw.ellipse((84, y + 14, 96, y + 26), fill=ACCENT)
        draw.text((120, y), bullet, font=bullet_font, fill=DIM)
        y += 58

    file_name = scene.get("file")
    if file_name:
        shot = load_screenshot(file_name, scene.get("focus"), True)
        shot = ImageOps.fit(shot, (830, 467), Image.Resampling.LANCZOS)
        x, y2 = 980, 240
        shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        sd = ImageDraw.Draw(shadow)
        sd.rounded_rectangle((x - 12, y2 - 12, x + 842, y2 + 479), radius=16, fill=(0, 0, 0, 120))
        image = Image.alpha_composite(image.convert("RGBA"), shadow).convert("RGB")
        draw = ImageDraw.Draw(image)
        draw.rounded_rectangle((x - 2, y2 - 2, x + 832, y2 + 469), radius=14, outline=ACCENT, width=3)
        image.paste(shot, (x, y2))

    footer_font = font(20, "regular")
    draw.text((80, H - 80), "AURORA TV  •  ANDROID TV", font=footer_font, fill=FAINT)
    return image


def outro_frame(scene: dict, episode: dict) -> Image.Image:
    image = gradient()
    draw = ImageDraw.Draw(image)
    draw_logo(draw, 80, 58, 30)
    title = scene.get("title", "AURORA TV")
    title_font = font(92 if len(title) > 12 else 112, "bold")
    title_width = text_width(draw, title, title_font)
    draw.text(((W - title_width) / 2, 350), title, font=title_font, fill=TEXT)
    subtitle = scene.get("subtitle", "")
    if subtitle:
        subtitle_font = font(36, "light")
        subtitle_width = text_width(draw, subtitle, subtitle_font)
        draw.text(((W - subtitle_width) / 2, 500), subtitle, font=subtitle_font, fill=ACCENT)
    text = scene.get("text", "")
    if text:
        text_font = font(28, "regular")
        lines = wrap(draw, text, text_font, W - 300)
        y = 600
        for line in lines:
            line_width = text_width(draw, line, text_font)
            draw.text(((W - line_width) / 2, y), line, font=text_font, fill=DIM)
            y += 40
    draw.rectangle((W // 2 - 70, 760, W // 2 + 70, 765), fill=ACCENT)
    footer_font = font(20, "regular")
    footer = "AURORA TV  •  ANDROID TV"
    draw.text(((W - text_width(draw, footer, footer_font)) / 2, H - 80), footer, font=footer_font, fill=FAINT)
    return image


def render_frame(scene: dict, template: str, episode: dict) -> Image.Image:
    kind = scene.get("type", "title")
    if kind == "title":
        return title_frame(scene, template, episode)
    if kind in ("screenshot", "step"):
        if template == "tutorial":
            return tutorial_frame(scene, episode)
        return screenshot_frame(scene, template, episode)
    if kind == "feature":
        return feature_frame(scene, episode)
    if kind == "outro":
        return outro_frame(scene, episode)
    return title_frame(scene, template, episode)


def audio_duration(path: Path) -> float:
    try:
        with wave.open(str(path), "rb") as handle:
            return handle.getnframes() / float(handle.getframerate())
    except Exception:
        return 0.0


def make_silence(path: Path, duration: float) -> None:
    rate = 22050
    frames = int(rate * duration)
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        handle.writeframes(b"\x00\x00" * frames)


def make_music(path: Path, duration: float) -> None:
    """Simple original ambient bed; no external music assets."""
    rate = 48000
    total = int(rate * (duration + 4))
    chords = [
        (110.00, 130.81, 164.81),
        (87.31, 110.00, 130.81),
        (130.81, 164.81, 196.00),
        (98.00, 123.47, 146.83),
    ]
    chord_len = 8.0
    samples = []
    for i in range(total):
        t = i / rate
        chord = chords[int(t / chord_len) % len(chords)]
        local = t % chord_len
        envelope = min(1.0, local / 0.6, (chord_len - local) / 0.8)
        value = 0.0
        for frequency in chord:
            value += math.sin(2 * math.pi * frequency * t)
        value += 0.35 * math.sin(2 * math.pi * 55.0 * t)
        value *= 0.05 * max(0.0, envelope)
        # gentle pulse
        value *= 0.85 + 0.15 * math.sin(2 * math.pi * 0.25 * t)
        samples.append(value)

    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(2)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        frames = bytearray()
        for value in samples:
            sample = int(max(-1.0, min(1.0, value)) * 32767)
            frames += sample.to_bytes(2, "little", signed=True) * 2
        handle.writeframes(bytes(frames))


def run(command: list[str]) -> None:
    print("  $", " ".join(str(part) for part in command[:8]), "...")
    result = subprocess.run([str(part) for part in command], capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout)
        print(result.stderr)
        raise SystemExit(f"command failed: {command[0]}")


def generate_tts(jobs: list[dict]) -> None:
    jobs_file = INFO / "tts_jobs.json"
    jobs_file.write_text(json.dumps(jobs, ensure_ascii=False, indent=2), encoding="utf-8")
    script = ROOT / "scripts" / "tts_irina.ps1"
    run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script), str(jobs_file)])


def build_segment(frame: Path, audio: Path, duration: float, out: Path) -> None:
    fade_out = max(0.0, duration - 0.35)
    video_filter = (
        f"fade=t=in:st=0:d=0.35,fade=t=out:st={fade_out:.2f}:d=0.35,format=yuv420p"
    )
    run([
        FFMPEG, "-y",
        "-loop", "1", "-framerate", str(FPS), "-i", str(frame),
        "-i", str(audio),
        "-t", f"{duration:.3f}",
        "-vf", video_filter,
        "-af", "aresample=48000,apad",
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "160k", "-ar", "48000", "-ac", "2",
        "-movflags", "+faststart",
        str(out),
    ])


def concat_segments(segments: list[Path], out: Path) -> None:
    list_file = BUILD / "concat.txt"
    list_file.write_text(
        "\n".join(f"file '{segment.as_posix()}'" for segment in segments) + "\n",
        encoding="utf-8",
    )
    run([
        FFMPEG, "-y", "-f", "concat", "-safe", "0", "-i", str(list_file),
        "-c", "copy", "-movflags", "+faststart", str(out),
    ])


def mix_music(voice: Path, music: Path, out: Path, duration: float) -> None:
    fade_out = max(0.0, duration - 3.0)
    run([
        FFMPEG, "-y", "-i", str(voice), "-i", str(music),
        "-filter_complex",
        f"[1:a]volume=0.12,afade=t=in:st=0:d=2,afade=t=out:st={fade_out:.2f}:d=3[bg];"
        f"[0:a][bg]amix=inputs=2:duration=first:dropout_transition=0[a]",
        "-map", "0:v", "-map", "[a]",
        "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
        "-movflags", "+faststart", str(out),
    ])


def build_episode(episode: dict, skip_tts: bool) -> dict:
    episode_id = episode["id"]
    template = episode.get("template", "tutorial")
    scenes = episode.get("scenes", [])
    print(f"\n=== {episode_id} ({template}, {len(scenes)} scenes)")

    jobs = []
    for index, scene in enumerate(scenes):
        text = (scene.get("text") or "").strip()
        output = AUDIO / f"{episode_id}_{index:02d}.wav"
        jobs.append({
            "output": str(output),
            "text": text,
            "rate": int(scene.get("voice_rate", episode.get("voice_rate", 0))),
        })
        if not text:
            make_silence(output, float(scene.get("duration", 4.0)))

    if not skip_tts:
        generate_tts([job for job in jobs if job["text"]])

    segments = []
    total_duration = 0.0
    for index, scene in enumerate(scenes):
        audio = AUDIO / f"{episode_id}_{index:02d}.wav"
        speech = audio_duration(audio) if audio.exists() else 0.0
        minimum = float(scene.get("duration", 4.0))
        duration = max(minimum, speech + 0.55)
        frame = render_frame(scene, template, episode)
        frame_path = FRAMES / f"{episode_id}_{index:02d}.png"
        frame.save(frame_path)
        segment = BUILD / f"{episode_id}_{index:02d}.mp4"
        print(f"  scene {index + 1}/{len(scenes)}: {duration:.1f}s, audio {speech:.1f}s")
        build_segment(frame_path, audio, duration, segment)
        segments.append(segment)
        total_duration += duration

    voice = BUILD / f"{episode_id}_voice.mp4"
    concat_segments(segments, voice)

    final = BUILD / f"{episode_id}.mp4"
    if episode.get("music"):
        music = BUILD / f"{episode_id}_music.wav"
        make_music(music, total_duration)
        mix_music(voice, music, final, total_duration)
    else:
        final.write_bytes(voice.read_bytes())

    print(f"  done: {final} ({total_duration:.1f}s)")
    return {
        "id": episode_id,
        "title": episode.get("title", episode_id),
        "description": episode.get("description", ""),
        "duration": round(total_duration, 1),
        "file": final.name,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", help="comma-separated episode ids")
    parser.add_argument("--skip-tts", action="store_true")
    args = parser.parse_args()

    for directory in (AUDIO, BUILD, FRAMES):
        directory.mkdir(parents=True, exist_ok=True)

    data = json.loads((INFO / "episodes.json").read_text(encoding="utf-8"))
    selected = set(args.only.split(",")) if args.only else None
    result = []
    for episode in data.get("episodes", []):
        if selected and episode["id"] not in selected:
            continue
        result.append(build_episode(episode, args.skip_tts))

    index = {
        "version": data.get("version", "1.4"),
        "episodes": result,
    }
    (INFO / "index.json").write_text(
        json.dumps(index, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"\nindex: {INFO / 'index.json'}")


if __name__ == "__main__":
    main()

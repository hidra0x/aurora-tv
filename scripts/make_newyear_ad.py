"""Generate a short New Year ident/ad for the cinema ad break."""

from __future__ import annotations

import math
import random
import subprocess
import tempfile
from pathlib import Path

import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFilter

import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_kids_startup import draw_accessory, draw_owl, font, star_points  # noqa: E402

ROOT = Path(r"C:\AuroraTV")
RAW = ROOT / "app" / "src" / "main" / "res" / "raw"
AMBIENT = RAW / "cinema_ambient.mp3"
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
W, H, FPS = 1280, 720, 24
DURATION = 18.0
DARK = (10, 24, 40)
RED = (192, 57, 43)
TEAL = (46, 111, 106)
GOLD = (242, 180, 65)
CREAM = (255, 246, 222)

random.seed(20261231)
FLAKES = [(random.random() * W, random.random() * H, 1 + random.random() * 3.5, 15 + random.random() * 45) for _ in range(90)]


def background(t: float) -> Image.Image:
    image = Image.new("RGBA", (W, H), DARK + (255,))
    draw = ImageDraw.Draw(image)
    # Warm glow.
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((-200, -250, 800, 650), fill=(192, 57, 43, 90))
    gd.ellipse((650, 180, 1500, 950), fill=(46, 111, 106, 95))
    glow = glow.filter(ImageFilter.GaussianBlur(100))
    image = Image.alpha_composite(image, glow)
    draw = ImageDraw.Draw(image)
    # Snow.
    for x, y, r, speed in FLAKES:
        yy = (y + speed * t) % (H + 30) - 15
        xx = (x + 18 * math.sin(t * 0.4 + y)) % W
        draw.ellipse((xx - r, yy - r, xx + r, yy + r), fill=(255, 255, 255, 150))
    # Red/teal corner shapes.
    draw.polygon([(0, 0), (230, 0), (60, H), (0, H)], fill=RED + (215,))
    draw.polygon([(W, 0), (W - 170, 0), (W - 40, H), (W, H)], fill=TEAL + (215,))
    draw.line((0, H - 54, W, H - 54), fill=GOLD + (120,), width=2)
    return image


def text_center(draw: ImageDraw.ImageDraw, y: int, text: str, size: int, fill, bold: bool = True) -> None:
    draw.text((W // 2, y), text, font=font(size, bold), fill=fill, anchor="ma")


def frame(t: float) -> Image.Image:
    image = background(t)
    draw = ImageDraw.Draw(image)
    pulse = 0.5 + 0.5 * math.sin(t * 1.6)
    if t < 4.2:
        draw_owl(draw, 270, 310, 0.9)
        draw_accessory(draw, 270, 310, 0.9, "santa")
        text_center(draw, 170, "AURORA TV", 28, GOLD + (240,))
        text_center(draw, 220, "НОВОГОДНИЙ СЕАНС", 58, CREAM + (255,))
        text_center(draw, 330, "СКОРО ЗИМА", 80, GOLD + (255,))
        text_center(draw, 450, "МАНДАРИНЫ  •  ШАМПУСЁНОК", 32, CREAM + (235,))
    elif t < 8.4:
        text_center(draw, 170, "СКОРО", 86, CREAM + (255,))
        text_center(draw, 275, "ЗИМА", 150, GOLD + (255,))
        for index in range(10):
            angle = index * math.tau / 10 + t * 0.6
            x = W // 2 + 470 * math.cos(angle)
            y = 470 + 150 * math.sin(angle)
            draw.polygon(star_points(x, y, 14 + 4 * pulse, inner_ratio=0.35, points=6), fill=(255, 252, 242, 180))
    elif t < 12.6:
        text_center(draw, 150, "МАНДАРИНЫ", 130, (255, 190, 80, 255))
        for index in range(8):
            x = 170 + index * 140
            y = 400 + (index % 2) * 40
            draw.ellipse((x - 46, y - 46, x + 46, y + 46), fill=(240, 140, 40, 255), outline=(160, 70, 20, 255), width=4)
            draw.ellipse((x + 10, y - 55, x + 36, y - 32), fill=(100, 160, 80, 255))
        text_center(draw, 540, "Праздничное настроение в каждом кадре", 26, CREAM + (230,))
    elif t < 16.4:
        text_center(draw, 155, "ШАМПУСЁНОК", 92, GOLD + (255,))
        text_center(draw, 310, "С НОВЫМ ГОДОМ!", 60, CREAM + (255,))
        text_center(draw, 430, "Семейный эфир Aurora TV", 30, (190, 215, 235, 235))
    else:
        draw_owl(draw, 330, 330, 0.95)
        draw_accessory(draw, 330, 330, 0.95, "santa")
        text_center(draw, 170, "С НОВЫМ ГОДОМ!", 88, GOLD + (255,))
        text_center(draw, 300, "AURORA TV", 54, CREAM + (255,))
        text_center(draw, 390, "НОВОГОДНИЙ ЭФИР  •  КАНАЛЫ 15–18", 26, (190, 215, 235, 235))
    return image.convert("RGB")


def main() -> None:
    RAW.mkdir(parents=True, exist_ok=True)
    silent = Path(tempfile.gettempdir()) / "ad_newyear.silent.mp4"
    proc = subprocess.Popen(
        [
            FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
            "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
            "-r", str(FPS), "-i", "pipe:0", "-an",
            "-c:v", "libx264", "-profile:v", "main", "-level", "4.0",
            "-preset", "medium", "-crf", "24", "-pix_fmt", "yuv420p", str(silent),
        ],
        stdin=subprocess.PIPE,
    )
    count = 0
    for index in range(int(DURATION * FPS)):
        proc.stdin.write(frame(index / FPS).tobytes())
        count += 1
    proc.stdin.close()
    if proc.wait() != 0:
        raise RuntimeError("ffmpeg failed: ad_newyear")

    out = RAW / "ad_newyear.mp4"
    subprocess.run(
        [
            FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(silent), "-stream_loop", "-1", "-i", str(AMBIENT),
            "-t", f"{DURATION:.3f}", "-map", "0:v", "-map", "1:a",
            "-c:v", "copy", "-c:a", "aac", "-b:a", "128k",
            "-af", "volume=0.18", "-movflags", "+faststart", str(out),
        ],
        check=True,
    )
    print(f"ad_newyear.mp4: {count} frames, {out.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()

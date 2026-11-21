"""Build the rebranded Sovyonok kids channel library.

Creates original Soviet-style diafilm stories and English lessons:
  * Колобок, Теремок, Репка — original illustrations and narration;
  * Английский с Совёнком — greeting, colours, animals (RU + EN voices).

No third-party cartoon footage or music is used. The stories are based on
public-domain Russian folk tales; the drawings and voice are original.
"""

from __future__ import annotations

import argparse
import json
import math
import shutil
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS))
from make_kids_startup import draw_owl, font, star_points  # noqa: E402

ASSETS = ROOT / "app" / "src" / "main" / "assets" / "kids"
MEDIA = ASSETS / "media"
CATALOG_ASSET = ASSETS / "catalog.json"
CATALOG_LOCAL = ROOT / "kids" / "catalog.json"
AUDIO = ROOT / "kids" / "audio" / "sovyonok"
TEMP = ROOT / "kids" / "build"
AMBIENT = ROOT / "app" / "src" / "main" / "res" / "raw" / "cinema_ambient.mp3"
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()

W, H, FPS = 1280, 720, 24
ART_H = 520
CREAM = (244, 227, 193)
RED = (192, 57, 43)
TEAL = (46, 111, 106)
DARK = (11, 27, 43)
GOLD = (242, 180, 65)
INK = (38, 38, 38)
WHITE = (255, 252, 242)

RU_VOICE = "Microsoft Irina Desktop"
EN_VOICE = "Microsoft Zira Desktop"

STORY_BACKGROUNDS: dict[str, Image.Image] = {}


def gradient(top: tuple[int, int, int], bottom: tuple[int, int, int], size: tuple[int, int]) -> Image.Image:
    width, height = size
    image = Image.new("RGB", size, top)
    draw = ImageDraw.Draw(image)
    for y in range(height):
        ratio = y / max(1, height - 1)
        color = tuple(int(top[i] + (bottom[i] - top[i]) * ratio) for i in range(3))
        draw.line((0, y, width, y), fill=color)
    return image


def base_landscape() -> Image.Image:
    image = gradient((188, 222, 220), (244, 227, 193), (W, ART_H)).convert("RGBA")
    draw = ImageDraw.Draw(image)
    # Sun.
    draw.ellipse((1040, 40, 1130, 130), fill=(255, 209, 102, 255), outline=(230, 150, 40, 255), width=3)
    for index in range(12):
        angle = index * math.tau / 12
        draw.line(
            (
                1085 + 58 * math.cos(angle),
                85 + 58 * math.sin(angle),
                1085 + 80 * math.cos(angle),
                85 + 80 * math.sin(angle),
            ),
            fill=(230, 150, 40, 230),
            width=3,
        )
    # Clouds.
    for cx, cy, scale in [(240, 70, 1.0), (650, 50, 0.7)]:
        for dx, dy, rx, ry in [(-40, 0, 50, 24), (0, -12, 58, 28), (48, 0, 44, 22)]:
            draw.ellipse(
                (cx + (dx - rx) * scale, cy + (dy - ry) * scale, cx + (dx + rx) * scale, cy + (dy + ry) * scale),
                fill=(255, 255, 255, 190),
            )
    # Hills.
    draw.ellipse((-260, 300, 760, 760), fill=(126, 178, 120, 255))
    draw.ellipse((420, 330, 1380, 800), fill=(105, 158, 107, 255))
    draw.rectangle((0, 460, W, ART_H), fill=(139, 190, 118, 255))
    # Path.
    draw.polygon([(240, 520), (420, 520), (860, 380), (790, 350)], fill=(226, 199, 145, 255))
    # Birches.
    for x, scale in [(1020, 1.0), (70, 0.8)]:
        draw.rectangle((x - 8 * scale, 250, x + 8 * scale, 470), fill=(245, 238, 220, 255))
        for y in range(270, 450, 28):
            draw.line((x - 7 * scale, y, x + 7 * scale, y + 6 * scale), fill=(70, 60, 50, 230), width=2)
        draw.ellipse((x - 70 * scale, 210, x + 70 * scale, 340), fill=(86, 144, 88, 255))
        draw.ellipse((x - 95 * scale, 250, x + 20 * scale, 350), fill=(100, 160, 95, 255))
    # Izba.
    draw.rectangle((100, 330, 290, 480), fill=(164, 110, 66, 255), outline=(90, 55, 30, 255), width=4)
    draw.polygon([(80, 330), (195, 250), (310, 330)], fill=(150, 64, 48, 255), outline=(90, 35, 25, 255))
    draw.rectangle((160, 390, 230, 480), fill=(104, 66, 40, 255))
    draw.rectangle((120, 360, 155, 395), fill=(255, 226, 150, 255), outline=(90, 55, 30, 255), width=2)
    draw.rectangle((240, 360, 275, 395), fill=(255, 226, 150, 255), outline=(90, 55, 30, 255), width=2)
    draw.line((120, 377, 155, 377), fill=(90, 55, 30, 255), width=2)
    draw.line((240, 377, 275, 377), fill=(90, 55, 30, 255), width=2)
    draw.rectangle((250, 260, 272, 300), fill=(120, 70, 50, 255))
    # Flowers.
    for x, y in [(540, 480), (600, 492), (900, 476), (950, 490), (400, 500)]:
        draw.ellipse((x - 6, y - 6, x + 6, y + 6), fill=GOLD + (220,))
        draw.line((x, y + 6, x, y + 20), fill=(70, 120, 70, 255), width=2)
    return image


def draw_grandpa(draw: ImageDraw.ImageDraw, x: float, y: float, scale: float = 1.0) -> None:
    s = scale
    draw.ellipse((x - 26 * s, y - 110 * s, x + 26 * s, y - 58 * s), fill=(245, 196, 160, 255), outline=INK, width=2)
    draw.polygon([(x - 30 * s, y - 96 * s), (x, y - 132 * s), (x + 30 * s, y - 96 * s)], fill=(90, 70, 55, 255))
    draw.ellipse((x - 18 * s, y - 80 * s, x - 7 * s, y - 69 * s), fill=INK)
    draw.ellipse((x + 7 * s, y - 80 * s, x + 18 * s, y - 69 * s), fill=INK)
    draw.ellipse((x - 18 * s, y - 74 * s, x + 18 * s, y - 44 * s), fill=(245, 245, 240, 255))
    draw.rounded_rectangle((x - 34 * s, y - 58 * s, x + 34 * s, y + 30 * s), radius=12 * s, fill=(178, 60, 48, 255), outline=INK, width=2)
    draw.rectangle((x - 28 * s, y + 30 * s, x - 6 * s, y + 90 * s), fill=(70, 55, 45, 255))
    draw.rectangle((x + 6 * s, y + 30 * s, x + 28 * s, y + 90 * s), fill=(70, 55, 45, 255))
    draw.ellipse((x - 34 * s, y + 82 * s, x - 2 * s, y + 98 * s), fill=(55, 40, 30, 255))
    draw.ellipse((x + 2 * s, y + 82 * s, x + 34 * s, y + 98 * s), fill=(55, 40, 30, 255))


def draw_grandma(draw: ImageDraw.ImageDraw, x: float, y: float, scale: float = 1.0) -> None:
    s = scale
    draw.ellipse((x - 25 * s, y - 104 * s, x + 25 * s, y - 54 * s), fill=(245, 196, 160, 255), outline=INK, width=2)
    draw.polygon([(x - 30 * s, y - 96 * s), (x, y - 126 * s), (x + 30 * s, y - 96 * s)], fill=(60, 100, 150, 255))
    draw.ellipse((x - 18 * s, y - 83 * s, x - 7 * s, y - 72 * s), fill=INK)
    draw.ellipse((x + 7 * s, y - 83 * s, x + 18 * s, y - 72 * s), fill=INK)
    draw.arc((x - 12 * s, y - 70 * s, x + 12 * s, y - 55 * s), 20, 160, fill=INK, width=2)
    draw.polygon([(x - 38 * s, y - 54 * s), (x + 38 * s, y - 54 * s), (x + 48 * s, y + 86 * s), (x - 48 * s, y + 86 * s)], fill=(70, 110, 160, 255))
    draw.rectangle((x - 34 * s, y + 86 * s, x - 8 * s, y + 96 * s), fill=(70, 55, 45, 255))
    draw.rectangle((x + 8 * s, y + 86 * s, x + 34 * s, y + 96 * s), fill=(70, 55, 45, 255))


def draw_kolobok(draw: ImageDraw.ImageDraw, x: float, y: float, radius: float = 55) -> None:
    draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=(245, 190, 80, 255), outline=(170, 100, 30, 255), width=4)
    draw.ellipse((x - 24, y - 12, x - 8, y + 4), fill=INK)
    draw.ellipse((x + 8, y - 12, x + 24, y + 4), fill=INK)
    draw.arc((x - 22, y + 2, x + 22, y + 28), 20, 160, fill=INK, width=4)
    draw.ellipse((x - 38, y + 10, x - 26, y + 20), fill=(230, 120, 90, 180))
    draw.ellipse((x + 26, y + 10, x + 38, y + 20), fill=(230, 120, 90, 180))
    for index in range(3):
        draw.line((x - radius - 20 - index * 14, y - 30 + index * 26, x - radius - 6, y - 30 + index * 26), fill=(160, 130, 90, 200), width=3)


def draw_hare(draw: ImageDraw.ImageDraw, x: float, y: float, scale: float = 1.0) -> None:
    s = scale
    draw.ellipse((x - 42 * s, y - 30 * s, x + 35 * s, y + 50 * s), fill=(220, 220, 218, 255), outline=INK, width=2)
    draw.ellipse((x - 60 * s, y - 20 * s, x - 30 * s, y + 8 * s), fill=(240, 240, 238, 255), outline=INK, width=2)
    draw.ellipse((x + 2 * s, y - 90 * s, x + 34 * s, y - 18 * s), fill=(225, 225, 222, 255), outline=INK, width=2)
    draw.ellipse((x + 34 * s, y - 80 * s, x + 66 * s, y - 14 * s), fill=(225, 225, 222, 255), outline=INK, width=2)
    draw.ellipse((x + 8 * s, y - 66 * s, x + 22 * s, y - 52 * s), fill=INK)
    draw.ellipse((x + 30 * s, y - 66 * s, x + 44 * s, y - 52 * s), fill=INK)
    draw.ellipse((x + 24 * s, y - 48 * s, x + 38 * s, y - 36 * s), fill=(235, 150, 150, 255))


def draw_wolf(draw: ImageDraw.ImageDraw, x: float, y: float, scale: float = 1.0) -> None:
    s = scale
    draw.ellipse((x - 50 * s, y - 45 * s, x + 42 * s, y + 48 * s), fill=(120, 126, 130, 255), outline=INK, width=2)
    draw.ellipse((x + 14 * s, y - 80 * s, x + 62 * s, y - 30 * s), fill=(130, 136, 140, 255), outline=INK, width=2)
    draw.polygon([(x + 20 * s, y - 74 * s), (x + 18 * s, y - 112 * s), (x + 40 * s, y - 82 * s)], fill=(110, 116, 120, 255), outline=INK)
    draw.polygon([(x + 46 * s, y - 80 * s), (x + 62 * s, y - 112 * s), (x + 68 * s, y - 72 * s)], fill=(110, 116, 120, 255), outline=INK)
    draw.ellipse((x + 28 * s, y - 64 * s, x + 40 * s, y - 52 * s), fill=INK)
    draw.ellipse((x + 54 * s, y - 62 * s, x + 66 * s, y - 50 * s), fill=INK)
    draw.polygon([(x + 54 * s, y - 38 * s), (x + 78 * s, y - 30 * s), (x + 54 * s, y - 22 * s)], fill=INK)
    draw.arc((x - 52 * s, y - 8 * s, x - 16 * s, y + 30 * s), 100, 260, fill=(90, 96, 100, 255), width=4)


def draw_bear(draw: ImageDraw.ImageDraw, x: float, y: float, scale: float = 1.0) -> None:
    s = scale
    draw.ellipse((x - 60 * s, y - 50 * s, x + 55 * s, y + 55 * s), fill=(130, 82, 52, 255), outline=INK, width=2)
    draw.ellipse((x + 8 * s, y - 100 * s, x + 70 * s, y - 34 * s), fill=(140, 92, 58, 255), outline=INK, width=2)
    draw.ellipse((x + 12 * s, y - 112 * s, x + 34 * s, y - 88 * s), fill=(120, 74, 46, 255), outline=INK, width=2)
    draw.ellipse((x + 52 * s, y - 110 * s, x + 74 * s, y - 86 * s), fill=(120, 74, 46, 255), outline=INK, width=2)
    draw.ellipse((x + 24 * s, y - 82 * s, x + 36 * s, y - 70 * s), fill=INK)
    draw.ellipse((x + 52 * s, y - 82 * s, x + 64 * s, y - 70 * s), fill=INK)
    draw.ellipse((x + 36 * s, y - 64 * s, x + 52 * s, y - 50 * s), fill=(60, 40, 30, 255))


def draw_fox(draw: ImageDraw.ImageDraw, x: float, y: float, scale: float = 1.0) -> None:
    s = scale
    draw.ellipse((x - 45 * s, y - 35 * s, x + 40 * s, y + 45 * s), fill=(220, 110, 46, 255), outline=INK, width=2)
    draw.ellipse((x + 8 * s, y - 78 * s, x + 62 * s, y - 24 * s), fill=(230, 120, 50, 255), outline=INK, width=2)
    draw.polygon([(x + 16 * s, y - 70 * s), (x + 12 * s, y - 110 * s), (x + 36 * s, y - 78 * s)], fill=(210, 100, 40, 255), outline=INK)
    draw.polygon([(x + 44 * s, y - 76 * s), (x + 62 * s, y - 110 * s), (x + 68 * s, y - 68 * s)], fill=(210, 100, 40, 255), outline=INK)
    draw.ellipse((x + 26 * s, y - 60 * s, x + 36 * s, y - 50 * s), fill=INK)
    draw.ellipse((x + 54 * s, y - 58 * s, x + 64 * s, y - 48 * s), fill=INK)
    draw.polygon([(x + 48 * s, y - 40 * s), (x + 66 * s, y - 32 * s), (x + 48 * s, y - 24 * s)], fill=(40, 30, 25, 255))
    draw.ellipse((x - 80 * s, y - 10 * s, x - 30 * s, y + 48 * s), fill=(230, 120, 50, 255), outline=INK, width=2)
    draw.ellipse((x - 86 * s, y - 4 * s, x - 60 * s, y + 22 * s), fill=(255, 248, 230, 255))


def draw_mouse(draw: ImageDraw.ImageDraw, x: float, y: float, scale: float = 0.8) -> None:
    s = scale
    draw.ellipse((x - 32 * s, y - 20 * s, x + 30 * s, y + 30 * s), fill=(160, 160, 165, 255), outline=INK, width=2)
    draw.ellipse((x + 18 * s, y - 36 * s, x + 52 * s, y - 4 * s), fill=(170, 170, 175, 255), outline=INK, width=2)
    draw.ellipse((x + 22 * s, y - 44 * s, x + 40 * s, y - 24 * s), fill=(210, 170, 170, 255), outline=INK, width=2)
    draw.ellipse((x + 40 * s, y - 34 * s, x + 48 * s, y - 26 * s), fill=INK)
    draw.arc((x - 46 * s, y - 10 * s, x - 10 * s, y + 20 * s), 90, 270, fill=(130, 130, 135, 255), width=3)


def draw_frog(draw: ImageDraw.ImageDraw, x: float, y: float, scale: float = 0.9) -> None:
    s = scale
    draw.ellipse((x - 42 * s, y - 24 * s, x + 42 * s, y + 34 * s), fill=(94, 170, 90, 255), outline=INK, width=2)
    draw.ellipse((x - 26 * s, y - 32 * s, x - 2 * s, y - 8 * s), fill=(120, 200, 110, 255), outline=INK, width=2)
    draw.ellipse((x + 8 * s, y - 32 * s, x + 32 * s, y - 8 * s), fill=(120, 200, 110, 255), outline=INK, width=2)
    draw.ellipse((x - 18 * s, y - 24 * s, x - 8 * s, y - 14 * s), fill=INK)
    draw.ellipse((x + 14 * s, y - 24 * s, x + 24 * s, y - 14 * s), fill=INK)
    draw.arc((x - 16 * s, y + 2 * s, x + 16 * s, y + 22 * s), 20, 160, fill=INK, width=3)


def draw_teremok(draw: ImageDraw.ImageDraw, x: float, y: float, scale: float = 1.0, broken: bool = False) -> None:
    s = scale
    if broken:
        draw.polygon([(x - 100 * s, y - 20 * s), (x - 40 * s, y - 100 * s), (x, y - 30 * s)], fill=(150, 90, 50, 255), outline=INK)
        draw.polygon([(x, y - 30 * s), (x + 80 * s, y - 110 * s), (x + 110 * s, y - 20 * s)], fill=(160, 100, 58, 255), outline=INK)
        draw.rectangle((x - 80 * s, y - 20 * s, x + 70 * s, y + 80 * s), fill=(170, 110, 64, 255), outline=INK, width=3)
        draw.line((x - 60 * s, y + 10 * s, x + 30 * s, y + 60 * s), fill=(90, 55, 30, 255), width=4)
    else:
        draw.rectangle((x - 90 * s, y - 20 * s, x + 90 * s, y + 90 * s), fill=(170, 110, 64, 255), outline=(90, 55, 30, 255), width=4)
        draw.polygon([(x - 110 * s, y - 20 * s), (x, y - 120 * s), (x + 110 * s, y - 20 * s)], fill=(150, 64, 48, 255), outline=(90, 35, 25, 255))
        draw.rectangle((x - 20 * s, y + 20 * s, x + 22 * s, y + 90 * s), fill=(104, 66, 40, 255))
        for dx in (-64, 34):
            draw.rectangle((x + dx * s, y, x + (dx + 34) * s, y + 38 * s), fill=(255, 226, 150, 255), outline=(90, 55, 30, 255), width=2)
    draw.rectangle((x - 4 * s, y - 140 * s, x + 4 * s, y - 110 * s), fill=(100, 60, 35, 255))


def draw_turnip(draw: ImageDraw.ImageDraw, x: float, y: float, scale: float = 1.0) -> None:
    s = scale
    draw.polygon([(x - 52 * s, y + 80 * s), (x + 52 * s, y + 80 * s), (x + 34 * s, y - 30 * s), (x - 34 * s, y - 30 * s)], fill=(240, 190, 80, 255), outline=(170, 110, 40, 255))
    for dx, angle in [(-35, -0.7), (0, 0), (35, 0.7)]:
        draw.ellipse((x + dx * s - 26 * s, y - 120 * s, x + dx * s + 26 * s, y - 30 * s), fill=(90, 160, 90, 255), outline=(50, 100, 50, 255))
    draw.arc((x - 24 * s, y + 12 * s, x + 24 * s, y + 58 * s), 20, 160, fill=(170, 110, 40, 255), width=3)


def draw_granddaughter(draw: ImageDraw.ImageDraw, x: float, y: float, scale: float = 1.0) -> None:
    s = scale
    draw.ellipse((x - 24 * s, y - 100 * s, x + 24 * s, y - 52 * s), fill=(245, 200, 165, 255), outline=INK, width=2)
    draw.ellipse((x - 32 * s, y - 106 * s, x + 32 * s, y - 86 * s), fill=(240, 190, 70, 255), outline=INK, width=2)
    draw.ellipse((x - 36 * s, y - 96 * s, x - 18 * s, y - 70 * s), fill=(245, 200, 165, 255), outline=INK, width=2)
    draw.ellipse((x + 18 * s, y - 96 * s, x + 36 * s, y - 70 * s), fill=(245, 200, 165, 255), outline=INK, width=2)
    draw.ellipse((x - 14 * s, y - 82 * s, x - 5 * s, y - 73 * s), fill=INK)
    draw.ellipse((x + 5 * s, y - 82 * s, x + 14 * s, y - 73 * s), fill=INK)
    draw.polygon([(x - 34 * s, y - 52 * s), (x + 34 * s, y - 52 * s), (x + 44 * s, y + 62 * s), (x - 44 * s, y + 62 * s)], fill=(240, 170, 70, 255), outline=INK, width=2)
    draw.rectangle((x - 30 * s, y + 62 * s, x - 8 * s, y + 78 * s), fill=(80, 60, 45, 255))
    draw.rectangle((x + 8 * s, y + 62 * s, x + 30 * s, y + 78 * s), fill=(80, 60, 45, 255))


def draw_dog(draw: ImageDraw.ImageDraw, x: float, y: float, scale: float = 0.8) -> None:
    s = scale
    draw.ellipse((x - 40 * s, y - 20 * s, x + 35 * s, y + 40 * s), fill=(160, 110, 70, 255), outline=INK, width=2)
    draw.ellipse((x + 5 * s, y - 62 * s, x + 55 * s, y - 12 * s), fill=(180, 130, 85, 255), outline=INK, width=2)
    draw.ellipse((x + 8 * s, y - 70 * s, x + 26 * s, y - 42 * s), fill=(120, 80, 50, 255), outline=INK, width=2)
    draw.ellipse((x + 38 * s, y - 70 * s, x + 56 * s, y - 42 * s), fill=(120, 80, 50, 255), outline=INK, width=2)
    draw.ellipse((x + 16 * s, y - 50 * s, x + 24 * s, y - 42 * s), fill=INK)
    draw.ellipse((x + 38 * s, y - 50 * s, x + 46 * s, y - 42 * s), fill=INK)
    draw.ellipse((x + 26 * s, y - 34 * s, x + 40 * s, y - 22 * s), fill=(60, 45, 35, 255))
    draw.arc((x - 48 * s, y - 30 * s, x - 20 * s, y + 10 * s), 90, 270, fill=(120, 80, 50, 255), width=3)


def draw_cat(draw: ImageDraw.ImageDraw, x: float, y: float, scale: float = 0.7) -> None:
    s = scale
    draw.ellipse((x - 38 * s, y - 16 * s, x + 32 * s, y + 36 * s), fill=(140, 140, 150, 255), outline=INK, width=2)
    draw.ellipse((x + 4 * s, y - 58 * s, x + 54 * s, y - 8 * s), fill=(155, 155, 165, 255), outline=INK, width=2)
    draw.polygon([(x + 10 * s, y - 50 * s), (x + 6 * s, y - 76 * s), (x + 28 * s, y - 56 * s)], fill=(155, 155, 165, 255), outline=INK, width=2)
    draw.polygon([(x + 40 * s, y - 54 * s), (x + 56 * s, y - 78 * s), (x + 58 * s, y - 48 * s)], fill=(155, 155, 165, 255), outline=INK, width=2)
    draw.ellipse((x + 18 * s, y - 42 * s, x + 26 * s, y - 34 * s), fill=INK)
    draw.ellipse((x + 40 * s, y - 42 * s, x + 48 * s, y - 34 * s), fill=INK)
    draw.arc((x - 46 * s, y - 20 * s, x - 18 * s, y + 14 * s), 90, 270, fill=(120, 120, 130, 255), width=3)


def landscape_with(*characters: tuple) -> Image.Image:
    image = base_landscape()
    draw = ImageDraw.Draw(image)
    for item in characters:
        name = item[0]
        args = item[1:]
        {"grandpa": draw_grandpa, "grandma": draw_grandma, "granddaughter": draw_granddaughter,
         "kolobok": draw_kolobok, "hare": draw_hare, "wolf": draw_wolf, "bear": draw_bear,
         "fox": draw_fox, "mouse": draw_mouse, "frog": draw_frog, "dog": draw_dog, "cat": draw_cat,
         "teremok": draw_teremok, "turnip": draw_turnip}[name](draw, *args)
    return image


def title_scene(title: str, subtitle: str, label: str) -> Image.Image:
    image = gradient((240, 226, 196), (214, 196, 164), (W, ART_H)).convert("RGBA")
    draw = ImageDraw.Draw(image)
    draw.polygon([(0, 0), (430, 0), (250, ART_H), (0, ART_H)], fill=RED + (235,))
    draw.polygon([(W, 0), (W - 260, 0), (W - 120, ART_H), (W, ART_H)], fill=TEAL + (230,))
    draw_owl(draw, 240, 270, 1.25)
    draw.text((500, 90), label, font=font(20, True), fill=TEAL + (240,))
    draw.text((500, 130), title, font=font(84, True), fill=RED + (255,))
    draw.text((504, 240), subtitle, font=font(30, False), fill=INK + (225,))
    draw.line((504, 300, 1050, 300), fill=GOLD + (230,), width=4)
    draw.text((504, 326), "СОВЁНОК  •  КАНАЛ 15", font=font(22, True), fill=TEAL + (230,))
    draw.polygon(star_points(1100, 110, 46), fill=GOLD + (255,), outline=(150, 100, 30, 255))
    return image


def lesson_scene(word: str, translation: str, icon: str, accent: tuple[int, int, int]) -> Image.Image:
    image = gradient((248, 238, 214), (235, 216, 180), (W, ART_H)).convert("RGBA")
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 22, ART_H), fill=RED + (255,))
    draw_owl(draw, 160, 280, 0.9)
    draw.rounded_rectangle((330, 56, 1190, 450), radius=26, fill=(255, 252, 242, 255), outline=TEAL + (200,), width=4)
    draw.rounded_rectangle((360, 86, 1160, 150), radius=14, fill=accent + (235,))
    draw.text((390, 96), word, font=font(44, True), fill=WHITE + (255,))
    draw.text((364, 190), translation, font=font(44, True), fill=INK + (240,))
    draw.line((364, 260, 760, 260), fill=accent + (220,), width=3)
    draw_lesson_icon(draw, icon, 920, 320, accent)
    draw.text((364, 380), "Слушай и повторяй", font=font(24, False), fill=TEAL + (220,))
    return image


def draw_lesson_icon(draw: ImageDraw.ImageDraw, icon: str, x: int, y: int, accent: tuple[int, int, int]) -> None:
    if icon.startswith("num"):
        draw.ellipse((x - 70, y - 70, x + 70, y + 70), fill=accent + (235,), outline=INK, width=3)
        draw.text((x, y - 4), icon[3:], font=font(120, True), fill=WHITE + (255,), anchor="mm")
    elif icon == "water":
        draw.polygon([(x, y - 90), (x + 64, y + 30), (x, y + 82), (x - 64, y + 30)], fill=(80, 170, 230, 255), outline=INK)
        draw.ellipse((x - 18, y + 10, x - 4, y + 24), fill=(220, 245, 255, 220))
    elif icon == "cloud":
        for dx, dy, rx, ry in [(-48, 10, 52, 30), (0, -16, 64, 42), (52, 8, 48, 28)]:
            draw.ellipse((x + dx - rx, y + dy - ry, x + dx + rx, y + dy + ry), fill=(245, 248, 250, 255), outline=INK, width=2)
    elif icon == "rain":
        for dx, dy, rx, ry in [(-48, 10, 50, 28), (0, -12, 62, 40), (52, 8, 46, 26)]:
            draw.ellipse((x + dx - rx, y + dy - ry, x + dx + rx, y + dy + ry), fill=(160, 180, 200, 255), outline=INK, width=2)
        for dx in (-40, 0, 40):
            draw.line((x + dx, y + 50, x + dx - 8, y + 90), fill=(80, 150, 220, 255), width=6)
    elif icon == "plant":
        draw.line((x, y + 80, x, y - 40), fill=(70, 130, 60, 255), width=10)
        for side in (-1, 1):
            left1 = min(x + side * 8, x + side * 70)
            right1 = max(x + side * 8, x + side * 70)
            draw.ellipse((left1, y - 50, right1, y + 10), fill=(90, 170, 80, 255), outline=INK, width=2)
            left2 = min(x + side * 8, x + side * 60)
            right2 = max(x + side * 8, x + side * 60)
            draw.ellipse((left2, y + 20, right2, y + 72), fill=(110, 190, 90, 255), outline=INK, width=2)
    elif icon == "internet":
        draw.ellipse((x - 75, y - 75, x + 75, y + 75), fill=(120, 200, 230, 255), outline=INK, width=3)
        draw.arc((x - 40, y - 75, x + 40, y + 75), 90, 270, fill=INK, width=2)
        draw.arc((x - 40, y - 75, x + 40, y + 75), 270, 450, fill=INK, width=2)
        draw.arc((x - 75, y - 30, x + 75, y + 30), 180, 360, fill=INK, width=2)
        draw.arc((x - 75, y - 30, x + 75, y + 30), 0, 180, fill=INK, width=2)
        for dx, dy in [(-90, -50), (95, -55), (-85, 60), (90, 55)]:
            draw.ellipse((x + dx - 8, y + dy - 8, x + dx + 8, y + dy + 8), fill=GOLD + (255,), outline=INK)
    elif icon == "server":
        draw.rounded_rectangle((x - 70, y - 80, x + 70, y + 80), radius=12, fill=(40, 55, 75, 255), outline=INK, width=3)
        for row in (-45, 0, 45):
            draw.rounded_rectangle((x - 54, y + row - 14, x + 54, y + row + 10), radius=5, fill=(70, 90, 115, 255))
            draw.ellipse((x + 38, y + row - 7, x + 48, y + row + 3), fill=(126, 226, 168, 255))
    elif icon == "phone":
        draw.rounded_rectangle((x - 48, y - 82, x + 48, y + 82), radius=18, fill=(45, 60, 85, 255), outline=INK, width=3)
        draw.rounded_rectangle((x - 36, y - 60, x + 36, y + 54), radius=8, fill=(139, 213, 255, 255))
        draw.ellipse((x - 8, y + 62, x + 8, y + 78), fill=(180, 190, 200, 255))
    elif icon == "red":
        draw.ellipse((x - 70, y - 70, x + 70, y + 70), fill=(210, 60, 50, 255), outline=INK, width=3)
    elif icon == "blue":
        draw.ellipse((x - 70, y - 70, x + 70, y + 70), fill=(60, 100, 190, 255), outline=INK, width=3)
    elif icon == "green":
        draw.ellipse((x - 70, y - 70, x + 70, y + 70), fill=(70, 160, 90, 255), outline=INK, width=3)
    elif icon == "yellow":
        draw.ellipse((x - 70, y - 70, x + 70, y + 70), fill=(245, 200, 60, 255), outline=INK, width=3)
    elif icon == "black":
        draw.ellipse((x - 70, y - 70, x + 70, y + 70), fill=(30, 30, 30, 255), outline=INK, width=3)
    elif icon == "white":
        draw.ellipse((x - 70, y - 70, x + 70, y + 70), fill=(250, 250, 245, 255), outline=INK, width=3)
    elif icon == "cat":
        draw.ellipse((x - 55, y - 20, x + 55, y + 70), fill=(140, 110, 80, 255), outline=INK, width=3)
        draw.ellipse((x - 50, y - 80, x + 50, y + 10), fill=(160, 130, 95, 255), outline=INK, width=3)
        draw.polygon([(x - 45, y - 70), (x - 60, y - 115), (x - 20, y - 85)], fill=(160, 130, 95, 255), outline=INK)
        draw.polygon([(x + 45, y - 70), (x + 60, y - 115), (x + 20, y - 85)], fill=(160, 130, 95, 255), outline=INK)
        draw.ellipse((x - 28, y - 52, x - 12, y - 36), fill=INK)
        draw.ellipse((x + 12, y - 52, x + 28, y - 36), fill=INK)
        draw.polygon([(x - 8, y - 34), (x + 8, y - 34), (x, y - 22)], fill=(230, 140, 140, 255))
    elif icon == "dog":
        draw.ellipse((x - 55, y - 12, x + 55, y + 70), fill=(160, 110, 70, 255), outline=INK, width=3)
        draw.ellipse((x - 45, y - 75, x + 45, y + 5), fill=(180, 130, 85, 255), outline=INK, width=3)
        draw.ellipse((x - 70, y - 55, x - 38, y + 5), fill=(120, 80, 50, 255), outline=INK, width=2)
        draw.ellipse((x + 38, y - 55, x + 70, y + 5), fill=(120, 80, 50, 255), outline=INK, width=2)
        draw.ellipse((x - 22, y - 46, x - 8, y - 32), fill=INK)
        draw.ellipse((x + 8, y - 46, x + 22, y - 32), fill=INK)
        draw.ellipse((x - 14, y - 22, x + 14, y - 2), fill=(60, 45, 35, 255))
    elif icon == "bird":
        draw.ellipse((x - 60, y - 20, x + 50, y + 50), fill=(80, 150, 190, 255), outline=INK, width=3)
        draw.ellipse((x + 10, y - 55, x + 70, y + 5), fill=(90, 165, 205, 255), outline=INK, width=3)
        draw.polygon([(x + 65, y - 30), (x + 105, y - 18), (x + 65, y - 8)], fill=GOLD + (255,), outline=INK)
        draw.ellipse((x + 45, y - 40, x + 57, y - 28), fill=INK)
        draw.polygon([(x - 50, y + 10), (x - 95, y - 20), (x - 55, y + 40)], fill=(70, 130, 170, 255), outline=INK)
    elif icon == "fish":
        draw.ellipse((x - 70, y - 35, x + 40, y + 35), fill=(240, 150, 60, 255), outline=INK, width=3)
        draw.polygon([(x - 65, y - 32), (x - 115, y - 60), (x - 115, y + 60), (x - 65, y + 32)], fill=(240, 150, 60, 255), outline=INK)
        draw.ellipse((x + 15, y - 12, x + 29, y + 2), fill=INK)
        draw.arc((x - 20, y - 10, x + 20, y + 20), 20, 160, fill=INK, width=2)
    elif icon == "bear":
        draw.ellipse((x - 60, y - 40, x + 60, y + 65), fill=(130, 82, 52, 255), outline=INK, width=3)
        draw.ellipse((x - 45, y - 90, x + 45, y + 0), fill=(140, 92, 58, 255), outline=INK, width=3)
        draw.ellipse((x - 52, y - 100, x - 26, y - 74), fill=(120, 74, 46, 255), outline=INK)
        draw.ellipse((x + 26, y - 100, x + 52, y - 74), fill=(120, 74, 46, 255), outline=INK)
        draw.ellipse((x - 22, y - 62, x - 8, y - 48), fill=INK)
        draw.ellipse((x + 8, y - 62, x + 22, y - 48), fill=INK)
        draw.ellipse((x - 16, y - 36, x + 16, y - 12), fill=(60, 40, 30, 255))
    elif icon == "fox":
        draw.ellipse((x - 55, y - 20, x + 55, y + 60), fill=(225, 120, 50, 255), outline=INK, width=3)
        draw.ellipse((x - 45, y - 80, x + 45, y + 0), fill=(235, 130, 55, 255), outline=INK, width=3)
        draw.polygon([(x - 40, y - 70), (x - 60, y - 115), (x - 15, y - 80)], fill=(220, 110, 45, 255), outline=INK)
        draw.polygon([(x + 40, y - 70), (x + 60, y - 115), (x + 15, y - 80)], fill=(220, 110, 45, 255), outline=INK)
        draw.ellipse((x - 22, y - 55, x - 8, y - 41), fill=INK)
        draw.ellipse((x + 8, y - 55, x + 22, y - 41), fill=INK)
        draw.polygon([(x - 8, y - 30), (x + 8, y - 30), (x, y - 16)], fill=(60, 40, 30, 255))
    elif icon == "hello":
        draw.rounded_rectangle((x - 100, y - 70, x + 100, y + 50), radius=24, fill=(139, 213, 255, 255), outline=INK, width=3)
        draw.polygon([(x - 60, y + 48), (x - 30, y + 100), (x - 10, y + 48)], fill=(139, 213, 255, 255), outline=INK)
        draw.text((x - 60, y - 28), "Hello!", font=font(30, True), fill=INK)
    elif icon == "morning":
        draw.ellipse((x - 60, y - 60, x + 60, y + 60), fill=(255, 209, 102, 255), outline=(230, 150, 40, 255), width=3)
        for index in range(10):
            angle = index * math.tau / 10
            draw.line((x + 70 * math.cos(angle), y + 70 * math.sin(angle), x + 92 * math.cos(angle), y + 92 * math.sin(angle)), fill=(230, 150, 40, 255), width=4)
    elif icon == "evening":
        draw.ellipse((x - 55, y - 55, x + 55, y + 55), fill=(120, 130, 190, 255), outline=INK, width=3)
        draw.polygon([(x - 30, y - 40), (x + 40, y - 20), (x + 30, y + 40), (x - 45, y + 20)], fill=(191, 210, 220, 255))


def quiz_scene(question: str, options: list[str], answer: str) -> Image.Image:
    image = gradient((248, 238, 214), (235, 216, 180), (W, ART_H)).convert("RGBA")
    draw = ImageDraw.Draw(image)
    draw.text((60, 50), "ПРОВЕРЬ СЕБЯ", font=font(22, True), fill=RED + (240,))
    draw.text((60, 92), question, font=font(40, True), fill=INK + (240,))
    for index, option in enumerate(options):
        left = 70 + index * 390
        correct = option == answer
        draw.rounded_rectangle(
            (left, 220, left + 340, 360),
            radius=20,
            fill=(226, 240, 220, 255) if correct else (255, 252, 242, 255),
            outline=(70, 150, 80, 255) if correct else (160, 140, 110, 255),
            width=5 if correct else 2,
        )
        draw.text(
            (left + 170, 270),
            option,
            font=font(30, True),
            fill=(40, 100, 50, 255) if correct else INK + (240,),
            anchor="mm",
        )
        if correct:
            draw.text((left + 170, 320), "✓", font=font(40, True), fill=(40, 130, 60, 255), anchor="mm")
    draw.text((60, 430), "Правильный ответ подсвечен", font=font(18), fill=TEAL + (220,))
    return image


# ---------------------------------------------------------------------------
# Episodes
# ---------------------------------------------------------------------------

def story_episode(
    id: str,
    title: str,
    subtitle: str,
    description: str,
    scenes: list[dict],
) -> dict:
    return {
        "id": id,
        "title": title,
        "originalTitle": "Русская народная сказка",
        "year": 0,
        "description": description,
        "license": "Оригинальная постановка Aurora TV",
        "file": f"{id}.mp4",
        "source": "",
        "previewAtSeconds": 12,
        "silent": False,
        "subtitle": "",
        "kind": "story",
        "label": "СКАЗКА",
        "scenes": scenes,
    }


def lesson_episode(
    id: str,
    title: str,
    description: str,
    scenes: list[dict],
) -> dict:
    return {
        "id": id,
        "title": title,
        "originalTitle": "English for kids",
        "year": 0,
        "description": description,
        "license": "Оригинальная постановка Aurora TV",
        "file": f"{id}.mp4",
        "source": "",
        "previewAtSeconds": 12,
        "silent": False,
        "subtitle": "",
        "kind": "lesson",
        "label": "УРОК АНГЛИЙСКОГО",
        "scenes": scenes,
    }


def s(caption: str, ru: str, image, min_dur: float = 6.0, en: str = "", voice: str = "ru") -> dict:
    return {
        "caption": caption,
        "ru": ru,
        "en": en,
        "voice": voice,
        "image": image,
        "min_dur": min_dur,
    }


def kolobok() -> dict:
    scenes = [
        s("Русская народная сказка", "", lambda: title_scene("КОЛОБОК", "Русская народная сказка", "СОВЕТСКИЙ ДИАФИЛЬМ"), 6.0, voice="none"),
        s("Жили-были дед и баба.", "Жили-были дед и баба. Вот и просит дед: испеки, баба, колобок.", lambda: landscape_with(("grandpa", 500, 470, 1.0), ("grandma", 700, 470, 1.0)), 8.0),
        s("Испекла баба колобок и положила на окошко.", "Замесила баба тесто, испекла колобок и положила на окошко остыть.", lambda: landscape_with(("grandma", 680, 470, 1.0), ("kolobok", 260, 360, 40)), 9.0),
        s("Колобок покатился по дорожке.", "Колобок полежал-полежал, да и покатился: с окна на лавку, с лавки на пол, по полу да к дверям.", lambda: landscape_with(("kolobok", 600, 420, 50)), 10.0),
        s("Навстречу ему заяц.", "Катится колобок, а навстречу ему заяц: «Колобок, колобок, я тебя съем!»", lambda: landscape_with(("kolobok", 500, 420, 52), ("hare", 760, 440, 1.0)), 10.0),
        s("«Не ешь меня, я тебе песенку спою!»", "А колобок отвечает: «Не ешь меня, я тебе песенку спою!» И покатился дальше.", lambda: landscape_with(("kolobok", 520, 420, 52), ("hare", 760, 440, 1.0)), 9.0),
        s("Навстречу ему волк.", "Катится колобок, а навстречу ему серый волк: «Колобок, колобок, я тебя съем!»", lambda: landscape_with(("kolobok", 480, 420, 52), ("wolf", 760, 440, 1.0)), 10.0),
        s("«Не ешь меня, я тебе песенку спою!»", "Колобок и волку спел свою песенку, и покатился дальше.", lambda: landscape_with(("kolobok", 520, 420, 52), ("wolf", 760, 440, 1.0)), 8.0),
        s("Навстречу ему медведь.", "Катится колобок, а навстречу ему медведь: «Колобок, колобок, я тебя съем!»", lambda: landscape_with(("kolobok", 480, 420, 52), ("bear", 780, 450, 1.0)), 10.0),
        s("«Не ешь меня, я тебе песенку спою!»", "Колобок и медведю спел песенку, и покатился дальше.", lambda: landscape_with(("kolobok", 520, 420, 52), ("bear", 780, 450, 1.0)), 8.0),
        s("Навстречу ему лиса.", "Катится колобок, а навстречу ему хитрая лиса: «Колобок, колобок, я тебя съем!»", lambda: landscape_with(("kolobok", 480, 420, 52), ("fox", 780, 440, 1.0)), 10.0),
        s("«Сядь мне на нос и спой ещё разок».", "Лиса говорит: «Сядь мне на нос, колобок, и спой ещё разок».", lambda: landscape_with(("kolobok", 600, 360, 42), ("fox", 760, 440, 1.0)), 9.0),
        s("Лиса — ам! — и съела колобка.", "Колобок сел лисе на нос, а лиса — ам! — и съела его.", lambda: landscape_with(("fox", 760, 440, 1.0)), 8.0),
        s("Вот и сказке конец.", "Вот и сказке конец, а кто слушал — молодец. Смотрите «Совёнка» каждый день.", lambda: title_scene("КОНЕЦ", "Спасибо за просмотр", "СОВЁНОК"), 7.0),
    ]
    return story_episode("kolobok", "Колобок", "Русская народная сказка", "Колобок ушёл от деда и бабы, но хитрую лису ему не перехитрить.", scenes)


def teremok() -> dict:
    scenes = [
        s("Русская народная сказка", "", lambda: title_scene("ТЕРЕМОК", "Русская народная сказка", "СОВЕТСКИЙ ДИАФИЛЬМ"), 6.0, voice="none"),
        s("Стоит в поле теремок.", "Стоит в поле теремок. Он не низок, не высок.", lambda: landscape_with(("teremok", 640, 430, 1.0)), 8.0),
        s("Бежит мимо мышка-норушка.", "Бежит мимо мышка-норушка: «Кто-кто в теремочке живёт?»", lambda: landscape_with(("teremok", 700, 430, 0.95), ("mouse", 430, 450, 0.9)), 9.0),
        s("Стала мышка жить в теремке.", "Никого нет. Стала мышка жить в теремке.", lambda: landscape_with(("teremok", 700, 430, 0.95), ("mouse", 600, 450, 0.8)), 8.0),
        s("Прискакала лягушка-квакушка.", "Прискакала лягушка-квакушка: «Пусти меня жить!» — «Иди», — говорит мышка.", lambda: landscape_with(("teremok", 700, 430, 0.95), ("mouse", 590, 450, 0.8), ("frog", 460, 460, 0.9)), 9.0),
        s("Прибежал зайчик-побегайчик.", "Прибежал зайчик-побегайчик. Стали жить втроём.", lambda: landscape_with(("teremok", 700, 430, 0.95), ("hare", 470, 450, 0.9)), 8.0),
        s("Пришла лисичка-сестричка.", "Пришла лисичка-сестричка. Стали жить вчетвером.", lambda: landscape_with(("teremok", 700, 430, 0.95), ("fox", 470, 450, 0.9)), 8.0),
        s("Прибежал волчок-серый бочок.", "Прибежал волчок-серый бочок. Стали жить впятером.", lambda: landscape_with(("teremok", 700, 430, 0.95), ("wolf", 470, 450, 0.9)), 8.0),
        s("Пришёл медведь косолапый.", "Пришёл медведь косолапый и полез на теремок.", lambda: landscape_with(("teremok", 700, 430, 0.95), ("bear", 470, 450, 0.9)), 9.0),
        s("Теремок не выдержал — трах! — и развалился.", "Теремок не выдержал — трах! — и развалился.", lambda: landscape_with(("teremok", 640, 430, 1.0, True), ("bear", 470, 450, 0.9)), 9.0),
        s("Звери построили новый теремок.", "Звери не расстроились: построили новый теремок, ещё лучше.", lambda: landscape_with(("teremok", 640, 430, 1.1)), 9.0),
        s("Вот и сказке конец.", "Вот и сказке конец, а кто слушал — молодец.", lambda: title_scene("КОНЕЦ", "Спасибо за просмотр", "СОВЁНОК"), 6.0),
    ]
    return story_episode("teremok", "Теремок", "Русская народная сказка", "Мышка, лягушка, зайчик, лиса, волк и медведь нашли дом — и построили новый.", scenes)


def repka() -> dict:
    scenes = [
        s("Русская народная сказка", "", lambda: title_scene("РЕПКА", "Русская народная сказка", "СОВЕТСКИЙ ДИАФИЛЬМ"), 6.0, voice="none"),
        s("Посадил дед репку.", "Посадил дед репку. Выросла репка большая-пребольшая.", lambda: landscape_with(("grandpa", 400, 460, 0.9), ("turnip", 760, 430, 1.0)), 9.0),
        s("Тянет-потянет — вытянуть не может.", "Стал дед репку тянуть: тянет-потянет — вытянуть не может.", lambda: landscape_with(("grandpa", 560, 460, 0.9), ("turnip", 760, 430, 1.0)), 9.0),
        s("Позвал дед бабку.", "Позвал дед бабку. Тянут-потянет — вытянуть не могут.", lambda: landscape_with(("grandpa", 520, 460, 0.9), ("grandma", 650, 460, 0.9), ("turnip", 820, 430, 1.0)), 9.0),
        s("Позвала бабка внучку.", "Позвала бабка внучку. Тянут-потянет — вытянуть не могут.", lambda: landscape_with(("grandpa", 500, 460, 0.85), ("grandma", 610, 460, 0.85), ("granddaughter", 720, 470, 0.8), ("turnip", 860, 430, 1.0)), 9.0),
        s("Позвала внучка Жучку.", "Позвала внучка собаку Жучку. Тянут-потянет — вытянуть не могут.", lambda: landscape_with(("grandpa", 480, 460, 0.8), ("grandma", 580, 460, 0.8), ("granddaughter", 680, 470, 0.8), ("dog", 790, 480, 0.8), ("turnip", 900, 430, 1.0)), 9.0),
        s("Позвала Жучка кошку.", "Позвала Жучка кошку. Тянут-потянет — вытянуть не могут.", lambda: landscape_with(("grandpa", 460, 460, 0.75), ("grandma", 550, 460, 0.75), ("granddaughter", 640, 470, 0.75), ("dog", 740, 480, 0.75), ("cat", 830, 480, 0.75), ("turnip", 940, 430, 1.0)), 9.0),
        s("Позвала кошка мышку.", "Позвала кошка мышку. Тянут-потянет — вытянули репку!", lambda: landscape_with(("grandpa", 440, 460, 0.7), ("grandma", 520, 460, 0.7), ("granddaughter", 600, 470, 0.7), ("dog", 690, 480, 0.7), ("cat", 770, 480, 0.7), ("mouse", 850, 490, 0.6), ("turnip", 970, 430, 1.0)), 10.0),
        s("Вот и сказке конец.", "Вот и сказке конец. Помогайте друг другу, и любое дело будет по плечу.", lambda: title_scene("КОНЕЦ", "Спасибо за просмотр", "СОВЁНОК"), 7.0),
    ]
    return story_episode("repka", "Репка", "Русская народная сказка", "Деду, бабке, внучке, Жучке, кошке и мышке — всем нашлось дело.", scenes)


def english_hello() -> dict:
    scenes = [
        s("Английский с Совёнком", "", lambda: title_scene("ПРИВЕТ!", "Урок 1 • Английский для детей", "ENGLISH WITH SOVYONOK"), 6.0, voice="none"),
        s("Hello! — Привет!", "Hello! — Привет!", lambda: lesson_scene("Hello!", "Привет!", "hello", (70, 140, 160)), 7.0, en="Hello!", voice="both"),
        s("Hi! — Привет!", "Hi! — Привет! Так говорят друзьям.", lambda: lesson_scene("Hi!", "Привет! (друзьям)", "hello", (70, 140, 160)), 7.0, en="Hi!", voice="both"),
        s("Good morning! — Доброе утро!", "Good morning! — Доброе утро!", lambda: lesson_scene("Good morning!", "Доброе утро!", "morning", (230, 150, 40)), 8.0, en="Good morning!", voice="both"),
        s("Good afternoon! — Добрый день!", "Good afternoon! — Добрый день!", lambda: lesson_scene("Good afternoon!", "Добрый день!", "morning", (230, 150, 40)), 8.0, en="Good afternoon!", voice="both"),
        s("Good evening! — Добрый вечер!", "Good evening! — Добрый вечер!", lambda: lesson_scene("Good evening!", "Добрый вечер!", "evening", (90, 100, 150)), 8.0, en="Good evening!", voice="both"),
        s("Goodbye! — До свидания!", "Goodbye! — До свидания!", lambda: lesson_scene("Goodbye!", "До свидания!", "hello", (160, 90, 80)), 7.0, en="Goodbye!", voice="both"),
        s("How are you? — Как дела?", "How are you? — Как дела?", lambda: lesson_scene("How are you?", "Как дела?", "hello", (70, 140, 160)), 8.0, en="How are you?", voice="both"),
        s("I'm fine, thank you! — У меня всё хорошо, спасибо!", "I'm fine, thank you! — У меня всё хорошо, спасибо!", lambda: lesson_scene("I'm fine, thank you!", "У меня всё хорошо, спасибо!", "hello", (70, 150, 110)), 9.0, en="I'm fine, thank you!", voice="both"),
        s("Проверь себя", "Как сказать «Доброе утро»?", lambda: quiz_scene("Как сказать «Доброе утро»?", ["Good morning!", "Goodbye!", "Hello!"], "Good morning!"), 10.0),
        s("Молодцы! See you soon!", "Молодцы! See you soon! — До встречи!", lambda: title_scene("SEE YOU SOON!", "До встречи на следующем уроке", "СОВЁНОК"), 7.0, en="See you soon!", voice="both"),
    ]
    return lesson_episode("english_hello", "Английский с Совёнком: Приветствие", "Учимся здороваться и прощаться по-английски.", scenes)


def english_colors() -> dict:
    scenes = [
        s("Английский с Совёнком", "", lambda: title_scene("ЦВЕТА", "Урок 2 • Английский для детей", "ENGLISH WITH SOVYONOK"), 6.0, voice="none"),
        s("Red — красный.", "Red — красный.", lambda: lesson_scene("Red", "красный", "red", (192, 57, 43)), 7.0, en="Red", voice="both"),
        s("Blue — синий.", "Blue — синий.", lambda: lesson_scene("Blue", "синий", "blue", (60, 100, 190)), 7.0, en="Blue", voice="both"),
        s("Green — зелёный.", "Green — зелёный.", lambda: lesson_scene("Green", "зелёный", "green", (70, 150, 90)), 7.0, en="Green", voice="both"),
        s("Yellow — жёлтый.", "Yellow — жёлтый.", lambda: lesson_scene("Yellow", "жёлтый", "yellow", (230, 180, 40)), 7.0, en="Yellow", voice="both"),
        s("Black — чёрный.", "Black — чёрный.", lambda: lesson_scene("Black", "чёрный", "black", (40, 40, 40)), 7.0, en="Black", voice="both"),
        s("White — белый.", "White — белый.", lambda: lesson_scene("White", "белый", "white", (180, 180, 180)), 7.0, en="White", voice="both"),
        s("What color is it? — Какого это цвета?", "What color is it? — Какого это цвета?", lambda: lesson_scene("What color is it?", "Какого это цвета?", "red", (192, 57, 43)), 8.0, en="What color is it?", voice="both"),
        s("It's red! — Это красный!", "It's red! — Это красный!", lambda: lesson_scene("It's red!", "Это красный!", "red", (192, 57, 43)), 8.0, en="It's red!", voice="both"),
        s("Проверь себя", "Какого цвета солнце?", lambda: quiz_scene("Какого цвета солнце?", ["Yellow", "Blue", "Green"], "Yellow"), 10.0),
        s("Молодцы! See you soon!", "Молодцы! See you soon! — До встречи!", lambda: title_scene("SEE YOU SOON!", "До встречи на следующем уроке", "СОВЁНОК"), 7.0, en="See you soon!", voice="both"),
    ]
    return lesson_episode("english_colors", "Английский с Совёнком: Цвета", "Учим цвета по-английски: red, blue, green, yellow, black, white.", scenes)


def english_animals() -> dict:
    scenes = [
        s("Английский с Совёнком", "", lambda: title_scene("ЖИВОТНЫЕ", "Урок 3 • Английский для детей", "ENGLISH WITH SOVYONOK"), 6.0, voice="none"),
        s("Cat — кошка.", "Cat — кошка.", lambda: lesson_scene("Cat", "кошка", "cat", (170, 120, 80)), 7.0, en="Cat", voice="both"),
        s("Dog — собака.", "Dog — собака.", lambda: lesson_scene("Dog", "собака", "dog", (150, 100, 60)), 7.0, en="Dog", voice="both"),
        s("Bird — птица.", "Bird — птица.", lambda: lesson_scene("Bird", "птица", "bird", (80, 150, 190)), 7.0, en="Bird", voice="both"),
        s("Fish — рыба.", "Fish — рыба.", lambda: lesson_scene("Fish", "рыба", "fish", (230, 140, 60)), 7.0, en="Fish", voice="both"),
        s("Bear — медведь.", "Bear — медведь.", lambda: lesson_scene("Bear", "медведь", "bear", (130, 82, 52)), 7.0, en="Bear", voice="both"),
        s("Fox — лиса.", "Fox — лиса.", lambda: lesson_scene("Fox", "лиса", "fox", (220, 110, 50)), 7.0, en="Fox", voice="both"),
        s("What animal is it? — Что это за животное?", "What animal is it? — Что это за животное?", lambda: lesson_scene("What animal is it?", "Что это за животное?", "cat", (170, 120, 80)), 8.0, en="What animal is it?", voice="both"),
        s("It's a cat! — Это кошка!", "It's a cat! — Это кошка!", lambda: lesson_scene("It's a cat!", "Это кошка!", "cat", (170, 120, 80)), 8.0, en="It's a cat!", voice="both"),
        s("Проверь себя", "Кто говорит «мяу»?", lambda: quiz_scene("Кто говорит «мяу»?", ["Cat", "Dog", "Fish"], "Cat"), 10.0),
        s("Молодцы! See you soon!", "Молодцы! See you soon! — До встречи!", lambda: title_scene("SEE YOU SOON!", "До встречи на следующем уроке", "СОВЁНОК"), 7.0, en="See you soon!", voice="both"),
    ]
    return lesson_episode("english_animals", "Английский с Совёнком: Животные", "Учим животных по-английски: cat, dog, bird, fish, bear, fox.", scenes)


def show_episode(id: str, title: str, description: str, scenes: list[dict], label: str) -> dict:
    episode = lesson_episode(id, title, description, scenes)
    episode["label"] = label
    return episode


def junior_count() -> dict:
    scenes = [
        s("Английский с Совёнком", "", lambda: title_scene("СЧЁТ ДО 5", "Урок 4 • Английский для детей", "ENGLISH WITH SOVYONOK"), 6.0, voice="none"),
        s("One — один.", "One — один.", lambda: lesson_scene("One", "один", "num1", (70, 140, 160)), 7.0, en="One", voice="both"),
        s("Two — два.", "Two — два.", lambda: lesson_scene("Two", "два", "num2", (70, 140, 160)), 7.0, en="Two", voice="both"),
        s("Three — три.", "Three — три.", lambda: lesson_scene("Three", "три", "num3", (70, 140, 160)), 7.0, en="Three", voice="both"),
        s("Four — четыре.", "Four — четыре.", lambda: lesson_scene("Four", "четыре", "num4", (70, 140, 160)), 7.0, en="Four", voice="both"),
        s("Five — пять.", "Five — пять.", lambda: lesson_scene("Five", "пять", "num5", (70, 140, 160)), 7.0, en="Five", voice="both"),
        s("Проверь себя", "Сколько звёзд? Правильный ответ — Five.", lambda: quiz_scene("Сколько звёзд?", ["One", "Three", "Five"], "Five"), 10.0),
        s("Молодцы! See you soon!", "Молодцы! See you soon! — До встречи!", lambda: title_scene("SEE YOU SOON!", "До встречи на следующем уроке", "СОВЁНОК"), 7.0, en="See you soon!", voice="both"),
    ]
    return lesson_episode("junior_count", "Совёнок Юниор: Счёт до 5", "Учимся считать по-английски: one, two, three, four, five.", scenes)


def class_rain() -> dict:
    scenes = [
        s("Наука для детей", "", lambda: title_scene("ПОЧЕМУ ИДЁТ ДОЖДЬ?", "Мульт-Класс • Наука для детей", "МУЛЬТ-КЛАСС"), 6.0, voice="none"),
        s("Вода нагревается солнцем.", "Вода в реках, морях и лужах нагревается солнцем.", lambda: lesson_scene("ВОДА", "нагревается солнцем", "water", (70, 130, 190)), 8.0),
        s("Вода превращается в пар.", "Часть воды превращается в невидимый пар.", lambda: lesson_scene("ПАР", "поднимается вверх", "water", (90, 150, 200)), 8.0),
        s("Пар поднимается к небу.", "Тёплый пар поднимается вверх и собирается в облака.", lambda: lesson_scene("ОБЛАКА", "собирают пар", "cloud", (110, 140, 170)), 8.0),
        s("В облаках пар остывает.", "В облаках пар остывает и снова становится капельками.", lambda: lesson_scene("КАПЛИ", "появляются в облаках", "cloud", (100, 130, 160)), 8.0),
        s("Капли становятся тяжёлыми.", "Капель становится так много, что облако не выдерживает.", lambda: lesson_scene("ДОЖДЬ", "капли падают вниз", "rain", (70, 120, 180)), 8.0),
        s("Дождь возвращает воду на землю.", "Дождь поливает растения и наполняет реки. Круговорот воды продолжается.", lambda: lesson_scene("КРУГОВОРОТ", "воды в природе", "plant", (70, 150, 90)), 9.0),
        s("Проверь себя", "Откуда берётся дождь? Правильный ответ — из облаков.", lambda: quiz_scene("Откуда берётся дождь?", ["Из облаков", "Из камней", "Из песка"], "Из облаков"), 10.0),
        s("Молодцы!", "Молодцы! Теперь ты знаешь, как работает круговорот воды.", lambda: title_scene("МОЛОДЦЫ!", "Так работает круговорот воды", "МУЛЬТ-КЛАСС"), 7.0),
    ]
    return show_episode("class_rain", "Мульт-Класс: Почему идёт дождь?", "Круговорот воды в природе — просто и наглядно.", scenes, "НАУКА ДЛЯ ДЕТЕЙ")


def teen_internet() -> dict:
    scenes = [
        s("Технологии", "", lambda: title_scene("КАК РАБОТАЕТ ИНТЕРНЕТ?", "Тин-Класс • Технологии", "ТИН-КЛАСС"), 6.0, voice="none"),
        s("Компьютеры и телефоны соединяются в сеть.", "Компьютеры, телефоны и серверы соединяются в сеть.", lambda: lesson_scene("СЕТЬ", "устройства вместе", "internet", (70, 140, 160)), 8.0),
        s("Сервер хранит сайты.", "Сервер — это компьютер, который хранит сайты и отвечает на запросы.", lambda: lesson_scene("СЕРВЕР", "хранит сайты", "server", (90, 110, 160)), 8.0),
        s("Клиент отправляет запрос.", "Телефон или компьютер — это клиент. Он отправляет запрос.", lambda: lesson_scene("КЛИЕНТ", "телефон и компьютер", "phone", (70, 150, 110)), 8.0),
        s("У устройства есть IP-адрес.", "У каждого устройства в сети есть свой IP-адрес.", lambda: lesson_scene("IP-АДРЕС", "адрес устройства", "internet", (70, 120, 190)), 8.0),
        s("Сервер присылает ответ.", "Сервер находит данные и отправляет ответ обратно.", lambda: lesson_scene("ОТВЕТ", "сайт открывается", "server", (80, 120, 170)), 8.0),
        s("Проверь себя", "Что хранит сайты? Правильный ответ — сервер.", lambda: quiz_scene("Что хранит сайты?", ["Сервер", "Наушники", "Клавиатура"], "Сервер"), 10.0),
        s("Молодцы!", "Молодцы! Теперь ты знаешь, как работает интернет.", lambda: title_scene("МОЛОДЦЫ!", "Теперь ты знаешь, как работает интернет", "ТИН-КЛАСС"), 7.0),
    ]
    return show_episode("teen_internet", "Тин-Класс: Как работает интернет?", "Сеть, сервер, клиент и IP-адрес — просто о сложном.", scenes, "ТЕХНОЛОГИИ")


EPISODES = [
    kolobok, teremok, repka,
    english_hello, english_colors, english_animals,
    junior_count, class_rain, teen_internet,
]


def wrap(draw: ImageDraw.ImageDraw, text: str, f: ImageFont.FreeTypeFont, width: int) -> list[str]:
    lines: list[str] = []
    for paragraph in text.split("\n"):
        if not paragraph:
            lines.append("")
            continue
        current = ""
        for word in paragraph.split():
            candidate = f"{current} {word}".strip()
            if draw.textlength(candidate, font=f) <= width:
                current = candidate
            else:
                if current:
                    lines.append(current)
                current = word
        if current:
            lines.append(current)
    return lines


def render_frame(scene: dict, base_image: Image.Image, t: float, duration: float) -> Image.Image:
    image = base_image
    progress = max(0.0, min(1.0, t / max(0.1, duration)))
    zoom = 1.02 + 0.035 * progress
    width = int(W * zoom)
    height = int(ART_H * zoom)
    resized = image.resize((width, height), Image.LANCZOS)
    left = max(0, (width - W) // 2)
    top = max(0, (height - ART_H) // 2)
    view = resized.crop((left, top, left + W, top + ART_H))

    frame = Image.new("RGBA", (W, H), CREAM + (255,))
    frame.paste(view, (0, 0))
    draw = ImageDraw.Draw(frame)
    # Paper frame.
    draw.rectangle((0, 0, W - 1, ART_H - 1), outline=RED + (230,), width=6)
    draw.rectangle((6, 6, W - 7, ART_H - 7), outline=GOLD + (140,), width=2)
    # Caption panel.
    draw.rectangle((0, ART_H, W, H), fill=DARK + (255,))
    draw.line((0, ART_H, W, ART_H), fill=RED + (255,), width=5)
    label = scene.get("label", "СОВЕТСКИЙ ДИАФИЛЬМ")
    draw.text((44, ART_H + 20), label, font=font(15, True), fill=GOLD + (235,))
    caption = scene["caption"]
    caption_font = font(28, False)
    lines = wrap(draw, caption, caption_font, W - 120)[:3]
    total_height = len(lines) * 34
    y = ART_H + 58 + max(0, (120 - total_height) // 2)
    for line in lines:
        draw.text((W // 2, y), line, font=caption_font, fill=CREAM + (245,), anchor="ma")
        y += 34
    draw.text((W - 44, H - 28), "СОВЁНОК  •  КАНАЛ 15", font=font(14, True), fill=(160, 180, 190, 220), anchor="ra")
    # Gentle projector fade.
    edge = min(1.0, t / 0.35, (duration - t) / 0.35)
    if edge < 1.0:
        black = Image.new("RGBA", (W, H), (0, 0, 0, 255))
        frame = Image.blend(black, frame, max(0.0, edge))
    return frame.convert("RGB")


def wav_seconds(path: Path) -> float:
    with wave.open(str(path), "rb") as handle:
        return handle.getnframes() / float(handle.getframerate())


def build_tts(episode: dict, work: Path) -> list[Path]:
    jobs = []
    outputs: list[Path] = []
    for index, scene in enumerate(episode["scenes"]):
        ru = scene.get("ru", "")
        en = scene.get("en", "")
        voice = scene.get("voice", "ru")
        ru_path = work / f"scene_{index:02d}_ru.wav"
        en_path = work / f"scene_{index:02d}_en.wav"
        final = work / f"scene_{index:02d}.wav"
        outputs.append(final)
        if voice in ("ru", "both") and ru:
            jobs.append({"output": str(ru_path), "text": ru, "voice": RU_VOICE, "rate": -1})
        if voice in ("en", "both") and en:
            jobs.append({"output": str(en_path), "text": en, "voice": EN_VOICE, "rate": -1})
    if jobs:
        jobs_file = work / "tts_jobs.json"
        jobs_file.write_text(json.dumps(jobs, ensure_ascii=False, indent=2), encoding="utf-8")
        subprocess.run(
            ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(SCRIPTS / "tts_voices.ps1"), str(jobs_file)],
            check=True,
        )
    for index, scene in enumerate(episode["scenes"]):
        ru_path = work / f"scene_{index:02d}_ru.wav"
        en_path = work / f"scene_{index:02d}_en.wav"
        final = work / f"scene_{index:02d}.wav"
        existing = [path for path in (ru_path, en_path) if path.exists()]
        if not existing:
            continue
        if len(existing) == 1:
            shutil.copyfile(existing[0], final)
        else:
            subprocess.run(
                [
                    FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
                    "-i", str(ru_path), "-i", str(en_path),
                    "-filter_complex", "[0:a]apad=pad_dur=0.35[a0];[a0][1:a]concat=n=2:v=0:a=1[a]",
                    "-map", "[a]", "-ar", "44100", "-ac", "2", str(final),
                ],
                check=True,
            )
    return outputs


def encode_scene(scene: dict, audio: Path | None, duration: float, out: Path) -> None:
    silent = out.with_suffix(".silent.mp4")
    frames = int(duration * FPS)
    base_image = scene["image"]()
    proc = subprocess.Popen(
        [
            FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
            "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
            "-r", str(FPS), "-i", "pipe:0", "-an",
            "-c:v", "libx264", "-profile:v", "main", "-level", "4.0",
            "-preset", "medium", "-crf", "24", "-pix_fmt", "yuv420p",
            str(silent),
        ],
        stdin=subprocess.PIPE,
    )
    for index in range(frames):
        proc.stdin.write(render_frame(scene, base_image, index / FPS, duration).tobytes())
    proc.stdin.close()
    if proc.wait() != 0:
        raise RuntimeError(f"ffmpeg failed: {out.name}")
    if audio is not None and audio.exists():
        audio_filter = (
            f"[1:a]volume=1.35[a1];"
            f"[2:a]volume=0.09,atrim=0:{duration:.3f},asetpts=PTS-STARTPTS[a2];"
            f"[a1][a2]amix=inputs=2:duration=longest:dropout_transition=0:normalize=0[a]"
        )
        cmd = [
            FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(silent), "-i", str(audio), "-stream_loop", "-1", "-i", str(AMBIENT),
            "-filter_complex", audio_filter, "-map", "0:v", "-map", "[a]", "-t", f"{duration:.3f}",
            "-c:v", "copy", "-c:a", "aac", "-b:a", "128k", "-ar", "44100", "-ac", "2",
            "-movflags", "+faststart", str(out),
        ]
    else:
        cmd = [
            FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(silent), "-stream_loop", "-1", "-i", str(AMBIENT), "-t", f"{duration:.3f}",
            "-map", "0:v", "-map", "1:a", "-af", "volume=0.16",
            "-c:v", "copy", "-c:a", "aac", "-b:a", "128k", "-ar", "44100", "-ac", "2",
            "-movflags", "+faststart", str(out),
        ]
    subprocess.run(cmd, check=True)
    silent.unlink(missing_ok=True)


def render_episode(episode: dict, out_dir: Path) -> Path:
    work = TEMP / episode["id"]
    if work.exists():
        shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True, exist_ok=True)
    build_tts(episode, work)
    files = []
    for index, scene in enumerate(episode["scenes"]):
        scene["kind"] = episode["kind"]
        scene["label"] = episode["label"]
        audio = work / f"scene_{index:02d}.wav"
        audio_dur = wav_seconds(audio) if audio.exists() else 0.0
        duration = max(scene.get("min_dur", 6.0), audio_dur + 1.2)
        scene_out = work / f"scene_{index:02d}.mp4"
        print(f"  {episode['id']} scene {index + 1}/{len(episode['scenes'])} {duration:.1f}s", flush=True)
        encode_scene(scene, audio if audio.exists() else None, duration, scene_out)
        files.append(scene_out)
    list_file = work / "concat.txt"
    list_file.write_text("".join(f"file '{path.as_posix()}'\n" for path in files), encoding="utf-8")
    out_dir.mkdir(parents=True, exist_ok=True)
    output = out_dir / episode["file"]
    subprocess.run(
        [FFMPEG, "-y", "-hide_banner", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(list_file), "-c", "copy", "-movflags", "+faststart", str(output)],
        check=True,
    )
    print(f"wrote {output} ({output.stat().st_size // 1024} KB)", flush=True)
    return output


def catalog_entry(episode: dict, duration: float) -> dict:
    return {
        "id": episode["id"],
        "title": episode["title"],
        "originalTitle": episode["originalTitle"],
        "year": episode["year"],
        "description": episode["description"],
        "license": episode["license"],
        "file": episode["file"],
        "source": episode["source"],
        "durationSeconds": round(duration, 2),
        "previewAtSeconds": episode["previewAtSeconds"],
        "silent": episode["silent"],
        "subtitle": episode["subtitle"],
        "kind": episode["kind"],
        "label": episode["label"],
        "promo": f"promo_{episode['id']}.mp4",
    }


PROMO_TIMES = {
    "kolobok": ("14:00", "в два часа дня"),
    "teremok": ("14:30", "в два тридцать дня"),
    "repka": ("15:00", "в три часа дня"),
    "english_hello": ("15:30", "в три тридцать дня"),
    "english_colors": ("16:00", "в четыре часа дня"),
    "english_animals": ("16:30", "в четыре тридцать дня"),
    "junior_count": ("14:00", "в два часа дня"),
    "class_rain": ("15:30", "в три тридцать дня"),
    "teen_internet": ("15:30", "в три тридцать дня"),
}

PROMO_TITLES = {
    "kolobok": "Колобок",
    "teremok": "Теремок",
    "repka": "Репка",
    "english_hello": "Английский с Совёнком. Приветствие",
    "english_colors": "Английский с Совёнком. Цвета",
    "english_animals": "Английский с Совёнком. Животные",
    "junior_count": "Счёт до 5",
    "class_rain": "Почему идёт дождь?",
    "teen_internet": "Как работает интернет?",
}

PROMO_DIR = ASSETS / "promos"


def promo_frame(t: float, episode: dict) -> Image.Image:
    image = gradient((247, 201, 72), (239, 174, 50), (W, H)).convert("RGBA")
    draw = ImageDraw.Draw(image)
    # Constructivist corner shapes.
    draw.polygon([(0, 0), (300, 0), (160, H), (0, H)], fill=RED + (255,))
    draw.polygon([(W, 0), (W - 210, 0), (W - 70, H), (W, H)], fill=TEAL + (255,))
    draw.line((0, H - 48, W, H - 48), fill=(120, 70, 30, 120), width=2)
    draw.text((28, H - 38), "СОВЁНОК  •  AURORA TV", font=font(15, True), fill=DARK + (220,))

    title = PROMO_TITLES.get(episode["id"], episode["title"])
    time_text, voice_time = PROMO_TIMES.get(episode["id"], ("14:00", "в два часа дня"))

    if t < 2.2:
        draw_owl(draw, 220, 270, 1.0)
        draw.text((440, 230), "СОВЁНОК", font=font(66, True), fill=CREAM + (255,))
        draw.text((444, 316), "ДЕТСКИЙ КАНАЛ", font=font(24, True), fill=DARK + (220,))
    elif t < 4.5:
        draw_owl(draw, 210, 300, 0.82)
        draw.text((420, 230), "ПРЕДСТАВЛЯЕТ", font=font(62, True), fill=RED + (255,))
        draw.text((424, 320), "НОВУЮ ПРОГРАММУ", font=font(24, True), fill=DARK + (220,))
    elif t < 9.2:
        draw.rounded_rectangle((90, 140, 1190, 640), radius=26, fill=(10, 24, 40, 235), outline=RED + (255,), width=8)
        draw.text((110, 104), "ФРАГМЕНТ", font=font(20, True), fill=RED + (230,))
        draw.text((W // 2, 660), "СМОТРИТЕ СЕЙЧАС", font=font(22, True), fill=DARK + (220,), anchor="ma")
    elif t < 14.5:
        draw.rounded_rectangle((160, 130, 1120, 620), radius=30, fill=(255, 252, 242, 255), outline=RED + (255,), width=7)
        draw.text((W // 2, 166), "ПРЕМЬЕРА", font=font(20, True), fill=TEAL + (240,), anchor="ma")
        title_font = font(58 if len(title) <= 16 else 42, True)
        lines = wrap(draw, title, title_font, 860)[:2]
        y = 218
        for line in lines:
            draw.text((W // 2, y), line, font=title_font, fill=RED + (255,), anchor="ma")
            y += 58
        draw.text((W // 2, 390), time_text, font=font(72, True), fill=TEAL + (255,), anchor="ma")
        draw.text((W // 2, 502), "НА КАНАЛЕ СОВЁНОК", font=font(28, True), fill=DARK + (230,), anchor="ma")
    else:
        draw_owl(draw, 250, 280, 1.0)
        draw.text((470, 238), "СМОТРИТЕ", font=font(40, True), fill=RED + (255,))
        draw.text((470, 300), "СЕЙЧАС", font=font(64, True), fill=DARK + (230,))
        draw.text((474, 390), "СОВЁНОК  •  AURORA TV", font=font(24, True), fill=TEAL + (240,))
    return image.convert("RGB")


def make_promo(episode: dict, episode_path: Path) -> None:
    work = TEMP / f"promo_{episode['id']}"
    if work.exists():
        shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True, exist_ok=True)
    duration = 18.0
    silent = work / "bg.mp4"
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
    for index in range(int(duration * FPS)):
        proc.stdin.write(promo_frame(index / FPS, episode).tobytes())
    proc.stdin.close()
    if proc.wait() != 0:
        raise RuntimeError(f"promo background failed: {episode['id']}")

    episode_duration = ffprobe_duration(episode_path)
    preview = min(episode["previewAtSeconds"], max(2.0, episode_duration - 8.0))
    frag = work / "frag.mp4"
    subprocess.run(
        [
            FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
            "-ss", f"{preview:.2f}", "-i", str(episode_path), "-t", "5",
            "-vf", "scale=1080:480:force_original_aspect_ratio=decrease,pad=1080:480:(ow-iw)/2:(oh-ih)/2:color=0x0B1B2B",
            "-an", "-c:v", "libx264", "-profile:v", "main", "-level", "4.0",
            "-preset", "medium", "-crf", "24", "-pix_fmt", "yuv420p", str(frag),
        ],
        check=True,
    )

    title = PROMO_TITLES.get(episode["id"], episode["title"])
    _, voice_time = PROMO_TIMES.get(episode["id"], ("14:00", "в два часа дня"))
    intro_wav = work / "intro.wav"
    title_wav = work / "title.wav"
    jobs = [
        {"output": str(intro_wav), "text": "Совёнок представляет.", "voice": RU_VOICE, "rate": -1},
        {"output": str(title_wav), "text": f"«{title}» — {voice_time} на канале Совёнок.", "voice": RU_VOICE, "rate": -1},
    ]
    jobs_file = work / "tts_jobs.json"
    jobs_file.write_text(json.dumps(jobs, ensure_ascii=False, indent=2), encoding="utf-8")
    subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(SCRIPTS / "tts_voices.ps1"), str(jobs_file)],
        check=True,
    )

    PROMO_DIR.mkdir(parents=True, exist_ok=True)
    output = PROMO_DIR / f"promo_{episode['id']}.mp4"
    audio_filter = (
        "[2:a]adelay=2000|2000,volume=1.45[a1];"
        "[3:a]adelay=9200|9200,volume=1.45[a2];"
        "[4:a]volume=0.10,atrim=0:18,asetpts=PTS-STARTPTS[a3];"
        "[a1][a2][a3]amix=inputs=3:duration=longest:dropout_transition=0:normalize=0,alimiter=limit=0.95[a]"
    )
    subprocess.run(
        [
            FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(silent), "-i", str(frag), "-i", str(intro_wav), "-i", str(title_wav),
            "-stream_loop", "-1", "-i", str(AMBIENT),
            "-filter_complex",
            "[1:v]scale=1080:480:force_original_aspect_ratio=decrease,pad=1080:480:(ow-iw)/2:(oh-ih)/2:color=0x0B1B2B[frag];"
            "[0:v][frag]overlay=100:150:enable='between(t,4.5,9.2)'[v];" + audio_filter,
            "-map", "[v]", "-map", "[a]", "-t", f"{duration:.3f}",
            "-c:v", "libx264", "-profile:v", "main", "-level", "4.0",
            "-preset", "medium", "-crf", "24", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "128k", "-ar", "44100", "-ac", "2",
            "-movflags", "+faststart", str(output),
        ],
        check=True,
    )
    print(f"wrote {output} ({output.stat().st_size // 1024} KB)", flush=True)


def ffprobe_duration(path: Path) -> float:
    import imageio_ffmpeg as iff
    ffprobe = Path(iff.get_ffmpeg_exe()).with_name("ffprobe.exe")
    if ffprobe.exists():
        result = subprocess.run(
            [str(ffprobe), "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(path)],
            capture_output=True, text=True, encoding="utf-8", errors="replace", check=True,
        )
        return float(result.stdout.strip())
    # Fallback: use ffmpeg stderr.
    result = subprocess.run([FFMPEG, "-hide_banner", "-i", str(path)], capture_output=True, text=True, encoding="utf-8", errors="replace")
    for line in result.stderr.splitlines():
        if "Duration:" in line:
            value = line.split("Duration:")[1].split(",")[0].strip()
            hours, minutes, seconds = value.split(":")
            return int(hours) * 3600 + int(minutes) * 60 + float(seconds)
    return 0.0


CHANNEL_INFO = [
    ("sovyonok", "Совёнок", [
        "kolobok", "teremok", "repka",
        "english_hello", "english_colors", "english_animals",
    ]),
    ("junior", "Совёнок Юниор", [
        "junior_count", "english_hello", "english_colors", "english_animals",
    ]),
    ("class", "Мульт-Класс", [
        "kolobok", "teremok", "repka", "class_rain",
    ]),
    ("teen", "Тин-Класс", [
        "english_hello", "english_colors", "english_animals", "teen_internet",
    ]),
]


def write_catalog(entries: list[dict]) -> None:
    by_id = {entry["id"]: entry for entry in entries}
    root_data = None
    for slug, name, ids in CHANNEL_INFO:
        cartoons = [by_id[item_id] for item_id in ids if item_id in by_id]
        data = {
            "version": 2,
            "channel": 15,
            "name": name,
            "openHour": 5,
            "closeHour": 22,
            "note": "Оригинальные советские диафильмы, наука и английский для детей. Классические советские мультфильмы не используются из-за копирайта.",
            "cartoons": cartoons,
        }
        target = ASSETS / slug / "catalog.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("wrote", target)
        if slug == "sovyonok":
            root_data = data
            local = ROOT / "kids" / "sovyonok" / "catalog.json"
            local.parent.mkdir(parents=True, exist_ok=True)
            local.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if root_data is not None:
        CATALOG_ASSET.parent.mkdir(parents=True, exist_ok=True)
        CATALOG_ASSET.write_text(json.dumps(root_data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        CATALOG_LOCAL.write_text(json.dumps(root_data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("wrote", CATALOG_ASSET)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", default="", help="comma-separated episode ids")
    parser.add_argument("--promos-only", action="store_true")
    parser.add_argument("--catalogs-only", action="store_true")
    parser.add_argument("--test", action="store_true")
    args = parser.parse_args()

    only = {item.strip() for item in args.only.split(",") if item.strip()}
    selected = [factory() for factory in EPISODES if not only or factory.__name__ in only or factory.__name__.replace("english_", "") in only]
    if args.catalogs_only:
        entries = []
        for factory in EPISODES:
            episode = factory()
            path = MEDIA / episode["file"]
            if path.exists():
                entries.append(catalog_entry(episode, ffprobe_duration(path)))
        if entries:
            write_catalog(entries)
        return
    if args.promos_only:
        entries = []
        for episode in selected:
            path = MEDIA / episode["file"]
            if not path.exists():
                continue
            make_promo(episode, path)
            entries.append(catalog_entry(episode, ffprobe_duration(path)))
        if entries:
            write_catalog(entries)
        return
    if args.test:
        for episode in selected:
            episode["scenes"][0]["image"]().save(ROOT / "test-artifacts" / f"sov_{episode['id']}_title.png")
            lesson_scene_episode = episode if episode["kind"] == "lesson" else None
            if lesson_scene_episode:
                lesson_scene_episode["scenes"][0]["image"]().save(ROOT / "test-artifacts" / f"sov_{episode['id']}_title.png")
            print("preview", episode["id"])
        return

    entries = []
    for episode in selected:
        print(f"=== {episode['id']} ===", flush=True)
        output = render_episode(episode, MEDIA)
        make_promo(episode, output)
        entries.append(catalog_entry(episode, ffprobe_duration(output)))

    if entries:
        write_catalog(entries)


if __name__ == "__main__":
    main()

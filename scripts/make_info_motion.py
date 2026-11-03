"""Render animated info-channel episodes (1920x1080) for Aurora TV."""

import math
import subprocess
import tempfile
from pathlib import Path

import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(r"C:\AuroraTV")
BG_PATH = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "cinema_bg.jpg"
AMBIENT = ROOT / "app" / "src" / "main" / "res" / "raw" / "cinema_ambient.mp3"
OUT = ROOT / "app" / "src" / "main" / "res" / "raw"
W, H, FPS = 1920, 1080, 30
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    path = Path(r"C:\Windows\Fonts\segoeuib.ttf" if bold else r"C:\Windows\Fonts\segoeuil.ttf")
    if not path.exists():
        path = Path(r"C:\Windows\Fonts\arial.ttf")
    return ImageFont.truetype(str(path), size)


def ease(value: float) -> float:
    value = max(0.0, min(1.0, value))
    return 1.0 - (1.0 - value) ** 3


def fade(t: float, start: float, end: float, edge: float = 0.35) -> float:
    if t < start or t > end:
        return 0.0
    return min(ease((t - start) / edge), ease((end - t) / edge), 1.0)


def rounded(draw, box, radius, fill, outline=None, width=2):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


base = Image.open(BG_PATH).convert("RGB").resize((W, H))
base = Image.alpha_composite(
    base.convert("RGBA"),
    Image.new("RGBA", (W, H), (3, 9, 24, 150)),
)

seed = 20261103


def rand() -> float:
    global seed
    seed = (1103515245 * seed + 12345) & 0x7FFFFFFF
    return seed / 0x7FFFFFFF


particles = [
    (rand() * W, rand() * H, 2 + rand() * 8, 6 + rand() * 22, rand() * 6.28)
    for _ in range(30)
]


def background(t: float) -> Image.Image:
    frame = base.copy()
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(glow)
    for x, y0, radius, speed, phase in particles:
        y = (y0 + speed * t) % (H + 40) - 20
        alpha = int(35 + 65 * (0.5 + 0.5 * math.sin(t * 0.7 + phase)))
        draw.ellipse(
            [x - radius, y - radius, x + radius, y + radius],
            fill=(139, 213, 255, alpha),
        )
    return Image.alpha_composite(frame, glow)


def encode(name: str, frames, duration: float) -> None:
    silent = Path(tempfile.gettempdir()) / f"{name}.silent.mp4"
    proc = subprocess.Popen(
        [
            FFMPEG,
            "-y",
            "-hide_banner",
            "-loglevel",
            "error",
            "-f",
            "rawvideo",
            "-pix_fmt",
            "rgb24",
            "-s",
            f"{W}x{H}",
            "-r",
            str(FPS),
            "-i",
            "pipe:0",
            "-an",
            "-c:v",
            "libx264",
            "-profile:v",
            "main",
            "-level",
            "4.0",
            "-preset",
            "medium",
            "-crf",
            "24",
            "-pix_fmt",
            "yuv420p",
            str(silent),
        ],
        stdin=subprocess.PIPE,
    )
    count = 0
    for frame in frames:
        proc.stdin.write(frame.tobytes())
        count += 1
    proc.stdin.close()
    if proc.wait() != 0:
        raise RuntimeError(f"ffmpeg failed: {name}")

    final = OUT / name
    subprocess.run(
        [
            FFMPEG,
            "-y",
            "-hide_banner",
            "-loglevel",
            "error",
            "-i",
            str(silent),
            "-i",
            str(AMBIENT),
            "-t",
            f"{duration:.3f}",
            "-map",
            "0:v",
            "-map",
            "1:a",
            "-c:v",
            "copy",
            "-c:a",
            "aac",
            "-b:a",
            "128k",
            "-af",
            "volume=0.30",
            "-movflags",
            "+faststart",
            str(final),
        ],
        check=True,
    )
    print(f"{name}: {count} frames, {final.stat().st_size // 1024} KB")


def server_video() -> None:
    duration = 24.0
    frames = int(duration * FPS)
    eyebrow = font(24, True)
    title = font(52, True)
    body = font(28)
    small = font(19)
    card_title = font(32, True)
    badge = font(18, True)
    url_font = font(24, True)

    def generate():
        for index in range(frames):
            t = index / FPS
            frame = background(t)
            draw = ImageDraw.Draw(frame)
            phase = int(t // 6)
            alpha = fade(t, phase * 6, (phase + 1) * 6, 0.45)

            draw.text((112, 120), "ИНФОКАНАЛ AURORA TV", font=eyebrow, fill=(139, 213, 255, 235))
            draw.text((112, 160), "СЕРВЕР УПРАВЛЕНИЯ", font=title, fill=(255, 255, 255, 255))
            rounded(draw, [112, 270, 700, 350], 18, (10, 26, 48, 235), (139, 213, 255, 150), 2)
            draw.text((140, 290), "http://IP_ТВ:8765", font=url_font, fill=(139, 213, 255, 255))
            lines = {
                0: "Открой панель в браузере телефона или ПК.",
                1: "Выбери канал: ТВ 1–14 или кино 20–32.",
                2: "Включи профилактику или обновление.",
                3: "Проверь статус и верни канал в эфир.",
            }
            draw.multiline_text(
                (112, 390),
                lines.get(phase, lines[3]),
                font=body,
                fill=(190, 205, 225, int(235 * max(0.45, alpha))),
                spacing=12,
            )
            # Bottom steps.
            steps = ["1  ПАНЕЛЬ", "2  КАНАЛ", "3  СТАТУС", "4  ЭФИР"]
            for i, label in enumerate(steps):
                color = (255, 209, 102, 230) if i == phase else (90, 110, 140, 140)
                rounded(draw, [112 + i * 150, 520, 250 + i * 150, 570], 12, (10, 24, 44, 200), color, 1)
                draw.text((128 + i * 150, 536), label, font=small, fill=color)

            # Browser mockup.
            bx0, by0, bx1, by1 = 790, 160, 1810, 840
            rounded(draw, [bx0, by0, bx1, by1], 24, (6, 16, 32, 240), (120, 160, 220, 100), 2)
            rounded(draw, [bx0, by0, bx1, by0 + 62], 24, (13, 32, 58, 255), None, 0)
            draw.rectangle([bx0, by0 + 32, bx1, by0 + 62], fill=(13, 32, 58, 255))
            for dot, color in enumerate([(255, 107, 107), (255, 209, 102), (126, 226, 168)]):
                draw.ellipse([bx0 + 24 + dot * 26, by0 + 24, bx0 + 40 + dot * 26, by0 + 40], fill=color + (220,))
            rounded(draw, [bx0 + 130, by0 + 16, bx1 - 30, by0 + 46], 10, (5, 13, 26, 255), None, 0)
            draw.text((bx0 + 150, by0 + 22), "192.168.0.3:8765", font=small, fill=(160, 190, 220, 220))

            cards = [
                (20, "Кинопремьера", "Потерянный уик-энд", "В ЭФИРЕ", (126, 226, 168)),
                (21, "Кинохит", "Носферату", "ПРОФИЛАКТИКА", (255, 107, 107)),
                (22, "Aurora Cinema", "Объезд", "ОБНОВЛЕНИЕ", (255, 209, 102)),
            ]
            for i, (number, name, film, state, color) in enumerate(cards):
                top = by0 + 92 + i * 186
                active = i == min(phase, 3)
                border = color if active else (80, 110, 150, 110)
                rounded(draw, [bx0 + 34, top, bx1 - 34, top + 158], 18, (10, 24, 44, 245), border, 3 if active else 1)
                rounded(draw, [bx0 + 56, top + 24, bx0 + 136, top + 104], 14, (20, 55, 100, 255), None, 0)
                draw.text((bx0 + 96, top + 52), str(number), font=font(36, True), fill=color + (255,), anchor="mm")
                draw.text((bx0 + 160, top + 30), name, font=card_title, fill=(255, 255, 255, 255))
                draw.text((bx0 + 160, top + 78), film, font=small, fill=(170, 195, 225, 230))
                rounded(draw, [bx1 - 310, top + 30, bx1 - 60, top + 72], 12, (16, 38, 66, 230), color + (230,), 1)
                draw.text((bx1 - 185, top + 51), state, font=badge, fill=color + (255,), anchor="mm")
                if active:
                    # Cursor / highlight.
                    cx = bx1 - 185 + 20 * math.sin(t * 2.2)
                    cy = top + 108 + 8 * math.cos(t * 2.0)
                    draw.ellipse([cx - 14, cy - 14, cx + 14, cy + 14], fill=(255, 255, 255, 230))
                    draw.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], fill=(10, 30, 55, 255))

            draw.text(
                (112, 930),
                "Настройки → Система → Сервер управления",
                font=small,
                fill=(150, 175, 205, 220),
            )
            draw.text((112, 970), "AURORA TV  •  КИНОЗАЛЫ 20–32", font=small, fill=(110, 140, 175, 210))
            yield frame.convert("RGB")

    encode("info_server.mp4", generate(), duration)


def whatsnew_video() -> None:
    scenes = [
        ("13", "КИНОЗАЛОВ", "Кинопремьера, Кинохит, Комедия,\nБоевик, Фантастика, Детектив и другие."),
        ("АФИША", "ВСЕХ КАНАЛОВ", "Фильмы 20–32 на ближайший сеанс.\nВыбранный канал подсвечен."),
        ("3 МИН", "РЕКЛАМА", "Туториал по пульту, трейлер каналов\nи заставка AURORA TV."),
        ("8765", "СЕРВЕР", "Профилактика и обновление каналов\nс телефона или ПК."),
        ("!", "ПРОФИЛАКТИКА", "Красная плашка, обратный отсчёт\nи автоматический возврат в эфир."),
        ("→", "БЕГУЩАЯ СТРОКА", "Новости, подсказки и анонсы\nвнизу инфоканала."),
    ]
    segment = 4.0
    duration = segment * len(scenes)
    frames = int(duration * FPS)
    big = font(150, True)
    label = font(54, True)
    body = font(30)

    def generate():
        for index in range(frames):
            t = index / FPS
            frame = background(t)
            draw = ImageDraw.Draw(frame)
            scene_index = min(len(scenes) - 1, int(t / segment))
            local = t - scene_index * segment
            alpha = fade(t, scene_index * segment, (scene_index + 1) * segment, 0.4)
            number, heading, text = scenes[scene_index]
            slide = int((1.0 - ease(local / 0.55)) * 40)

            draw.text((120, 150), "ЧТО НОВОГО", font=font(24, True), fill=(139, 213, 255, 235))
            draw.text((120, 190), "AURORA TV 1.6", font=font(42, True), fill=(255, 255, 255, 240))
            draw.text(
                (120, 300 - slide),
                number,
                font=big,
                fill=(255, 209, 102, int(255 * max(0.2, alpha))),
            )
            draw.text(
                (120, 490 - slide),
                heading,
                font=label,
                fill=(255, 255, 255, int(255 * max(0.2, alpha))),
            )
            draw.multiline_text(
                (122, 590),
                text,
                font=body,
                fill=(190, 205, 225, int(235 * max(0.25, alpha))),
                spacing=14,
            )
            # Right-hand animated card.
            cx, cy = 1450, 560
            pulse = 1.0 + 0.03 * math.sin(t * 2.0)
            radius = int(250 * pulse)
            draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=(12, 34, 66, 220), outline=(139, 213, 255, 160), width=3)
            draw.text((cx, cy - 20), number, font=font(110, True), fill=(139, 213, 255, 240), anchor="mm")
            draw.text((cx, cy + 90), "AURORA", font=font(28, True), fill=(190, 210, 235, 230), anchor="mm")
            for dot in range(len(scenes)):
                color = (255, 209, 102, 230) if dot == scene_index else (80, 105, 140, 140)
                draw.ellipse([120 + dot * 38, 900, 146 + dot * 38, 926], fill=color)
            draw.text((120, 970), "AURORA TV  •  ИНФОКАНАЛ 0", font=font(18), fill=(130, 155, 190, 210))
            yield frame.convert("RGB")

    encode("info_whatsnew16.mp4", generate(), duration)


server_video()
whatsnew_video()

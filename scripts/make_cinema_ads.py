"""Render three short animated MP4 ads for the Aurora TV cinema break."""

import json
import math
import subprocess
from pathlib import Path

import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFont

W, H, FPS = 1280, 720, 24
ROOT = Path(r"C:\AuroraTV")
OUT = ROOT / "app" / "src" / "main" / "res" / "raw"
BG_PATH = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "cinema_bg.jpg"

ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        Path(r"C:\Windows\Fonts\segoeuib.ttf" if bold else r"C:\Windows\Fonts\segoeuil.ttf"),
        Path(r"C:\Windows\Fonts\arialbd.ttf" if bold else r"C:\Windows\Fonts\arial.ttf"),
    ]
    for path in candidates:
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def ease_out(value: float) -> float:
    value = max(0.0, min(1.0, value))
    return 1.0 - (1.0 - value) ** 3


def fade_window(t: float, start: float, end: float, edge: float = 0.3) -> float:
    if t < start or t > end:
        return 0.0
    return min(
        ease_out((t - start) / edge),
        ease_out((end - t) / edge),
        1.0,
    )


base = Image.open(BG_PATH).convert("RGB").resize((W, H))
base = Image.alpha_composite(
    base.convert("RGBA"),
    Image.new("RGBA", (W, H), (3, 9, 24, 145)),
)

rng_state = 20261103


def next_rand() -> float:
    global rng_state
    rng_state = (1103515245 * rng_state + 12345) & 0x7FFFFFFF
    return rng_state / 0x7FFFFFFF


particles = []
for _ in range(34):
    particles.append(
        (
            next_rand() * W,
            next_rand() * H,
            2 + next_rand() * 8,
            6 + next_rand() * 26,
            0.3 + next_rand() * 0.9,
        )
    )


def make_frame(t: float) -> Image.Image:
    frame = base.copy()
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    for x, y0, radius, speed, phase in particles:
        y = (y0 + speed * t) % (H + 40) - 20
        alpha = int(40 + 70 * (0.5 + 0.5 * math.sin(t * 0.8 + phase)))
        glow_draw.ellipse(
            [x - radius, y - radius, x + radius, y + radius],
            fill=(139, 213, 255, alpha),
        )
    frame = Image.alpha_composite(frame, glow)
    return frame


def rounded(draw: ImageDraw.ImageDraw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def draw_remote(draw, x, y, w, h, highlight: str, pulse: float):
    """Draw a stylised TV remote with one highlighted control."""
    rounded(draw, [x, y, x + w, y + h], 36, (18, 24, 34, 245), (120, 150, 190, 120), 2)
    # Power button
    rounded(draw, [x + w / 2 - 22, y + 26, x + w / 2 + 22, y + 70], 18, (50, 60, 76, 255))
    draw.ellipse([x + w / 2 - 9, y + 38, x + w / 2 + 9, y + 56], outline=(255, 107, 107, 230), width=4)
    # D-pad
    cx, cy = x + w / 2, y + 170
    draw.ellipse([cx - 64, cy - 64, cx + 64, cy + 64], outline=(90, 110, 140, 180), width=3)
    draw.ellipse([cx - 34, cy - 34, cx + 34, cy + 34], fill=(28, 38, 54, 255), outline=(139, 213, 255, 180), width=2)
    draw.text((cx, cy), "OK", font=font(20, True), fill=(235, 245, 255, 255), anchor="mm")
    # Navigation arrows
    for dx, dy, char in [(0, -82, "▲"), (0, 82, "▼"), (-82, 0, "◀"), (82, 0, "▶")]:
        draw.text((cx + dx, cy + dy), char, font=font(18), fill=(160, 185, 215, 220), anchor="mm")
    # Back / home / menu row
    keys = [("BACK", x + 42), ("HOME", x + w / 2 - 32), ("MENU", x + w - 92)]
    for name, kx in keys:
        rounded(draw, [kx, y + 268, kx + 60, y + 308], 12, (40, 50, 66, 255))
        draw.text((kx + 30, y + 288), name, font=font(9, True), fill=(190, 210, 235, 255), anchor="mm")
    # Digits
    for index in range(10):
        row = index // 3
        column = index % 3
        dx = x + 34 + column * 64
        dy = y + 336 + row * 46
        rounded(draw, [dx, dy, dx + 52, dy + 36], 10, (34, 43, 58, 255))
        draw.text((dx + 26, dy + 18), str((index + 1) % 10), font=font(17, True), fill=(225, 235, 250, 255), anchor="mm")
    # Highlight
    boxes = {
        "ok": [cx - 44, cy - 44, cx + 44, cy + 44],
        "back": [x + 34, y + 260, x + 110, y + 316],
        "home": [x + w / 2 - 40, y + 260, x + w / 2 + 40, y + 316],
        "menu": [x + w - 100, y + 260, x + w - 26, y + 316],
        "digits": [x + 26, y + 328, x + w - 26, y + h - 24],
    }
    box = boxes.get(highlight)
    if box:
        alpha = int(60 + 100 * pulse)
        draw.rounded_rectangle(box, radius=18, outline=(255, 214, 102, alpha), width=4)
        draw.rounded_rectangle(
            [box[0] - 7, box[1] - 7, box[2] + 7, box[3] + 7],
            radius=24,
            outline=(255, 214, 102, max(20, alpha - 40)),
            width=2,
        )


def render_remote() -> None:
    tips = [
        ("OK — ВЫБРАТЬ", "Открывает канал, фильм или приложение.", "ok"),
        ("BACK — НАЗАД", "Возвращает на предыдущий экран.", "back"),
        ("0 — ИНФО-КАНАЛ", "Подсказки, музыка и видео о возможностях ТВ.", "digits"),
        ("1–14 — ТЕЛЕКАНАЛЫ", "Прямые эфиры и IPTV-каналы.", "digits"),
        ("20–32 — КИНО", "13 кинозалов: премьеры, комедии, детективы.", "digits"),
        ("HOME — ТВ / AURORA", "Переключает обычное ТВ и Aurora TV.", "home"),
    ]
    segment = 2.6
    duration = segment * len(tips)
    frames = int(duration * FPS)
    title_font = font(46, True)
    desc_font = font(23)
    eyebrow_font = font(15, True)

    def generate():
        for index in range(frames):
            t = index / FPS
            frame = make_frame(t)
            draw = ImageDraw.Draw(frame)
            tip_index = min(len(tips) - 1, int(t / segment))
            local = t - tip_index * segment
            alpha = fade_window(t, tip_index * segment, (tip_index + 1) * segment, 0.32)
            title, description, key = tips[tip_index]
            pulse = 0.5 + 0.5 * math.sin(t * 5.0)
            draw_remote(draw, 820, 86, 280, 548, key, pulse)
            draw.text((92, 152), "ПУЛЬТ AURORA TV", font=eyebrow_font, fill=(139, 213, 255, int(230 * alpha)))
            draw.text((92, 190), title, font=title_font, fill=(255, 255, 255, int(255 * alpha)))
            draw.multiline_text(
                (92, 262),
                description,
                font=desc_font,
                fill=(190, 205, 225, int(235 * alpha)),
                spacing=8,
            )
            # Progress dots.
            for dot in range(len(tips)):
                color = (255, 214, 102, 220) if dot == tip_index else (90, 110, 140, 130)
                draw.ellipse([92 + dot * 30, 610, 112 + dot * 30, 630], fill=color)
            draw.text((92, 660), "AURORA TV  •  КИНОЗАЛЫ 20–32", font=font(13), fill=(140, 165, 195, 210))
            yield frame.convert("RGB")

    encode("ad_remote.mp4", generate())


channel_data = json.loads((ROOT / "info" / "cinema_channels.json").read_text(encoding="utf-8"))["channels"]
film_data = {item["id"]: item for item in json.loads((ROOT / "info" / "cinema_films.json").read_text(encoding="utf-8"))}


def render_channels() -> None:
    segment = 1.2
    duration = segment * len(channel_data)
    frames = int(duration * FPS)
    number_font = font(150, True)
    name_font = font(44, True)
    film_font = font(26)

    def generate():
        for index in range(frames):
            t = index / FPS
            frame = make_frame(t)
            draw = ImageDraw.Draw(frame)
            channel_index = min(len(channel_data) - 1, int(t / segment))
            local = t - channel_index * segment
            fade = fade_window(t, channel_index * segment, (channel_index + 1) * segment, 0.25)
            channel = channel_data[channel_index]
            accent = channel.get("color", "#8BD5FF")
            accent_rgb = tuple(int(accent[i : i + 2], 16) for i in (1, 3, 5))
            # Soft colour wash.
            wash = Image.new("RGBA", (W, H), accent_rgb + (int(28 * fade),))
            frame = Image.alpha_composite(frame, wash)
            draw = ImageDraw.Draw(frame)
            draw.text((92, 150), "13 КИНОЗАЛОВ", font=font(15, True), fill=(180, 200, 230, 220))
            draw.text(
                (92, 178),
                str(channel["number"]),
                font=number_font,
                fill=(255, 255, 255, int(255 * fade)),
            )
            draw.text(
                (300, 238),
                channel["name"].upper(),
                font=name_font,
                fill=accent_rgb + (int(255 * fade),),
            )
            films = channel.get("films") or []
            film_title = ""
            if films:
                film = film_data.get(films[0], {})
                film_title = film.get("title", "")
            draw.rectangle(
                [96, 336, 96 + int(120 * fade), 340],
                fill=accent_rgb + (int(220 * fade),),
            )
            draw.text((96, 366), film_title, font=film_font, fill=(205, 220, 240, int(230 * fade)))
            draw.text((96, 620), "AURORA TV  •  КИНО КАЖДЫЙ ДЕНЬ", font=font(15), fill=(150, 175, 205, 200))
            yield frame.convert("RGB")

    encode("ad_channels.mp4", generate())


def render_ident() -> None:
    duration = 8.0
    frames = int(duration * FPS)
    logo_font = font(92, True)
    sub_font = font(22)

    def generate():
        for index in range(frames):
            t = index / FPS
            frame = make_frame(t)
            draw = ImageDraw.Draw(frame)
            appear = ease_out(t / 1.4)
            leave = ease_out((t - 6.6) / 1.2)
            alpha = max(0.0, appear * (1.0 - leave))
            scale = 0.82 + 0.18 * appear
            logo = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            logo_draw = ImageDraw.Draw(logo)
            logo_draw.text(
                (W / 2, H / 2 - 20),
                "AURORA TV",
                font=logo_font,
                fill=(255, 255, 255, int(255 * alpha)),
                anchor="mm",
            )
            if scale != 1.0:
                logo = logo.resize((int(W * scale), int(H * scale)), Image.LANCZOS)
                logo = logo.resize((W, H), Image.LANCZOS)
            frame = Image.alpha_composite(frame, logo)
            draw = ImageDraw.Draw(frame)
            line_width = int(150 * appear * (1.0 - leave))
            draw.rectangle(
                [W / 2 - line_width, H / 2 + 52, W / 2 + line_width, H / 2 + 56],
                fill=(139, 213, 255, int(230 * alpha)),
            )
            draw.text(
                (W / 2, H / 2 + 95),
                "КИНОЗАЛЫ 20–32  •  ПУБЛИЧНОЕ ДОСТОЯНИЕ",
                font=sub_font,
                fill=(180, 200, 230, int(230 * alpha)),
                anchor="mm",
            )
            yield frame.convert("RGB")

    encode("ad_aurora.mp4", generate())


def encode(name: str, frames) -> None:
    out = OUT / name
    proc = subprocess.Popen(
        [
            ffmpeg,
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
            "3.1",
            "-preset",
            "medium",
            "-crf",
            "24",
            "-pix_fmt",
            "yuv420p",
            "-movflags",
            "+faststart",
            str(out),
        ],
        stdin=subprocess.PIPE,
    )
    count = 0
    for frame in frames:
        proc.stdin.write(frame.tobytes())
        count += 1
    proc.stdin.close()
    code = proc.wait()
    if code != 0:
        raise RuntimeError(f"ffmpeg failed for {name}")
    print(f"{name}: {count} frames, {out.stat().st_size // 1024} KB")


render_remote()
render_channels()
render_ident()

"""Generate the kids-channel logo and the morning startup film.

The startup film is an original Aurora TV production: schedule card,
beep, one minute of "broadcast starts soon", three logo cycles and a
short trailer with a synthesized Russian announcer. No third-party
cartoon footage or music is used here.
"""

from __future__ import annotations

import json
import math
import subprocess
import tempfile
import wave
from functools import lru_cache
from pathlib import Path

import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(r"C:\AuroraTV")
RAW = ROOT / "app" / "src" / "main" / "res" / "raw"
DRAWABLE = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
AUDIO = ROOT / "kids" / "audio"
CATALOG = ROOT / "app" / "src" / "main" / "assets" / "kids" / "catalog.json"
AMBIENT = RAW / "cinema_ambient.mp3"
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
FONTS = Path(r"C:\Windows\Fonts")

W, H, FPS = 1280, 720, 30
SOON_START = 12.0
SOON_END = 72.0
CYCLES_START = 72.0
TRAILER_START = 87.0

ACCENT = (139, 213, 255)
GOLD = (255, 209, 102)
CORAL = (255, 107, 107)
MINT = (126, 226, 168)
CREAM = (250, 242, 222)
TEXT = (247, 250, 255)
DIM = (176, 198, 218)


@lru_cache(maxsize=64)
def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    path = FONTS / ("segoeuib.ttf" if bold else "segoeuil.ttf")
    if not path.exists():
        path = FONTS / "arial.ttf"
    return ImageFont.truetype(str(path), size)


def ease(value: float) -> float:
    value = max(0.0, min(1.0, value))
    return 1.0 - (1.0 - value) ** 3


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


def catalog_titles() -> list[str]:
    try:
        data = json.loads(CATALOG.read_text(encoding="utf-8"))
        return [item["title"] for item in data.get("cartoons", [])]
    except Exception:
        return [
            "Колобок",
            "Теремок",
            "Репка",
            "Английский: Приветствие",
            "Английский: Цвета",
            "Английский: Животные",
        ]


def schedule_rows() -> list[tuple[str, str]]:
    titles = catalog_titles()
    times = ["14:00", "14:30", "15:00", "15:30", "16:00", "16:30"]
    used = titles[:6]
    rows = []
    for index, title in enumerate(used):
        rows.append((times[index], title))
    rows.append(("17:00", "далее по кругу до 22:00"))
    return rows


# ---------------------------------------------------------------------------
# Logo artwork
# ---------------------------------------------------------------------------

RED = (192, 57, 43)
TEAL = (46, 111, 106)
DARK = (11, 27, 43)
OWL_CREAM = (244, 227, 193)


def star_points(cx: float, cy: float, outer: float, inner_ratio: float = 0.45, points: int = 5):
    result = []
    for index in range(points * 2):
        angle = -math.pi / 2 + index * math.pi / points
        radius = outer if index % 2 == 0 else outer * inner_ratio
        result.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    return result


def draw_owl(draw: ImageDraw.ImageDraw, cx: float, cy: float, scale: float = 1.0) -> None:
    s = scale
    # Branch and tail.
    draw.rounded_rectangle(
        (cx - 128 * s, cy + 112 * s, cx + 132 * s, cy + 130 * s),
        radius=max(3, int(8 * s)),
        fill=(112, 72, 44, 255),
    )
    draw.polygon(
        [(cx - 34 * s, cy + 90 * s), (cx + 34 * s, cy + 90 * s), (cx, cy + 138 * s)],
        fill=TEAL + (255,),
    )
    # Body and belly.
    draw.ellipse(
        (cx - 88 * s, cy - 55 * s, cx + 88 * s, cy + 112 * s),
        fill=OWL_CREAM + (255,),
        outline=TEAL + (255,),
        width=max(2, int(4 * s)),
    )
    draw.ellipse((cx - 52 * s, cy - 2 * s, cx + 52 * s, cy + 98 * s), fill=(255, 248, 228, 255))
    # Wings.
    for side in (-1, 1):
        left = cx + min(side * 58, side * 121) * s
        right = cx + max(side * 58, side * 121) * s
        draw.ellipse(
            (left, cy - 20 * s, right, cy + 86 * s),
            fill=TEAL + (255,),
            outline=(16, 54, 52, 255),
            width=max(2, int(3 * s)),
        )
    # Head and ears.
    draw.ellipse(
        (cx - 82 * s, cy - 125 * s, cx + 82 * s, cy + 5 * s),
        fill=OWL_CREAM + (255,),
        outline=TEAL + (255,),
        width=max(2, int(4 * s)),
    )
    for side in (-1, 1):
        draw.polygon(
            [
                (cx + side * 72 * s, cy - 105 * s),
                (cx + side * 94 * s, cy - 152 * s),
                (cx + side * 44 * s, cy - 126 * s),
            ],
            fill=TEAL + (255,),
        )
    # Eyes.
    for side in (-1, 1):
        left = cx + min(side * 10, side * 58) * s
        right = cx + max(side * 10, side * 58) * s
        draw.ellipse(
            (left, cy - 86 * s, right, cy - 38 * s),
            fill=(255, 255, 255, 255),
            outline=(20, 30, 40, 255),
            width=max(2, int(3 * s)),
        )
        pupil_left = cx + min(side * 22, side * 42) * s
        pupil_right = cx + max(side * 22, side * 42) * s
        draw.ellipse(
            (pupil_left, cy - 70 * s, pupil_right, cy - 50 * s),
            fill=(20, 30, 40, 255),
        )
        glint_left = cx + min(side * 33, side * 39) * s
        glint_right = cx + max(side * 33, side * 39) * s
        draw.ellipse(
            (glint_left, cy - 67 * s, glint_right, cy - 61 * s),
            fill=(255, 255, 255, 255),
        )
    # Beak.
    draw.polygon(
        [(cx - 14 * s, cy - 32 * s), (cx + 14 * s, cy - 32 * s), (cx, cy - 8 * s)],
        fill=GOLD + (255,),
        outline=(120, 60, 20, 255),
    )
    # Feet.
    for side in (-1, 1):
        draw.line(
            (cx + side * 30 * s, cy + 108 * s, cx + side * 30 * s, cy + 130 * s),
            fill=GOLD + (255,),
            width=max(2, int(5 * s)),
        )
        for toe in (-10, 0, 10):
            draw.line(
                (
                    cx + side * 30 * s,
                    cy + 130 * s,
                    cx + side * 30 * s + toe * s,
                    cy + 138 * s,
                ),
                fill=GOLD + (255,),
                width=max(1, int(3 * s)),
            )
    # Red star.
    draw.polygon(star_points(cx + 96 * s, cy - 124 * s, 28 * s), fill=RED + (255,), outline=(120, 24, 20, 255))


def make_logos() -> None:
    DRAWABLE.mkdir(parents=True, exist_ok=True)

    square = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    draw = ImageDraw.Draw(square)
    draw_owl(draw, 256, 185, 1.0)
    shadow = (10, 24, 40, 130)
    draw.text((260, 372), "СОВЁНОК", font=font(62, True), fill=shadow, anchor="ma")
    draw.text((256, 368), "СОВЁНОК", font=font(62, True), fill=(255, 252, 242, 255), anchor="ma")
    draw.text((256, 448), "AURORA TV  •  КАНАЛ 15", font=font(18, True), fill=OWL_CREAM + (230,), anchor="ma")
    square.save(DRAWABLE / "kids_logo.png")

    banner = Image.new("RGBA", (1000, 260), (0, 0, 0, 0))
    draw = ImageDraw.Draw(banner)
    draw.rounded_rectangle((4, 12, 996, 248), radius=38, fill=DARK + (232,), outline=GOLD + (170,), width=3)
    draw.line((18, 70, 18, 190), fill=RED + (230,), width=6)
    draw_owl(draw, 120, 128, 0.62)
    draw.text((214, 54), "СОВЁНОК", font=font(78, True), fill=(255, 252, 242, 255))
    draw.text((218, 152), "AURORA TV  •  КАНАЛ 15", font=font(24, True), fill=OWL_CREAM + (235,))
    draw.text((218, 190), "ДЕТСКИЙ КАНАЛ  •  НОВАЯ ДИРЕКЦИЯ", font=font(17, True), fill=GOLD + (220,))
    banner.save(DRAWABLE / "kids_logo_banner.png")
    make_family_logos()
    print("wrote kids_logo.png and kids_logo_banner.png")


def draw_accessory(draw: ImageDraw.ImageDraw, cx: float, cy: float, scale: float, kind: str) -> None:
    s = scale
    if kind == "cap":
        draw.polygon(
            [
                (cx - 52 * s, cy - 145 * s),
                (cx, cy - 174 * s),
                (cx + 52 * s, cy - 145 * s),
                (cx, cy - 118 * s),
            ],
            fill=DARK + (255,),
            outline=GOLD + (255,),
        )
        draw.line((cx + 46 * s, cy - 146 * s, cx + 70 * s, cy - 110 * s), fill=GOLD + (255,), width=max(2, int(4 * s)))
        draw.ellipse((cx + 62 * s, cy - 116 * s, cx + 78 * s, cy - 100 * s), fill=GOLD + (255,))
    elif kind == "santa":
        draw.polygon(
            [
                (cx - 78 * s, cy - 145 * s),
                (cx + 10 * s, cy - 230 * s),
                (cx + 86 * s, cy - 145 * s),
            ],
            fill=RED + (255,),
        )
        draw.rounded_rectangle(
            (cx - 86 * s, cy - 154 * s, cx + 92 * s, cy - 132 * s),
            radius=max(4, int(10 * s)),
            fill=(255, 252, 242, 255),
        )
        draw.ellipse(
            (cx - 4 * s, cy - 244 * s, cx + 30 * s, cy - 210 * s),
            fill=(255, 252, 242, 255),
        )
    elif kind == "phones":
        draw.arc(
            (cx - 96 * s, cy - 168 * s, cx + 96 * s, cy + 20 * s),
            190,
            350,
            fill=DARK + (255,),
            width=max(4, int(9 * s)),
        )
        for side in (-1, 1):
            draw.rounded_rectangle(
                (cx + side * 104 * s - 13 * s, cy - 120 * s, cx + side * 104 * s + 13 * s, cy - 56 * s),
                radius=max(4, int(10 * s)),
                fill=DARK + (255,),
                outline=GOLD + (255,),
                width=max(1, int(2 * s)),
            )


def make_family_logos() -> None:
    variants = [
        ("junior", "СОВЁНОК", "ЮНИОР", "СОВЁНОК ЮНИОР", "КАНАЛ 16", (74, 144, 190), "none"),
        ("class", "МУЛЬТ", "КЛАСС", "МУЛЬТ-КЛАСС", "КАНАЛ 17", (46, 111, 106), "cap"),
        ("teen", "ТИН", "КЛАСС", "ТИН-КЛАСС", "КАНАЛ 18", (54, 74, 130), "phones"),
    ]
    for suffix, line1, line2, banner_text, channel, accent, accessory in variants:
        square = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
        draw = ImageDraw.Draw(square)
        draw_owl(draw, 256, 205, 1.0)
        if accessory != "none":
            draw_accessory(draw, 256, 205, 1.0, accessory)
        draw.text((256, 352), line1, font=font(54, True), fill=(255, 252, 242, 255), anchor="ma")
        draw.text((256, 406), line2, font=font(54, True), fill=accent + (255,), anchor="ma")
        draw.text((256, 468), f"AURORA TV  •  {channel}", font=font(17, True), fill=OWL_CREAM + (230,), anchor="ma")
        square.save(DRAWABLE / f"kids_logo_{suffix}.png")

        banner = Image.new("RGBA", (1000, 260), (0, 0, 0, 0))
        draw = ImageDraw.Draw(banner)
        draw.rounded_rectangle((4, 12, 996, 248), radius=38, fill=DARK + (232,), outline=accent + (220,), width=3)
        draw.line((18, 70, 18, 190), fill=accent + (230,), width=6)
        draw_owl(draw, 120, 128, 0.62)
        if accessory != "none":
            draw_accessory(draw, 120, 128, 0.62, accessory)
        draw.text((214, 54), banner_text, font=font(62, True), fill=(255, 252, 242, 255))
        draw.text((218, 146), f"AURORA TV  •  {channel}", font=font(22, True), fill=OWL_CREAM + (235,))
        draw.text((218, 184), "СЕМЕЙСТВО СОВЁНОК", font=font(16, True), fill=GOLD + (220,))
        banner.save(DRAWABLE / f"kids_logo_{suffix}_banner.png")
    print("wrote family logos: junior, class, teen")
    make_newyear_logos()


def draw_snowflakes(draw: ImageDraw.ImageDraw, cx: int, cy: int, count: int, spread: int) -> None:
    for index in range(count):
        angle = index * math.tau / count + 0.4
        x = cx + int(spread * math.cos(angle))
        y = cy + int(spread * 0.62 * math.sin(angle))
        radius = 7 + (index % 4) * 3
        draw.polygon(star_points(x, y, radius, inner_ratio=0.34, points=6), fill=(255, 252, 242, 210))


def make_newyear_logos() -> None:
    variants = [
        ("", "СОВЁНОК", "НОВОГОДНИЙ", "СОВЁНОК", "КАНАЛ 15", (192, 57, 43)),
        ("_junior", "СОВЁНОК", "ЮНИОР", "СОВЁНОК ЮНИОР", "КАНАЛ 16", (74, 144, 190)),
        ("_class", "МУЛЬТ-КЛАСС", "НОВОГОДНИЙ", "МУЛЬТ-КЛАСС", "КАНАЛ 17", (46, 111, 106)),
        ("_teen", "ТИН-КЛАСС", "НОВОГОДНИЙ", "ТИН-КЛАСС", "КАНАЛ 18", (54, 74, 130)),
    ]
    for suffix, line1, line2, banner_text, channel, accent in variants:
        square = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
        draw = ImageDraw.Draw(square)
        draw_snowflakes(draw, 256, 210, 10, 210)
        draw_owl(draw, 256, 235, 0.9)
        draw_accessory(draw, 256, 235, 0.9, "santa")
        draw.text((256, 378), line1, font=font(46, True), fill=(255, 252, 242, 255), anchor="ma")
        draw.text((256, 424), line2, font=font(42, True), fill=accent + (255,), anchor="ma")
        draw.text((256, 474), f"AURORA TV  •  {channel}", font=font(16, True), fill=OWL_CREAM + (230,), anchor="ma")
        square.save(DRAWABLE / f"kids_logo_ny{suffix}.png")

        banner = Image.new("RGBA", (1000, 260), (0, 0, 0, 0))
        draw = ImageDraw.Draw(banner)
        draw.rounded_rectangle((4, 12, 996, 248), radius=38, fill=DARK + (235,), outline=accent + (230,), width=3)
        draw.line((18, 70, 18, 190), fill=accent + (230,), width=6)
        draw_snowflakes(draw, 120, 120, 8, 95)
        draw_owl(draw, 120, 145, 0.52)
        draw_accessory(draw, 120, 145, 0.52, "santa")
        draw.text((214, 54), banner_text, font=font(60, True), fill=(255, 252, 242, 255))
        draw.text((218, 146), f"AURORA TV  •  {channel}", font=font(22, True), fill=OWL_CREAM + (235,))
        draw.text((218, 184), "НОВОГОДНИЙ ЭФИР", font=font(16, True), fill=GOLD + (230,))
        banner.save(DRAWABLE / f"kids_logo_ny{suffix}_banner.png")
    print("wrote New Year logos")


def load_logo(path: Path, size: int) -> Image.Image:
    image = Image.open(path).convert("RGBA")
    image.thumbnail((size, size), Image.LANCZOS)
    return image


# ---------------------------------------------------------------------------
# Startup film
# ---------------------------------------------------------------------------

_seed = 20261104
STARS = []


def rnd() -> float:
    global _seed
    _seed = (1103515245 * _seed + 12345) & 0x7FFFFFFF
    return _seed / 0x7FFFFFFF


for _ in range(70):
    STARS.append((rnd() * W, rnd() * H, 1.0 + rnd() * 2.4, rnd() * 6.28))

BALLOONS = [
    (rnd() * W, 180 + rnd() * 420, 18 + rnd() * 18, rnd() * 6.28, i % 3)
    for i in range(9)
]


def background(t: float) -> Image.Image:
    image = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    draw = ImageDraw.Draw(image)
    for y in range(H):
        ratio = y / (H - 1)
        color = (
            int(8 + (23 - 8) * ratio),
            int(24 + (76 - 24) * ratio),
            int(52 + (96 - 52) * ratio),
            255,
        )
        draw.line((0, y, W, y), fill=color)
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((700, -180, 1450, 520), fill=(255, 176, 92, 52))
    gd.ellipse((-220, 420, 500, 1040), fill=(139, 213, 255, 28))
    glow = glow.filter(ImageFilter.GaussianBlur(100))
    image = Image.alpha_composite(image, glow)
    draw = ImageDraw.Draw(image)
    for x, y, radius, phase in STARS:
        alpha = int(65 + 115 * (0.5 + 0.5 * math.sin(t * 0.7 + phase)))
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=(255, 244, 214, alpha))
    for x, y, radius, phase, color_index in BALLOONS:
        drift = 18 * math.sin(t * 0.22 + phase)
        y2 = (y - t * 5) % (H + 120) - 40
        color = [(255, 107, 107), (255, 209, 102), (126, 226, 168)][color_index]
        draw.line((x + drift, y2 + radius, x + drift, y2 + radius * 2.2), fill=(210, 220, 235, 90), width=2)
        draw.ellipse(
            (x + drift - radius, y2 - radius, x + drift + radius, y2 + radius),
            fill=color + (45,),
            outline=color + (110,),
            width=2,
        )
    return image


def paste_logo(frame: Image.Image, logo: Image.Image, cx: int, cy: int, scale: float, alpha: float) -> None:
    if alpha <= 0.01:
        return
    width = max(1, int(logo.width * scale))
    height = max(1, int(logo.height * scale))
    resized = logo.resize((width, height), Image.LANCZOS)
    if alpha < 0.999:
        resized = resized.copy()
        pixels = resized.load()
        for y in range(resized.height):
            for x in range(resized.width):
                r, g, b, a = pixels[x, y]
                pixels[x, y] = (r, g, b, int(a * alpha))
    frame.alpha_composite(resized, (cx - width // 2, cy - height // 2))


def draw_schedule(draw: ImageDraw.ImageDraw, t: float) -> None:
    slide = int((1.0 - ease(min(1.0, t / 0.5))) * 26)
    draw.rounded_rectangle((92, 56 - slide, 1188, 646 - slide), radius=28, fill=(7, 20, 36, 238), outline=(255, 209, 102, 150), width=3)
    draw.text((126, 92 - slide), "ПРОГРАММА НА СЕГОДНЯ", font=font(32, True), fill=GOLD + (245,))
    draw.text((126, 136 - slide), "СОВЁНОК  •  14:00–17:00", font=font(18, True), fill=ACCENT + (225,))
    rows = schedule_rows()
    top = 196 - slide
    for index, (time_text, title_text) in enumerate(rows):
        y = top + index * 48
        if index % 2 == 0:
            draw.rounded_rectangle((118, y - 6, 1162, y + 38), radius=10, fill=(15, 38, 62, 170))
        draw.text((134, y), time_text, font=font(22, True), fill=GOLD + (240,))
        draw.text((246, y + 1), title_text, font=font(22), fill=(236, 243, 250, 235))


def draw_soon(draw: ImageDraw.ImageDraw, t: float, logo_banner: Image.Image) -> None:
    progress = max(0.0, min(1.0, (t - SOON_START) / (SOON_END - SOON_START)))
    pulse = 0.5 + 0.5 * math.sin(t * 1.6)
    draw.text((W // 2, 208), "AURORA TV  •  ДЕТСКИЙ КАНАЛ", font=font(20, True), fill=ACCENT + (230,), anchor="ma")
    draw.text((W // 2, 250), "ТРАНСЛЯЦИЯ", font=font(84, True), fill=(255, 255, 255, 255), anchor="ma")
    draw.text((W // 2, 338), "СКОРО НАЧНЁТСЯ", font=font(64, True), fill=GOLD + (255,), anchor="ma")
    draw.text((W // 2, 438), "СОВЁНОК  •  КАНАЛ 15", font=font(26, True), fill=CREAM + (230,), anchor="ma")
    # Progress bar.
    draw.rounded_rectangle((250, 520, 1030, 540), radius=10, fill=(8, 20, 34, 220), outline=(139, 213, 255, 90), width=1)
    draw.rounded_rectangle((250, 520, 250 + int(780 * progress), 540), radius=10, fill=GOLD + (220,))
    draw.text((W // 2, 560), "музыка • заставка • начало", font=font(17), fill=DIM + (210,), anchor="ma")
    # Pulsing dots.
    for index in range(3):
        x = W // 2 - 30 + index * 30
        alpha = int(120 + 100 * (0.5 + 0.5 * math.sin(t * 3.0 - index * 0.7)))
        draw.ellipse((x - 6, 620 - 6, x + 6, 620 + 6), fill=GOLD + (alpha,))


def startup_frame(t: float, total: float, logo_square: Image.Image, logo_banner: Image.Image) -> Image.Image:
    frame = background(t)
    draw = ImageDraw.Draw(frame)
    corner_visible = True
    center_alpha = 0.0
    center_scale = 1.0
    center_y = 390

    if t < SOON_START:
        draw_schedule(draw, t)
        corner_visible = False
    elif t < SOON_END:
        draw_soon(draw, t, logo_banner)
        corner_visible = True
    elif t < TRAILER_START:
        # Three centre/corner logo cycles.
        cycle = (t - CYCLES_START) / 5.0
        phase = cycle % 1.0
        if phase < 0.55:
            center_alpha = min(1.0, phase / 0.18, (0.55 - phase) / 0.18)
            center_scale = 0.82 + 0.18 * ease(phase / 0.55)
            corner_visible = False
        else:
            corner_visible = True
        draw.text((W // 2, 610), "СОВЁНОК  •  СКОРО", font=font(24, True), fill=CREAM + (210,), anchor="ma")
    else:
        # Trailer.
        local = t - TRAILER_START
        trailer_len = max(17.0, total - TRAILER_START)
        if local < 5.5:
            draw.text((W // 2, 220), "AURORA TV  •  НОВАЯ ДИРЕКЦИЯ", font=font(30, True), fill=ACCENT + (240,), anchor="ma")
            draw.text((W // 2, 282), "ДЕТСКИЙ КАНАЛ", font=font(76, True), fill=(255, 255, 255, 255), anchor="ma")
            draw.text((W // 2, 380), "СОВЁНОК", font=font(86, True), fill=GOLD + (255,), anchor="ma")
            corner_visible = True
        elif local < trailer_len * 0.58:
            draw.text((W // 2, 360), "НОВЫЙ КАНАЛ", font=font(32, True), fill=ACCENT + (235,), anchor="ma")
            draw.text((W // 2, 406), "ДЛЯ НОВЫХ", font=font(66, True), fill=(255, 255, 255, 255), anchor="ma")
            draw.text((W // 2, 482), "ПОКОЛЕНИЙ", font=font(66, True), fill=GOLD + (255,), anchor="ma")
            center_alpha = 1.0
            center_scale = 0.55 + 0.02 * math.sin(t * 1.2)
            center_y = 215
            corner_visible = False
        else:
            draw.text((W // 2, 250), "СМОТРИТЕ КАЖДЫЙ ДЕНЬ", font=font(44, True), fill=(255, 255, 255, 255), anchor="ma")
            draw.text((W // 2, 330), "05:00 — 22:00", font=font(64, True), fill=GOLD + (255,), anchor="ma")
            draw.text((W // 2, 430), "КАНАЛ 15  •  СОВЁНОК", font=font(28, True), fill=CREAM + (235,), anchor="ma")
            corner_visible = True

    if center_alpha > 0:
        paste_logo(frame, logo_square, W // 2, center_y, 0.78 * center_scale, center_alpha)
    if corner_visible:
        paste_logo(frame, logo_banner, 148, 44, 0.27, 1.0)

    # Bottom line.
    draw.line((0, H - 44, W, H - 44), fill=(255, 209, 102, 80), width=1)
    draw.text((28, H - 38), "AURORA TV  •  СОВЁНОК  •  КАНАЛ 15", font=font(15, True), fill=(176, 198, 218, 210))

    # Gentle fade at both ends.
    edge = min(1.0, t / 0.35, (total - t) / 0.35)
    if edge < 1.0:
        black = Image.new("RGBA", (W, H), (0, 0, 0, 255))
        frame = Image.blend(black, frame, max(0.0, edge))
    return frame.convert("RGB")


def encode(frames, duration: float, voice: Path, beep: Path) -> None:
    silent = Path(tempfile.gettempdir()) / "kids_startup.silent.mp4"
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
    count = 0
    for frame in frames:
        proc.stdin.write(frame.tobytes())
        count += 1
    proc.stdin.close()
    if proc.wait() != 0:
        raise RuntimeError("ffmpeg failed: kids_startup video")

    out = RAW / "kids_startup.mp4"
    voice_delay = int(TRAILER_START * 1000)
    audio_filter = (
        "[1:a]volume=0.24,atrim=0:{duration:.3f},asetpts=PTS-STARTPTS[m];"
        "[2:a]adelay=600|600,volume=0.50[b];"
        "[3:a]adelay={vd}|{vd},volume=1.55[v];"
        "[m][b][v]amix=inputs=3:duration=longest:dropout_transition=0:normalize=0,alimiter=limit=0.95[a]"
    ).format(duration=duration, vd=voice_delay)
    subprocess.run(
        [
            FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(silent), "-stream_loop", "-1", "-i", str(AMBIENT),
            "-i", str(beep), "-i", str(voice),
            "-filter_complex", audio_filter,
            "-map", "0:v", "-map", "[a]", "-t", f"{duration:.3f}",
            "-c:v", "copy", "-c:a", "aac", "-b:a", "128k",
            "-movflags", "+faststart", str(out),
        ],
        check=True,
    )
    print(f"kids_startup.mp4: {count} frames, {out.stat().st_size // 1024} KB")


def make_beep() -> Path:
    AUDIO.mkdir(parents=True, exist_ok=True)
    path = AUDIO / "beep.wav"
    rate = 44100
    duration = 0.45
    frames = int(rate * duration)
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        samples = bytearray()
        for index in range(frames):
            t = index / rate
            env = min(1.0, t / 0.01, (duration - t) / 0.06)
            value = int(12000 * env * math.sin(2 * math.pi * 1000 * t))
            samples += int(value).to_bytes(2, "little", signed=True)
        handle.writeframes(bytes(samples))
    return path


def voice_duration(path: Path) -> float:
    with wave.open(str(path), "rb") as handle:
        return handle.getnframes() / float(handle.getframerate())


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--test", action="store_true")
    args = parser.parse_args()

    make_logos()
    voice = AUDIO / "startup_voice.wav"
    if not voice.exists():
        raise SystemExit(f"missing {voice}; run scripts\\tts_irina.ps1 first")
    beep = make_beep()
    total = max(97.0, TRAILER_START + voice_duration(voice) + 2.5)
    logo_square = load_logo(DRAWABLE / "kids_logo.png", 420)
    logo_banner = load_logo(DRAWABLE / "kids_logo_banner.png", 560)

    if args.test:
        for stamp, name in [(2.0, "schedule"), (30.0, "soon"), (67.0, "logo"), (83.0, "trailer")]:
            startup_frame(stamp, total, logo_square, logo_banner).save(ROOT / "test-artifacts" / f"kids_{name}.png")
            print("wrote", name)
        return

    frames_count = int(total * FPS)

    def generate():
        for index in range(frames_count):
            t = index / FPS
            yield startup_frame(t, total, logo_square, logo_banner)
            if index % 120 == 0:
                print(f"kids_startup: frame {index}/{frames_count}", flush=True)

    encode(generate(), total, voice, beep)


if __name__ == "__main__":
    main()

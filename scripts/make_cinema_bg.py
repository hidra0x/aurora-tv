"""Generate the blue bokeh backdrop used by the cinema schedule and player."""

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

WIDTH, HEIGHT = 1920, 1080
OUT = Path(r"C:\AuroraTV\app\src\main\res\drawable-nodpi\cinema_bg.jpg")

base = Image.new("RGB", (WIDTH, HEIGHT))
draw = ImageDraw.Draw(base)
top = (6, 16, 62)
bottom = (18, 64, 158)
for y in range(HEIGHT):
    t = y / (HEIGHT - 1)
    draw.line(
        [(0, y), (WIDTH, y)],
        fill=(
            round(top[0] + (bottom[0] - top[0]) * t),
            round(top[1] + (bottom[1] - top[1]) * t),
            round(top[2] + (bottom[2] - top[2]) * t),
        ),
    )

palette = [
    (60, 120, 230),
    (95, 155, 250),
    (135, 190, 255),
    (175, 215, 255),
    (80, 140, 240),
    (55, 110, 220),
]

rng = random.Random(20261103)

far = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
far_draw = ImageDraw.Draw(far)
for _ in range(12):
    radius = rng.randint(190, 420)
    cx = rng.randint(-120, WIDTH + 120)
    cy = rng.randint(-100, HEIGHT + 100)
    color = rng.choice(palette)
    alpha = rng.randint(26, 54)
    far_draw.ellipse(
        [cx - radius, cy - radius, cx + radius, cy + radius],
        fill=color + (alpha,),
    )
far = far.filter(ImageFilter.GaussianBlur(90))

near = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
near_draw = ImageDraw.Draw(near)
for _ in range(26):
    radius = rng.randint(36, 150)
    cx = rng.randint(-80, WIDTH + 80)
    cy = rng.randint(-60, HEIGHT + 60)
    color = rng.choice(palette)
    alpha = rng.randint(42, 100)
    near_draw.ellipse(
        [cx - radius, cy - radius, cx + radius, cy + radius],
        fill=color + (alpha,),
    )
    # A brighter inner disc makes the bokeh feel like out-of-focus highlights.
    inner = radius * rng.uniform(0.45, 0.7)
    near_draw.ellipse(
        [cx - inner, cy - inner, cx + inner, cy + inner],
        fill=color + (min(alpha + 45, 150),),
    )
near = near.filter(ImageFilter.GaussianBlur(24))

spark = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
spark_draw = ImageDraw.Draw(spark)
for _ in range(34):
    radius = rng.randint(5, 24)
    cx = rng.randint(0, WIDTH)
    cy = rng.randint(0, HEIGHT)
    color = rng.choice(palette)
    alpha = rng.randint(70, 150)
    spark_draw.ellipse(
        [cx - radius, cy - radius, cx + radius, cy + radius],
        fill=color + (alpha,),
    )
spark = spark.filter(ImageFilter.GaussianBlur(6))

glow = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
glow_draw = ImageDraw.Draw(glow)
for cx, cy, radius, alpha in [
    (1560, 880, 520, 60),
    (300, 220, 420, 34),
    (1180, 240, 330, 38),
]:
    glow_draw.ellipse(
        [cx - radius, cy - radius, cx + radius, cy + radius],
        fill=(110, 178, 245, alpha),
    )
glow = glow.filter(ImageFilter.GaussianBlur(120))

image = Image.alpha_composite(base.convert("RGBA"), far)
image = Image.alpha_composite(image, near)
image = Image.alpha_composite(image, spark)
image = Image.alpha_composite(image, glow)

# Gentle vignette keeps the text readable at the edges.
vignette = Image.new("L", (WIDTH, HEIGHT), 0)
v_draw = ImageDraw.Draw(vignette)
for i in range(160):
    alpha = round(70 * (1 - i / 160) ** 1.6)
    v_draw.rectangle([i, i, WIDTH - i, HEIGHT - i], outline=alpha)
dark = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 4, 255))
image = Image.composite(Image.alpha_composite(image, dark), image, vignette)

image.convert("RGB").save(OUT, quality=92, subsampling=2)
print("wrote", OUT, image.size)

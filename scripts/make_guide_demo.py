#!/usr/bin/env python3
"""Сборка короткого видео-демо телегида Aurora TV из кадров реального теста на ТВ."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(r'C:\AuroraTV')
SRC = ROOT / 'test-artifacts'
OUT = ROOT / 'release-assets' / 'guide-demo-frames'
OUT.mkdir(parents=True, exist_ok=True)

FONT = r'C:\Windows\Fonts\arial.ttf'
BOLD = r'C:\Windows\Fonts\arialbd.ttf'
W, H = 1920, 1080
BAR_H = 128


def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size)


def caption(image, title, detail):
    frame = image.convert('RGB').resize((W, H))
    overlay = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    draw.rectangle((0, H - BAR_H, W, H), fill=(6, 12, 28, 235))
    draw.rectangle((0, H - BAR_H, 10, H), fill=(80, 160, 255, 255))
    draw.text((48, H - BAR_H + 22), title, font=font(44, True), fill=(255, 255, 255, 255))
    draw.text((50, H - BAR_H + 82), detail, font=font(26), fill=(160, 195, 240, 255))
    return Image.alpha_composite(frame.convert('RGBA'), overlay).convert('RGB')


def title_card():
    img = Image.new('RGB', (W, H), (5, 10, 24))
    d = ImageDraw.Draw(img)
    for y in range(H):
        k = y / H
        d.line((0, y, W, y), fill=(int(5 + 12 * k), int(10 + 24 * k), int(24 + 52 * k)))
    d.rounded_rectangle((160, 250, 1760, 830), radius=36, fill=(10, 22, 48), outline=(70, 140, 230), width=4)
    d.text((W // 2, 380), 'AURORA TV', font=font(96, True), fill=(255, 255, 255), anchor='mm')
    d.text((W // 2, 500), 'ТЕЛЕГИД', font=font(120, True), fill=(90, 175, 255), anchor='mm')
    d.text((W // 2, 620), '43 канала · «Сейчас / Далее» · переключение без лаунчера',
           font=font(34), fill=(190, 210, 240), anchor='mm')
    d.text((W // 2, 720), 'тест на реальном ТВ · 1.9-beta6',
           font=font(30), fill=(130, 160, 205), anchor='mm')
    return img


frames = [
    ('00-title.png', title_card(), 3.0),
    ('01-guide.png', caption(Image.open(SRC / 'guide.png'), 'Телегид — все каналы',
                             'GUIDE открывает полный список: 0, ТВ 1–14, дети 15–18, кино 20–43'), 3.2),
    ('02-scroll.png', caption(Image.open(SRC / 'guide_scroll.png'), 'Прокрутка и программа',
                              'У каждого канала — «Сейчас» и «Далее», статус эфира'), 2.8),
    ('03-filter.png', caption(Image.open(SRC / 'guide_filter2.png'), 'Фильтры',
                              'Все · ТВ 1–14 · Дети 15–18 · Кино 20–43'), 2.8),
    ('04-current.png', caption(Image.open(SRC / 'guide_direct34.png'), 'Текущий канал подсвечен',
                               'Канал 34 Aurora Нуар — в гиде отмечен как текущий'), 2.8),
    ('05-switch.png', caption(Image.open(SRC / 'guide_after_chplus.png'), 'Переключение из гида',
                              'Выбор канала в гиде или CH+/CH- переключает эфир без выхода в меню'), 3.2),
    ('06-cinema.png', caption(Image.open(SRC / 'cinema34b.png'), 'Кинозал 34',
                              'Новый тематический канал начал сеанс сразу после выбора в гиде'), 3.0),
]

for name, image, seconds in frames:
    image.save(OUT / name)
    (OUT / (name + '.dur')).write_text(str(seconds), encoding='utf-8')

lines = ['ffconcat version 1.0']
for name, _, seconds in frames:
    lines.append(f"file '{OUT / name}'")
    lines.append(f'duration {seconds}')
lines.append(f"file '{OUT / frames[-1][0]}'")
(OUT / 'list.ffconcat').write_text('\n'.join(lines), encoding='utf-8')
print('frames:', OUT)

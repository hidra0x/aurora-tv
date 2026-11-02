# Инфоканал Aurora TV

Канал **0** в Aurora — это не одно видео, а плейлист из нескольких роликов.
После последнего эпизода плейлист начинается заново, как телеканал.

## Эпизоды 1.4

| № | Эпизод | Шаблон | Длительность |
|---|---|---|---|
| 1 | Трейлер Aurora TV 1.4 | `trailer` | ~43 с |
| 2 | Каналы и цифры | `tutorial` | ~49 с |
| 3 | Туториал по лаунчеру | `tutorial` | ~42 с |
| 4 | Пульт и навигация | `tutorial` | ~31 с |
| 5 | Свой M3U-плейлист | `tutorial` | ~31 с |
| 6 | Что нового в 1.4 | `whatsnew` | ~38 с |

Включение: Aurora → набрать `0` → OK (или подождать 3 секунды).
Порядок роликов **случайный** при каждом запуске; после последнего
плейлист перемешивается и начинается заново. Между роликами появляются
короткие подсказки, тоже случайные.

## Как генерируются видео

`info/episodes.json` описывает эпизоды и сцены. Для каждой сцены:

1. **Голос** — Windows SAPI, голос `Microsoft Irina Desktop` (ru-RU).
   Текст озвучки берётся из поля `text`.
2. **Кадр** — Pillow рисует 1920×1080: заголовок, скриншот, плашки,
   номер шага, нижнюю панель.
3. **Реальное видео** — для сцен `clip` используются записи экрана
   (`adb shell screenrecord`), которые накладываются на оверлей с плашками.
4. **Сегмент** — ffmpeg соединяет кадр/клип и WAV, добавляет fade in/out,
   кодирует H.264/AAC.
5. **Эпизод** — сегменты склеиваются; для трейлера и «что нового»
   добавляется сгенерированная фоновая музыка.
6. **Индекс** — `info/index.json` с названиями и длительностями.

Готовые MP4 лежат в `app/src/main/res/raw/info_*.mp4` и работают без сети.

## Шаблоны

- **trailer** — быстрый монтаж, крупные титры, полноэкранные скриншоты.
- **tutorial** — карточка со скриншотом справа, шаг и текст слева.
- **whatsnew** — фиче-карточки: заголовок, буллеты, скриншот.
- **title/outro** — общие титульные и финальные экраны.

## Generate

Требования: Windows, Python 3.11+, ffmpeg, русский голос SAPI Irina.

```powershell
python -m pip install --user pillow imageio-ffmpeg

# все эпизоды
python scripts\generate_info.py

# только один/два
python scripts\generate_info.py --only trailer,channels

# пересобрать без повторной озвучки
python scripts\generate_info.py --skip-tts
```

Скриншоты для сцен — в `info/screens/`. Их можно обновить через ADB:

```powershell
adb shell screencap -p /sdcard/home.png
adb pull /sdcard/home.png info/screens/home.png
```

После генерации скопируйте `info/build/<id>.mp4` в
`app/src/main/res/raw/info_<id>.mp4` и соберите APK.

## Добавить эпизод

1. Добавьте объект в `info/episodes.json`:
   ```json
   {
     "id": "remote",
     "title": "Пульт",
     "template": "tutorial",
     "voice_rate": -1,
     "scenes": [
       {"type": "step", "step": 1, "file": "home.png",
        "title": "Пульт", "caption": "OK — выбрать",
        "text": "Короткий текст озвучки.", "duration": 5}
     ]
   }
   ```
2. Для реального видео добавьте сцену `clip`:
   ```json
   {"type": "clip", "file": "aurora_nav.mp4", "start": 1.0,
    "title": "Вживую", "caption": "Навигация с пульта",
    "text": "Вот как это выглядит вживую.", "duration": 8}
   ```
   Клипы лежат в `info/clips/`; запись: `adb shell screenrecord ...`.
3. Запустите `python scripts\generate_info.py --only remote`.
4. Добавьте MP4 в `res/raw` и эпизод в `InfoRepository.kt`.

## Ограничения

- SAPI Irina звучит ровно, но не «как диктор»; для другого голоса нужен
  RHVoice/Silero или запись человека.
- Музыка в трейлере генерируется математически (без сторонних треков).
- Видео 1080p H.264/AAC; на Android 7+ играет через Media3 ExoPlayer.

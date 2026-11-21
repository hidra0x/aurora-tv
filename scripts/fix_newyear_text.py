import json
from pathlib import Path

ROOT = Path(r"C:\AuroraTV")
films_path = ROOT / "app" / "src" / "main" / "assets" / "cinema" / "films.json"
channels_path = ROOT / "app" / "src" / "main" / "assets" / "cinema" / "channels.json"

film_text = {
    "christmas-carol-1935": (
        "Рождественская песнь",
        "Экранизация повести Чарльза Диккенса о скупом Скрудже, которому предстоит измениться в рождественскую ночь.",
    ),
    "christmas-carol-1910": (
        "Рождественская песнь (1910)",
        "Ранняя немая экранизация рождественской повести Диккенса.",
    ),
    "christmas-comes-1936": (
        "Рождество бывает раз в году",
        "Цветной рождественский мультфильм 1936 года о добром празднике и подарках.",
    ),
    "trap-santa-1909": (
        "Ловушка для Санта-Клауса",
        "Рождественская история 1909 года: дети ждут Санта-Клауса.",
    ),
    "scrooge-1935": (
        "Скрудж",
        "Классическая рождественская история о Скрудже, public domain.",
    ),
}

films = json.loads(films_path.read_text(encoding="utf-8"))
for film in films:
    if film["id"] in film_text:
        film["title"], film["description"] = film_text[film["id"]]
films_path.write_text(json.dumps(films, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

channels = json.loads(channels_path.read_text(encoding="utf-8"))
for channel in channels["channels"]:
    if channel["number"] == 33:
        channel["name"] = "Новогодний сеанс"
        channel["description"] = (
            "Зимние и рождественские фильмы public domain: Диккенс, Санта-Клаус и праздничные мультфильмы."
        )
channels_path.write_text(json.dumps(channels, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("fixed New Year text")

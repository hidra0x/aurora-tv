import json
from pathlib import Path

ROOT = Path(r"C:\AuroraTV")
PATH = ROOT / "app" / "src" / "main" / "assets" / "cinema" / "channels.json"

NEW_CHANNELS = [
    (34, "aurora-noir", "Aurora Нуар", "Ноар, детективы и мрачные городские истории.", "#B0A7FF",
     ["detour", "doa", "scarlet-street", "quicksand", "stranger", "39-steps", "red-house", "amazing-mr-x"]),
    (35, "aurora-thriller", "Aurora Триллер", "Напряжение, тайны и неожиданные развязки.", "#FF7A9C",
     ["39-steps", "stranger", "charade", "lady-vanishes", "red-house", "amazing-mr-x", "detour", "quicksand"]),
    (36, "aurora-drama", "Aurora Драма", "Сильные истории о людях и выборе.", "#F5A97A",
     ["farewell-arms", "of-human-bondage", "meet-john-doe", "lost-weekend", "quicksand", "scrooge", "mother", "end-st-petersburg"]),
    (37, "aurora-classic", "Aurora Классика", "Золотая классика мирового кино.", "#E0E0E0",
     ["caligari", "nosferatu", "potemkin", "mother", "end-st-petersburg", "metropolis", "october", "earth"]),
    (38, "aurora-silent", "Aurora Немое кино", "Немые шедевры и раннее кино.", "#C8B6A6",
     ["potemkin", "mother", "end-st-petersburg", "october", "earth", "caligari", "nosferatu", "metropolis", "aelita", "movie-camera", "christmas-carol-1910", "trap-santa-1909"]),
    (39, "aurora-adventure", "Aurora Приключения", "Путешествия, погони и большие истории.", "#7EE2A8",
     ["captain-marvel-1", "captain-marvel-2", "captain-marvel-3", "39-steps", "lady-vanishes", "charade", "farewell-arms", "scarlet-street"]),
    (40, "aurora-retro-comedy", "Aurora Ретро-комедия", "Старые добрые комедии для хорошего вечера.", "#FFB86B",
     ["his-girl-friday", "little-shop", "nothing-sacred", "front-page", "sherlock-jr", "meet-john-doe", "charade", "house-trubnaya"]),
    (41, "aurora-gold", "Aurora Золотая коллекция", "Проверенные временем фильмы.", "#F5C97A",
     ["charade", "his-girl-friday", "lost-weekend", "farewell-arms", "of-human-bondage", "sherlock-jr", "meet-john-doe", "39-steps"]),
    (42, "aurora-chronicle", "Aurora Кинохроника", "Документальное и историческое кино.", "#8BD5FF",
     ["movie-camera", "earth", "potemkin", "october", "mother", "end-st-petersburg", "caligari", "nosferatu"]),
    (43, "aurora-night", "Aurora Ночной клуб", "Нуар, мистика и кино для позднего вечера.", "#6C7AFF",
     ["night-living-dead", "last-man-earth", "detour", "scarlet-street", "red-house", "quicksand", "plan-9", "little-shop"]),
]


def main() -> None:
    data = json.loads(PATH.read_text(encoding="utf-8"))
    existing = {channel["number"] for channel in data["channels"]}
    for number, ident, name, description, color, films in NEW_CHANNELS:
        if number in existing:
            continue
        data["channels"].append({
            "number": number,
            "id": ident,
            "name": name,
            "description": description,
            "color": color,
            "films": films,
        })
    PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("channels", len(data["channels"]), "last", data["channels"][-1]["number"])


if __name__ == "__main__":
    main()

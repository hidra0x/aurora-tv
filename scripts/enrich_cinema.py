#!/usr/bin/env python3
"""Add Russian classics and a serial to the Archive.org cinema catalog."""

from __future__ import annotations

import json
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "info" / "cinema_films.json"


def download_url(identifier: str, name: str) -> str:
    return f"https://archive.org/download/{identifier}/{urllib.parse.quote(name)}"


EXTRA = [
    {
        "id": "potemkin",
        "title": "Броненосец «Потёмкин»",
        "year": 1925,
        "genres": ["russian", "classic"],
        "duration": 4297,
        "url": download_url(
            "battleship-potemkin-1925-by-sergei-m.-eisenstein",
            "Battleship.Potemkin.1925.1080p.BluRay.x264-[YTS.AM].mp4",
        ),
        "page": "https://archive.org/details/battleship-potemkin-1925-by-sergei-m.-eisenstein",
        "description": "Немое кино Эйзенштейна о восстании матросов в 1905 году.",
    },
    {
        "id": "aelita",
        "title": "Аэлита",
        "year": 1924,
        "genres": ["russian", "sci-fi"],
        "duration": 4838,
        "url": download_url(
            "Aelita-QueenOfMarsrussianScience-fictionFilm1924",
            "Aelita-QueenOfMarsrussianSci-fi1924.mp4",
        ),
        "page": "https://archive.org/details/Aelita-QueenOfMarsrussianScience-fictionFilm1924",
        "description": "Советская фантастика о полёте на Марс и марсианской революции.",
    },
    {
        "id": "movie-camera",
        "title": "Человек с киноаппаратом",
        "year": 1929,
        "genres": ["russian", "documentary"],
        "duration": 4009,
        "url": download_url(
            "ChelovekskinoapparatomManWithAMovieCamera",
            "Chelovek_s_kino-apparatom_512kb.mp4",
        ),
        "page": "https://archive.org/details/ChelovekskinoapparatomManWithAMovieCamera",
        "description": "Экспериментальный документальный фильм Дзиги Вертова.",
    },
    {
        "id": "captain-marvel-1",
        "title": "Капитан Марвел — серия 1",
        "year": 1941,
        "genres": ["series", "adventure"],
        "duration": 1809,
        "url": download_url("the-adventures-of-captain-marvel", "captainmarvel_ep01.mp4"),
        "page": "https://archive.org/details/the-adventures-of-captain-marvel",
        "description": "Киносериал 1941 года: Билли Бэтсон и Капитан Марвел.",
    },
    {
        "id": "captain-marvel-2",
        "title": "Капитан Марвел — серия 2",
        "year": 1941,
        "genres": ["series", "adventure"],
        "duration": 993,
        "url": download_url("the-adventures-of-captain-marvel", "captainmarvel_ep02.mp4"),
        "page": "https://archive.org/details/the-adventures-of-captain-marvel",
        "description": "Вторая серия киносериала о Капитане Марвеле.",
    },
    {
        "id": "captain-marvel-3",
        "title": "Капитан Марвел — серия 3",
        "year": 1941,
        "genres": ["series", "adventure"],
        "duration": 1070,
        "url": download_url("the-adventures-of-captain-marvel", "captainmarvel_ep03.mp4"),
        "page": "https://archive.org/details/the-adventures-of-captain-marvel",
        "description": "Третья серия киносериала о Капитане Марвеле.",
    },
    {
        "id": "earth",
        "title": "Земля",
        "year": 1930,
        "genres": ["russian", "classic"],
        "duration": 4681,
        "url": download_url("earth-1930", "Earth (1930).ia.mp4"),
        "page": "https://archive.org/details/earth-1930",
        "description": "Немая драма Довженко о земле, хлебе и коллективизации.",
    },
    {
        "id": "october",
        "title": "Октябрь",
        "year": 1928,
        "genres": ["russian", "classic"],
        "duration": 6016,
        "url": download_url(
            "october1928",
            "BBC.October.Ten.Days.that.Shook.the.World.720p.HDTV.x264.AAC.MVGroup.Forum.mp4",
        ),
        "page": "https://archive.org/details/october1928",
        "description": "Эйзенштейн о революции 1917 года.",
    },
    {
        "id": "mother",
        "title": "Мать",
        "year": 1926,
        "genres": ["russian", "classic"],
        "duration": 5235,
        "url": download_url(
            "mother-1926-by-vsevolod-pudovkin",
            "Mother.1926.1080p.BluRay.x264.AAC-[YTS.MX].mp4",
        ),
        "page": "https://archive.org/details/mother-1926-by-vsevolod-pudovkin",
        "description": "Драма Пудовкина о матери, которая встаёт на сторону революции.",
    },
    {
        "id": "end-st-petersburg",
        "title": "Конец Санкт-Петербурга",
        "year": 1927,
        "genres": ["russian", "classic"],
        "duration": 4402,
        "url": download_url(
            "the-end-of-st.-petersburg-1927-by-vsevolod-pudovkin",
            "The.End.Of.St..Petersburg.1927.1080p.BluRay.x264.AAC-[YTS.MX].mp4",
        ),
        "page": "https://archive.org/details/the-end-of-st.-petersburg-1927-by-vsevolod-pudovkin",
        "description": "Пудовкин: деревня, завод и революция.",
    },
    {
        "id": "house-trubnaya",
        "title": "Дом на Трубной",
        "year": 1928,
        "genres": ["russian", "comedy"],
        "duration": 5320,
        "url": download_url(
            "dom-na-trubnoy-1928",
            "Dom.na.Trubnoy.1928.1080p.WEB-DL.DD+2.0.H.264-SbR.mp4",
        ),
        "page": "https://archive.org/details/dom-na-trubnoy-1928",
        "description": "Советская комедия о домработнице, которая меняет жизнь дома.",
    },
]


DESCRIPTIONS = {
    "nosferatu": "Немое выражение ужаса: граф Орлок приносит чуму в Висборг.",
    "night-living-dead": "Классика зомби-хоррора: группа выживших в фермерском доме.",
    "caligari": "Немецкий экспрессионизм: сомнамбула и убийства на ярмарке.",
    "sherlock-jr": "Бастер Китон — киномеханик, который входит внутрь фильма.",
    "his-girl-friday": "Скорострельная газетная комедия с Кэри Грантом.",
    "little-shop": "Мрачная комедия о цветке, который питается людьми.",
    "metropolis": "Футуристический город, машины и восстание рабочих.",
    "plan-9": "Легендарный трэш-шедевр Эда Вуда.",
    "last-man-earth": "Последний человек против вампиров; по мотивам «Я — легенда».",
    "brain-wouldnt-die": "Хирург и живая голова — культовый хоррор.",
    "detour": "Нуар о случайном попутчике и цепочке неприятностей.",
    "doa": "Бухгалтер узнаёт, что отравлен, и ищет убийцу.",
    "stranger": "Орсон Уэллс — нацистский преступник в маленьком городке.",
    "scarlet-street": "Нуар о художнике, девушке и шантаже.",
    "red-house": "Готическая тайна старого дома.",
    "farewell-arms": "Романтика на фоне Первой мировой.",
    "meet-john-doe": "Газетная история о человеке, ставшем символом.",
    "snows-kilimanjaro": "Приключенческая драма по Хемингуэю.",
    "amazing-mr-x": "Нуар с медиумом и обманом.",
    "quicksand": "Молодой человек увязает в долгах и преступлении.",
    "lost-weekend": "Драма об алкоголизме.",
    "of-human-bondage": "Драма по Сомерсету Моэму.",
    "nothing-sacred": "Комедия о фальшивом герое.",
    "my-man-godfrey": "Скрюбол-комедия о дворецком.",
    "front-page": "Газетная комедия.",
    "charade": "Триллер с Кэри Грантом и Одри Хепбёрн.",
    "39-steps": "Шпионский триллер Хичкока.",
    "lady-vanishes": "Детектив Хичкока в поезде.",
    "the-kid": "Чаплин: Малыш и Бродяга.",
}

REMOVE = {"the-kid", "brain-wouldnt-die", "snows-kilimanjaro", "my-man-godfrey"}

TITLES = {
    "nosferatu": "Носферату",
    "night-living-dead": "Ночь живых мертвецов",
    "caligari": "Кабинет доктора Калигари",
    "sherlock-jr": "Шерлок-младший",
    "his-girl-friday": "Его девушка Пятница",
    "little-shop": "Маленький магазинчик ужасов",
    "metropolis": "Метрополис",
    "plan-9": "План 9 из открытого космоса",
    "last-man-earth": "Последний человек на Земле",
    "detour": "Объезд",
    "doa": "Д.О.А.",
    "stranger": "Чужак",
    "scarlet-street": "Улица греха",
    "red-house": "Красный дом",
    "farewell-arms": "Прощай, оружие!",
    "meet-john-doe": "Знакомьтесь, Джон Доу",
    "amazing-mr-x": "Удивительный мистер Икс",
    "quicksand": "Зыбучий песок",
    "lost-weekend": "Потерянный уик-энд",
    "of-human-bondage": "О человеческом рабстве",
    "nothing-sacred": "Ничего святого",
    "front-page": "Первая полоса",
    "charade": "Шарада",
    "39-steps": "39 ступеней",
    "lady-vanishes": "Леди исчезает",
    "potemkin": "Броненосец «Потёмкин»",
    "aelita": "Аэлита",
    "movie-camera": "Человек с киноаппаратом",
    "captain-marvel-1": "Капитан Марвел — серия 1",
    "captain-marvel-2": "Капитан Марвел — серия 2",
    "captain-marvel-3": "Капитан Марвел — серия 3",
}

DURATIONS = {
    "night-living-dead": 96 * 60,
    "little-shop": 72 * 60,
    "plan-9": 79 * 60,
    "detour": 67 * 60,
    "lady-vanishes": 96 * 60,
    "charade": 113 * 60,
}

RATINGS = {
    "nosferatu": "16+",
    "night-living-dead": "18+",
    "caligari": "16+",
    "sherlock-jr": "6+",
    "his-girl-friday": "12+",
    "little-shop": "16+",
    "metropolis": "12+",
    "plan-9": "16+",
    "last-man-earth": "18+",
    "detour": "16+",
    "doa": "16+",
    "stranger": "16+",
    "scarlet-street": "16+",
    "red-house": "16+",
    "farewell-arms": "12+",
    "meet-john-doe": "12+",
    "amazing-mr-x": "16+",
    "quicksand": "16+",
    "lost-weekend": "16+",
    "of-human-bondage": "16+",
    "nothing-sacred": "12+",
    "front-page": "12+",
    "charade": "12+",
    "39-steps": "12+",
    "lady-vanishes": "12+",
    "potemkin": "12+",
    "aelita": "12+",
    "movie-camera": "6+",
    "captain-marvel-1": "6+",
    "captain-marvel-2": "6+",
    "captain-marvel-3": "6+",
}


def main() -> None:
    films = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_id = {film["id"]: film for film in films}
    for film in EXTRA:
        by_id[film["id"]] = film
    for film_id in REMOVE:
        by_id.pop(film_id, None)
    for film in by_id.values():
        film["title"] = TITLES.get(film["id"], film.get("title", film["id"]))
        if film.get("description") is None:
            film["description"] = DESCRIPTIONS.get(
                film["id"], "Публичное достояние. Источник: Archive.org."
            )
        film["rating"] = RATINGS.get(film["id"], film.get("rating", "12+"))
        film["duration"] = DURATIONS.get(film["id"], film.get("duration", 0))
    result = sorted(by_id.values(), key=lambda film: (film.get("year", 0), film["title"]))
    CATALOG.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"catalog: {len(result)} films")


if __name__ == "__main__":
    main()

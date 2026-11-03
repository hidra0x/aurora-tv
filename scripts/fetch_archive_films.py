#!/usr/bin/env python3
"""Find public-domain films on Archive.org and build a catalog.

Usage:
  python scripts/fetch_archive_films.py

The script is intentionally conservative: it searches exact title/year,
prefers the "h.264 IA" MP4 derivative and writes info/cinema_films.json.
Rights can vary by country; the catalog links to the Archive.org item.
"""

from __future__ import annotations

import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INFO = ROOT / "info"
OUT = INFO / "cinema_films.json"

CANDIDATES = [
    ("nosferatu", "Nosferatu", 1922, ["horror", "classic"]),
    ("night-living-dead", "Night of the Living Dead", 1968, ["horror"]),
    ("carnival-souls", "Carnival of Souls", 1962, ["horror"]),
    ("caligari", "The Cabinet of Dr. Caligari", 1920, ["horror", "classic"]),
    ("general", "The General", 1926, ["comedy", "classic"]),
    ("sherlock-jr", "Sherlock Jr.", 1924, ["comedy", "classic"]),
    ("his-girl-friday", "His Girl Friday", 1940, ["comedy"]),
    ("little-shop", "The Little Shop of Horrors", 1960, ["comedy", "horror"]),
    ("mclintock", "McLintock!", 1963, ["comedy", "western"]),
    ("metropolis", "Metropolis", 1927, ["sci-fi", "classic"]),
    ("plan-9", "Plan 9 from Outer Space", 1959, ["sci-fi", "horror"]),
    ("last-man-earth", "The Last Man on Earth", 1964, ["sci-fi", "horror"]),
    ("brain-wouldnt-die", "The Brain That Wouldn't Die", 1962, ["sci-fi", "horror"]),
    ("detour", "Detour", 1945, ["noir", "detective"]),
    ("doa", "D.O.A.", 1949, ["noir", "detective"]),
    ("hitch-hiker", "The Hitch-Hiker", 1953, ["thriller", "detective"]),
    ("stranger", "The Stranger", 1946, ["thriller", "detective"]),
    ("scarlet-street", "Scarlet Street", 1945, ["noir", "detective"]),
    ("red-house", "The Red House", 1947, ["mystery", "detective"]),
    ("the-kid", "The Kid", 1921, ["family", "classic"]),
    ("farewell-arms", "A Farewell to Arms", 1932, ["drama", "classic"]),
    ("meet-john-doe", "Meet John Doe", 1941, ["drama", "classic"]),
    ("snows-kilimanjaro", "The Snows of Kilimanjaro", 1952, ["adventure", "classic"]),
    ("amazing-mr-x", "The Amazing Mr. X", 1948, ["mystery", "detective"]),
    ("quicksand", "Quicksand", 1950, ["noir", "drama"]),
    ("kansas-city", "Kansas City Confidential", 1952, ["noir", "crime"]),
    ("woman-window", "The Woman in the Window", 1944, ["noir", "detective"]),
    ("lost-weekend", "The Lost Weekend", 1945, ["drama", "classic"]),
    ("of-human-bondage", "Of Human Bondage", 1934, ["drama", "classic"]),
    ("nothing-sacred", "Nothing Sacred", 1937, ["comedy"]),
    ("my-man-godfrey", "My Man Godfrey", 1936, ["comedy"]),
    ("front-page", "The Front Page", 1931, ["comedy"]),
    ("charade", "Charade", 1963, ["thriller", "comedy"]),
    ("39-steps", "The 39 Steps", 1935, ["thriller", "detective"]),
    ("lady-vanishes", "The Lady Vanishes", 1938, ["mystery", "detective"]),
]

UA = "AuroraTV/1.5 (public-domain catalog; contact: github.com/nenasevartem70-cmyk)"


def get_json(url: str) -> dict:
    request = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def search(title: str, year: int) -> list[dict]:
    query = f'title:("{title}") AND year:{year} AND mediatype:movies'
    url = (
        "https://archive.org/advancedsearch.php?"
        + urllib.parse.urlencode([
            ("q", query),
            ("fl[]", "identifier"),
            ("fl[]", "title"),
            ("fl[]", "year"),
            ("rows", "5"),
            ("output", "json"),
        ])
    )
    return get_json(url).get("response", {}).get("docs", [])


def metadata(identifier: str) -> dict:
    return get_json(f"https://archive.org/metadata/{urllib.parse.quote(identifier)}")


def pick_file(files: list[dict]) -> dict | None:
    candidates = []
    for file in files:
        name = file.get("name", "")
        if not name.lower().endswith((".mp4", ".m4v")):
            continue
        try:
            size = int(file.get("size", "0"))
        except (TypeError, ValueError):
            size = 0
        if size < 30_000_000:
            continue
        score = 0
        if "h.264" in file.get("format", "").lower() or name.endswith(".ia.mp4"):
            score += 3
        if "512" in name:
            score += 2
        if size < 900_000_000:
            score += 1
        if size > 2_000_000_000:
            score -= 5
        candidates.append((score, size, file))
    if not candidates:
        return None
    candidates.sort(key=lambda value: (-value[0], value[1]))
    return candidates[0][2]


def main() -> None:
    films = []
    for film_id, title, year, genres in CANDIDATES:
        print(f"=== {title} ({year})")
        try:
            docs = search(title, year)
            if not docs:
                print("  no search results")
                continue
            doc = docs[0]
            identifier = doc["identifier"]
            meta = metadata(identifier)
            file = pick_file(meta.get("files", []))
            if not file:
                print(f"  no mp4 in {identifier}")
                continue
            length = meta.get("metadata", {}).get("runtime") or file.get("length") or ""
            match = re.search(r"(\d+)", str(length))
            duration = int(match.group(1)) if match else 0
            url = f"https://archive.org/download/{identifier}/{urllib.parse.quote(file['name'])}"
            films.append({
                "id": film_id,
                "title": meta.get("metadata", {}).get("title", doc.get("title", title)),
                "year": year,
                "genres": genres,
                "duration": duration,
                "url": url,
                "page": f"https://archive.org/details/{identifier}",
                "identifier": identifier,
            })
            print(f"  {identifier} -> {file['name']} ({int(file.get('size', 0)) // 1_000_000} MB)")
        except Exception as error:  # noqa: BLE001
            print(f"  error: {error}")
        time.sleep(0.4)

    OUT.write_text(json.dumps(films, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nwrote {len(films)} films to {OUT}")


if __name__ == "__main__":
    main()

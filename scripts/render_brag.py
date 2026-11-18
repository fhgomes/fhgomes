#!/usr/bin/env python3
"""Render brag.yml into the generated block of README.md.

Usage: python3 scripts/render_brag.py [--check]
  --check  exit 1 if README.md is out of date (no write)
"""
import datetime
import pathlib
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
START = "<!-- BRAG:START (generated from brag.yml, do not edit by hand) -->"
END = "<!-- BRAG:END -->"
FORMAT_LABEL = {"panel": "panel", "podcast": "podcast", "live": "live"}
RECORDING_HOSTS = ("youtube.com/", "youtu.be/")
# `lang` -> text label after the event name.
LANG_LABEL = {"pt": "PT-BR", "en": "EN"}
# `country` -> flag in front of the entry: where the session happened, NOT its
# language. "global" is the globe (online, no single country). Without
# `country`, a session in Portuguese counts as Brazil and anything else as
# global. Images, not emoji: Windows renders flag emoji as two letters ("BR").
# Each code needs assets/flags/<code>.svg.
LANG_COUNTRY = {"pt": "br"}
COUNTRY_NAME = {"br": "Brazil", "in": "India", "us": "United States",
                "pe": "Peru", "ng": "Nigeria",
                "global": "Online, international audience"}


def format_label(e):
    """'live' / 'podcast' / 'panel', or 'recording' for a recorded talk.
    Linked to the recording whenever the main url is one."""
    recorded = any(h in (e.get("url") or "") for h in RECORDING_HOSTS)
    label = FORMAT_LABEL.get(e.get("format")) or ("recording" if recorded else None)
    if not label:
        return ""
    return f" ([{label}]({e['url']}))" if recorded else f" ({label})"


def fmt_date(d):
    if isinstance(d, str):
        d = datetime.date.fromisoformat(d)
    return d.strftime("%b %d, %Y")


def flag(e):
    code = str(e.get("country") or LANG_COUNTRY.get(e.get("lang"), "global")).lower()
    if not code.isalnum() or not (ROOT / "assets" / "flags" / f"{code}.svg").exists():
        print(f"no flag for country {code!r} ({e['event']})", file=sys.stderr)
        return ""
    name = COUNTRY_NAME.get(code, code.upper())
    return (f'<img src="assets/flags/{code}.svg" height="11" alt="{name}" '
            f'title="{name}"> ')


def entry(e):
    title = f"[{e['title']}]({e['url']})" if e.get("url") else e["title"]
    lang = f" · {LANG_LABEL[e['lang']]}" if e.get("lang") in LANG_LABEL else ""
    line = f"* {flag(e)}**{fmt_date(e['date'])} · {e['event']}{lang}:** {title}"
    if e.get("with"):
        line += f" w/ {e['with']}"
    line += format_label(e)
    for x in e.get("extra") or []:
        line += f" · [{x['label']}]({x['url']})"
    if e.get("note"):
        line += f"\n  <br><sub>{' '.join(e['note'].split())}</sub>"
    return line


def section(title, items):
    items = sorted(items or [], key=lambda e: str(e["date"]), reverse=True)
    return [f"### {title}", ""] + [entry(e) for e in items] + [""]


def as_date(d):
    return d if isinstance(d, datetime.date) else datetime.date.fromisoformat(str(d))


def render(data, today=None):
    # Today or later -> "Next Events" (soonest first). The workflow also runs
    # daily, so an event moves to the history the day after it happens.
    today = today or datetime.date.today()
    upcoming, past = [], {}
    for key in ("speaking", "mentoring"):
        past[key] = [e for e in data.get(key) or [] if as_date(e["date"]) < today]
        upcoming += [e for e in data.get(key) or [] if as_date(e["date"]) >= today]
    out = ["## Activity Log", ""]
    if upcoming:
        upcoming.sort(key=lambda e: as_date(e["date"]))
        out += ["### Next Events", ""] + [entry(e) for e in upcoming] + [""]
    out += section("Talks, Lives & Podcasts", past["speaking"])
    out += section("Mentoring", past["mentoring"])
    if data.get("profiles"):
        out += ["### Speaker Profiles", ""]
        out += [f"* [{p['label']}]({p['url']})" for p in data["profiles"]] + [""]
    return "\n".join(out).rstrip() + "\n"


def main():
    data = yaml.safe_load((ROOT / "brag.yml").read_text(encoding="utf-8"))
    readme_path = ROOT / "README.md"
    readme = readme_path.read_text(encoding="utf-8")
    head, rest = readme.split(START, 1)
    _, tail = rest.split(END, 1)
    new = f"{head}{START}\n{render(data)}{END}{tail}"
    if "--check" in sys.argv:
        sys.exit(0 if new == readme else 1)
    if new != readme:
        readme_path.write_text(new, encoding="utf-8")
        print("README.md updated")
    else:
        print("README.md already up to date")


if __name__ == "__main__":
    main()

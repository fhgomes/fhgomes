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
FORMAT_SUFFIX = {"panel": " (panel)", "podcast": " (podcast)", "live": " (live)"}


def fmt_date(d):
    if isinstance(d, str):
        d = datetime.date.fromisoformat(d)
    return d.strftime("%b %d, %Y")


def entry(e):
    title = f"[{e['title']}]({e['url']})" if e.get("url") else e["title"]
    line = f"* **{fmt_date(e['date'])} · {e['event']}:** {title}"
    if e.get("with"):
        line += f" w/ {e['with']}"
    line += FORMAT_SUFFIX.get(e.get("format"), "")
    for x in e.get("extra") or []:
        line += f" · [{x['label']}]({x['url']})"
    if e.get("note"):
        line += f"\n  <br><sub>{' '.join(e['note'].split())}</sub>"
    return line


def section(title, items):
    items = sorted(items or [], key=lambda e: str(e["date"]), reverse=True)
    return [f"### {title}", ""] + [entry(e) for e in items] + [""]


def render(data):
    out = ["## Activity Log", ""]
    out += section("Talks, Lives & Podcasts", data.get("speaking"))
    out += section("Mentoring", data.get("mentoring"))
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

"""Validation helpers for the Planner's small, date-bounded research cache."""
from datetime import date
from pathlib import Path
import re
from urllib.parse import urlparse


def _metadata(path):
    try:
        text = Path(path).read_text(encoding="utf-8")
    except (OSError, UnicodeError):
        return None
    match = re.match(r"\A---\s*\n(.*?)\n---(?:\n|\Z)", text, re.DOTALL)
    if not match:
        return None
    values = {}
    current = None
    for line in match.group(1).splitlines():
        if line.startswith(("- ", "  - ")) and current == "sources":
            values.setdefault("sources", []).append(line.lstrip()[2:].strip())
            continue
        key, separator, value = line.partition(":")
        if not separator:
            continue
        current = key.strip()
        if current == "sources":
            values[current] = []
        else:
            values[current] = value.strip().strip("\"'")
    return values


def is_current(path, *, topic, version, today=None):
    """Return true only for exact-scope, sourced entries no more than 30 days old."""
    values = _metadata(path)
    if not values or values.get("topic") != topic or values.get("version") != version:
        return False
    try:
        accessed = date.fromisoformat(values.get("accessed", ""))
    except (TypeError, ValueError):
        return False
    today = today or date.today()
    age = (today - accessed).days
    sources = values.get("sources", [])
    return (
        0 <= age <= 30
        and bool(sources)
        and all(urlparse(source).scheme == "https" and bool(urlparse(source).netloc)
                for source in sources)
    )

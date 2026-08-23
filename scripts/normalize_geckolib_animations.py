#!/usr/bin/env python3
"""Make bundled donor animations portable across GeckoLib 4.9.2.

Custom NPCs only consumes bone keyframes from these resources. Donor particle,
sound, and timeline events depend on registries and parsers from the original
mods, so they are intentionally removed from the standalone visual bundle.
"""

from __future__ import annotations

import json
from pathlib import Path


PROJECT_DIR = Path(__file__).resolve().parent.parent
ANIMATION_ROOTS = tuple(
    path
    for path in (PROJECT_DIR / "src" / "main" / "resources" / "assets").glob("*/animations")
    if path.is_dir()
)
UNSUPPORTED_EVENT_KEYS = {"particle_effects", "sound_effects", "timeline"}
MALFORMED_EXPRESSIONS = {"-", "0-"}


def normalize(value: object, stats: dict[str, int]) -> object:
    if isinstance(value, dict):
        normalized: dict[str, object] = {}
        for key, child in value.items():
            if key in UNSUPPORTED_EVENT_KEYS:
                stats["events"] += 1
                continue
            normalized[key] = normalize(child, stats)
        return normalized
    if isinstance(value, list):
        return [normalize(child, stats) for child in value]
    if isinstance(value, str) and value.strip() in MALFORMED_EXPRESSIONS:
        stats["expressions"] += 1
        return "0"
    return value


def main() -> None:
    changed_files = 0
    stats = {"events": 0, "expressions": 0}
    for root in ANIMATION_ROOTS:
        for path in root.rglob("*.json"):
            document = json.loads(path.read_text(encoding="utf-8-sig"))
            before = json.dumps(document, ensure_ascii=False, sort_keys=True)
            document = normalize(document, stats)
            after = json.dumps(document, ensure_ascii=False, sort_keys=True)
            if after == before:
                continue
            path.write_text(
                json.dumps(document, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
            changed_files += 1
    print(
        f"Normalized {changed_files} files: removed {stats['events']} donor event "
        f"sections and repaired {stats['expressions']} malformed expressions"
    )


if __name__ == "__main__":
    main()

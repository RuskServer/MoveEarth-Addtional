#!/usr/bin/env python3
"""Check the worldgen JSON we ship before a server has to.

A datapack file is read by nothing until a server loads it, and one that does
not parse does not degrade -- it takes the server down at registry load. One
did: the int providers were written with their bounds nested under a `value`
key, which is the shape a loot number provider takes and not the one a worldgen
int provider takes. Nothing between writing the file and the server refusing to
boot ever looked at it.

The mod's unit tests deliberately run without Minecraft on the classpath, so the
real codecs cannot be called. This reads their definitions instead, out of the
NeoForge sources jar the build already downloads: which class each dispatch id
maps to, and which `fieldOf` names that class and its superclasses declare. The
same reading is done on this mod's own placers, so a field renamed on one side
and not the other is a failed check rather than a crash.

Reading declarations rather than vanilla's own data matters. The first version
compared against the keys vanilla writes, and could not tell an optional field
vanilla always leaves at its default from a field that does not exist -- it
reported `offset` on `would_survive`, which is real and simply never written.

    python3 tools/validate_worldgen.py
"""
from __future__ import annotations

import json
import pathlib
import re
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = ROOT / "src/main/resources/data/moveearth_addtional/worldgen"
ARTIFACTS = ROOT / "build/moddev/artifacts"
OWN_SOURCES = ROOT / "src/main/java/com/ruskserver/moveearth_addtional"

FIELD = re.compile(r'(?:optionalF|f)ieldOf\(\s*"([a-zA-Z0-9_]+)"')
EXTENDS = re.compile(r'\bclass\s+\w+(?:<[^>]*>)?\s+extends\s+([A-Za-z0-9_]+)')
REGISTER = re.compile(r'register\(\s*(?:[A-Za-z0-9_.]+\s*,\s*)?"([a-z0-9_]+)"\s*,\s*'
                      r'(?:new\s+\w+<[^>]*>\(\s*)?([A-Za-z0-9_]+)\.(?:CODEC|MAP_CODEC)')


def load_sources() -> dict[str, str]:
    """Every vanilla source file, by simple class name."""
    jars = sorted(ARTIFACTS.glob("*-sources.jar"))
    if not jars:
        return {}
    out: dict[str, str] = {}
    with zipfile.ZipFile(jars[-1]) as archive:
        for name in archive.namelist():
            if name.endswith(".java"):
                out[pathlib.Path(name).stem] = archive.read(name).decode("utf-8", "replace")
    return out


def fields_of(class_name: str, sources: dict[str, str], seen=None) -> set[str]:
    """Field names the class declares, plus those it inherits."""
    seen = seen if seen is not None else set()
    if class_name in seen or class_name not in sources:
        return set()
    seen.add(class_name)
    body = sources[class_name]
    out = set(FIELD.findall(body))
    for parent in EXTENDS.findall(body):
        out |= fields_of(parent, sources, seen)
    return out


def dispatch_index(sources: dict[str, str]) -> dict[str, set[str]]:
    """Dispatch id to the fields its codec accepts.

    The same short id is registered in more than one registry -- `uniform` is
    both an int and a float provider -- and which registry a given position uses
    is not knowable from the JSON alone, so the fields are unioned. That is
    permissive between two real shapes and still rejects a key that is neither.
    """
    index: dict[str, set[str]] = {}
    for body in sources.values():
        for name, class_name in REGISTER.findall(body):
            index.setdefault(name, set()).update(fields_of(class_name, sources))
    return index


def own_index(vanilla: dict[str, str]) -> dict[str, set[str]]:
    """This mod's own dispatch ids, paired from WorldgenRegistration.

    Resolved against the vanilla sources as well as ours, because the placers
    inherit `radius`, `offset` and the trunk's height fields from the vanilla
    base classes they extend.
    """
    sources = dict(vanilla)
    sources.update({p.stem: p.read_text(encoding="utf-8") for p in OWN_SOURCES.rglob("*.java")})
    registration = sources.get("WorldgenRegistration", "")
    index: dict[str, set[str]] = {}
    for name, holder in re.findall(r'register\(\s*"([a-z0-9_]+)"\s*,\s*\(\)\s*->\s*new\s+\w+<>\(\s*'
                                   r'([A-Za-z0-9_]+)\.CODEC', registration):
        index.setdefault(f"moveearth_addtional:{name}", set()).update(fields_of(holder, sources))
    return index


def walk(node, out):
    """Every object that names a dispatch type, with the keys it carries."""
    if isinstance(node, dict):
        kind = node.get("type")
        if isinstance(kind, str):
            out.setdefault(kind, set()).update(node.keys())
        for value in node.values():
            walk(value, out)
    elif isinstance(node, list):
        for value in node:
            walk(value, out)


def main() -> int:
    if not DATA.is_dir():
        print(f"no worldgen data at {DATA}", file=sys.stderr)
        return 1
    sources = load_sources()
    if not sources:
        print("WARNING: no sources jar in build/moddev/artifacts; "
              "run a gradle build first. Vanilla types are not checked.", file=sys.stderr)
    index = dispatch_index(sources)
    index.update(own_index(sources))

    problems: list[str] = []
    configured: set[str] = set()
    for path in sorted(DATA.rglob("*.json")):
        body = json.loads(path.read_text(encoding="utf-8"))
        if path.parent.name == "configured_feature":
            configured.add("moveearth_addtional:" + path.stem)
        used: dict[str, set[str]] = {}
        walk(body, used)
        for kind, keys in sorted(used.items()):
            known = index.get(kind.removeprefix("minecraft:") if kind.startswith("minecraft:")
                              else kind)
            if known is None:
                if kind.startswith("moveearth_addtional:"):
                    problems.append(f"{path.name}: {kind} is not registered by this mod")
                continue
            unexpected = keys - known - {"type"}
            if unexpected:
                problems.append(f"{path.name}: {kind} carries {sorted(unexpected)}; "
                                f"it accepts {sorted(known)}")

    for path in sorted((DATA / "placed_feature").glob("*.json")):
        feature = json.loads(path.read_text(encoding="utf-8")).get("feature")
        if isinstance(feature, str) and feature.startswith("moveearth_addtional:") \
                and feature not in configured:
            problems.append(f"{path.name}: points at {feature}, which we do not ship")

    for problem in problems:
        print("FAIL", problem)
    count = len(list(DATA.rglob("*.json")))
    print(f"{count} file(s) checked, {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())

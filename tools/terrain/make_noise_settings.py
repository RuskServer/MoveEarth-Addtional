#!/usr/bin/env python3
"""Generate the overworld noise settings that feed our climate layers to biomes.

Everything else about the terrain is steered through density function overrides
(`data/minecraft/worldgen/density_function/overworld/*.json`), which is enough
because vanilla's `initial_density_without_jaggedness` and `final_density` both
*reference* `overworld/depth`, `overworld/factor` and `overworld/sloped_cheese`
rather than inlining them.

The two exceptions are the router's `temperature` and `vegetation` entries.
Those are written inline in the noise settings, so steering biome selection from
the simulated climate means shipping our own copy of the file.

Rather than hand-write it, this takes the vanilla file from the development
environment and swaps exactly those two entries, leaving caves, aquifers and the
spawn target untouched. The result is generated, not committed: run this after
changing the climate mapping or updating Minecraft.

The one other edit is the powder snow gate below, which exists because our
climate is simulated and puts snowy biomes at heights vanilla never does.

Do not add surface rules that choose blocks by height or slope. A set of altitude
bands mottling andesite, granite, tuff and the rest onto high ground was tried
and removed: picking blocks from a rule is not how good terrain gets its
material, and measuring Tectonic's surface rule settled it -- at 31,978 bytes and
24 blocks it is no richer than vanilla's 32,237 and 24. What it looks like comes
from the shape of the ground, so that is where the work belongs.

    python3 tools/terrain/make_noise_settings.py
"""
from __future__ import annotations

import argparse
import json
import pathlib
import sys
import zipfile

VANILLA_PATH = "data/minecraft/worldgen/noise_settings/overworld.json"
OUTPUT_PATH = "src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json"
ARTIFACTS = "build/moddev/artifacts"


def find_resource_jar(root: pathlib.Path) -> pathlib.Path:
    candidates = sorted((root / ARTIFACTS).glob("*client-extra*.jar"))
    if not candidates:
        raise SystemExit(
            f"No Minecraft resource jar under {root / ARTIFACTS}.\n"
            "Run a Gradle build once so the NeoForge dev artifacts are present.")
    return candidates[-1]


def gate_powder_snow(surface_rule: dict, floor_y: int) -> int:
    """Keep powder snow to the high mountains.

    Vanilla places it in `snowy_slopes` and `grove` wherever a noise threshold
    passes, with no height condition -- those biomes only occur high up in
    vanilla's own climate, so none was needed. Our climate comes from the
    simulated map, so the same biomes can land far lower and the powder snow
    comes with them.

    The placement sits in a sequence whose next entry lays a snow block, so
    gating it on height does not leave a hole: below the line the surface simply
    gets snow instead.

    Returns how many placements were gated.
    """
    gate = {
        "type": "minecraft:y_above",
        "anchor": {"absolute": floor_y},
        "surface_depth_multiplier": 0,
        "add_stone_depth": False,
    }

    def is_powder_snow_rule(node) -> bool:
        return (isinstance(node, dict)
                and node.get("type") == "minecraft:condition"
                and isinstance(node.get("if_true"), dict)
                and node["if_true"].get("type") == "minecraft:noise_threshold"
                and node["if_true"].get("noise") == "minecraft:powder_snow")

    count = 0

    def walk(node):
        nonlocal count
        if isinstance(node, dict):
            for key, value in list(node.items()):
                if is_powder_snow_rule(value):
                    node[key] = {"type": "minecraft:condition", "if_true": gate, "then_run": value}
                    count += 1
                else:
                    walk(value)
        elif isinstance(node, list):
            for index, value in enumerate(node):
                if is_powder_snow_rule(value):
                    node[index] = {"type": "minecraft:condition", "if_true": gate, "then_run": value}
                    count += 1
                else:
                    walk(value)

    walk(surface_rule)
    return count


def map_field(field: str) -> dict:
    return {
        "type": "minecraft:flat_cache",
        "argument": {"type": "moveearth_addtional:map_field", "field": field},
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=".", help="repository root")
    parser.add_argument("--check", action="store_true",
                        help="only report what would change")
    parser.add_argument("--powder-snow-floor", type=int, default=150,
                        dest="powder_snow_floor",
                        help="lowest Y at which powder snow may generate (default: 150)")
    args = parser.parse_args(argv)
    root = pathlib.Path(args.root).resolve()

    jar = find_resource_jar(root)
    with zipfile.ZipFile(jar) as archive:
        settings = json.loads(archive.read(VANILLA_PATH))

    router = settings["noise_router"]
    replaced = {
        "temperature": map_field("temperature"),
        "vegetation": map_field("vegetation"),
    }
    for key, value in replaced.items():
        if key not in router:
            raise SystemExit(f"Vanilla noise router has no '{key}' entry; "
                             "the format changed and this script needs revisiting.")
        router[key] = value

    gated = gate_powder_snow(settings["surface_rule"], args.powder_snow_floor)

    out = root / OUTPUT_PATH
    rendered = json.dumps(settings, indent=2, sort_keys=True) + "\n"
    if args.check:
        current = out.read_text(encoding="utf-8") if out.exists() else ""
        print("up to date" if current == rendered else "STALE: re-run without --check")
        return 0 if current == rendered else 1

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(rendered, encoding="utf-8")
    print(f"source : {jar.name}")
    print(f"written: {out.relative_to(root)}  ({len(rendered)} bytes)")
    print(f"swapped: {', '.join(sorted(replaced))}")
    print(f"powder snow: {gated} placement(s) gated to Y >= {args.powder_snow_floor}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

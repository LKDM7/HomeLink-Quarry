"""Check texture bounds and coplanar surfaces, including assembled and rotating models.

Opposite-facing contact faces are intentionally ignored: back-face culling hides them.
All six faces include element rotations. Head assemblies are sampled through a full turn.
Run with Python 3, no dependencies.
"""
import json
import math
from collections import defaultdict
from copy import deepcopy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/homelink_quarry/models"
FACES = {"west": (0, "from"), "east": (0, "to"), "down": (1, "from"),
         "up": (1, "to"), "north": (2, "from"), "south": (2, "to")}


def rotate(point, element, vector=False):
    rotation = element.get("rotation", {})
    if rotation.get("axis", "y") != "y" or rotation.get("rescale", False):
        raise ValueError("Extend the checker for non-Y or rescaled element rotations")
    angle = math.radians(rotation.get("angle", 0))
    ox, _, oz = [0, 0, 0] if vector else rotation.get("origin", [0, 0, 0])
    x, y, z = point
    return (ox + (x - ox) * math.cos(angle) + (z - oz) * math.sin(angle), y,
            oz - (x - ox) * math.sin(angle) + (z - oz) * math.cos(angle))


def polygons_overlap(a, b):
    # Separating-axis test: touching edges have no overlapping surface area.
    for polygon in (a, b):
        for i, p in enumerate(polygon):
            q = polygon[(i + 1) % len(polygon)]
            nx, nz = p[1] - q[1], q[0] - p[0]
            pa = [x * nx + z * nz for x, z in a]
            pb = [x * nx + z * nz for x, z in b]
            if min(max(pa), max(pb)) - max(min(pa), min(pb)) <= 1e-7:
                return False
    return True


def conflicts(elements):
    planes = defaultdict(list)
    for element in elements:
        for side, (axis, end) in FACES.items():
            if side not in element["faces"]:
                continue
            other = [k for k in range(3) if k != axis]
            points = []
            for s, t in (("from", "from"), ("to", "from"), ("to", "to"), ("from", "to")):
                p = list(element[end])
                p[other[0]], p[other[1]] = element[s][other[0]], element[t][other[1]]
                points.append(rotate(p, element))
            normal = [0, 0, 0]
            normal[axis] = -1 if end == "from" else 1
            normal = rotate(normal, element, vector=True)
            distance = sum(n * p for n, p in zip(normal, points[0]))
            key = tuple(round(v, 7) for v in (*normal, distance))
            drop_axis = max(range(3), key=lambda k: abs(normal[k]))
            polygon = [tuple(p[k] for k in range(3) if k != drop_axis) for p in points]
            for name, previous_side, previous in planes[key]:
                if polygons_overlap(polygon, previous):
                    yield name, element["name"], f"{previous_side}/{side}"
            planes[key].append((element["name"], side, polygon))


def invalid_uvs(elements):
    for element in elements:
        x, y, z = element["from"]
        xx, yy, zz = element["to"]
        implicit = {"down": [x, 16-zz, xx, 16-z], "up": [x, z, xx, zz],
                    "north": [16-xx, 16-yy, 16-x, 16-y], "south": [x, 16-yy, xx, 16-y],
                    "west": [z, 16-yy, zz, 16-y], "east": [16-zz, 16-yy, 16-z, 16-y]}
        for side, face in element["faces"].items():
            uv = face.get("uv", implicit[side])
            if len(uv) != 4 or any(not math.isfinite(v) or v < 0 or v > 16 for v in uv):
                yield element["name"], side, uv


def main():
    count = 0
    models = {}
    for path in sorted(ROOT.rglob("*.json")):
        model = json.loads(path.read_text(encoding="utf-8"))
        models[path.relative_to(ROOT).as_posix()[:-5]] = model.get("elements", [])
        for name, side, uv in invalid_uvs(model.get("elements", [])):
            print(f"{path.relative_to(ROOT)}: UV outside sprite: {name} ({side}) {uv}")
            count += 1
    scenes = dict(models)
    for tier in ("i", "ii", "iii"):
        for angle in range(0, 360, 15):
            rotor = deepcopy(models[f"block/head_rotor_{tier}"])
            for element in rotor:
                rotation = element.setdefault("rotation", {"origin": [8, 8, 8], "axis": "y", "angle": 0})
                rotation["angle"] += angle
            scenes[f"assembled/head_{tier}@{angle}"] = models[f"block/head_housing_{tier}"] + rotor
        scenes[f"assembled/controller_{tier}_connected"] = (models[f"block/quarry_{tier}"]
                + models["block/quarry_port_lit"] + models["block/quarry_port_connector"])
    for name, elements in scenes.items():
        for a, b, side in conflicts(elements):
            print(f"{name}: {a} / {b} ({side})")
            count += 1
    print(f"Checked {len(models)} models and {len(scenes) - len(models)} assemblies; surface/UV errors: {count}")
    raise SystemExit(1 if count else 0)


if __name__ == "__main__":
    main()

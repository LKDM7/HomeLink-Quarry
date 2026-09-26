"""Build the quarry's vanilla cuboid models and matching animated head parts.

Run from any directory with Python 3. No third-party dependencies are needed.
Industrial copper / steel: tiers are distinguished by silhouette and grade marks.
The inventory head and its two reusable parts share the exact same geometry.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/homelink_quarry/models"
SIDES = ("down", "up", "north", "south", "west", "east")
METAL = {
    "particle": "homelink_quarry:block/quarry_steel",
    "shell": "homelink_quarry:block/quarry_steel",
    "frame": "homelink_quarry:block/quarry_steel_dark",
    "copper": "homelink_quarry:block/quarry_copper",
    "screen": "minecraft:block/black_concrete",
    "shaft": "minecraft:block/iron_block",
    "tier": "homelink_quarry:block/quarry_copper",
    "bit": "minecraft:block/iron_block",
    "dial": "minecraft:block/cyan_concrete",
}
DISPLAY = {
    "gui": {"rotation": [25, 225, 0], "translation": [0, 0, 0], "scale": [0.72] * 3},
    "ground": {"translation": [0, 3, 0], "scale": [0.45] * 3},
    "fixed": {"rotation": [0, 180, 0], "scale": [0.7] * 3},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4] * 3},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4] * 3},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1, 0], "scale": [0.55] * 3},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 1, 0], "scale": [0.55] * 3},
}


def box(name, start, end, material, sides=SIDES, rotation=None, glow=False):
    faces = {side: {"texture": "#" + material} for side in sides}
    # Vanilla derives UVs from the element coordinates. Parts extending outside the block
    # would sample neighbouring atlas sprites; keep those faces inside their own texture.
    x, y, z = start
    xx, yy, zz = end
    default_uv = {
        "down": [x, 16 - zz, xx, 16 - z], "up": [x, z, xx, zz],
        "north": [16 - xx, 16 - yy, 16 - x, 16 - y], "south": [x, 16 - yy, xx, 16 - y],
        "west": [z, 16 - yy, zz, 16 - y], "east": [16 - zz, 16 - yy, 16 - z, 16 - y],
    }
    for side, face in faces.items():
        uv = default_uv[side]
        if min(uv) < 0 or max(uv) > 16:
            for axis in (0, 1):
                low, high = uv[axis], uv[axis + 2]
                if high - low >= 16:
                    uv[axis], uv[axis + 2] = 0, 16
                else:
                    shift = max(0, -low) - max(0, high - 16)
                    uv[axis], uv[axis + 2] = low + shift, high + shift
            face["uv"] = uv
    if glow:
        for face in faces.values():
            face["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    result = {"name": name, "from": start, "to": end, "faces": faces}
    if rotation is not None:
        result["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": rotation}
    if glow:
        result["shade"] = False
    return result


def write(path, elements=None, textures=None, parent="minecraft:block/block", display=None):
    data = {"parent": parent}
    if textures:
        data["textures"] = textures
    if display:
        data["display"] = display
    # One element per line keeps the geometry readable, including its face assignments.
    text = json.dumps(data, indent=2)
    if elements is not None:
        text = text[:-2] + ',\n  "elements": [\n    ' + ",\n    ".join(json.dumps(e) for e in elements) + "\n  ]\n}"
    destination = ROOT / (path + ".json")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(text + "\n", encoding="utf-8")


def controller():
    parts = [
        box("foundation", [0, 0, 0], [16, 2, 16], "frame"),
        box("lower_trim", [0.5, 2, 0.5], [15.5, 3, 15.5], "copper"),
        box("enclosure", [2, 3, 1], [14, 13, 15], "shell"),
        box("lid", [1, 13, 1], [15, 14, 15], "frame"),
        box("service_hatch", [4, 14, 4], [12, 14.75, 12], "shell"),
        box("hatch_latch", [7, 14.75, 6], [9, 15.5, 10], "copper"),
        box("display_bezel", [3, 6, 0.25], [13, 12, 1], "frame"),
        box("display_glass", [3.75, 6.75, 0.18], [12.25, 11.25, 0.25], "screen"),
        box("display_scale", [6, 7.5, 0.15], [11.5, 7.75, 0.18], "copper", ["north"]),
        box("display_readout", [6, 8.25, 0.15], [9.5, 8.75, 0.18], "dial", ["north"]),
        box("control_left", [4, 4, 0.25], [5.5, 5.25, 1], "copper"),
        box("control_right", [6.25, 4, 0.25], [7.75, 5.25, 1], "shaft"),
        box("output_socket", [4.5, 5, 15], [11.5, 11, 16], "copper"),
        box("output_recess", [6, 6.5, 16], [10, 9.5, 16.02], "screen", ["south"]),
    ]
    for x in (0, 14):
        for z in (0, 14):
            parts.append(box("corner_post", [x, 3, z], [x + 2, 13, z + 2], "frame"))
            # Collars stand proud of their posts instead of sharing the same outer faces.
            parts.append(box("corner_clamp", [x - 0.125, 11, z - 0.125],
                             [x + 2.125, 12, z + 2.125], "copper"))
            if z == 0:
                parts.append(box("front_fastener", [x + 0.5, 12, -0.02],
                                 [x + 1.5, 12.75, 0], "shaft", ["north"]))
    for x in (1.75, 14):
        parts.append(box("vent_recess", [x, 5, 4], [x + 0.25, 11, 12], "frame"))
        for y in (6, 8, 10):
            parts.append(box("vent_louvre", [x - 0.15, y, 4.5], [x + 0.4, y + 0.5, 11.5], "shell"))
    return parts


def controller_details(level):
    parts = [box("grade_plate", [8.5, 3.5, 0.2], [13, 5.5, 1], "frame")]
    # Actual I / II / III bars, readable independently of material or lighting.
    for i in range(level):
        x = 9 + i * 1.25
        parts.append(box("grade_bar_" + str(i), [x, 3.75, 0.1], [x + 0.75, 5.25, 0.2], "copper"))
    if level == 1:
        for x in (2.5, 12.5):
            parts.append(box("service_handle", [x, 14, 6], [x + 1, 15, 10], "copper"))
        return parts

    # Twin exposed copper cooling banks on II; enclosed armoured banks on III.
    for x in (0.25, 14.5):
        for z in (4, 10):
            parts.append(box("coolant_manifold", [x, 4, z], [x + 1.25, 12.5, z + 2], "copper"))
            for y in (4, 11.5):
                parts.append(box("manifold_collar", [x - 0.125, y - 0.0625, z - 0.25],
                                 [x + 1.375, y + 1.0625, z + 2.25], "frame"))
    for x in (2, 13):
        parts.append(box("front_reinforcement", [x, 3, 0], [x + 1, 13, 1], "copper"))
    for x in (2.5, 12):
        for z in (3, 6, 9, 12):
            parts.append(box("roof_cooling_fin", [x, 14, z], [x + 1.5, 16, z + 1], "shell"))
    if level == 3:
        for x in (0, 14):
            parts.append(box("armour_shoulder", [x, 12, 2], [x + 2, 16, 14], "frame"))
            parts.append(box("armour_belt", [x - 0.125, 7, 3], [x + 2.125, 10, 13], "shell"))
            parts.append(box("shoulder_inlay", [x + 0.5, 16, 3],
                             [x + 1.5, 16.02, 13], "copper", ["up"]))
        parts.append(box("roof_heat_exchanger", [5, 14.75, 10.5], [11, 16, 14], "copper"))
        for x in (5.5, 7.5, 9.5):
            parts.append(box("exchanger_fin", [x, 16, 10.5], [x + 1, 16.02, 14], "frame", ["up"]))
        for x in (0, 13):
            parts.append(box("armoured_foot", [x - 0.125, -0.0625, -0.125],
                             [x + 3.125, 4, 3.125], "shell"))
    return parts


def head(level):
    width = 5 - level
    fixed = [
        box("upper_bearing", [5, 14, 5], [11, 16, 11], "frame"),
        box("motor_body", [width, 9, width], [16 - width, 14, 16 - width], "copper"),
        box("lower_bearing", [3.875, 8, 3.875], [12.125, 9.5, 12.125], "frame"),
        box("motor_band", [width - 0.25, 10, width - 0.25], [16.25 - width, 11, 16.25 - width], "shell"),
    ]
    for x in (width - 0.5, 15.75 - width):
        fixed.append(box("cooling_fin", [x, 9.75, width + 1], [x + 0.75, 13.5, 15 - width], "frame"))
    for i in range(level):
        x = 8 - (level * 1.5 - 0.75) / 2 + i * 1.5
        fixed.append(box("head_grade", [x, 12.5, width - 0.1], [x + 0.75, 13.5, width], "shaft", ["north"]))
    if level >= 2:
        for z in (width - 0.5, 15.5 - width):
            fixed.append(box("motor_heat_sink", [width + 1, 14, z],
                             [15 - width, 15.5, z + 1], "frame"))
        for x in (width - 1, 16 - width):
            fixed.append(box("hydraulic_ram", [x, 8, 6.5], [x + 1, 14, 9.5], "shaft"))
            fixed.append(box("ram_cuff", [x - 0.3125, 8.9375, 5.9375],
                             [x + 1.3125, 11.0625, 10.0625], "copper"))
    if level == 3:
        fixed.append(box("armoured_crown", [1.75, 13.5, 1.875], [14.25, 14.5, 14.125], "frame"))
        for x in (2, 12):
            fixed.append(box("motor_armour", [x - 0.125, 8.875, 2.875],
                             [x + 2.125, 13.625, 13.125], "shell"))
    # The spindle's lower cap is inside the rotated cutting flight at Y=2.
    rotor = [box("spindle", [6.5, 2, 6.5], [9.5, 8.5, 9.5], "shaft",
                 [side for side in SIDES if side != "down"])]
    for i, (y, radius, angle) in enumerate(((6, 3.5, 0), (4, 3, 22.5), (2, 2.25, 45), (0, 1.25, -22.5))):
        radius += (level - 1) * 0.2
        rotor.append(box("cutting_flight_" + str(i), [8 - radius, y, 8 - radius],
                         [8 + radius, y + 1.5, 8 + radius], "bit", rotation=angle))
    if level >= 2:
        rotor.append(box("drive_ring", [3.75, 6.75, 3.75], [12.25, 8, 12.25], "copper"))
    if level == 3:
        for x, z in ((3.5, 6.75), (10.5, 6.75), (6.75, 3.5), (6.75, 10.5)):
            rotor.append(box("carbide_tooth", [x, 4.75, z], [x + 2, 7, z + 2], "bit"))
    return fixed, rotor


def rail():
    # Native-size I-beam sections keep steel grain and copper joints at block pixel scale.
    return [
        box("lower_flange", [0, 0, 4], [16, 1, 12], "frame"),
        box("steel_web", [0, 1, 7], [16, 4, 9], "shell"),
        box("running_surface", [0, 4, 5], [16, 5, 11], "shaft"),
        # Inset the joint from the segment end; its horizontal caps are hidden by the flanges.
        box("joint_plate", [0.125, 1, 6.5], [2.125, 4, 9.5], "copper",
            ["north", "south", "west", "east"]),
    ]


def main():
    base = controller()
    write("block/quarry_controller", base, METAL, display=DISPLAY)
    for level, suffix, bit in ((1, "i", "minecraft:block/iron_block"),
                               (2, "ii", METAL["shell"]),
                               (3, "iii", "minecraft:block/netherite_block")):
        textures = {"tier": METAL["copper"], "bit": bit}
        write("block/quarry_" + suffix, base + controller_details(level), textures, "homelink_quarry:block/quarry_controller")
        fixed, rotor = head(level)
        write("item/mining_head_" + suffix, fixed + rotor, textures, "homelink_quarry:item/mining_head")
        write("block/head_housing_" + suffix, fixed, METAL | textures)
        write("block/head_rotor_" + suffix, rotor, METAL | textures)
    fixed, rotor = head(1)
    write("item/mining_head", fixed + rotor, METAL, display=DISPLAY)
    for name in ("frame", "shell", "copper", "shaft"):
        write("block/gantry_" + name, textures={"all": METAL[name]}, parent="minecraft:block/cube_all")
    write("block/gantry_rail", rail(), METAL)
    # Static connector geometry stays aligned with the south-facing socket.
    connector = [
        box("sleeve", [6, 6.5, 16], [10, 9.5, 17], "copper"),
        box("collar", [5.5, 6, 16.4], [10.5, 10, 16.9], "frame"),
        box("conduit", [6.75, 15, 13.5], [9.25, 15.75, 18], "copper"),
        box("conduit_clip", [6.25, 14.9375, 15.5], [9.75, 16, 16.5], "frame"),
    ]
    write("block/quarry_port_connector", connector, METAL)
    lamps = [box("port_upper", [5.5, 10, 16.02], [10.5, 10.5, 16.08], "lamp", ["south"], glow=True),
             box("port_lower", [5.5, 5.5, 16.02], [10.5, 6, 16.08], "lamp", ["south"], glow=True),
             box("port_top", [5.5, 15.01, 14], [10.5, 15.2, 15], "lamp", glow=True)]
    write("block/quarry_port_lit", lamps, {"lamp": "minecraft:block/orange_concrete"})
    # Status is rendered from the already-synchronised entity status, without new block states.
    for name, color in (("idle", "gray"), ("running", "lime"), ("waiting", "orange"), ("error", "red"), ("finished", "light_blue")):
        panel = [box("status_line", [4.5, 9.5, 0.1], [11.5, 10, 0.17], "lamp", ["north"], glow=True),
                 box("status_dot", [4.5, 7.5, 0.1], [5.25, 8.25, 0.17], "lamp", ["north"], glow=True)]
        write("block/status_" + name, panel, {"lamp": "minecraft:block/" + color + "_concrete"})
    marker = [box("grip", [7, 1, 7], [9, 9, 9], "frame"),
              box("end_cap", [6.5, 0, 6.5], [9.5, 1.5, 9.5], "copper"),
              box("sensor_collar", [6, 9, 6], [10, 11, 10], "copper"),
              box("sensor", [6.5, 11, 6.5], [9.5, 14, 9.5], "shaft"),
              box("sensor_tip", [7, 14, 7], [9, 16, 9], "tier")]
    for y in (3, 5, 7):
        marker.append(box("grip_ring", [6.75, y, 6.75], [9.25, y + 0.5, 9.25], "copper"))
    write("item/quarry_marker", marker, METAL | {"tier": "minecraft:block/redstone_block"}, display=DISPLAY)


if __name__ == "__main__":
    main()

"""
Procedural 3D mannequin exercise demos (run headless inside Blender):

  blender -b -P mannequin.py -- <exercise_id> <out_dir> [--still]

Side view (character faces +Y). Every joint rotates about X, so a pose is a handful of
angles. Conventions (degrees): a limb hangs down at rest; a positive rotation swings it
forward (+Y). The root pitch is applied as -lean.
"""
import math
import os
import sys

import bpy

D2R = math.pi / 180.0

# ---------------------------------------------------------------- body dimensions (m)
TORSO = 0.50
UPPER_ARM, FOREARM = 0.30, 0.27
THIGH, SHIN = 0.43, 0.42
FOOT_H = 0.07


def v(angle_deg, length):
    """Direction of a down-pointing limb with absolute rotation angle."""
    a = angle_deg * D2R
    return (math.sin(a) * length, -math.cos(a) * length)


def ik2(l1, l2, target, bend):
    """2-link IK in the YZ plane for down-pointing limbs. Returns absolute angles (a1, a2)."""
    dy, dz = target
    dist = max(1e-6, min(math.hypot(dy, dz), l1 + l2 - 1e-4))
    base = math.degrees(math.atan2(dy, -dz))
    cg = (l1 * l1 + dist * dist - l2 * l2) / (2 * l1 * dist)
    gamma = math.degrees(math.acos(max(-1, min(1, cg))))
    a1 = base + bend * gamma
    e = v(a1, l1)
    a2 = math.degrees(math.atan2(dy - e[0], -(dz - e[1])))
    return a1, a2


def solve_pose(p):
    """Turn a high level pose dict into joint rotations + root location."""
    lean = p.get("lean", 0.0)          # whole body forward tilt
    spine = p.get("spine", 0.0)        # extra torso flexion
    hip = p.get("hip", 0.0)            # thigh relative to pelvis, + = forward
    knee = p.get("knee", 0.0)          # + = bent
    foot_abs = p.get("foot_abs", 0.0)  # absolute foot rotation
    t1 = -lean + hip
    t2 = t1 - knee
    ankle_rel = foot_abs - t2
    # root position so that the ankle sits on the target
    ay, az = p.get("ankle", (0.0, FOOT_H))
    th = v(t1, THIGH)
    sh = v(t2, SHIN)
    root = (ay - th[0] - sh[0], az - th[1] - sh[1])

    L = lean + spine
    s_pos = (root[0] + TORSO * 0.9 * math.sin(L * D2R), root[1] + TORSO * 0.9 * math.cos(L * D2R))
    if "hands" in p:
        hy, hz = p["hands"]
        a1, a2 = ik2(UPPER_ARM, FOREARM, (hy - s_pos[0], hz - s_pos[1]), p.get("elbow_bend", -1))
    else:
        a1 = p.get("arm", 0.0)                     # absolute upper-arm angle
        a2 = a1 + p.get("elbow", 0.0)              # + = flexed forward
    return {
        "root": root,
        "root_rx": -lean,
        "spine_rx": -spine,
        "neck_rx": p.get("neck", 0.0),
        "shoulder_rx": a1 + L,
        "elbow_rx": a2 - a1,
        "hip_rx": hip,
        "knee_rx": -knee,
        "ankle_rx": ankle_rel,
        "effort": p.get("effort", 1.0),
    }


# ---------------------------------------------------------------- scene helpers
def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    return bpy.context.scene


def material(name, color, rough=0.45, metallic=0.0, emission=None, strength=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metallic
    if emission:
        b.inputs["Emission Color"].default_value = (*emission, 1)
        b.inputs["Emission Strength"].default_value = strength
    return m


def joint(name, parent, loc=(0, 0, 0)):
    o = bpy.data.objects.new(name, None)
    bpy.context.scene.collection.objects.link(o)
    o.parent = parent
    o.location = loc
    o.rotation_mode = "XYZ"
    return o


def blob(name, parent, loc, scale, mat, kind="sphere", rot=(0, 0, 0)):
    if kind == "sphere":
        bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16, radius=1)
    else:
        bpy.ops.mesh.primitive_cylinder_add(vertices=32, radius=1, depth=1)
    o = bpy.context.active_object
    o.name = name
    o.parent = parent
    o.location = loc
    o.scale = scale
    o.rotation_euler = tuple(r * D2R for r in rot)
    o.data.materials.append(mat)
    bpy.ops.object.shade_smooth()
    return o


def limb(name, parent, length, radius, mat, taper=0.82):
    """Capsule hanging down from the joint."""
    blob(name + "_seg", parent, (0, 0, -length / 2), (radius, radius, length / 2), mat)
    blob(name + "_end", parent, (0, 0, -length), (radius * taper, radius * taper, radius * taper), mat)
    blob(name + "_top", parent, (0, 0, 0), (radius * 1.08,) * 3, mat)


MUSCLES = {
    # name: (joint, offset, scale)  [offsets in the joint frame, +Y is the front]
    "chest": [("spine", (0.085, 0.075, 0.37), (0.085, 0.06, 0.075)), ("spine", (-0.085, 0.075, 0.37), (0.085, 0.06, 0.075))],
    "abs": [("spine", (0, 0.09, 0.2), (0.075, 0.04, 0.12))],
    "back": [("spine", (0, -0.085, 0.3), (0.15, 0.04, 0.17))],
    "shoulders": [("shoulderR", (0, 0, -0.01), (0.062, 0.062, 0.062)), ("shoulderL", (0, 0, -0.01), (0.062, 0.062, 0.062))],
    "biceps": [("shoulderR", (0, 0.04, -0.14), (0.04, 0.03, 0.1)), ("shoulderL", (0, 0.04, -0.14), (0.04, 0.03, 0.1))],
    "triceps": [("shoulderR", (0, -0.04, -0.14), (0.04, 0.03, 0.1)), ("shoulderL", (0, -0.04, -0.14), (0.04, 0.03, 0.1))],
    "glutes": [("root", (0.07, -0.08, 0.02), (0.09, 0.07, 0.09)), ("root", (-0.07, -0.08, 0.02), (0.09, 0.07, 0.09))],
    "quads": [("hipR", (0, 0.06, -0.22), (0.058, 0.04, 0.17)), ("hipL", (0, 0.06, -0.22), (0.058, 0.04, 0.17))],
    "hamstrings": [("hipR", (0, -0.06, -0.22), (0.058, 0.04, 0.17)), ("hipL", (0, -0.06, -0.22), (0.058, 0.04, 0.17))],
    "calves": [("kneeR", (0, -0.045, -0.15), (0.045, 0.035, 0.11)), ("kneeL", (0, -0.045, -0.15), (0.045, 0.035, 0.11))],
}


def box(name, loc, size, mat, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
    o = bpy.context.active_object
    o.name = name
    o.scale = size
    o.rotation_euler = tuple(r * D2R for r in rot)
    o.data.materials.append(mat)
    return o


def build_mannequin(muscles):
    skin = material("skin", (0.50, 0.31, 0.20), rough=0.5)
    shirt = material("shirt", (0.02, 0.45, 0.55), rough=0.7)
    shorts = material("shorts", (0.05, 0.06, 0.09), rough=0.75)
    shoe = material("shoe", (0.9, 0.9, 0.92), rough=0.5)
    sole = material("sole", (0.8, 0.12, 0.05), rough=0.6)
    hair = material("hair", (0.015, 0.012, 0.012), rough=0.55)
    eye_w = material("eye_w", (0.95, 0.95, 0.95), rough=0.2)
    eye_b = material("eye_b", (0.02, 0.015, 0.012), rough=0.2)
    lip = material("lip", (0.32, 0.12, 0.10), rough=0.5)
    glow = material("glow", (0.9, 0.18, 0.02), rough=0.3, emission=(1.0, 0.1, 0.0), strength=1.0)

    J = {}
    J["root"] = joint("root", None)
    J["spine"] = joint("spine", J["root"], (0, 0, 0.02))
    J["neck"] = joint("neck", J["spine"], (0, 0, TORSO))
    for side, sx in (("R", 0.2), ("L", -0.2)):
        J["shoulder" + side] = joint("shoulder" + side, J["spine"], (sx, 0, TORSO * 0.9))
        J["elbow" + side] = joint("elbow" + side, J["shoulder" + side], (0, 0, -UPPER_ARM))
        J["hip" + side] = joint("hip" + side, J["root"], (sx * 0.5, 0, 0))
        J["knee" + side] = joint("knee" + side, J["hip" + side], (0, 0, -THIGH))
        J["ankle" + side] = joint("ankle" + side, J["knee" + side], (0, 0, -SHIN))
        limb("uarm" + side, J["shoulder" + side], UPPER_ARM, 0.05, skin)
        blob("delt" + side, J["shoulder" + side], (0, 0, -0.03), (0.068, 0.062, 0.075), skin)
        blob("sleeve" + side, J["shoulder" + side], (0, 0, -0.045), (0.072, 0.066, 0.065), shirt)
        limb("farm" + side, J["elbow" + side], FOREARM, 0.042, skin)
        blob("hand" + side, J["elbow" + side], (0, 0, -FOREARM - 0.035), (0.042, 0.055, 0.055), skin)
        limb("thigh" + side, J["hip" + side], THIGH, 0.075, skin)
        blob("shortsleg" + side, J["hip" + side], (0, 0, -0.14), (0.088, 0.088, 0.17), shorts)
        limb("shin" + side, J["knee" + side], SHIN, 0.056, skin)
        blob("calfbulge" + side, J["knee" + side], (0, -0.02, -0.14), (0.058, 0.062, 0.11), skin)
        blob("shoe" + side, J["ankle" + side], (0, 0.07, -0.04), (0.055, 0.13, 0.05), shoe)
        blob("sole" + side, J["ankle" + side], (0, 0.07, -0.085), (0.057, 0.135, 0.016), sole)
    # torso (tank top), pelvis (shorts)
    blob("pelvis", J["root"], (0, 0, 0.03), (0.18, 0.12, 0.12), shorts)
    blob("torso", J["spine"], (0, 0, 0.27), (0.17, 0.105, 0.25), shirt)
    blob("chest_plate", J["spine"], (0, 0.005, 0.39), (0.205, 0.118, 0.14), shirt)
    blob("traps", J["spine"], (0, -0.01, 0.47), (0.12, 0.07, 0.05), skin)
    blob("neck_seg", J["neck"], (0, 0, 0.05), (0.052, 0.055, 0.075), skin)
    # head + face (faces +Y)
    n = J["neck"]
    blob("head", n, (0, 0.02, 0.17), (0.095, 0.115, 0.12), skin)
    blob("hair", n, (0, -0.012, 0.185), (0.101, 0.119, 0.126), hair)
    blob("fringe", n, (0, 0.03, 0.255), (0.09, 0.1, 0.045), hair)
    for sx in (-1, 1):
        blob("eye_w", n, (sx * 0.038, 0.118, 0.182), (0.017, 0.012, 0.012), eye_w)
        blob("eye_b", n, (sx * 0.038, 0.128, 0.182), (0.008, 0.006, 0.008), eye_b)
        blob("brow", n, (sx * 0.04, 0.12, 0.209), (0.024, 0.007, 0.006), hair, rot=(0, sx * -8, 0))
        blob("ear", n, (sx * 0.094, 0.02, 0.165), (0.012, 0.02, 0.03), skin)
    blob("nose", n, (0, 0.14, 0.155), (0.014, 0.018, 0.022), skin)
    blob("mouth", n, (0, 0.128, 0.118), (0.03, 0.008, 0.007), lip)

    glow_mats = []
    for name in muscles:
        for jn, loc, sc in MUSCLES[name]:
            m = glow.copy()
            m.name = f"glow_{name}"
            blob(f"m_{name}", J[jn], loc, sc, m)
            glow_mats.append(m)
    return J, glow_mats


def dumbbell(name, loc, mat, size=1.0):
    h = bpy.ops.mesh.primitive_cylinder_add
    h(vertices=16, radius=0.012 * size, depth=0.2 * size, location=loc, rotation=(math.pi / 2, 0, 0))
    bpy.context.active_object.data.materials.append(mat["steel"])
    for dy in (-0.09 * size, 0.09 * size):
        h(vertices=6, radius=0.055 * size, depth=0.045 * size, location=(loc[0], loc[1] + dy, loc[2]), rotation=(math.pi / 2, 0, 0))
        bpy.context.active_object.data.materials.append(mat["iron"])


def build_gym():
    wall = material("wall", (0.08, 0.09, 0.115), rough=0.85)
    panel = material("panel", (0.12, 0.13, 0.17), rough=0.6)
    rubber = material("rubber", (0.025, 0.027, 0.032), rough=0.9)
    wood = material("wood", (0.16, 0.09, 0.045), rough=0.7)
    steel = material("steel", (0.55, 0.57, 0.6), rough=0.25, metallic=1.0)
    iron = material("iron", (0.04, 0.04, 0.045), rough=0.4, metallic=0.8)
    red = material("redtape", (0.7, 0.05, 0.04), rough=0.6)
    neon = material("neon", (0.0, 0.8, 1.0), emission=(0.0, 0.8, 1.0), strength=6.0)
    warm = material("warm", (1, 0.85, 0.6), emission=(1.0, 0.8, 0.5), strength=9.0)
    window = material("window", (0.3, 0.45, 0.8), emission=(0.35, 0.5, 0.9), strength=0.9)
    pads = {"steel": steel, "iron": iron}

    # floor (rubber) + lifting platform
    bpy.ops.mesh.primitive_plane_add(size=16, location=(0, 0, 0))
    bpy.context.active_object.data.materials.append(rubber)
    box("platform", (0, 0, 0.012), (3.2, 3.4, 0.024), wood)
    box("tape1", (1.6, 0, 0.026), (0.03, 3.4, 0.004), red)
    box("tape2", (-1.6, 0, 0.026), (0.03, 3.4, 0.004), red)

    # back wall with panels, windows, neon strip
    box("backwall", (-3.6, 0, 2.5), (0.1, 14, 5), wall)
    for i, y in enumerate((-4.5, -1.5, 1.5, 4.5)):
        box(f"panel{i}", (-3.53, y, 1.5), (0.04, 2.6, 2.2), panel)
    for i, y in enumerate((-3.0, 0.0, 3.0)):
        box(f"win{i}", (-3.53, y, 3.2), (0.03, 1.6, 1.0), window)
    box("neon", (-3.5, 0, 2.55), (0.05, 14, 0.05), neon)
    box("ceil_light1", (-1.0, -1.5, 3.9), (0.12, 3.0, 0.05), warm)
    box("ceil_light2", (-1.0, 2.0, 3.9), (0.12, 3.0, 0.05), warm)

    # squat rack behind the athlete
    for y in (-1.0, 1.0):
        box("upright", (-2.9, y, 1.1), (0.07, 0.07, 2.2), iron)
        box("base", (-2.6, y, 0.04), (0.9, 0.07, 0.08), iron)
        box("hook", (-2.82, y, 1.35), (0.14, 0.05, 0.03), steel)
    box("crossbar", (-2.9, 0, 2.15), (0.07, 2.07, 0.07), iron)
    bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=0.014, depth=2.4, location=(-2.8, 0, 1.38), rotation=(math.pi / 2, 0, 0))
    bpy.context.active_object.data.materials.append(steel)
    for y in (-0.9, 0.9, -1.0, 1.0):
        bpy.ops.mesh.primitive_cylinder_add(vertices=32, radius=0.22 if abs(y) < 1 else 0.2, depth=0.03, location=(-2.8, y * 1.1, 1.38), rotation=(math.pi / 2, 0, 0))
        bpy.context.active_object.data.materials.append(iron)

    # dumbbell rack (right) + bench (left)
    for z in (0.45, 0.85):
        box("shelf", (-3.25, 2.5, z), (0.45, 1.5, 0.04), iron)
        for k in range(6):
            dumbbell("db", (-3.25, 1.95 + k * 0.22, z + 0.08), pads, size=0.75 + 0.07 * k)
    box("rack_leg1", (-3.25, 1.78, 0.45), (0.05, 0.05, 0.9), iron)
    box("rack_leg2", (-3.25, 3.22, 0.45), (0.05, 0.05, 0.9), iron)
    box("bench_pad", (-2.0, -2.2, 0.42), (0.28, 1.1, 0.1), red)
    box("bench_leg1", (-2.0, -2.55, 0.2), (0.06, 0.06, 0.4), iron)
    box("bench_leg2", (-2.0, -1.85, 0.2), (0.06, 0.06, 0.4), iron)
    # kettlebells
    for i, y in enumerate((-3.1, -2.85, -2.6)):
        bpy.ops.mesh.primitive_uv_sphere_add(radius=0.11, location=(-3.2, y - 0.4, 0.11))
        bpy.context.active_object.data.materials.append(iron)
        bpy.ops.mesh.primitive_torus_add(major_radius=0.06, minor_radius=0.012, location=(-3.2, y - 0.4, 0.25), rotation=(0, math.pi / 2, 0))
        bpy.context.active_object.data.materials.append(iron)


def apply_pose(J, glow_mats, sol, frame):
    def key(obj, rx):
        obj.rotation_euler = (rx * D2R, 0, 0)
        obj.keyframe_insert("rotation_euler", frame=frame)

    J["root"].location = (0, sol["root"][0], sol["root"][1])
    J["root"].keyframe_insert("location", frame=frame)
    key(J["root"], sol["root_rx"])
    key(J["spine"], sol["spine_rx"])
    key(J["neck"], sol["neck_rx"])
    for s in "RL":
        key(J["shoulder" + s], sol["shoulder_rx"])
        key(J["elbow" + s], sol["elbow_rx"])
        key(J["hip" + s], sol["hip_rx"])
        key(J["knee" + s], sol["knee_rx"])
        key(J["ankle" + s], sol["ankle_rx"])
    for m in glow_mats:
        em = m.node_tree.nodes.get("GLOW_EMIT")
        if em is not None:  # 2D toon material
            sock = em.inputs["Strength"]
            sock.default_value = 0.55 + 0.9 * sol["effort"]
        else:
            sock = m.node_tree.nodes["Principled BSDF"].inputs["Emission Strength"]
            sock.default_value = 0.3 + 1.7 * sol["effort"]
        sock.keyframe_insert("default_value", frame=frame)


def toonify():
    """Convert every material to flat 3-step cel shading (2D look)."""
    for m in bpy.data.materials:
        if not m.use_nodes:
            continue
        nt = m.node_tree
        b = nt.nodes.get("Principled BSDF")
        if b is None:
            continue
        base = tuple(b.inputs["Base Color"].default_value)[:3]
        emit_strength = b.inputs["Emission Strength"].default_value
        emit_color = tuple(b.inputs["Emission Color"].default_value)[:3]
        nt.nodes.clear()
        out = nt.nodes.new("ShaderNodeOutputMaterial")
        if emit_strength > 0.01:
            em = nt.nodes.new("ShaderNodeEmission")
            em.name = "GLOW_EMIT"
            em.inputs["Color"].default_value = (*emit_color, 1)
            em.inputs["Strength"].default_value = 1.0
            nt.links.new(em.outputs[0], out.inputs[0])
            continue
        diff = nt.nodes.new("ShaderNodeBsdfDiffuse")
        diff.inputs["Color"].default_value = (1, 1, 1, 1)
        s2r = nt.nodes.new("ShaderNodeShaderToRGB")
        ramp = nt.nodes.new("ShaderNodeValToRGB")
        ramp.color_ramp.interpolation = "CONSTANT"
        ramp.color_ramp.elements[0].position = 0.0
        ramp.color_ramp.elements[0].color = (0, 0, 0, 1)
        ramp.color_ramp.elements[1].position = 0.10
        ramp.color_ramp.elements[1].color = (1, 1, 1, 1)
        mix = nt.nodes.new("ShaderNodeMix")
        mix.data_type = "RGBA"
        rgba_in = [i for i in mix.inputs if i.type == "RGBA"]
        rgba_out = [o for o in mix.outputs if o.type == "RGBA"][0]
        rgba_in[0].default_value = (base[0] * 0.42, base[1] * 0.42, base[2] * 0.46, 1)  # shadow
        rgba_in[1].default_value = (*[min(1.0, c * 1.15) for c in base], 1)              # lit
        em = nt.nodes.new("ShaderNodeEmission")
        nt.links.new(diff.outputs[0], s2r.inputs[0])
        nt.links.new(s2r.outputs[0], ramp.inputs[0])
        nt.links.new(ramp.outputs[0], mix.inputs[0])
        nt.links.new(rgba_out, em.inputs["Color"])
        nt.links.new(em.outputs[0], out.inputs[0])


def setup_freestyle(thickness=1.7):
    scn = bpy.context.scene
    scn.render.use_freestyle = True
    scn.render.line_thickness_mode = "ABSOLUTE"
    scn.render.line_thickness = thickness
    vl = scn.view_layers[0]
    vl.use_freestyle = True
    fs = vl.freestyle_settings
    if not fs.linesets:
        fs.linesets.new("outline")
    ls = fs.linesets[0]
    ls.select_silhouette = True
    ls.select_border = True
    ls.select_crease = True
    ls.select_edge_mark = False
    if ls.linestyle is None:
        ls.linestyle = bpy.data.linestyles.new("outline_style")
    ls.linestyle.color = (0.03, 0.03, 0.05)


def stage(cam_target, cam_dist, azimuth, elev, res, ortho=None):
    scn = bpy.context.scene
    # floor + backdrop colour
    build_gym()
    scn.world = bpy.data.worlds.new("w")
    scn.world.use_nodes = True
    bg = scn.world.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0.02, 0.03, 0.06, 1)
    bg.inputs[1].default_value = 1.0

    for name, loc, energy, color in (
        ("key", (2.5, -2.0, 3.2), 260, (1, 0.93, 0.85)),
        ("rim", (-2.0, 2.5, 2.4), 200, (0.25, 0.7, 1)),
        ("fill", (1.5, -3.5, 1.4), 80, (0.9, 0.9, 1)),
    ):
        ld = bpy.data.lights.new(name, "AREA")
        ld.energy = energy
        ld.color = color
        ld.size = 2.5
        lo = bpy.data.objects.new(name, ld)
        lo.location = loc
        scn.collection.objects.link(lo)
        c = lo.constraints.new("TRACK_TO")
        c.target = None

    target = bpy.data.objects.new("cam_target", None)
    target.location = (0, cam_target[0], cam_target[1])
    scn.collection.objects.link(target)
    for lo in [o for o in scn.objects if o.type == "LIGHT"]:
        lo.constraints[0].target = target
        lo.constraints[0].track_axis = "TRACK_NEGATIVE_Z"
        lo.constraints[0].up_axis = "UP_Y"

    cam_data = bpy.data.cameras.new("cam")
    cam_data.lens = 42
    if ortho:
        cam_data.type = "ORTHO"
        cam_data.ortho_scale = ortho
        azimuth, elev = 0, 0
        cam_dist = 9
    else:
        cam_data.dof.use_dof = True
        cam_data.dof.aperture_fstop = 4.0
    cam = bpy.data.objects.new("cam", cam_data)
    a = azimuth * D2R
    e = elev * D2R
    cam.location = (
        cam_dist * math.cos(e) * math.cos(a),
        cam_target[0] - cam_dist * math.cos(e) * math.sin(a) * 0.35,
        cam_target[1] + cam_dist * math.sin(e),
    )
    scn.collection.objects.link(cam)
    c = cam.constraints.new("TRACK_TO")
    c.target = target
    c.track_axis = "TRACK_NEGATIVE_Z"
    c.up_axis = "UP_Y"
    scn.camera = cam
    if not ortho:
        cam_data.dof.focus_object = target

    scn.render.resolution_x = res
    scn.render.resolution_y = int(res * 1.25)
    scn.render.fps = 24
    for eng in ("BLENDER_EEVEE", "BLENDER_EEVEE_NEXT", "CYCLES"):
        try:
            scn.render.engine = eng
            break
        except TypeError:
            continue
    if scn.render.engine == "CYCLES":
        scn.cycles.samples = 24
        scn.cycles.use_denoising = True
    scn.view_settings.view_transform = "Standard"


# ---------------------------------------------------------------- exercises
EXERCISES = {
    "pushup": {
        "name": "Push-Up",
        "muscles": ["chest", "triceps", "shoulders"],
        "camera": ((-0.05, 0.38), 3.3, 38, 8),
        "ortho": 2.7,
        "poses": [
            # up: arms straight
            dict(lean=64, hip=0, knee=0, foot_abs=-90, ankle=(-0.85, 0.13), hands=(0.0, 0.05), elbow_bend=-1, neck=18, effort=0.35),
            # down: chest near the floor
            dict(lean=80, hip=0, knee=0, foot_abs=-90, ankle=(-0.85, 0.13), hands=(0.0, 0.05), elbow_bend=-1, neck=10, effort=1.0),
        ],
    },
    "squat": {
        "name": "Bodyweight Squat",
        "muscles": ["quads", "glutes"],
        "camera": ((0.0, 0.8), 3.5, 36, 6),
        "ortho": 2.15,
        "poses": [
            dict(lean=0, hip=0, knee=0, ankle=(0, FOOT_H), arm=0, effort=0.3),
            dict(lean=38, hip=128, knee=110, foot_abs=0, ankle=(0, FOOT_H), arm=92, effort=1.0),
        ],
    },
}


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ex_id = argv[0]
    out = argv[1]
    still = "--still" in argv
    flat = "--2d" in argv
    seconds = int(argv[argv.index("--seconds") + 1]) if "--seconds" in argv else 2
    ex = EXERCISES[ex_id]
    os.makedirs(out, exist_ok=True)

    scn = reset_scene()
    J, glow_mats = build_mannequin(ex["muscles"])
    cam_target, dist, az, el = ex["camera"]
    stage(cam_target, dist, az, el, 576, ortho=ex["ortho"] if flat else None)
    if flat:
        toonify()
        setup_freestyle()
        scn.world.node_tree.nodes["Background"].inputs[0].default_value = (0.05, 0.07, 0.12, 1)
    tag = "_2d" if flat else ""

    poses = ex["poses"]
    if still:
        for i, p in enumerate(poses):
            apply_pose(J, glow_mats, solve_pose(p), i + 1)
            scn.frame_set(i + 1)
            scn.render.filepath = os.path.join(out, f"{ex_id}{tag}_pose{i}.png")
            bpy.ops.render.render(write_still=True)
        return

    cycle = 48  # one rep = 2 seconds at 24 fps
    n = seconds * 24
    reps = max(1, n // cycle)
    for r in range(reps):
        apply_pose(J, glow_mats, solve_pose(poses[0]), r * cycle)
        apply_pose(J, glow_mats, solve_pose(poses[1]), r * cycle + cycle // 2)
    apply_pose(J, glow_mats, solve_pose(poses[0]), reps * cycle)
    scn.frame_start, scn.frame_end = 0, reps * cycle - 1
    try:
        scn.render.image_settings.media_type = "VIDEO"  # Blender 5+
    except (AttributeError, TypeError):
        pass
    scn.render.image_settings.file_format = "FFMPEG"
    scn.render.ffmpeg.format = "MPEG4"
    scn.render.ffmpeg.codec = "H264"
    scn.render.ffmpeg.constant_rate_factor = "MEDIUM"
    scn.render.filepath = os.path.join(out, f"{ex_id}{tag}.mp4")
    bpy.ops.render.render(animation=True)


if __name__ == "__main__":
    main()

"""
Render a Mixamo exercise FBX inside the gym scene (headless Blender):

  blender -b -P mixamo_render.py -- <fbx> <out_dir> <name> [--frames 1,12,24] [--seconds 4]
"""
import math
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mannequin as mq  # noqa: E402  (gym stage + helpers)


def args():
    a = sys.argv[sys.argv.index("--") + 1:]
    opt = {"fbx": BASE_FBX, "anim": a[0], "out": a[1], "name": a[2], "frames": None, "seconds": 4,
           "muscles": ["chest", "triceps", "shoulders"], "cam": (0.5, 5.6), "az": 36, "static": False}
    opt["plain"] = "--athlete" not in a
    if "--static" in a:
        opt["static"] = True
    if "--cam" in a:
        t, d = a[a.index("--cam") + 1].split(",")
        opt["cam"] = (float(t), float(d))
    if "--az" in a:
        opt["az"] = float(a[a.index("--az") + 1])
    if "--muscles" in a:
        opt["muscles"] = a[a.index("--muscles") + 1].split(",")
    if "--frames" in a:
        opt["frames"] = [int(x) for x in a[a.index("--frames") + 1].split(",")]
    if "--seconds" in a:
        opt["seconds"] = int(a[a.index("--seconds") + 1])
    return opt


# muscle -> (bone weights, facing, (t_min, t_max) of body height or None)
MUSCLE_SPEC = {
    "chest": ({"Spine2": 1, "Spine1": 0.5, "LeftShoulder": 1, "RightShoulder": 1}, "front", (0.66, 0.80)),
    "abs": ({"Spine": 1, "Spine1": 1, "Hips": 0.3}, "front", (0.53, 0.68)),
    "back": ({"Spine1": 1, "Spine2": 1, "Neck": 0.3}, "back", (0.60, 0.82)),
    "shoulders": ({"LeftShoulder": 1, "RightShoulder": 1, "LeftArm": 1, "RightArm": 1}, "any", (0.74, 0.90)),
    "biceps": ({"LeftArm": 1, "RightArm": 1}, "front", None),
    "triceps": ({"LeftArm": 1, "RightArm": 1}, "back", None),
    "quads": ({"LeftUpLeg": 1, "RightUpLeg": 1}, "front", None),
    "hamstrings": ({"LeftUpLeg": 1, "RightUpLeg": 1}, "back", None),
    "glutes": ({"Hips": 1, "LeftUpLeg": 0.5, "RightUpLeg": 0.5}, "back", (0.44, 0.58)),
    "calves": ({"LeftLeg": 1, "RightLeg": 1}, "back", (0.0, 0.30)),
}


GLOW_MATS = ('Bodymat', 'Topmat', 'Bottommat')
BASE_FBX = 'C:/Users/Sanket/workout/downloads/mixamo/remy_pushup_with_skin.fbx'


def smooth(x, a, b):
    if b == a:
        return 1.0
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def build_masks(arm, muscles):
    """Create one vertex group per muscle on the skin/clothing meshes (rest pose geometry)."""
    from mathutils import Vector
    arm.data.pose_position = "REST"
    bpy.context.view_layer.update()
    body = bpy.data.objects["Body"]
    zs = [(body.matrix_world @ v.co).z for v in body.data.vertices]
    zmin, zmax = min(zs), max(zs)
    height = zmax - zmin
    eyes = bpy.data.objects["Eyes"]
    ecen = sum((eyes.matrix_world @ v.co for v in eyes.data.vertices), Vector()) / len(eyes.data.vertices)
    hips = arm.matrix_world @ arm.data.bones["mixamorig:Hips"].head_local
    fwd = Vector((ecen.x - hips.x, ecen.y - hips.y, 0))
    fwd.normalize()
    for name in ("Body", "Tops", "Bottoms"):
        ob = bpy.data.objects[name]
        mw = ob.matrix_world
        n3 = mw.to_3x3()
        gname = {g.index: g.name.replace("mixamorig:", "") for g in ob.vertex_groups}
        for m in muscles:
            weights, facing, zr = MUSCLE_SPEC[m]
            vg = ob.vertex_groups.new(name="m_" + m)
            for v in ob.data.vertices:
                bw = sum(weights.get(gname.get(g.group, ""), 0.0) * g.weight for g in v.groups)
                if bw <= 0.01:
                    continue
                bw = min(1.0, bw)
                nrm = (n3 @ v.normal).normalized()
                d = nrm.x * fwd.x + nrm.y * fwd.y
                f = 1.0 if facing == "any" else smooth(d if facing == "front" else -d, 0.0, 0.55)
                zt = ((mw @ v.co).z - zmin) / height
                zf = 1.0 if zr is None else smooth(zt, zr[0] - 0.03, zr[0] + 0.03) * (1 - smooth(zt, zr[1] - 0.03, zr[1] + 0.03))
                w = bw * f * zf
                if w > 0.02:
                    vg.add([v.index], w, "REPLACE")
    arm.data.pose_position = "POSE"


def add_glow(muscles):
    """Add an animated orange emission driven by the muscle vertex groups to each material."""
    for matname in GLOW_MATS:
        m = bpy.data.materials[matname]
        nt = m.node_tree
        out = next(n for n in nt.nodes if n.type == "OUTPUT_MATERIAL")
        link = out.inputs["Surface"].links[0]
        base = link.from_node
        mask = None
        for mu in muscles:
            at = nt.nodes.new("ShaderNodeAttribute")
            at.attribute_type = "GEOMETRY"
            at.attribute_name = "m_" + mu
            if mask is None:
                mask = at.outputs["Fac"]
            else:
                add = nt.nodes.new("ShaderNodeMath")
                add.operation = "ADD"
                nt.links.new(mask, add.inputs[0])
                nt.links.new(at.outputs["Fac"], add.inputs[1])
                mask = add.outputs[0]
        strength = nt.nodes.new("ShaderNodeValue")
        strength.name = "GLOW_STR"
        mul = nt.nodes.new("ShaderNodeMath")
        mul.operation = "MULTIPLY"
        nt.links.new(mask, mul.inputs[0])
        nt.links.new(strength.outputs[0], mul.inputs[1])
        em = nt.nodes.new("ShaderNodeEmission")
        em.inputs["Color"].default_value = (1.0, 0.22, 0.02, 1)
        nt.links.new(mul.outputs[0], em.inputs["Strength"])
        add_sh = nt.nodes.new("ShaderNodeAddShader")
        nt.links.new(base.outputs[0], add_sh.inputs[0])
        nt.links.new(em.outputs[0], add_sh.inputs[1])
        nt.links.new(add_sh.outputs[0], out.inputs["Surface"])


def key_glow(frames_levels):
    for matname in GLOW_MATS:
        nt = bpy.data.materials[matname].node_tree
        node = nt.nodes["GLOW_STR"]
        for f, lvl in frames_levels:
            node.outputs[0].default_value = lvl
            node.outputs[0].keyframe_insert("default_value", frame=f)


def all_fcurves(action):
    try:
        return list(action.fcurves)
    except AttributeError:
        pass
    out = []
    for layer in action.layers:
        for strip in layer.strips:
            for cb in strip.channelbags:
                out.extend(cb.fcurves)
    return out


def make_cyclic():
    for act in bpy.data.actions:
        for fc in all_fcurves(act):
            if not any(mod.type == "CYCLES" for mod in fc.modifiers):
                fc.modifiers.new("CYCLES")


def mul_color(mat, rgb, fac=1.0):
    """Multiply the material's base colour by a tint (keeps the texture)."""
    nt = mat.node_tree
    bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    sock = bsdf.inputs["Base Color"]
    mix = nt.nodes.new("ShaderNodeMix")
    mix.data_type = "RGBA"
    mix.blend_type = "MULTIPLY"
    mix.inputs["Factor"].default_value = fac
    rgba_in = [i for i in mix.inputs if i.type == "RGBA"]
    rgba_out = [o for o in mix.outputs if o.type == "RGBA"][0]
    rgba_in[1].default_value = (*rgb, 1)
    if sock.links:
        src = sock.links[0].from_socket
        nt.links.new(src, rgba_in[0])
    else:
        rgba_in[0].default_value = sock.default_value
    nt.links.new(rgba_out, sock)


def retarget(arm, anim_fbx):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.fbx(filepath=anim_fbx)
    new = [o for o in bpy.data.objects if o not in before]
    new_arm = next(o for o in new if o.type == "ARMATURE")
    act = new_arm.animation_data.action
    ad = arm.animation_data_create()
    ad.action = act
    if hasattr(ad, "action_slot") and getattr(act, "slots", None):
        ad.action_slot = act.slots[0]
    for o in new:
        bpy.data.objects.remove(o, do_unlink=True)
    return act


def make_athlete(arm, bulk=0.034):
    """Shirtless muscular build: bulk displace, shorts, socks, dark hair, tan skin."""
    from mathutils import Vector
    body = bpy.data.objects["Body"]
    bpy.data.objects["Shoes"].hide_render = True
    bpy.data.objects["Shoes"].hide_viewport = True

    bulk_w = {"LeftArm": 1.0, "RightArm": 1.0, "LeftShoulder": 0.8, "RightShoulder": 0.8,
              "Spine2": 0.85, "Spine1": 0.55, "Spine": 0.3, "LeftForeArm": 0.5, "RightForeArm": 0.5,
              "LeftUpLeg": 0.8, "RightUpLeg": 0.8, "LeftLeg": 0.6, "RightLeg": 0.6, "Hips": 0.35, "Neck": 0.4}
    zs = [(body.matrix_world @ v.co).z for v in body.data.vertices]
    zmin, zmax = min(zs), max(zs)
    height = zmax - zmin
    gname = {g.index: g.name.replace("mixamorig:", "") for g in body.vertex_groups}
    vg_bulk = body.vertex_groups.new(name="bulk")
    vg_sock = body.vertex_groups.new(name="socks")
    for v in body.data.vertices:
        w = sum(bulk_w.get(gname.get(g.group, ""), 0.0) * g.weight for g in v.groups)
        if w > 0.01:
            vg_bulk.add([v.index], min(1.0, w), "REPLACE")
        sw = 0.0
        zt = ((body.matrix_world @ v.co).z - zmin) / height
        for g in v.groups:
            n = gname.get(g.group, "")
            if n in ("LeftFoot", "RightFoot", "LeftToeBase", "RightToeBase", "LeftToe_End", "RightToe_End"):
                sw += g.weight
            elif n in ("LeftLeg", "RightLeg"):
                sw += g.weight * (1 - smooth(zt, 0.10, 0.16))
        if sw > 0.01:
            vg_sock.add([v.index], min(1.0, sw), "REPLACE")

    def displace(ob, group, strength, name):
        m = ob.modifiers.new(name, "DISPLACE")
        m.direction = "NORMAL"
        m.mid_level = 0.0
        m.strength = strength
        m.vertex_group = group
        return m

    # bulk goes before the armature modifier
    d = displace(body, "bulk", bulk, "Bulk")
    while body.modifiers[0] != d:
        bpy.context.view_layer.objects.active = body
        bpy.ops.object.modifier_move_up(modifier=d.name)

    # Remy's body has no torso under the shirt: reuse the Tops mesh as bare skin, bulked up.
    tops = bpy.data.objects["Tops"]
    tg = {g.index: g.name.replace("mixamorig:", "") for g in tops.vertex_groups}
    vt = tops.vertex_groups.new(name="bulk")
    for v in tops.data.vertices:
        w = sum(bulk_w.get(tg.get(g.group, ""), 0.0) * g.weight for g in v.groups)
        if w > 0.01:
            vt.add([v.index], min(1.0, w), "REPLACE")
    dt = displace(tops, "bulk", bulk * 1.15, "Bulk")
    while tops.modifiers[0] != dt:
        bpy.context.view_layer.objects.active = tops
        bpy.ops.object.modifier_move_up(modifier=dt.name)

    # socks: masked copy of the body, slightly inflated
    socks = body.copy()
    socks.data = body.data.copy()
    socks.name = "Socks"
    bpy.context.scene.collection.objects.link(socks)
    for m in list(socks.modifiers):
        socks.modifiers.remove(m)
    mk = socks.modifiers.new("SockMask", "MASK")
    mk.vertex_group = "socks"
    mk.threshold = 0.45
    displace(socks, "bulk", bulk, "Bulk")
    displace(socks, "socks", 0.006, "SockThick")
    am = socks.modifiers.new("Armature", "ARMATURE")
    am.object = arm
    socks.parent = arm
    socks.data.materials.clear()
    sm = bpy.data.materials.new("sock")
    sm.use_nodes = True
    b = sm.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (0.92, 0.92, 0.95, 1)
    b.inputs["Roughness"].default_value = 0.85
    socks.data.materials.append(sm)

    tm = bpy.data.materials["Topmat"]
    tb = next(n for n in tm.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    for l in list(tb.inputs["Base Color"].links):
        tm.node_tree.links.remove(l)
    tb.inputs["Base Color"].default_value = (0.78, 0.53, 0.38, 1)
    tb.inputs["Roughness"].default_value = 0.55
    mul_color(bpy.data.materials["Bottommat"], (0.07, 0.09, 0.14), 0.92)   # dark gym shorts
    mul_color(bpy.data.materials["Hairmat"], (0.10, 0.08, 0.07), 1.0)      # black hair
    mul_color(bpy.data.materials["Bodymat"], (0.93, 0.80, 0.68), 1.0)      # warm tan skin


def main():
    o = args()
    os.makedirs(o["out"], exist_ok=True)
    mq.reset_scene()
    bpy.ops.import_scene.fbx(filepath=o["fbx"])
    arm = next(x for x in bpy.data.objects if x.type == "ARMATURE")
    arm.rotation_euler[2] = math.pi  # face +Y like the stage expects
    act = retarget(arm, o["anim"])
    if not o["plain"]:
        make_athlete(arm)
    mq.stage((0.0, o["cam"][0]), o["cam"][1], o["az"], 6, 576)

    scn = bpy.context.scene
    start, end = int(act.frame_range[0]), int(act.frame_range[1])
    scn.render.fps = 30
    muscles = o["muscles"]
    build_masks(arm, muscles)
    add_glow(muscles)
    mid = (start + end) // 2
    key_glow([(start, 1.6), (mid, 0.25), (end, 1.6)])
    make_cyclic()
    if o["frames"]:
        for f in o["frames"]:
            scn.frame_set(f)
            scn.render.filepath = os.path.join(o["out"], f"{o['name']}_f{f}.png")
            bpy.ops.render.render(write_still=True)
        return

    period = end - start
    reps = max(1, round(o["seconds"] * 30 / period))
    scn.frame_start, scn.frame_end = start, start + period * reps - 1
    try:
        scn.render.image_settings.media_type = "VIDEO"
    except (AttributeError, TypeError):
        pass
    scn.render.image_settings.file_format = "FFMPEG"
    scn.render.ffmpeg.format = "MPEG4"
    scn.render.ffmpeg.codec = "H264"
    scn.render.ffmpeg.constant_rate_factor = "MEDIUM"
    scn.render.filepath = os.path.join(o["out"], o["name"] + ".mp4")
    bpy.ops.render.render(animation=True)


if __name__ == "__main__":
    main()

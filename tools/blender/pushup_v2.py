"""
Push-up v2: Mixamo Remy push-up in a neon gym, grey-blue muscular mannequin, orange muscle glow.

  blender -b -P pushup_v2.py -- <out_dir> <name> [--frames 1,10,20] [--seconds 5] [--probe]
"""
import math
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mannequin as mq  # noqa: E402
import mixamo_render as mr  # noqa: E402


def parse():
    a = sys.argv[sys.argv.index("--") + 1:]
    o = {"fbx": mr.BASE_FBX, "out": a[0], "name": a[1], "frames": None, "seconds": 5, "res": 720,
         "cam": (0.0, 0.42), "ortho": 2.2}
    if "--frames" in a:
        o["frames"] = [int(x) for x in a[a.index("--frames") + 1].split(",")]
    if "--seconds" in a:
        o["seconds"] = int(a[a.index("--seconds") + 1])
    if "--res" in a:
        o["res"] = int(a[a.index("--res") + 1])
    if "--ortho" in a:
        o["ortho"] = float(a[a.index("--ortho") + 1])
    if "--cam" in a:
        t, d = a[a.index("--cam") + 1].split(",")
        o["cam"] = (float(t), float(d))
    return o


def grey_blue_skin():
    """Replace tan skin with a grey-blue matte-satin mannequin skin (no texture)."""
    for matname in ("Bodymat", "Topmat"):
        m = bpy.data.materials[matname]
        nt = m.node_tree
        b = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
        for l in list(b.inputs["Base Color"].links):
            nt.links.remove(l)
        b.inputs["Base Color"].default_value = (0.20, 0.26, 0.34, 1)
        b.inputs["Roughness"].default_value = 0.38
        for name in ("Specular IOR Level", "Specular"):
            if name in b.inputs:
                b.inputs[name].default_value = 0.6
                break


def main():
    o = parse()
    os.makedirs(o["out"], exist_ok=True)
    mq.reset_scene()
    bpy.ops.import_scene.fbx(filepath=o["fbx"])
    arm = next(x for x in bpy.data.objects if x.type == "ARMATURE")
    arm.rotation_euler[2] = math.pi
    act = mr.retarget(arm, o["fbx"])
    mr.make_athlete(arm, bulk=0.04)
    grey_blue_skin()
    scn = bpy.context.scene
    start, end = int(act.frame_range[0]), int(act.frame_range[1])
    ys, zs = [], []
    for f in range(start, end + 1, 3):
        scn.frame_set(f)
        for pb in arm.pose.bones:
            w = arm.matrix_world @ pb.head
            ys.append(w.y)
            zs.append(w.z)
    cy, cz = (min(ys) + max(ys)) / 2, (min(zs) + max(zs)) / 2
    span_y = max(ys) - min(ys)
    print("BBOX y", min(ys), max(ys), "z", min(zs), max(zs))
    ortho = span_y * 1.12 / 0.8          # vertical span so the width fits 9:16 with margin
    mq.stage((cy, cz + ortho * 0.12), 9, 0, 0, o["res"], ortho=ortho)
    scn.render.resolution_x = 720
    scn.render.resolution_y = 900
    for lo in [x for x in scn.objects if x.type == "LIGHT"]:
        lo.data.energy *= 0.42
    scn.view_settings.exposure = -0.4
    # cyan rim lights from both sides for the neon look
    for nm, loc in (("neonL", (3.0, -2.2, 1.2)), ("neonR", (3.0, 2.2, 1.2))):
        ld = bpy.data.lights.new(nm, "AREA")
        ld.energy = 260
        ld.color = (0.1, 0.75, 1.0)
        ld.size = 1.5
        lo = bpy.data.objects.new(nm, ld)
        lo.location = loc
        scn.collection.objects.link(lo)
        c = lo.constraints.new("TRACK_TO")
        c.target = bpy.data.objects["cam_target"]
        c.track_axis = "TRACK_NEGATIVE_Z"
        c.up_axis = "UP_Y"
    scn.render.fps = 30
    print("ANIM RANGE", start, end)
    muscles = ["chest", "triceps", "shoulders"]
    mr.build_masks(arm, muscles)
    mr.add_glow(muscles)
    mid = (start + end) // 2
    mr.key_glow([(start, 0.2), (mid, 1.8), (end, 0.2)])
    mr.make_cyclic()
    if o["frames"]:
        for f in o["frames"]:
            scn.frame_set(f)
            scn.render.filepath = os.path.join(o["out"], f"{o['name']}_f{f:03d}.png")
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


main()

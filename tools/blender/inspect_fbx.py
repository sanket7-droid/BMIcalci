import bpy, sys
bpy.ops.wm.read_factory_settings(use_empty=True)
path = sys.argv[sys.argv.index("--") + 1]
bpy.ops.import_scene.fbx(filepath=path)
for o in bpy.data.objects:
    print("OBJ", o.type, o.name, tuple(round(x, 2) for x in o.dimensions))
arm = next(o for o in bpy.data.objects if o.type == "ARMATURE")
print("BONES", len(arm.data.bones), [b.name for b in arm.data.bones][:12])
a = arm.animation_data.action if arm.animation_data else None
print("ACTION", a.name if a else None, a.frame_range[:] if a else None)
meshes = [o for o in bpy.data.objects if o.type == "MESH"]
for m in meshes:
    print("MESH", m.name, len(m.data.vertices), [g.name for g in m.vertex_groups][:5], "groups", len(m.vertex_groups))

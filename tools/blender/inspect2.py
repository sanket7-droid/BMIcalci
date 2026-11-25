import bpy, sys
from mathutils import Vector
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.fbx(filepath=sys.argv[sys.argv.index("--")+1])
arm = next(o for o in bpy.data.objects if o.type=="ARMATURE")
print("ARM scale", tuple(arm.scale), "rot", tuple(round(x,2) for x in arm.rotation_euler))
for o in bpy.data.objects:
    if o.type=="MESH":
        print("MESH", o.name, "parent", o.parent.name if o.parent else None, "scale", tuple(round(x,3) for x in o.scale), "mats", [m.name for m in o.data.materials])
for m in bpy.data.materials:
    imgs=[n.image.name+("(packed)" if n.image.packed_file else "("+n.image.filepath+")") for n in m.node_tree.nodes if n.type=="TEX_IMAGE" and n.image] if m.use_nodes else []
    print("MAT", m.name, imgs)
sc=bpy.context.scene
for f in (1,12,24,36,47):
    sc.frame_set(f)
    h=arm.pose.bones["mixamorig:Hips"]; hw=arm.matrix_world@h.head
    body=bpy.data.objects["Body"]
    dg=bpy.context.evaluated_depsgraph_get(); eb=body.evaluated_get(dg)
    pts=[eb.matrix_world@Vector(c) for c in eb.bound_box]
    mn=Vector((min(p.x for p in pts),min(p.y for p in pts),min(p.z for p in pts))); mx=Vector((max(p.x for p in pts),max(p.y for p in pts),max(p.z for p in pts)))
    print("F",f,"hips",tuple(round(x,2) for x in hw),"bbox",tuple(round(x,2) for x in mn),tuple(round(x,2) for x in mx))
eyes=bpy.data.objects["Eyes"]; print("EYES world", tuple(round(x,2) for x in (eyes.matrix_world@Vector((0,0,0)))))

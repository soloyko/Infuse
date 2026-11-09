# Thumbnail recreation

A three.js voxel scene that recreates the reference thumbnail (`reference.jpg`)
with the custom skin (`skin-render.png`). Output: `thumbnail.png` (1920x1080).

- `skin.js` – the skin rebuilt as a standard 64x64 Minecraft-layout texture
- `sprites.js` – pixel sprites for the sword, pickaxe, enchanted apple, bow, arrows
- `scene.js` – layout, lighting, blood particles, slash streaks, clouds, grass, bloom

Render:

    npm install
    NODE_PATH=$(npm root -g) node render.mjs thumbnail.png            # built-in skin
    NODE_PATH=$(npm root -g) node render.mjs thumbnail.png myskin.png # real 64x64 skin file

## Blender version (current `thumbnail.png`)

Rendered with real Blender (Cycles) through the `bpy` Python package:

    pip install bpy numpy
    python blender/scene.py                       # full quality -> thumbnail.png
    python blender/scene.py --preview --out p.png # quick half-res preview
    python blender/scene.py --skin myskin.png     # use a real 64x64 skin file

`skin.png` is the rebuilt 64x64 skin it generates.

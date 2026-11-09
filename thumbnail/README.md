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

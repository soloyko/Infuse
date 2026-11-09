import * as THREE from 'three';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import { buildSkin } from './skin.js';
import { SWORD, PICKAXE, APPLE, ARROW, BOW } from './sprites.js';

const W = 1920, H = 1080;
const params = new URLSearchParams(location.search);

let seed = 1234;
const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
const R = (a, b) => a + rnd() * (b - a);

const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
renderer.setSize(W, H);
renderer.setPixelRatio(1);
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.05;
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(33, W / H, 0.1, 2000);
camera.position.set(0, 0, 40);
camera.lookAt(0, 0, 0);

// ---------- background: dark crimson radial glow ------------------------
{
  const c = document.createElement('canvas'); c.width = 960; c.height = 540;
  const g = c.getContext('2d');
  const grd = g.createRadialGradient(560, 250, 20, 520, 270, 620);
  grd.addColorStop(0, '#6e0d12');
  grd.addColorStop(0.28, '#3d070c');
  grd.addColorStop(0.6, '#1a0408');
  grd.addColorStop(1, '#060205');
  g.fillStyle = grd; g.fillRect(0, 0, 960, 540);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace;
  scene.background = t;
}

// ---------- lights -------------------------------------------------------
scene.add(new THREE.AmbientLight(0x6a5a6a, 0.9));
const key = new THREE.DirectionalLight(0xfff0f0, 2.0); key.position.set(-30, 20, 40); scene.add(key);
const redBack = new THREE.PointLight(0xff1a22, 900, 0, 1.6); redBack.position.set(6, 12, -14); scene.add(redBack);
const redRim = new THREE.DirectionalLight(0xff2a2a, 3.2);
const redRim2 = new THREE.DirectionalLight(0xff3030, 1.6); redRim2.position.set(-30, 25, -10); scene.add(redRim2); redRim.position.set(30, 10, -20); scene.add(redRim);
const cool = new THREE.DirectionalLight(0x5a7cff, 0.6); cool.position.set(-20, -10, 10); scene.add(cool);

// ---------- helpers ------------------------------------------------------
const skinCanvas = buildSkin();
let skinTex = new THREE.CanvasTexture(skinCanvas);
skinTex.magFilter = skinTex.minFilter = THREE.NearestFilter;
skinTex.colorSpace = THREE.SRGBColorSpace;

const skinMat = new THREE.MeshLambertMaterial({ map: skinTex });
const overlayMat = new THREE.MeshLambertMaterial({ map: skinTex, transparent: true, alphaTest: 0.5, side: THREE.DoubleSide });

// Map MC skin UV rects onto a BoxGeometry. Box face order: +x,-x,+y,-y,+z,-z.
function skinBox(w, h, d, u, v, inflate = 0) {
  const geo = new THREE.BoxGeometry(w + inflate * 2, h + inflate * 2, d + inflate * 2);
  const rects = [
    [u + d + w, v + d, d, h], // +x  = character's left side
    [u, v + d, d, h],         // -x  = character's right side
    [u + d, v, w, d],         // top
    [u + d + w, v, w, d],     // bottom
    [u + d, v + d, w, h],     // front
    [u + 2 * d + w, v + d, w, h], // back
  ];
  const uv = geo.attributes.uv;
  rects.forEach(([x, y, rw, rh], f) => {
    const x0 = x / 64, x1 = (x + rw) / 64, y0 = 1 - y / 64, y1 = 1 - (y + rh) / 64;
    const quad = [[x0, y0], [x1, y0], [x0, y1], [x1, y1]];
    for (let i = 0; i < 4; i++) uv.setXY(f * 4 + i, quad[i][0], quad[i][1]);
  });
  return geo;
}

const glowMat = (color, opacity) => new THREE.MeshBasicMaterial({
  color, side: THREE.BackSide, transparent: true, opacity, blending: THREE.AdditiveBlending, depthWrite: false,
});

// A skin part: base + overlay + red glowing hull, pivoted at `pivot` (local).
function part(w, h, d, uv, ov, inflate, pivotY) {
  const grp = new THREE.Group();
  const inner = new THREE.Group(); inner.position.y = pivotY; grp.add(inner);
  inner.add(new THREE.Mesh(skinBox(w, h, d, ...uv), skinMat));
  inner.add(new THREE.Mesh(skinBox(w, h, d, ...ov, inflate), overlayMat));
  const hull1 = new THREE.Mesh(new THREE.BoxGeometry(w + 1.0, h + 1.0, d + 1.0), glowMat(0xff1010, 0.0));
  const hull2 = new THREE.Mesh(new THREE.BoxGeometry(w + 2.2, h + 2.2, d + 2.2), glowMat(0xb00008, 0.0));
  inner.add(hull1, hull2);
  return grp;
}

// Extrude a 16x16 sprite into voxels (1 unit per pixel).
function voxelSprite(sprite, { emissive = 0, depth = 1, glow = null, glowChars = null } = {}) {
  const grp = new THREE.Group();
  const cells = [];
  sprite.rows.forEach((row, y) => [...row].forEach((ch, x) => { if (ch !== '.') cells.push([x, y, ch]); }));
  const geo = new THREE.BoxGeometry(1, 1, depth);
  const mat = new THREE.MeshLambertMaterial({ vertexColors: false });
  const mesh = new THREE.InstancedMesh(geo, mat, cells.length);
  const m = new THREE.Matrix4(), col = new THREE.Color();
  cells.forEach(([x, y, ch], i) => {
    m.makeTranslation(x - 7.5, 7.5 - y, 0);
    mesh.setMatrixAt(i, m);
    col.set(sprite.pal[ch]); mesh.setColorAt(i, col);
  });
  if (emissive) { mat.emissive = new THREE.Color(0xffffff); mat.emissiveIntensity = emissive; mat.emissiveMap = null; }
  grp.add(mesh);
  if (emissive) {
    // emissive copy so instance colours glow
    const emat = new THREE.MeshBasicMaterial({ transparent: true, opacity: emissive, blending: THREE.AdditiveBlending, depthWrite: false });
    const em = new THREE.InstancedMesh(geo, emat, cells.length);
    cells.forEach(([, ,], i) => { mesh.getMatrixAt(i, m); em.setMatrixAt(i, m); mesh.getColorAt(i, col); em.setColorAt(i, col); });
    grp.add(em);
    mat.emissiveIntensity = 0;
  }
  if (glow) {
    const gc = cells.map((c, i) => i).filter(i => !glowChars || glowChars.includes(cells[i][2]));
    const gm = new THREE.InstancedMesh(new THREE.BoxGeometry(1.7, 1.7, depth + 0.7),
      new THREE.MeshBasicMaterial({ color: glow, transparent: true, opacity: 0.1, blending: THREE.AdditiveBlending, depthWrite: false }), gc.length);
    gc.forEach((ci, i) => { mesh.getMatrixAt(ci, m); gm.setMatrixAt(i, m); });
    grp.add(gm);
  }
  return grp;
}

// ---------- layout ---------------------------------------------------------
// Camera looks straight down -z; at z=0 the frame spans x:[-20.9,20.9] y:[-11.75,11.75].
// Positions below were measured from the reference thumbnail as frame fractions.
const fx = f => (f - 0.5) * 41.8, fy = f => (0.5 - f) * 23.5;

// ---------- character ----------------------------------------------------
const head = part(8, 8, 8, [0, 0], [32, 0], 0.5, 0);
head.position.set(fx(0.475), fy(0.29), 0);
head.rotation.set(0.1, -0.6, 0.03);
scene.add(head);

const body = part(8, 12, 4, [16, 16], [16, 32], 0.25, 0);
body.position.set(fx(0.53), fy(0.29) - 10.1, -1.5);
body.rotation.set(0.05, -0.75, 0);
scene.add(body);

function limb(uv, ov, shoulder, dir) {
  const a = part(4, 12, 4, uv, ov, 0.25, -6); // pivot at top
  a.position.copy(shoulder);
  a.quaternion.setFromUnitVectors(new THREE.Vector3(0, -1, 0), dir.clone().normalize());
  scene.add(a);
  return a;
}
// right arm (screen-left): forearm comes toward the camera, hand up, holding apple
const rArm = limb([40, 16], [40, 32], new THREE.Vector3(-2.5, 0.3, -5), new THREE.Vector3(-2.7, -2.4, 12));
// left arm (screen-right): hangs back and down
const lArm = limb([32, 48], [48, 48], new THREE.Vector3(fx(0.64), fy(0.47), -3.5), new THREE.Vector3(0.25, -1, -0.15));

// enchanted golden apple
const apple = voxelSprite(APPLE, { emissive: 0.12, glow: 0xff3cf0 });
apple.scale.setScalar(0.32);
apple.position.set(fx(0.31) * 31 / 40, fy(0.41) * 31 / 40, 9);
apple.rotation.set(0, 0.35, 0.05);
scene.add(apple);

// diamond sword + pickaxe crossed on the back
const sword = voxelSprite(SWORD, { glow: 0x40fff0, glowChars: 'DMLWk' });
sword.scale.setScalar(0.78);
sword.position.set(fx(0.37) * 1.15, fy(0.28) * 1.15, -6);
sword.rotation.set(0, 0, Math.PI / 2 + 0.12);
scene.add(sword);
const pick = voxelSprite(PICKAXE, { glow: 0x40fff0, glowChars: 'DMLW' });
pick.scale.setScalar(0.72);
pick.position.set(fx(0.715) * 1.16, fy(0.23) * 1.16, -6.5);
pick.rotation.set(0, 0, Math.PI / 2 - 0.08);
scene.add(pick);

// ---------- bow in foreground + arrows -----------------------------------
const bow = voxelSprite(BOW, {});
bow.scale.setScalar(0.7);
bow.position.set(fx(0.46), fy(0.95), 3);
bow.rotation.set(-0.3, 0.1, -0.55);
scene.add(bow);

const arrow1 = voxelSprite(ARROW, { emissive: 0.1 });
arrow1.scale.setScalar(0.5);
arrow1.position.set(fx(0.2), fy(0.54), 1);
arrow1.rotation.set(0, 0, 1.97);
scene.add(arrow1);

const arrow2 = voxelSprite(ARROW, { emissive: 0.1 });
arrow2.scale.setScalar(0.42);
arrow2.position.set(fx(0.75), fy(0.68), 1);
arrow2.rotation.set(0, 0, -0.87);
scene.add(arrow2);

// ---------- glowing white slash streaks (pixel staircase) ----------------
const slashMat = new THREE.MeshBasicMaterial({ color: 0xf2f2f2 });
const slashGlow = new THREE.MeshBasicMaterial({ color: 0xff9a9a, transparent: true, opacity: 0.035, blending: THREE.AdditiveBlending, depthWrite: false });
function slash(pts, thick, z, step = 0.42) {
  const g = new THREE.Group();
  const box = new THREE.BoxGeometry(step, step, 0.3);
  const glowBox = new THREE.BoxGeometry(step * 3.5, step * 3.5, 0.1);
  const points = pts.map(([a, b]) => [fx(a), fy(b)]);
  const total = points.length - 1;
  for (let s = 0; s < total; s++) {
    const [ax, ay] = points[s], [bx, by] = points[s + 1];
    const n = Math.ceil(Math.hypot(bx - ax, by - ay) / step);
    for (let i = 0; i <= n; i++) {
      const t = i / n, u = (s + t) / total;
      const th = Math.max(1, Math.round(thick * (0.25 + 0.75 * Math.sin(Math.PI * u))));
      const x = Math.round((ax + (bx - ax) * t) / step) * step;
      const y = Math.round((ay + (by - ay) * t) / step) * step;
      for (let k = 0; k < th; k++) {
        const m = new THREE.Mesh(box, slashMat); m.position.set(x, y - k * step, z); g.add(m);
      }
      if (i % 2 === 0) { const gm = new THREE.Mesh(glowBox, slashGlow); gm.position.set(x, y - th * step / 2, z - 0.2); g.add(gm); }
    }
  }
  scene.add(g);
}
slash([[0.25, 0.975], [0.4, 0.985], [0.55, 0.95], [0.68, 0.86], [0.8, 0.76], [0.87, 0.7]], 3, 4);
slash([[0.56, 0.88], [0.64, 0.84], [0.72, 0.77]], 2, 4);
slash([[0.13, 0.66], [0.19, 0.68], [0.215, 0.72], [0.235, 0.74], [0.26, 0.72]], 3, 2);
slash([[0.12, 0.69], [0.17, 0.73], [0.2, 0.78]], 2, 2);
slash([[0.64, 0.43], [0.69, 0.44], [0.73, 0.44], [0.76, 0.42]], 2.5, 0);
slash([[0.6, 0.92], [0.67, 0.92], [0.74, 0.88]], 2, 4.5);

// ---------- blood splatter particles -------------------------------------
{
  const reds = [0xff1a1a, 0xd40d14, 0xa8060c, 0xff3b30];
  const geo = new THREE.BoxGeometry(1, 1, 1);
  const mats = reds.map(c => new THREE.MeshBasicMaterial({ color: c }));
  // [frameX, frameY, radius, count]
  const clusters = [[0.36, 0.18, 3.5, 260], [0.33, 0.36, 2.5, 110], [0.63, 0.15, 3.5, 220], [0.61, 0.33, 2.5, 110], [0.47, 0.06, 3, 90],
                    [0.24, 0.66, 2.5, 60], [0.36, 0.72, 2.0, 40], [0.71, 0.08, 2, 40]];
  clusters.forEach(([cx, cy, r, n]) => {
    for (let i = 0; i < n; i++) {
      const a = R(0, Math.PI * 2), d = Math.pow(rnd(), 1.5) * r;
      const m = new THREE.Mesh(geo, mats[Math.floor(rnd() * mats.length)]);
      m.scale.setScalar(Math.max(0.06, R(0.08, 0.32) * (1 - d / (r * 1.3))));
      m.position.set(fx(cx) + Math.cos(a) * d * 1.3, fy(cy) + Math.sin(a) * d * 0.8, R(-4.5, 4.5));
      m.rotation.set(R(0, 3), R(0, 3), R(0, 3));
      scene.add(m);
    }
  });
}

// ---------- blocky dark clouds ------------------------------------------
const cloudMat = new THREE.MeshLambertMaterial({ color: 0x2a1c26 });
const cloudMat2 = new THREE.MeshLambertMaterial({ color: 0x3a2430 });
for (let i = 0; i < 46; i++) {
  const z = R(-160, -40);
  const spread = (-z) * 0.9;
  const x = R(-spread, spread), y = R(-spread * 0.35, spread * 0.55);
  if (Math.abs(x) < 25 && Math.abs(y - 5) < 18) continue; // keep the hero clear
  const grp = new THREE.Group();
  const n = 1 + Math.floor(R(1, 4));
  for (let j = 0; j < n; j++) {
    const w = R(14, 40), h = R(2.5, 6);
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, R(6, 14)), rnd() < 0.5 ? cloudMat : cloudMat2);
    m.position.set(R(-12, 12), R(-4, 4), R(-3, 3));
    grp.add(m);
  }
  grp.position.set(x, y, z);
  grp.rotation.set(R(-0.15, 0.15), R(-0.3, 0.3), R(-0.12, 0.12));
  scene.add(grp);
}

// red glow sprite behind the hero
{
  const c = document.createElement('canvas'); c.width = c.height = 256;
  const g = c.getContext('2d');
  const grd = g.createRadialGradient(128, 128, 0, 128, 128, 128);
  grd.addColorStop(0, 'rgba(255,40,40,0.85)'); grd.addColorStop(0.4, 'rgba(200,10,20,0.35)'); grd.addColorStop(1, 'rgba(120,0,10,0)');
  g.fillStyle = grd; g.fillRect(0, 0, 256, 256);
  const t = new THREE.CanvasTexture(c);
  const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: t, blending: THREE.AdditiveBlending, depthWrite: false, transparent: true }));
  s.scale.set(60, 45, 1); s.position.set(2, 3, -25); scene.add(s);
}

// red blood mist around the head
{
  const c = document.createElement('canvas'); c.width = c.height = 128;
  const g = c.getContext('2d');
  const grd = g.createRadialGradient(64, 64, 0, 64, 64, 64);
  grd.addColorStop(0, 'rgba(230,20,25,0.7)'); grd.addColorStop(1, 'rgba(160,0,10,0)');
  g.fillStyle = grd; g.fillRect(0, 0, 128, 128);
  const t = new THREE.CanvasTexture(c);
  [[0.36, 0.2, 9], [0.63, 0.17, 9], [0.34, 0.38, 6], [0.62, 0.36, 6], [0.25, 0.66, 5]].forEach(([x, y, sz]) => {
    const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: t, blending: THREE.AdditiveBlending, depthWrite: false, transparent: true, opacity: 0.55 }));
    s.scale.set(sz, sz * 0.8, 1); s.position.set(fx(x) * 1.1, fy(y) * 1.1, -4); scene.add(s);
  });
}

// floating white dust specks
{
  const geo = new THREE.BoxGeometry(1, 1, 0.2);
  const mat = new THREE.MeshBasicMaterial({ color: 0xbfb2b6 });
  for (let i = 0; i < 40; i++) {
    const m = new THREE.Mesh(geo, mat);
    const z = R(-60, -5), sp = (50 - z) * 0.45;
    m.position.set(R(-sp, sp), R(-sp * 0.5, sp * 0.55), z);
    m.scale.setScalar(R(0.3, 0.7));
    scene.add(m);
  }
}

// ---------- grass in the foreground --------------------------------------
{
  const greens = [0x3fae2a, 0x5fd13a, 0x2f8a20, 0x7ae84a, 0x4cc232];
  const mats = greens.map(c => new THREE.MeshLambertMaterial({ color: c, emissive: c, emissiveIntensity: 0.2 }));
  const geo = new THREE.BoxGeometry(1, 1, 0.4);
  const tuft = (x0, x1, n) => {
    for (let i = 0; i < n; i++) {
      const x = Math.round(R(x0, x1)), h = Math.floor(R(2, 6)), z = R(8, 12);
      let xx = x;
      for (let k = 0; k < h; k++) {
        if (rnd() < 0.25) xx += rnd() < 0.5 ? -1 : 1;
        const m = new THREE.Mesh(geo, mats[Math.floor(rnd() * mats.length)]);
        m.position.set(xx * 0.45, -10.3 + k * 0.45, z); m.scale.set(0.45, 0.45, 0.45);
        scene.add(m);
      }
    }
  };
  tuft(-40, -22, 60);
  tuft(24, 40, 55);
}

// ---------- post -----------------------------------------------------------
const composer = new EffectComposer(renderer);
composer.addPass(new RenderPass(scene, camera));
composer.addPass(new UnrealBloomPass(new THREE.Vector2(W, H), 0.32, 0.45, 0.8));
composer.addPass(new OutputPass());

// optional: use a real 64x64 skin file via ?skin=path.png
async function maybeLoadSkin() {
  const p = params.get('skin');
  if (!p) return;
  const t = await new THREE.TextureLoader().loadAsync(p);
  t.magFilter = t.minFilter = THREE.NearestFilter; t.colorSpace = THREE.SRGBColorSpace;
  skinMat.map = overlayMat.map = t; skinMat.needsUpdate = overlayMat.needsUpdate = true;
}

maybeLoadSkin().then(() => {
  composer.render();
  window.__done = true;
});

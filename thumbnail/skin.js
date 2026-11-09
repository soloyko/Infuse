// Builds a standard 64x64 Minecraft-layout skin from the reference render:
// black hoodie/clothes, black messy hair covering most of the face,
// dark-brown skin and a single visible grey/white eye.
export function buildSkin() {
  const c = document.createElement('canvas');
  c.width = c.height = 64;
  const g = c.getContext('2d');
  const px = (x, y, col) => { g.fillStyle = col; g.fillRect(x, y, 1, 1); };
  let seed = 7;
  const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const noisy = (x, y, w, h, cols) => {
    for (let j = 0; j < h; j++) for (let i = 0; i < w; i++)
      px(x + i, y + j, cols[Math.floor(rnd() * cols.length)]);
  };

  const HAIR = ['#151515', '#1c1c1c', '#242424', '#191919', '#2c2c2c'];
  const CLOTH = ['#1b1b1b', '#1f1f1f', '#232323', '#1d1d1d'];
  const SKIN = '#3a2414', SKIN2 = '#2e1c10';

  // --- base layers -------------------------------------------------------
  noisy(0, 0, 32, 16, HAIR);       // head (all sides hair by default)
  noisy(16, 16, 24, 16, CLOTH);    // body
  noisy(40, 16, 16, 16, CLOTH);    // right arm
  noisy(32, 48, 16, 16, CLOTH);    // left arm
  noisy(0, 16, 16, 16, CLOTH);     // right leg
  noisy(16, 48, 16, 16, CLOTH);    // left leg

  // face (front of head = 8,8 .. 16,16)
  const face = [
    'HHHHHHHH',
    'HHHHHHHH',
    'HHHHHHHH',
    'HHdgHHHH',
    'HHWLBHHH',
    'HHBBBBHH',
    'HHHbBBHH',
    'HHHHbHHH',
  ];
  const map = { d: '#3c3c40', g: '#5d5d63', W: '#ece8e8', L: '#a9a9ae', B: SKIN, b: SKIN2 };
  face.forEach((row, y) => [...row].forEach((ch, x) => { if (map[ch]) px(8 + x, 8 + y, map[ch]); }));
  // neck / bottom of head shows a bit of skin
  px(19, 6, SKIN2); px(20, 6, SKIN2);

  // --- hat overlay: messy hair volume -------------------------------------
  noisy(40, 0, 16, 8, HAIR);        // top + bottom row region
  g.clearRect(48, 0, 8, 8);         // overlay bottom transparent
  noisy(32, 8, 8, 8, HAIR);         // right side
  noisy(48, 8, 16, 8, HAIR);        // left side + back
  // front fringe: covers top & right of the face, leaves eye + chin visible
  const fringe = [
    'HHHHHHHH',
    'HHHHHHHH',
    'HH.HHHHH',
    'H...HHHH',
    'H....HHH',
    '......HH',
    '......HH',
    '.......H',
  ];
  fringe.forEach((row, y) => [...row].forEach((ch, x) => {
    if (ch === 'H') px(40 + x, 8 + y, HAIR[Math.floor(rnd() * HAIR.length)]);
  }));
  // ragged bottom edge on the sides/back
  for (let x = 32; x < 64; x++) if (rnd() < 0.45 && !(x >= 40 && x < 48)) g.clearRect(x, 15, 1, 1);

  // jacket overlay: subtle hood seam around the neck
  for (let x = 20; x < 28; x++) px(x, 36, '#1a1a1a');
  return c;
}

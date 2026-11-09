// Hand-drawn 16x16 pixel sprites for the items in the thumbnail.
// '.' = empty. Each sprite carries its own palette.

export const SWORD = {
  pal: { k: '#0b3238', D: '#13817c', M: '#2fd8c4', L: '#8ef5e6', W: '#e4fffb',
         h: '#3b2a10', H: '#6b4f1e', G: '#c99a2e' },
  rows: [
    '................',
    '.............kkk',
    '............kWLk',
    '...........kLMkk',
    '..........kLMDk.',
    '.........kLMDk..',
    '........kLMDk...',
    '.......kLMDk....',
    '..kk..kLMDk.....',
    '..kGk.kMDk......',
    '...kGkDDk.......',
    '....kGGk........',
    '....hHkGk.......',
    '...hHk.kGk......',
    '.khHk...kk......',
    '.kkk............',
  ],
};

export const PICKAXE = {
  pal: { k: '#0b3238', D: '#13817c', M: '#2fd8c4', L: '#8ef5e6', W: '#e4fffb',
         h: '#5a3f12', H: '#c8952c', Y: '#f0c94a' },
  rows: [
    '................',
    '....kkkkkk......',
    '...kWLLLMMkk....',
    '....kkkkkMMDk...',
    '.........kHMDk..',
    '........kHYkMDk.',
    '.......kHYk.kDk.',
    '......kHYk..kMk.',
    '.....kHYk....kk.',
    '....kHYk........',
    '...kHYk.........',
    '..kHYk..........',
    '.kHYk...........',
    '.khk............',
    '.kk.............',
    '................',
  ],
};

// Enchanted golden apple: pale gold body, magenta enchantment outline.
export const APPLE = {
  pal: { s: '#5a3a12', S: '#8a5c1e', l: '#ff7cf2', k: '#c22bd6', K: '#8e17b0',
         Y: '#f6dfa0', W: '#fff7e6', O: '#e3b46a', P: '#f9c4c4' },
  rows: [
    '................',
    '.......kk.......',
    '........kSk.....',
    '....kkkkSkkk....',
    '...kWWYYkYYYk...',
    '..kWWWYYYYYYOk..',
    '..kWWPYYYYYYOk..',
    '.kWWPYYYYYYYYOk.',
    '.kWYYYYYYYYYOOk.',
    '.kYYYYYYYYYYOOk.',
    '..kYYYYYYYYOOk..',
    '..kOYYYYYYOOOk..',
    '...kOOYYOOOOk...',
    '....kkOOOkkk....',
    '......kkk.......',
    '................',
  ],
};

export const ARROW = {
  pal: { w: '#e9e9e9', W: '#ffffff', g: '#b7b7b7', s: '#6b5232', S: '#8c6c3e',
         t: '#5c5c5c', T: '#9a9a9a' },
  rows: [
    '................',
    '...........w.ww.',
    '............wWw.',
    '...........sWgw.',
    '..........S..w..',
    '.........s......',
    '........S.......',
    '.......s........',
    '......S.........',
    '.....s..........',
    '..TtS...........',
    '..Tt............',
    '..tT............',
    '................',
    '................',
    '................',
  ],
};

export const BOW = {
  pal: { k: '#2b1d0a', h: '#6b4a1a', H: '#9a6e2a', s: '#d8d8d8' },
  rows: [
    '................',
    '..........kkk...',
    '........kkHHhs..',
    '.......kHhkk.s..',
    '......kHk...s...',
    '.....kHk...s....',
    '.....kHk..s.....',
    '....kHk..s......',
    '....kHk.s.......',
    '...kHk.s........',
    '...kHks.........',
    '..kHks..........',
    '..khs...........',
    '..kks...........',
    '..ss............',
    '................',
  ],
};

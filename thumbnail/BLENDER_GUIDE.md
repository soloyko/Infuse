# Recreating the thumbnail with your skin (Blender + Photopea)

Everything here is free. Plan on about 1–2 hours the first time.
Blender only runs on a computer (Windows, Mac or Linux), not on a phone.

**What you'll use**
- **Blender** (4.2 or newer): blender.org
- **MCprep**, a free Blender add-on that gives you a ready-made Minecraft player rig, skin swapping and Minecraft items: search "MCprep" (by TheDuckCow) and download the `.zip`
- **Photopea** (photopea.com, free in the browser) for the blood splatter, glowing streaks and final touch-up
- **Your skin file**: the 64×64 `.png` (download it from NameMC or the Minecraft launcher)

---

## 1. Set up Blender + MCprep
1. Open Blender, then go to **Edit → Preferences → Add-ons → Install from Disk**, pick the MCprep `.zip` and tick it on.
2. Press **N** in the 3D view and open the **MCprep** tab on the right.
3. Delete the default cube (select it, press **X**).
4. In the **Render** properties (camera-back icon), set **Render Engine: Eevee**.
5. In the **Output** properties, set **Resolution 1920 × 1080**.

## 2. Spawn your character with your skin
1. MCprep tab → **Spawn → Mob/Rig spawner** → pick a **Player** rig (for example "Player – Steve"). Click **Spawn**.
2. MCprep tab → **Skins** → **Skin from file** → choose your `.png` → select the rig → **Apply skin**.
   (You can also type your username under "Skin from username".)
3. Your character should now have the black hoodie, the black hair and the eye.

## 3. Pose it like the reference
Select the rig, switch to **Pose Mode** (Ctrl+Tab), select a bone, press **R** to rotate it, and type the number of degrees.

| Body part | What to do |
|---|---|
| **Whole body** | Turn about **35–40° toward the left of the screen** (you see the front and a bit of the right side) |
| **Head** | Turn a bit further left (~35° more) so it looks at the apple, and tilt it **slightly down** (~10°) |
| **Right arm** (the one on the left of the screen) | Raise it **forward and up**, about **110–120°**, so the hand sits at chin height, left of the face, pointing toward the camera |
| **Left arm** (right of the screen) | Hanging down, swung slightly **back** (~15–20°) |
| **Legs** | Don't matter: they're out of frame |

## 4. Add the items
MCprep tab → **Spawn → Item spawner** (it turns item textures into 3D pixel items). Spawn these:

- **Enchanted golden apple**: put it right above the raised hand, a bit closer to the camera than the face. Scale it so it's about **¾ the size of the head**. Parent it to the hand bone (select apple, then Shift-select the rig, go to Pose Mode, select the hand bone, **Ctrl+P → Bone**) so it follows the arm.
- **Diamond sword**: behind the back, blade sticking out **up and to the left** past the head, tip near the top-left of the frame.
- **Diamond pickaxe**: behind the back, crossing the sword like an **X**. The pickaxe head peeks out at the **top-right** of the head and the gold handle runs **down-right**.
- **Bow**: low in the **bottom-center** of the frame, close to the camera, lying diagonally (it gets mostly covered by the glowing streaks later).
- **Two arrows**:
  - one at the **left middle**, flying **in toward the character** (feathers on the left)
  - one at the **right**, a bit lower than the shoulder
- **Grass**: spawn a few grass blocks/tall grass in the **bottom-left and bottom-right corners**, very close to the camera so they come out blurry.

For the enchant shimmer on the apple, MCprep adds it automatically to enchanted items. If it doesn't, give the apple's material an **Emission** of a pink-magenta color, strength **2–3**.

## 5. Camera
1. **Add → Camera**, then press **Numpad 0** to look through it.
2. In the camera properties set **Focal Length: 50 mm**.
3. Framing: a **close-up of the upper body**, with the camera **a little below the head looking slightly up**.
   - The head sits in the **upper middle** of the frame, top of the hair around 8% down from the top edge.
   - The body fills the bottom half.
   - The apple sits on the left, the crossed tools stick out on both sides of the head.
4. Turn on **Depth of Field**: Focus Object = the head, **F-Stop 2.8**. This blurs the grass and arrows like in the original.

## 6. Lighting (this is what makes the black skin readable)
Delete the default light first. Then add these lights (**Add → Light → Area**):

| Light | Where | Color | Power |
|---|---|---|---|
| **Red rim (main)** | Behind the character, upper **right** | Pure red `#FF1A1A` | **1500–3000 W** |
| **Red rim 2** | Behind, upper **left** | Red `#FF2A2A` | ~800 W |
| **Key light** | In front, upper **left**, soft (size 3–5 m) | Warm white / light pink | **200–400 W** |

In **World** properties set the color to a very dark maroon (`#140306`), strength 1.

What to aim for: the edges of the head, shoulders and arm glow red against the dark background. If your character still looks like a black blob, move the red lights more **behind and to the side** and raise their power. Don't add more front light.

## 7. Background
- **Dark clouds**: add a few long flat cubes (**Add → Mesh → Cube**, scale them long and thin) far behind the character. Give them a dark purple-grey material (`#2A1C26`). Spread them around the edges of the frame, not behind the head.
- **Red glow behind the head**: the red rim lights already light up the clouds. If you want more, add a big plane far behind with an **Emission** material, deep red, strength ~1.5.

## 8. Glow, motion blur, render
1. **Render properties → Motion Blur**: on (only matters if you animate items flying, which is optional).
2. **Compositing workspace** (tab at the top) → tick **Use Nodes**. Add **Filter → Glare** between *Render Layers* and *Composite*. Set **Type: Fog Glow** (or **Bloom**), **Threshold 0.8**, **Size 7–8**. This gives the soft glow around bright parts.
3. Press **F12** to render, then **Image → Save As → PNG**.

## 9. Finish in Photopea (blood + white streaks)
These two effects are much easier to paint than to build in 3D.

1. Open your render in **photopea.com**.
2. **Blood splatter**:
   - Make a new layer and use a splatter brush (search "blood splatter brush .abr" and load it via **File → Open**), color `#D40D14` to `#FF1A1A`.
   - Splatter it on both sides of the head and above it, plus a little near the bottom-left.
   - Add **Layer Style → Outer Glow** (red, small) on that layer.
3. **White glowing streaks**:
   - New layer, **Pencil tool** (not brush, so the edges stay pixel-sharp), white, size 8–14 px.
   - Draw stepped, pixel-looking swooshes:
     - a big one along the **bottom** that curves **up toward the right** over the bow
     - a zig-zag at the **left** near the arrow
     - a small one to the **right of the shoulder**
   - Add **Layer Style → Outer Glow**: white/light pink, size ~20, opacity ~60%.
4. **Small white specks**: a few single dots in the background with the pencil, 3–6 px.
5. Optional: **Filter → Camera Raw** or **Adjustments → Curves** to push contrast and make the reds deeper.
6. **File → Export As → PNG** (or JPG), 1920×1080 or 1280×720 for YouTube.

---

### Alternative: Mine-imator (simpler, but looks less polished)
If Blender feels like too much, **Mine-imator** (free, made just for Minecraft scenes) does steps 2–8 with menus:
- add a Character, load your skin, pose it, add the items
- add red lights behind it, and turn on bloom and depth of field in the render settings

Then do step 9 in Photopea the same way.

"""
Paints the 128x64 texture for Emily's Gaia-layout model (emily_gaia.png + emily_gaia_glow.png) entirely procedurally
-- every pixel is computed from the palette below and the box UVs of HelperGaiaModel.buildMesh(), nothing is copied
or sampled from another mod's or game's files -- and renders a 4-angle 3D preview to check the UVs and proportions.

History: the first version of this script (through 2026-10-21) pasted cropped regions of Grimoire of Gaia 4's
Succubus/Valkyrie textures (design/helper_art/gaia_ref/, kept only as a design reference, never shipped) for the
head, hair and wings. Rewritten 2026-10-22, before the mod's first public release, to paint those regions from
scratch so nothing in the shipped texture is derived from another project's art.
"""
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
OUT_DIR = ROOT / "blighted_helper" / "src" / "main" / "resources" / "assets" / "blighted_helper" / "textures" / "entity"
PREVIEW_PATH = ROOT / "design" / "helper_art" / "skin" / "preview_gaia.png"


# Colors
SKIN_LIT = (255, 226, 206, 255)
SKIN_BASE = (249, 212, 188, 255)
SKIN_MID = (236, 191, 165, 255)
SKIN_SHADE = (214, 164, 138, 255)

BLACK_LIT = (48, 45, 58, 255)
BLACK_BASE = (30, 28, 38, 255)
BLACK_SHADE = (18, 16, 24, 255)
BLACK_DEEP = (11, 10, 15, 255)

GOLD_BRIGHT = (255, 232, 130, 255)
GOLD_BASE = (232, 188, 72, 255)
GOLD_SHADE = (184, 136, 36, 255)
GOLD_DARK = (132, 92, 22, 255)

EMERALD_BRIGHT = (80, 255, 150, 255)
EMERALD_BASE = (22, 210, 100, 255)
EMERALD_DARK = (10, 130, 58, 255)

CRIMSON_LIT = (200, 52, 64, 255)
CRIMSON_BASE = (156, 32, 44, 255)
CRIMSON_DARK = (102, 18, 28, 255)

# Golden-blonde hair, original to this palette (distinct from the GOLD_* trim ramp above)
HAIR_LIT = (255, 244, 196, 255)
HAIR_BASE = (240, 214, 140, 255)
HAIR_MID = (214, 182, 106, 255)
HAIR_SHADE = (176, 142, 74, 255)


def set_px(img: Image.Image, x: int, y: int, c: tuple):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def fill_rect(img: Image.Image, x: int, y: int, w: int, h: int, c: tuple):
    for py in range(y, y + h):
        for px in range(x, x + w):
            set_px(img, px, py, c)


def set_box_faces(img: Image.Image, u: int, v: int, dx: int, dy: int, dz: int,
                  front=None, back=None, left=None, right=None, top=None, bottom=None):
    if top:
        fill_rect(img, u + dz, v, dx, dz, top)
    if bottom:
        fill_rect(img, u + dz + dx, v, dx, dz, bottom)
    if right:
        fill_rect(img, u, v + dz, dz, dy, right)
    if front:
        fill_rect(img, u + dz, v + dz, dx, dy, front)
    if left:
        fill_rect(img, u + dz + dx, v + dz, dz, dy, left)
    if back:
        fill_rect(img, u + dz + dx + dz, v + dz, dx, dy, back)


def build_textures():
    tex = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    glow = Image.new("RGBA", (128, 64), (0, 0, 0, 0))

    # 1. Head (inner 6x6x6, u=0,v=0): skin on every face but the front; the front is the face, painted in step 2.
    set_box_faces(tex, 0, 0, 6, 6, 6,
                  back=SKIN_SHADE, left=SKIN_MID, right=SKIN_MID, top=SKIN_BASE, bottom=SKIN_SHADE)

    # Hair shell (outer 7x7x7, u=36,v=0): solid hair colour all round, with a rectangular window left transparent
    # on the front so the face box shows through (the shell sits 0.5 units in front of the head box in 3D).
    set_box_faces(tex, 36, 0, 7, 7, 7,
                  front=HAIR_BASE, back=HAIR_SHADE, left=HAIR_MID, right=HAIR_MID, top=HAIR_LIT, bottom=HAIR_SHADE)
    for y in range(9, 14):      # clear the lower 5 rows of the 7-tall front face for the face window
        for x in range(44, 49):  # inset 1px from each side so a hairline fringe/forehead band frames it
            set_px(tex, x, y, (0, 0, 0, 0))
    for x in range(43, 50):     # a little vertical shading across the fringe band (y=7..8) and the two side locks
        set_px(tex, x, 7, HAIR_LIT if x % 2 == 0 else HAIR_BASE)
        set_px(tex, x, 8, HAIR_BASE if x % 2 == 0 else HAIR_MID)

    # Cascading back hair (hair1 u=36,v=14 8x8x3; hair2 u=36,v=25 9x9x3): the same hair palette, with a soft
    # top-to-bottom gradient and a few darker strand lines for texture.
    for (u, v, dx, dy, dz) in [(36, 14, 8, 8, 3), (36, 25, 9, 9, 3)]:
        set_box_faces(tex, u, v, dx, dy, dz,
                      front=HAIR_BASE, back=HAIR_SHADE, left=HAIR_MID, right=HAIR_MID, top=HAIR_LIT, bottom=HAIR_SHADE)
        fx0, fy0 = u + dz, v + dz
        for row in range(dy):
            py = fy0 + row
            lit = row < dy // 3
            for col in range(dx):
                px = fx0 + col
                strand = (col + row) % 5 == 0
                col_c = HAIR_LIT if lit else (HAIR_SHADE if strand else HAIR_BASE)
                set_px(tex, px, py, col_c)

    # 2. Face (front of the inner 6x6x6 head box, x=6..12, y=6..12): fair skin, dark eyelashes, emerald eyes, a
    # warm blush and rose lips -- positions follow the box geometry above, the shapes and colours are our own.
    for y in range(6, 12):
        for x in range(6, 12):
            set_px(tex, x, y, SKIN_BASE if (x + y) % 2 else SKIN_LIT)
    set_px(tex, 6, 8, (52, 38, 36, 255))
    set_px(tex, 7, 8, (36, 26, 28, 255))
    set_px(tex, 10, 8, (36, 26, 28, 255))
    set_px(tex, 11, 8, (52, 38, 36, 255))
    set_px(tex, 6, 9, (255, 255, 255, 255))
    set_px(tex, 6, 10, (242, 245, 248, 255))
    set_px(tex, 11, 9, (255, 255, 255, 255))
    set_px(tex, 11, 10, (242, 245, 248, 255))
    set_px(tex, 7, 9, EMERALD_DARK)
    set_px(tex, 7, 10, EMERALD_BRIGHT)
    set_px(tex, 10, 9, EMERALD_DARK)
    set_px(tex, 10, 10, EMERALD_BRIGHT)
    set_px(glow, 7, 9, (20, 200, 95, 190))
    set_px(glow, 7, 10, (70, 255, 150, 230))
    set_px(glow, 10, 9, (20, 200, 95, 190))
    set_px(glow, 10, 10, (70, 255, 150, 230))
    set_px(tex, 6, 11, (245, 182, 170, 255))
    set_px(tex, 11, 11, (245, 182, 170, 255))
    set_px(tex, 8, 11, (228, 146, 140, 255))
    set_px(tex, 9, 11, (228, 146, 140, 255))

    # 3. Gold Forehead Circlet & Glowing Emerald Gem on outer head layer (u=36, v=0, 7x7x7)
    # Front of 7x7x7 box is at x = 36 + 7 = 43 .. 50 (width 7), y = 0 + 7 = 7 .. 14 (height 7)
    set_px(tex, 44, 8, GOLD_SHADE)
    set_px(tex, 45, 8, GOLD_BRIGHT)
    set_px(tex, 46, 7, GOLD_BRIGHT)
    set_px(tex, 46, 8, EMERALD_BRIGHT)
    set_px(tex, 46, 9, GOLD_BASE)
    set_px(tex, 47, 8, GOLD_BRIGHT)
    set_px(tex, 48, 8, GOLD_SHADE)
    set_px(glow, 46, 8, EMERALD_BRIGHT)

    # 4. Stepped Curved Obsidian & Gold Dragon Horns (u=36, v=37..55)
    # hornbase: texOffs(36, 37) 2x2x2
    set_box_faces(tex, 36, 37, 2, 2, 2,
                  front=GOLD_BASE, back=GOLD_SHADE, left=GOLD_BASE, right=GOLD_SHADE, top=GOLD_BRIGHT, bottom=GOLD_DARK)
    # horn1: texOffs(36, 41) 2x2x3
    set_box_faces(tex, 36, 41, 2, 2, 3,
                  front=BLACK_LIT, back=BLACK_DEEP, left=BLACK_BASE, right=BLACK_BASE, top=GOLD_BASE, bottom=BLACK_DEEP)
    # horn2: texOffs(36, 46) 2x2x2
    set_box_faces(tex, 36, 46, 2, 2, 2,
                  front=BLACK_BASE, back=BLACK_DEEP, left=BLACK_LIT, right=BLACK_SHADE, top=BLACK_LIT, bottom=BLACK_DEEP)
    # horn tip: texOffs(36, 50) 1x1x1
    set_box_faces(tex, 36, 50, 1, 1, 1,
                  front=GOLD_BRIGHT, back=GOLD_SHADE, left=GOLD_BASE, right=GOLD_BASE, top=GOLD_BRIGHT, bottom=GOLD_DARK)

    # Front shoulder hair locks: texOffs(48, 37) 2x6x2
    set_box_faces(tex, 48, 37, 2, 6, 2,
                  front=(242, 200, 102, 255), back=(198, 152, 64, 255),
                  left=(230, 185, 86, 255), right=(230, 185, 86, 255),
                  top=(248, 212, 118, 255), bottom=(182, 136, 52, 255))
    for y in range(39, 45):
        set_px(tex, 50, y, (252, 220, 130, 255) if y % 2 == 0 else (225, 178, 78, 255))
        set_px(tex, 51, y, (235, 190, 90, 255) if y % 2 == 0 else (208, 160, 68, 255))

    # 5. Upper Torso (bodytop_female: u=0, v=16, dx=5, dy=6, dz=3)
    # Front is x=3..8 (width 5), y=19..25 (height 6)
    # Back is x=11..16 (width 5), y=19..25 (height 6)
    # Top (shoulders/collarbone) is x=3..8, y=16..19 -> bare skin!
    # Paint off-shoulder sweetheart bodice on lower 4 rows of bodytop (y=21..25), leaving y=19..20 bare collarbone/cleavage!
    for y in range(21, 25):
        for x in range(3, 16):
            set_px(tex, x, y, BLACK_BASE if (x + y) % 2 == 0 else BLACK_SHADE)
    # Gold sweetheart neckline trim at y=20..21
    set_px(tex, 3, 20, GOLD_BASE)
    set_px(tex, 4, 20, GOLD_BRIGHT)
    set_px(tex, 5, 21, GOLD_BRIGHT)  # center dip
    set_px(tex, 6, 20, GOLD_BRIGHT)
    set_px(tex, 7, 20, GOLD_BASE)
    # Center vertical gold corset busk on front (x=5, y=22..25)
    for y in range(22, 25):
        set_px(tex, 5, y, GOLD_BASE)
    # Gold back trim at y=21, x=11..16
    for x in range(11, 16):
        set_px(tex, x, 21, GOLD_SHADE)
    # Subtle gold choker necklace at top of chest/neck (u=0, v=12, 2x2x2 -> front x=2..4, y=14..16)
    set_px(tex, 2, 15, GOLD_BASE)
    set_px(tex, 3, 15, GOLD_BRIGHT)

    # 6. Chest / Bust (chest: u=0, v=36, dx=2, dy=2, dz=2)
    # Top: x=2..4, y=36..38 (upper slope of bust -> bare skin + gold sweetheart edge!)
    # Front: x=2..4, y=38..40 (black velvet cup + gold trim)
    set_box_faces(tex, 0, 36, 2, 2, 2,
                  top=SKIN_LIT,
                  bottom=BLACK_DEEP,
                  front=BLACK_BASE,
                  back=BLACK_DEEP,
                  left=BLACK_SHADE,
                  right=BLACK_SHADE)
    # Gold sweetheart rim along the top-front edge of the bust cups
    set_px(tex, 2, 37, GOLD_BRIGHT)
    set_px(tex, 3, 37, GOLD_BASE)
    set_px(tex, 2, 38, GOLD_BASE)
    set_px(tex, 3, 38, BLACK_LIT)

    # 7. Midsection / Waist (bodymiddle: u=0, v=25, dx=4, dy=3, dz=2)
    # Front: x=2..6, y=27..30; Side/Back: x=0..2, 6..12, y=27..30
    set_box_faces(tex, 0, 25, 4, 3, 2,
                  front=BLACK_BASE, back=BLACK_SHADE, left=BLACK_SHADE, right=BLACK_SHADE,
                  top=BLACK_DEEP, bottom=BLACK_DEEP)
    # Gold corset boning on waist front (x=2..6, y=27..30)
    for y in range(27, 30):
        set_px(tex, 2, y, GOLD_SHADE)
        set_px(tex, 3, y, BLACK_LIT)
        set_px(tex, 4, y, BLACK_LIT)
        set_px(tex, 5, y, GOLD_SHADE)

    # 8. Hips / Pelvis (bodybottom: u=0, v=30, dx=6, dy=3, dz=3) + Hip Belt Wrap (u=64, v=24, dx=7, dy=4, dz=4)
    # Underneath pelvis (u=0, v=30):
    # Right side (x=3..6) is black velvet dress; Left side (x=6..9) is bare left hip skin through the high slit!
    set_box_faces(tex, 0, 30, 6, 3, 3,
                  front=BLACK_BASE, back=BLACK_SHADE, left=SKIN_BASE, right=BLACK_SHADE,
                  top=BLACK_DEEP, bottom=BLACK_DEEP)
    for y in range(33, 36):
        set_px(tex, 6, y, GOLD_BASE)   # slit gold border
        set_px(tex, 7, y, SKIN_LIT)    # bare left hip through high slit
        set_px(tex, 8, y, SKIN_BASE)

    # Hip wrap (u=64, v=24, dx=7, dy=4, dz=4) -> Ornate gold V-belt at top + slit opening on left hip!
    # Clear hip wrap first
    fill_rect(tex, 64, 24, 22, 8, (0, 0, 0, 0))
    # Top row of hip wrap (y=28..29) is the ornate gold belt all around
    for x in range(64, 86):
        set_px(tex, x, 28, GOLD_BRIGHT if x % 2 == 0 else GOLD_BASE)
        set_px(tex, x, 29, GOLD_SHADE)
    # Front of hip wrap is x=68..75 (width 7), y=28..32 (height 4)
    # Center emerald buckle at x=71, y=29..30
    set_px(tex, 71, 29, EMERALD_BRIGHT)
    set_px(tex, 71, 30, EMERALD_BASE)
    set_px(glow, 71, 29, EMERALD_BRIGHT)
    set_px(glow, 71, 30, EMERALD_BASE)
    set_px(tex, 70, 30, GOLD_BRIGHT)
    set_px(tex, 72, 30, GOLD_BRIGHT)
    set_px(tex, 71, 31, GOLD_BASE)
    # Right half of front & right side & back are black velvet skirt top; Left half of front (x=72..75, y=30..32) is transparent for the high leg slit!
    for y in range(30, 32):
        for x in range(64, 71):
            set_px(tex, x, y, BLACK_BASE)
        set_px(tex, 71, y, GOLD_BASE)  # gold trim along slit
        for x in range(79, 86):        # back
            set_px(tex, x, y, BLACK_SHADE)

    # 9. Arms & Opera Gloves
    # Upper arm: u=16, v=12, dx=2, dy=6, dz=2 (bare shoulder/upper arm top 3px, black opera glove bottom 3px)
    set_box_faces(tex, 16, 12, 2, 6, 2,
                  front=SKIN_BASE, back=SKIN_SHADE, left=SKIN_MID, right=SKIN_MID, top=SKIN_LIT, bottom=BLACK_DEEP)
    for y in range(16, 20):
        for x in range(16, 24):
            if y == 16:
                set_px(tex, x, y, GOLD_BASE)  # gold top trim of opera glove
            else:
                set_px(tex, x, y, BLACK_BASE if x % 2 == 0 else BLACK_SHADE)
    # Lower arm: u=16, v=18, dx=2, dy=6, dz=2 (black opera glove + dainty fingers at bottom y=25)
    set_box_faces(tex, 16, 18, 2, 6, 2,
                  front=BLACK_BASE, back=BLACK_SHADE, left=BLACK_SHADE, right=BLACK_LIT, top=BLACK_DEEP, bottom=SKIN_BASE)
    for x in range(16, 24):
        set_px(tex, x, 24, GOLD_BASE)  # gold wrist bracelet
        set_px(tex, x, 25, SKIN_LIT)   # fingertips
    # Forearm glove cuff overlay: u=64, v=6, dx=2, dy=6, dz=3
    fill_rect(tex, 64, 6, 10, 9, (0, 0, 0, 0))
    set_box_faces(tex, 64, 6, 2, 6, 3,
                  front=BLACK_BASE, back=BLACK_DEEP, left=BLACK_SHADE, right=BLACK_SHADE)
    for x in range(64, 74):
        set_px(tex, x, 9, GOLD_SHADE)
        set_px(tex, x, 14, GOLD_BRIGHT)

    # Off-shoulder sleeve puff (pauldron: u=64, v=0, dx=3, dy=3, dz=3)
    fill_rect(tex, 64, 0, 12, 6, (0, 0, 0, 0))
    for y in range(4, 6):
        for x in range(64, 76):
            set_px(tex, x, y, GOLD_BASE if y == 4 else BLACK_BASE)

    # 10. Legs (upper leg: u=24, v=12, dx=3, dy=7, dz=3; lower leg: u=24, v=22, dx=3, dy=7, dz=3)
    # Upper leg (u=24, v=12): warm shaded skin + delicate gold thigh-strap at y=17
    set_box_faces(tex, 24, 12, 3, 7, 3,
                  front=SKIN_LIT, back=SKIN_SHADE, left=SKIN_BASE, right=SKIN_BASE, top=SKIN_BASE, bottom=SKIN_SHADE)
    # Vertical highlight down front of thigh (x=28, y=15..22)
    for y in range(15, 22):
        set_px(tex, 27, y, SKIN_BASE)
        set_px(tex, 28, y, (255, 234, 216, 255))
        set_px(tex, 29, y, SKIN_BASE)
    # Gold garter/thigh ring at y=17
    for x in range(24, 36):
        set_px(tex, x, 17, GOLD_BRIGHT if x in (27, 28, 29) else GOLD_SHADE)
    set_px(tex, 28, 18, EMERALD_BRIGHT)

    # Lower leg (u=24, v=22, dx=3, dy=7, dz=3): bare shin + black & gold high-heel sandal on bottom 3 rows (y=29..32)
    set_box_faces(tex, 24, 22, 3, 7, 3,
                  front=SKIN_BASE, back=SKIN_SHADE, left=SKIN_MID, right=SKIN_MID, top=SKIN_BASE, bottom=BLACK_DEEP)
    for y in range(25, 29):
        set_px(tex, 28, y, SKIN_LIT)
    # Ankle strap at y=29, black/gold heel at y=30..31
    for x in range(24, 36):
        set_px(tex, x, 29, GOLD_BASE)
        set_px(tex, x, 30, BLACK_BASE)
        set_px(tex, x, 31, BLACK_DEEP)
    # Expose top of foot skin on front between straps (x=27..29, y=30)
    set_px(tex, 28, 30, SKIN_BASE)
    set_px(tex, 27, 31, GOLD_BRIGHT)
    set_px(tex, 28, 31, BLACK_LIT)
    set_px(tex, 29, 31, GOLD_BRIGHT)

    # 11. Flowing Back/Side Gown Train (skirt1: u=96, v=8, 8x4x4; skirt2: u=96, v=16, 9x5x4; skirt3: u=96, v=25, 10x5x4)
    # Wait: let's check UV width of 10x5x4 -> 2*(10+4) = 28 -> 96 + 28 = 124 <= 128! Fits cleanly!
    # Because Emily's gown is open on the front-left (for her bare left leg slit), we paint the back, right side, and right half of front, leaving the front-left slit transparent!
    for (u, v, dx, dy, dz) in [(96, 8, 8, 4, 4), (96, 16, 9, 5, 4), (96, 25, 10, 5, 4)]:
        fill_rect(tex, u, v, 2 * (dx + dz), dz + dy, (0, 0, 0, 0))
        # Right side face: x = u .. u+dz, y = v+dz .. v+dz+dy (solid black velvet with gold vertical trim)
        for py in range(v + dz, v + dz + dy):
            for px in range(u, u + dz):
                set_px(tex, px, py, BLACK_BASE if (px + py) % 2 == 0 else BLACK_SHADE)
        # Front face: x = u+dz .. u+dz+dx, y = v+dz .. v+dz+dy
        # Only paint the RIGHT half of the front face (first dx//2 + 1 columns) with a diagonal gold slit edge!
        slit_cols = dx // 2 + 1
        for py in range(v + dz, v + dz + dy):
            for i in range(slit_cols):
                px = u + dz + i
                if i == slit_cols - 1:
                    set_px(tex, px, py, GOLD_BRIGHT if py % 2 == 0 else GOLD_BASE)
                else:
                    set_px(tex, px, py, BLACK_BASE if i % 2 == 0 else BLACK_LIT)
        # Left side face: x = u+dz+dx .. u+dz+dx+dz -> only back half of left side so her left leg is open from the side too!
        for py in range(v + dz, v + dz + dy):
            for i in range(dz // 2, dz):
                px = u + dz + dx + i
                set_px(tex, px, py, GOLD_BASE if i == dz // 2 else BLACK_SHADE)
        # Back face: x = u+dz+dx+dz .. u+2*(dx+dz), y = v+dz .. v+dz+dy (full flowing black & gold back gown!)
        for py in range(v + dz, v + dz + dy):
            for px in range(u + dz + dx + dz, u + 2 * (dx + dz)):
                is_pleat = (px - (u + dz + dx + dz)) % 3 == 1
                set_px(tex, px, py, BLACK_LIT if is_pleat else BLACK_BASE)
        # Bottom gold hem on skirt3 (v=25)
        if v == 25:
            py = v + dz + dy - 1
            for px in range(u, u + 2 * (dx + dz)):
                if tex.getpixel((px, py))[3] > 0:
                    set_px(tex, px, py, GOLD_BRIGHT if px % 2 == 0 else GOLD_BASE)

    # 12. Right-Leg Front/Side Gown Panel (u=76, v=34, dx=4, dy=7, dz=4 and lower u=76, v=45, dx=4, dy=5, dz=4)
    set_box_faces(tex, 76, 34, 4, 7, 4,
                  front=BLACK_BASE, back=BLACK_SHADE, left=GOLD_BASE, right=BLACK_SHADE)
    # Vertical pleats + gold slit trim on inner-left edge of right leg panel
    for py in range(38, 45):
        set_px(tex, 80, py, BLACK_LIT)
        set_px(tex, 81, py, BLACK_BASE)
        set_px(tex, 82, py, BLACK_SHADE)
        set_px(tex, 83, py, GOLD_BRIGHT)
    set_box_faces(tex, 76, 45, 4, 5, 4,
                  front=BLACK_BASE, back=BLACK_SHADE, left=GOLD_BASE, right=BLACK_SHADE)
    for py in range(49, 54):
        set_px(tex, 80, py, BLACK_LIT)
        set_px(tex, 83, py, GOLD_BRIGHT)
    for px in range(76, 92):
        if tex.getpixel((px, 53))[3] > 0:
            set_px(tex, px, 53, GOLD_BASE)

    # 13. Dragon Tail (tail1: u=64, v=34, 3x6x3; tail2: u=64, v=43, 2x6x2; tail3: u=64, v=51, 1x5x1 + spade u=70, v=51, 3x3x1)
    set_box_faces(tex, 64, 34, 3, 6, 3,
                  front=BLACK_BASE, back=BLACK_DEEP, left=BLACK_SHADE, right=BLACK_SHADE, top=BLACK_DEEP, bottom=BLACK_DEEP)
    for py in range(37, 43):
        set_px(tex, 68, py, GOLD_SHADE if py % 2 == 0 else BLACK_LIT)
    set_box_faces(tex, 64, 43, 2, 6, 2,
                  front=BLACK_BASE, back=BLACK_DEEP, left=BLACK_SHADE, right=BLACK_SHADE, top=BLACK_DEEP, bottom=BLACK_DEEP)
    for py in range(45, 51):
        set_px(tex, 66, py, GOLD_SHADE if py % 2 == 0 else BLACK_LIT)
    set_box_faces(tex, 64, 51, 1, 5, 1,
                  front=BLACK_BASE, back=BLACK_DEEP, left=BLACK_SHADE, right=BLACK_SHADE, top=BLACK_DEEP, bottom=BLACK_DEEP)
    set_box_faces(tex, 70, 51, 3, 3, 1,
                  front=CRIMSON_BASE, back=BLACK_DEEP, left=GOLD_BASE, right=GOLD_BASE, top=GOLD_BRIGHT, bottom=CRIMSON_LIT)

    # 14. Crimson & Obsidian Bat Wings (u=104, v=42, dx=0, dy=14, dz=8): a flat double-sided plane, so only the
    # Right (-X, x=104..112) and Left (+X, x=112..120) faces have any area, y=42..64. Painted as a leathery
    # membrane: dark near the wing bones (top and the two side edges), a crimson web between them, with a few
    # straight veins fanning out from the top-right anchor -- an original pattern, not a traced silhouette.
    for x in range(8):
        for y in range(22):
            edge = x == 0 or x == 7 or y < 1
            # veins: straight lines from the anchor (x=7,y=0) spaced every 3 rows along y, thinned by distance
            vein = False
            for vy in range(0, 22, 3):
                if abs((x * (vy + 1)) - (7 * (y + 1))) < 3:
                    vein = True
                    break
            if edge:
                col = BLACK_SHADE
            elif vein:
                col = BLACK_BASE
            else:
                t = 1.0 - (x / 7.0) * 0.5 - (y / 21.0) * 0.3
                col = (int(CRIMSON_BASE[0] * t + CRIMSON_DARK[0] * (1 - t)),
                       int(CRIMSON_BASE[1] * t + CRIMSON_DARK[1] * (1 - t)),
                       int(CRIMSON_BASE[2] * t + CRIMSON_DARK[2] * (1 - t)), 255)
            set_px(tex, 104 + x, 42 + y, col)
            set_px(tex, 112 + x, 42 + y, col)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    tex.save(OUT_DIR / "emily_gaia.png")
    glow.save(OUT_DIR / "emily_gaia_glow.png")
    print(f"Saved {OUT_DIR / 'emily_gaia.png'} and emily_gaia_glow.png")
    return tex, glow


# ==============================================================================
# 3D Software Preview Renderer of HelperGaiaModel
# ==============================================================================
class Part:
    def __init__(self, name, px=0.0, py=0.0, pz=0.0, rx=0.0, ry=0.0, rz=0.0):
        self.name = name
        self.px, self.py, self.pz = px, py, pz
        self.rx, self.ry, self.rz = rx, ry, rz
        self.boxes = []  # (u, v, x, y, z, dx, dy, dz, mirror, inflate)
        self.children = []

    def add_box(self, u, v, x, y, z, dx, dy, dz, mirror=False, inflate=0.0):
        self.boxes.append((u, v, x, y, z, dx, dy, dz, mirror, inflate))
        return self

    def add_child(self, child):
        self.children.append(child)
        return child


def build_gaia_rig() -> Part:
    root = Part("root", 0.0, 24.0, 0.0)

    bodybottom = root.add_child(Part("bodybottom", 0.0, -13.5, -0.5, 0.0873, 0.0, 0.0))
    bodybottom.add_box(0, 30, -3.0, -1.5, -1.5, 6, 3, 3)
    bodybottom.add_box(64, 24, -3.5, -1.0, -2.0, 7, 4, 4)

    bodymiddle = bodybottom.add_child(Part("bodymiddle", 0.0, -1.5, 0.5, -0.0873, 0.0, 0.0))
    bodymiddle.add_box(0, 25, -2.0, -2.5, -1.5, 4, 3, 2)

    bodytop = bodymiddle.add_child(Part("bodytop", 0.0, -2.0, 0.4, -0.0873, 0.0, 0.0))
    bodytop_female = bodytop.add_child(Part("bodytop_female", 0.0, 0.0, 0.6))
    bodytop_female.add_box(0, 16, -2.5, -6.0, -3.0, 5, 6, 3)

    chest = bodytop_female.add_child(Part("chest", 0.0, -5.3, -2.5, 0.7854, 0.0, 0.0))
    chest.add_box(0, 36, -2.3, -0.45, -2.0, 2, 2, 2)
    chest.add_box(0, 36, 0.3, -0.45, -2.0, 2, 2, 2, mirror=True)

    neck = bodytop.add_child(Part("neck", 0.0, -6.0, -0.9, 0.0873, 0.0, 0.0))
    neck.add_box(0, 12, -1.0, -1.0, -1.0, 2, 2, 2)

    head = neck.add_child(Part("head", 0.0, 0.0, 0.5))
    head.add_box(0, 0, -3.0, -6.0, -3.0, 6, 6, 6)
    head.add_box(36, 0, -3.5, -6.5, -3.5, 7, 7, 7)

    # Slender front shoulder hair locks framing outer shoulders
    head.add_box(48, 37, -3.4, -0.5, -2.5, 1, 5, 1)
    head.add_box(48, 37, 2.4, -0.5, -2.5, 1, 5, 1, mirror=True)

    # Stepped dragon horns (swept upward and slightly back)
    hornbase = head.add_child(Part("hornbase", 0.0, 0.0, 0.0, -0.2618, 0.0, 0.0))
    hornbase.add_box(36, 37, 2.0, -7.0, -1.5, 2, 2, 2)
    hornbase.add_box(36, 37, -4.0, -7.0, -1.5, 2, 2, 2)
    horn1 = hornbase.add_child(Part("horn1"))
    horn1.add_box(36, 41, 2.8, -9.0, -1.0, 2, 2, 3)
    horn1.add_box(36, 41, -4.8, -9.0, -1.0, 2, 2, 3)
    horn1.add_box(36, 46, 3.2, -10.8, 0.0, 2, 2, 2)
    horn1.add_box(36, 46, -5.2, -10.8, 0.0, 2, 2, 2)
    horn1.add_box(36, 50, 3.5, -11.8, 0.5, 1, 1, 1)
    horn1.add_box(36, 50, -4.5, -11.8, 0.5, 1, 1, 1)

    # Cascading 3D back hair (hair1 + hair2)
    hair1 = neck.add_child(Part("hair1", 0.0, 0.0, 0.5, 0.1745, 0.0, 0.0))
    hair1.add_box(36, 14, -4.0, -5.5, 1.0, 8, 8, 3)
    hair2 = hair1.add_child(Part("hair2", 0.0, 2.0, 1.5, 0.0873, 0.0, 0.0))
    hair2.add_box(36, 25, -4.5, -1.0, 0.0, 9, 9, 3)

    # Arms
    rightarm = bodytop.add_child(Part("rightarm", -2.5, -4.5, -0.9, 0.0873, 0.0, 0.1745))
    rightarm.add_box(16, 12, -2.0, -1.0, -1.0, 2, 6, 2)
    rightarm.add_box(64, 0, -2.5, -1.5, -1.5, 3, 3, 3)
    rightarmlower = rightarm.add_child(Part("rightarmlower", -1.0, 5.0, 1.0, -0.1745, 0.0, 0.0))
    rightarmlower.add_box(16, 18, -1.005, 0.0, -2.0, 2, 6, 2)
    rightarmlower.add_box(64, 6, -1.5, 0.5, -2.5, 2, 6, 3)

    leftarm = bodytop.add_child(Part("leftarm", 2.5, -4.5, -0.9, 0.0873, 0.0, -0.1745))
    leftarm.add_box(16, 12, 0.0, -1.0, -1.0, 2, 6, 2, mirror=True)
    leftarm.add_box(64, 0, -0.5, -1.5, -1.5, 3, 3, 3, mirror=True)
    leftarmlower = leftarm.add_child(Part("leftarmlower", 1.0, 5.0, 1.0, -0.1745, 0.0, 0.0))
    leftarmlower.add_box(16, 18, -0.995, 0.0, -2.0, 2, 6, 2, mirror=True)
    leftarmlower.add_box(64, 6, -0.5, 0.5, -2.5, 2, 6, 3, mirror=True)

    # Wings (Succubus bat wings - both use u=104, v=42 since both -X and +X faces are painted)
    rightwing = bodytop.add_child(Part("rightwing", -1.5, -5.0, 1.6, 0.4363, -0.65, 0.0))
    rightwing.add_box(104, 42, 0.0, -1.0, 0.0, 0, 14, 8)
    leftwing = bodytop.add_child(Part("leftwing", 1.5, -5.0, 1.6, 0.4363, 0.65, 0.0))
    leftwing.add_box(104, 42, 0.0, -1.0, 0.0, 0, 14, 8)

    # Flowing back/side skirt train (from ValkyrieModel)
    skirt1 = bodybottom.add_child(Part("skirt1", 0.0, 0.5, 0.0, 0.14, 0.0, 0.0))
    skirt1.add_box(96, 8, -4.0, 0.0, -2.2, 8, 4, 4)
    skirt2 = skirt1.add_child(Part("skirt2", 0.0, 3.5, 0.2, 0.05, 0.0, 0.0))
    skirt2.add_box(96, 16, -4.5, 0.0, -2.2, 9, 5, 4)
    skirt3 = skirt2.add_child(Part("skirt3", 0.0, 4.5, 0.2, 0.05, 0.0, 0.0))
    skirt3.add_box(96, 25, -5.0, 0.0, -2.2, 10, 5, 4)

    # Dragon tail (3 articulated segments)
    tail1 = bodybottom.add_child(Part("tail1", 0.0, 0.5, 1.5, 0.5236, 0.0, 0.0))
    tail1.add_box(64, 34, -1.5, 0.0, -1.5, 3, 6, 3)
    tail2 = tail1.add_child(Part("tail2", 0.0, 5.5, 0.0, 0.4363, 0.2, 0.0))
    tail2.add_box(64, 43, -1.0, 0.0, -1.0, 2, 6, 2)
    tail3 = tail2.add_child(Part("tail3", 0.0, 5.5, 0.0, 0.3491, 0.2, 0.0))
    tail3.add_box(64, 51, -0.5, 0.0, -0.5, 1, 5, 1)
    tail3.add_box(70, 51, -1.5, 4.0, -0.5, 3, 3, 1)

    # Legs (Right leg has outer gown panel; Left leg is bare through the high slit!)
    rightleg = root.add_child(Part("rightleg", -2.0, -13.0, 0.0, 0.0, 0.0, 0.03))
    rightleg.add_box(24, 12, -1.5, -1.0, -1.5, 3, 7, 3)
    rightleg.add_box(76, 34, -2.0, -1.0, -2.0, 4, 7, 4)
    rightleglower = rightleg.add_child(Part("rightleglower", 0.0, 6.0, 0.0))
    rightleglower.add_box(24, 22, -1.505, 0.0, -1.5, 3, 7, 3)
    rightleglower.add_box(76, 45, -2.005, 0.0, -2.0, 4, 5, 4)

    leftleg = root.add_child(Part("leftleg", 2.0, -13.0, -0.2, -0.05, -0.08, -0.04))
    leftleg.add_box(24, 12, -1.5, -1.0, -1.5, 3, 7, 3, mirror=True)
    leftleglower = leftleg.add_child(Part("leftleglower", 0.0, 6.0, 0.0, 0.05, 0.0, 0.0))
    leftleglower.add_box(24, 22, -1.495, 0.0, -1.5, 3, 7, 3, mirror=True)

    return root


def mat_rot_xyz(rx, ry, rz):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    # Minecraft ModelPart rotation order: Z * Y * X
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def collect_quads(part: Part, parent_M=None, parent_T=None):
    if parent_M is None:
        parent_M = np.eye(3)
        parent_T = np.zeros(3)
    T = parent_T + parent_M @ np.array([part.px, part.py, part.pz])
    M = parent_M @ mat_rot_xyz(part.rx, part.ry, part.rz)

    quads = []
    for (u, v, x, y, z, dx, dy, dz, mirror, inf) in part.boxes:
        x0, y0, z0 = x - inf, y - inf, z - inf
        x1, y1, z1 = x + dx + inf, y + dy + inf, z + dz + inf

        # 6 faces of a Minecraft box: (vertices, uv_rect (u0, v0, u1, v1), shade)
        faces = [
            # Front (-Z)
            ([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
             (u + dz, v + dz, u + dz + dx, v + dz + dy), 1.0),
            # Back (+Z)
            ([(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)],
             (u + dz + dx + dz, v + dz, u + dz + dx + dz + dx, v + dz + dy), 0.82),
            # Right (-X)
            ([(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)],
             (u, v + dz, u + dz, v + dz + dy), 0.88),
            # Left (+X)
            ([(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
             (u + dz + dx, v + dz, u + dz + dx + dz, v + dz + dy), 0.88),
            # Top (-Y)
            ([(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
             (u + dz, v, u + dz + dx, v + dz), 1.05),
            # Bottom (+Y)
            ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
             (u + dz + dx, v, u + dz + dx + dx, v + dz), 0.72),
        ]
        for verts, (u0, v0, u1, v1), shade in faces:
            if u0 == u1 or v0 == v1:
                continue
            if mirror:
                u0, u1 = u1, u0
            w_verts = [T + M @ np.array(pt) for pt in verts]
            quads.append((w_verts, (u0, v0, u1, v1), shade))

    for ch in part.children:
        quads.extend(collect_quads(ch, M, T))
    return quads


def render_preview(tex: Image.Image):
    root = build_gaia_rig()
    quads = collect_quads(root)

    W, H = 960, 480
    canvas = Image.new("RGBA", (W, H), (24, 24, 32, 255))
    draw = ImageDraw.Draw(canvas)

    angles = [
        ("Front", 0.0, 125),
        ("3/4 Front-Left", -0.55, 365),
        ("3/4 Back-Right", 2.45, 605),
        ("Back", math.pi, 840),
    ]

    scale = 10.5
    cy_screen = 250

    for label, yaw, cx_screen in angles:
        R_cam = mat_rot_xyz(0.12, yaw, 0.0)
        # Subdivide each quad into texel micro-quads so pixel art is crystal-clear
        micro_polys = []
        for w_verts, (u0, v0, u1, v1), shade in quads:
            c_verts = [R_cam @ (pt - np.array([0.0, 12.0, 0.0])) for pt in w_verts]
            du = abs(u1 - u0)
            dv = abs(v1 - v0)
            steps_u = max(1, int(du))
            steps_v = max(1, int(dv))
            p00, p10, p11, p01 = c_verts
            for iv in range(steps_v):
                tv0, tv1 = iv / steps_v, (iv + 1) / steps_v
                ty = int(v0 + (v1 - v0) * (iv + 0.5) / steps_v)
                for iu in range(steps_u):
                    tu0, tu1 = iu / steps_u, (iu + 1) / steps_u
                    tx = int(u0 + (u1 - u0) * (iu + 0.5) / steps_u)
                    if not (0 <= tx < tex.width and 0 <= ty < tex.height):
                        continue
                    r, g, b, a = tex.getpixel((tx, ty))
                    if a < 20:
                        continue
                    q00 = (1 - tu0) * (1 - tv0) * p00 + tu0 * (1 - tv0) * p10 + tu0 * tv0 * p11 + (1 - tu0) * tv0 * p01
                    q10 = (1 - tu1) * (1 - tv0) * p00 + tu1 * (1 - tv0) * p10 + tu1 * tv0 * p11 + (1 - tu1) * tv0 * p01
                    q11 = (1 - tu1) * (1 - tv1) * p00 + tu1 * (1 - tv1) * p10 + tu1 * tv1 * p11 + (1 - tu1) * tv1 * p01
                    q01 = (1 - tu0) * (1 - tv1) * p00 + tu0 * (1 - tv1) * p10 + tu0 * tv1 * p11 + (1 - tu0) * tv1 * p01
                    z_avg = (q00[2] + q10[2] + q11[2] + q01[2]) * 0.25
                    col = (
                        min(255, int(r * shade)),
                        min(255, int(g * shade)),
                        min(255, int(b * shade)),
                        255,
                    )
                    pts_2d = [
                        (cx_screen - q[0] * scale, cy_screen + q[1] * scale)
                        for q in (q00, q10, q11, q01)
                    ]
                    micro_polys.append((z_avg, pts_2d, col))

        # Sort back-to-front (largest +Z is furthest away in camera space where -Z is front)
        micro_polys.sort(key=lambda item: -item[0])
        for _, pts_2d, col in micro_polys:
            draw.polygon(pts_2d, fill=col)
        draw.text((cx_screen - 35, H - 28), label, fill=(220, 220, 235, 255))

    canvas.save(PREVIEW_PATH)
    print(f"Saved 3D preview to {PREVIEW_PATH}")


if __name__ == "__main__":
    tex, _ = build_textures()
    render_preview(tex)

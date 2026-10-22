"""Generates Emily's custom 64x64 Minecraft Slim (Alex 3px-arm) skin, emissive glow map,
3D voxel dragon-parts texture (horns, wings, tail), and a 3D preview sheet."""
import math
import os
import shutil
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
ENTITY_TEX_DIR = os.path.join(
    ROOT,
    "blighted_enhancements",
    "src",
    "main",
    "resources",
    "assets",
    "blighted_enhancements",
    "textures",
    "entity",
)
DEV_ENTITY_TEX_DIR = r"C:\Users\srafa\bd\game\resourcepacks\emily_dev\assets\blighted_enhancements\textures\entity"

# Palette carefully matched to Emily's character sheet
SKIN_BASE = (242, 214, 198, 255)
SKIN_LIGHT = (250, 226, 212, 255)
SKIN_SHADE = (224, 188, 170, 255)
SKIN_DEEP = (202, 162, 145, 255)
BLUSH = (238, 176, 168, 255)
LIPS = (212, 138, 134, 255)

EYE_WHITE = (248, 248, 250, 255)
EYE_EMERALD_DARK = (24, 118, 68, 255)
EYE_EMERALD_BRIGHT = (58, 214, 118, 255)
EYE_SPEC = (230, 255, 240, 255)
EYELASH = (28, 20, 24, 255)

HAIR_BASE = (230, 194, 128, 255)
HAIR_HIGH = (246, 218, 158, 255)
HAIR_MID = (210, 170, 104, 255)
HAIR_DARK = (178, 136, 76, 255)
HAIR_ROOT = (148, 108, 56, 255)

DRESS_BASE = (20, 18, 24, 255)
DRESS_HIGH = (36, 33, 44, 255)
DRESS_FOLD = (12, 10, 15, 255)
DRESS_DEEP = (7, 6, 9, 255)

GOLD_BRIGHT = (242, 202, 100, 255)
GOLD_MID = (204, 158, 66, 255)
GOLD_DARK = (148, 108, 38, 255)

GEM_BRIGHT = (64, 235, 132, 255)
GEM_DARK = (20, 132, 72, 255)

HORN_BASE = (22, 20, 26, 255)
HORN_RIDGE = (42, 38, 48, 255)
HORN_TIP = (10, 9, 12, 255)

WING_BONE = (24, 20, 26, 255)
WING_MEM_DARK = (38, 14, 20, 255)
WING_MEM_RED = (92, 22, 30, 255)
WING_MEM_VEIN = (136, 32, 40, 255)


def set_px(arr, x, y, color):
    if 0 <= x < arr.shape[1] and 0 <= y < arr.shape[0]:
        arr[y, x] = color


def fill_rect(arr, x, y, w, h, color):
    arr[max(0, y) : min(arr.shape[0], y + h), max(0, x) : min(arr.shape[1], x + w)] = color


def shade_hair_rect(arr, x0, y0, w, h, seed=0):
    for dy in range(h):
        for dx in range(w):
            strand = (dx + seed) % 4
            wave = (dy + dx) % 5
            if strand == 0 and wave in (1, 2):
                c = HAIR_HIGH
            elif strand == 2 and wave in (0, 3):
                c = HAIR_DARK
            elif dy == 0:
                c = HAIR_ROOT
            elif dy == h - 1 and strand % 2 == 1:
                c = HAIR_DARK
            else:
                c = HAIR_BASE if (dx + dy) % 2 == 0 else HAIR_MID
            set_px(arr, x0 + dx, y0 + dy, c)


def shade_dress_rect(arr, x0, y0, w, h, seed=0):
    for dy in range(h):
        for dx in range(w):
            fold = (dx * 2 + dy + seed) % 5
            if fold == 0:
                c = DRESS_HIGH
            elif fold in (2, 4):
                c = DRESS_BASE
            elif fold == 3:
                c = DRESS_FOLD
            else:
                c = DRESS_DEEP
            set_px(arr, x0 + dx, y0 + dy, c)


def build_emily_skin():
    skin = np.zeros((64, 64, 4), dtype=np.uint8)
    glow = np.zeros((64, 64, 4), dtype=np.uint8)

    # =========================================================================
    # 1. HEAD BASE (UV 0, 0) - 8x8x8
    # Top (8,0,8,8), Bottom (16,0,8,8), Right (0,8,8,8), Front (8,8,8,8), Left (16,8,8,8), Back (24,8,8,8)
    # =========================================================================
    # Top of head: blonde hair crown with center part
    shade_hair_rect(skin, 8, 0, 8, 8, seed=1)
    for dy in range(8):
        set_px(skin, 11, dy, HAIR_ROOT)
        set_px(skin, 12, dy, HAIR_HIGH)

    # Bottom of head (chin/neck underside)
    fill_rect(skin, 16, 0, 8, 8, SKIN_DEEP)
    fill_rect(skin, 18, 2, 4, 4, SKIN_SHADE)

    # Front face (8..15, 8..15)
    fill_rect(skin, 8, 8, 8, 8, SKIN_BASE)
    # Forehead & upper hairline
    shade_hair_rect(skin, 8, 8, 8, 2, seed=2)
    set_px(skin, 11, 9, SKIN_LIGHT)
    set_px(skin, 12, 9, SKIN_LIGHT)
    # Forehead circlet gold band + center emerald gem
    set_px(skin, 10, 9, GOLD_MID)
    set_px(skin, 11, 9, GOLD_BRIGHT)
    set_px(skin, 12, 9, GEM_BRIGHT)
    set_px(skin, 13, 9, GOLD_MID)
    set_px(glow, 12, 9, GEM_BRIGHT)

    # Slanted dark-gold eyebrows (sly arch)
    set_px(skin, 9, 10, HAIR_ROOT)
    set_px(skin, 10, 10, HAIR_DARK)
    set_px(skin, 13, 10, HAIR_DARK)
    set_px(skin, 14, 10, HAIR_ROOT)

    # Emerald anime eyes (rows y=11..12)
    # Right eye (viewer left: x=9,10)
    set_px(skin, 9, 11, EYELASH)
    set_px(skin, 10, 11, EYE_EMERALD_DARK)
    set_px(skin, 9, 12, EYE_WHITE)
    set_px(skin, 10, 12, EYE_EMERALD_BRIGHT)
    # Left eye (viewer right: x=13,14)
    set_px(skin, 13, 11, EYE_EMERALD_DARK)
    set_px(skin, 14, 11, EYELASH)
    set_px(skin, 13, 12, EYE_EMERALD_BRIGHT)
    set_px(skin, 14, 12, EYE_WHITE)

    # Emissive glow on emerald irises
    set_px(glow, 10, 11, EYE_EMERALD_DARK)
    set_px(glow, 10, 12, EYE_EMERALD_BRIGHT)
    set_px(glow, 13, 11, EYE_EMERALD_DARK)
    set_px(glow, 13, 12, EYE_EMERALD_BRIGHT)

    # Soft cheek blush & nose highlight (row y=13)
    set_px(skin, 8, 13, HAIR_BASE)
    set_px(skin, 9, 13, BLUSH)
    set_px(skin, 10, 13, SKIN_BASE)
    set_px(skin, 11, 13, SKIN_LIGHT)
    set_px(skin, 12, 13, SKIN_LIGHT)
    set_px(skin, 13, 13, SKIN_BASE)
    set_px(skin, 14, 13, BLUSH)
    set_px(skin, 15, 13, HAIR_BASE)

    # Subtle anime smirk & chin contour (rows y=14..15)
    set_px(skin, 8, 14, HAIR_BASE)
    set_px(skin, 9, 14, SKIN_BASE)
    set_px(skin, 10, 14, SKIN_LIGHT)
    set_px(skin, 11, 14, LIPS)
    set_px(skin, 12, 14, SKIN_SHADE)
    set_px(skin, 13, 14, SKIN_LIGHT)
    set_px(skin, 14, 14, SKIN_BASE)
    set_px(skin, 15, 14, HAIR_BASE)

    set_px(skin, 8, 15, HAIR_DARK)
    set_px(skin, 9, 15, SKIN_SHADE)
    set_px(skin, 10, 15, SKIN_BASE)
    set_px(skin, 11, 15, SKIN_LIGHT)
    set_px(skin, 12, 15, SKIN_LIGHT)
    set_px(skin, 13, 15, SKIN_BASE)
    set_px(skin, 14, 15, SKIN_SHADE)
    set_px(skin, 15, 15, HAIR_DARK)

    # Right & Left sides of head: hair + pointed elf/dragon ear
    shade_hair_rect(skin, 0, 8, 8, 8, seed=3)
    shade_hair_rect(skin, 16, 8, 8, 8, seed=4)
    # Pointed ear on right side (x=3..6, y=11..13)
    set_px(skin, 5, 11, SKIN_LIGHT)
    set_px(skin, 4, 11, SKIN_LIGHT)
    set_px(skin, 3, 11, SKIN_SHADE)
    set_px(skin, 5, 12, SKIN_BASE)
    set_px(skin, 4, 12, SKIN_SHADE)
    set_px(skin, 6, 12, SKIN_BASE)
    # Pointed ear on left side (x=17..20, y=11..13)
    set_px(skin, 18, 11, SKIN_LIGHT)
    set_px(skin, 19, 11, SKIN_LIGHT)
    set_px(skin, 20, 11, SKIN_SHADE)
    set_px(skin, 18, 12, SKIN_BASE)
    set_px(skin, 19, 12, SKIN_SHADE)
    set_px(skin, 17, 12, SKIN_BASE)

    # Back of head (24..31, 8..15): rich cascading blonde hair
    shade_hair_rect(skin, 24, 8, 8, 8, seed=5)

    # =========================================================================
    # 2. HAT / HAIR OVERLAY (UV 32, 0) - 3D bangs, sidelocks, gold circlet
    # =========================================================================
    # Top of hat (40..47, 0..7): hair crown volume + horn root accents
    shade_hair_rect(skin, 40, 0, 8, 8, seed=6)
    fill_rect(skin, 40, 2, 2, 2, HORN_BASE)
    fill_rect(skin, 46, 2, 2, 2, HORN_BASE)

    # Front hat (40..47, 8..15): parted bangs + long side-locks framing cheeks + gold circlet
    # Top row of bangs
    for dx in range(8):
        set_px(skin, 40 + dx, 8, HAIR_HIGH if dx in (2, 5) else HAIR_BASE)
    # Side bangs & parted curtain bangs
    set_px(skin, 40, 9, HAIR_BASE)
    set_px(skin, 41, 9, HAIR_HIGH)
    set_px(skin, 46, 9, HAIR_HIGH)
    set_px(skin, 47, 9, HAIR_BASE)
    # Gold circlet bridge on overlay
    set_px(skin, 42, 9, GOLD_MID)
    set_px(skin, 45, 9, GOLD_MID)
    set_px(skin, 43, 9, GOLD_BRIGHT)
    set_px(skin, 44, 9, GEM_BRIGHT)
    set_px(glow, 44, 9, GEM_BRIGHT)
    # Side locks framing face down to y=15
    for y_lock in range(10, 16):
        set_px(skin, 40, y_lock, HAIR_HIGH if y_lock % 2 == 0 else HAIR_BASE)
        if y_lock in (10, 14, 15):
            set_px(skin, 41, y_lock, HAIR_MID)
            set_px(skin, 46, y_lock, HAIR_MID)
        set_px(skin, 47, y_lock, HAIR_HIGH if y_lock % 2 == 0 else HAIR_BASE)

    # Right & Left hat sides (32..39, 8..15) and (48..55, 8..15): 3D hair waves leaving ear tip visible
    shade_hair_rect(skin, 32, 8, 8, 8, seed=7)
    shade_hair_rect(skin, 48, 8, 8, 8, seed=8)
    # Cut window in side hat overlay so pointed ears & face stay crisp
    fill_rect(skin, 35, 10, 5, 5, (0, 0, 0, 0))
    fill_rect(skin, 48, 10, 5, 5, (0, 0, 0, 0))

    # Back hat (56..63, 8..15): 3D layered blonde locks
    shade_hair_rect(skin, 56, 8, 8, 8, seed=9)

    # =========================================================================
    # 3. TORSO / BODY BASE (UV 16, 16) - 8x12x4
    # Top (20,16,8,4), Bottom (28,16,8,4), Right (16,20,4,12), Front (20,20,8,12), Left (28,20,4,12), Back (32,20,8,12)
    # =========================================================================
    fill_rect(skin, 20, 16, 8, 4, SKIN_BASE)
    fill_rect(skin, 28, 16, 8, 4, DRESS_DEEP)

    # Front torso (20..27, 20..31)
    # Rows 20..23: bare ivory neck/chest + black-and-gold lace choker + emerald teardrop gem
    fill_rect(skin, 20, 20, 8, 4, SKIN_BASE)
    # Black lace choker at y=20..21
    fill_rect(skin, 22, 20, 4, 1, DRESS_BASE)
    set_px(skin, 21, 20, GOLD_DARK)
    set_px(skin, 26, 20, GOLD_DARK)
    set_px(skin, 23, 21, GOLD_BRIGHT)
    set_px(skin, 24, 21, GEM_BRIGHT)
    set_px(glow, 24, 21, GEM_BRIGHT)
    # Subtle cleavage & collarbone shading at y=22..23
    set_px(skin, 21, 22, SKIN_LIGHT)
    set_px(skin, 22, 22, SKIN_LIGHT)
    set_px(skin, 25, 22, SKIN_LIGHT)
    set_px(skin, 26, 22, SKIN_LIGHT)
    set_px(skin, 23, 22, SKIN_SHADE)
    set_px(skin, 24, 22, SKIN_SHADE)
    set_px(skin, 23, 23, SKIN_DEEP)
    set_px(skin, 24, 23, SKIN_DEEP)

    # Sweetheart neckline black silk corset (y=23..31)
    shade_dress_rect(skin, 20, 23, 8, 9, seed=1)
    # Re-expose central V of sweetheart neckline at y=23
    set_px(skin, 23, 23, SKIN_DEEP)
    set_px(skin, 24, 23, SKIN_SHADE)
    # Gold sweetheart trim along y=23..24
    set_px(skin, 20, 23, GOLD_MID)
    set_px(skin, 21, 23, GOLD_BRIGHT)
    set_px(skin, 22, 24, GOLD_MID)
    set_px(skin, 23, 24, GOLD_BRIGHT)
    set_px(skin, 24, 24, GOLD_BRIGHT)
    set_px(skin, 25, 24, GOLD_MID)
    set_px(skin, 26, 23, GOLD_BRIGHT)
    set_px(skin, 27, 23, GOLD_MID)
    # Gold filigree embroidery curving down the left side of waist (x=25..27, y=26..31)
    set_px(skin, 25, 26, GOLD_MID)
    set_px(skin, 26, 27, GOLD_BRIGHT)
    set_px(skin, 25, 28, GOLD_DARK)
    set_px(skin, 26, 29, GOLD_MID)
    set_px(skin, 27, 30, GOLD_BRIGHT)
    set_px(skin, 26, 31, GOLD_MID)
    # Subtle hourglass waist shading on sides
    for y_w in range(26, 30):
        set_px(skin, 20, y_w, DRESS_DEEP)
        set_px(skin, 27, y_w, DRESS_DEEP)

    # Right & Left torso sides (16..19, 20..31) and (28..31, 20..31)
    fill_rect(skin, 16, 20, 4, 3, SKIN_SHADE)
    shade_dress_rect(skin, 16, 23, 4, 9, seed=2)
    fill_rect(skin, 28, 20, 4, 3, SKIN_SHADE)
    shade_dress_rect(skin, 28, 23, 4, 9, seed=3)

    # Back torso (32..39, 20..31): bare upper back + cascading blonde hair down to y=26 + black corset
    fill_rect(skin, 32, 20, 8, 4, SKIN_SHADE)
    shade_dress_rect(skin, 32, 24, 8, 8, seed=4)
    # Long blonde hair V cascading down her upper back (y=20..26)
    shade_hair_rect(skin, 32, 20, 8, 5, seed=10)
    shade_hair_rect(skin, 33, 25, 6, 2, seed=11)
    shade_hair_rect(skin, 34, 27, 4, 1, seed=12)
    # Gold back trim at waist
    set_px(skin, 33, 29, GOLD_DARK)
    set_px(skin, 36, 29, GOLD_DARK)
    set_px(skin, 34, 30, GOLD_MID)
    set_px(skin, 35, 30, GOLD_MID)

    # =========================================================================
    # 4. JACKET / TORSO OVERLAY (UV 16, 32) - 3D hair locks on chest/back + gold filigree
    # =========================================================================
    # Front jacket (20..27, 36..47): blonde hair locks draped over shoulders + 3D gold bodice filigree
    for y_s in range(36, 41):
        set_px(skin, 20, y_s, HAIR_HIGH if y_s % 2 == 0 else HAIR_BASE)
        set_px(skin, 21, y_s, HAIR_MID)
        set_px(skin, 26, y_s, HAIR_MID)
        set_px(skin, 27, y_s, HAIR_HIGH if y_s % 2 == 0 else HAIR_BASE)
    set_px(skin, 20, 41, HAIR_DARK)
    set_px(skin, 27, 41, HAIR_DARK)
    # 3D Gold filigree along waist slit start
    set_px(skin, 26, 44, GOLD_BRIGHT)
    set_px(skin, 25, 45, GOLD_MID)
    set_px(skin, 26, 46, GOLD_BRIGHT)
    set_px(skin, 25, 47, GOLD_MID)

    # Back jacket (32..39, 36..47): 3D flowing blonde hair tips over upper back
    shade_hair_rect(skin, 32, 36, 8, 5, seed=13)
    shade_hair_rect(skin, 33, 41, 6, 2, seed=14)
    for dx in (0, 7):
        set_px(skin, 32 + dx, 40, (0, 0, 0, 0))

    # =========================================================================
    # 5. ARMS (SLIM 3x12x4) - Right Arm (40, 16), Left Arm (32, 48)
    # Off-shoulder black & gold straps, bare upper arm, long black opera gloves with gold filigree
    # =========================================================================
    def paint_slim_arm(u0, v0, overlay_u0, overlay_v0):
        # Top (u0+4, v0, 3, 4) -> bare shoulder skin
        fill_rect(skin, u0 + 4, v0, 3, 4, SKIN_BASE)
        # Bottom (u0+7, v0, 3, 4) -> black glove palm/fingertips
        fill_rect(skin, u0 + 7, v0, 3, 4, DRESS_DEEP)
        # All 4 vertical faces: Right(u0, v0+4, 4, 12), Front(u0+4, v0+4, 3, 12), Left(u0+7, v0+4, 4, 12), Back(u0+11, v0+4, 3, 12)
        total_w = 14
        x_start = u0
        y_start = v0 + 4
        # Rows 0..1: bare shoulder skin
        fill_rect(skin, x_start, y_start, total_w, 2, SKIN_BASE)
        # Rows 2..3: off-shoulder black silk band with gold trim
        fill_rect(skin, x_start, y_start + 2, total_w, 2, DRESS_BASE)
        for dx in range(total_w):
            if dx % 2 == 0:
                set_px(skin, x_start + dx, y_start + 2, GOLD_MID)
        # Rows 4..5: bare mid-upper arm with gold lace tattoo/embroidery
        fill_rect(skin, x_start, y_start + 4, total_w, 2, SKIN_SHADE)
        set_px(skin, x_start + 5, y_start + 4, SKIN_LIGHT)
        set_px(skin, x_start + 5, y_start + 5, SKIN_BASE)
        # Rows 6..11: long black opera gloves with gold cuff
        shade_dress_rect(skin, x_start, y_start + 6, total_w, 6, seed=5)
        for dx in range(total_w):
            set_px(skin, x_start + dx, y_start + 6, GOLD_BRIGHT if dx % 3 == 0 else GOLD_DARK)

        # Overlay sleeve: 3D off-shoulder drape & glove cuff
        ox = overlay_u0
        oy = overlay_v0 + 4
        for dx in range(total_w):
            set_px(skin, ox + dx, oy + 2, GOLD_BRIGHT if dx % 2 == 0 else DRESS_HIGH)
            set_px(skin, ox + dx, oy + 3, DRESS_BASE)
            set_px(skin, ox + dx, oy + 6, GOLD_MID if dx % 2 == 1 else DRESS_HIGH)

    paint_slim_arm(40, 16, 40, 32)  # Right arm & right sleeve
    paint_slim_arm(32, 48, 48, 48)  # Left arm & left sleeve

    # =========================================================================
    # 6. LEGS (4x12x4) - Right Leg (0, 16) = Full Black & Gold Gown;
    #    Left Leg (16, 48) = High Slit Bare Leg + Gold Ankle Strap + Black Stiletto Heel!
    # =========================================================================
    # Right Leg Base (0, 16): Full flowing black silk skirt with gold filigree hem & stiletto heel
    fill_rect(skin, 4, 16, 4, 4, DRESS_DEEP)
    fill_rect(skin, 8, 16, 4, 4, DRESS_DEEP)
    shade_dress_rect(skin, 0, 20, 16, 12, seed=7)
    # Gold baroque embroidery near the lower skirt hem (rows y=28..30)
    for dx in range(16):
        if dx % 3 == 0:
            set_px(skin, dx, 28, GOLD_DARK)
            set_px(skin, dx, 29, GOLD_MID)
        if dx % 2 == 0:
            set_px(skin, dx, 30, GOLD_BRIGHT)
        set_px(skin, dx, 31, DRESS_DEEP)
    # Peeking toe & gold ankle strap on front of right foot (x=4..7, y=29..31)
    set_px(skin, 5, 29, GOLD_BRIGHT)
    set_px(skin, 6, 29, GOLD_BRIGHT)
    set_px(skin, 5, 30, SKIN_SHADE)
    set_px(skin, 6, 30, SKIN_SHADE)
    fill_rect(skin, 4, 31, 4, 1, DRESS_DEEP)

    # Right Pants Overlay (0, 32): 3D gown folds & gold hem filigree
    shade_dress_rect(skin, 0, 36, 16, 11, seed=8)
    for dx in range(16):
        set_px(skin, dx, 46, GOLD_MID if dx % 2 == 0 else GOLD_DARK)

    # Left Leg Base (16, 48): Iconic high leg slit revealing bare left leg on Front & Right(inner) faces,
    # draped black gown on Back & Left(outer) faces, gold ankle strap, and black stiletto heel!
    fill_rect(skin, 20, 48, 4, 4, SKIN_SHADE)
    fill_rect(skin, 24, 48, 4, 4, DRESS_DEEP)
    # Start with bare leg across all faces (16..31, 52..63)
    for dy in range(12):
        for dx in range(16):
            set_px(skin, 16 + dx, 52 + dy, SKIN_BASE)
    # Highlight down center of front shin/thigh (x=21, 22)
    for dy in range(10):
        set_px(skin, 20, 52 + dy, SKIN_SHADE)
        set_px(skin, 21, 52 + dy, SKIN_LIGHT)
        set_px(skin, 22, 52 + dy, SKIN_BASE)
        set_px(skin, 23, 52 + dy, SKIN_SHADE)
    # Subtle knee contour at y=56
    set_px(skin, 21, 56, SKIN_SHADE)
    set_px(skin, 22, 56, SKIN_LIGHT)

    # Outer side (24..27) and Back (28..31) of Left Leg are covered by the black gown drape
    shade_dress_rect(skin, 24, 52, 8, 12, seed=9)
    # Diagonal slit edge along outer side of front face (x=23, y=52..55)
    set_px(skin, 23, 52, DRESS_BASE)
    set_px(skin, 22, 52, GOLD_BRIGHT)
    set_px(skin, 23, 53, GOLD_BRIGHT)
    set_px(skin, 23, 54, GOLD_MID)
    for dy in range(12):
        set_px(skin, 24, 52 + dy, GOLD_MID if dy % 2 == 0 else GOLD_DARK)

    # Gold ankle strap (y=61) and black high heel (y=62..63) on Left Leg
    for dx in range(8):
        set_px(skin, 16 + dx, 61, GOLD_BRIGHT if dx % 2 == 0 else GOLD_MID)
    fill_rect(skin, 16, 62, 16, 2, DRESS_DEEP)
    set_px(skin, 21, 62, SKIN_SHADE)
    set_px(skin, 22, 62, SKIN_SHADE)

    # Left Pants Overlay (0, 48): 3D split skirt edge framing the bare leg slit!
    # Outer (8..11, 52..62) and Back (12..15, 52..62) have 3D black & gold skirt panel;
    # Front (4..7, 52..63) has only the top diagonal gold slit border at x=7, y=52..58 so the bare leg shines through!
    shade_dress_rect(skin, 8, 52, 8, 11, seed=10)
    for dy in range(11):
        set_px(skin, 8, 52 + dy, GOLD_BRIGHT if dy % 2 == 0 else GOLD_MID)
    set_px(skin, 6, 52, GOLD_BRIGHT)
    set_px(skin, 7, 52, DRESS_BASE)
    set_px(skin, 7, 53, GOLD_BRIGHT)
    set_px(skin, 7, 54, GOLD_MID)

    return skin, glow


def build_dragon_parts_texture():
    """Builds a 64x64 texture for Emily's 3D voxel Dragon Horns, Articulated Bat Wings, and Dragon Tail."""
    tex = np.zeros((64, 64, 4), dtype=np.uint8)
    # 0..31, 0..31: Obsidian & Gold Dragon Horns + Dragon Tail scales
    for y in range(32):
        for x in range(32):
            ridge = (y + x) % 3 == 0
            c = HORN_RIDGE if ridge else (HORN_BASE if (x + y) % 2 == 0 else HORN_TIP)
            if y in (0, 1, 15, 16):
                c = GOLD_MID if (x % 2 == 0) else GOLD_DARK
            tex[y, x] = c

    # 0..63, 32..63: Bat Wing Bone & Dark-Crimson Veined Wing Membrane
    for y in range(32, 64):
        for x in range(64):
            dy = y - 32
            # Top & left borders are dark wing bone / claw
            if dy < 3 or x < 3 or x in (20, 21, 38, 39):
                tex[y, x] = WING_BONE if (x + dy) % 2 == 0 else HORN_RIDGE
            else:
                # Veined dark crimson membrane
                vein = ((x * 3 + dy * 5) % 11 == 0) or ((x - dy * 2) % 13 == 0)
                if vein:
                    tex[y, x] = WING_MEM_VEIN
                elif (x + dy) % 3 == 0:
                    tex[y, x] = WING_MEM_RED
                else:
                    tex[y, x] = WING_MEM_DARK
    return tex


def render_3d_preview(skin, parts_tex):
    """Renders a crisp 3D front, angled, and back preview of the Minecraft Slim Player NPC with Emily's skin,
    3D overlay layers, curved dragon horns, bat wings, and dragon tail."""
    W, H = 960, 420
    canvas = Image.new("RGBA", (W, H), (30, 34, 44, 255))
    draw = ImageDraw.Draw(canvas)

    def sample_face(tex, u, v, w, h, scale=8):
        sub = tex[v : v + h, u : u + w]
        img = Image.fromarray(sub, "RGBA")
        return img.resize((w * scale, h * scale), Image.NEAREST)

    def composite_face(base_img, over_img):
        out = base_img.copy()
        out.alpha_composite(over_img)
        return out

    S = 9  # 9x pixel scale for crisp preview
    # 1. FRONT VIEW (centered at x=190)
    fx, fy = 190, 60
    # Draw Open Bat Wings behind body
    wing_patch = sample_face(parts_tex, 0, 32, 16, 14, S)
    wing_l = wing_patch.transpose(Image.FLIP_LEFT_RIGHT).rotate(14, expand=True, resample=Image.NEAREST)
    wing_r = wing_patch.rotate(-14, expand=True, resample=Image.NEAREST)
    canvas.alpha_composite(wing_l, (fx - 150, fy + 45))
    canvas.alpha_composite(wing_r, (fx + 10, fy + 45))

    # Draw Curved Voxel Dragon Horns on top of head
    horn_seg = sample_face(parts_tex, 0, 0, 2, 4, S)
    # Left & Right stepped horns
    for step, (dx, dy) in enumerate([(0, 0), (-7, -18), (-2, -34)]):
        canvas.alpha_composite(horn_seg, (fx - 34 + dx, fy - 16 + dy))
        canvas.alpha_composite(horn_seg, (fx + 16 - dx, fy - 16 + dy))

    # Head + Hat
    head_f = composite_face(sample_face(skin, 8, 8, 8, 8, S), sample_face(skin, 40, 8, 8, 8, S))
    canvas.alpha_composite(head_f, (fx - 4 * S, fy))

    # Torso + Jacket
    body_f = composite_face(sample_face(skin, 20, 20, 8, 12, S), sample_face(skin, 20, 36, 8, 12, S))
    canvas.alpha_composite(body_f, (fx - 4 * S, fy + 8 * S))

    # Right Arm (3x12) + Sleeve
    rarm_f = composite_face(sample_face(skin, 44, 20, 3, 12, S), sample_face(skin, 44, 36, 3, 12, S))
    canvas.alpha_composite(rarm_f, (fx - 7 * S, fy + 8 * S))

    # Left Arm (3x12) + Sleeve
    larm_f = composite_face(sample_face(skin, 36, 52, 3, 12, S), sample_face(skin, 52, 52, 3, 12, S))
    canvas.alpha_composite(larm_f, (fx + 4 * S, fy + 8 * S))

    # Right Leg (4x12) + Pants
    rleg_f = composite_face(sample_face(skin, 4, 20, 4, 12, S), sample_face(skin, 4, 36, 4, 12, S))
    canvas.alpha_composite(rleg_f, (fx - 4 * S, fy + 20 * S))

    # Left Leg (4x12) + Pants (Bare leg slit!)
    lleg_f = composite_face(sample_face(skin, 20, 52, 4, 12, S), sample_face(skin, 4, 52, 4, 12, S))
    canvas.alpha_composite(lleg_f, (fx, fy + 20 * S))

    # 2. BACK VIEW (centered at x=510)
    bx, by = 510, 60
    canvas.alpha_composite(wing_l, (bx - 150, by + 45))
    canvas.alpha_composite(wing_r, (bx + 10, by + 45))
    for step, (dx, dy) in enumerate([(0, 0), (-7, -18), (-2, -34)]):
        canvas.alpha_composite(horn_seg, (bx - 34 + dx, by - 16 + dy))
        canvas.alpha_composite(horn_seg, (bx + 16 - dx, by - 16 + dy))

    head_b = composite_face(sample_face(skin, 24, 8, 8, 8, S), sample_face(skin, 56, 8, 8, 8, S))
    canvas.alpha_composite(head_b, (bx - 4 * S, by))

    body_b = composite_face(sample_face(skin, 32, 20, 8, 12, S), sample_face(skin, 32, 36, 8, 12, S))
    canvas.alpha_composite(body_b, (bx - 4 * S, by + 8 * S))

    rarm_b = composite_face(sample_face(skin, 51, 20, 3, 12, S), sample_face(skin, 51, 36, 3, 12, S))
    canvas.alpha_composite(rarm_b, (bx + 4 * S, by + 8 * S))

    larm_b = composite_face(sample_face(skin, 43, 52, 3, 12, S), sample_face(skin, 59, 52, 3, 12, S))
    canvas.alpha_composite(larm_b, (bx - 7 * S, by + 8 * S))

    rleg_b = composite_face(sample_face(skin, 12, 20, 4, 12, S), sample_face(skin, 12, 36, 4, 12, S))
    canvas.alpha_composite(rleg_b, (bx, by + 20 * S))

    lleg_b = composite_face(sample_face(skin, 28, 52, 4, 12, S), sample_face(skin, 12, 52, 4, 12, S))
    canvas.alpha_composite(lleg_b, (bx - 4 * S, by + 20 * S))

    # Articulated Dragon Tail curving down from lower back
    tail_seg = sample_face(parts_tex, 4, 4, 3, 4, S)
    for idx_t, (tx, ty) in enumerate([(0, 0), (8, 26), (20, 52), (34, 76)]):
        canvas.alpha_composite(tail_seg, (bx - 12 + tx, by + 18 * S + ty))

    # 3. RAW 64x64 SKIN TEXTURE SHEET (at x=680, scaled 4x = 256x256)
    checker = Image.new("RGBA", (256, 256), (48, 52, 66, 255))
    skin_4x = Image.fromarray(skin, "RGBA").resize((256, 256), Image.NEAREST)
    checker.alpha_composite(skin_4x)
    canvas.alpha_composite(checker, (680, 80))

    draw.text((135, 16), "Minecraft NPC (Front + Dragon Parts)", fill=(235, 240, 250, 255))
    draw.text((460, 16), "Back View (Wings + Dragon Tail)", fill=(235, 240, 250, 255))
    draw.text((715, 52), "64x64 Slim Skin (emily_skin.png)", fill=(235, 240, 250, 255))
    return canvas


def main():
    os.makedirs(HERE, exist_ok=True)
    skin, glow = build_emily_skin()
    parts_tex = build_dragon_parts_texture()

    skin_img = Image.fromarray(skin, "RGBA")
    glow_img = Image.fromarray(glow, "RGBA")
    parts_img = Image.fromarray(parts_tex, "RGBA")

    skin_path = os.path.join(HERE, "emily_skin.png")
    glow_path = os.path.join(HERE, "emily_skin_glow.png")
    parts_path = os.path.join(HERE, "emily_dragon_parts.png")
    preview_path = os.path.join(HERE, "preview_skin.png")

    skin_img.save(skin_path)
    glow_img.save(glow_path)
    parts_img.save(parts_path)

    preview = render_3d_preview(skin, parts_tex)
    preview.save(preview_path)

    for dst_dir in [ENTITY_TEX_DIR, DEV_ENTITY_TEX_DIR]:
        if os.path.exists(os.path.dirname(dst_dir)):
            os.makedirs(dst_dir, exist_ok=True)
            shutil.copy2(skin_path, os.path.join(dst_dir, "emily_skin.png"))
            shutil.copy2(glow_path, os.path.join(dst_dir, "emily_skin_glow.png"))
            shutil.copy2(parts_path, os.path.join(dst_dir, "emily_dragon_parts.png"))
            print(f"Installed Emily skin & dragon textures to {dst_dir}")


if __name__ == "__main__":
    main()

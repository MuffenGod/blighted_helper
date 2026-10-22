"""Build multi-directional 2.5D depth-layered standee textures from /segments + GROK_SHEET.jpg + views2/.

Uses the user's exact cutouts in /segments (mapped via segments_map.json) across all turnaround views:
  - front : tail (z=-0.10), wing_l (z=-0.07), wing_r (z=-0.07), body (z=0.0, unified body+dress+heels),
            hair (z=+0.012), glow (z=+0.005, emissive eyes/gems/gold)
  - left  : tail (z=-0.06), body (z=0.0, unified side body+dress+heels), hair (z=+0.012), horns (z=+0.018)
  - right : mirrored left stack
  - back  : body_dress (z=0.0), wing_l (z=+0.05), wing_r (z=+0.05), hair (z=+0.07), tail (z=+0.09)  [open wings]
  - rear  : body_dress (z=0.0), wings_folded (z=+0.04), hair (z=+0.06), tail (z=+0.08)              [folded wings]

All views are normalized to a shared 1280x1536 canvas with spine centered at u=0.50 and heels/hem grounded at v=1.0,
de-fringed against the sheet's gray background, with clean inward root underlaps strictly inside eroded opaque body
masks so parallax & wing/hair/tail motion never open holes or expose filler blocks.
"""
import json
import os
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage as ndi

HERE = os.path.dirname(os.path.abspath(__file__))
ART_DIR = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(ART_DIR, "..", ".."))
SEG_DIR = os.path.join(ROOT, "segments")
VIEWS2_DIR = os.path.join(ART_DIR, "views2")

OUT_W, OUT_H = 1280, 1536
ASPECT = round(OUT_W / OUT_H, 4)

sheet_img = Image.open(os.path.join(ART_DIR, "GROK_SHEET.jpg")).convert("RGB")
sa = np.asarray(sheet_img).astype(np.int16)
SHEET_H, SHEET_W = sa.shape[:2]

# Exact background detection (using segments/gray-background.png + color distance to gray background)
b_border = np.concatenate([sa[:8].reshape(-1, 3), sa[-8:].reshape(-1, 3), sa[:, :8].reshape(-1, 3), sa[:, -8:].reshape(-1, 3)])
bg_color = np.median(b_border, axis=0)  # ~(128, 128, 128)
gray_bg_rgba = np.asarray(Image.open(os.path.join(SEG_DIR, "gray-background.png")).convert("RGBA"))
is_gray_color = (np.abs(sa - bg_color).max(axis=2) < 16) & ((sa.max(axis=2) - sa.min(axis=2)) < 14)
fg = (~is_gray_color) & ((gray_bg_rgba[..., 3] < 200) | (np.abs(sa - bg_color).sum(axis=2) > 48))
# Only fill tiny pinholes (< 60 px), NEVER large background gaps between tail and dress!
holes = (~fg)
hole_lab, n_holes = ndi.label(holes)
if n_holes > 0:
    hole_sizes = ndi.sum(holes, hole_lab, range(1, n_holes + 1))
    tiny_holes = np.isin(hole_lab, [k + 1 for k, s in enumerate(hole_sizes) if s < 60])
    fg = fg | tiny_holes

seg_map = json.load(open(os.path.join(ART_DIR, "segments_map.json"), encoding="utf-8"))
FIG_SEGS = {
    "closeup": ["blonde-hair/blonde-hair-3.png", "gold-jewelry/gold-jewelry-1.png", "green-gem/green-gem-1.png"],
    "front": [
        "bat-wings/bat-wings-2.png",
        "black-dress/black-dress-1.png",
        "blonde-hair/blonde-hair-4.png",
        "gold-jewelry/gold-jewelry-2.png",
        "green-gem/green-gem-2.png",
        "high-heels/high-heels-2.png",
        "high-heels/high-heels-3.png",
    ],
    "side": [
        "black-dress/black-dress-2.png",
        "blonde-hair/blonde-hair-1.png",
        "black-horns.png",
        "high-heels/high-heels-1.png",
        "high-heels/high-heels-4.png",
        "gold-jewelry/gold-jewelry-3.png",
    ],
    "back": [
        "bat-wings/bat-wings-1.png",
        "black-dress/black-dress-4.png",
        "blonde-hair/blonde-hair-5.png",
    ],
    "rear": [
        "bat-wings/bat-wings-3.png",
        "bat-wings/bat-wings-4.png",
        "black-dress/black-dress-3.png",
        "blonde-hair/blonde-hair-2.png",
    ],
}


def load_seg_mask_on_sheet(rel_path, thresh=96):
    """Returns a boolean mask on the (752, 1376) sheet for a segment cutout."""
    info = seg_map[rel_path]
    im = np.asarray(Image.open(os.path.join(SEG_DIR, rel_path)).convert("RGBA"))
    m = np.zeros((SHEET_H, SHEET_W), dtype=bool)
    x, y, w, h = info["x"], info["y"], info["w"], info["h"]
    m[y : y + h, x : x + w] = im[..., 3] >= thresh
    return m


# Assign every foreground pixel on the sheet to one of the 5 figures
fig_names = list(FIG_SEGS.keys())
labels = np.zeros((SHEET_H, SHEET_W), np.int32)
for idx_fig, fname in enumerate(fig_names, 1):
    for rel in FIG_SEGS[fname]:
        labels[load_seg_mask_on_sheet(rel, 128)] = idx_fig

ci = fig_names.index("closeup") + 1
labels[0:268, 0:345][labels[0:268, 0:345] == 0] = ci
labels[268:340, 0:222][labels[268:340, 0:222] == 0] = ci
labels[268:292, 306:345][labels[268:292, 306:345] == 0] = ci

# Seed the side view's dragon tail (which sweeps from x=980..1195, y=300..605) so it belongs to 'side' and not 'rear'
side_idx = fig_names.index("side") + 1
rear_dress = load_seg_mask_on_sheet("black-dress/black-dress-3.png", 64)
rear_wings = load_seg_mask_on_sheet("bat-wings/bat-wings-3.png", 64) | load_seg_mask_on_sheet("bat-wings/bat-wings-4.png", 64)
side_tail_box = np.zeros((SHEET_H, SHEET_W), dtype=bool)
side_tail_box[290:615, 960:1205] = True
side_tail_seed = fg & side_tail_box & (~rear_dress) & (~rear_wings)
# Keep only the connected component attached to the side figure's hip (around x=995, y=350)
st_lab, st_n = ndi.label(side_tail_seed)
if st_n > 0:
    hip_lbl = st_lab[355, 995]
    if hip_lbl > 0:
        labels[st_lab == hip_lbl] = side_idx

_, nearest_idx = ndi.distance_transform_edt(labels == 0, return_indices=True)
assign = np.where(fg, labels[nearest_idx[0], nearest_idx[1]], 0)


def get_fig_crop_and_mask(fig_name):
    i = fig_names.index(fig_name) + 1
    m = assign == i
    lab, n = ndi.label(ndi.binary_dilation(m, iterations=1))
    if n > 1:
        sz = ndi.sum(m, lab, range(1, n + 1))
        keep = np.isin(lab, [k + 1 for k, s in enumerate(sz) if s > 0.04 * max(sz)])
        m = m & keep
    if fig_name == "back":
        lum = sa.sum(axis=2) / 3.0
        rb = sa[..., 0] - sa[..., 2]
        junk = np.zeros_like(m)
        junk[262:312, 222:312] = True
        m = m & ~(junk & (lum > 62) & (rb < 38))
        m = ndi.binary_opening(m, iterations=1) | (m & ndi.binary_dilation(ndi.binary_opening(m, iterations=2), iterations=3))
    ys, xs = np.where(m)
    pad = 14
    x0, x1 = int(max(0, xs.min() - pad)), int(min(SHEET_W, xs.max() + pad))
    y0, y1 = int(max(0, ys.min() - pad)), int(min(SHEET_H, ys.max() + pad))
    return (x0, y0, x1, y1), m


# Load 4x-AnimeSharp once for any view that needs fresh clean upscaling
_UPSCALER = None


def upscale_crop_4x(crop_rgb):
    global _UPSCALER
    try:
        import spandrel
        import torch

        device = "cuda" if torch.cuda.is_available() else "cpu"
        if _UPSCALER is None:
            model_path = "C:/ComfyUI_windows_portable/ComfyUI/models/upscale_models/4x-AnimeSharp.pth"
            _UPSCALER = spandrel.ModelLoader().load_from_file(model_path).eval().to(device)
        arr = np.asarray(crop_rgb).astype(np.float32) / 255.0
        t = torch.from_numpy(arr).permute(2, 0, 1).unsqueeze(0).to(device)
        with torch.no_grad():
            out_t = _UPSCALER(t).squeeze(0).permute(1, 2, 0).clamp(0, 1).cpu().numpy()
        return Image.fromarray((out_t * 255.0 + 0.5).astype(np.uint8), "RGB")
    except Exception as e:
        print("Fallback Lanczos:", e)
        return crop_rgb.resize((crop_rgb.width * 4, crop_rgb.height * 4), Image.LANCZOS)


def build_clean_upscaled_view(fig_name):
    """Crops fig_name from GROK_SHEET.jpg, upscales RGB with 4x-AnimeSharp, and builds a de-fringed RGBA image."""
    (x0, y0, x1, y1), m = get_fig_crop_and_mask(fig_name)
    crop_rgb = sheet_img.crop((x0, y0, x1, y1))
    big_rgb = upscale_crop_4x(crop_rgb)
    th = 2048
    sc = th / big_rgb.height
    big_rgb = big_rgb.resize((round(big_rgb.width * sc), th), Image.LANCZOS)

    # Upscale clean mask and strip any gray background fringe along the outer edge
    mcrop = Image.fromarray((m[y0:y1, x0:x1] * 255).astype(np.uint8))
    mbig = np.asarray(mcrop.resize(big_rgb.size, Image.BICUBIC)).astype(np.float32) / 255.0
    rgb_arr = np.asarray(big_rgb).astype(np.float32)

    # Distance to neutral sheet gray (~128,128,128)
    gray_dist = np.abs(rgb_arr - bg_color[None, None, :]).max(axis=2)
    sat = rgb_arr.max(axis=2) - rgb_arr.min(axis=2)
    is_bg_fringe = (gray_dist < 22.0) & (sat < 16.0)

    binary_m = (mbig >= 0.52) & (~is_bg_fringe)
    # Remove any tiny isolated specks (< 120 px)
    lab, n = ndi.label(binary_m)
    if n > 0:
        sz = ndi.sum(binary_m, lab, range(1, n + 1))
        binary_m = np.isin(lab, [k + 1 for k, s in enumerate(sz) if s >= 120])
    # Erode 1px and feather 0.8px for crisp anti-aliased anime silhouette without gray halo
    clean_alpha = ndi.gaussian_filter(ndi.binary_erosion(binary_m, iterations=1).astype(np.float32), sigma=0.75)
    clean_alpha = np.clip((clean_alpha - 0.15) / 0.85, 0.0, 1.0)

    rgba = np.zeros((big_rgb.height, big_rgb.width, 4), dtype=np.uint8)
    rgba[..., :3] = np.asarray(big_rgb)
    rgba[..., 3] = (clean_alpha * 255.0 + 0.5).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA"), (x0, y0, x1, y1)


def map_sheet_mask_to_view(sheet_mask, crop_box, view_size, dilate_px=0):
    x0, y0, x1, y1 = crop_box
    sub = sheet_mask[y0:y1, x0:x1]
    if dilate_px > 0:
        sub = ndi.binary_dilation(sub, iterations=dilate_px)
    elif dilate_px < 0:
        sub = ndi.binary_erosion(sub, iterations=-dilate_px)
    im = Image.fromarray((sub * 255).astype(np.uint8))
    im_up = im.resize(view_size, Image.BICUBIC)
    return np.asarray(im_up) >= 115


def normalize_view_canvas(view_rgba, masks_dict, spine_x_frac=None):
    """Places a high-res view_rgba and all its masks onto the unified (OUT_W, OUT_H) canvas
    with heels/hem grounded at y = OUT_H - 4, horn tips at y = 12, and spine centered at x = OUT_W // 2."""
    arr = np.asarray(view_rgba)
    alpha = arr[..., 3] > 32
    ys, xs = np.where(alpha)
    y_min, y_max = int(ys.min()), int(ys.max())
    fig_h = y_max - y_min + 1

    if spine_x_frac is None:
        band = (ys >= y_min + int(0.06 * fig_h)) & (ys <= y_min + int(0.32 * fig_h))
        cx_src = float(np.median(xs[band]))
    else:
        cx_src = spine_x_frac * arr.shape[1]

    target_h = OUT_H - 16
    scale = target_h / float(fig_h)
    new_w = max(1, round(arr.shape[1] * scale))
    new_h = max(1, round(arr.shape[0] * scale))

    resized_rgba = view_rgba.resize((new_w, new_h), Image.LANCZOS)
    cx_scaled = round(cx_src * scale)
    y_min_scaled = round(y_min * scale)

    paste_x = (OUT_W // 2) - cx_scaled
    paste_y = 12 - y_min_scaled

    canvas_rgba = Image.new("RGBA", (OUT_W, OUT_H), (0, 0, 0, 0))
    canvas_rgba.paste(resized_rgba, (paste_x, paste_y))

    out_masks = {}
    for k, m in masks_dict.items():
        m_img = Image.fromarray((m.astype(np.float32) * 255.0).clip(0, 255).astype(np.uint8))
        m_res = m_img.resize((new_w, new_h), Image.BILINEAR)
        m_can = Image.new("L", (OUT_W, OUT_H), 0)
        m_can.paste(m_res, (paste_x, paste_y))
        out_masks[k] = np.array(m_can, dtype=np.float32) / 255.0

    return np.array(canvas_rgba, copy=True), out_masks


def inpaint_underlap(layer_rgba, target_mask, occluder_mask, radius=22):
    """Extends a background layer's colors inward under an opaque foreground occluder (strictly inside
    an eroded interior of occluder_mask) so parallax or wing/tail motion never opens a gap."""
    out = layer_rgba.copy()
    has_px = (out[..., 3] > 32) & target_mask
    if not has_px.any():
        return out
    safe_occluder = ndi.binary_erosion(occluder_mask, iterations=12)
    underlap_zone = ndi.binary_dilation(has_px, iterations=radius) & safe_occluder & (~has_px)
    if not underlap_zone.any():
        return out
    dist, idx = ndi.distance_transform_edt(~has_px, return_indices=True)
    nearest_rgb = out[idx[0], idx[1], :3].astype(np.float32)
    for c in range(3):
        nearest_rgb[..., c] = ndi.gaussian_filter(nearest_rgb[..., c], sigma=3.0)
    falloff = np.clip(1.0 - (dist / float(radius + 2)), 0.0, 1.0)
    out[underlap_zone, :3] = nearest_rgb[underlap_zone].astype(np.uint8)
    out[underlap_zone, 3] = (falloff[underlap_zone] * 255.0).astype(np.uint8)
    return out


def extract_layer(base_rgba, mask_float, underlap_occluder=None, underlap_radius=20):
    out = np.zeros_like(base_rgba)
    out[..., :3] = base_rgba[..., :3]
    alpha = (base_rgba[..., 3].astype(np.float32) / 255.0) * np.clip(mask_float, 0.0, 1.0)
    out[..., 3] = (alpha * 255.0 + 0.5).astype(np.uint8)
    if underlap_occluder is not None and underlap_radius > 0:
        out = inpaint_underlap(out, mask_float > 0.35, underlap_occluder, radius=underlap_radius)
    return out


def bleed_and_save(arr, rel_path):
    """Edge-bleeds RGB into transparent pixels so bilinear filtering in OpenGL never produces dark halos."""
    a = arr.copy()
    opaque = a[:, :, 3] > 8
    if opaque.any():
        idx = ndi.distance_transform_edt(~opaque, return_distances=False, return_indices=True)
        a[~opaque, :3] = a[idx[0], idx[1]][~opaque, :3]
    full_path = os.path.join(HERE, rel_path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    img = Image.fromarray(a, "RGBA")
    img.save(full_path)
    return img


def soft_mask(binary_mask, blur_sigma=0.75):
    return ndi.gaussian_filter(binary_mask.astype(np.float32), sigma=blur_sigma)


xx = np.arange(OUT_W)[None, :]
yy = np.arange(OUT_H)[:, None]

# ==============================================================================
# 1. BUILD FRONT VIEW LAYERS
# ==============================================================================
print("Building clean FRONT layers...")
front_src, front_crop = build_clean_upscaled_view("front")
fsize = front_src.size

m_wings_f = map_sheet_mask_to_view(load_seg_mask_on_sheet("bat-wings/bat-wings-2.png"), front_crop, fsize, dilate_px=1)
m_dress_f = map_sheet_mask_to_view(load_seg_mask_on_sheet("black-dress/black-dress-1.png"), front_crop, fsize, dilate_px=1)
m_hair_f = map_sheet_mask_to_view(load_seg_mask_on_sheet("blonde-hair/blonde-hair-4.png"), front_crop, fsize, dilate_px=1)
m_heels_f = map_sheet_mask_to_view(
    load_seg_mask_on_sheet("high-heels/high-heels-2.png") | load_seg_mask_on_sheet("high-heels/high-heels-3.png"),
    front_crop,
    fsize,
    dilate_px=1,
)
m_jewel_f = map_sheet_mask_to_view(load_seg_mask_on_sheet("gold-jewelry/gold-jewelry-2.png"), front_crop, fsize, dilate_px=1)
m_gem_f = map_sheet_mask_to_view(load_seg_mask_on_sheet("green-gem/green-gem-2.png"), front_crop, fsize, dilate_px=1)

front_rgba, f_masks = normalize_view_canvas(
    front_src,
    {
        "wings": m_wings_f,
        "dress": m_dress_f | m_heels_f,
        "hair": m_hair_f,
        "jewel": m_jewel_f,
        "gem": m_gem_f,
    },
    spine_x_frac=0.501,
)

f_opaq = front_rgba[..., 3] > 24
f_dress_b = (f_masks["dress"] > 0.35) & f_opaq
f_hair_b = (f_masks["hair"] > 0.35) & f_opaq

# Keep her entire forearms & black gloves attached to the core body (never split onto the wings)
t_arm = np.clip((yy - 365.0) / (755.0 - 365.0), 0.0, 1.0)
arm_l_cx = 508.0 + t_arm * (430.0 - 508.0)
arm_r_cx = 772.0 + t_arm * (850.0 - 772.0)
arm_corridor = (yy >= 365) & (yy <= 755) & ((np.abs(xx - arm_l_cx) <= 38.0) | (np.abs(xx - arm_r_cx) <= 38.0))

f_wings_b = (f_masks["wings"] > 0.35) & f_opaq & (~f_dress_b) & (~arm_corridor)
# Remove any small disconnected glove specks left near the forearms (y > 520)
for side_mask_attr in [xx < OUT_W // 2, xx >= OUT_W // 2]:
    sub_w = f_wings_b & side_mask_attr
    lw, nw = ndi.label(sub_w)
    if nw > 1:
        sz_w = ndi.sum(sub_w, lw, range(1, nw + 1))
        main_lbl = int(np.argmax(sz_w)) + 1
        # Keep main outer wing plus inner shoulder web (y <= 525)
        keep_w = (lw == main_lbl) | (sub_w & (yy <= 525))
        f_wings_b = (f_wings_b & (~side_mask_attr)) | keep_w

f_wing_l_b = f_wings_b & (xx < OUT_W // 2)
f_wing_r_b = f_wings_b & (xx >= OUT_W // 2)

# Dragon tail in front view: strictly outside the horizontal [min_x, max_x] span of the central skirt on each row!
inside_dress_span = np.zeros((OUT_H, OUT_W), dtype=bool)
for y_row in range(OUT_H):
    d_xs = np.where(f_dress_b[y_row])[0]
    if len(d_xs) > 0:
        inside_dress_span[y_row, d_xs.min() : d_xs.max() + 1] = True

unclaimed_lower = f_opaq & (~f_wings_b) & (~f_dress_b) & (~arm_corridor) & (~f_hair_b) & (yy > 640)
f_tail_b = unclaimed_lower & (~inside_dress_span)
lab_t, nt = ndi.label(f_tail_b)
if nt > 0:
    sz_t = ndi.sum(f_tail_b, lab_t, range(1, nt + 1))
    f_tail_b = np.isin(lab_t, [k + 1 for k, s in enumerate(sz_t) if s > 150])

# Unified core body (head, horns, skin, arms, bare leg in slit, dress, heels, and hair base)
f_core_b = f_opaq & (~f_wings_b) & (~f_tail_b)

# Emissive glow accents (emerald eyes, forehead gem, chest pendant gem, gold anklet)
r_ch = front_rgba[..., 0].astype(np.float32)
g_ch = front_rgba[..., 1].astype(np.float32)
eye_glow = (yy >= 160) & (yy <= 240) & (xx >= (OUT_W // 2 - 60)) & (xx <= (OUT_W // 2 + 60)) & ((g_ch - r_ch) > 14) & (g_ch > 75)
chest_gem = (yy >= 285) & (yy <= 380) & (xx >= (OUT_W // 2 - 28)) & (xx <= (OUT_W // 2 + 28)) & ((g_ch - r_ch) > 8) & (g_ch > 45)
f_glow_b = ((f_masks["gem"] > 0.25) | (f_masks["jewel"] > 0.35) | eye_glow | chest_gem) & f_core_b

front_tail = extract_layer(front_rgba, soft_mask(f_tail_b, 0.75), underlap_occluder=f_core_b, underlap_radius=24)
front_wing_l = extract_layer(front_rgba, soft_mask(f_wing_l_b, 0.75), underlap_occluder=f_core_b & (~arm_corridor), underlap_radius=28)
front_wing_r = extract_layer(front_rgba, soft_mask(f_wing_r_b, 0.75), underlap_occluder=f_core_b & (~arm_corridor), underlap_radius=28)

# Bridge the upper-arm wing membrane (y=365..520) across arm_corridor so parallax never shows an arm-shaped slot
for y_row in range(365, 522):
    # Left wing bridge
    w_xs_l = np.where(front_wing_l[y_row, : OUT_W // 2, 3] > 180)[0]
    if len(w_xs_l) > 20:
        x_min_l, x_max_l = int(w_xs_l.min()), int(w_xs_l.max())
        gap_xs = [x for x in range(x_min_l, x_max_l + 1) if front_wing_l[y_row, x, 3] < 220]
        if gap_xs:
            src_x = max(x_min_l, gap_xs[0] - 8)
            for gx in gap_xs:
                front_wing_l[y_row, gx, :3] = front_wing_l[y_row, max(x_min_l, src_x - (gx % 10)), :3]
                front_wing_l[y_row, gx, 3] = 255
    # Right wing bridge
    w_xs_r = np.where(front_wing_r[y_row, OUT_W // 2 :, 3] > 180)[0] + (OUT_W // 2)
    if len(w_xs_r) > 20:
        x_min_r, x_max_r = int(w_xs_r.min()), int(w_xs_r.max())
        gap_xs = [x for x in range(x_min_r, x_max_r + 1) if front_wing_r[y_row, x, 3] < 220]
        if gap_xs:
            src_x = min(x_max_r, gap_xs[-1] + 8)
            for gx in gap_xs:
                front_wing_r[y_row, gx, :3] = front_wing_r[y_row, min(x_max_r, src_x + (gx % 10)), :3]
                front_wing_r[y_row, gx, 3] = 255

front_body = extract_layer(front_rgba, soft_mask(f_core_b, 0.75))
front_hair = extract_layer(front_rgba, soft_mask(f_hair_b, 0.75))
front_glow = extract_layer(front_rgba, soft_mask(f_glow_b, 0.6))

bleed_and_save(front_tail, "front/tail.png")
bleed_and_save(front_wing_l, "front/wing_l.png")
bleed_and_save(front_wing_r, "front/wing_r.png")
bleed_and_save(front_body, "front/body.png")
bleed_and_save(front_hair, "front/hair.png")
bleed_and_save(front_glow, "front/glow.png")

bleed_and_save(front_wing_l, "wing_l.png")
bleed_and_save(front_wing_r, "wing_r.png")
bleed_and_save(front_body, "body.png")

# ==============================================================================
# 2. BUILD LEFT & RIGHT SIDE VIEW LAYERS
# ==============================================================================
print("Building clean LEFT & RIGHT side layers...")
left_src, side_crop = build_clean_upscaled_view("side")
lsize = left_src.size

m_dress_s = map_sheet_mask_to_view(load_seg_mask_on_sheet("black-dress/black-dress-2.png"), side_crop, lsize, dilate_px=1)
m_hair_s = map_sheet_mask_to_view(load_seg_mask_on_sheet("blonde-hair/blonde-hair-1.png"), side_crop, lsize, dilate_px=1)
m_horns_s = map_sheet_mask_to_view(load_seg_mask_on_sheet("black-horns.png"), side_crop, lsize, dilate_px=1)
m_heels_s = map_sheet_mask_to_view(
    load_seg_mask_to_view_raw := (load_seg_mask_on_sheet("high-heels/high-heels-1.png") | load_seg_mask_on_sheet("high-heels/high-heels-4.png")),
    side_crop,
    lsize,
    dilate_px=1,
)
m_jewel_s = map_sheet_mask_to_view(load_seg_mask_on_sheet("gold-jewelry/gold-jewelry-3.png"), side_crop, lsize, dilate_px=1)

left_rgba, l_masks = normalize_view_canvas(
    left_src,
    {
        "dress": m_dress_s | m_heels_s | m_jewel_s,
        "hair": m_hair_s,
        "horns": m_horns_s,
    },
    spine_x_frac=0.36,
)

l_opaq = left_rgba[..., 3] > 24
l_horns_b = (l_masks["horns"] > 0.35) & l_opaq
l_hair_b = (l_masks["hair"] > 0.35) & l_opaq & (~l_horns_b)
l_dress_b = (l_masks["dress"] > 0.35) & l_opaq

# Dragon tail in side profile: pixels to the right of her dress (x > OUT_W//2 + 25, y > 510)
l_unclaimed = l_opaq & (~l_horns_b) & (~l_hair_b) & (~l_dress_b)
l_tail_b = l_unclaimed & (xx > (OUT_W // 2 + 25)) & (yy > 510)
lab_lt, nlt = ndi.label(l_tail_b)
if nlt > 0:
    sz_lt = ndi.sum(l_tail_b, lab_lt, range(1, nlt + 1))
    l_tail_b = np.isin(lab_lt, [k + 1 for k, s in enumerate(sz_lt) if s > 200])

l_core_b = l_opaq & (~l_tail_b)

left_tail = extract_layer(left_rgba, soft_mask(l_tail_b, 0.75), underlap_occluder=l_core_b, underlap_radius=22)
left_body = extract_layer(left_rgba, soft_mask(l_core_b, 0.75))
left_hair = extract_layer(left_rgba, soft_mask(l_hair_b, 0.75))
left_horns = extract_layer(left_rgba, soft_mask(l_horns_b, 0.75))

for name, arr in [("tail", left_tail), ("body", left_body), ("hair", left_hair), ("horns", left_horns)]:
    bleed_and_save(arr, f"left/{name}.png")
    bleed_and_save(np.ascontiguousarray(arr[:, ::-1, :]), f"right/{name}.png")

# ==============================================================================
# 3. BUILD BACK VIEW (OPEN WINGS) LAYERS
# ==============================================================================
print("Building clean BACK (open wings) layers...")
back_src, back_crop = build_clean_upscaled_view("back")
bsize = back_src.size

m_wings_b = map_sheet_mask_to_view(load_seg_mask_on_sheet("bat-wings/bat-wings-1.png"), back_crop, bsize, dilate_px=1)
m_dress_b = map_sheet_mask_to_view(load_seg_mask_on_sheet("black-dress/black-dress-4.png"), back_crop, bsize, dilate_px=1)
m_hair_b = map_sheet_mask_to_view(load_seg_mask_on_sheet("blonde-hair/blonde-hair-5.png"), back_crop, bsize, dilate_px=1)

back_rgba, b_masks = normalize_view_canvas(
    back_src,
    {
        "wings": m_wings_b,
        "dress": m_dress_b,
        "hair": m_hair_b,
    },
    spine_x_frac=0.512,
)

b_opaq = back_rgba[..., 3] > 24
b_wings_m = (b_masks["wings"] > 0.35) & b_opaq
b_hair_m = (b_masks["hair"] > 0.35) & b_opaq
b_dress_m = (b_masks["dress"] > 0.35) & b_opaq & (~b_hair_m) & (~b_wings_m)

b_wing_l_m = b_wings_m & (xx < OUT_W // 2)
b_wing_r_m = b_wings_m & (xx >= OUT_W // 2)

b_unclaimed = b_opaq & (~b_wings_m) & (~b_hair_m) & (~b_dress_m)
b_tail_m = b_unclaimed & (yy > 520) & (xx > (OUT_W // 2 - 120)) & (xx < (OUT_W // 2 + 190))
b_core_m = b_opaq & (~b_wings_m)

back_body_dress = extract_layer(back_rgba, soft_mask(b_core_m, 0.75))
back_wing_l = extract_layer(back_rgba, soft_mask(b_wing_l_m, 0.75), underlap_occluder=b_core_m, underlap_radius=20)
back_wing_r = extract_layer(back_rgba, soft_mask(b_wing_r_m, 0.75), underlap_occluder=b_core_m, underlap_radius=20)
back_hair = extract_layer(back_rgba, soft_mask(b_hair_m, 0.75))
back_tail = extract_layer(back_rgba, soft_mask(b_tail_m, 0.75))

bleed_and_save(back_body_dress, "back/body_dress.png")
bleed_and_save(back_wing_l, "back/wing_l.png")
bleed_and_save(back_wing_r, "back/wing_r.png")
bleed_and_save(back_hair, "back/hair.png")
bleed_and_save(back_tail, "back/tail.png")

# ==============================================================================
# 4. BUILD REAR VIEW (FOLDED WINGS) LAYERS
# ==============================================================================
print("Building clean REAR (folded wings) layers...")
rear_src, rear_crop = build_clean_upscaled_view("rear")
rsize = rear_src.size

m_wings_r = map_sheet_mask_to_view(
    load_seg_mask_on_sheet("bat-wings/bat-wings-3.png") | load_seg_mask_on_sheet("bat-wings/bat-wings-4.png"),
    rear_crop,
    rsize,
    dilate_px=1,
)
m_dress_r = map_sheet_mask_to_view(load_seg_mask_on_sheet("black-dress/black-dress-3.png"), rear_crop, rsize, dilate_px=1)
m_hair_r = map_sheet_mask_to_view(load_seg_mask_on_sheet("blonde-hair/blonde-hair-2.png"), rear_crop, rsize, dilate_px=1)

rear_rgba, r_masks = normalize_view_canvas(
    rear_src,
    {
        "wings": m_wings_r,
        "dress": m_dress_r,
        "hair": m_hair_r,
    },
    spine_x_frac=0.485,
)

# Repair the small notch on the lower-left skirt (y=950..1250, x < OUT_W//2) where the side view's tail tip crossed over on the sheet
dress_left_top = np.where((r_masks["dress"][945, : OUT_W // 2] > 0.5) & (rear_rgba[945, : OUT_W // 2, 3] > 180))[0]
dress_left_bot = np.where((r_masks["dress"][1255, : OUT_W // 2] > 0.5) & (rear_rgba[1255, : OUT_W // 2, 3] > 180))[0]
if len(dress_left_top) > 0 and len(dress_left_bot) > 0:
    x_top = float(dress_left_top.min())
    x_bot = float(dress_left_bot.min())
    for y_row in range(948, 1254):
        t_row = (y_row - 945.0) / (1255.0 - 945.0)
        x_edge = int(round(x_top + t_row * (x_bot - x_top)))
        # Clear any stray tail specks outside the smooth skirt edge
        rear_rgba[y_row, :x_edge, 3] = 0
        r_masks["dress"][y_row, :x_edge] = 0.0
        # Fill any transparent or non-dress gap inside [x_edge, OUT_W // 2) from clean interior dress fabric
        for x_col in range(x_edge, OUT_W // 2):
            if rear_rgba[y_row, x_col, 3] < 240 or r_masks["dress"][y_row, x_col] < 0.5:
                src_x = (OUT_W // 2) - 35 - ((x_col - x_edge) % 20)
                rear_rgba[y_row, x_col, :3] = rear_rgba[y_row, src_x, :3]
                rear_rgba[y_row, x_col, 3] = 255
                r_masks["dress"][y_row, x_col] = 1.0

r_opaq = rear_rgba[..., 3] > 24
r_wings_m = (r_masks["wings"] > 0.35) & r_opaq
r_hair_m = (r_masks["hair"] > 0.35) & r_opaq
r_dress_m = (r_masks["dress"] > 0.35) & r_opaq & (~r_hair_m) & (~r_wings_m)
r_unclaimed = r_opaq & (~r_wings_m) & (~r_hair_m) & (~r_dress_m)
r_tail_m = r_unclaimed & (yy > 500)
r_core_m = r_opaq & (~r_wings_m)

rear_body_dress = extract_layer(rear_rgba, soft_mask(r_core_m, 0.75))
rear_wings = extract_layer(rear_rgba, soft_mask(r_wings_m, 0.75), underlap_occluder=r_core_m, underlap_radius=18)
rear_hair = extract_layer(rear_rgba, soft_mask(r_hair_m, 0.75))
rear_tail = extract_layer(rear_rgba, soft_mask(r_tail_m, 0.75))

# Also ensure back_body_dress has the complete symmetric horn tips from rear_body_dress (y < 225)
horn_patch = (yy < 225) & (rear_body_dress[..., 3] > back_body_dress[..., 3]) & (~b_hair_m)
back_body_dress[horn_patch] = rear_body_dress[horn_patch]
bleed_and_save(back_body_dress, "back/body_dress.png")

bleed_and_save(rear_body_dress, "rear/body_dress.png")
bleed_and_save(rear_wings, "rear/wings_folded.png")
bleed_and_save(rear_hair, "rear/hair.png")
bleed_and_save(rear_tail, "rear/tail.png")

# ==============================================================================
# 5. WRITE MULTI-DIRECTIONAL layers.json + PREVIEWS
# ==============================================================================
TEX = "blighted_enhancements:textures/entity/emily_standee/"

FRONT_LAYERS = [
    {"id": "tail", "texture": TEX + "front/tail.png", "z": -0.10, "pivot": [0.52, 0.52], "flap": {"deg": 2.6, "hz": 0.34, "phase": 0.6}},
    {"id": "wing_l", "texture": TEX + "front/wing_l.png", "z": -0.07, "pivot": [0.39, 0.25], "flap": {"deg": 4.5, "hz": 0.42, "phase": 0.0}},
    {"id": "wing_r", "texture": TEX + "front/wing_r.png", "z": -0.07, "pivot": [0.61, 0.25], "flap": {"deg": -4.5, "hz": 0.42, "phase": 0.3}},
    {"id": "body", "texture": TEX + "front/body.png", "z": 0.00, "breathe": 0.004},
    {"id": "glow", "texture": TEX + "front/glow.png", "z": 0.004, "breathe": 0.004, "emissive": True},
    {"id": "hair", "texture": TEX + "front/hair.png", "z": 0.012, "pivot": [0.50, 0.08], "flap": {"deg": 1.1, "hz": 0.28, "phase": 0.2}},
]

LAYERS_JSON = {
    "aspect": ASPECT,
    "layers": FRONT_LAYERS,
    "views": {
        "front": FRONT_LAYERS,
        "left": [
            {"id": "tail", "texture": TEX + "left/tail.png", "z": -0.05, "pivot": [0.53, 0.44], "flap": {"deg": 3.0, "hz": 0.35, "phase": 0.0}},
            {"id": "body", "texture": TEX + "left/body.png", "z": 0.00, "breathe": 0.004},
            {"id": "hair", "texture": TEX + "left/hair.png", "z": 0.012, "pivot": [0.50, 0.08], "flap": {"deg": 1.4, "hz": 0.28, "phase": 0.1}},
            {"id": "horns", "texture": TEX + "left/horns.png", "z": 0.018},
        ],
        "right": [
            {"id": "tail", "texture": TEX + "right/tail.png", "z": -0.05, "pivot": [0.47, 0.44], "flap": {"deg": -3.0, "hz": 0.35, "phase": 0.0}},
            {"id": "body", "texture": TEX + "right/body.png", "z": 0.00, "breathe": 0.004},
            {"id": "hair", "texture": TEX + "right/hair.png", "z": 0.012, "pivot": [0.50, 0.08], "flap": {"deg": -1.4, "hz": 0.28, "phase": 0.1}},
            {"id": "horns", "texture": TEX + "right/horns.png", "z": 0.018},
        ],
        "back": [
            {"id": "body_dress", "texture": TEX + "back/body_dress.png", "z": 0.00, "breathe": 0.003},
            {"id": "wing_l", "texture": TEX + "back/wing_l.png", "z": 0.05, "pivot": [0.41, 0.26], "flap": {"deg": 4.5, "hz": 0.42, "phase": 0.0}},
            {"id": "wing_r", "texture": TEX + "back/wing_r.png", "z": 0.05, "pivot": [0.59, 0.26], "flap": {"deg": -4.5, "hz": 0.42, "phase": 0.3}},
            {"id": "hair", "texture": TEX + "back/hair.png", "z": 0.07, "pivot": [0.50, 0.08], "flap": {"deg": 1.2, "hz": 0.28, "phase": 0.2}},
            {"id": "tail", "texture": TEX + "back/tail.png", "z": 0.09, "pivot": [0.50, 0.46], "flap": {"deg": 2.4, "hz": 0.34, "phase": 0.5}},
        ],
        "rear": [
            {"id": "body_dress", "texture": TEX + "rear/body_dress.png", "z": 0.00, "breathe": 0.003},
            {"id": "wings_folded", "texture": TEX + "rear/wings_folded.png", "z": 0.04, "breathe": 0.005},
            {"id": "hair", "texture": TEX + "rear/hair.png", "z": 0.06, "pivot": [0.50, 0.08], "flap": {"deg": 1.1, "hz": 0.28, "phase": 0.2}},
            {"id": "tail", "texture": TEX + "rear/tail.png", "z": 0.08, "pivot": [0.50, 0.46], "flap": {"deg": 2.0, "hz": 0.34, "phase": 0.5}},
        ],
    },
}

# Preview 1: Front parallax at -30, -15, 0, +15, +30 degrees
BLOCK_PX = OUT_H / 2.5
panels = []
front_stack_imgs = [
    (Image.open(os.path.join(HERE, "front/tail.png")), -0.10),
    (Image.open(os.path.join(HERE, "front/wing_l.png")), -0.07),
    (Image.open(os.path.join(HERE, "front/wing_r.png")), -0.07),
    (Image.open(os.path.join(HERE, "front/body.png")), 0.00),
    (Image.open(os.path.join(HERE, "front/glow.png")), 0.004),
    (Image.open(os.path.join(HERE, "front/hair.png")), 0.012),
]
for deg in (-30, -15, 0, 15, 30):
    t = np.tan(np.radians(deg))
    canvas = Image.new("RGBA", (OUT_W, OUT_H), (38, 52, 66, 255))
    for img, z in front_stack_imgs:
        dx = int(round(z * t * BLOCK_PX))
        shifted = Image.new("RGBA", (OUT_W, OUT_H), (0, 0, 0, 0))
        shifted.paste(img, (dx, 0))
        canvas.alpha_composite(shifted)
    d = ImageDraw.Draw(canvas)
    d.text((16, 12), f"front parallax {deg:+d} deg", fill=(255, 235, 120, 255))
    panels.append(canvas.resize((OUT_W // 3, OUT_H // 3), Image.LANCZOS))

sheet_p = Image.new("RGB", (sum(p.size[0] for p in panels) + 8 * (len(panels) - 1), panels[0].size[1]), (10, 10, 14))
x_off = 0
for p in panels:
    sheet_p.paste(p.convert("RGB"), (x_off, 0))
    x_off += p.size[0] + 8
sheet_p.save(os.path.join(HERE, "preview_parallax.png"))

# Preview 2: All 5 directional views + cast ground shadow simulation
dir_panels = []
for vname in ("front", "left", "back", "rear", "right"):
    canvas = Image.new("RGBA", (OUT_W, OUT_H + 140), (42, 48, 56, 255))
    ground = ImageDraw.Draw(canvas)
    ground.rectangle([0, OUT_H - 40, OUT_W, OUT_H + 140], fill=(56, 62, 70, 255))
    v_layers = LAYERS_JSON["views"][vname]
    sil = Image.new("RGBA", (OUT_W, OUT_H), (0, 0, 0, 0))
    for linfo in v_layers:
        rel = linfo["texture"].split("emily_standee/")[1]
        lim = Image.open(os.path.join(HERE, rel))
        sil.alpha_composite(lim)
    sh_a = np.asarray(sil)[..., 3].astype(np.float32) / 255.0
    sh_img = Image.fromarray((sh_a * 115).astype(np.uint8), "L").resize((OUT_W, int(OUT_H * 0.22)), Image.BILINEAR)
    sh_img = sh_img.transform(sh_img.size, Image.AFFINE, (1.0, -0.35, 60.0, 0.0, 1.0, 0.0), resample=Image.BILINEAR).filter(ImageFilter.GaussianBlur(4))
    black_sh = Image.new("RGBA", sh_img.size, (8, 8, 12, 255))
    black_sh.putalpha(sh_img)
    canvas.alpha_composite(black_sh, (0, OUT_H - sh_img.height + 6))
    for linfo in v_layers:
        rel = linfo["texture"].split("emily_standee/")[1]
        lim = Image.open(os.path.join(HERE, rel))
        canvas.alpha_composite(lim, (0, 0))
    d = ImageDraw.Draw(canvas)
    d.text((16, 12), f"direction: {vname}", fill=(255, 235, 120, 255))
    dir_panels.append(canvas.resize((OUT_W // 3, (OUT_H + 140) // 3), Image.LANCZOS))

sheet_d = Image.new("RGB", (sum(p.size[0] for p in dir_panels) + 8 * (len(dir_panels) - 1), dir_panels[0].size[1]), (10, 10, 14))
x_off = 0
for p in dir_panels:
    sheet_d.paste(p.convert("RGB"), (x_off, 0))
    x_off += p.size[0] + 8
sheet_d.save(os.path.join(HERE, "preview_directions.png"))
print("Wrote preview_parallax.png and preview_directions.png")

if "--install" in sys.argv:
    assets_roots = [
        os.path.join(ROOT, "blighted_enhancements", "src", "main", "resources", "assets", "blighted_enhancements"),
    ]
    dev_pack = r"C:\Users\srafa\bd\game\resourcepacks\emily_dev\assets\blighted_enhancements"
    if os.path.exists(os.path.dirname(os.path.dirname(dev_pack))):
        assets_roots.append(dev_pack)
    for a in assets_roots:
        tdir = os.path.join(a, "textures", "entity", "emily_standee")
        os.makedirs(tdir, exist_ok=True)
        for root_f in ("body.png", "wing_l.png", "wing_r.png"):
            shutil.copyfile(os.path.join(HERE, root_f), os.path.join(tdir, root_f))
        for vdir in ("front", "left", "right", "back", "rear"):
            src_v = os.path.join(HERE, vdir)
            dst_v = os.path.join(tdir, vdir)
            os.makedirs(dst_v, exist_ok=True)
            for fn in os.listdir(src_v):
                if fn.endswith(".png"):
                    shutil.copyfile(os.path.join(src_v, fn), os.path.join(dst_v, fn))
        jdir = os.path.join(a, "helper", "standee")
        os.makedirs(jdir, exist_ok=True)
        with open(os.path.join(jdir, "layers.json"), "w", encoding="utf-8") as f:
            json.dump(LAYERS_JSON, f, indent=2)
        print("Installed directional standee layers to", a)

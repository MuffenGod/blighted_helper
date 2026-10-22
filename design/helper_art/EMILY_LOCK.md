# Emily: locked character design (2026-10-16)

**Final reference: REFERENCE_SHEET.jpg (chosen by the user). Pointed elf ears are GOOD (user, 2026-10-16). Black horns with gold line inlay, black scaled off-shoulder sleeves, long black gloves, side-slit black gown with gold embroidery, big black bat wings, black scaled tail, gold chain circlet with a green gem, wavy blonde hair, green eyes, wicked smile. Skin painted by make_skin.ps1.**

Source of truth for her look. Prompt strings live in `gen_emily5.py` (`LOCK`, `NEG`). Reference renders: `out5/`.

- **Who:** Emily, the player's companion. Tall, mature, noble half-dragon. Seductive and almost evil, yandere.
- **Hair:** long wavy pale blonde (not green, not white).
- **Eyes:** glowing emerald green, heart-shaped slit pupils, shaded, half-lidded.
- **Face:** wicked sultry smile with a small fang, dark lips, small black scales at the temples and cheeks.
- **Dragon parts:** glossy **black** horns, large **black** wings, **black** tail, black scales on arms and shoulders.
  Green is an accent only (gem glow, wing membrane glow, scale highlights).
- **Crown:** slim gold circlet with a green heart-shaped gem.
- **Outfit:** sleek floor-length black evening gown, fitted bodice, off-shoulder, thin gold embroidery, green gem pendant,
  long black satin opera gloves. No frills or lace.
- **Skin:** fair human skin on face, neck and chest (the round-5 renders drifted grey, fix in the next pass).
- **Palette for the Minecraft skin:** black (30,24,34), gold (232,192,82), emerald (58,196,112), pale blonde (246,226,150),
  skin (252,226,212), scale highlight dark teal-black (36,48,52).

## Known problems in round 5
- Black scales leaked into her face and neck skin (grey/dark skin in face_a, face_b).
- Wings and tail pick up bright green membranes in some takes (front_b, back).
- Side and back views are not true turnarounds, the model invents extra figures and props.
- face_b has pointed elf ears (now WANTED, see the top of this file).


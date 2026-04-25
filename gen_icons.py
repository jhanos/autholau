#!/usr/bin/env python3
"""Generate Android adaptive icon PNGs from alpaca.svg."""
import os, cairosvg
from PIL import Image
import io

BASE = os.path.dirname(os.path.abspath(__file__))
SVG  = os.path.join(BASE, "alpaca.svg")
RES  = os.path.join(BASE, "app/src/main/res")

# mipmap density -> size in px for launcher icon
SIZES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

# Adaptive icon foreground (108dp grid, so 1.5x the launcher icon size)
# mdpi=108, hdpi=162, xhdpi=216, xxhdpi=324, xxxhdpi=432
ADAPTIVE_FG_SIZES = {
    "mipmap-mdpi":    108,
    "mipmap-hdpi":    162,
    "mipmap-xhdpi":   216,
    "mipmap-xxhdpi":  324,
    "mipmap-xxxhdpi": 432,
}

def svg_to_png(size: int) -> bytes:
    return cairosvg.svg2png(url=SVG, output_width=size, output_height=size)

for density, size in SIZES.items():
    out_dir = os.path.join(RES, density)
    os.makedirs(out_dir, exist_ok=True)
    png = svg_to_png(size)
    path = os.path.join(out_dir, "ic_launcher.png")
    with open(path, "wb") as f:
        f.write(png)
    # Also write round variant
    img = Image.open(io.BytesIO(png)).convert("RGBA")
    path_round = os.path.join(out_dir, "ic_launcher_round.png")
    with open(path_round, "wb") as f:
        f.write(png)
    print(f"  {density}: {size}px -> ic_launcher.png + ic_launcher_round.png")

# Adaptive foreground (same alpaca, slightly smaller inside safe zone)
for density, size in ADAPTIVE_FG_SIZES.items():
    out_dir = os.path.join(RES, density)
    os.makedirs(out_dir, exist_ok=True)
    png = svg_to_png(size)
    path = os.path.join(out_dir, "ic_launcher_foreground.png")
    with open(path, "wb") as f:
        f.write(png)
    print(f"  {density}: {size}px -> ic_launcher_foreground.png")

print("Done.")

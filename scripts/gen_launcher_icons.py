"""
Generate Android launcher icon PNGs from store-assets/play_store_icon_512.png.
Writes adaptive-icon foreground PNGs into mipmap-*/ic_launcher_foreground.png
at all 5 densities. Also writes legacy square ic_launcher.png for completeness.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "store-assets" / "play_store_icon_512.png"
RES = ROOT / "app" / "src" / "main" / "res"

# Adaptive-icon foreground native sizes (108dp drawable at each density).
# The actual artwork must live in the inner 72dp safe zone => 2/3 of canvas.
FG_SIZES = {
    "mdpi":    108,
    "hdpi":    162,
    "xhdpi":   216,
    "xxhdpi":  324,
    "xxxhdpi": 432,
}
# Legacy square launcher icon sizes (48dp at each density)
LEGACY_SIZES = {
    "mdpi":    48,
    "hdpi":    72,
    "xhdpi":   96,
    "xxhdpi":  144,
    "xxxhdpi": 192,
}

SAFE_FRACTION = 72 / 108  # adaptive icon safe zone

src = Image.open(SRC).convert("RGBA")
print(f"Loaded source: {src.size}")

for density, canvas in FG_SIZES.items():
    art = int(canvas * SAFE_FRACTION)
    resized = src.resize((art, art), Image.LANCZOS)
    fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    off = (canvas - art) // 2
    fg.paste(resized, (off, off), resized)
    out_dir = RES / f"mipmap-{density}"
    out_dir.mkdir(parents=True, exist_ok=True)
    fg.save(out_dir / "ic_launcher_foreground.png", optimize=True)
    print(f"  wrote {out_dir / 'ic_launcher_foreground.png'} ({canvas}x{canvas}, art {art}x{art})")

# Legacy icons (round + square) for pre-API-26 devices and Play Store fallback
for density, sz in LEGACY_SIZES.items():
    resized = src.resize((sz, sz), Image.LANCZOS)
    out_dir = RES / f"mipmap-{density}"
    out_dir.mkdir(parents=True, exist_ok=True)
    resized.save(out_dir / "ic_launcher.png", optimize=True)
    resized.save(out_dir / "ic_launcher_round.png", optimize=True)
    print(f"  wrote legacy ic_launcher(.png|_round.png) at {density} ({sz}x{sz})")

print("Done.")

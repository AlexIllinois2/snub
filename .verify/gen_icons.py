"""Generate launcher icon resources from snub-logo.png.

Outputs:
- mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png  (legacy icons, white bg kept)
- drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png (108dp canvas, white -> transparent)
- drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_monochrome.png (same transparent version)
"""
from PIL import Image
import os

SRC = r"D:\weiheng\proj\snub\snub-logo.png"
RES = r"D:\weiheng\proj\snub\app\src\main\res"

# density -> (legacy icon px, 108dp canvas px)
DENSITIES = {
    "mdpi": (48, 108),
    "hdpi": (72, 162),
    "xhdpi": (96, 216),
    "xxhdpi": (144, 324),
    "xxxhdpi": (192, 432),
}

WHITE_THRESHOLD = 200  # pixels with all channels above this become transparent


def make_transparent(img: Image.Image) -> Image.Image:
    """Convert the white background to alpha; keep anti-aliased edges partially transparent."""
    img = img.convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if r > WHITE_THRESHOLD and g > WHITE_THRESHOLD and b > WHITE_THRESHOLD:
                px[x, y] = (r, g, b, 0)
    return img


def main() -> None:
    logo = Image.open(SRC).convert("RGB")
    print("source:", logo.size, logo.mode)
    transparent = make_transparent(logo)

    for density, (icon_px, canvas_px) in DENSITIES.items():
        os.makedirs(rf"{RES}\drawable-{density}", exist_ok=True)
        # Legacy launcher icon: original logo (white background) at legacy size.
        legacy = logo.resize((icon_px, icon_px), Image.LANCZOS)
        legacy.save(rf"{RES}\mipmap-{density}\ic_launcher.png", optimize=True)
        print(f"mipmap-{density}/ic_launcher.png -> {icon_px}px")

        # Adaptive foreground / monochrome: 108dp canvas, transparent version.
        fg = transparent.resize((canvas_px, canvas_px), Image.LANCZOS)
        fg.save(rf"{RES}\drawable-{density}\ic_launcher_foreground.png", optimize=True)
        fg.save(rf"{RES}\drawable-{density}\ic_launcher_monochrome.png", optimize=True)
        print(f"drawable-{density}/ic_launcher_foreground.png + monochrome.png -> {canvas_px}px")


if __name__ == "__main__":
    main()

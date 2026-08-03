from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

out = Path(r"C:\Users\yishe\AndroidStudioProjects\Keyic\playstore")
out.mkdir(parents=True, exist_ok=True)

TEAL = (0x1A, 0x6B, 0x5C, 255)
MIST = (0xF4, 0xF5, 0xF0, 255)
size = 512


def vx(x: float, y: float) -> tuple[float, float]:
    sx = 54 + (x - 54) * 0.78
    sy = 54 + (y - 54) * 0.78
    return sx / 108 * size, sy / 108 * size


def cubic(p0, p1, p2, p3, n=24):
    pts = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        x = u**3 * p0[0] + 3 * u**2 * t * p1[0] + 3 * u * t**2 * p2[0] + t**3 * p3[0]
        y = u**3 * p0[1] + 3 * u**2 * t * p1[1] + 3 * u * t**2 * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


icon = Image.new("RGBA", (size, size), TEAL)
draw = ImageDraw.Draw(icon)

# Shackle
stroke = max(8, int(6.5 / 108 * size * 0.78))
shackle = Image.new("RGBA", (size, size), (0, 0, 0, 0))
sd = ImageDraw.Draw(shackle)
x0, y0 = vx(54 - 13, 35 - 13)
x1, y1 = vx(54 + 13, 35 + 13)
sd.arc([x0, y0, x1, y1], start=200, end=340, fill=MIST, width=stroke)
lx, ly = vx(41, 35)
lx2, ly2 = vx(41, 43)
rx, ry = vx(67, 35)
rx2, ry2 = vx(67, 43)
sd.line([(lx, ly), (lx2, ly2)], fill=MIST, width=stroke)
sd.line([(rx, ry), (rx2, ry2)], fill=MIST, width=stroke)
icon = Image.alpha_composite(icon, shackle)
draw = ImageDraw.Draw(icon)

shield_vp = []
shield_vp += cubic((54, 40), (64, 44), (74, 46.5), (80, 47.5), 16)
shield_vp += cubic((80, 47.5), (80, 58), (80, 58), (80, 66.5), 8)
shield_vp += cubic((80, 66.5), (80, 81.5), (69, 91.5), (54, 97.5), 20)
shield_vp += cubic((54, 97.5), (39, 91.5), (28, 81.5), (28, 66.5), 20)
shield_vp += cubic((28, 66.5), (28, 58), (28, 58), (28, 47.5), 8)
shield_vp += cubic((28, 47.5), (34, 46.5), (44, 44), (54, 40), 16)
draw.polygon([vx(x, y) for x, y in shield_vp], fill=MIST)

k_vp = [
    (46, 52),
    (51.2, 52),
    (51.2, 60.6),
    (58.6, 52),
    (65, 52),
    (55.2, 62.2),
    (66, 74),
    (59.4, 74),
    (51.2, 64.6),
    (51.2, 74),
    (46, 74),
    (46, 52),
]
draw.polygon([vx(x, y) for x, y in k_vp], fill=TEAL)

icon_path = out / "play-icon-512.png"
icon.convert("RGB").save(icon_path, "PNG", optimize=True)
print("icon", icon_path, icon_path.stat().st_size)

# Feature graphic
feat = Image.new("RGB", (1024, 500))
px = feat.load()
for y in range(500):
    for x in range(1024):
        tx = x / 1023
        ty = y / 499
        d = ((tx - 0.20) ** 2 + (ty - 0.45) ** 2) ** 0.5
        t = min(1.0, d * 1.05)
        r = int(0x1A + (0xF4 - 0x1A) * t)
        g = int(0x6B + (0xF5 - 0x6B) * t)
        b = int(0x5C + (0xF0 - 0x5C) * t)
        px[x, y] = (r, g, b)

fd = ImageDraw.Draw(feat)
mark = icon.resize((210, 210), Image.Resampling.LANCZOS)
feat.paste(mark, (78, 145), mark)

font_brand_path = next(
    (
        p
        for p in (
            r"C:\Windows\Fonts\segoeuib.ttf",
            r"C:\Windows\Fonts\arialbd.ttf",
            r"C:\Windows\Fonts\calibrib.ttf",
        )
        if Path(p).exists()
    ),
    None,
)
font_sub_path = next(
    (
        p
        for p in (
            r"C:\Windows\Fonts\segoeui.ttf",
            r"C:\Windows\Fonts\arial.ttf",
        )
        if Path(p).exists()
    ),
    font_brand_path,
)
font_brand = ImageFont.truetype(font_brand_path, 118) if font_brand_path else ImageFont.load_default()
font_sub = ImageFont.truetype(font_sub_path, 34) if font_sub_path else ImageFont.load_default()

fd.text((340, 150), "Keyic", font=font_brand, fill=(0xF4, 0xF5, 0xF0))
fd.text((340, 295), "Offline by design", font=font_sub, fill=(0xE8, 0xEB, 0xE4))

feat_path = out / "play-feature-graphic-1024x500.png"
feat.save(feat_path, "PNG", optimize=True)
print("feature", feat_path, feat_path.stat().st_size)

# Exact-size version of generated AI banner as alternate
src = Path(
    r"C:\Users\yishe\.cursor\projects\c-Users-yishe-Documents-GitHub-keyic-privacy"
    r"\assets\keyic-feature-graphic-1024x500.png"
)
if src.exists():
    ai = Image.open(src).convert("RGB").resize((1024, 500), Image.Resampling.LANCZOS)
    alt = out / "play-feature-graphic-ai-1024x500.png"
    ai.save(alt, "PNG", optimize=True)
    print("feature_ai", alt, alt.stat().st_size)

# Verify sizes
for p in (icon_path, feat_path):
    im = Image.open(p)
    print(p.name, im.size, p.stat().st_size)

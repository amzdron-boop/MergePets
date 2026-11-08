"""Generates Google Play store graphics: icon 512x512 and feature graphic 1024x500."""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = os.path.dirname(os.path.abspath(__file__))
SS = 4  # supersampling


def gradient(w, h, top, bottom, diagonal=False):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = ((x / w + y / h) / 2) if diagonal else (y / h)
            px[x, y] = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return img


def draw_cat(d, cx, cy, s):
    """Cat face. Coordinates come from the 108x108 launcher vector; (54,58) maps to (cx,cy)."""
    def P(x, y):
        return (cx + (x - 54) * s, cy + (y - 58) * s)

    def poly(pts, col):
        d.polygon([P(*p) for p in pts], fill=col)

    def ell(x0, y0, x1, y1, col):
        a, b = P(x0, y0), P(x1, y1)
        d.ellipse([a, b], fill=col)

    orange, inner = (255, 159, 67), (255, 201, 201)
    poly([(30, 50), (34, 22), (53, 38)], orange)
    poly([(78, 50), (74, 22), (55, 38)], orange)
    poly([(35, 43), (36.5, 30), (46, 38)], inner)
    poly([(73, 43), (71.5, 30), (62, 38)], inner)
    ell(28, 34, 80, 83, (255, 180, 94))          # head
    ell(39, 52, 47, 62, (42, 22, 70))            # eyes
    ell(61, 52, 69, 62, (42, 22, 70))
    ell(42.8, 54, 45.6, 56.8, (255, 255, 255))   # eye shine
    ell(64.8, 54, 67.6, 56.8, (255, 255, 255))
    poly([(50.5, 64), (57.5, 64), (54, 68.5)], (255, 107, 139))  # nose
    w = max(2, int(1.7 * s))
    d.line([P(54, 68), P(54, 71)], fill=(42, 22, 70), width=w)
    d.arc([P(47, 68), P(54, 75)], 0, 180, fill=(42, 22, 70), width=w)
    d.arc([P(54, 68), P(61, 75)], 0, 180, fill=(42, 22, 70), width=w)
    cheek = Image.new("RGBA", (1, 1))
    ell(32, 64, 40, 70, (255, 140, 150))         # cheeks
    ell(68, 64, 76, 70, (255, 140, 150))


def sparkle(d, x, y, r, col):
    d.polygon([(x, y - r), (x + r * 0.28, y - r * 0.28), (x + r, y), (x + r * 0.28, y + r * 0.28),
               (x, y + r), (x - r * 0.28, y + r * 0.28), (x - r, y), (x - r * 0.28, y - r * 0.28)], fill=col)


def make_icon():
    size = 512 * SS
    img = gradient(size, size, (110, 70, 200), (60, 30, 130), diagonal=True).convert("RGBA")
    # soft glow behind the cat
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([size * 0.12, size * 0.12, size * 0.88, size * 0.88], fill=(255, 220, 140, 70))
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.06))
    img = Image.alpha_composite(img, glow)
    d = ImageDraw.Draw(img)
    draw_cat(d, size * 0.5, size * 0.53, size / 108 * 1.45)
    sparkle(d, size * 0.84, size * 0.2, size * 0.065, (255, 232, 130))
    sparkle(d, size * 0.15, size * 0.3, size * 0.04, (255, 255, 255))
    sparkle(d, size * 0.8, size * 0.8, size * 0.035, (255, 255, 255))
    img = img.convert("RGB").resize((512, 512), Image.LANCZOS)
    img.save(os.path.join(OUT, "icon-512.png"), optimize=True)


def font(path, size):
    return ImageFont.truetype(path, size)


def make_feature():
    W, H = 1024, 500
    img = gradient(W, H, (40, 20, 85), (110, 55, 190), diagonal=True).convert("RGBA")
    d = ImageDraw.Draw(img)
    for x, y, r, c in [(60, 60, 14, (255, 232, 130)), (960, 80, 18, (255, 255, 255)),
                       (900, 430, 12, (255, 232, 130)), (120, 440, 10, (255, 255, 255)),
                       (520, 40, 9, (255, 255, 255))]:
        sparkle(d, x, y, r, c)

    bold = r"C:\Windows\Fonts\segoeuib.ttf"
    emoji_path = r"C:\Windows\Fonts\seguiemj.ttf"
    title = font(bold, 112)
    sub = font(bold, 40)

    def shadow_text(xy, text, f, fill):
        d.text((xy[0] + 4, xy[1] + 5), text, font=f, fill=(20, 8, 45, 200))
        d.text(xy, text, font=f, fill=fill)

    shadow_text((70, 70), "Merge Pets", title, (255, 216, 77))
    shadow_text((74, 205), "Merge. Grow. Collect them all!", sub, (255, 255, 255))

    # evolution chain of pets
    try:
        ef = font(emoji_path, 109)
        chain = ["🥚", "🐣", "🐱", "🦊", "🐲", "🦄"]
        colors = [(244, 233, 216), (255, 224, 138), (247, 143, 179), (155, 181, 255), (255, 122, 107), (255, 215, 0)]
        n = len(chain)
        card, gap = 128, 28
        total = n * card + (n - 1) * gap
        x0 = (W - total) // 2
        y0 = 300
        for i, (e, col) in enumerate(zip(chain, colors)):
            x = x0 + i * (card + gap)
            d.rounded_rectangle([x, y0, x + card, y0 + card], radius=28, fill=col)
            tmp = Image.new("RGBA", (160, 160), (0, 0, 0, 0))
            ImageDraw.Draw(tmp).text((80, 80), e, font=ef, embedded_color=True, anchor="mm")
            tmp = tmp.resize((92, 92), Image.LANCZOS)
            img.alpha_composite(tmp, (x + (card - 92) // 2, y0 + (card - 92) // 2))
            if i < n - 1:
                ax = x + card + gap // 2
                d.polygon([(ax - 7, y0 + card // 2 - 12), (ax + 9, y0 + card // 2), (ax - 7, y0 + card // 2 + 12)],
                          fill=(255, 255, 255))
    except Exception as ex:  # emoji font problems: keep the graphic without pets
        print("emoji rendering failed:", ex)

    img.convert("RGB").save(os.path.join(OUT, "feature-graphic-1024x500.png"), optimize=True)


if __name__ == "__main__":
    make_icon()
    make_feature()
    print("done")

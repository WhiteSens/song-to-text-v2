# Генерує фони панелі (село, вечір/день). Можна замінити своїми PNG 1280x720 з тими ж іменами.
from PIL import Image, ImageDraw
import random, math
W, H, B = 1280, 720, 8
w, h = W // B, H // B
HORIZON = 52

def scene(night):
    random.seed(11)
    img = Image.new("RGB", (w, h)); px = img.load()
    lit = []
    for y in range(h):
        t = min(1, y / HORIZON)
        top = (74, 128, 205) if not night else (6, 10, 30)
        bot = (160, 205, 240) if not night else (28, 38, 70)
        for x in range(w):
            px[x, y] = tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3))
    d = ImageDraw.Draw(img)
    # далекі дерева
    for cx, r in [(6, 20), (20, 14), (140, 22), (152, 16), (80, 9), (66, 8), (96, 8)]:
        for y in range(HORIZON - r, HORIZON + 2):
            for x in range(cx - r, cx + r):
                if 0 <= x < w and (x - cx) ** 2 + (y - (HORIZON - 2)) ** 2 * 1.6 < r * r:
                    px[x, y] = (30 + random.randint(0, 14), 70 + random.randint(0, 20), 40) if not night else (10, 22, 18)
    # земля
    for y in range(HORIZON, h):
        for x in range(w):
            g = random.randint(-14, 14)
            px[x, y] = (70 + g, 130 + g, 50 + g // 2)
    # будинки
    def house(x0, x1, y0):
        for y in range(y0, HORIZON + 8):
            for x in range(x0, x1):
                n = random.randint(-12, 12)
                px[x, y] = (112 + n, 78 + n, 44 + n // 2)
        for i in range(0, 9):  # дах сходами
            for x in range(x0 - 2 + i, x1 + 2 - i):
                y = y0 - 1 - i
                if 0 <= y < h:
                    n = random.randint(-8, 8)
                    px[x, y] = (84 + n, 56 + n, 34)
        for wx in (x0 + 8, x1 - 14):
            for y in range(y0 + 8, y0 + 14):
                for x in range(wx, wx + 6):
                    px[x, y] = (40, 30, 24)
                    lit.append((x, y))
    house(0, 46, 28); house(108, 152, 26)
    # шлях
    for y in range(HORIZON + 1, h):
        hw = 3 + (y - HORIZON) * 1.35
        for x in range(w):
            if abs(x - w / 2) < hw:
                n = random.randint(-16, 16)
                px[x, y] = (96 + n, 92 + n, 96 + n)
    # небесне світило
    sx, sy = 114, 7
    lights = []
    for y in range(sy, sy + 7):
        for x in range(sx, sx + 7):
            px[x, y] = (255, 240, 150) if not night else (205, 220, 245)
            lights.append((x, y))
    if night:
        for _ in range(70):
            x, y = random.randint(0, w - 1), random.randint(0, 34)
            px[x, y] = (200, 210, 240); lights.append((x, y))
        k = (0.30, 0.34, 0.5)
        for y in range(h):
            for x in range(w):
                if (x, y) in set():
                    continue
        L = set(lights)
        for y in range(HORIZON - 30, h):
            for x in range(w):
                if (x, y) not in L:
                    r, g, b = px[x, y]
                    px[x, y] = (int(r * k[0]), int(g * k[1]), int(b * k[2]))
        for x, y in lit:
            px[x, y] = (255, 170, 60)
    return img.resize((W, H), Image.NEAREST)

def rounded(img):
    img = img.convert("RGBA")
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).rounded_rectangle((32, 24, 1248, 693), radius=46, fill=235)
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0)); out.paste(img, (0, 0), m)
    ImageDraw.Draw(out).rounded_rectangle((32, 24, 1248, 693), radius=46, outline=(255, 255, 255, 110), width=3)
    return out

for name, n in (("panel_day", False), ("panel_night", True)):
    rounded(scene(n)).save(f"src/main/resources/assets/songtotext/textures/gui/{name}.png")

# Робить чисті фони з еталонних картинок: прибирає підписи, перемикачі, кнопки, значення (inpaint),
# лишає село, картки, роздільники й заголовок «Song to text». Інтерфейс малює Java2D у грі.
import cv2, numpy as np
from PIL import Image, ImageDraw
SRC = {"day": "/mnt/user-data/uploads/Same_bright_daytime_Minecraft_HUD_as_the_reference.jpg",
       "night": "/mnt/user-data/uploads/Same_night_Minecraft_HUD_as_the_reference__Keep_ev.jpg"}
ROWS = [173, 253, 333, 413]

def build(name):
    img = cv2.imread(SRC[name]); h, w = img.shape[:2]
    mask = np.zeros((h, w), np.uint8)
    gray = img.min(axis=2)
    # світлий текст у зонах підписів
    zones = [(84, 480), (668, 1000)]
    for y in ROWS:
        for x0, x1 in zones:
            z = gray[y - 26:y + 26, x0:x1] > (135 if name == "day" else 110)
            mask[y - 26:y + 26, x0:x1][z] = 255
    # значення (100% / 0.0 с)
    for y in (333, 413):
        z = gray[y - 26:y + 26, 1080:1200] > (135 if name == "day" else 110)
        mask[y - 26:y + 26, 1080:1200][z] = 255
    mask = cv2.dilate(mask, np.ones((11, 11), np.uint8))
    # суцільні блоки: перемикачі, свотч, ряд кнопок шрифту
    for y in ROWS:
        cv2.rectangle(mask, (484, y - 28), (584, y + 28), 255, -1)
    cv2.rectangle(mask, (1108, 138), (1182, 208), 255, -1)
    cv2.rectangle(mask, (892, 222), (1212, 292), 255, -1)
    out = cv2.inpaint(img, mask, 6, cv2.INPAINT_TELEA)
    rgb = cv2.cvtColor(out, cv2.COLOR_BGR2RGB)
    im = Image.fromarray(rgb).convert("RGBA")
    # прозорі кути: все поза заокругленою панеллю
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((32, 24, 1248, 693), radius=46, fill=255)
    res = Image.new("RGBA", (w, h), (0, 0, 0, 0)); res.paste(im, (0, 0), m)
    res.save(f"src/main/resources/assets/songtotext/textures/gui/panel_{name}.png")
    Image.fromarray(cv2.cvtColor(mask, cv2.COLOR_GRAY2RGB)).save(f"/home/claude/mask_{name}.png")

for n in SRC: build(n)

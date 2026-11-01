# -*- coding: utf-8 -*-
"""LiteBrawl 插件图标生成器。

用法：python tools/MakeIcon.py
产物：assets/icon.png (512) / assets/icon-128.png / assets/icon-64.png

风格取自插件玩法本身：等距体素绿宝石块（出生方块）+ 交叉铁剑（固定装备）
+ 暗色深板岩底，暖橙火花点缀。全部用几何绘制，可随时调参数重出。
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "assets")
SIZE = 512
SS = 4                      # 超采样倍数，最后统一缩小做抗锯齿
W = SIZE * SS

# 调色板
BG = (13, 16, 23)
STONE = (33, 38, 49)
EMERALD_TOP = (88, 234, 158)
EMERALD_LEFT = (38, 176, 110)
EMERALD_RIGHT = (20, 114, 70)
EMERALD_DARK = (9, 44, 28)
STEEL = (206, 213, 224)
STEEL_HI = (244, 248, 253)
STEEL_SH = (132, 143, 160)
OUTLINE = (14, 17, 22)


def s(v):
    return int(round(v * SS))


def shade(c, d):
    return tuple(max(0, min(255, int(x + d))) for x in c)


def draw_poly(d, pts, fill=None, outline=None, width=2):
    """pts 为 512 坐标系下的浮点坐标，内部自动放大。"""
    pts = [(s(x), s(y)) for x, y in pts]
    if fill is not None:
        d.polygon(pts, fill=fill)
    if outline is not None:
        d.line(list(pts) + [pts[0]], fill=outline, width=max(1, s(width)), joint="curve")


def rot(px, py, ox, oy, deg):
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    dx, dy = px - ox, py - oy
    return ox + dx * ca - dy * sa, oy + dx * sa + dy * ca


# ---------------------------------------------------------------- 背景
def draw_background(img, d):
    d.rectangle([0, 0, W, W], fill=BG)

    rnd = random.Random(20261101)
    bw, bh = s(94), s(44)
    y = s(-30)
    row = 0
    while y < W + bh:
        x = s(-30) - (bw // 2 if row % 2 else 0)
        while x < W + bw:
            v = rnd.randint(-6, 10)
            base = shade(STONE, v)
            d.rectangle([x, y, x + bw - s(4), y + bh - s(4)], fill=base)
            d.line([(x, y + bh - s(4)), (x, y), (x + bw - s(4), y)],
                   fill=shade(base, 15), width=max(1, s(1.5)))
            if rnd.random() < 0.2:
                cx = x + rnd.randint(s(12), max(s(13), bw - s(34)))
                cy = y + rnd.randint(s(10), max(s(11), bh - s(22)))
                d.line([(cx, cy), (cx + s(rnd.randint(12, 28)), cy + s(5))],
                       fill=shade(base, -12), width=max(1, s(1.5)))
            x += bw
        y += bh
        row += 1

    # 中央冷光
    glow = Image.new("L", (W, W), 0)
    gd = ImageDraw.Draw(glow)
    for i in range(30):
        r = s(330) - i * s(11)
        if r <= 0:
            break
        gd.ellipse([s(256) - r, s(250) - r, s(256) + r, s(250) + r], fill=int(58 * (i / 30.0) ** 1.6))
    glow = glow.filter(ImageFilter.GaussianBlur(s(30)))
    cool = Image.new("RGB", (W, W), (28, 52, 76))
    img.paste(cool, (0, 0), glow.point(lambda v: int(v * 1.5)))


# ---------------------------------------------------------------- 剑
def sword(d, ox, oy, deg, scale=1.0):
    """以 (ox, oy) 为握把中心、deg 为倾斜角度画一把像素风铁剑。"""
    bw = 30 * scale
    hilt = 116 * scale
    blade_len = 232 * scale
    tip_extra = 44 * scale
    guard_w = 60 * scale
    handle_len = 58 * scale

    def T(pts):
        out = []
        for x, y in pts:
            rx, ry = rot(ox + x * SS / SS, oy + y, ox, oy, deg)
            out.append((rx, ry))
        return out

    # 剑身
    blade = [(-bw / 2, -hilt), (bw / 2, -hilt),
             (bw / 2, -hilt - blade_len), (0, -hilt - blade_len - tip_extra),
             (-bw / 2, -hilt - blade_len)]
    draw_poly(d, T(blade), fill=STEEL, outline=OUTLINE, width=3.5)
    # 刃口高光
    hi = [(-bw / 2 + 4 * scale, -hilt + 2), (-bw / 2 + 12 * scale, -hilt + 2),
          (-bw / 2 + 12 * scale, -hilt - blade_len + 14), (-bw / 2 + 4 * scale, -hilt - blade_len + 30)]
    draw_poly(d, T(hi), fill=STEEL_HI)
    # 刃背阴影
    sh = [(bw / 2 - 8 * scale, -hilt + 4), (bw / 2, -hilt + 4),
          (bw / 2, -hilt - blade_len + 2), (0, -hilt - blade_len - tip_extra + 6)]
    draw_poly(d, T(sh), fill=STEEL_SH)
    # 血槽
    fuller = [(-3.5 * scale, -hilt - 16 * scale), (3.5 * scale, -hilt - 16 * scale),
              (3.5 * scale, -hilt - blade_len + 18 * scale), (-3.5 * scale, -hilt - blade_len + 18 * scale)]
    draw_poly(d, T(fuller), fill=shade(STEEL, -22))
    # 护手
    guard = [(-guard_w, -hilt), (guard_w, -hilt), (guard_w - 9 * scale, -hilt + 21 * scale),
             (-guard_w + 9 * scale, -hilt + 21 * scale)]
    draw_poly(d, T(guard), fill=(214, 221, 232), outline=OUTLINE, width=3.5)
    # 握柄
    grip = [(-12 * scale, -hilt + 21 * scale), (12 * scale, -hilt + 21 * scale),
            (12 * scale, -hilt + 21 * scale + handle_len), (-12 * scale, -hilt + 21 * scale + handle_len)]
    draw_poly(d, T(grip), fill=(98, 70, 45), outline=OUTLINE, width=3.5)
    for k in range(3):
        gy = -hilt + 29 * scale + k * 17 * scale
        band = [(-12 * scale, gy), (12 * scale, gy), (12 * scale, gy + 6 * scale), (-12 * scale, gy + 6 * scale)]
        draw_poly(d, T(band), fill=(72, 49, 31))
    # 剑首
    pom = [(-20 * scale, -hilt + 80 * scale), (20 * scale, -hilt + 80 * scale),
           (20 * scale, -hilt + 108 * scale), (-20 * scale, -hilt + 108 * scale)]
    draw_poly(d, T(pom), fill=(202, 164, 80), outline=OUTLINE, width=3.5)


# ---------------------------------------------------------------- 绿宝石块
CX, CY = 256, 244          # 顶面中心
HW, HH = 126, 65           # 顶面半宽 / 半高
SIDE = 128                 # 侧面高度


def faces():
    top = [(CX, CY - HH), (CX + HW, CY), (CX, CY + HH), (CX - HW, CY)]
    left = [(CX - HW, CY), (CX, CY + HH), (CX, CY + HH + SIDE), (CX - HW, CY + SIDE)]
    right = [(CX + HW, CY), (CX, CY + HH), (CX, CY + HH + SIDE), (CX + HW, CY + SIDE)]
    return top, left, right


def face_mask(pts):
    m = Image.new("L", (W, W), 0)
    ImageDraw.Draw(m).polygon([(s(x), s(y)) for x, y in pts], fill=255)
    return m


def draw_cube(img, d):
    top, left, right = faces()

    # 身后绿光
    glow = Image.new("L", (W, W), 0)
    gd = ImageDraw.Draw(glow)
    for i in range(24):
        r = s(250) - i * s(10)
        if r <= 0:
            break
        gd.ellipse([s(CX) - r, s(CY + 55) - r, s(CX) + r, s(CY + 55) + r],
                   fill=int(150 * (i / 24.0) ** 1.5))
    glow = glow.filter(ImageFilter.GaussianBlur(s(26)))
    img.paste(Image.new("RGB", (W, W), (44, 168, 110)), (0, 0), glow)

    # 各面单独成层，细节用面自身遮罩裁切。
    # 面内用真实方块贴图坐标：A 为贴图原点，T1 为贴图 U 轴、T2 为贴图 V 轴，
    # 这样 3x3（或 4x3）格子天然铺满整个面，不会溢出或斜切。
    faces_def = [
        # 顶面：贴图 U 轴指向右下棱，V 轴指向左下棱
        (top, EMERALD_TOP, (CX, CY - HH), (HW, HH), (-HW, HH), 3, 3,
         [[-6, 12, 0], [16, 30, 8], [-2, 14, -4]]),
        # 左面：U 轴沿上棱向右下，V 轴向下
        (left, EMERALD_LEFT, (CX - HW, CY), (HW, HH), (0, SIDE), 4, 3,
         [[10, -8, 14, 4], [18, -6, 6, 20], [4, 12, -8, 8]]),
        # 右面：U 轴沿上棱向右上，V 轴向下
        (right, EMERALD_RIGHT, (CX, CY + HH), (HW, -HH), (0, SIDE), 4, 3,
         [[8, -6, 12, 2], [14, 4, -8, 10], [-4, 8, 4, -6]]),
    ]

    for pts, base, A, T1, T2, cols, rows, deltas in faces_def:
        layer = Image.new("RGB", (W, W), base)
        ld = ImageDraw.Draw(layer)

        def P(a, b):
            return (A[0] + a * T1[0] + b * T2[0], A[1] + a * T1[1] + b * T2[1])

        for j in range(rows):
            for i in range(cols):
                a0, b0 = i / float(cols), j / float(rows)
                a1, b1 = (i + 1) / float(cols), (j + 1) / float(rows)
                draw_poly(ld, [P(a0, b0), P(a1, b0), P(a1, b1), P(a0, b1)],
                          fill=shade(base, deltas[j][i]))

        img.paste(layer, (0, 0), face_mask(pts))

    # 暖色边缘光（右侧来光，和火花的暖调呼应）
    d = ImageDraw.Draw(img)
    rim = [right[0], right[1]]
    d.line([(s(right[1][0]), s(right[1][1])), (s(right[2][0]), s(right[2][1]))],
           fill=(128, 176, 122), width=s(2.5))
    d.line([(s(right[0][0]), s(right[0][1])), (s(right[1][0]), s(right[1][1]))],
           fill=(108, 158, 104), width=s(2.5))

    # 面缘描边
    for pts in faces():
        draw_poly(d, pts, outline=EMERALD_DARK, width=2.5)


# ---------------------------------------------------------------- 火花
def gem_star(img, d, cx=222, cy=224, r=19):
    """顶面的一颗星芒高光，让它读起来像宝石而不是纯色方块。"""
    halo = Image.new("L", (W, W), 0)
    ImageDraw.Draw(halo).ellipse([s(cx - r), s(cy - r), s(cx + r), s(cy + r)], fill=150)
    halo = halo.filter(ImageFilter.GaussianBlur(s(9)))
    img.paste(Image.new("RGB", (W, W), (198, 255, 220)), (0, 0), halo)

    d = ImageDraw.Draw(img)
    k = r * 1.15
    star = [(cx, cy - k), (cx + r * 0.30, cy - r * 0.30), (cx + k, cy),
            (cx + r * 0.30, cy + r * 0.30), (cx, cy + k), (cx - r * 0.30, cy + r * 0.30),
            (cx - k, cy), (cx - r * 0.30, cy - r * 0.30)]
    draw_poly(d, star, fill=(246, 255, 250))
    draw_poly(d, [(cx, cy - r * 0.42), (cx + r * 0.18, cy), (cx, cy + r * 0.42), (cx - r * 0.18, cy)],
              fill=(255, 255, 255))


def sparks(d):
    for fx, fy, r, a in [(0.17, 0.26, 9, 1.0), (0.83, 0.30, 7, 1.0), (0.10, 0.55, 5, 0.85),
                         (0.90, 0.58, 6, 0.85), (0.30, 0.13, 4, 0.7), (0.71, 0.11, 5, 0.75),
                         (0.50, 0.93, 4, 0.6), (0.22, 0.88, 4, 0.6)]:
        cx, cy, r = 512 * fx, 512 * fy, r
        draw_poly(d, [(cx, cy - r), (cx + r * 0.55, cy), (cx, cy + r), (cx - r * 0.55, cy)],
                  fill=tuple(int(c * a) for c in (255, 188, 78)))
        draw_poly(d, [(cx, cy - r * 0.48), (cx + r * 0.26, cy), (cx, cy + r * 0.48), (cx - r * 0.26, cy)],
                  fill=(255, 244, 205))


def main():
    img = Image.new("RGB", (W, W), BG)
    d = ImageDraw.Draw(img)
    draw_background(img, d)
    d = ImageDraw.Draw(img)

    # 交叉的两把剑：握把藏在方块后面，剑尖朝斜上探出，构成 X 形轮廓
    sword(d, 232, 512, -32, 1.02)
    sword(d, 280, 512, 32, 1.02)

    # 地面投影
    sh = Image.new("L", (W, W), 0)
    sd = ImageDraw.Draw(sh)
    sd.ellipse([s(96), s(392), s(416), s(468)], fill=110)
    sh = sh.filter(ImageFilter.GaussianBlur(s(26)))
    img.paste(Image.new("RGB", (W, W), (2, 3, 5)), (0, 0), sh)
    d = ImageDraw.Draw(img)

    draw_cube(img, d)
    gem_star(img, d)

    d = ImageDraw.Draw(img)
    sparks(d)

    # 暗角（保留中心亮度）
    vign = Image.new("L", (W, W), 0)
    vd = ImageDraw.Draw(vign)
    for i in range(48):
        r = s(400) - i * s(5)
        vd.ellipse([s(256) - r, s(256) - r, s(256) + r, s(256) + r], fill=int(120 * (i / 48.0) ** 1.4))
    vign = vign.filter(ImageFilter.GaussianBlur(s(34)))
    img = Image.composite(Image.new("RGB", (W, W), (4, 5, 8)), img, vign)

    # 内描边边框
    d = ImageDraw.Draw(img)
    m = s(14)
    d.rectangle([m, m, W - m, W - m], outline=(6, 8, 12), width=s(7))
    d.rectangle([m, m, W - m, W - m], outline=(62, 76, 96), width=s(2))

    os.makedirs(OUT_DIR, exist_ok=True)
    icon = img.resize((SIZE, SIZE), Image.LANCZOS)
    icon.save(os.path.join(OUT_DIR, "icon.png"))
    for px in (128, 64):
        icon.resize((px, px), Image.LANCZOS).save(os.path.join(OUT_DIR, "icon-%d.png" % px))
    print("ok ->", os.path.join(OUT_DIR, "icon.png"))


if __name__ == "__main__":
    main()

# -*- coding: utf-8 -*-
"""生成 GitHub Social Preview 图（1280x640）。

用法：python tools/MakeSocialPreview.py
产物：assets/social-preview.png

GitHub 的分享卡片（Settings -> Social preview）要求 1280x640 横图，
直接用正方形的 icon.png 会被裁切，所以这里做一张横版：
左侧图标 + 右侧标题与要点，配色沿用插件图标本身的深板岩底与绿宝石绿。
"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets")
ICON = os.path.join(ASSETS, "icon.png")
OUT = os.path.join(ASSETS, "social-preview.png")

W, H = 1280, 640
SS = 2                      # 超采样，缩小后文字边缘更干净
CW, CH = W * SS, H * SS

BG_TOP = (24, 28, 37)
BG_BOTTOM = (9, 11, 15)
ACCENT = (55, 211, 155)
GOLD = (245, 185, 66)
TEXT = (231, 234, 241)
MUTED = (139, 149, 169)
LINE = (39, 44, 57)

FONT_CANDIDATES_BOLD = [
    r"C:\Windows\Fonts\msyhbd.ttc",
    r"C:\Windows\Fonts\segoeuib.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]
FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\segoeui.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]


def font(candidates, size):
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def s(v):
    return int(round(v * SS))


def main():
    # ---- 背景：垂直渐变 + 左侧绿色辉光 ----
    base = Image.new("RGB", (CW, CH), BG_BOTTOM)
    draw = ImageDraw.Draw(base)
    for y in range(CH):
        t = y / CH
        draw.line(
            [(0, y), (CW, y)],
            fill=(
                int(BG_TOP[0] + (BG_BOTTOM[0] - BG_TOP[0]) * t),
                int(BG_TOP[1] + (BG_BOTTOM[1] - BG_TOP[1]) * t),
                int(BG_TOP[2] + (BG_BOTTOM[2] - BG_TOP[2]) * t),
            ),
        )
    base = base.convert("RGBA")

    glow = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse(
        [s(-180), s(60), s(700), s(600)], fill=ACCENT + (70,)
    )
    base = Image.alpha_composite(base, glow.filter(ImageFilter.GaussianBlur(s(70))))

    # 细网格，让深色背景不显得空
    grid = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grid)
    for x in range(0, CW, s(48)):
        gd.line([(x, 0), (x, CH)], fill=(255, 255, 255, 6))
    for y in range(0, CH, s(48)):
        gd.line([(0, y), (CW, y)], fill=(255, 255, 255, 6))
    base = Image.alpha_composite(base, grid)

    # ---- 左侧图标 ----
    icon_size = s(372)
    icon = Image.open(ICON).convert("RGBA")
    icon = icon.resize((icon_size, icon_size), Image.LANCZOS)
    icon_x, icon_y = s(84), (CH - icon_size) // 2
    halo = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
    ImageDraw.Draw(halo).rounded_rectangle(
        [icon_x, icon_y, icon_x + icon_size, icon_y + icon_size],
        radius=s(28), fill=ACCENT + (48,),
    )
    base = Image.alpha_composite(base, halo.filter(ImageFilter.GaussianBlur(s(30))))
    base.alpha_composite(icon, (icon_x, icon_y))

    draw = ImageDraw.Draw(base)

    # ---- 右侧文字 ----
    text_x = s(520)
    title_font = font(FONT_CANDIDATES_BOLD, s(112))
    sub_font = font(FONT_CANDIDATES, s(38))
    body_font = font(FONT_CANDIDATES, s(30))
    tag_font = font(FONT_CANDIDATES, s(26))

    title = "LiteBrawl"
    draw.text((text_x, s(146)), title, font=title_font, fill=TEXT)

    title_w = draw.textlength(title, font=title_font)
    draw.rounded_rectangle(
        [text_x, s(292), text_x + s(120), s(298)], radius=s(3), fill=ACCENT
    )

    draw.text((text_x, s(330)), "Paper 1.21.1 迷你大乱斗插件", font=sub_font, fill=TEXT)

    lines = [
        "随机绿宝石出生点 · 固定装备 · 4 支自动补箭",
        "连杀赏金 · 6 类外观 · 全息排行榜 · 陪练假人",
        "内置网页控制台：改配置 / 看数据 / 玩家面板",
    ]
    y = s(398)
    for line in lines:
        draw.ellipse([text_x, y + s(11), text_x + s(9), y + s(20)], fill=ACCENT)
        draw.text((text_x + s(26), y), line, font=body_font, fill=MUTED)
        y += s(46)

    # ---- 底部信息条 ----
    draw.line([(s(84), s(556)), (CW - s(84), s(556))], fill=LINE, width=s(2))
    draw.text((s(84), s(578)), "Paper 1.21.1 · Java 21 · MIT License", font=tag_font, fill=MUTED)

    text_w = draw.textlength("github.com/DengzaDev/LiteBrawl", font=tag_font)
    draw.text((CW - s(84) - text_w, s(578)), "github.com/DengzaDev/LiteBrawl",
              font=tag_font, fill=GOLD)

    os.makedirs(ASSETS, exist_ok=True)
    final = base.convert("RGB").resize((W, H), Image.LANCZOS)
    final.save(OUT, "PNG", optimize=True)
    print("已生成 " + OUT)


if __name__ == "__main__":
    main()

"""Generate Google Play Store promotional graphics for KULMS+ WebView.

出力:
- feature_graphic.png         (1024 x 500)  フィーチャーグラフィック
- phone_01_assignment.png     (1080 x 1920) 9:16 スマホ用
- phone_02_textbooks.png      (1080 x 1920) 9:16 スマホ用
- tablet7_01_assignment.png   (1920 x 1080) 16:9 7インチタブレット
- tablet7_02_textbooks.png    (1920 x 1080) 16:9 7インチタブレット
- tablet10_01_assignment.png  (2560 x 1440) 16:9 10インチタブレット
- tablet10_02_textbooks.png   (2560 x 1440) 16:9 10インチタブレット

ソース画像: kulms-extension/docs/images/ の拡張機能スクリーンショット
"""

from PIL import Image, ImageDraw, ImageFont, ImageFilter
import numpy as np
import os

OUTPUT_DIR = os.path.dirname(os.path.abspath(__file__))
EXT_IMAGES = os.path.join(os.path.dirname(OUTPUT_DIR), "..", "kulms-extension", "docs", "images")

FONT_BOLD = "/System/Library/Fonts/ヒラギノ角ゴシック W6.ttc"
FONT_REGULAR = "/System/Library/Fonts/ヒラギノ角ゴシック W4.ttc"

SRC_01 = os.path.join(EXT_IMAGES, "assignments.png")
SRC_02 = os.path.join(EXT_IMAGES, "textbooks.png")

# 画面1: 課題一覧（青系）
BG1_TOP = (41, 98, 255)
BG1_BOT = (88, 166, 255)
TITLE1 = "全科目の課題を\nひと目で確認"
SUB1 = "緊急度別に色分け表示"

# 画面2: 教科書（紫系）
BG2_TOP = (109, 58, 230)
BG2_BOT = (170, 120, 255)
TITLE2 = "教科書・参考書を\n自動で取得"
SUB2 = "Amazonリンク付きですぐに購入可能"

# タブレット用
TITLE_T1 = "全科目の課題を\nまとめて確認"
SUB_T1 = "緊急度別に色分け表示"
TITLE_T2 = "教科書・参考書を\n自動で取得"
SUB_T2 = "KULASISシラバスから教科書情報を取得"


def make_gradient(width, height, top, bottom):
    arr = np.zeros((height, width, 3), dtype=np.uint8)
    for c in range(3):
        arr[:, :, c] = np.linspace(top[c], bottom[c], height, dtype=np.uint8)[:, None]
    return Image.fromarray(arr)


def make_gradient_horizontal(width, height, left, right):
    arr = np.zeros((height, width, 3), dtype=np.uint8)
    for c in range(3):
        arr[:, :, c] = np.linspace(left[c], right[c], width, dtype=np.uint8)[None, :]
    return Image.fromarray(arr)


def add_rounded_corners(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([(0, 0), img.size], radius=radius, fill=255)
    result = Image.new("RGBA", img.size, (0, 0, 0, 0))
    result.paste(img, mask=mask)
    return result


def add_shadow(img, offset=(0, 15), blur_radius=40, opacity=60):
    shadow = Image.new(
        "RGBA", (img.width + blur_radius * 2, img.height + blur_radius * 2), (0, 0, 0, 0)
    )
    inner = Image.new("RGBA", img.size, (0, 0, 0, opacity))
    if img.mode == "RGBA":
        inner.putalpha(img.split()[3].point(lambda p: min(p, opacity)))
    shadow.paste(inner, (blur_radius + offset[0], blur_radius + offset[1]))
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur_radius))
    return shadow, blur_radius


def draw_centered_text(draw, text, y, width, font, fill="white", line_height=None):
    if line_height is None:
        line_height = int(font.size * 1.25)
    for line in text.split("\n"):
        bbox = font.getbbox(line)
        lw = bbox[2] - bbox[0]
        draw.text(((width - lw) / 2, y), line, fill=fill, font=font)
        y += line_height
    return y


# ============ 1. フィーチャーグラフィック (1024x500) ============
def gen_feature_graphic():
    W, H = 1024, 500
    bg = make_gradient_horizontal(W, H, (41, 98, 255), (109, 58, 230)).convert("RGBA")
    draw = ImageDraw.Draw(bg)

    title_font = ImageFont.truetype(FONT_BOLD, 78)
    sub_font = ImageFont.truetype(FONT_REGULAR, 36)

    draw.text((60, 130), "KULMS+", fill="white", font=title_font)
    draw.text((60, 230), "京都大学LMS 拡張アプリ", fill=(255, 255, 255, 230), font=sub_font)
    draw.text((60, 290), "全科目の課題・テストを一覧管理", fill=(255, 255, 255, 200), font=sub_font)
    draw.text((60, 350), "教科書の自動取得・締切通知", fill=(255, 255, 255, 200), font=sub_font)

    ss = Image.open(SRC_01).convert("RGBA")
    target_h = int(H * 0.85)
    scale = target_h / ss.height
    target_w = int(ss.width * scale)
    ss = ss.resize((target_w, target_h), Image.LANCZOS)
    ss = add_rounded_corners(ss, 25)

    shadow, blur_r = add_shadow(ss, offset=(0, 10), blur_radius=25, opacity=80)
    ss_x = W - target_w - 80
    ss_y = (H - target_h) // 2
    bg.paste(shadow, (ss_x - blur_r, ss_y - blur_r), shadow)
    bg.paste(ss, (ss_x, ss_y), ss)

    out = os.path.join(OUTPUT_DIR, "feature_graphic.png")
    bg.convert("RGB").save(out, "PNG", optimize=True)
    print(f"Saved: {out} ({W}x{H})")


# ============ 2. スマホ用 (1080x1920, 9:16) ============
def gen_phone(src, title, subtitle, bg_top, bg_bot, output):
    W, H = 1080, 1920
    bg = make_gradient(W, H, bg_top, bg_bot).convert("RGBA")
    draw = ImageDraw.Draw(bg)

    title_font = ImageFont.truetype(FONT_BOLD, 76)
    sub_font = ImageFont.truetype(FONT_REGULAR, 42)

    y = 140
    y = draw_centered_text(draw, title, y, W, title_font, line_height=96)
    y += 20
    draw_centered_text(draw, subtitle, y, W, sub_font, fill=(255, 255, 255, 220))

    ss = Image.open(src).convert("RGBA")
    target_w = int(W * 0.92)
    scale = target_w / ss.width
    target_h = int(ss.height * scale)
    ss = ss.resize((target_w, target_h), Image.LANCZOS)
    ss = add_rounded_corners(ss, 36)

    shadow, blur_r = add_shadow(ss, offset=(0, 15), blur_radius=40, opacity=60)
    ss_x = (W - target_w) // 2
    ss_y = 440
    bg.paste(shadow, (ss_x - blur_r, ss_y - blur_r), shadow)
    bg.paste(ss, (ss_x, ss_y), ss)

    out = os.path.join(OUTPUT_DIR, output)
    bg.convert("RGB").save(out, "PNG", optimize=True)
    print(f"Saved: {out} ({W}x{H})")


# ============ 3. タブレット (16:9 横長) ============
def gen_tablet(W, H, src, title, subtitle, bg_top, bg_bot, output, title_size, sub_size):
    bg = make_gradient(W, H, bg_top, bg_bot).convert("RGBA")
    draw = ImageDraw.Draw(bg)

    title_font = ImageFont.truetype(FONT_BOLD, title_size)
    sub_font = ImageFont.truetype(FONT_REGULAR, sub_size)

    y = int(H * 0.06)
    for line in title.split("\n"):
        bbox = title_font.getbbox(line)
        lw = bbox[2] - bbox[0]
        draw.text(((W - lw) / 2, y), line, fill="white", font=title_font)
        y += int(title_size * 1.25)

    y += 20
    sub_bbox = sub_font.getbbox(subtitle)
    sub_w = sub_bbox[2] - sub_bbox[0]
    draw.text(((W - sub_w) / 2, y), subtitle, fill=(255, 255, 255, 220), font=sub_font)
    text_bottom = y + sub_size + 30

    ss = Image.open(src).convert("RGBA")
    available_h = H - text_bottom - int(H * 0.06)
    target_h = available_h
    target_w = int(ss.width * (target_h / ss.height))
    max_w = int(W * 0.88)
    if target_w > max_w:
        target_w = max_w
        target_h = int(ss.height * (target_w / ss.width))
    ss = ss.resize((target_w, target_h), Image.LANCZOS)

    corner_r = int(36 * (H / 1920))
    ss = add_rounded_corners(ss, corner_r)

    blur_r = int(40 * (H / 1920))
    shadow, blur_r = add_shadow(ss, offset=(0, 15), blur_radius=blur_r, opacity=60)

    ss_x = (W - target_w) // 2
    ss_y = text_bottom + (available_h - target_h) // 2
    bg.paste(shadow, (ss_x - blur_r, ss_y - blur_r), shadow)
    bg.paste(ss, (ss_x, ss_y), ss)

    out = os.path.join(OUTPUT_DIR, output)
    bg.convert("RGB").save(out, "PNG", optimize=True)
    print(f"Saved: {out} ({W}x{H})")


if __name__ == "__main__":
    gen_feature_graphic()

    gen_phone(SRC_01, TITLE1, SUB1, BG1_TOP, BG1_BOT, "phone_01_assignment.png")
    gen_phone(SRC_02, TITLE2, SUB2, BG2_TOP, BG2_BOT, "phone_02_textbooks.png")

    gen_tablet(1920, 1080, SRC_01, TITLE_T1, SUB_T1, BG1_TOP, BG1_BOT,
               "tablet7_01_assignment.png", title_size=72, sub_size=38)
    gen_tablet(1920, 1080, SRC_02, TITLE_T2, SUB_T2, BG2_TOP, BG2_BOT,
               "tablet7_02_textbooks.png", title_size=72, sub_size=38)

    gen_tablet(2560, 1440, SRC_01, TITLE_T1, SUB_T1, BG1_TOP, BG1_BOT,
               "tablet10_01_assignment.png", title_size=96, sub_size=50)
    gen_tablet(2560, 1440, SRC_02, TITLE_T2, SUB_T2, BG2_TOP, BG2_BOT,
               "tablet10_02_textbooks.png", title_size=96, sub_size=50)

    print("Done!")

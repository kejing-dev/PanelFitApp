"""Contrast table for ui-audit.md (taste D13): every text / icon colour on every background it is drawn on.
Theme roles come from the app's log line `PF-COLORS ...` (resolved PicoTheme colours); the glass colour is sampled
from an HD screenshot (the window and labels are system glass, so their real background depends on the room).
Translucent colours are composited over the glass first.

Usage: python tools/contrast.py ["<PF-COLORS line>"] [--glass #RRGGBB] [--solid-dark #RRGGBB]
Prints a Markdown table; exit code 1 when a pair is under 4.5:1 (text) or 3:1 (icons / large text)."""
import re
import sys

BRAND = {
    "ink": "#FF1C1F22",
    "good": "#FF5BD68A",
    "alertBrand": "#FFFFB020",
    "errorBrand": "#FFFF5A4E",
    "solidDark": "#FF2B2F36",
    "solidLight": "#FFECEEF2",
    "white": "#FFFFFFFF",
    "black": "#FF000000",
}


def parse(c):
    c = c.lstrip("#")
    if len(c) == 6:
        c = "FF" + c
    a, r, g, b = (int(c[i:i + 2], 16) / 255 for i in (0, 2, 4, 6))
    return a, r, g, b


def over(fg, bg):
    a, r, g, b = fg
    _, br, bgg, bb = bg
    return 1.0, r * a + br * (1 - a), g * a + bgg * (1 - a), b * a + bb * (1 - a)


def lum(c):
    def ch(v):
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4

    _, r, g, b = c
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def ratio(fg, bg):
    l1, l2 = lum(fg), lum(bg)
    hi, lo = max(l1, l2), min(l1, l2)
    return (hi + 0.05) / (lo + 0.05)


def main(argv):
    colors = dict(BRAND)
    glass = None
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--glass":
            glass = argv[i + 1]
            i += 2
            continue
        for k, v in re.findall(r"(\w+)=(#[0-9A-Fa-f]{6,8})", a):
            colors[k] = v
        i += 1
    if glass:
        colors["glass"] = glass
    c = {k: parse(v) for k, v in colors.items()}
    rows = []

    def pair(name, fg, bg, need, where):
        if fg not in c or bg not in c:
            rows.append((name, where, "—", need, "needs PF-COLORS / glass sample"))
            return
        b = c[bg]
        if b[0] < 1 and "glass" in c:
            b = over(b, c["glass"])
        f = c[fg]
        if f[0] < 1:
            f = over(f, b)
        r = ratio(f, b)
        rows.append((name, where, "%.2f : 1" % r, need, "ok" if r >= need else "FAIL"))

    # tool window (system glass) and size labels
    pair("labelPrimary / glass", "labelPrimary", "glass", 4.5, "window text, labels")
    pair("labelSecondary / glass", "labelSecondary", "glass", 4.5, "small print, sub lines")
    pair("labelPrimary / fillTertiary", "labelPrimary", "fillTertiary", 4.5, "buttons, cards, steppers, hint row")
    pair("labelTertiary / fillTertiary", "labelTertiary", "fillTertiary", 3.0, "disabled key icon (icon, 3:1)")
    pair("ink / labelPrimaryLight", "ink", "labelPrimaryLight", 4.5, "primary buttons (完成, 存成文件, 试一块, 打开面板)")
    pair("ink / interaction", "ink", "interaction", 4.5, "overlay toggles on, stick / drag hint row")
    pair("ink / error", "ink", "error", 4.5, "删除 / 清空 / 退出 confirm buttons")
    pair("alert / glass", "alert", "glass", 4.5, "label line 2 when covering, card 遮挡 row (14 sp text)")
    pair("good / ink", "good", "ink", 3.0, "reference dot 够大")
    pair("alertBrand / ink", "alertBrand", "ink", 3.0, "reference dot 偏小")
    pair("errorBrand / ink", "errorBrand", "ink", 3.0, "reference dot 太小")
    pair("labelPrimary / solidDark", "labelPrimary", "solidDark", 4.5, "specimen 实色 under light text")
    pair("labelPrimary / solidLight", "labelPrimary", "solidLight", 4.5, "specimen 实色 under dark text")
    pair("black / white", "black", "white", 3.0, "ruler ticks")
    print("| 前景 / 底 | 用在哪 | 比值 | 门槛 | 结论 |")
    print("|---|---|---|---|---|")
    bad = False
    for name, where, r, need, verdict in rows:
        print("| %s | %s | %s | %.1f | %s |" % (name, where, r, need, verdict))
        bad = bad or verdict == "FAIL"
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

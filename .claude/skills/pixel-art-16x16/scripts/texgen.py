#!/usr/bin/env python3
"""texgen.py - herramienta de pixel art para texturas de Minecraft (16x16 y múltiplos).

Subcomandos:
  draw      spec.json -> PNG           Dibuja desde filas de caracteres + paleta.
  inspect   PNG -> spec JSON (stdout)  Convierte una textura existente en filas+paleta.
  preview   PNG -> PNG ampliado         Ampliación nearest-neighbor con tablero y rejilla, para revisar.
  validate  PNG [...]                   Comprueba tamaño, modo, animación (.mcmeta) y nº de colores.
  ramp      #hex N                      Rampa de sombreado con desplazamiento de tono (oscuro -> claro).
  recolor   PNG map.json -> PNG         Cambia colores (variantes: lingote de cobre -> de rubí, etc.).

Formato spec.json:
{
  "size": [16, 16],
  "palette": {".": "transparent", "K": "#2b1d0e", "R": "#c0392b", "r": "#e74c3c"},
  "rows": ["................", "...KKKK.........", ...]   # exactamente H filas de W caracteres
}
Requiere Pillow (pip install pillow).
"""
import argparse
import colorsys
import json
import os
import sys

from PIL import Image

TRANSPARENT = (0, 0, 0, 0)
KEYS = ".ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#@$%&*+=-~"


def parse_color(value):
    if value is None or str(value).lower() in ("transparent", "none", ""):
        return TRANSPARENT
    v = str(value).lstrip("#")
    if len(v) == 6:
        return tuple(int(v[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
    if len(v) == 8:
        return tuple(int(v[i:i + 2], 16) for i in (0, 2, 4, 6))
    raise ValueError(f"Color no válido: {value}")


def to_hex(rgba):
    if rgba[3] == 0:
        return "transparent"
    h = "#%02x%02x%02x" % rgba[:3]
    return h if rgba[3] == 255 else h + "%02x" % rgba[3]


def cmd_draw(a):
    with open(a.spec, encoding="utf-8") as f:
        spec = json.load(f)
    rows = spec["rows"]
    w, h = spec.get("size", [len(rows[0]), len(rows)])
    palette = {k: parse_color(v) for k, v in spec["palette"].items()}
    palette.setdefault(".", TRANSPARENT)
    errors = []
    if len(rows) != h:
        errors.append(f"Hay {len(rows)} filas, se esperaban {h}")
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    for y, row in enumerate(rows[:h]):
        if len(row) != w:
            errors.append(f"Fila {y} tiene {len(row)} caracteres, se esperaban {w}: '{row}'")
        for x, ch in enumerate(row[:w]):
            if ch not in palette:
                errors.append(f"Carácter '{ch}' en ({x},{y}) no está en la paleta")
                continue
            img.putpixel((x, y), palette[ch])
    if errors:
        print("ERRORES:\n  " + "\n  ".join(errors), file=sys.stderr)
        sys.exit(1)
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    img.save(a.out)
    print(f"OK {a.out} ({w}x{h})")
    if a.preview:
        _preview(img, a.preview, a.scale, True)
        print(f"Preview: {a.preview}")


def cmd_inspect(a):
    img = Image.open(a.png).convert("RGBA")
    w, h = img.size
    palette, rows = {}, []
    rev = {}
    for y in range(h):
        row = ""
        for x in range(w):
            px = img.getpixel((x, y))
            if px[3] == 0:
                px = TRANSPARENT
            if px not in rev:
                if len(rev) >= len(KEYS):
                    sys.exit("Demasiados colores para representar como texto")
                key = "." if px == TRANSPARENT else KEYS[1 + sum(1 for k in rev.values() if k != ".")]
                rev[px] = key
                palette[key] = to_hex(px)
            row += rev[px]
        rows.append(row)
    print(json.dumps({"size": [w, h], "palette": palette, "rows": rows}, indent=2, ensure_ascii=False))


def _preview(img, out, scale, grid):
    w, h = img.size
    big = Image.new("RGBA", (w * scale, h * scale))
    c1, c2 = (200, 200, 200, 255), (160, 160, 160, 255)
    cell = max(scale // 2, 1)
    for y in range(h * scale):
        for x in range(w * scale):
            big.putpixel((x, y), c1 if ((x // cell) + (y // cell)) % 2 == 0 else c2)
    up = img.resize((w * scale, h * scale), Image.NEAREST)
    big.alpha_composite(up)
    if grid and scale >= 8:
        for i in range(0, w * scale, scale):
            for y in range(h * scale):
                big.putpixel((i, y), (90, 90, 90, 255))
        for j in range(0, h * scale, scale):
            for x in range(w * scale):
                big.putpixel((x, j), (90, 90, 90, 255))
    big.save(out)


def cmd_preview(a):
    img = Image.open(a.png).convert("RGBA")
    _preview(img, a.out, a.scale, not a.no_grid)
    print(f"OK {a.out}")


def cmd_validate(a):
    bad = False
    for path in a.pngs:
        msgs = []
        img = Image.open(path)
        w, h = img.size
        if img.mode not in ("RGBA", "RGB", "P", "LA"):
            msgs.append(f"modo de color raro: {img.mode}")
        if w % 16 != 0 or w & (w - 1):
            msgs.append(f"ancho {w} no es 16/32/64… (potencia de 2 múltiplo de 16)")
        if h != w:
            if h % w == 0:
                if not os.path.exists(path + ".mcmeta"):
                    msgs.append(f"{h // w} frames apilados pero falta {os.path.basename(path)}.mcmeta (animación)")
            else:
                msgs.append(f"tamaño {w}x{h} no es cuadrado ni tira de animación")
        rgba = img.convert("RGBA")
        pixels = [rgba.getpixel((x, y)) for y in range(h) for x in range(w)]
        colors = {px for px in pixels if px[3] > 0}
        semi = sum(1 for px in pixels if 0 < px[3] < 255)
        info = f"{w}x{h}, {len(colors)} colores"
        if semi:
            info += f", {semi} px semitransparentes (requiere render translúcido en bloques)"
        if len(colors) > 32 and w == 16:
            msgs.append(f"{len(colors)} colores en 16x16: el estilo vanilla suele usar ≤ 16")
        status = "AVISO" if msgs else "OK"
        bad |= bool(msgs)
        print(f"[{status}] {path}: {info}")
        for m in msgs:
            print(f"    - {m}")
    sys.exit(1 if bad and a.strict else 0)


def cmd_ramp(a):
    r, g, b, _ = parse_color(a.color)
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    out = []
    n = a.steps
    for i in range(n):
        t = (i / (n - 1)) - 0.5 if n > 1 else 0  # -0.5 (sombra) .. +0.5 (luz)
        nl = min(max(l + t * a.spread, 0.04), 0.96)
        # sombras hacia azul/morado, luces hacia amarillo: desplazamiento de tono
        nh = (h - t * a.hue_shift / 360) % 1.0
        ns = min(max(s * (1 - abs(t) * 0.35), 0), 1)
        rr, gg, bb = colorsys.hls_to_rgb(nh, nl, ns)
        out.append("#%02x%02x%02x" % (round(rr * 255), round(gg * 255), round(bb * 255)))
    print(json.dumps(out))


def cmd_recolor(a):
    img = Image.open(a.png).convert("RGBA")
    with open(a.map, encoding="utf-8") as f:
        mapping = {parse_color(k)[:3]: parse_color(v) for k, v in json.load(f).items()}
    px = img.load()
    changed = 0
    for y in range(img.height):
        for x in range(img.width):
            p = px[x, y]
            if p[3] and p[:3] in mapping:
                nc = mapping[p[:3]]
                px[x, y] = nc[:3] + (p[3],)
                changed += 1
    img.save(a.out)
    print(f"OK {a.out} ({changed} px cambiados)")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    d = sub.add_parser("draw"); d.add_argument("spec"); d.add_argument("out")
    d.add_argument("--preview"); d.add_argument("--scale", type=int, default=24); d.set_defaults(f=cmd_draw)

    i = sub.add_parser("inspect"); i.add_argument("png"); i.set_defaults(f=cmd_inspect)

    v = sub.add_parser("preview"); v.add_argument("png"); v.add_argument("out")
    v.add_argument("--scale", type=int, default=24); v.add_argument("--no-grid", action="store_true")
    v.set_defaults(f=cmd_preview)

    c = sub.add_parser("validate"); c.add_argument("pngs", nargs="+"); c.add_argument("--strict", action="store_true")
    c.set_defaults(f=cmd_validate)

    r = sub.add_parser("ramp"); r.add_argument("color"); r.add_argument("steps", type=int, nargs="?", default=5)
    r.add_argument("--spread", type=float, default=0.55); r.add_argument("--hue-shift", type=float, default=20)
    r.set_defaults(f=cmd_ramp)

    m = sub.add_parser("recolor"); m.add_argument("png"); m.add_argument("map"); m.add_argument("out")
    m.set_defaults(f=cmd_recolor)

    a = p.parse_args()
    a.f(a)


if __name__ == "__main__":
    main()

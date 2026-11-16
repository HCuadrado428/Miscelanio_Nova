#!/usr/bin/env python3
"""Hook PostToolUse del plugin minecraft-modding.

Se ejecuta cada vez que Claude escribe o edita un fichero. Si el fichero es un recurso
de Minecraft (assets/ o data/), lo valida:

  - rutas en minúsculas y con caracteres válidos
  - PNG de texturas: tamaño, animaciones (.mcmeta)
  - JSON: sintaxis; modelos (límites de cubos, rotaciones, texturas/padres que faltan);
    blockstates y definiciones de item (modelos que faltan); lang; carpetas plural/singular de data

Errores  -> exit 2 + stderr (Claude lo ve y debe corregirlo).
Avisos   -> exit 0 + JSON con additionalContext (Claude lo ve como contexto).
Cualquier fichero que no sea un recurso de Minecraft se ignora.
"""
import json
import os
import re
import sys

VALID_PATH = re.compile(r"^[a-z0-9_.\-/]+$")
ALLOWED_ANGLES = {-45.0, -22.5, 0.0, 22.5, 45.0}
PLURAL_DATA_DIRS = {
    "tags/blocks": "tags/block", "tags/items": "tags/item", "tags/entity_types": "tags/entity_type",
    "tags/fluids": "tags/fluid", "tags/game_events": "tags/game_event", "tags/functions": "tags/function",
    "loot_tables": "loot_table", "recipes": "recipe", "advancements": "advancement",
    "structures": "structure", "predicates": "predicate", "item_modifiers": "item_modifier",
    "functions": "function",
}


class Report:
    def __init__(self):
        self.errors, self.warnings = [], []

    def error(self, msg):
        self.errors.append(msg)

    def warn(self, msg):
        self.warnings.append(msg)


def split_resource_path(path):
    """Devuelve (raiz_resources, 'assets'|'data', namespace, resto) o None."""
    p = path.replace("\\", "/")
    m = re.search(r"^(.*?/)(assets|data)/([^/]+)/(.+)$", p)
    if not m:
        return None
    return m.group(1), m.group(2), m.group(3), m.group(4)


def parse_ref(ref, default_ns):
    ref = ref.split("#")[0]
    if ":" in ref:
        ns, path = ref.split(":", 1)
    else:
        ns, path = "minecraft", ref
    return ns or default_ns, path


def check_png(abs_path, kind_path, rep):
    try:
        from PIL import Image
    except ImportError:
        return  # sin Pillow no validamos imágenes; mc-doctor ya lo avisa
    try:
        img = Image.open(abs_path)
        w, h = img.size
    except Exception as e:  # noqa: BLE001
        rep.error(f"No se puede abrir el PNG: {e}")
        return
    pow2 = lambda n: n > 0 and (n & (n - 1)) == 0  # noqa: E731
    if kind_path.startswith(("textures/item/", "textures/block/")):
        if not pow2(w) or w < 16 or not (pow2(h) or h % w == 0):
            rep.error(f"Textura de item/bloque de {w}x{h}: el ancho debe ser potencia de 2 (16, 32, 64…) y el alto "
                      "potencia de 2 o múltiplo del ancho (tira de animación).")
        elif h > w and h % w == 0 and not os.path.exists(abs_path + ".mcmeta"):
            rep.warn(f"{w}x{h} ({h // w} cuadros apilados): si es una animación falta {os.path.basename(abs_path)}.mcmeta; "
                     "si es la textura UV de un modelo 3D o de GeckoLib, ignora este aviso.")
    elif kind_path.startswith("textures/entity/") and not (pow2(w) and pow2(h)):
        # (los iconos de efecto son 18x18 y las GUI tienen tamaños libres: solo se avisa en entidades)
        rep.warn(f"Textura de entidad {w}x{h}: se recomienda que ancho y alto sean potencias de 2.")
    if img.mode not in ("RGBA", "LA", "P", "RGB", "L"):
        rep.warn(f"Modo de color poco habitual: {img.mode}. Usa RGBA.")


def check_model(data, root, ns, rep):
    for i, el in enumerate(data.get("elements", []) or []):
        for key in ("from", "to"):
            for v in el.get(key, []) or []:
                if isinstance(v, (int, float)) and not -16 <= v <= 32:
                    rep.error(f"elements[{i}].{key} = {el.get(key)}: las coordenadas deben estar entre -16 y 32.")
                    break
        rot = el.get("rotation")
        if isinstance(rot, dict) and "angle" in rot:
            try:
                angle = float(rot["angle"])
            except (TypeError, ValueError):
                rep.error(f"elements[{i}].rotation.angle no es un número.")
                continue
            if angle not in ALLOWED_ANGLES:
                rep.warn(f"elements[{i}].rotation.angle = {angle}: hasta 1.21.x solo se garantizan -45/-22.5/0/22.5/45.")
    for name, ref in (data.get("textures") or {}).items():
        if isinstance(ref, str) and not ref.startswith("#"):
            rns, rpath = parse_ref(ref, ns)
            if rns == ns and not os.path.exists(os.path.join(root, "assets", rns, "textures", rpath + ".png")):
                rep.warn(f"textures.{name} -> '{ref}': no existe assets/{rns}/textures/{rpath}.png (¿falta crearla?).")
    parent = data.get("parent")
    if isinstance(parent, str) and not parent.startswith("builtin/"):
        pns, ppath = parse_ref(parent, ns)
        if pns == ns and not os.path.exists(os.path.join(root, "assets", pns, "models", ppath + ".json")):
            rep.warn(f"parent '{parent}': no existe assets/{pns}/models/{ppath}.json.")


def collect_model_refs(node, out, key_is_model=False):
    if isinstance(node, dict):
        for k, v in node.items():
            collect_model_refs(v, out, k == "model")
    elif isinstance(node, list):
        for v in node:
            collect_model_refs(v, out, key_is_model)
    elif isinstance(node, str) and key_is_model:
        out.append(node)


def check_model_refs(data, root, ns, rep, label):
    refs = []
    collect_model_refs(data, refs)
    for ref in sorted(set(refs)):
        if ":" not in ref and "/" not in ref:
            continue  # p. ej. "type": "minecraft:model" ya filtrado; valores raros se ignoran
        rns, rpath = parse_ref(ref, ns)
        if rns == ns and not os.path.exists(os.path.join(root, "assets", rns, "models", rpath + ".json")):
            rep.warn(f"{label} referencia el modelo '{ref}' pero no existe assets/{rns}/models/{rpath}.json.")


def check_json(abs_path, top, ns, rest, root, rep):
    try:
        with open(abs_path, encoding="utf-8") as f:
            data = json.load(f)
    except json.JSONDecodeError as e:
        rep.error(f"JSON no válido (línea {e.lineno}, columna {e.colno}): {e.msg}")
        return
    except UnicodeDecodeError:
        rep.error("El fichero no está en UTF-8.")
        return
    if top == "assets":
        if rest.startswith("models/") and isinstance(data, dict):
            check_model(data, root, ns, rep)
        elif rest.startswith("blockstates/"):
            check_model_refs(data, root, ns, rep, "El blockstate")
        elif rest.startswith("items/"):
            check_model_refs(data, root, ns, rep, "La definición de item (1.21.4+)")
        elif rest.startswith("lang/"):
            if not isinstance(data, dict):
                rep.error("Un fichero lang debe ser un objeto {\"clave\": \"texto\"}.")
            else:
                bad = [k for k, v in data.items() if not isinstance(v, str)]
                if bad:
                    rep.error(f"Valores de lang que no son texto: {', '.join(bad[:5])}")


def project_mc_versions(resources_root):
    """Versiones de Minecraft del proyecto: gradle.properties (minecraft_version) y versions/dependencies/*.properties."""
    d = os.path.abspath(resources_root)
    for _ in range(6):
        if any(os.path.exists(os.path.join(d, f)) for f in ("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")):
            break
        parent = os.path.dirname(d)
        if parent == d:
            return []
        d = parent
    else:
        return []
    versions = []
    files = [os.path.join(d, "gradle.properties")]
    dep_dir = os.path.join(d, "versions", "dependencies")
    if os.path.isdir(dep_dir):
        files += [os.path.join(dep_dir, f) for f in os.listdir(dep_dir) if f.endswith(".properties")]
    for fp in files:
        try:
            with open(fp, encoding="utf-8", errors="replace") as f:
                for line in f:
                    m = re.match(r"\s*minecraft_version\s*=\s*([0-9][0-9.]*)", line)
                    if m:
                        versions.append(tuple(int(x) for x in m.group(1).strip(".").split(".")))
        except OSError:
            pass
    return versions


def check_data_dirs(rest, rep, resources_root):
    versions = project_mc_versions(resources_root)
    if versions and all(v < (1, 21) for v in versions):
        return  # proyecto solo < 1.21: el plural es lo correcto
    for plural, singular in PLURAL_DATA_DIRS.items():
        if rest.startswith(plural + "/"):
            rep.warn(
                f"Carpeta '{plural}/': desde Minecraft 1.21 se llama '{singular}/'. "
                "Usa el plural solo para targets < 1.21; si soportas ambas, genera los datos con datagen."
            )
            return


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:  # noqa: BLE001
        return 0
    tool_input = payload.get("tool_input") or {}
    path = tool_input.get("file_path") or tool_input.get("path")
    if not path:
        return 0
    if not os.path.isabs(path):
        path = os.path.join(payload.get("cwd") or os.getcwd(), path)
    parts = split_resource_path(path)
    if not parts or not os.path.exists(path):
        return 0
    root, top, ns, rest = parts
    rep = Report()

    rel = f"{ns}/{rest}"
    if not VALID_PATH.match(rel):
        rep.error(f"Ruta de recurso '{top}/{rel}' no válida: solo se permiten minúsculas, números y _ . - /")

    if rest.endswith(".png") and rest.startswith("textures/"):
        check_png(path, rest, rep)
    elif rest.endswith(".json"):
        check_json(path, top, ns, rest, root, rep)
    elif rest.endswith(".mcmeta"):
        try:
            with open(path, encoding="utf-8") as f:
                json.load(f)
        except Exception as e:  # noqa: BLE001
            rep.error(f".mcmeta no es JSON válido: {e}")
    if top == "data":
        check_data_dirs(rest, rep, root)

    name = f"{top}/{rel}"
    if rep.errors:
        msg = [f"[minecraft-modding] Problemas en {name}:"] + [f"  ✗ {e}" for e in rep.errors]
        msg += [f"  ⚠ {w}" for w in rep.warnings]
        msg.append("Corrígelo antes de seguir.")
        print("\n".join(msg), file=sys.stderr)
        return 2
    if rep.warnings:
        text = "\n".join([f"[minecraft-modding] Avisos en {name}:"] + [f"  ⚠ {w}" for w in rep.warnings])
        print(json.dumps({"hookSpecificOutput": {"hookEventName": "PostToolUse", "additionalContext": text}}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())

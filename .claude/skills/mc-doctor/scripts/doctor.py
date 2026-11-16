#!/usr/bin/env python3
"""Diagnóstico del entorno para el plugin minecraft-modding. Solo lee; no instala nada."""
import os
import platform
import re
import shutil
import socket
import subprocess
import sys


def run(cmd):
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=20)
        return (out.stdout + out.stderr).strip()
    except Exception:
        return None


def java_versions():
    found = {}
    candidates = []
    if shutil.which("java"):
        candidates.append(shutil.which("java"))
    if os.environ.get("JAVA_HOME"):
        exe = "java.exe" if os.name == "nt" else "java"
        candidates.append(os.path.join(os.environ["JAVA_HOME"], "bin", exe))
    for c in candidates:
        if c and os.path.exists(c):
            c = os.path.realpath(c)
            if c in found:
                continue
            out = run([c, "-version"]) or ""
            m = re.search(r'version "(\d+)(?:\.(\d+))?', out)
            if m:
                major = int(m.group(1)) if m.group(1) != "1" else int(m.group(2))
                found[c] = major
    return found


def port_open(host, port):
    try:
        with socket.create_connection((host, port), timeout=1):
            return True
    except OSError:
        return False


def line(status, name, detail=""):
    print(f"[{status:^7}] {name}{' — ' + detail if detail else ''}")


def main():
    print(f"Sistema: {platform.system()} {platform.release()} · Python {platform.python_version()}\n")

    jv = java_versions()
    if not jv:
        line("FALTA", "Java", "instala Temurin 25 (https://adoptium.net/)")
    else:
        for path, major in jv.items():
            if major >= 25:
                line("OK", f"Java {major}", path)
            elif major >= 21:
                line("AVISO", f"Java {major}", path + " · la plantilla (Stonecraft) necesita Java 25 para ejecutar Gradle")
            else:
                line("AVISO", f"Java {major}", path + " · instala Java 25 para Gradle; 1.20.5+ compila con Java 21")

    try:
        import PIL  # noqa: F401
        line("OK", "Pillow", PIL.__version__)
    except ImportError:
        line("FALTA", "Pillow", f"{os.path.basename(sys.executable)} -m pip install pillow")

    line("OK" if shutil.which("git") else "FALTA", "Git", shutil.which("git") or "https://git-scm.com/")

    gradlew = "gradlew.bat" if os.name == "nt" else "gradlew"
    line("OK" if os.path.exists(gradlew) else "INFO", "Gradle wrapper en este directorio",
         "encontrado" if os.path.exists(gradlew) else "no estás en la raíz de un proyecto (normal si aún no lo creaste)")

    line("OK" if os.path.exists("CLAUDE.md") else "INFO", "CLAUDE.md del proyecto",
         "encontrado" if os.path.exists("CLAUDE.md") else "ejecuta /minecraft-modding:mc-init")

    bb = port_open("127.0.0.1", 3000)
    line("OK" if bb else "OPCIONAL", "Blockbench MCP (localhost:3000)",
         "escuchando" if bb else "abre Blockbench con el plugin MCP cargado")

    ase = shutil.which("aseprite") or shutil.which("Aseprite")
    line("OK" if ase else "OPCIONAL", "Aseprite", ase or "no está en el PATH (solo necesario para el MCP de Aseprite)")


if __name__ == "__main__":
    main()

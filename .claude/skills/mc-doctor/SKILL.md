---
name: mc-doctor
description: Comprueba que el equipo tiene todo lo necesario para el plugin minecraft-modding (Java 25/21/17, Python + Pillow, Git, Blockbench MCP, Aseprite MCP) y explica cómo arreglar lo que falte. Usar cuando el usuario ejecute /mc-doctor o algo del plugin no funcione.
disable-model-invocation: true
---

# Diagnóstico del entorno

1. Ejecuta: `python3 ".claude/skills/mc-doctor/scripts/doctor.py"`
   (en Windows, si `python3` no existe: `python` o `py`). Si no hay ningún Python, ese es el primer problema que debes reportar.
2. Comprueba los servidores MCP con la herramienta disponible o pidiendo al usuario que ejecute `/mcp`:
   - `blockbench` conectado → requiere Blockbench de escritorio abierto con el plugin MCP cargado.
   - `aseprite` (opcional).
3. Presenta una tabla: componente · estado (OK / falta / opcional) · cómo arreglarlo.
   Arreglos habituales:
   - Java 25: https://adoptium.net/ (Temurin 25) para ejecutar Gradle con la plantilla (Stonecraft). Java 21 y 17 los usan los targets (1.20.5+ y 1.20.1); Gradle puede descargarlos solo con toolchains, o instálalos también.
   - Pillow: `python3 -m pip install pillow` (Windows: `py -m pip install pillow`).
   - Blockbench: https://www.blockbench.net/ → File → Plugins → Load Plugin from URL → `https://jasonjgardner.github.io/blockbench-mcp-plugin/mcp.js`.
   - Aseprite MCP (opcional): https://github.com/diivi/aseprite-mcp
4. No instales nada sin que el usuario lo confirme.

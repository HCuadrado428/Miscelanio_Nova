---
name: pixel-art-16x16
description: Crear, revisar y variar texturas pixel art de Minecraft (items, bloques, animadas) en 16x16 con texgen.py, y con Aseprite/Blockbench vía MCP si están conectados. Usar para cualquier textura PNG del mod.
---

# Pixel art 16×16 para Minecraft

Script: `python3 ".claude/skills/pixel-art-16x16/scripts/texgen.py" <subcomando>`
(en Windows usa `python` o `py` si `python3` no existe; necesita Pillow: `pip install pillow`).
Ejemplo completo: `.claude/skills/pixel-art-16x16/examples/ruby.json`.
Guarda las previews en una carpeta temporal del proyecto (p. ej. `build/tex-preview/`), no dentro de `src/`.

## Resolución del proyecto
Muchos mods no usan 16×16. **Antes de dibujar, mira las texturas existentes de la misma carpeta** (o lo que diga el `CLAUDE.md`) y usa esa resolución: `texgen.py` admite cualquier tamaño (`"size": [32, 32]` y 32 filas de 32 caracteres). A 32×32 o más, todo lo de abajo sigue valiendo; solo cambia el número de filas/columnas y se permiten algunos colores más (≤ 32).

## Flujo obligatorio
1. **Referencia**: si hay una textura vanilla parecida (lingote, gema, mineral…), conviértela en texto con `inspect` para copiar proporciones y estilo. Las texturas vanilla están en el jar del cliente (`assets/minecraft/textures/`); NO las copies al mod, solo úsalas de referencia.
2. **Paleta**: `ramp "#colorbase" 4` → 3-5 tonos con desplazamiento de tono. Añade un contorno oscuro (no negro puro) y, como mucho, 1 brillo.
3. **Dibujar**: escribe el `spec.json` (16 filas × 16 caracteres) y ejecuta
   `draw spec.json src/main/resources/assets/<modid>/textures/item/<nombre>.png --preview build/tex-preview/<nombre>.png`
4. **Revisar**: abre la preview con Read y critica: ¿silueta legible?, ¿luz arriba-izquierda?, ¿contorno continuo?, ¿huérfanos (píxeles sueltos)?, ¿banding? Corrige y repite (mínimo 2 iteraciones).
5. **Validar**: `validate <png>` antes de terminar.
6. **Variantes**: `recolor base.png map.json out.png` (p. ej. lingote de cobre → de estaño).

## Convenciones de estilo vanilla
- Luz desde arriba a la izquierda; sombras abajo a la derecha.
- Items: silueta centrada, ~1 px de margen, contorno de 1 px más oscuro que el relleno.
- Bloques: textura que **tesela** sin costuras (revisa bordes opuestos), sin contorno, ruido sutil de 3-4 tonos.
- Evitar degradados suaves, anti-aliasing y semitransparencias (salvo vidrio/hielo, que además requieren render translúcido).
- ≤ 16 colores por textura 16×16.

## Rutas
- Items: `assets/<modid>/textures/item/` · Bloques: `assets/<modid>/textures/block/` · Entidades: `textures/entity/` (tamaño libre, potencia de 2, lo define el modelo).
- Animadas: frames apilados verticalmente (16×(16·N)) + `<nombre>.png.mcmeta`:
  ```json
  { "animation": { "frametime": 2, "interpolate": false } }
  ```

## Si hay MCP conectados
- **Aseprite MCP** (`mcp__aseprite__*`): úsalo para sombreado, dithering, outlines y animaciones; exporta a PNG y pasa `validate`.
- **Blockbench MCP** (`mcp__blockbench__*`): pinta texturas directamente sobre el modelo 3D (útil para entidades y bloques con modelo).
Si no están, `texgen.py` basta para items y bloques.

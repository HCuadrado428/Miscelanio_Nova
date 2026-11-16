---
name: blockbench-models
description: Crear modelos 3D de Minecraft (bloques, items, entidades) con Blockbench vía MCP o escribiendo el JSON a mano, respetando las limitaciones del formato Java de 1.20.1 a 1.21.11. Usar para cualquier modelo .json, .bbmodel o modelo de entidad.
---

# Modelos 3D con Blockbench

## Qué formato toca
| Qué | Formato Blockbench | Salida |
|---|---|---|
| Bloque / item con forma | **Java Block/Item** | `assets/<modid>/models/block|item/<n>.json` |
| Entidad vanilla-style | **Modded Entity** (elige mappings Mojang y versión) | Clase Java del modelo + textura |
| Entidad animada compleja | **GeckoLib Animated Model** (plugin GeckoLib) | `.geo.json` + `.animation.json` (requiere dependencia GeckoLib) |

Guarda siempre también el `.bbmodel` en `art/models/` (fuente editable, fuera de `resources`).

## Limitaciones del formato Java Block/Item (¡las rompe todo el mundo!)
- Coordenadas de cubos entre **-16 y 32** (un bloque es 0–16).
- Rotación de un elemento: **un solo eje** y solo **-45, -22.5, 0, 22.5, 45** (1.21.x recientes pueden ampliar esto; compruébalo en la versión).
- UV en unidades 0–16 por cara.
- Sin animaciones (salvo texturas animadas con .mcmeta).
- `"parent": "block/block"` o `"item/generated"`/`"item/handheld"` para items planos.
- Ajustar la pestaña **Display** (gui, firstperson, thirdperson, ground) para items 3D.

## Ficheros que hacen falta por versión
- Bloque: `blockstates/<n>.json` → `models/block/<n>.json` → `textures/block/...`
- Item ≥ 1.21.4: además `assets/<modid>/items/<n>.json` (definición de modelo de item).
- Item < 1.21.4: solo `models/item/<n>.json`.
- Si el bloque no es cubo completo: `noOcclusion()` en sus propiedades y render type cutout si tiene transparencias.

## Con Blockbench MCP conectado (`mcp__blockbench__*`)
1. Crea el proyecto en el formato correcto (tabla de arriba).
2. Construye con cubos, pon pivotes razonables, asigna texturas (16×16 por defecto para bloques).
3. Pide/haz capturas para revisar el resultado antes de exportar.
4. Exporta y coloca en la ruta del mod; guarda el `.bbmodel`.
5. Lanza `runClient` o `runDatagen` según toque y verifica que no hay "missing model" en el log.

## Sin MCP
Escribe el JSON a mano respetando las limitaciones y valida que es JSON válido (`python3 -m json.tool archivo.json`).

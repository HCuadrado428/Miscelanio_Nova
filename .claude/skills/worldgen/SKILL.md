---
name: worldgen
description: Generación de mundo en Minecraft 1.20.1–1.21.11 para Fabric, NeoForge y Forge - minerales, features, biome modifiers, estructuras y biomas. Usar para cualquier cosa que aparezca en el mundo al generarse.
---

# Worldgen (1.20.1 – 1.21.11)

Todo el worldgen moderno es **data-driven**: JSON en `data/<modid>/worldgen/...` + un "pegamento" por loader para meterlo en los biomas.
Doc: https://docs.neoforged.net/ (sección Worldgen) · https://docs.fabricmc.net/develop/ · https://minecraft.wiki/w/Configured_feature

## Mineral nuevo (el caso más común) — 3 piezas
1. **Configured feature** `data/<modid>/worldgen/configured_feature/ruby_ore.json`
   ```json
   {
     "type": "minecraft:ore",
     "config": {
       "size": 6,
       "discard_chance_on_air_exposure": 0.0,
       "targets": [
         { "target": { "predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables" },
           "state": { "Name": "<modid>:ruby_ore" } },
         { "target": { "predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables" },
           "state": { "Name": "<modid>:deepslate_ruby_ore" } }
       ]
     }
   }
   ```
2. **Placed feature** `data/<modid>/worldgen/placed_feature/ruby_ore.json`
   ```json
   {
     "feature": "<modid>:ruby_ore",
     "placement": [
       { "type": "minecraft:count", "count": 8 },
       { "type": "minecraft:in_square" },
       { "type": "minecraft:height_range", "height": { "type": "minecraft:trapezoid",
         "min_inclusive": { "above_bottom": 0 }, "max_inclusive": { "absolute": 64 } } },
       { "type": "minecraft:biome" }
     ]
   }
   ```
3. **Meterlo en los biomas** (depende del loader):
   - **Fabric** (código, en `onInitialize`):
     ```java
     BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
         GenerationStep.Decoration.UNDERGROUND_ORES,
         ResourceKey.create(Registries.PLACED_FEATURE, ModIds.id("ruby_ore")));
     ```
   - **NeoForge** (JSON) `data/<modid>/neoforge/biome_modifier/ruby_ore.json`
     ```json
     { "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
       "features": "<modid>:ruby_ore", "step": "underground_ores" }
     ```
   - **Forge 1.20.1** (JSON) `data/<modid>/forge/biome_modifier/ruby_ore.json` con `"type": "forge:add_features"` y los mismos campos.

Con Stonecutter, los JSON de NeoForge y Forge pueden convivir: cada loader ignora la carpeta del otro.

## Diferencias por versión
- Las carpetas de `worldgen/` **no** cambiaron a singular en 1.21 (ya lo eran). Las de `tags/` sí: `tags/worldgen/biome/` existe desde siempre; revisa `tags/block` vs `tags/blocks` para las tags de bloques.
- 1.21.x: algunos campos de placement/feature cambian de forma; si un JSON falla al cargar, el log dice el campo exacto. Compara con los JSON vanilla de esa versión (`data/minecraft/worldgen/` dentro del jar del cliente).

## Generarlo con datagen (recomendado para multiversión)
- `RegistrySetBuilder().add(Registries.CONFIGURED_FEATURE, ctx -> ...).add(Registries.PLACED_FEATURE, ctx -> ...)`
- NeoForge: `DatapackBuiltinEntriesProvider` (+ `NeoForgeRegistries.Keys.BIOME_MODIFIERS` para el biome modifier).
- Fabric: `FabricDynamicRegistryProvider` + `registryBuilder.add(...)` en `buildRegistry` del `DataGeneratorEntrypoint`.
Ver skill `datagen`.

## Comprobar
1. `runClient` → mundo nuevo → `/place feature <modid>:ruby_ore` para verlo al instante.
2. Si no aparece en el mundo natural: revisa el biome modifier (Forge/NeoForge) o la llamada a `BiomeModifications` (Fabric) y el `step`.
3. Errores de JSON salen al cargar el mundo en `logs/latest.log`, buscando el id del feature.

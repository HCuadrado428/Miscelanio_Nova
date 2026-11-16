---
name: datagen
description: Generar automáticamente modelos, blockstates, recetas, tags, loot tables, traducciones y worldgen con datagen en Fabric, NeoForge y Forge (1.20.1–1.21.11). Usar cuando haya que crear muchos JSON o mantenerlos en varias versiones.
---

# Datagen

Por qué: escribir JSON a mano en 3 versiones × 3 loaders es lento y se rompe (cambios de formato en 1.21, 1.21.2, 1.21.4…). Con datagen el **código** genera los JSON correctos para cada versión.

## Tareas
- Fabric: `./gradlew runDatagen` (entrypoint `fabric-datagen` en `fabric.mod.json`).
- NeoForge: `./gradlew runData` o `runClientData`/`runServerData` en versiones recientes.
- Forge 1.20.1: `./gradlew runData`.
- Con Stonecraft: `./gradlew runDatagen` lanza el datagen de cada target.
Salida típica: `src/main/generated/` (añádelo a los resources; Stonecraft lo hace).

## Fabric (`DataGeneratorEntrypoint`)
```java
public class ModDataGen implements DataGeneratorEntrypoint {
    @Override public void onInitializeDataGenerator(FabricDataGenerator gen) {
        var pack = gen.createPack();
        pack.addProvider(ModModelProvider::new);      // FabricModelProvider
        pack.addProvider(ModRecipeProvider::new);     // FabricRecipeProvider
        pack.addProvider(ModBlockTagProvider::new);   // FabricTagProvider.BlockTagProvider
        pack.addProvider(ModLangProvider::new);       // FabricLanguageProvider
        pack.addProvider(ModLootProvider::new);       // FabricBlockLootTableProvider
    }
}
```
En 1.21.x el tipo de salida es `FabricDataOutput` (en 26.x pasa a `FabricPackOutput`). Los constructores de providers cambian entre versiones: **compruébalos en las fuentes** (`genSources`).

## NeoForge (`GatherDataEvent`, bus del mod)
- 1.21.1: `GatherDataEvent` único → `event.getGenerator().addProvider(event.includeServer(), new ModRecipes(output, lookup))`.
- 1.21.4+: dividido en `GatherDataEvent.Client` / `.Server` y helper `event.createProvider(ModRecipes::new)`.
- Providers vanilla: `RecipeProvider`, `LootTableProvider`, `BlockTagsProvider`/`ItemTagsProvider`, `LanguageProvider` (NeoForge), modelos: `BlockStateProvider`/`ItemModelProvider` (≤1.21.3) → `ModelProvider` vanilla (1.21.4+).

## Forge 1.20.1
`GatherDataEvent` (net.minecraftforge.data.event) con `BlockStateProvider`, `ItemModelProvider`, `RecipeProvider` (método `buildRecipes(Consumer<FinishedRecipe>)`), `LootTableProvider`, `LanguageProvider`.

## Cambios que afectan a datagen
| Versión | Cambio |
|---|---|
| 1.20.5 | Recetas/loot usan `HolderLookup.Provider`; item components en resultados |
| 1.21 | Carpetas en singular (`recipe/`, `loot_table/`, `tags/block`) — datagen lo hace solo |
| 1.21.2 | Recetas reescritas (`RecipeProvider` con `Runner`), `HolderGetter` para items |
| 1.21.4 | Modelos: nuevo `ModelProvider` vanilla + definiciones `items/` |

## Flujo recomendado
1. Escribe los providers en código común cuando sea posible; el registro del provider es por loader.
2. Ejecuta el datagen de **cada** target y revisa el diff de `src/main/generated`.
3. Commitea los ficheros generados (así el build no depende de ejecutar datagen).
4. El hook del plugin valida automáticamente los JSON generados si Claude los escribe; tras `runDatagen` pide a Claude que revise `git diff --stat`.

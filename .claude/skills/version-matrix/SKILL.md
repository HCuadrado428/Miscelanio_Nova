---
name: version-matrix
description: Diferencias de API, Java, mappings y formato de recursos entre Minecraft 1.20.1 y 1.21.11 para Fabric, NeoForge y Forge. Usar antes de escribir o portar código que dependa de la versión.
---

# Matriz de versiones 1.20.1 → 1.21.11

> Los números exactos de loader cambian a menudo. Para la última build consulta siempre:
> Fabric `https://fabricmc.net/develop/` · NeoForge `https://projects.neoforged.net/neoforged/neoforge` · Forge `https://files.minecraftforge.net/`
> Guías de migración versión a versión (vanilla, válidas para todos los loaders): `https://docs.neoforged.net/primer/docs/<version>` (p. ej. `/1.21`, `/1.21.11`).

## Java y toolchain
| MC | Java | Fabric | NeoForge | Forge |
|----|------|--------|----------|-------|
| 1.20.1 | 17 | Loom | (fork 47.1.x, legado: preferir Forge) | 47.x (ForgeGradle) |
| 1.20.2 – 1.20.4 | 17 | Loom | 20.2.x / 20.4.x | sí |
| 1.20.5 – 1.20.6 | **21** | Loom | 20.6.x | sí |
| 1.21 – 1.21.11 | 21 | Loom | `21.<minor>.x` (p. ej. 21.1.x = 1.21.1, 21.11.x = 1.21.11) | sí |

NeoForge numera así: `<minor MC>.<patch MC>.<build>`. NeoForge recomienda **ModDevGradle**.

## Cambios que rompen código (los más importantes)
### 1.20.2
- NeoForge se separa de verdad de Forge: a partir de aquí las APIs de eventos/red divergen.
### 1.20.5 / 1.20.6
- **Data Components** sustituyen al NBT en `ItemStack` (`stack.get(DataComponents.X)`, `stack.set(...)`). Nada de `getTag()/getOrCreateTag()`.
- Red vanilla: `CustomPacketPayload` + `StreamCodec`. Fabric: `PayloadTypeRegistry`; NeoForge: `RegisterPayloadHandlersEvent`.
- Java 21.
### 1.21
- `new ResourceLocation(...)` es privado → `ResourceLocation.fromNamespaceAndPath(ns, path)`, `ResourceLocation.parse(str)`, `ResourceLocation.withDefaultNamespace(path)`.
- **Carpetas en singular** en data packs: `tags/block`, `tags/item`, `loot_table`, `recipe`, `advancement`, `structure`… (antes en plural).
- Encantamientos 100 % data-driven.
### 1.21.2
- `Item.Properties` / `BlockBehaviour.Properties` necesitan `.setId(ResourceKey)` **antes** de construir el item/bloque (los helpers de registro de cada loader lo hacen por ti si los usas).
- Reescritura del sistema de recetas y cambios en `HolderSet`.
### 1.21.4
- **Definiciones de modelo de item** obligatorias: `assets/<modid>/items/<item>.json` que apunta al modelo en `models/item/`.
### 1.21.9
- Grandes cambios de renderizado (submisión de features, glifos de fuente, separación de assets cliente).
### 1.21.11
- **`ResourceLocation` → `Identifier`** en Mojang mappings (`Identifier.fromNamespaceAndPath`, `ResourceKey#identifier`, `FriendlyByteBuf#readIdentifier`).
- Utilidades movidas a `net.minecraft.util`; `advancements.critereon` → `advancements.criterion`.
- Otra reescritura de renderizado; anotaciones de nulabilidad JSpecify.
- Última versión ofuscada (26.1 ya no lo está; fuera del alcance de este proyecto).

## Mappings
- **Recomendado: Mojang official + Parchment** en los 3 loaders → el mismo código compila en todos.
- Yarn (Fabric) usa otros nombres (`Identifier` desde siempre, `Text` en vez de `Component`…). Si ves ejemplos con Yarn en internet, tradúcelos.
- Fabric: migrar de Yarn a Mojmap con `./gradlew migrateMappings --mappings "net.minecraft:mappings:<mc>"`.

## Patrón Stonecutter para diferencias
```java
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
```
Mejor aún: centraliza en `common/` un helper `ModIds.id("path")` y pon la condición en un único sitio.

## Quilt
Compila para Fabric; prueba el jar en Quilt Loader. No dependas de QSL/QFAPI.

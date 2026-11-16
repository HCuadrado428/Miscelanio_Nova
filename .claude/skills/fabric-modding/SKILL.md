---
name: fabric-modding
description: Cómo escribir la capa Fabric (y compatibilidad Quilt) de un mod para Minecraft 1.20.1–1.21.11 con Loom y Fabric API. Usar al tocar el módulo fabric/ o fabric.mod.json.
---

# Fabric (1.20.1 – 1.21.11)

Doc oficial (elige la versión en el selector): https://docs.fabricmc.net/develop/
Antes de usar una API: `./gradlew genSources` y busca en las fuentes; o mira el repo FabricMC/fabric en la rama de esa versión.

## Ficheros clave
- `fabric.mod.json` → `id`, `version`, `entrypoints` (`main`, `client`, `fabric-datagen`), `mixins`, `depends` (`fabricloader`, `minecraft`, `fabric-api`, `java`).
- `build.gradle` → `loom { }`, `mappings loom.officialMojangMappings()` (+ Parchment con `layered { }`).
- Access Widener (`*.accesswidener`) en lugar de Access Transformer.

## Entry points
- `ModInitializer#onInitialize` (común), `ClientModInitializer#onInitializeClient` (cliente).
- No hay bus de eventos: se usan **callbacks** de Fabric API (`ServerLifecycleEvents`, `UseBlockCallback`, `ServerTickEvents`, `ItemGroupEvents`…).

## Registro
- Directo: `Registry.register(BuiltInRegistries.ITEM, id, item)`.
- 1.21.2+: construye `Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))` antes de `new Item(...)`.
- Pestañas creativas: `ItemGroupEvents.modifyEntriesEvent(...)` o registrar un `CreativeModeTab` propio.

## Red
- < 1.20.5: `ServerPlayNetworking.registerGlobalReceiver(id, handler)` con `FriendlyByteBuf`.
- ≥ 1.20.5: `PayloadTypeRegistry.playC2S()/playS2C().register(TYPE, CODEC)` + `ServerPlayNetworking.registerGlobalReceiver(TYPE, handler)`.

## Datagen
`FabricDataGenerator` con providers (`FabricModelProvider`, `FabricRecipeProvider`, `FabricTagProvider`, `FabricLanguageProvider`, loot tables). Ejecutar `./gradlew runDatagen`.

## Cliente
- Render de bloques (cutout/translucent): `BlockRenderLayerMap` (renombrado en algunas versiones; comprueba).
- Entidades: `EntityRendererRegistry`, `EntityModelLayerRegistry`.

## Quilt
El jar de Fabric suele funcionar en Quilt Loader. Pruébalo, no añadas dependencias de QSL.

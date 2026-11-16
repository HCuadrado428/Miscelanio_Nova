---
name: neoforge-modding
description: Cómo escribir la capa NeoForge de un mod para Minecraft 1.20.2–1.21.11 (ModDevGradle, DeferredRegister, buses de eventos, payloads). Usar al tocar el módulo neoforge/ o neoforge.mods.toml.
---

# NeoForge (1.20.2 – 1.21.11)

Doc oficial: https://docs.neoforged.net/ (selector de versión arriba) · Primers de migración: https://docs.neoforged.net/primer/docs/<version>
Para 1.20.1 usa **Forge** (skill `forge-modding`).

## Ficheros clave
- `src/main/resources/META-INF/neoforge.mods.toml` (en 1.20.2–1.20.4 era `mods.toml`). Comprueba el nombre en tu versión.
- `build.gradle` con **ModDevGradle** (`net.neoforged.moddev`), Parchment en `neoForge { parchment { … } }`.
- Access Transformer: `META-INF/accesstransformer.cfg`.

## Clase principal
```java
@Mod(MyMod.MODID)
public class MyMod {
    public MyMod(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        modBus.addListener(this::commonSetup);
    }
}
```
- **Mod bus** (registro, setup, datagen, payloads) vs **game bus** `NeoForge.EVENT_BUS` (eventos en partida).
- `@EventBusSubscriber(modid = MODID)`; en versiones antiguas además `bus = Bus.MOD` (comprueba si existe en tu versión).

## Registro
```java
public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
public static final DeferredItem<Item> RUBY = ITEMS.registerSimpleItem("ruby");
```
`DeferredRegister.Blocks`, `registerSimpleBlock`, `registerBlockItem`. En 1.21.2+ estos helpers ponen el `setId` por ti.

## Red
`RegisterPayloadHandlersEvent` → `registrar.playToServer(TYPE, STREAM_CODEC, handler)` / `playToClient(...)`. Enviar con `PacketDistributor`.

## Datagen
`GatherDataEvent` (en versiones recientes dividido en `.Client`/`.Server`; compruébalo) + providers vanilla (`RecipeProvider`, `BlockStateProvider`/model providers, `LanguageProvider`, tags). `./gradlew runData` (o `runClientData`).

## Capacidades
Sistema de **Capabilities** reescrito en 20.3+: `RegisterCapabilitiesEvent`, `Capabilities.ItemHandler.BLOCK`… No uses `LazyOptional` (eso es Forge).

## Config
`ModConfigSpec` + `container.registerConfig(ModConfig.Type.COMMON, SPEC)`.

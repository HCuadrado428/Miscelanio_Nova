---
name: forge-modding
description: Cómo escribir la capa MinecraftForge (sobre todo 1.20.1) y sus diferencias con NeoForge. Usar al tocar el módulo forge/ o mods.toml de Forge.
---

# Forge (foco en 1.20.1)

Doc: https://docs.minecraftforge.net/ (elige versión). Builds: https://files.minecraftforge.net/
En 1.20.1 Forge es la opción principal (mayor base de modpacks). Para 1.21.x prioriza NeoForge y trata Forge como opcional.

## Ficheros clave
- `META-INF/mods.toml`, `build.gradle` con **ForgeGradle** (o MDG Legacy), `mappings channel: 'official'` / Parchment.
- Access Transformer: `META-INF/accesstransformer.cfg`.

## Clase principal (1.20.1)
```java
@Mod(MyMod.MODID)
public class MyMod {
    public MyMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        MinecraftForge.EVENT_BUS.register(this);
    }
}
```
(En Forge 1.21.x el constructor cambia; compruébalo en el MDK de esa versión.)

## Registro
```java
public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
public static final RegistryObject<Item> RUBY = ITEMS.register("ruby", () -> new Item(new Item.Properties()));
```

## Red (1.20.1)
`SimpleChannel` (`NetworkRegistry.newSimpleChannel`) con `FriendlyByteBuf`. NO existe `CustomPacketPayload` en 1.20.1.

## Items con datos (1.20.1)
NBT: `stack.getOrCreateTag()`. (Data Components llegan en 1.20.5.)

## Capacidades (1.20.1)
`ICapabilityProvider`, `LazyOptional`, `ForgeCapabilities.ITEM_HANDLER`.

## Diferencias rápidas Forge 1.20.1 → NeoForge 1.21.1
| Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|
| `RegistryObject<T>` | `DeferredHolder` / `DeferredItem` / `DeferredBlock` |
| `MinecraftForge.EVENT_BUS` | `NeoForge.EVENT_BUS` |
| `SimpleChannel` | `RegisterPayloadHandlersEvent` + `StreamCodec` |
| NBT en ItemStack | Data Components |
| `LazyOptional` caps | Capabilities nuevas |
| `new ResourceLocation(a,b)` | `ResourceLocation.fromNamespaceAndPath(a,b)` |
| `data/<ns>/tags/blocks` | `data/<ns>/tags/block` |

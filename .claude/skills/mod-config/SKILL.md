---
name: mod-config
description: Ficheros de configuración de mods en Fabric, NeoForge y Forge (1.20.1–1.21.11) - ModConfigSpec, ForgeConfigSpec, Forge Config API Port y pantallas de configuración. Usar cuando el mod necesite opciones configurables.
---

# Configuración

## Elige una estrategia (multiloader)
| Opción | Cuándo | Notas |
|---|---|---|
| **Forge Config API Port** (Fuzs) | Quieres la misma API en Fabric y en NeoForge/Forge | En Fabric es una dependencia extra; en NeoForge/Forge usa la API nativa. Mismo `.toml` en todos |
| **Config propia en `common/`** (JSON + `Codec`) | Pocas opciones, cero dependencias | Tú gestionas carga/guardado en `config/<modid>.json` |
| Librerías Fabric (Cloth Config, YACL, MidnightLib) | Mod solo Fabric o con UI rica | MidnightLib es multiloader |

## NeoForge (nativo)
```java
public class ModConfig {
    public static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue ORE_COUNT = B
        .comment("Vetas de rubí por chunk")
        .defineInRange("oreCount", 8, 0, 64);
    public static final ModConfigSpec SPEC = B.build();
}
// en el constructor del mod:
container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC);
```
Pantalla de configuración automática (1.21+): `container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)` en el lado cliente.

## Forge 1.20.1
`ForgeConfigSpec.Builder` (misma idea) y `ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC)`.

## Tipos de config
- `COMMON`: ambos lados, por instancia. `SERVER`: por mundo, se sincroniza a clientes. `CLIENT`: solo cliente (gráficos, HUD). `STARTUP` (NeoForge 1.21+): se lee antes de registrar cosas.
- **No leas valores de config durante el registro** (salvo `STARTUP`): aún no están cargados.

## Reglas
- Nombres de claves en `camelCase`, siempre con comentario y rango.
- Valores que cambian el mundo → `SERVER` (así el servidor manda).
- Documenta las opciones en el README del mod.

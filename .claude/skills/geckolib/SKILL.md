---
name: geckolib
description: Entidades, bloques, items y armaduras animadas con GeckoLib (4.x y 5.x) en Fabric, NeoForge y Forge, modeladas en Blockbench. Usar cuando algo necesite animaciones complejas.
---

# GeckoLib

GeckoLib = librería de animación (modelos Bedrock `.geo.json` + animaciones `.animation.json` hechas en Blockbench).
Wiki: https://github.com/bernie-g/geckolib/wiki · Requiere añadirla como dependencia (y que el jugador la instale).

## ¿Qué versión de GeckoLib?
| Minecraft | GeckoLib | Paquete base | Carpetas de assets |
|---|---|---|---|
| 1.20.1 – 1.21.1 | **4.x** | `software.bernie.geckolib` | `assets/<modid>/geo/` y `assets/<modid>/animations/` |
| 1.21.5 – 1.21.11 | **5.x** | `software.bernie.geckolib` | `assets/<modid>/geckolib/models/` y `assets/<modid>/geckolib/animations/` |
| 26.1+ | 5.5+ | `com.geckolib` | igual que 5.x |

Los modelos de 5.x no siempre cargan en 4.x: exporta desde Blockbench con la versión correcta del plugin. Las APIs de 4 y 5 son distintas (renderers basados en `RenderState` en 5.x): **consulta la wiki de tu versión** antes de escribir el renderer.
Dependencia: busca la coordenada Maven exacta en la wiki ("Installation") para tu loader y versión.

## En Blockbench
1. Instala el plugin **GeckoLib Animation Utils** (File → Plugins).
2. File → New → **GeckoLib Animated Model**; File → GeckoLib Model Settings → tipo (Entity/Block/Item/Armor).
3. Solo los **grupos** se animan: mete los cubos en grupos con pivotes bien puestos.
4. Exporta modelo (`.geo.json`) y animaciones (`.animation.json`) a las carpetas de la tabla. Guarda el `.bbmodel` en `art/models/`.

## Entidad animada (estructura, GeckoLib 4)
```java
public class Golem extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5,
            state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
```
- Modelo: `new DefaultedEntityGeoModel<>(ModIds.id("golem"))` busca `golem.geo.json`, `golem.animation.json` y `textures/entity/golem.png`.
- Renderer (cliente): `new GeoEntityRenderer<>(ctx, model)`; regístralo como cualquier renderer de entidad del loader.
- En 5.x `AnimationController` y el test de estado cambian de firma: compruébalo.

## Comprobar
- "Could not find model/animation": la carpeta no coincide con la versión de GeckoLib (tabla de arriba).
- Animación que no se mueve: el nombre en `RawAnimation` debe ser idéntico al de Blockbench (p. ej. `animation.golem.walk` si así se llama).
- Textura: mismo tamaño que el definido en el modelo (Blockbench → Project → Texture size).
Para el modelado general usa también `blockbench-models`.

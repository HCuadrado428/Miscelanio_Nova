---
name: gametests
description: Tests automáticos dentro del juego (GameTests) para Fabric, NeoForge y Forge en 1.20.1–1.21.11, incluido el sistema nuevo de test_instance de 1.21.5+. Usar para probar mecánicas del mod de forma automática en CI.
---

# GameTests

Un GameTest coloca una estructura en un mundo de pruebas, ejecuta lógica y comprueba el resultado. Se lanzan con `./gradlew runGameTestServer` (sale con error si algún test falla → perfecto para CI).

## Antes de 1.21.5 (1.20.1 – 1.21.4): tests como métodos anotados
- **Fabric**: dependencia `fabric-gametest-api-v1` (Stonecraft la añade), entrypoint `"fabric-gametest"` en `fabric.mod.json`, clase con métodos
  ```java
  @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
  public void rubyIsRegistered(GameTestHelper helper) {
      helper.assertTrue(ModItems.RUBY != null, "El rubí no está registrado");
      helper.succeed();
  }
  ```
- **NeoForge / Forge**: `@GameTestHolder(ExampleMod.MOD_ID)` en la clase + `@PrefixGameTestTemplate(false)`, métodos `@GameTest` estáticos; registro con `RegisterGameTestsEvent` (`event.register(MyTests.class)`).
- Estructuras: `data/<modid>/structure/<nombre>.nbt` (en 1.20.1: `structures/`), creadas con un bloque de estructura en el juego.

## 1.21.5+ (incluye 1.21.11): tests data-driven
- Cada test es una entrada del registro **`test_instance`**: `data/<modid>/test_instance/<test>.json`, y cada uno corre en un **`test_environment`** (`data/<modid>/test_environment/...`; `minecraft:default` existe siempre).
- Tests en código: se registra una función de test y una `FunctionGameTestInstance` que la referencia (NeoForge: con datagen sobre `Registries.TEST_INSTANCE`). Fabric API adapta su `fabric-gametest` a este sistema; mira la doc de Fabric de tu versión.
- Nuevo comando en el juego: `/test run <id>`, `/test runall`, `/test create <id>`.
- Doc: https://docs.neoforged.net/docs/1.21.5/misc/gametest (y la misma ruta para versiones posteriores).

## Con Stonecutter
Mantén dos paquetes (`gametest/legacy` < 1.21.5 y `gametest` ≥ 1.21.5) y elige con `//? if <1.21.5 {` o con `variableReplacements` en `build.gradle.kts`, como hace la plantilla de Stonecraft.

## En CI
Añade `runGameTestServer` antes de `buildAndCollect` en el workflow. Un test que falla rompe el build: así no publicas nada roto.

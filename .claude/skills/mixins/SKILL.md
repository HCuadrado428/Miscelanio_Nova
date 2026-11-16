---
name: mixins
description: Buenas prácticas para escribir y revisar Mixins compatibles entre Fabric, NeoForge y Forge (1.20.1–1.21.11). Usar al crear o depurar un mixin.
---

# Mixins

## Antes de escribir uno
1. ¿Hay un evento/callback del loader que ya lo haga? Úsalo. El mixin es el último recurso.
2. Busca el método objetivo en las fuentes de ESA versión. Los nombres cambian entre versiones.
3. Si va en `common/`, debe compilar con Mojang mappings para todos los loaders.

## Preferencias (de más a menos seguro)
1. `@Inject` en `HEAD`/`RETURN` con `CallbackInfo`/`CallbackInfoReturnable`.
2. **MixinExtras** (incluido en Fabric Loader y en NeoForge; en Forge 1.20.1 añádelo como dependencia): `@WrapOperation`, `@ModifyExpressionValue`, `@WrapWithCondition`, `@Local`.
3. `@ModifyVariable`, `@ModifyArg` con `ordinal`/`index` explícito.
4. **Evitar** `@Redirect` y `@Overwrite`: rompen otros mods.

## Reglas
- `@Inject(..., cancellable = true)` solo si cancelas.
- `@Shadow` y `@Accessor`/`@Invoker` para campos privados (o Access Widener/Transformer).
- Mixins de cliente en la sección `client` del json, nunca en `mixins` común.
- `"required": true` y `"defaultRequire": 1` en el json para que fallen pronto en desarrollo.
- Los targets dependientes de versión → condición de Stonecutter o mixin por versión.

## Depurar
- `-Dmixin.debug.export=true` exporta las clases transformadas a `.mixin.out/`.
- Error "Critical injection failure" → el target cambió de firma: revisa las fuentes de esa versión.

---
name: publishing
description: Publicar el mod en Modrinth y CurseForge (y GitHub Releases) para todas las versiones y loaders a la vez, con Stonecraft/mod-publish-plugin y GitHub Actions. Usar al preparar una release o configurar la publicación.
---

# Publicar en Modrinth y CurseForge

## Preparación (una sola vez)
1. Crea el proyecto en **Modrinth** (https://modrinth.com/dashboard) y en **CurseForge** (https://authors.curseforge.com/). Apunta:
   - Modrinth: el **ID** del proyecto (en la página del proyecto → ⋯ → Copy ID).
   - CurseForge: el **Project ID** numérico y el **slug** (lo que sale en la URL).
2. Crea tokens:
   - Modrinth: Settings → **PATs** → permiso *Create versions*.
   - CurseForge: https://legacy.curseforge.com/account/api-tokens
3. En GitHub → Settings → Secrets and variables → Actions:
   - **Secrets**: `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`
   - **Variables**: `MODRINTH_ID`, `CURSEFORGE_ID`, `CURSEFORGE_SLUG`

## Configuración en el proyecto (plantilla del plugin)
`build.gradle.kts` ya trae `publishMods { modrinth { ... } curseforge { ... } }`. Añade dependencias del mod ahí:
```kotlin
modrinth { if (mod.isFabric) requires("fabric-api"); optional("jei") }
curseforge { if (mod.isFabric) requires("fabric-api") }
```
Si un jar sirve para más versiones (p. ej. 1.21.1 también vale para 1.21), añade en `versions/dependencies/<v>.properties`:
`additional_versions=1.21`

## Publicar
1. Sube `mod.version` en `gradle.properties` (versionado semántico: 1.2.0).
2. Escribe el changelog (en la descripción de la release).
3. En GitHub → Releases → **Draft a new release** → tag `v1.2.0` → Publish. Marca *pre-release* para publicar como **beta**.
4. El workflow `build.yml` compila los 6 targets y ejecuta `./gradlew publishMods` con `DO_PUBLISH=true`.

Para probar sin publicar: `./gradlew publishMods` en local **sin** `DO_PUBLISH` hace un *dry run*.

## Checklist antes de publicar
- [ ] `./gradlew buildAndCollect` verde en todos los targets
- [ ] Probado en cliente **y** en servidor dedicado (`runServer`) en al menos una versión por loader
- [ ] Dependencias correctas en `fabric.mod.json` / `mods.toml` / `neoforge.mods.toml`
- [ ] Icono, descripción y licencia
- [ ] Changelog escrito
- [ ] Versión subida (Modrinth rechaza versiones repetidas)

## Nombres de versión
Stonecraft nombra los ficheros con versión del mod + Minecraft + loader. No renombres a mano los jars.

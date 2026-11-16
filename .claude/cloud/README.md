# Configurar el entorno de nube (claude.ai/code y app de Claude)

Generado por el plugin minecraft-modding 1.2.0. Loaders detectados: forge · Java del mod: 17.

Las sesiones en la nube cargan `.claude/` y `CLAUDE.md` del repositorio, pero la red y las herramientas
del sistema se configuran en el **entorno**. Hazlo una vez:

1. Abre https://claude.ai/code → selector de entorno → icono de ajustes del entorno (o crea uno nuevo, p. ej. "Minecraft").
2. **Network access → Custom**, marca **"Also include default list of common package managers"** y pega en *Allowed domains*:

```text
libraries.minecraft.net
*.mojang.com
resources.download.minecraft.net
api.foojay.io
maven.minecraftforge.net
dl.cloudsmith.io
maven.blamejared.com
maven.kosmx.dev
maven.maxhenkel.de
modmaven.dev
repo.spongepowered.org
```

3. **Setup script** (instala lo que no viene en la máquina; se guarda en caché si tarda menos de ~5 minutos):

```bash
#!/bin/bash
# Setup script del entorno de nube (generado por minecraft-modding)
apt-get update -qq && apt-get install -y -qq openjdk-17-jdk-headless || true
python3 -m pip install -q pillow || pip install -q pillow || true
```

4. Usa ese entorno al crear sesiones de este repositorio (también desde la pestaña **Code** de la app).

## Qué funciona en la nube

| Funciona | No funciona (necesita tu PC) |
|---|---|
| Compilar (`./gradlew build`), datagen, GameTests en servidor | `runClient` (no hay pantalla) |
| Skills, subagentes y hook de validación | Blockbench y Aseprite por MCP (están en tu PC) |
| `texgen.py` para texturas | Probar el mod a mano en el juego |

Para lo que no funciona en la nube, usa Claude Code en tu PC con el plugin instalado y, si quieres manejarlo
desde el móvil, `/remote-control`.

## Actualizar

Cuando salga una versión nueva del plugin, vuelve a ejecutar desde tu PC (con el plugin instalado)
`/minecraft-modding:mc-cloud-setup` en este repositorio y haz commit de los cambios en `.claude/`.

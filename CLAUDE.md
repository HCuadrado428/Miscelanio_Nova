# Miscelánea (mod de Forge 1.20.1)

Mod de Forge para **Minecraft 1.20.1** (modid `miscelanea`): sala recreativa, feria, juegos de mesa, móvil, armas, pociones, bromas… Pensado para servidores de amigos. Cliente **y** servidor necesitan el mod.

## Toolchain
| | |
|---|---|
| Minecraft / Forge | 1.20.1 / 47.3.0 (`gradle.properties`) |
| Build | ForgeGradle 6 (`net.minecraftforge.gradle`), Gradle 8.8, **Java 17** (toolchain) |
| Mappings | `official` (Mojang) — los nombres son los de Mojang, **no** Yarn ni SRG |
| Mixins | plugin `org.spongepowered.mixin`, config `miscelanea.mixins.json`, paquete `com.miscelanea.mixin` |
| Dependencias | GeckoLib 4.8.4 (forge 1.20.1), player-animation-lib, mclib. Opcionales solo para compilar: Simple Voice Chat API, JEI API |

**Solo hay un target (Forge 1.20.1).** Ignora todo lo de NeoForge, Fabric, Stonecutter o 1.21+ de las skills salvo que se pida portar el mod.

## Comandos
- Compilar: `./gradlew build` → jar en `build/libs/` (lo reofusca `reobfJar`).
- Probar: `./gradlew runClient` / `./gradlew runServer` (los GameTests del namespace `miscelanea` están activados en ambos).
- En la nube (claude.ai/code, app) **no hay pantalla**: compila con `./gradlew build`; para probar en el juego, la CI "Probar en el juego" arranca un servidor y el cliente sin pantalla y sube capturas como artefactos.

## Estructura y convenciones (síguelas)
- Código, nombres y comentarios **en español**, como el resto del mod.
- Un paquete por sistema: `com.miscelanea.<sistema>` (`laser`, `movil`, `feria`, `armasfuego`…). Lo de cliente va en `client/` o en subpaquetes `client` y **nunca** se referencia desde código común/servidor.
- **Registro:** `DeferredRegister` + `RegistryObject`. Los registros generales están en `com.miscelanea.registry` (`ModItems`, `ModBlocks`, `ModEntities`, `ModSounds`, `ModTabs`…). Un sistema grande tiene su propia clase `XxxRegistro` con `public static void registrar(IEventBus modBus)`, que **hay que llamar desde el constructor de `Miscelanea`** (ver `LaserRegistro` como ejemplo).
- **Pestañas del creativo:** todo objeto nuevo se mete en una pestaña en `Miscelanea.creativeTabs`; si a propósito no va en ninguna, añádelo a `SIN_PESTANA` (si no, sale un aviso).
- **Red:** `com.miscelanea.network.ModNetwork` (`SimpleChannel` de Forge 1.20.1), un `XxxPacket` por mensaje; valida en el servidor todo lo que llega del cliente.
- **Configuración:** `ConfigServidor` (config SERVER, `serverconfig/miscelanea-server.toml`). Cada sistema nuevo lleva su interruptor y sus números ahí, con comentario en español.
- **Idiomas:** añade siempre las claves a `assets/miscelanea/lang/es_es.json` **y** `en_us.json`. El build copia `es_es` a `es_mx`, `es_ar`, `ca_es`… (no edites esas copias).
- **Datos (1.20.1):** carpetas en plural, como están: `loot_tables/`, `recipes/`, `tags/items/`, `advancements/`. Las armas de fuego salen por Global Loot Modifier (`data/miscelanea/loot_modifiers`).
- **GeckoLib 4:** modelos en `assets/miscelanea/geo/`, animaciones en `assets/miscelanea/animations/`, texturas en `textures/`.
- **Texturas:** iconos de objetos y bloques a **32×32** por defecto (2 texeles por píxel), materiales de modelos 3D de armas a 16 px por bloque; mira siempre las texturas vecinas de la misma carpeta y usa su tamaño. Las texturas UV de modelos 3D/GeckoLib pueden ser rectangulares (p. ej. 1024×512).
- **Canciones con copyright** en `assets/miscelanea_canciones`: el repositorio es privado por eso. Nunca las muevas, publiques ni subas a otro sitio.
- No hagas commit de `src/main/java/javac.*.args` (restos de compilaciones manuales).

## Ramas, versiones y CHANGELOG
- `main` = lo que se juega (ahora **1.3.2**). **No subas `mod_version` sin que lo diga el dueño.**
- `arreglos` → siguiente 1.x.y; `novedades` → siguiente 1.x.0; `version-2` → 2.0.0. Un push a cualquiera de ellas compila y publica un jar `build-N` en Releases (y lanza "Probar en el juego").
- Trabaja en una rama de tema (como `peluche-persona`, `armas-laser`…) y une a la rama que toque cuando esté probado. No hagas push directo a `main` salvo que se pida.
- Todo cambio visible para el jugador se apunta en `CHANGELOG.md`, en la sección de la versión en curso, con el mismo estilo: frase en negrita con el nombre y explicación para jugadores (cómo se usa, cómo se consigue, recetas).
- Si cambia algo que el jugador tiene que saber, actualiza también la sección correspondiente del `README.md`.

## Herramientas de Claude en este repo (`.claude/`)
Copiadas del plugin **minecraft-modding** (https://github.com/HCuadrado428/Modding-better-) para que funcionen también en la web y en la app:
- **Skills** (se cargan solas cuando hacen falta): `forge-modding`, `mixins`, `geckolib`, `gui-screens`, `networking`, `datagen`, `worldgen`, `mod-config`, `gametests`, `pixel-art-16x16` (sirve para cualquier tamaño), `blockbench-models`, `version-matrix`… 
- **Subagentes:** `build-fixer` (build roto), `mixin-reviewer` (revisar mixins), `api-researcher` (verificar APIs de 1.20.1 antes de usarlas), `texture-artist`, `model-artist`, `mod-porter` (solo si se porta a otra versión/loader).
- **Hook:** cada PNG o JSON que se escribe en `assets/` o `data/` se valida automáticamente (rutas, JSON, tamaños de textura, modelos). Si da un error, corrígelo antes de seguir.
- Entorno de la nube (red y Java 17): ver `.claude/cloud/README.md`.

## Reglas de oro
1. **No inventes APIs.** Este mod es 1.20.1: nada de `Identifier`, Data Components, `CustomPacketPayload` ni `setId` (son de 1.20.5+/1.21+). Si dudas, usa `api-researcher` o busca en las fuentes de Forge 47 / el código existente del mod.
2. Antes de crear algo, busca si ya hay un sistema parecido en el mod y sigue su estilo.
3. Un cambio no está terminado hasta que `./gradlew build` pasa.

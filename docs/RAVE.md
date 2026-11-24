# Rave, discoteca y concierto: diseño (borrador)

Petición de la gente del servidor: luces de neón como los punteros láser, mesa de DJ o tele donde se puedan poner vídeos, altavoces que lleguen a mucha distancia, pulseras y objetos de neón, andamios de rave con luces, caramelos de colores (las "pastillas" de fiesta), máquinas de humo de colores, chispas y bola de discoteca. Y una recomendación: **interfaces para los objetos y modelos 3D con GeckoLib**, no solo bloques.

Rama de trabajo: `rave` (sale de `novedades`). Pestaña nueva del creativo: **Rave**.

## Lo que ya hay y se reutiliza
- **Pista de baile y gramola de videoclip** (`baile`): canciones, coreografías y decorados.
- **Puntero láser con tinte** (`laser`): el color y el haz.
- **GeckoLib** en objetos y entidades; `AutoGlowingGeoLayer` para lo que brilla.
- **Cámara y vídeo del móvil** (`movil`): fotos y clips en JPEG por el servidor (para la pantalla de vídeo).
- **Sonidos del juego a distancia:** un volumen mayor que 1 aumenta el alcance (volumen 4 = unos 64 bloques).

## Iluminación (todo con color, de 16 tintes)
- **Barras y paneles de neón** (bloques emisivos con color). Se tiñen con colorante.
- **Foco móvil** (GeckoLib): gira y barre, con su haz de luz.
- **Láser de escenario:** haces que cruzan el local (con el sistema del puntero láser, en varios colores y patrones).
- **Bola de discoteca** (GeckoLib): gira y reparte manchas de luz por las paredes.
- **Estroboscopio.**

## Escenario
- **Andamio (truss)**: se conecta con los vecinos como las vallas, con luces a lo largo.
- **Plataformas de escenario** y **pantalla LED** (la tele de abajo).

## Sonido y DJ
- **Mesa de DJ** (GeckoLib, con interfaz): elige canción, volumen, efecto de luces, color y velocidad.
- **Altavoces grandes** (GeckoLib): se enlazan a la mesa con un enlazador o por cable, y propagan el sonido a mucha distancia (alcance configurable, hasta 128 bloques). Late con el ritmo.
- **Música:** las canciones de la gramola, los discos del juego y lo que ya tiene el mod (las que llevan copyright siguen en el repositorio privado, sin tocarlas).

## Vídeo
- **Pantalla/tele:** reproduce los **clips del móvil** y los videoclips de la gramola (sin vídeos de internet: Minecraft no trae decodificador y cada cliente tendría que descargarlos). Se cuelga de pared o techo.
- Se conecta con el sistema de cámaras del laboratorio (hito 7).

## Efectos
- **Máquina de humo** de colores, **fuente de chispas** (frías, sin fuego), **cañón de confeti** y **burbujas**.

## Objetos de neón (con modelo 3D y brillo)
- **Barras luminosas** (de mano, se rompen y se lanzan), **pulseras**, **collares** y **gafas de neón** (casco), en todos los colores.

## Caramelos de fiesta
- Los "caramelos de corazón de colores": objetos de fantasía con efectos del mod (euforia, visión de colores, bajón). Con tiempo de espera y un "bajón" al acabar, como una poción. Nada de drogas reales: son caramelos mágicos.

## Interfaces
- **Mesa de DJ** (lista de canciones y mezclador), **panel de luces** (color, patrón, velocidad, para todas las luces enlazadas) y **mando de luces** (objeto que abre ese panel desde lejos).

## Hitos
1. **Neón:** barras y paneles de color, barras luminosas y pulseras (objetos 3D que brillan).
2. **Focos, bola de discoteca y estroboscopio** (GeckoLib).
3. **Mesa de DJ con interfaz y altavoces a distancia.**
4. **Máquinas de humo, chispas y confeti.**
5. **Andamios y escenario.**
6. **Pantalla de vídeo** (clips del móvil).
7. **Caramelos de fiesta**, panel y mando de luces, README y CHANGELOG.

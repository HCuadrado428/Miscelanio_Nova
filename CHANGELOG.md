# Cambios de Miscelánea

Cómo van los números de versión:
- **1.0.x**: arreglos de fallos y ajustes pequeños (una receta, un número…).
- **1.x.0**: objetos o cosas pequeñas nuevas.
- **x.0.0**: cambios o añadidos grandes.

Ramas del repositorio (cada una compila su propio jar al subir algo):
- **main**: lo publicado, lo que se juega.
- **arreglos**: la siguiente 1.x.y (solo arreglos). Se une a main en cuanto está probada, y de main pasa a las otras dos.
- **novedades**: la siguiente 1.x.0 (cosas pequeñas). Se une a main cuando se publica.
- **version-2**: la 2.0.0 (lo gordo). Recoge lo de main por el camino y se une al final.

## 1.2.0 (en curso)

## 1.1.2
- **Anillo de medianoche:** decía que The Midnight no estaba instalado aunque lo estuviera. Buscaba la dimensión `midnight:midnight`, pero en The Midnight 0.6.x se llama `midnight:the_midnight`; ahora encuentra cualquiera de las dos (aunque la config antigua tenga el nombre viejo).

## 1.1.1
- **Gramola:** el decorado del videoclip se pone a la altura de los pies de quien baila. Si el suelo delante de la gramola estaba más alto o más bajo (o había alfombras o losas), el decorado se quedaba a otra altura y la cámara se metía por debajo del suelo.
- **Gramola (videoclip):** sin shaders, los rayos, focos y halos del decorado salían como planos de color macizo tapando la pantalla (las luces suaves se pintaban con el búfer del color opaco). Ahora cada tipo de luz tiene su búfer y se ven translúcidas.

## 1.1.0
- **Pociones en supervivencia:** Gigante (seta roja), Enano (seta marrón), Ecolocalización (fragmento de eco), Niebla viviente (tinta de calamar), Espejismo (fragmento de amatista), Helio (pluma), Ventilador (bola de nieve), Borrachera (trigo) y Tartamudeo (granos de cacao), sobre poción rara; la redstone las alarga. Sed de sangre y Fantasma siguen en creativo.

## 1.0.1
- **Caras en el móvil con Customizable Player Models:** cada jugador manda una foto de la cabeza de su modelo (de frente y sin el móvil) y el servidor la reparte: se ve en contactos, chats y llamadas aunque esté lejos o desconectado, y ya no sale agarrando el móvil.
- **Cámara y videollamada con shaders (Oculus):** la imagen salía negra o congelada; ahora se copia cuando el pack de shaders ya ha pintado el mundo (y con el móvil abierto no sale la mano en la foto).
- **Cámara del móvil:** la vista previa con filtro y los vídeos cortos salían negros; arreglado. Los vídeos duran lo que de verdad duran aunque el juego vaya a pocos fps.
- **Móvil:** la letra pequeña ya tiene letras (antes solo números): se ven los nombres en las llamadas de grupo, las etiquetas de los botones y los avisos pequeños.
- **Manos libres:** la tarjeta de la llamada va a la derecha, más abajo, para que no la tapen los avisos ni los efectos.
- **Gambito** (mazo de Wemmbu): Ráfaga de viento II en vez de III (sube menos tras el golpe en caída).
- **Ecolocalización:** los jugadores ya no parpadean con cada ruido; solo se ven mientras hablan (y el ruido de fondo del micro ya no cuenta). Lo que se ve con las ondas ya no da fogonazos al volver a tocarlo.
- El **puntero láser** ya cae justo en la mira (antes el punto salía más abajo), y el rayo sale de la mano aunque el jugador haya cambiado de tamaño (Pehkui, Gigante, Enano).
- La **venda** ya se puede craftear en supervivencia: 1 lana blanca + 2 cuerdas (sin forma) → 3 vendas.

## 1.0.0
- Primera versión: todo lo anterior hasta la build 275 (móvil con llamadas de grupo, manos libres, cámara con filtros y vídeos cortos; fantasma que aparece de golpe y tal cual; etc.).

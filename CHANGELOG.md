# Cambios de Miscelánea

Cómo van los números de versión:
- **1.0.x**: arreglos de fallos y ajustes pequeños (una receta, un número…).
- **1.x.0**: objetos o cosas pequeñas nuevas.
- **x.0.0**: cambios o añadidos grandes.

Ramas del repositorio (cada una compila su propio jar al subir algo):
- **main**: la versión que se juega (ahora la 1.1.0, en curso). No se sube de número hasta que lo diga el dueño del mod.
- **arreglos**: la siguiente 1.x.y (solo arreglos). Se une a main en cuanto está probada, y de main pasa a las otras dos.
- **novedades**: la siguiente 1.x.0 (cosas pequeñas). Se une a main cuando se publica.
- **version-2**: la 2.0.0 (lo gordo). Recoge lo de main por el camino y se une al final.

## 2.0.0 (en curso)
- **Montaña rusa** (pestaña Feria): vía (recta, curvas, subida y bajada: agáchate y clic con la mano vacía para cambiar la forma), vía de cadena (sube el tren), estación (para y sale sola a los 5 s con gente, o con redstone), vía de freno y rizo de 4 bloques de alto. El tren va de 2 a 4 vagones de dos plazas (agáchate y clic con otro tren para añadir uno); cae por las cuestas con su peso, se inclina en las curvas y la cámara da la vuelta entera en el rizo. La barra de seguridad no deja bajarse en marcha. Pensado para ordenadores flojos: las vías son bloques normales y el tren es una sola entidad.
  - Pilares: debajo de la vía que va por el aire sale sola una columna de acero hasta el suelo (una sí y otra no, con su placa y su zapata). Si quitas la vía, su pilar se cae.
  - Los pasajeros se ven girados con el vagón: inclinados en las curvas y las cuestas, y boca abajo en lo alto del rizo.
- **El Purgatorio**: adonde van los errores purgados. Se llega colándose por una pared (a veces, al asfixiarte dentro de un bloque) o cayéndose del mapa, y se vuelve justo a donde estabas por una puerta con el cartel verde de SALIDA (más o menos una cada 60 chunks). Sin cielo y con niebla corta; cada zona tiene su ambiente sonoro en bucle (zumbido de fluorescente, viento con un organillo desafinado a lo lejos, pitidos de máquinas, música de ascensor, audio roto), que se funde al pasar de una a otra. Zonas:
  - **Feria Cerrada**: de noche, casetas con la lona echada, farolas (alguna fundida), un tiovivo desmontado y papeles por el suelo.
  - **Sala de Espera**: baldosas, fluorescentes, filas de sillas y tabiques. Nadie te va a llamar.
  - **Recreativa Infinita**: moqueta de neón de los noventa y filas de máquinas que no se acaban. Casi todas están fundidas (con la pantalla quemada), pero una de cada seis sigue funcionando y se puede jugar.
  - **Hotel del Videoclip**: pasillos sin fin con moqueta de hexágonos (sí, la de la peli), paredes color crema, apliques y puertas cerradas a los dos lados. Detrás de cada puerta, la misma habitación: la cama hecha, la mesilla con su lámpara y nadie. De fondo, música de ascensor por un altavoz del techo.
  - **Mundo sin Texturas**: todo es la textura que falta (cuadros rosa y negro, que se ven igual a oscuras): árboles a los que solo les cargó el tronco, casas a medio cargar con el tejado flotando, bloques sueltos en el aire, algún jugador en pose de T y agujeros en el suelo: por ellos te caes fuera del Purgatorio y vuelves a casa (otra salida).
  - **Habitantes** (los purgados): no ven, pero oyen. Si hablas por el chat de voz te oyen desde lejos, si corres desde menos y andando solo de cerca; agachado no te oyen. Cuando oyen algo van hacia allí a tientas, con los brazos por delante, y si te pillan te vuelven a purgar: te quedas a oscuras un momento y apareces en otra parte del Purgatorio. No hacen daño ni se les puede hacer (si les pegas, se descolocan). Hay tres: uno con la cara de siempre y los ojos en blanco (por fin se sabe adónde fue Herobrine después de tantos «Removed Herobrine»), uno al que no le cargó la textura y un muñeco de relleno gris. Como mucho dos cerca de cada jugador, y no piensan nada mientras no oyen nada.
  - Solo de allí: el **bloque sin textura**, la **moqueta de recreativa** y la **moqueta de hotel** se pueden picar y llevar a casa.
  - Ligero: cada chunk son unos cientos de bloques puestos sin ruido, siempre igual; casi nada tiene bloque-entidad.

## 1.1.0 (en curso: aún no se publica; todo lo nuevo de la 1.1 va aquí)
- **Pociones en supervivencia:** Gigante (seta roja), Enano (seta marrón), Ecolocalización (fragmento de eco), Niebla viviente (tinta de calamar), Espejismo (fragmento de amatista), Helio (pluma), Ventilador (bola de nieve), Borrachera (trigo) y Tartamudeo (granos de cacao), sobre poción rara; la redstone las alarga. Sed de sangre y Fantasma siguen en creativo.
- **Encantamientos nuevos, al estilo Enchancement:**
  - Fuera casi todos los vanilla: Protecciones, Espinas, Filo, Golpeo, Perdición, Empuje, Aspecto ígneo, Barrido, Eficiencia, Irrompibilidad, Reparación, Poder, Retroceso, Fuego, Infinidad, los de la caña, del tridente y de la ballesta, Caída de pluma, Agilidad acuática, Paso helado, Velocidad del alma y Sigilo rápido. Se quedan Toque de seda, Fortuna, Botín, Respiración, Afinidad acuática y las maldiciones.
  - 24 encantamientos nuevos sin niveles (siempre a tope): Percepción y Ojo de búho (casco); Adrenalina, Espinazo y Vitalidad (peto); Ráfaga y Deslizar (pantalones); Rebote, Pegajoso y Zancada (botas); Berserker, Escarcha y Vampiro (espada); Hendidura y Tala (hacha); Presteza (herramientas); Veta (pico); Excavar (pala); Cosecha (azada); Certero y Recuperación (arco); Metralla (ballesta); Tormenta (tridente) y Arpón (caña).
  - Como mucho 2 encantamientos en armas y herramientas y 4 en cada pieza de armadura (las maldiciones no cuentan). Ningún nivel por encima del normal.
  - **Mesa de encantar de elegir:** sin azar. Con el objeto en la mano, clic derecho a la mesa: salen los encantamientos de los libros que haya en estanterías cinceladas alrededor (donde irían las estanterías). Cuesta niveles y lapislázuli (más cuantos más tenga ya); el libro no se gasta.
  - **Sin durabilidad:** nada se gasta ni se rompe (la etiqueta `miscelanea:con_durabilidad` lo devuelve a los objetos que queráis).
  - **Lo que ya teníais:** al llevarlo en el inventario se quita lo prohibido y lo que pase del límite, y se devuelve la experiencia (20 puntos por nivel). Los libros encantados que se queden vacíos (los del botín, por ejemplo) se convierten en un libro de un encantamiento nuevo al azar.
  - En el yunque ya no se ponen encantamientos; los aldeanos ya no venden libros ni cosas encantadas.
  - Todo se ajusta en la config del servidor, sección `encantamientos`; con `activo = false` vuelve a ser vanilla.
- **Equilibrio de armaduras:** entre armadura, dureza, encantamientos y efectos (Resistencia, el perk Guardián de Tombstone…) no se puede quitar más del 75 % de un golpe. Con armaduras de Ice and Fire ya no se es inmortal. Config: sección `equilibrio`.
- **Anillo de medianoche:** decía que The Midnight no estaba instalado aunque lo estuviera. Buscaba la dimensión `midnight:midnight`, pero en The Midnight 0.6.x se llama `midnight:the_midnight`; ahora encuentra cualquiera de las dos (aunque la config antigua tenga el nombre viejo).
- **Gramola:** el decorado del videoclip se pone a la altura de los pies de quien baila. Si el suelo delante de la gramola estaba más alto o más bajo (o había alfombras o losas), el decorado se quedaba a otra altura y la cámara se metía por debajo del suelo.
- **Gramola (videoclip):** sin shaders, los rayos, focos y halos del decorado salían como planos de color macizo tapando la pantalla (las luces suaves se pintaban con el búfer del color opaco). Ahora cada tipo de luz tiene su búfer y se ven translúcidas.

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

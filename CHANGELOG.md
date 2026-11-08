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
- **Rendimiento de la sala recreativa (y de jugar en ella):** la pantalla de la recreativa dibujaba el mueble con unas 350 llamadas de dibujo por fotograma (cada círculo de los botones y cada línea de barrido por separado); ahora va en un solo lote, y la imagen del juego solo se redibuja y se sube cuando hay un paso nuevo. Las pantallas de las máquinas solo se convierten y se suben cuando la máquina se dibuja de verdad (de lejos, o la propia mientras se juega en ella, 3 veces por segundo), y solo los píxeles y las filas que han cambiado, con el efecto de tubo de imagen precalculado. Los textos de las pantallas solo se dibujan a menos de 14 bloques y el marcador de la bolera a menos de 32. Los carteles y tablones de récords dibujan primero los fondos y luego los textos (antes cada cambio era una llamada de dibujo más; el tablón hacía unas cuarenta), ya no buscan su grupo en cada fotograma y las motas del corcho solo se ven de cerca. El tablero ya no calcula el jaque en cada fotograma, y las pociones de tamaño ya no revisan los atributos de cada criatura en cada tic si no hace falta. De cerca se ve igual.

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

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

## 1.2.0 (en curso)
- **Jeringuilla de sedante** (pestaña Tortura): el suavis se seca en el horno, ahumador u hoguera, y con una jeringuilla, dos de suavis seco y una botella de agua se hace la jeringuilla de sedante. Clic derecho a alguien: se marea y tiene un forcejeo como el del mordisco de Dream (A / D) para no dormirse, más difícil si se le pincha por la espalda. Si no lo aguanta (o es un mob), se queda dormido en el suelo 30 s (config `sedante_segundos`); un golpe fuerte le despierta. Agachado y al aire te lo pinchas tú (siesta). Devuelve la jeringuilla vacía. Jeringuillas: pepita de hierro, panel de cristal y lingote de hierro en columna → 3.

## 1.1.0 (en curso: aún no se publica; todo lo nuevo de la 1.1 va aquí)
- **Pociones en supervivencia:** Gigante (seta roja), Enano (seta marrón), Ecolocalización (fragmento de eco), Niebla viviente (tinta de calamar), Espejismo (fragmento de amatista), Helio (pluma), Ventilador (bola de nieve), Borrachera (trigo) y Tartamudeo (granos de cacao), sobre poción rara; la redstone las alarga. Sed de sangre y Fantasma siguen en creativo.
- **Niveles de encantamiento imposibles:** ningún encantamiento (vanilla o de otros mods) puede pasar de su nivel máximo normal. Lo que lo pase (p. ej. Protección VII hecha con el libro de impregnación de Tombstone) se deja en el máximo al llevarlo en el inventario, y se devuelve la experiencia de los niveles quitados (20 puntos por nivel); los libros encantados también se recortan, y los mobs aparecen con el equipo ya recortado. Todo lo demás de los encantamientos sigue como en vanilla. Config: sección `equilibrio` (`recortar_niveles`, `xp_por_nivel`).
- **Equilibrio de armaduras:** entre armadura, dureza, encantamientos y efectos (Resistencia, el perk Guardián de Tombstone…) no se puede quitar más del 75 % de un golpe. Con armaduras de Ice and Fire ya no se es inmortal. Config: sección `equilibrio`.
- **Anillo de medianoche:** decía que The Midnight no estaba instalado aunque lo estuviera. Buscaba la dimensión `midnight:midnight`, pero en The Midnight 0.6.x se llama `midnight:the_midnight`; ahora encuentra cualquiera de las dos (aunque la config antigua tenga el nombre viejo).
- **Gramola:** el decorado del videoclip se pone a la altura de los pies de quien baila. Si el suelo delante de la gramola estaba más alto o más bajo (o había alfombras o losas), el decorado se quedaba a otra altura y la cámara se metía por debajo del suelo.
- **Gramola (videoclip):** sin shaders, los rayos, focos y halos del decorado salían como planos de color macizo tapando la pantalla (las luces suaves se pintaban con el búfer del color opaco). Ahora cada tipo de luz tiene su búfer y se ven translúcidas.
- **Rendimiento de la sala recreativa (y de jugar en ella):** la pantalla de la recreativa dibujaba el mueble con unas 350 llamadas de dibujo por fotograma (cada círculo de los botones y cada línea de barrido por separado); ahora va en un solo lote, y la imagen del juego solo se redibuja y se sube cuando hay un paso nuevo. Las pantallas de las máquinas solo se convierten y se suben cuando la máquina se dibuja de verdad (de lejos, o la propia mientras se juega en ella, 3 veces por segundo), y solo los píxeles y las filas que han cambiado, con el efecto de tubo de imagen precalculado. Los textos de las pantallas solo se dibujan a menos de 14 bloques y el marcador de la bolera a menos de 32. Los carteles y tablones de récords dibujan primero los fondos y luego los textos (antes cada cambio era una llamada de dibujo más; el tablón hacía unas cuarenta), ya no buscan su grupo en cada fotograma y las motas del corcho solo se ven de cerca. El tablero ya no calcula el jaque en cada fotograma, y las pociones de tamaño ya no revisan los atributos de cada criatura en cada tic si no hace falta. De cerca se ve igual.
- **Puntero láser:** el punto se iba a un lado de la mira (a veces bastante a la derecha y un poco arriba). Ahora el rayo propio pasa por el centro de la pantalla tal como se dibuja cada fotograma (con el FOV dinámico, la proporción de la ventana, el tamaño del jugador, el balanceo y las cámaras de otros mods), y los demás jugadores ven el punto donde mira el que lo lleva. La punta del rayo sigue a la mano con cualquier FOV.
- **Ecolocalización:** los pasos no hacían ondas, ni los tuyos ni los de los demás jugadores. Solo salían si llegaba a sonar el sonido de cada paso. Ahora las ondas salen de lo que anda cada uno por el suelo: tus pasos, los de los demás jugadores y los de los mobs (agachado o volando, no suenan, como en vanilla). Los jugadores siguen viéndose como silueta solo mientras hablan; al andar sale solo la onda de sus pasos.
- **Ecolocalización:** con alguna criatura con algo encantado (el brillo), atada con correa o con nombre, y con los jugadores que hablan (por su nombre), las siluetas fallaban al dibujarse en cada fotograma y se borraban todas: no se veía ninguna silueta y el que hablaba parpadeaba a medio encender. Con shaders, ese fallo dejaba el búfer roto y el juego se cerraba. Ahora esas capas simplemente no se dibujan en la silueta.
- **Ecolocalización:** a ciegas ya no se ven el sol, la luna ni las estrellas (como con ceguera), ni los nombres de los jugadores, que se veían a través de la niebla y delataban dónde estaba cada uno aunque no hablara.
- **Ecolocalización:** al cambiar de dimensión, reaparecer o salir del mundo se borran las ondas y siluetas de antes (salían un rato en sitios que no eran, y al salir se quedaban en memoria el mundo viejo y sus criaturas). La caja de cada silueta ya no va un tic por delante de la criatura. Con shaders, las ondas y siluetas ya no se dibujan también en el mapa de sombras.
- **Ecolocalización con shaders:** el pack no hacía caso de la niebla (lo lejano se veía gris), las ondas y aristas no salían y las siluetas se veían rotas. Ahora, con shaders, todo se pone negro al acabar de dibujar el mundo y encima se dibujan las ondas, las aristas y las siluetas. Con shaders, las paredes no tapan lo que hay detrás: se ve a través de ellas.

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

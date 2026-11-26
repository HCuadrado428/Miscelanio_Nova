# Miscelánea

**Un mod de Forge para Minecraft 1.20.1 que junta en uno solo una sala recreativa, una feria, juegos de mesa, un móvil con apps, armas sacadas de videojuegos, pociones raras, bromas entre amigos y un hombre de estática.** Está hecho para servidores de amigos y rol: casi todo se juega con otras personas.

| | |
|---|---|
| **Minecraft** | Java 1.20.1 |
| **Cargador** | Forge 47.x |
| **Lado** | Cliente **y** servidor (los dos necesitan el mod) |
| **Idiomas** | Español e inglés |
| **Arte** | Pixel art a 32×32 (2 texeles por píxel del modelo), sonidos generados por código |

---

## Índice

1. [Instalación](#instalación)
2. [Primeros pasos](#primeros-pasos)
3. [El móvil](#el-móvil)
4. [Banco, Bizum y cajero](#banco-bizum-y-cajero)
5. [Sala recreativa](#sala-recreativa)
6. [Feria](#feria)
7. [Juegos de mesa y mesas](#juegos-de-mesa-y-mesas)
8. [Pista de baile](#pista-de-baile)
9. [Armas y equipo](#armas-y-equipo)
10. [El hombre de estática](#el-hombre-de-estática)
11. [Pociones y efectos](#pociones-y-efectos)
12. [Bromas](#bromas)
13. [Sala de juegos](#sala-de-juegos)
14. [Rave, discoteca y concierto](#rave-discoteca-y-concierto)
15. [Colección, pesca y tesoros](#colección-pesca-y-tesoros)
16. [Casa, cocina y movimiento](#casa-cocina-y-movimiento)
17. [Comandos](#comandos)
18. [Configuración](#configuración)
19. [Compatibilidad con otros mods](#compatibilidad-con-otros-mods)
20. [Compilar desde el código](#compilar-desde-el-código)

---

## Instalación

1. Instala **Minecraft Java 1.20.1** con **Forge 47.x**.
2. Descarga el `.jar` de Miscelánea desde [Releases](https://github.com/HCuadrado428/Miscelanio_Nova/releases) y ponlo en la carpeta `mods`.
3. Añade las dependencias obligatorias:
   - **GeckoLib** (Forge 1.20.1)
   - **playerAnimator** (Forge 1.20.1)
4. En un servidor, el mod y las dependencias van **en el servidor y en todos los clientes**, siempre en la misma versión. Si las versiones no coinciden, el juego no deja entrar.

**Opcionales** (el mod funciona sin ellos y, si están, se integra con ellos):

| Mod | Para qué |
|---|---|
| **Simple Voice Chat** (2.5.0 o más) | Llamadas y videollamadas, notas de voz, imitar voces de la estática y voces con efecto |
| **Customizable Player Models** | Las texturas de la estática y las heridas se adaptan a cada modelo |
| **bendy-lib** (4.0.x) | Codos y rodillas que se doblan en las animaciones |
| **OmegaChips** | El banco usa sus monedas en vez de esmeraldas |
| **JEI** | Consultar recetas |
| **Incapacitated** | La sala de juegos respeta el estado de "incapacitado" |
| **WaterFrames** | Sus teles, cuadros y proyectores cuentan como pantallas para la estática |
| **SecurityCraft** | La estática viaja por las cámaras de seguridad y sale en los monitores |

---

## Primeros pasos

- Todo el contenido está en **9 pestañas del creativo**: Feria y atracciones, Sala recreativa, Juegos de mesa, Armas y combate, Sala de juegos, Pociones y efectos, Bromas, Peluches y Varios.
- **Teclas con preferencia:** mientras usas un arma o poder del mod, sus teclas solo hacen lo suyo aunque otro mod use la misma tecla (por ejemplo, R para recargar la ballesta no recarga los sombreadores, y V para fijar objetivo con la Llave Espada o el Hacha Leviatán no abre el chat de voz). Las teclas de Minecraft y la rueda del ratón siguen funcionando. Fuera de ese momento, la tecla es de todos.
- **Bloques solo de creativo** (las máquinas, atracciones y aparatos que no tienen receta): en supervivencia son **irrompibles**, ni se pican ni los vuela una explosión. En creativo se quitan normal. La lista es la etiqueta `miscelanea:solo_creativo`.
- Las teclas propias están en **Opciones → Controles → Miscelánea** y se pueden cambiar todas. Si algo no responde, mira que su tecla no choque con otra.
- Cada objeto explica en su descripción cómo se usa.
- Los marcadores de récords y los rankings se guardan en el servidor. Usa `/miscelanea ranking` para verlos.

---

## El móvil

Un smartphone de verdad dentro del juego. Se abre con **clic derecho** o con su tecla (**B** por defecto) y la carcasa se tiñe con tintes.

| App | Qué hace |
|---|---|
| **Chats** | Mensajes privados y de grupo, con fotos y notas de voz (mantén pulsado el micro) |
| **Teléfono** | Llamadas y videollamadas con Simple Voice Chat, a cualquier distancia. Llamadas de grupo (hasta 4, con el teléfono de la cabecera del chat de grupo), llamada en espera (coges la nueva y la otra espera; tocas la tarjeta para cambiar), número oculto (tecla #31# del teclado o en Ajustes: te ven como «Desconocido»), botón de mudo, manos libres (se guarda el móvil y la llamada sigue mientras te mueves y juegas, con una tarjeta arriba a la derecha; en videollamada se manda lo que ves; clic derecho con el móvil o su tecla te devuelve a la llamada) y un tono distinto para cada contacto (en su ficha). En las de grupo se marca en verde quién habla |
| **Agenda** | Contactos con número propio y si están conectados |
| **Cámara y galería** | Fotos y selfies en vertical o en horizontal (botón del móvil tumbado o tecla R: el móvil gira en la pantalla). Zoom con la rueda (hasta x4), temporizador de 3 o 10 s, filtros (blanco y negro, sepia, antigua, viva, visión nocturna, 8 bits, Game Boy y térmica), flash de verdad (ilumina el sitio y deslumbra a quien esté delante mirándote; también automático) y vídeos cortos de hasta 10 s con sonido (si hay Simple Voice Chat) que se ven en la galería y se pueden mandar por chat. Las fotos apaisadas se ven giradas a pantalla completa. Todo se guarda en el servidor |
| **Minegram** | La red social del servidor: publicaciones con pie de foto, me gusta (doble clic), comentarios, seguidores, perfiles y notificaciones |
| **Ubicación** | Compartir dónde estás en tiempo real durante 15, 30 o 60 minutos |
| **Mapa y GPS** | Mapa de lo explorado, puntos guardados, amigos que comparten ubicación y una flecha de navegación en el HUD |
| **Banco** | Saldo, Bizum, enviar objetos e historial (ver abajo) |
| **Reloj** | Hora y alarma |
| **Linterna** | Ilumina de verdad con luces invisibles que se quitan solas |
| **Ajustes** | Tono de llamada (seis, entre ellos el Gran Vals y una bocina de payaso), ocultar tu número y otras opciones |

El móvil no tiene batería: funciona siempre. Los nombres salen como los ve el juego (si usas Fake Name, con el nombre falso) y, con Customizable Player Models, las caras de los jugadores que tengas cerca son la cabeza de su modelo. En la pantalla de inicio hay una tarjeta con la hora, el día, la luna y el tiempo que hace. Durante una llamada puedes salir a otras apps; la pastilla verde de arriba te devuelve a la llamada. Si cierras el móvil con una llamada en marcha, al volver a abrirlo se abre directamente en la llamada.

Los mensajes, fotos y publicaciones se guardan en el servidor. El mapa explorado se guarda en el cliente, en `config/miscelanea_movil_mapa/`.

---

## Banco, Bizum y cajero

El banco guarda el dinero de cada jugador en el servidor.

- **Con OmegaChips**, el dinero son **chips**: 1 chip = 1 moneda de cobre.

  | Moneda | Valor | Bloque (4 monedas) |
  |---|---|---|
  | Cobre | 1 | 4 |
  | Hierro | 9 | 36 |
  | Oro | 81 | 324 |
  | Diamante | 729 | 2916 |

- **Sin OmegaChips**, el dinero son esmeraldas (1) y bloques de esmeralda (9).

**Dónde se mueve el dinero**

- **Cajero automático** (bloque): ingresa y retira **sin límite**. Tiene teclado, un botón por moneda para sumar su valor, "Ingresar todo" y "Retirar todo", y muestra el saldo desglosado en monedas. Al retirar te da el mínimo de monedas posible, y si pagas de más te devuelve el cambio.
- **Desde el móvil**, lejos de un cajero, solo se puede mover un poco: por defecto 81 por operación y 729 al día. Se cambia en la configuración.
- **Bizum**: transferencias a cualquier contacto, con concepto y notificación. También funciona aunque el otro no esté conectado.
- **Enviar objetos**: pasa un objeto de tu inventario a otro jugador conectado.
- **Fichas de recreativa**: se compran en el cajero con el saldo (por defecto 9 cada una) y sirven para las recreativas y la feria.

Receta del cajero: lingotes de hierro, panel de cristal, redstone y un lingote de oro. Cada movimiento queda en el historial de la app y en el registro del servidor (`[Banco] ...`).

---

## Sala recreativa

Máquinas que funcionan con **fichas**. Hay recreativas sencillas, dobles para 2 jugadores, mesa cóctel, cabina de carreras y recreativa de pistola, además de una **consola de bolsillo** con cartuchos.

- **Juegos:** Tetrominós, Serpiente, Ladrillos, Pong, Pájaro Saltarín, Invasores, Comecocos, Duelo de Bloques, Comando Chatarra, Puños de Barrio, Duelo Final, Pinball Galaxia, Mazmorra 3D, Turbo Costa y Astro Patrulla.
- Las máquinas de un solo juego empiezan directamente.
- **Juegos de dos jugadores:** al meter la ficha sale «Esperando a otro jugador». Otro jugador se une con clic derecho en esa misma máquina con su ficha, o abajo está el botón **Jugar contra la CPU** (o START).
- La **pista de baile** y la **gramola de videoclip** también están en la pestaña de la sala recreativa, como en el tablón de récords.
- Si alguien está jugando, la partida se ve en la pantalla **en tiempo real** para los que miran.
- **Bolera**, la típica de salón recreativo: la plataforma con los diez bolos y su marcador, la bola y los tramos de **pista de bolos** (la misma madera con canaletas negras) para alargar la calle por delante. Receta de la pista: tinte negro, tablones y tinte negro (da 4).
- El **marcador retro** y el **tablón de récords** guardan las mejores puntuaciones.
- Las **fichas** se compran en el cajero automático con el saldo del banco.

## Feria

- **Atracciones** en las que se puede montar: noria, tiovivo, pulpo, coches de choque, vagoneta de la bruja y caja de sustos.
- **Puestos:** caseta de tiro con escopeta de corcho (la escopeta es prestada: se recoge sola al acabar), pesca de patitos, tómbola, máquina de gancho, tragaperras, gachapón, canastas, boxeo, martillo de fuerza, dardos, petanca, torre de bloques y churrería.
- **Pizarra de récords de la feria:** una pizarra de tiza con marco de madera, banderines y repisa. Varias juntas forman una pizarra grande que enseña más juegos a la vez.
- **Comida y ambiente:** algodón de azúcar, palomitas, churros y castillo de fuegos artificiales.
- Los premios son peluches, chapas y cápsulas sorpresa.

## Juegos de mesa y mesas

- **Tablero de juegos:** ajedrez, damas y Reversi.
- **Tableros propios:** Imperio (juego de propiedades), Parchís, Oca, Cuatro en raya, Hundir la flota y Dominó.
- **Mesas:** billar, futbolín, air hockey y cartas.
- Se juega contra otras personas o, en algunos juegos, contra la CPU. La cámara y los gestos ayudan a ver el tablero, sin textos flotantes.

## Pista de baile

Un juego de ritmo para varios jugadores, con flechas (teclas configurables), notas largas, una cámara que sigue la coreografía y una coreografía propia para cada canción.

- **Tres dificultades que se notan:**
  - Fácil: solo en los tiempos, sin saltos, mucho margen (~1 flecha por segundo).
  - Normal: algún contratiempo, saltos en el estribillo (~2 por segundo).
  - Difícil: corcheas casi seguidas, ráfagas rápidas, escaleras, muchos saltos, poco margen y los fallos quitan más vida (~4 por segundo).
  - El menú enseña cuántas flechas tiene la canción y su nivel del 1 al 10.
- **En el menú**, debajo de las canciones:
  - **Flechas suben / bajan:** las flechas suben hacia los receptores de arriba (como siempre) o bajan hacia unos receptores abajo. Se guarda para la próxima vez.
  - **Probar teclas:** las cuatro flechas se encienden al pulsarlas, con su tecla debajo y, en amarillo, qué más hace esa tecla en el juego.
- **Mientras se usa la pista o la gramola, las teclas solo sirven para bailar:** no se disparan armas, ni se abren menús, ni se activan cosas de otros mods. Solo funcionan el chat (T y /) y las teclas del chat de voz.

- **Canciones del servidor:** Boogie Wonderland, P.Y.T. y Weapon of Choice. La de Weapon of Choice tiene una coreografía inspirada en su videoclip: empieza sentado en el sillón, se levanta con chasquidos, meneo de hombros, el paseo chulesco y patadas, se desliza como por las escaleras mecánicas, vuela con los brazos en cruz y aterriza otra vez en el sillón.

Las canciones van dentro del mod (`assets/miscelanea_canciones`). El repositorio es **privado** porque son música con derechos: no se debe hacer público mientras estén aquí.

## Gramola de videoclip

Como la pista de baile, pero se baila el videoclip entero. Pones la gramola, haces clic, eliges canción y, al empezar, un fogonazo blanco transforma todo lo que ves en el decorado de la canción. El mundo de verdad no cambia: el decorado solo existe en la pantalla de los que bailan.

- **Weapon of Choice:** el vestíbulo de un hotel elegante, con suelo de mármol, alfombra roja, columnas, galería, escaleras mecánicas, ascensores y lámparas de araña. Con el paseo chulesco avanzas por el vestíbulo, en el vuelo planeas entre las lámparas y al final aterrizas en el sillón.
- **Boogie Wonderland** (y cualquier canción sin decorado propio): una discoteca de los setenta, con pista de baldosas que se encienden al ritmo, bola de espejos, focos, neones y un sol de rayos detrás del escenario.
- **Con más gente:** quien pone la gramola es el protagonista. Los demás pueden unirse con clic derecho entre canción y canción (hasta 5 en total) y bailan detrás en formación, cada uno con sus flechas y su puntuación. Nadie se pisa ni se atraviesa, porque cada uno tiene su sitio.
- **Dos gramolas cerca:** igual que con las pistas, si ya suena una canción cerca no empieza otra, así que no se mezclan decorados ni músicas.
- Hace falta espacio libre delante de la gramola (dos bloques). Agáchate para dejar de bailar.

---

## Armas y equipo

Cada arma tiene modelo, animaciones, sonidos y controles propios. Hacen bastante daño en servidores con mods fuertes (Ice and Fire, Protección V), porque parte del daño atraviesa la armadura.

| Objeto | En pocas palabras |
|---|---|
| **Aspirafantasmas 3000** | Aspira objetos y criaturas, sopla, ciega con un destello y guarda pociones. Mochila en el pecho y boquilla en la mano; los encantamientos desbloquean modos |
| **Porra eléctrica** | Descargas que aturden y bloquean el uso de objetos; también agarra al objetivo |
| **Medigun** | Cura a distancia con un rayo y carga la ÜberCarga |
| **Superescopeta** | Disparo potente y gancho para acercarte |
| **Lanzacohetes** | Recarga de cohete en cohete |
| **Ballesta de repetición** | Ráfagas con recarga propia |
| **BFG** | Cañón de plasma enorme |
| **Llave Espada** | Combos en tierra y aire, fijar objetivo y los comandos Piro, Hielo y Cura |
| **Hacha Leviatán** | Lanzar y recuperar el hacha, escudo, parry, esquivas y ataques rúnicos |
| **Pistola de gravedad** | Coger, mover y lanzar cosas |
| **Lanzatintas y rodillo** | Combate con tinta |
| **Puntero láser** | Apunta, deslumbra y vuelve locos a los gatos |
| **Núcleo de error** *(creativo)* | Carga un aliento que corrompe bloques blandos |
| **Hacha de sangre** *(creativo)* | Bebe de lo que golpea y despierta en cuatro fases: guarda sangre y suelta la Siega (giro que corta a todos), cobra el Diezmo (pagas vida y el golpe vale por tres), deja charcos que te curan y, al final, el Diezmo sale gratis de vez en cuando. Tiene sus encantamientos (Sed, Coágulo y Hemorragia) y su sección `hacha_sangre` en la config |

### Armas de fuego

Trece armas de cobre de la vida real, a lo Minecraft. **No se fabrican: se encuentran en cofres** de estructuras (aldeas, barcos hundidos, puestos de saqueadores, pirámides, minas, mansiones, fortalezas, bastiones, tesoros enterrados, templos de la jungla, ciudades antiguas y ruinas submarinas). JEI dice dónde sale cada una y las probabilidades se cambian en la config del servidor. La munición sí se fabrica.

| Arma | En pocas palabras |
|---|---|
| **Revólver de ruleta** | Seis recámaras que se cargan una a una con la munición que quieras; agachado + clic izquierdo gira el tambor. Retos de **duelo** a otro jugador |
| **Rifle de catalejo** | Rifle de cerrojo con catalejo: mira de verdad, bala con caída, más daño en la cabeza y tirolinas con la bala de cuerda |
| **Escopeta de presión** | Aire comprimido en vez de pólvora: se bombea, tiene manómetro y revienta si te pasas. Cartuchos de grava, escarcha, miel, sal, semillas y más |
| **Gatling de manivela** | Se planta en su trípode; se calienta y se atasca. Hace de torreta con redstone |
| **Winchester 1873** | Palanca y depósito de 12 balas mezclables que se cargan una a una |
| **Derringer** | Pistola de bolsillo: el as en la manga, que se saca con una tecla |
| **Thompson con estuche de violín** | Ráfaga con mucho retroceso; el estuche es un violín que se toca con arco |
| **Mosquete de chispa con bayoneta** | Mucho daño, recarga lenta y bayoneta con cualquier espada y sus encantamientos |
| **Cañón de mano** | Dispara bolaños y bloques que caen; a veces revienta en las manos |
| **Cerbatana de cobre** | Silenciosa (ni el sculk ni el Warden la oyen). Dardos normales, de veneno y somníferos |
| **Lanzallamas de cobre** | Un chorro corto y ancho que quema todo el cono. Bidones de combustible; no prende bajo el agua |
| **Pistola de resonancia** | Una onda de sonido que atraviesa bloques y armadura. Funciona con fragmentos de eco; es ruidosa |
| **Arponera de cobre** | Un arpón con cuerda: trae hacia ti lo que alcanza, o te lleva a ti hasta un bloque. Mucho más fuerte bajo el agua |

- **Granadas** de humo, metralla y pegamento de miel, que se fabrican con cobre y pólvora.
- **Cómo se manejan:** clic izquierdo dispara y mantener el clic derecho apunta (el arma va al centro de la pantalla, el campo de visión se estrecha y dispara más junto). El rifle sigue con su catalejo y la Derringer, el cañón de mano y el lanzallamas van siempre a la cadera. **Pulso firme** apunta mejor; la tecla N inspecciona el arma.
- **Accesorios que se ven en el arma:** Silenciador (rifle, Derringer, Winchester y Thompson), Cañón largo (revólver), Estrangulador (escopeta), Mira de cobre (mosquete) y Ánima rayada (cañón de mano).
- **Cobre que se oxida:** las piezas de cobre pasan por cuatro fases con el uso. Oxidada, el arma se encasquilla y dispersa más, pero pega y perfora un poco más. Se encera con un panal (la cera se gasta) y se rasca con un hacha.
- **Ruido:** cada arma se oye a su distancia; los monstruos van a ver de dónde viene el disparo y los animales y aldeanos huyen. El Silenciador casi lo quita.
- **Mobs armados:** algunos saqueadores llevan una Winchester y disparan de verdad (se cubren, flanquean y acuden al ruido). Sección `mobs_armados` de la config.
- **Ropa de vaquero y de bandido:** se fabrica con cuero y da ventajas con las armas. El vaquero apunta mejor, recarga más deprisa y oxida menos el cobre; el bandido hace menos ruido y a veces no gasta la bala. El juego completo da un extra. Sección `ropa` de la config.
- **Encantamientos propios** por arma (mesa y libros) y **munición especial** (bengalas, dardos de sedante, balas de plata, de hielo, de oro, de pintura...).
- Ningún disparo suelto mata de un tiro a un jugador con la vida llena (salvo la ruleta rusa). Todos los valores de equilibrio están en la sección `armas_de_fuego_equilibrio` de la config.

---

## El hombre de estática

La **poción de estática** (solo de creativo) te convierte en un ser de ruido de televisión. Tiene una cinemática de transformación y la textura de estática se adapta a cada skin y a CPM.

**Pasivos**
- El puñetazo con la mano vacía da una descarga que salta a los enemigos de alrededor.
- **Fragmentarse:** esquivas golpes y flechas deshaciéndote en estática.
- **Fundirse en la oscuridad:** si te quedas quieto a oscuras, solo se te ven los ojos.
- Te curas a oscuras, las luces se apagan a tu paso y dejas charcos de estática.

**Señal:** una barra que se llena al golpear, al acertar poderes y despacio a oscuras. Los poderes fuertes la gastan.

**Rueda de poderes:** mantén su tecla, elige con el ratón o con los números, y usa el poder con **clic derecho y la mano vacía**. Puedes seguir andando con la rueda abierta.

| Poder | Qué hace |
|---|---|
| **Parpadeo** | Hasta 3 saltos seguidos. Dejas una silueta que explota y el tercer salto pega el doble |
| **Tentáculos** | Agarras a alguien y eliges: clic izquierdo lo estampa, clic derecho lo lanza y, si no haces nada, lo atraes |
| **Pausa** | Congelas al objetivo; todo el daño que reciba le llega de golpe al soltarlo, multiplicado por 1,5 |
| **Canal perdido** | Metes a la víctima en un vacío de estática y la escupes lejos, mareada |
| **Meterse en pantallas** | Entras por una tele, recreativa, pantalla de WaterFrames o cámara de SecurityCraft y sales por otra con un fogonazo; tu cara se ve en las pantallas por las que pasas y en el monitor de quien mire esa cámara |
| **Imitar voces** | Reproduces voces grabadas desde un bloque y las teles cercanas (con Simple Voice Chat) |
| **Rebobinar** | Vuelves a donde estabas hace 4 segundos, con la vida de entonces |
| **Sin señal** | Una zona de ruido donde los enemigos no ven y tú pegas más fuerte |
| **Grabación** | Un clon repite tus últimos 3 segundos y tus golpes |
| **Sobrecarga** | La definitiva: gasta toda la Señal, hace un daño enorme y deja a todos sin señal |

---

## Pociones y efectos

Casi todos los efectos tienen poción normal, arrojadiza, persistente y flecha. El **caldero de mezclas** combina varios efectos en un frasco.

- **Relaciones:** Cupido (atrae a los afectados entre sí) y Enemigo (se hacen daño al acercarse).
- **Cuerpo:** Gigante, Enano, Helio, Ventilador y Borrachera.
- **Mente:** Miedo, Espejismo, Tartamudeo y Sed de sangre. El Miedo también llega con la granada de gas (deja una nube de humo verde) y con el dardo, y trae los sustos del Espantapájaros, con su saco de arpillera por cabeza, que también se puede llevar puesto.
- **Sigilo:** Fantasma *(creativo: eres invisible y solo apareces de golpe, tal cual, por el rabillo del ojo de los demás)*, Ecolocalización (con Simple Voice Chat, los jugadores solo se ven mientras hablan, y tu voz también hace ondas) y Niebla viviente.
- **Rol** *(creativo)*:
  - Esencia y Veneno de Dream. El veneno mata al instante, con su propia animación.
  - Anillo de medianoche: mantén clic derecho para ir a The Midnight.
  - Mordisco venenoso: un agarre con veneno sostenido y un minijuego para escapar.
  - Muestra de glitch.
  - Estática (ver la sección anterior).

---

**Se pueden hacer en el soporte para pociones** (sobre una poción rara; la redstone las alarga; la pólvora y el aliento de dragón, como siempre):

| Ingrediente | Poción |
| --- | --- |
| Seta roja | Gigante |
| Seta marrón (o Gigante + ojo de araña fermentado) | Enano |
| Fragmento de eco | Ecolocalización |
| Tinta de calamar | Niebla viviente |
| Fragmento de amatista | Espejismo |
| Pluma | Helio |
| Bola de nieve | Ventilador |
| Trigo | Borrachera |
| Granos de cacao | Tartamudeo |
| Pétalos rosas (Cupido) y Cupido + ojo de araña fermentado (Enemigo) | Cupido / Enemigo |

Sed de sangre y Fantasma siguen siendo solo de creativo.

## Bromas

Inofensivas pero molestas. Mejor acordarlas con el grupo antes de usarlas.

- **Varita uwu:** el chat de la víctima escribe "uwu".
- **Poción de controles cambiados.**
- **Espejo del revés:** pone la pantalla al revés.
- **Cartel de «PATÉAME».**
- **Duende travieso:** te revuelve el inventario.
- **Rueda de Catalina.**

## Sala de juegos

Trampas y aparatos "de película de miedo", en versión graciosa: cepos, grilletes, jaulas, la caja de agua, el péndulo, la silla de inmersión, la silla de interrogatorio y más. Muchos tienen su minijuego para escapar (ganzúa, alicates, sierra...), y al que está en el cepo se le pueden tirar tomates y huevos podridos. Es para rol y hay que acordarlo con quien participe. Incluye la **jeringuilla de sedante**: se hace con suavis seco y un poco de agua, marea a quien pincha (con un forcejeo para no dormirse, más difícil por la espalda) y, si no lo aguanta, lo deja dormido en el suelo medio minuto. El administrador puede desactivar partes en la configuración.

### Laboratorio de experimentos

En la pestaña **Tortura**: estaciones donde un cautivo hace de sujeto de pruebas, con un cuaderno que va apuntando los resultados. Todas se manejan desde un puesto de mando en el bloque de detrás y tienen tres niveles (**suave**, cómico; **intenso**, inquietante; **extremo**, con daño de verdad, que pide pulsar dos veces para confirmarlo). En la configuración (`[laboratorio]`) se pueden apagar las estaciones o limitar el nivel máximo.

- **Para medir y anotar:** *cuaderno de laboratorio* (lee las fichas), *pulsómetro* (pulso y miedo de quien tocas), *cronómetro* y *portapapeles* (pone "Sujeto N" a cada jugador).
- **Estaciones** (solo creativo): *centrífuga* (silla en un brazo que gira, con QTE para aguantar), *cámara de temperatura* (cabina de cristal con termostato de -3 a +3), *tanque de privación sensorial* (oscuridad y ruidos que solo oye el sujeto) y *laberinto de la rata* (un laberinto que se construye solo; el sujeto, encogido, tiene que llegar al queso esquivando baldosas con descarga).
- **Muestras:** al acabar un experimento, con un *tubo de ensayo* en el inventario, sale una muestra (de mareo, de frío, de calor o de miedo).
- **Mezclas:** en la *mesa de mezclas* dos ingredientes (muestras, queso o sueros) dan un *suero sin etiquetar* con uno de nueve efectos, siempre el mismo para cada pareja. El *analizador* lo identifica y lo apunta en el cuaderno.
- **Destilador térmico:** convierte el suavis seco (de The Midnight, cocinado) en sedante clase M con fuego debajo; con una botella de cristal se saca la Poción de sedante.

### Cámaras de vigilancia y televisiones

- **Cámara de vigilancia:** se cuelga de una pared o del techo y manda su imagen en directo a las pantallas de su canal (clic derecho: canal siguiente). Si la renombras en un yunque, su nombre sale en la pantalla.
- **Televisión:** pantalla plana de 2 bloques de ancho (ocupa su bloque y el de su derecha). Clic derecho: la imagen en pantalla grande; agachado + clic: canal (el 0 rota entre todas las cámaras).
- **Monitor de laboratorio:** pon varios juntos en rectángulo (hasta 8x8) y forman una sola pantalla grande.
- En el móvil, la app **Cámaras** enseña cualquier cámara desde donde estés.

---

## Rave, discoteca y concierto

Pestaña **Rave** del creativo. Todo se enciende o apaga con clic derecho, cambia de patrón con agachado + clic, de color con un tinte, y también va con redstone.

- **Neón:** tubos y paneles de 16 colores que alumbran, barras luminosas que se lanzan y alumbran 5 minutos donde caen, y pulseras y collares de neón para llevar puestos (clic derecho; no ocupan armadura).
- **Luces:** bola de discoteca (bola de espejos grande: cuélgala con 2 bloques libres debajo; manchas de luz por todo el local), foco móvil (haz de color que barre; en el suelo o colgado) y estroboscopio (respeta «Ocultar destellos»).
- **Cabina de DJ y torres de altavoces:** la cabina mide 3 bloques de ancho y cada torre 3 de alto (necesitan ese hueco libre; se ponen y se rompen enteras). La cabina pone las canciones de la pista de baile y los discos, sueltas o en una lista de reproducción (en orden o al azar); se oye a 32 bloques y cada altavoz cercano suma 24 (hasta 128). Con Simple Voice Chat, **🎧 Emitir mi PC** saca por la mesa lo que suene en tu ordenador (elige la entrada «Mezcla estéreo» o un cable de audio virtual).
- **Efectos:** máquina de humo de colores, fuente de chispas frías y cañón de confeti.
- **Escenario:** andamio (truss) que se une solo y lleva luces LED, tarima y suelo LED que cambia al ritmo de la mesa.
- **Pantalla de vídeo:** paneles que forman una pantalla (hasta 12×8) y enseñan en bucle un vídeo corto de la galería de tu móvil.
- **Panel y mando de luces:** manejan de golpe todas las luces a 24 bloques (encender, apagar, patrón, color, confeti).
- **Caramelos de fiesta:** corazón, piruleta arcoíris, polvo de estrellas y chicle eléctrico: un subidón con efectos y, al acabar, medio minuto de bajón.

Configuración en `[rave]` (alcance de la música). Las recetas están en el CHANGELOG.

## Colección, pesca y tesoros

- **Red cazabichos y caña de pescar:** mariposas, luciérnagas, escorpiones, celacantos y más. Cada pez tiene su tamaño en centímetros.
- **Bichopedia:** lo que has encontrado y dónde vive cada criatura.
- **Vitrina del museo, acuario y terrario:** para exponer la colección.
- **Detector de metales:** fósiles, mosquito en ámbar, tesoros y cápsulas del tiempo con notas.
- **Peluches de personas** (pestaña «Peluches»): un bloque por persona que chilla al apretarlo y se cansa, se abraza agachado, se lanza y rebota y se lleva de gorro. Con uno encima no salen fantasmas. También se exponen en la vitrina.
- **Marco de fotos:** un bloque de pared que enseña una foto de tu galería del móvil; con el móvil en la mano se van pasando (agachado, hacia atrás) y lo ve todo el mundo.

## Casa, cocina y movimiento

- **Muebles:** sillas, sofás, mesas que se unen entre ellas, taburetes y lámparas de pie.
- **Cocina:** tomates y arroz, fogón, paella, tortilla de patatas, gazpacho y chocolate con churros.
- **Ocarina:** melodías que cambian la hora o el tiempo, o te llevan a casa.
- **Tirolina:** dos postes unidos por cable, hasta 64 bloques.
- **Trampolín.**

---

## Comandos

Todos empiezan por `/miscelanea`. Menos `ranking`, todos necesitan nivel de operador 2.

| Comando | Para qué |
|---|---|
| `ranking [categoría]` | Ver los 10 mejores de un juego, o la lista de categorías |
| `pruebas` | Construye delante de ti una zona con todo el mod ya colocado (una calle por pestaña del creativo), cofres con el resto de objetos y uno con dinero |
| `banco <jugador> ver` · `dar <n>` · `quitar <n>` | Ver o cambiar el saldo del banco |
| `voz <jugadores> poner <voz>` · `quitar <voz>` · `limpiar` | Voces con efecto fijas (por ejemplo, con estática), sin necesidad de poción |
| `heridas poner <jugador> <parte> <tipo> <gravedad>` · `heridas curar <jugador>` | Heridas visibles para rol |
| `espejismo <jugador> <objetivo>` | El jugador ve a otro con el aspecto del objetivo |

---

## Configuración

Todo está en **`serverconfig/miscelanea-server.toml`**, dentro de la carpeta del mundo. Forge la copia a los clientes al entrar.

Se puede activar o desactivar cada sistema por separado y ajustar sus números: daños y enfriamientos de armas y poderes, costes de Señal, límites del banco y del móvil, bromas y sala de juegos, radios, duraciones, etc. Cada opción lleva un comentario en español que explica qué hace.

Si actualizas el mod con un archivo de configuración ya creado, se mantienen tus valores. Para recuperar un valor por defecto nuevo, borra esa línea y Forge la vuelve a escribir.

---

## Compatibilidad con otros mods

Probado pensando en un servidor con, entre otros:

- **Customizable Player Models**
- **Simple Voice Chat**
- **Pehkui**
- **Incapacitated**
- **Ice and Fire**
- **Corail Tombstone**
- **OmegaChips**

Además:

- El daño de las armas y de la estática atraviesa en parte la armadura y la Protección, para que siga contando contra equipo muy fuerte.
- Los jefes (dragón, wither y la etiqueta `forge:bosses`) reciben menos efectos; se ajusta en la configuración.
- Con **Pehkui** (el mod de tamaños que usa Customizable Player Models), quien entra en un aparato de la sala de juegos, una cápsula del laboratorio, el cepo, la jaula o una atracción de la feria pasa a talla normal mientras está dentro y recupera la suya al salir. Se ajusta en la configuración (`[pehkui]`: `ajustar_talla`, `ajustar_en_feria` y `talla`).
- Los contactos y el Bizum se guardan por el identificador interno de cada jugador (UUID), así que cambiarse el nombre (Fakename) no los rompe.

---

## Compilar desde el código

Necesitas **JDK 17**. La primera vez hace falta internet para descargar las dependencias.

```bash
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

El `.jar` sale en `build/libs/`. Para probar el cliente: `./gradlew runClient`.

Notas para quien contribuya:

- Las texturas, modelos y sonidos se generan con scripts de Python. Las canciones con copyright están en `assets/miscelanea_canciones`; por eso el repositorio es privado.
- Cada `push` a `main` compila en GitHub Actions y publica el `.jar` en Releases como `build-N`.

---

Miscelánea sigue creciendo. Abre las pestañas del creativo, lee las descripciones y prueba cosas con tus amigos.

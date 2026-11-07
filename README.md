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
14. [Colección, pesca y tesoros](#colección-pesca-y-tesoros)
15. [Casa, cocina y movimiento](#casa-cocina-y-movimiento)
16. [Comandos](#comandos)
17. [Configuración](#configuración)
18. [Compatibilidad con otros mods](#compatibilidad-con-otros-mods)
19. [Compilar desde el código](#compilar-desde-el-código)

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

- Todo el contenido está en **8 pestañas del creativo**: Feria y atracciones, Sala recreativa, Juegos de mesa, Armas y combate, Sala de juegos, Pociones y efectos, Bromas y Varios.
- Las teclas propias están en **Opciones → Controles → Miscelánea** y se pueden cambiar todas. Si algo no responde, mira que su tecla no choque con otra.
- Cada objeto explica en su descripción cómo se usa.
- Los marcadores de récords y los rankings se guardan en el servidor. Usa `/miscelanea ranking` para verlos.

---

## El móvil

Un smartphone de verdad dentro del juego. Se abre con **clic derecho** o con su tecla (**B** por defecto) y la carcasa se tiñe con tintes.

| App | Qué hace |
|---|---|
| **Chats** | Mensajes privados y de grupo, con fotos y notas de voz (mantén pulsado el micro) |
| **Teléfono** | Llamadas y videollamadas con Simple Voice Chat, a cualquier distancia |
| **Agenda** | Contactos con número propio y si están conectados |
| **Cámara y galería** | Fotos y selfies en vertical o en horizontal (botón del móvil tumbado o tecla R: el móvil gira en la pantalla). Las fotos apaisadas se ven giradas a pantalla completa. Se guardan en el servidor |
| **Minegram** | La red social del servidor: publicaciones con pie de foto, me gusta (doble clic), comentarios, seguidores, perfiles y notificaciones |
| **Ubicación** | Compartir dónde estás en tiempo real durante 15, 30 o 60 minutos |
| **Mapa y GPS** | Mapa de lo explorado, puntos guardados, amigos que comparten ubicación y una flecha de navegación en el HUD |
| **Banco** | Saldo, Bizum, enviar objetos e historial (ver abajo) |
| **Reloj** | Hora y alarma |
| **Linterna** | Ilumina de verdad con luces invisibles que se quitan solas |
| **Ajustes** | Tono de llamada y otras opciones |

El móvil no tiene batería: funciona siempre. En la pantalla de inicio hay una tarjeta con la hora, el día, la luna y el tiempo que hace. Durante una llamada puedes salir a otras apps; la pastilla verde de arriba te devuelve a la llamada.

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
- Si alguien está jugando, la partida se ve en la pantalla **en tiempo real** para los que miran.
- **Bolera**, la típica de salón recreativo: la plataforma con los diez bolos y su marcador, la bola y los tramos de **pista de bolos** (la misma madera con canaletas negras) para alargar la calle por delante. Receta de la pista: tinte negro, tablones y tinte negro (da 4).
- El **marcador retro** y el **tablón de récords** guardan las mejores puntuaciones.
- Las **fichas** se compran en el cajero automático con el saldo del banco.

## Feria

- **Atracciones** en las que se puede montar: noria, tiovivo, pulpo, coches de choque, vagoneta de la bruja y caja de sustos.
- **Puestos:** caseta de tiro con escopeta de corcho, pesca de patitos, tómbola, máquina de gancho, tragaperras, gachapón, canastas, boxeo, martillo de fuerza, dardos, petanca, torre de bloques y churrería.
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
- **Sigilo:** Fantasma *(creativo)*, Ecolocalización y Niebla viviente.
- **Rol** *(creativo)*:
  - Esencia y Veneno de Dream. El veneno mata al instante, con su propia animación.
  - Anillo de medianoche: mantén clic derecho para ir a The Midnight.
  - Mordisco venenoso: un agarre con veneno sostenido y un minijuego para escapar.
  - Muestra de glitch.
  - Estática (ver la sección anterior).

---

## Bromas

Inofensivas pero molestas. Mejor acordarlas con el grupo antes de usarlas.

- **Varita uwu:** el chat de la víctima escribe "uwu".
- **Poción de controles cambiados.**
- **Espejo del revés:** pone la pantalla al revés.
- **Cartel de «PATÉAME».**
- **Duende travieso:** te revuelve el inventario.
- **Rueda de Catalina.**

## Sala de juegos

Trampas y aparatos "de película de miedo", en versión graciosa: cepos, grilletes, jaulas, la caja de agua, el péndulo, la silla de inmersión, la silla de interrogatorio y más. Muchos tienen su minijuego para escapar (ganzúa, alicates, sierra...), y al que está en el cepo se le pueden tirar tomates y huevos podridos. Es para rol y hay que acordarlo con quien participe. El administrador puede desactivar partes en la configuración.

---

## Colección, pesca y tesoros

- **Red cazabichos y caña de pescar:** mariposas, luciérnagas, escorpiones, celacantos y más. Cada pez tiene su tamaño en centímetros.
- **Bichopedia:** lo que has encontrado y dónde vive cada criatura.
- **Vitrina del museo, acuario y terrario:** para exponer la colección.
- **Detector de metales:** fósiles, mosquito en ámbar, tesoros y cápsulas del tiempo con notas.

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
- **Incapacitated**
- **Ice and Fire**
- **Corail Tombstone**
- **OmegaChips**

Además:

- El daño de las armas y de la estática atraviesa en parte la armadura y la Protección, para que siga contando contra equipo muy fuerte.
- Los jefes (dragón, wither y la etiqueta `forge:bosses`) reciben menos efectos; se ajusta en la configuración.
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

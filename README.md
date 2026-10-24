# Miscelánea (Forge 1.20.1)

Un mod de cosas que no tienen nada que ver entre sí, pero que molan.

## Contenido actual

### Efecto: Cupido 💘
- Si dos entidades (jugadores **o mobs**) tienen Cupido y están a ≤ 32 bloques, se atraen solas hacia la otra.
- Cuanto más lejos, más fuerte el tirón. Cualquier movimiento que los aleje se anula: no se pueden separar.
- Sin daño de caída mientras tira, saltan escalones solos y sueltan corazones.
- Con 3+ entidades con Cupido, cada una va hacia la más cercana.

### Efecto: Enemigo 💔
- Si dos entidades con Enemigo están a ≤ 5 bloques, ambas reciben daño cada segundo.
- Nivel I: medio corazón/s. Nivel II: 1,5 corazones/s. Pegados (≤ 2 bloques): daño doble.
- Ignora armadura, funciona con el PvP desactivado y los mobs afectados se pelean entre ellos.
- Mensaje de muerte propio: "X fue consumido por su odio hacia Y".

Entre dos afectados se ve un **lazo**: una cinta rosa que cuelga y late (Cupido) o un rayo rojo en zigzag que chisporrotea más cuanto más cerca están (Enemigo).

### Pociones y recetas de alquimia
| Poción | Receta | Duración |
|---|---|---|
| Cupido | Poción rara + Pétalos rosas | 3:00 |
| Cupido (larga) | Cupido + Redstone | 8:00 |
| Cupido II | Cupido + Polvo de piedra luminosa | 1:30 |
| Enemistad | Cupido + Ojo de araña fermentado | 1:00 |
| Enemistad (larga) | Cupido largo + Ojo fermentado, o Enemistad + Redstone | 2:00 |
| Enemistad II | Cupido II + Ojo fermentado, o Enemistad + Piedra luminosa | 0:30 |

Pólvora (arrojadiza), aliento de dragón (persistente) y flechas con efecto funcionan como en vanilla.

Comandos de prueba:
```
/effect give @a miscelanea:cupido 60
/effect give @e[type=zombie] miscelanea:enemigo 60
```

### Aspirafantasmas 3000 👻 (GeckoLib)
Necesitas **la mochila puesta** (hueco del pecho) y **la boquilla en la mano**. Las dos tienen modelo 3D animado y texturas pintadas; una **manguera 3D** une la mochila con la boquilla. En tercera persona se sujeta **a dos manos** (gatillo detrás, empuñadura delante) y apunta hacia donde miras; en primera persona apunta al frente. La succión usa el humo de las fogatas.

**Clic derecho (mantener): aspirar**
- Absorbe en un cono de 8 bloques los ítems y la experiencia y los mete en tu inventario.
- Atrae a mobs y jugadores. Los grandes y los jefes se resisten más. Si llegan a la boca, la aspiradora les hace **daño** (1,5 corazones cada medio segundo).
- **Captura:** los mobs pequeños (`data/miscelanea/tags/entity_types/capturable.json`) se resisten cuando están cerca. Gira la cámara **al lado contrario** de su tirón hasta vaciar la barra. Se guardan hasta 3 en el depósito.
- **R** suelta la última captura (se cambia en Controles).

**Shift + clic derecho sin encantamiento:** expulsa aire (empuja cosas y gente y apaga el fuego).

**Shift + clic derecho: según el encantamiento** (solo uno por boquilla, se ponen en mesa o con libro)
| Encantamiento | Uso |
|---|---|
| Destello | Mantén 1 s y suelta. Ciega y aturde a quien esté delante y te esté mirando. Los jugadores ven un flashbang blanco con pitido. Los mobs aturdidos se capturan más fácil. |
| Desatascador | Dispara una ventosa atada con una cuerda floja. Si da a alguien, **aspira (clic normal)**: la cuerda se va tensando cuanto más se acerca hasta que queda **agarrado** delante de la boquilla. Mientras mantengas el clic sigue agarrado (calienta poco, aguanta ~25 s) y la barra de **fuerza** se carga (hasta x1,8 en 3 s). Al soltar: mirando abajo lo **estampas**, mirando arriba lo mandas **a las nubes** y al frente lo **lanzas**. Si al soltar das un **latigazo de cámara** (giras el ratón rápido), sale disparado hasta x7 más fuerte, y con carga completa y latigazo máximo puede volar más de 100 bloques. El daño del impacto también sube. Si da en un bloque, aspirar te lleva hasta él como un gancho. |
| Soplido | Versión mejorada del soplido: más fuerza. Mantén para empujar a todo, apagar fuego y **devolver proyectiles**. Con un toque rápido dispara un mob capturado como **bala de cañón**. |
| Turbo | Impulso hacia el lado contrario de donde apunta la boquilla: si apuntas al suelo, sales volando. |
| Alquimista | Con este encantamiento, el **clic derecho** aspira como siempre y además **absorbe**: nubes de pociones persistentes (apuntando a cualquier parte, también aliento de dragón y nubes de otros mods), **pociones lanzadas** en pleno vuelo y **un efecto por segundo** de quien tengas delante. Los efectos **negativos** van al tanque y con **Shift + clic derecho** se sueltan en un **chorro continuo, como un lanzallamas**: lo que toca el chorro recibe los efectos y en el suelo quedan charquitos de nube (el tanque lleno dura unos 5 s). Los **positivos** se guardan en la **reserva** de la mochila y se activan solos, como un faro, solo mientras hacen falta. Velocidad y salto mientras corres, regeneración si te falta vida, curación instantánea por debajo del 40 %, resistencia al fuego si te quemas, respiración acuática si te ahogas, visión nocturna a oscuras, caída lenta al caer, fuerza al pelear, prisa al picar, invisibilidad agachado, resistencia y absorción al recibir daño, saturación con hambre. La reserva solo se gasta mientras está activa. |

**Ventana de la mochila (tecla B, con la mochila puesta):** tres tarros con los bichos capturados flotando dentro (botón para soltar cada uno), el tubo del tanque con su color y sus efectos (rociar o tirar), y la **Reserva** con una barra de tiempo por efecto y botones para **pausar** su uso automático, **tomarlo todo ya** o **tirarlo**. Sin texto: al pasar el ratón por encima sale la descripción.

**Sensaciones:** zumbido continuo que sube de tono con el calor (más agudo al soplar, más grave con el lanzallamas), la vista tiembla y se mete hacia dentro al aspirar o se abre al soplar, y la boca de la boquilla se pone **al rojo** a partir del 30 % de calor. Boquilla y mochila tienen animaciones distintas para aspirar, soplar, lanzallamas y cargar el destello. Al capturar, el tira y afloja se ve con una **barra y una flecha** (sin texto) y el bicho te arrastra la cámara hacia su lado.

**Sobrecalentamiento:** la barra de la boquilla pasa de verde a rojo. Al llenarse se bloquea echando humo hasta enfriarse del todo (unos 6 s).

**Recetas**
- Mochila: `hierro, cristal, hierro / bloque de redstone, tolva, bloque de redstone / hierro, diamante, hierro`
- Boquilla (en diagonal): tolva arriba a la derecha, diamante en el centro, bloque de redstone a su derecha y hierro abajo a la izquierda.

Los modelos son `.geo.json` de Blockbench (`assets/miscelanea/geo/item/...`) y se pueden abrir con el plugin de GeckoLib para retocarlos. Si la boquilla sale mal colocada en la mano, ajusta `display` en `models/item/aspirafantasmas.json`.

### Porra eléctrica y generador ⚡ (GeckoLib + playerAnimator)
Inspirado en el carcelero de The Outlast Trials, con diseño propio. El **generador** (carcasa militar caqui con pegatina de alto voltaje, bastidor tubular con asa, cilindro con aletas, depósito de gasolina con mirilla, arranque de tirón, silenciador perforado con escape que echa humo, alternador de cobre y panel de control) va en el hueco del pecho y la **porra** en la mano. Un cable 3D une la caja eléctrica con la porra, y la punta brilla y chisporrotea (delata dónde estás).

- **Golpe normal:** chispazo extra (+1,5 corazones) y **bloquea los ítems de uso** del golpeado 5 s (escudo, arco, aspiradora…).
- **Agarrar:** clic derecho a alguien cercano (3 bloques), o automáticamente si alguien **choca contigo corriendo**. Funciona con jugadores y mobs de tamaño normal.
- **Durante el agarre:**
  - La víctima queda levantada delante de ti, sin poder moverse, atacar ni usar nada.
  - Su **cámara** queda obligada a mirarte y tiembla con cada descarga.
  - Recibe una **descarga** cada 1,5 s (1,5 corazones y 10 s de ítems bloqueados).
- **Forcejeo:** la víctima pulsa repetidamente las teclas de movimiento o salto para soltarse (barra azul). Cada descarga le quita progreso. Si se suelta, tú quedas aturdido.
- **Rescate:** si otro golpea al agresor, suelta a la víctima y queda aturdido.
- **Soltar:** clic derecho otra vez (empujón). El agarre dura como mucho 7 s.
- **Ejecución:** si la víctima tiene ≤30 % de vida, el clic derecho la ejecuta. Levantas la porra en alto, se carga de chispas y la hundes.
- **Animaciones** en primera y tercera persona, calculadas para que la mano llegue al cuello y la porra apunte a la víctima. Entran y salen suavemente. La víctima ve sus propias manos forcejeando.
- **Ataque normal:** estocada con zancada (brazo atrás, torso girado, y pincha recto hacia donde miras).
- **Arco eléctrico:** cada golpe con carga y cada descarga del agarre lanzan un arco visible de la punta de la porra a la víctima.
- **Generador:** cada descarga gasta carga (barra en el ítem). Se recarga sola tras 3 s sin usarla y muy rápido bajo una tormenta.

**Recetas**
- Porra (en diagonal): lingote de cobre arriba a la derecha, pararrayos en el centro y lingote de hierro abajo a la izquierda.
- Generador: `cobre, cuero, cobre / hierro, bloque de redstone, hierro / hierro, hierro, hierro`

### Medigun 💉 (GeckoLib, inspirada en TF2)
Pistola blanca con emisor de tres púas y medidor de carga, y **mochila médica** con dos depósitos y un vial que se llena con la ÜberCarga. Las une una manguera 3D. Se lleva a dos manos.

- **Curar:** mantén clic derecho apuntando a un jugador (hasta 10 bloques). El rayo se queda enganchado aunque dejes de apuntarle, mientras esté a menos de 14 bloques y a la vista (aguanta 1 s detrás de una esquina).
- **Curación:** 1 corazón/s, que sube hasta 3 corazones/s si el paciente lleva 3 s sin recibir daño. Con la vida llena le **sobrecura** hasta 4 corazones dorados extra, que se pierden poco a poco.
- **ÜberCarga:** se llena curando (40 s con heridos, el doble si están sanos). Con la barra llena, **Shift + clic derecho**: tú y tu paciente sois **invulnerables 8 s** (salvo el vacío y /kill), con una capa brillante del color del rayo y los golpes rebotan.
- **El rayo** es una cinta ondulante con dos hilos en espiral que sale de la boca de la pistola y se dobla hacia donde miras. Zumba mientras cura.
- **Color:** medigun + cualquier tinte en la mesa de crafteo. Cambia el rayo, la capa de ÜberCarga y las luces de la pistola y la mochila.
- **HUD sin texto:** barra de ÜberCarga abajo a la derecha (parpadea cuando está lista) y la vida del paciente bajo la mira.

**Recetas**
- Medigun: `_, oro, lágrima de ghast / hierro, diamante, oro / hierro, _, _`
- Mochila médica: `cuero, _, cuero / cristal, manzana dorada, cristal / hierro, soporte para pociones, hierro`

## Compilar
Cada vez que se sube algo a GitHub, **GitHub Actions** compila el mod y publica el `.jar` en **Releases**.

En local requiere JDK 17 e internet (descarga Forge y GeckoLib 4.8.4 la primera vez). Para jugar, **GeckoLib** y **playerAnimator** (1.0.2-rc1+1.20) tienen que estar también en la carpeta `mods`:
```
./gradlew build        (Windows: gradlew.bat build)
```
El .jar sale en `build/libs/miscelanea-1.0.0.jar` → cópialo a `.minecraft/mods`.

Para probar directamente: `./gradlew runClient`.

## Ajustes rápidos
- Radio de Cupido / fuerza del tirón: `effect/CupidoEffect.java` (`RADIUS`, `MAX_PULL`).
- Radio y daño de Enemigo: `effect/EnemigoEffect.java` (`RADIUS`, `CLOSE_RADIUS`, `DAMAGE_INTERVAL`).

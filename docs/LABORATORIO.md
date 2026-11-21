# Laboratorio de experimentos (zona de torturas): diseño (borrador)

Rama de trabajo: `laboratorio` (sale de `novedades`, la 1.3.0). Enlaza con el archivo del Laboratorio STAG (el sedante de suavis).

## Decisiones ya tomadas (con el dueño del mod)
- **Enfoque:** estaciones de experimento + un **cuaderno de resultados** que apunta lo que pasa con cada sujeto.
- **Primeras estaciones:** centrífuga, cámara de temperatura, laberinto de la rata y tanque de privación sensorial.
- **Sujetos:** solo jugadores (cautivos, con su QTE para escapar). Nada de mobs.
- **Tono:** las tres cosas. Cada estación tiene tres niveles: **suave** (cómico, tipo dibujos), **intenso** (inquietante, de laboratorio sospechoso) y **extremo** (muerte cinemática, como la doncella o la silla eléctrica).

## Piezas que ya existen y se reutilizan
- `tortura.aparatos`: bloque + bloque-entidad + `AsientoAparato` (el sujeto va sentado), `Aparatos` (preparar al cautivo, daño propio, heridas), `PermisoAparatos` (quién puede usarlos).
- `tortura.qte`: QTE (alternar, círculo, secuencia, esquiva, barra...) para que el sujeto resista o escape.
- `tortura.muertes`: cinemáticas de muerte (el nivel extremo añade tipos nuevos).
- `tortura.heridas`, `tortura.efectos` (miedo, fantasma), pociones de tamaño (enano para el laberinto), efectos de voz, `Lazo`/`Cautivos`.

## El cuaderno de laboratorio
- Un objeto (`Cuaderno de laboratorio`) por científico. Al terminar un experimento, anota una ficha: estación, sujeto, nivel, fecha y **lecturas** (pulso, miedo, resistencia, tolerancia con nota de A a F).
- Pantalla con la lista de fichas y el detalle (con el mismo estilo que el móvil). Se guarda en el propio objeto (se puede perder y prestar) y el sujeto también recibe copia de su ficha.
- Logros por descubrir (primera centrífuga a tope, sujeto que aguanta el nivel extremo, todas las estaciones...).

## Las estaciones
1. **Centrífuga:** silla en un brazo que gira. Una palanca fija las vueltas por minuto. La cámara del sujeto se tuerce y se estira; sube el mareo y hay un QTE de alternar para aguantar. Lecturas: g máximas y tiempo aguantado. Suave: se marea. Intenso: se desmaya (como el sedante). Extremo: sale disparado.
2. **Cámara de temperatura:** cabina de cristal con un dial de -40 a +120. Efectos de escarcha o de calor en pantalla; daño solo en los extremos. Lecturas: temperatura tolerada. Extremo: bloque de hielo o tostado.
3. **Laberinto de la rata:** el sujeto bebe la poción de enano y entra a un mini-laberinto de bloques con queso al final y baldosas con descarga suave. Lecturas: tiempo y fallos. El queso es el premio.
4. **Tanque de privación sensorial:** tanque oscuro: ceguera, silencio (también del chat de voz) y alucinaciones (el efecto fantasma y sonidos falsos). Lecturas: tiempo hasta alucinar y tiempo aguantado.

## Objetos del laboratorio
Todos con receta de supervivencia salvo los marcados como "creativo" (como el resto de la zona). Texturas a 32x32.

| Objeto | Para qué sirve | Estación |
|---|---|---|
| **Cuaderno de laboratorio** | Anota las fichas de cada experimento (ver arriba). | todas |
| **Pulsómetro** | Clic derecho a un cautivo: muestra su pulso y su miedo en la barra de acción. Es lo que alimenta las lecturas. | todas |
| **Termómetro de laboratorio** | Mide la temperatura del sujeto o de la cámara (con la barra de color que sube). | cámara de temperatura |
| **Cronómetro** | Pulsas para empezar y parar: apunta el tiempo aguantado en la ficha. | todas |
| **Portapapeles** | Pone número y nombre al sujeto ("Sujeto 7") que luego sale en el cuaderno. | todas |
| **Electrodos** | Dos pinzas que se colocan al sujeto: dan lecturas más finas y se usan en el test de reflejos. | centrífuga, laberinto |
| **Bata de laboratorio** | Pechera blanca (ropa). La lleva el científico: las salpicaduras de sueros le duran menos. | todas |
| **Gafas de seguridad** | Casco. Quitan el efecto de escarcha o de calor de la pantalla del científico cuando está cerca. | cámara de temperatura |
| **Tubo de ensayo y probeta** | Recipientes para guardar muestras. Se rellenan al acabar un experimento. | todas |
| **Muestra de miedo / de mareo / de frío** | Resultado de un experimento (sale del tubo). Es un ingrediente: sirve para las mezclas, los dardos y las pociones de miedo. | todas |
| **Suero sin etiquetar** | Frasco con un efecto al azar (tamaño, voz, helio, eco...). Se descubre usándolo y se apunta en el cuaderno. | mesa de mezclas |
| **Jeringuilla de suero** | Como la de sedante, pero se carga con un suero o una muestra. Pincha al sujeto. | todas |
| **Queso de laboratorio** | El premio del laberinto de la rata. Se puede comer (efecto absurdo al azar). | laberinto |
| **Mesa de mezclas** (bloque) | Pones dos sueros o muestras y sale un tercero. Resultados nuevos y a veces absurdos. | mezclas |
| **Decoración** (creativo): probeta, matraz burbujeante, microscopio, camilla, taquilla de laboratorio, cartel de biopeligro, placas de Petri | Para montar la sala. | ambiente |

El bucle que une todo: **experimento -> muestra -> mezcla -> suero -> jeringuilla -> nuevo experimento.**

### Dónde entran en los hitos
- Hito 1 (cuaderno): cuaderno, pulsómetro, cronómetro y portapapeles.
- Hitos 2 a 5 (estaciones): termómetro, electrodos, queso y las muestras que da cada estación.
- Hito 6: bata, gafas, mesa de mezclas, sueros, jeringuilla de suero y decoración.

## Máquinas del laboratorio
Bloques con bloque-entidad, con su interfaz (un menú sencillo) y salida de comparador para redstone. **Sin sistema de energía:** funcionan a mano o con redstone, como el resto de máquinas del mod. Todas son solo de creativo salvo que se diga otra cosa.

| Máquina | Qué hace | Entra -> sale |
|---|---|---|
| **Camilla de experimentos** | La base común: el sujeto va tumbado y atado, con electrodos y pulsómetro puestos. Las estaciones la usan para dar sus lecturas. | cautivo -> lecturas |
| **Panel de control** | La consola central: palancas y diales para manejar las estaciones a distancia (nivel suave, intenso o extremo, con llave para el extremo) y botón de parada de emergencia. | redstone / mano |
| **Monitor de constantes** | Pantalla con el pulso y el miedo del sujeto en vivo (una gráfica que sube y baja). La ven los demás desde fuera. | lecturas -> gráfica |
| **Espejo unidireccional** | Cristal que de un lado es espejo y del otro es ventana: la sala de observación. | decorativo |
| **Intercomunicador** | Altavoz y micro: el científico habla con el sujeto aislado (también por el chat de voz) y ve su nivel de ruido. | voz |
| **Destilador térmico** | Cocina suavis seco para sacar el **sedante clase M**, como dice el archivo del Laboratorio STAG: dos fases y se calienta con fuego debajo. | suavis seco -> sedante |
| **Centrifugadora de muestras** | La pequeña: separa una muestra en sus componentes (miedo, mareo, frío) en tubos distintos. | muestra -> 2 o 3 tubos |
| **Incubadora** | Hace crecer cultivos con el tiempo: una muestra se convierte en suero tras unos minutos. | muestra -> suero |
| **Analizador** | Mete una muestra o un suero: lo analiza y dice qué es y qué hace (lo apunta en el cuaderno). | muestra -> ficha |
| **Impresora de informes** | Imprime la ficha del cuaderno en papel (un informe que se puede dejar o colgar en un marco). | cuaderno -> papel |
| **Dispensador de sueros** | Un expendedor del laboratorio: cambia fichas de recreativa por un suero sin etiquetar. | fichas -> suero |

### Dónde entran en los hitos
- Hito 2 (centrífuga): camilla de experimentos y panel de control.
- Hito 3 (cámara de temperatura): monitor de constantes.
- Hito 4 (tanque): espejo unidireccional e intercomunicador.
- Hito 6: destilador térmico (con la receta del archivo STAG), centrifugadora de muestras, incubadora, analizador, impresora y dispensador.

## Sistema de cámaras (hito 7)
Cámaras y televisiones **a tiempo real, a unos 10 fps**, también fuera de la zona de torturas. Pensado para servidores.

### Cómo llega la señal (la idea clave)
El cliente solo tiene cargado lo que queda dentro de su distancia de visión, así que **solo un jugador cerca de la cámara puede dibujar lo que ve**. Por eso la señal pasa por el servidor:
1. Cuando alguien mira una tele que está en un canal, el servidor sabe qué cámara quiere ver.
2. El servidor elige un **transmisor**: un jugador que tenga el chunk de esa cámara cargado (el más cercano, y con preferencia al que menos carga lleve ya).
3. El cliente del transmisor dibuja el mundo desde la posición de la cámara en una textura pequeña (128x72) cada seis fotogramas, la comprime en JPEG (como ya hacen las videollamadas del móvil) y la manda al servidor.
4. El servidor la reenvía a todos los que miran esa cámara, estén donde estén (en la misma dimensión o en otra), y sus teles la dibujan con estática, ruido y sello de fecha y hora.
5. **Si nadie tiene el chunk de la cámara cargado, la tele muestra "SIN SEÑAL".** El servidor no puede inventarse lo que no se está dibujando en ningún cliente. (Un jugador cerca, un familiar con chunk forzado no sirve: hace falta un cliente que lo dibuje.)

### Reglas para que no pese
- Una cámara solo transmite si alguien la está mirando; si deja de haber espectadores, el servidor le dice al transmisor que pare.
- Máximo de cámaras activas por servidor (configurable) y de cámaras por transmisor.
- Fotogramas de unos 3 a 6 KB: unos 30 a 60 KB/s por cámara y espectador. El servidor solo reenvía, no decodifica.
- Si el transmisor se aleja, el servidor pasa la transmisión a otro jugador cercano sin que se note (un parpadeo de estática).

### Bloques y objetos
- **Cámara de vigilancia:** se cuelga de pared o techo, gira con el ratón al colocarla y tiene un nombre y un código de canal.
- **Televisión** (varios tamaños): también se cuelga de la pared o del techo. Clic derecho para elegir el canal.
- **Panel de canales** (conmutador): una tele puede ir a un canal fijo o rotar entre varios cada pocos segundos, como en FNAF.
- **Enlazador:** objeto para unir una cámara a un canal.
- Encaja con el monitor de constantes y el espejo unidireccional del laboratorio.

### Riesgos conocidos
- Los shaders (Iris, Oculus) pueden estropear el dibujado extra: primero sin shaders, luego con ellos aparte.
- El rendimiento del transmisor: cada cámara es un dibujado extra del mundo en su ordenador (se limita a pocas cámaras y a baja resolución).
- No lo he medido todavía: el primer paso del hito 7 es una cámara y una tele fijas para ver cuánto cuesta.

## Hitos
1. Modelo de datos y **cuaderno** (objeto, ficha, pantalla), sin estaciones todavía. Con prueba.
2. **Centrífuga** completa (con sus tres niveles y lecturas).
3. **Cámara de temperatura.**
4. **Tanque de privación.**
5. **Laberinto de la rata.**
6. Pulido: logros, sonidos, textos, README y CHANGELOG, y pruebas con capturas.
7. **Sistema de cámaras y televisiones** (ver arriba): primero una cámara y una tele fijas, luego la señal por el servidor, colgar y girar, y los canales.

## Reglas
- Solo se puede meter a quien ya es cautivo, y siempre con la salida del QTE; el nivel extremo pide confirmación del científico.
- Configuración `[laboratorio]`: activar cada estación, nivel máximo permitido (para servidores que no quieran el extremo) y daño.
- Los aparatos nuevos son bloques solo de creativo (irrompibles en supervivencia), como el resto de la zona.

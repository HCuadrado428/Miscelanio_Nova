# Mario Party para Minecraft: diseño (borrador)

Nombre provisional: **Fiesta** (por decidir). Rama de trabajo: `mario-party` (sale de `novedades`; a qué versión va, se decide después).

## Decisiones ya tomadas (con el dueño del mod)
- **Formato:** tablero con casillas + un minijuego al final de cada ronda. Gana quien tenga más estrellas tras N rondas (monedas de desempate).
- **Dónde:** una **dimensión propia** (`miscelanea:party`), pensada para esto: vacía, plana, sin ciclo de día y noche, sin mobs, luz fija.
- **Minijuegos de la primera entrega:** carrera de obstáculos, suelo que desaparece, puntería, memoria y reflejos.
- **Jugadores:** de 2 a 4 jugadores reales. Sin bots.

## Flujo de una partida
1. **Lobby:** una plataforma con un cartel de partida. Los jugadores se apuntan (bloque "Mesa de la fiesta" o `/fiesta`).
2. Al empezar, se guarda el inventario, la posición y la vida de cada jugador (se devuelven al terminar o al desconectarse) y se les lleva a la dimensión.
3. **Ronda:** por turnos, cada jugador tira el dado (1 a 10), avanza casilla a casilla y se resuelve la casilla.
4. **Minijuego:** al acabar los turnos de la ronda, todos van a la arena del minijuego sorteado. El orden de llegada o la puntuación reparte monedas (5/3/1/0).
5. Tras la última ronda: recuento de estrellas, pantalla de resultados, y vuelta al mundo.

## Tablero
- Un **grafo de casillas** (con bifurcaciones y un bucle central), dibujado con bloques de colores sobre plataformas flotantes.
- Casillas: azul (+3 monedas), roja (-3), verde (evento aleatorio), amarilla (objeto), **estrella** (cuesta 20 monedas, y cambia de sitio al comprarla), y casilla de duelo.
- El jugador **anda de verdad** por las casillas (se mueve por el tablero con saltos suaves; inmovilizado fuera de su turno).
- Dado: un bloque que se golpea, con una animación de cara que cambia.

## Piezas técnicas
- `fiesta/` (paquete nuevo): `FiestaDimension`, `FiestaPartida` (estado: jugadores, turno, ronda, monedas, estrellas, casilla de cada uno), `Tablero` (el grafo), `Casilla`, `Dado`, `Fiesta` (eventos del servidor).
- Se reutiliza lo que ya hay: turnos y partidas (`juegos`, `mesas.Turnos`), victorias y ranking, fichas.
- `fiesta/minijuegos/`: una interfaz `Minijuego` (`preparar`, `tick`, `terminado`, `resultados`) y cuatro implementaciones. Cada arena está en coordenadas fijas de la dimensión y se construye por código al empezar y se limpia al acabar.
- Red: solo lo imprescindible (marcador y turno en pantalla), validado en el servidor.
- Configuración (`[fiesta]`): rondas, monedas iniciales, precio de la estrella, minijuegos activos.
- Pruebas: pasos nuevos en `PruebaCI` (crear partida con jugador de prueba, tirar el dado, recorrer una casilla, un minijuego de cada) con capturas.

## Hitos
1. **Dimensión vacía y lobby:** entrar, salir y restaurar al jugador. Con prueba.
2. **Tablero y dado:** el grafo, andar por casillas y resolver monedas. Con capturas.
3. **Ronda completa con dos jugadores:** turnos, marcador y fin de partida.
4. **Primer minijuego (suelo que desaparece)** conectado al final de ronda.
5. **Los otros tres minijuegos.**
6. **Pulido:** sonidos, marcador, estrellas, resultados, textos en español e inglés, README y CHANGELOG.

## Por decidir
- Nombre definitivo.
- Versión de destino (¿1.3.0 o 2.0.0?).

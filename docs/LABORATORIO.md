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

## Hitos
1. Modelo de datos y **cuaderno** (objeto, ficha, pantalla), sin estaciones todavía. Con prueba.
2. **Centrífuga** completa (con sus tres niveles y lecturas).
3. **Cámara de temperatura.**
4. **Tanque de privación.**
5. **Laberinto de la rata.**
6. Pulido: logros, sonidos, textos, README y CHANGELOG, y pruebas con capturas.

## Reglas
- Solo se puede meter a quien ya es cautivo, y siempre con la salida del QTE; el nivel extremo pide confirmación del científico.
- Configuración `[laboratorio]`: activar cada estación, nivel máximo permitido (para servidores que no quieran el extremo) y daño.
- Los aparatos nuevos son bloques solo de creativo (irrompibles en supervivencia), como el resto de la zona.

package com.miscelanea.baile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miscelanea.Miscelanea;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canciones de la pista de baile: se leen del propio .jar (las mismas en el servidor y en el cliente) y cada una trae
 * su música, sus flechas en tres dificultades y su coreografía.
 *
 * <h2>Dónde van</h2>
 * <ul>
 *   <li>Índice: {@code assets/miscelanea/baile/canciones.json} → {@code {"canciones": ["miscelanea:ri_verbena", ...]}}.
 *       Opcional, para las canciones que añade el dueño del servidor con {@code importar_cancion.py}:
 *       {@code assets/miscelanea_canciones/baile/canciones.json} (esa carpeta no se sube al repositorio).</li>
 *   <li>Cada canción {@code espacio:id} → {@code assets/<espacio>/baile/canciones/<id>.json}.</li>
 *   <li>El sonido ({@code "sonido": "espacio:nombre"}) va en {@code assets/<espacio>/sounds.json} (con
 *       {@code "stream": true}) y el .ogg MONO en {@code assets/<espacio>/sounds/}. No hace falta registrarlo en Java.</li>
 * </ul>
 *
 * <h2>Formato de una canción</h2>
 * <pre>
 * {
 *   "titulo": "Boogie Wonderland",          // texto tal cual (o "titulo_clave": clave de idioma)
 *   "sonido": "miscelanea_canciones:boogie_wonderland",
 *   "bpm": 132.0,                           // pulsos por minuto (constante)
 *   "offset": 0.42,                         // segundos hasta el pulso 0 (el primer tiempo fuerte)
 *   "mapa": [[0, 0.42], [4, 2.25], ...],    // opcional: [pulso, segundo] (uno por compás) si el tempo no es fijo
 *                                           // (grupos en directo); entre medias se interpola y manda sobre bpm/offset
 *   "duracion": 288.3,                      // segundos que dura la canción
 *   "secciones": [                          // en pulsos; nombre: intro, verso, estribillo, puente, final, outro
 *     {"nombre": "intro", "desde": 0, "hasta": 16,
 *      "pasos": ["groove", {"paso": "disco", "pulsos": 8, "vel": 0.5, "espejo": true}]},
 *     ...
 *   ],
 *   "notas": {                              // [pulso, carril] = flecha; [pulso, carril, largo] = flecha que se mantiene
 *     "facil":   [[16, 2], [18, 0, 2.0], ...],   // carriles: 0 izquierda, 1 abajo, 2 arriba, 3 derecha
 *     "normal":  [...],                          // dos flechas en el mismo pulso = salto (las dos a la vez)
 *     "dificil": [...]
 *   }
 * }
 * </pre>
 * Los pasos de baile de "pasos" son los de {@code assets/miscelanea/baile/pasos.json}; en cada sección se encadenan
 * uno tras otro (y vuelven a empezar) hasta llenarla. "pulsos" = cuánto dura ese paso en la canción (si no, lo que dura
 * el paso una vez a su velocidad), "vel" = velocidad (0.5 = a medio tiempo), "espejo" = con el otro lado del cuerpo.
 */
public final class CancionesBaile {
    private static final Logger LOG = com.mojang.logging.LogUtils.getLogger();
    public static final String[] DIFICULTADES = {"facil", "normal", "dificil"};
    /** Pasos de juego por segundo (las flechas se cuentan en sesentavos de segundo). */
    public static final int PPS = 60;

    private CancionesBaile() {}

    /** Una flecha: en el paso t (1/60 s) y carril; fin = paso en el que se suelta si hay que mantenerla, o -1. */
    public static final class Nota {
        public final int t, carril, fin;

        Nota(int t, int carril, int fin) {
            this.t = t;
            this.carril = carril;
            this.fin = fin;
        }

        public boolean mantener() {
            return fin > t;
        }
    }

    /** Un paso de baile dentro de una sección, ya colocado en la canción: de "desde" a "hasta" (en pulsos). */
    public static final class Tramo {
        public final String paso, seccion;
        public final float desde, hasta, vel;
        public final boolean espejo;

        Tramo(String paso, String seccion, float desde, float hasta, float vel, boolean espejo) {
            this.paso = paso;
            this.seccion = seccion;
            this.desde = desde;
            this.hasta = hasta;
            this.vel = vel;
            this.espejo = espejo;
        }
    }

    public static final class Seccion {
        public final String nombre;
        public final float desde, hasta;

        Seccion(String nombre, float desde, float hasta) {
            this.nombre = nombre;
            this.desde = desde;
            this.hasta = hasta;
        }
    }

    public static final class Cancion {
        public final String id, sonido, titulo, tituloClave;
        public final float bpm, offset, duracion;
        public final Nota[][] notas = new Nota[3][];
        public final List<Seccion> secciones = new ArrayList<>();
        public final List<Tramo> tramos = new ArrayList<>();
        /** Mapa de tempo (pulso → segundo), o null si el tempo es fijo. */
        private float[] mapaP, mapaS;

        Cancion(String id, JsonObject o) {
            this.id = id;
            sonido = o.has("sonido") ? o.get("sonido").getAsString() : id;
            titulo = o.has("titulo") ? o.get("titulo").getAsString() : id;
            tituloClave = o.has("titulo_clave") ? o.get("titulo_clave").getAsString() : null;
            bpm = Math.max(30F, o.get("bpm").getAsFloat());
            offset = o.has("offset") ? o.get("offset").getAsFloat() : 0F;
            duracion = o.get("duracion").getAsFloat();
            if (o.has("mapa") && o.getAsJsonArray("mapa").size() >= 2) {
                JsonArray m = o.getAsJsonArray("mapa");
                mapaP = new float[m.size()];
                mapaS = new float[m.size()];
                for (int i = 0; i < m.size(); i++) {
                    mapaP[i] = m.get(i).getAsJsonArray().get(0).getAsFloat();
                    mapaS[i] = m.get(i).getAsJsonArray().get(1).getAsFloat();
                }
            }
            JsonObject ns = o.getAsJsonObject("notas");
            for (int d = 0; d < 3; d++) {
                List<Nota> l = new ArrayList<>();
                JsonArray a = ns != null && ns.has(DIFICULTADES[d]) ? ns.getAsJsonArray(DIFICULTADES[d]) : new JsonArray();
                for (JsonElement e : a) {
                    JsonArray n = e.getAsJsonArray();
                    float p = n.get(0).getAsFloat();
                    int c = n.get(1).getAsInt() & 3;
                    float largo = n.size() > 2 ? n.get(2).getAsFloat() : 0F;
                    int t = paso(p);
                    l.add(new Nota(t, c, largo > 0 ? Math.max(t + 6, paso(p + largo)) : -1));
                }
                l.sort((x, y) -> x.t != y.t ? Integer.compare(x.t, y.t) : Integer.compare(x.carril, y.carril));
                notas[d] = l.toArray(new Nota[0]);
            }
            if (o.has("secciones")) for (JsonElement e : o.getAsJsonArray("secciones")) {
                JsonObject s = e.getAsJsonObject();
                String nombre = s.has("nombre") ? s.get("nombre").getAsString() : "verso";
                float desde = s.get("desde").getAsFloat(), hasta = s.get("hasta").getAsFloat();
                if (hasta <= desde) continue;
                secciones.add(new Seccion(nombre, desde, hasta));
                JsonArray ps = s.has("pasos") ? s.getAsJsonArray("pasos") : new JsonArray();
                if (ps.isEmpty()) ps.add("groove");
                // los pasos de la sección uno tras otro, volviendo a empezar, hasta llenarla
                float t = desde;
                for (int i = 0; t < hasta - 1e-3F && i < 4096; i++) {
                    JsonElement pe = ps.get(i % ps.size());
                    String paso;
                    float pulsos = -1, vel = 1;
                    boolean espejo = false;
                    if (pe.isJsonObject()) {
                        JsonObject po = pe.getAsJsonObject();
                        paso = po.get("paso").getAsString();
                        if (po.has("pulsos")) pulsos = po.get("pulsos").getAsFloat();
                        if (po.has("vel")) vel = Math.max(0.05F, po.get("vel").getAsFloat());
                        if (po.has("espejo")) espejo = po.get("espejo").getAsBoolean();
                    } else paso = pe.getAsString();
                    if (pulsos <= 0) pulsos = PasosLargo.largo(paso) / vel;
                    float fin = Math.min(hasta, t + Math.max(0.25F, pulsos));
                    tramos.add(new Tramo(paso, nombre, t, fin, vel, espejo));
                    t = fin;
                }
            }
        }

        /** Segundo de la canción en el que cae un pulso. */
        public float segundo(float pulso) {
            if (mapaP == null) return offset + pulso * 60F / bpm;
            return interpolar(mapaP, mapaS, pulso);
        }

        /** Paso de juego (1/60 s) de un pulso de la canción. */
        public int paso(float pulso) {
            return Math.round(segundo(pulso) * PPS);
        }

        /** Pulso de la canción en el segundo s. */
        public float pulso(float s) {
            if (mapaP == null) return (s - offset) * bpm / 60F;
            return interpolar(mapaS, mapaP, s);
        }

        /** Interpolación por tramos (y se alarga con la pendiente del primer/último tramo). */
        private static float interpolar(float[] xs, float[] ys, float x) {
            int n = xs.length, i;
            if (x <= xs[0]) i = 1;
            else if (x >= xs[n - 1]) i = n - 1;
            else {
                int lo = 0, hi = n - 1;
                while (hi - lo > 1) {
                    int m = (lo + hi) >>> 1;
                    if (xs[m] <= x) lo = m;
                    else hi = m;
                }
                i = hi;
            }
            float dx = xs[i] - xs[i - 1];
            return ys[i - 1] + (ys[i] - ys[i - 1]) * (dx == 0 ? 0 : (x - xs[i - 1]) / dx);
        }

        public int duracionPasos() {
            return Math.round(duracion * PPS);
        }

        /** Sección en el pulso p (la primera antes de empezar, la última al acabar), o null si no tiene. */
        public Seccion seccion(float p) {
            if (secciones.isEmpty()) return null;
            for (Seccion s : secciones) if (p < s.hasta) return s;
            return secciones.get(secciones.size() - 1);
        }

        /** Índice del tramo de baile en el pulso p, o -1 si no hay ninguno (antes de empezar). */
        public int tramo(float p) {
            if (tramos.isEmpty() || p < tramos.get(0).desde) return -1;
            int lo = 0, hi = tramos.size() - 1;
            while (lo < hi) {
                int m = (lo + hi + 1) >>> 1;
                if (tramos.get(m).desde <= p) lo = m;
                else hi = m - 1;
            }
            return lo;
        }

        /**
         * Lo más que se puede haber sacado en la dificultad d hasta el paso t (todo perfecto con el combo subiendo:
         * 300 + 2·combo por cada juicio; una flecha que se mantiene da dos, al pisarla y al soltarla).
         */
        public int maxPuntos(int d, long t) {
            long n = 0;
            for (Nota x : notas[Math.max(0, Math.min(2, d))]) {
                if (x.t > t) break;
                n++;
                if (x.mantener() && x.fin <= t) n++;
            }
            return (int) Math.min(Integer.MAX_VALUE, 300 * n + n * (n - 1));
        }
    }

    /** Lo que dura cada paso de baile una vez (en pulsos); lo pone el cliente al cargar pasos.json (el servidor no lo usa). */
    public static final class PasosLargo {
        static final Map<String, Float> LARGO = new java.util.concurrent.ConcurrentHashMap<>();

        private PasosLargo() {}

        static float largo(String paso) {
            if (LARGO.isEmpty()) cargarLargos();
            return LARGO.getOrDefault(paso, 4F);
        }

        private static synchronized void cargarLargos() {
            if (!LARGO.isEmpty()) return;
            JsonObject o = leer("/assets/" + Miscelanea.MOD_ID + "/baile/pasos.json");
            if (o != null && o.has("pasos")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("pasos").entrySet())
                LARGO.put(e.getKey(), e.getValue().getAsJsonObject().get("pulsos").getAsFloat());
            if (LARGO.isEmpty()) LARGO.put("groove", 2F);
        }
    }

    private static volatile List<Cancion> LISTA;
    private static final Map<String, Cancion> POR_ID = new LinkedHashMap<>();

    /** Lee un JSON del .jar (o null si no está o está mal). */
    public static JsonObject leer(String ruta) {
        try (InputStream in = CancionesBaile.class.getResourceAsStream(ruta)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            LOG.warn("Pista de baile: no se puede leer {}: {}", ruta, e.toString());
            return null;
        }
    }

    public static List<Cancion> lista() {
        if (LISTA == null) cargar();
        return LISTA;
    }

    public static Cancion de(String id) {
        if (id == null) return null;
        lista();
        return POR_ID.get(id);
    }

    private static synchronized void cargar() {
        if (LISTA != null) return;
        List<Cancion> l = new ArrayList<>();
        for (String indice : new String[]{"/assets/" + Miscelanea.MOD_ID + "/baile/canciones.json", "/assets/miscelanea_canciones/baile/canciones.json"}) {
            JsonObject o = leer(indice);
            if (o == null || !o.has("canciones")) continue;
            for (JsonElement e : o.getAsJsonArray("canciones")) {
                String id = e.getAsString();
                if (POR_ID.containsKey(id)) continue;
                int dp = id.indexOf(':');
                String ns = dp < 0 ? Miscelanea.MOD_ID : id.substring(0, dp), nombre = dp < 0 ? id : id.substring(dp + 1);
                JsonObject c = leer("/assets/" + ns + "/baile/canciones/" + nombre + ".json");
                if (c == null) continue;
                try {
                    Cancion can = new Cancion(id, c);
                    l.add(can);
                    POR_ID.put(id, can);
                } catch (Exception ex) {
                    LOG.warn("Pista de baile: canción {} mal escrita: {}", id, ex.toString());
                }
            }
        }
        LOG.info("Pista de baile: {} canciones", l.size());
        LISTA = Collections.unmodifiableList(l);
    }
}

package com.miscelanea.client.arcade;

import com.miscelanea.baile.CancionesBaile;
import com.miscelanea.baile.CancionesPacket;
import com.miscelanea.baile.JuicioBaile;
import com.miscelanea.client.baile.BaileCliente;
import com.miscelanea.client.consola.Botones;
import com.miscelanea.ranking.Ranking;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

/**
 * Paso Doble: las flechas suben al ritmo de la canción y hay que pisar la dirección justo cuando llegan arriba. Usa las
 * mismas canciones que la pista de baile ({@link CancionesBaile}: las del mod y las que añade el dueño del servidor), con
 * sus tres dificultades, sus flechas que se mantienen y las mismas reglas de juicio ({@link JuicioBaile}). Uno o dos
 * jugadores a la vez (cada uno con su dificultad).
 * <p>
 * La lista sale del servidor ({@link CancionesPacket}, en el mismo orden para los dos jugadores); si a este cliente le
 * falta una canción sale en gris: a solas no se puede elegir, y a dos se baila sin flechas ni música en este lado.
 * La pantalla de la recreativa pone la música (con posición, en la máquina), avisa al servidor y hace bailar al jugador
 * con la coreografía de la canción ({@link #empiezaId}, {@link #acaba}).
 */
public class PasoDoble extends JuegoArcade {
    static final int MENU = 0, BAILE = 1, RESULTADO = 2;
    static final int OBJ_Y = 30, LATENCIA = BaileCliente.LATENCIA, FILAS = 5;
    public static final int[] CARRIL_BOTON = {Botones.IZQ, Botones.ABAJO, Botones.ARRIBA, Botones.DER};
    static final int[] CARRIL_COLOR = {43, 63, 28, 11}, CARRIL_LUZ = {44, 40, 29, 12}, CARRIL_COLA = {42, 37, 27, 10};
    static final float[] VELOCIDAD = {1.7F, 2.2F, 2.7F};
    static final String[] JUICIO = {"paso.miscelanea.perfecto", "paso.miscelanea.genial", "paso.miscelanea.bien", "paso.miscelanea.fallo",
            "paso.miscelanea.mantenido", "paso.miscelanea.soltado"};
    static final int[] JUICIO_COLOR = {20, 63, 29, 11, 20, 15};
    static final String[] DIF = {"paso.miscelanea.facil", "paso.miscelanea.normal", "paso.miscelanea.dificil"};
    public static final String[] FLECHA = {
            "......##......", ".....####.....", "....######....", "...########...", "..##########..", ".############.",
            "##############", "....######....", "....######....", "....######....", "....######....", "....######....", "....######....", "....######...."};
    static boolean[][][] mascaras;

    /** Una canción de la lista: lo que dice el servidor y la canción de este cliente (null si no la tiene). */
    static final class Tema {
        final String id, titulo;
        final float bpm, duracion;
        final CancionesBaile.Cancion c;

        Tema(String id, String titulo, float bpm, float duracion, CancionesBaile.Cancion c) {
            this.id = id;
            this.titulo = titulo;
            this.bpm = bpm;
            this.duracion = duracion;
            this.c = c;
        }

        int duracionPasos() {
            return Math.round(duracion * CancionesBaile.PPS);
        }
    }

    static List<Tema> temas() {
        List<Tema> l = new ArrayList<>();
        List<CancionesPacket.Tema> s = CancionesPacket.servidor;
        if (s != null) {
            for (CancionesPacket.Tema t : s) {
                String tit = !t.tituloClave().isEmpty() && I18n.exists(t.tituloClave()) ? I18n.get(t.tituloClave()) : t.titulo();
                l.add(new Tema(t.id(), tit, t.bpm(), t.duracion(), CancionesBaile.de(t.id())));
            }
        } else for (CancionesBaile.Cancion c : CancionesBaile.lista())
            l.add(new Tema(c.id, c.tituloClave != null && I18n.exists(c.tituloClave) ? I18n.get(c.tituloClave) : c.titulo, c.bpm, c.duracion, c));
        return l;
    }

    final class Bailarin {
        final int lado;
        int dif = 1, juicio = -1, juicioT;
        boolean listo, fuera;
        JuicioBaile j = new JuicioBaile(null, null);
        final int[] pulsado = new int[4];
        final boolean[] abajo = new boolean[4];

        Bailarin(int lado) {
            this.lado = lado;
        }

        void preparar(Tema tm) {
            j = new JuicioBaile(tm.c == null ? null : tm.c.notas[dif], (carril, mostrar, paso) -> {
                juicio = mostrar;
                juicioT = 30;
            });
            juicio = -1;
            fuera = false;
            java.util.Arrays.fill(abajo, false);
        }

        void paso(Botones bb, int tiempo) {
            if (juicioT > 0) juicioT--;
            for (int c = 0; c < 4; c++) if (pulsado[c] > 0) pulsado[c]--;
            if (fuera) return;
            for (int c = 0; c < 4; c++) {
                boolean m = bb.mantiene(CARRIL_BOTON[c]);
                if (bb.pulsa(CARRIL_BOTON[c])) {
                    pulsado[c] = 8;
                    j.pulsar(c, tiempo);
                } else if (abajo[c] && !m) j.soltar(c, tiempo);
                abajo[c] = m;
            }
            j.avanzar(tiempo);
            if (j.sinVida()) {
                j.vida = 0;
                fuera = true;
                sonido(ModSounds.GB_FIN, 0.8F);
            }
        }

        String nota() {
            return JuicioBaile.letra(j.nota());
        }
    }

    final Bailarin[] bs = {new Bailarin(0), new Bailarin(1)};
    final List<Tema> temas;
    int estado, cancion, t, tiempoCancion, aviso;
    /** Para la pantalla: id de la canción que acaba de empezar (lo vuelve a null) y si acaba de terminar. */
    public String empiezaId;
    public boolean acaba;

    public PasoDoble(Botones[] b, long semilla, int jugadores) {
        super(b, semilla, jugadores);
        temas = temas();
    }

    Tema tema() {
        return temas.isEmpty() ? null : temas.get(Math.max(0, Math.min(cancion, temas.size() - 1)));
    }

    public int dif(int i) {
        return bs[Math.max(0, Math.min(1, i))].dif;
    }

    public int puntosDe(int i) {
        return bs[Math.max(0, Math.min(1, i))].j.puntos;
    }

    public int notaDe(int i) {
        return bs[Math.max(0, Math.min(1, i))].j.nota();
    }

    @Override
    public void reiniciar() {
        Lucha.cargar();
        if (mascaras == null) {
            mascaras = new boolean[4][14][14];
            for (int y = 0; y < 14; y++)
                for (int x = 0; x < 14; x++) {
                    boolean v = FLECHA[y].charAt(x) == '#';
                    mascaras[2][y][x] = v;                 // arriba
                    mascaras[1][13 - y][x] = v;            // abajo
                    mascaras[0][x][y] = v;                 // izquierda
                    mascaras[3][x][13 - y] = v;            // derecha
                }
        }
        estado = MENU;
        t = 0;
        puntos = 0;
        fin = false;
    }

    @Override
    public void paso() {
        t++;
        if (aviso > 0) aviso--;
        switch (estado) {
            case MENU -> {
                Botones b0 = b[0];
                int n = temas.size();
                if (n > 0 && b0.repite(Botones.ARRIBA, 14, 5)) {
                    cancion = (cancion + n - 1) % n;
                    sonido(ModSounds.GB_MOVER, 1F);
                }
                if (n > 0 && b0.repite(Botones.ABAJO, 14, 5)) {
                    cancion = (cancion + 1) % n;
                    sonido(ModSounds.GB_MOVER, 1F);
                }
                for (int i = 0; i < jugadores; i++) {
                    Botones bb = b[i];
                    if (bb.pulsa(Botones.IZQ)) bs[i].dif = Math.max(0, bs[i].dif - 1);
                    if (bb.pulsa(Botones.DER)) bs[i].dif = Math.min(2, bs[i].dif + 1);
                    if (bb.pulsa(Botones.A) || bb.pulsa(Botones.START)) bs[i].listo = true;
                }
                Tema tm = tema();
                boolean todos = bs[0].listo && (jugadores < 2 || bs[1].listo);
                if (tm == null) {
                    bs[0].listo = bs[1].listo = false;
                    break;
                }
                // a solas no se puede elegir una canción que este cliente no tiene (a dos sí: la partida va a la vez en los dos)
                if (jugadores < 2 && tm.c == null && (todos || t > 60 * 30)) {
                    bs[0].listo = false;
                    t = 0;
                    aviso = 120;
                    sonido(ModSounds.GB_FIN, 1.4F);
                    break;
                }
                if (todos || t > 60 * 30) {
                    for (int i = 0; i < 2; i++) bs[i].preparar(tm);
                    bs[1].fuera = jugadores < 2;
                    estado = BAILE;
                    t = 0;
                    empiezaId = tm.id;
                }
            }
            case BAILE -> {
                tiempoCancion = t - LATENCIA;
                for (int i = 0; i < jugadores; i++) bs[i].paso(b[i], tiempoCancion);
                boolean vivos = !bs[0].fuera || (jugadores >= 2 && !bs[1].fuera);
                if (tiempoCancion > tema().duracionPasos() + 30 || !vivos) {
                    estado = RESULTADO;
                    t = 0;
                    acaba = true;
                    puntos = Math.max(bs[0].j.puntos, jugadores >= 2 ? bs[1].j.puntos : 0);
                    sonido(vivos ? ModSounds.GB_INICIO : ModSounds.GB_FIN, 1F);
                }
            }
            case RESULTADO -> {
                if (t > 240 || (t > 60 && b[0].pulsa(Botones.A))) {
                    fin = true;
                    mensajeFin = I18n.get("paso.miscelanea.nota", bs[0].nota());
                }
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ dibujo
    /** Pulso de la canción en el paso de juego (con su mapa de tempo, si lo tiene). */
    float pulso() {
        Tema tm = tema();
        if (tm == null) return t / 30F;
        float s = tiempoCancion / (float) CancionesBaile.PPS;
        return tm.c != null ? tm.c.pulso(s) : s * tm.bpm / 60F;
    }

    @Override
    public void dibujar(Pantalla p) {
        float pul = estado == BAILE ? pulso() : t / 30F;
        boolean golpe = estado == BAILE && tiempoCancion > 0 && pul >= 0 && pul - (float) Math.floor(pul) < 0.09F;
        p.limpiar(golpe ? 42 : 41);
        // ecualizador de fondo
        for (int k = 0; k < 16; k++) {
            int h = estado == BAILE ? 10 + (int) (Math.abs(Math.sin(k * 1.7 + t * 0.09)) * (golpe ? 50 : 30)) : 8;
            p.rect(k * 16 + 2, Pantalla.H - h, 12, h, k % 2 == 0 ? 43 : 45);
        }
        for (int y = 0; y < Pantalla.H; y += 3) p.rect(0, y, Pantalla.W, 1, 41);
        if (estado == MENU) {
            menu(p);
            return;
        }
        if (jugadores >= 2) {
            pista(p, bs[0], 20);
            pista(p, bs[1], 140);
        } else {
            pista(p, bs[0], 60);
            bailarin(p, golpe, pul);
        }
        rotulo(p);
        if (estado == RESULTADO) resultado(p);
    }

    String recortar(Pantalla p, String s, int ancho) {
        if (p.ancho(s) <= ancho) return s;
        while (s.length() > 1 && p.ancho(s + "…") > ancho) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    void menu(Pantalla p) {
        p.rect(0, 6, Pantalla.W, 18, 0);
        p.textoC(I18n.get("paso.miscelanea.titulo"), 128, 11, 20);
        int n = temas.size();
        if (n == 0) {
            p.textoC(I18n.get("baile.miscelanea.sin_canciones"), 128, 80, 12);
            return;
        }
        int primero = Math.max(0, Math.min(cancion - FILAS / 2, n - FILAS));
        for (int f = 0; f < FILAS && primero + f < n; f++) {
            int c = primero + f, y = 34 + f * 20;
            Tema tm = temas.get(c);
            boolean s = c == cancion, falta = tm.c == null;
            p.rect(16, y - 4, 224, 17, s ? 45 : 1);
            if (s) p.marco(16, y - 4, 224, 17, falta ? 3 : 20);
            if (s && (t / 15) % 2 == 0) for (int k = 0; k < 5; k++) p.rect(20, y + k, 3 - Math.abs(k - 2), 1, 20);
            String der = falta ? I18n.get("paso.miscelanea.no_instalada") : Math.round(tm.bpm) + " BPM";
            int wd = p.ancho(der);
            p.texto(recortar(p, tm.titulo, 232 - 26 - wd - 8), 26, y, falta ? 3 : s ? 7 : 5);
            p.textoD(der, 234, y, falta ? (s ? 12 : 10) : s ? 20 : 4);
        }
        if (primero > 0 && (t / 15) % 2 == 0) p.textoC("▲", 128, 22, 6);
        if (primero + FILAS < n && (t / 15) % 2 == 0) p.textoC("▼", 128, 30 + FILAS * 20 - 6, 6);
        for (int i = 0; i < jugadores; i++) {
            int x = jugadores >= 2 ? (i == 0 ? 70 : 186) : 128;
            p.textoC((i == 0 ? "1P" : "2P") + ": ◄ " + I18n.get(DIF[bs[i].dif]) + " ►", x, 136, bs[i].listo ? 29 : 7);
        }
        // récord del servidor de la canción elegida (en la dificultad del jugador 1)
        Tema tm = tema();
        ListTag tb = ArcadeCliente.tabla(Ranking.categoriaPaso(tm.id, bs[0].dif));
        String rec = tb.isEmpty() ? I18n.get("paso.miscelanea.sin_record")
                : I18n.get("paso.miscelanea.record", tb.getCompound(0).getInt("p"), tb.getCompound(0).getString("n"));
        p.textoC(recortar(p, rec, 240), 128, 149, tb.isEmpty() ? 4 : 19);
        if (aviso > 0) {
            if ((aviso / 8) % 2 == 0) p.textoC(I18n.get("paso.miscelanea.falta_cancion"), 128, 164, 12);
        } else p.textoC(I18n.get("paso.miscelanea.ayuda"), 128, 162, 6);
        if ((t / 20) % 2 == 0) p.textoC(I18n.get("paso.miscelanea.empezar"), 128, 176, 19);
    }

    /** Título de la canción y barra de lo que lleva, abajo del todo. */
    void rotulo(Pantalla p) {
        Tema tm = tema();
        if (tm == null) return;
        p.rect(0, 180, Pantalla.W, 12, 0);
        int dur = Math.max(1, tm.duracionPasos());
        p.rect(0, 180, Math.max(0, Math.min(Pantalla.W, Pantalla.W * tiempoCancion / dur)), 1, 46);
        String d = I18n.get(DIF[bs[0].dif]) + (jugadores >= 2 ? " / " + I18n.get(DIF[bs[1].dif]) : "");
        p.textoD(d, 250, 182, 39);
        p.texto(recortar(p, "♪ " + tm.titulo, 240 - p.ancho(d) - 10), 6, 182, tm.c == null ? 3 : 7);
    }

    void pista(Pantalla p, Bailarin bl, int x0) {
        int tiempo = tiempoCancion;
        float vel = VELOCIDAD[bl.dif];
        JuicioBaile j = bl.j;
        // receptores (iluminados al pisar o mientras se mantiene el paso)
        for (int c = 0; c < 4; c++) {
            boolean luz = bl.pulsado[c] > 0 || bl.abajo[c] && estado == BAILE;
            flecha(p, x0 + c * 24, OBJ_Y, c, luz ? 7 : 3, luz ? 6 : 2, true);
        }
        if (estado == BAILE || estado == RESULTADO) {
            // primero las colas de las flechas que se mantienen (por debajo de las cabezas)
            for (int i = 0; i < j.notas.length; i++) {
                CancionesBaile.Nota n = j.notas[i];
                int yh = OBJ_Y + Math.round((n.t - tiempo) * vel);
                if (yh > Pantalla.H + 10) break;
                if (!n.mantener() || j.estado[i] == JuicioBaile.HECHA) continue;
                int ye = OBJ_Y + Math.round((n.fin - tiempo) * vel);
                if (ye < -16) continue;
                cola(p, x0 + n.carril * 24, n.carril, j.estado[i] == JuicioBaile.MANTENIENDO ? OBJ_Y : yh, ye, j.estado[i], j.tRoto[i], tiempo, vel);
            }
            for (int i = 0; i < j.notas.length; i++) {
                CancionesBaile.Nota n = j.notas[i];
                int c = n.carril, y = OBJ_Y + Math.round((n.t - tiempo) * vel);
                if (y > Pantalla.H + 10) break;
                if (j.estado[i] == JuicioBaile.MANTENIENDO) {
                    // la cabeza se queda en el receptor, brillando
                    flecha(p, x0 + c * 24, OBJ_Y, c, (tiempo / 3) % 2 == 0 ? 7 : CARRIL_LUZ[c], 7, false);
                    continue;
                }
                if (j.estado[i] != JuicioBaile.PENDIENTE || y < -16) continue;
                flecha(p, x0 + c * 24, y, c, CARRIL_COLOR[c], CARRIL_LUZ[c], false);
            }
        }
        // vida, puntos, juicio y combo
        p.rect(x0, 6, 92, 6, 0);
        p.rect(x0 + 1, 7, Math.round(90 * Math.max(0, j.vida) / 100F), 4, j.vida < 25 ? 11 : 29);
        p.textoD(Integer.toString(j.puntos), x0 + 92, 14, 7);
        if (bl.juicioT > 0 && bl.juicio >= 0) p.textoC(I18n.get(JUICIO[bl.juicio]), x0 + 46, 70, JUICIO_COLOR[bl.juicio]);
        if (j.combo >= 5) p.textoC(j.combo + " COMBO", x0 + 46, 84, 19);
        if (bl.fuera && estado == BAILE) p.textoC(I18n.get("paso.miscelanea.fuera"), x0 + 46, 100, 11);
    }

    /** Cola de una flecha que se mantiene: de la cabeza (yh) al final (ye), en el carril que empieza en x. */
    void cola(Pantalla p, int x, int c, int yh, int ye, int est, int tRoto, int tiempo, float vel) {
        int arriba = yh + 7, abajo = Math.min(Pantalla.H + 4, ye + 7);
        if (abajo <= arriba) return;
        if (est == JuicioBaile.FALLADA) {
            // rota: gris, partida en trozos que se caen (solo por debajo de los receptores)
            arriba = Math.max(arriba, OBJ_Y + 7);
            int caida = Math.max(0, tiempo - tRoto) / 2;
            for (int y = arriba, k = 0; y < abajo; y += 9, k++) {
                int h = Math.min(6, abajo - y), dx = (k % 2) - (k % 3 == 0 ? 1 : 0);
                p.rect(x + 3 + dx, y + caida + k % 2, 8, h, 0);
                p.rect(x + 4 + dx, y + caida + k % 2, 6, h - 1, 3);
                p.rect(x + 4 + dx, y + caida + k % 2, 1, h - 1, 4);
            }
            return;
        }
        boolean sujeta = est == JuicioBaile.MANTENIENDO;
        p.rect(x + 2, arriba, 10, abajo - arriba, 0);
        if (sujeta) {
            // brilla mientras se mantiene: halo a los lados y cuerpo parpadeando
            boolean on = (tiempo / 3) % 2 == 0;
            p.rect(x + 1, arriba, 1, abajo - arriba, CARRIL_COLOR[c]);
            p.rect(x + 12, arriba, 1, abajo - arriba, CARRIL_COLOR[c]);
            p.rect(x + 3, arriba, 8, abajo - arriba, on ? CARRIL_LUZ[c] : CARRIL_COLOR[c]);
            p.rect(x + 4, arriba, 2, abajo - arriba, 7);
        } else {
            p.rect(x + 3, arriba, 8, abajo - arriba, CARRIL_COLA[c]);
            p.rect(x + 4, arriba, 1, abajo - arriba, CARRIL_LUZ[c]);
            // rayas que suben con la flecha
            int fase = Math.floorMod(Math.round(-tiempo * vel), 6);
            for (int y = arriba + fase; y < abajo - 3; y += 6) p.rect(x + 5, y, 6, 1, CARRIL_COLOR[c]);
        }
        // remate de la cola
        p.rect(x + 1, abajo - 3, 12, 4, 0);
        p.rect(x + 2, abajo - 2, 10, 2, sujeta ? 7 : CARRIL_LUZ[c]);
        p.rect(x + 3, abajo - 3, 8, 1, sujeta ? 7 : CARRIL_COLOR[c]);
    }

    void flecha(Pantalla p, int x, int y, int c, int col, int luz, boolean receptor) {
        boolean[][] m = mascaras[c];
        for (int yy = 0; yy < 14; yy++)
            for (int xx = 0; xx < 14; xx++) {
                boolean v = m[yy][xx];
                if (v) {
                    if (receptor) {
                        boolean borde = yy == 0 || xx == 0 || yy == 13 || xx == 13 || !m[yy - 1][xx] || !m[yy + 1][xx] || !m[yy][xx - 1] || !m[yy][xx + 1];
                        if (borde) p.px(x + xx, y + yy, col);
                        else if (col == 7) p.px(x + xx, y + yy, luz);
                    } else p.px(x + xx, y + yy, yy + xx < 12 ? luz : col);
                } else {
                    boolean vecino = (yy > 0 && m[yy - 1][xx]) || (yy < 13 && m[yy + 1][xx]) || (xx > 0 && m[yy][xx - 1]) || (xx < 13 && m[yy][xx + 1]);
                    if (vecino && !receptor) p.px(x + xx, y + yy, 0);
                }
            }
    }

    void bailarin(Pantalla p, boolean golpe, float pul) {
        int[] pasos = {Lucha.QUIETO, Lucha.VICTORIA, Lucha.PUNO, Lucha.PATADA, Lucha.SALTO, Lucha.PUNO2, Lucha.AGACHADO, Lucha.VICTORIA};
        int k = estado == BAILE && tiempoCancion > 0 ? (int) Math.floor(Math.max(0, pul)) : t / 30;
        JuicioBaile j = bs[0].j;
        int f = bs[0].fuera ? Lucha.TUMBADO : j.combo >= 10 || estado != BAILE ? pasos[k % pasos.length] : (k % 2 == 0 ? Lucha.QUIETO : Lucha.QUIETO + 2);
        Lucha.sombra(p, 205, 170, 16);
        Lucha.dibujar(p, Lucha.LUNA, f, 205, 170 - (golpe ? 2 : 0), k % 4 < 2 ? 1 : -1, 0);
    }

    void resultado(Pantalla p) {
        p.rect(28, 36, 200, 120, 0);
        p.marco(28, 36, 200, 120, 45);
        p.textoC(I18n.get("paso.miscelanea.resultado"), 128, 42, 20);
        for (int i = 0; i < jugadores; i++) {
            Bailarin bl = bs[i];
            JuicioBaile j = bl.j;
            int x = jugadores >= 2 ? (i == 0 ? 78 : 178) : 128;
            p.textoC(bl.nota(), x, 56, bl.nota().equals("S") ? 20 : 7);
            p.textoC(I18n.get("paso.miscelanea.perfecto") + " " + j.perf, x, 70, 20);
            p.textoC(I18n.get("paso.miscelanea.genial") + " " + j.geni, x, 81, 63);
            p.textoC(I18n.get("paso.miscelanea.bien") + " " + j.bien, x, 92, 29);
            p.textoC(I18n.get("paso.miscelanea.fallo") + " " + j.fall, x, 103, 11);
            if (j.mantTotal > 0) p.textoC(I18n.get("baile.miscelanea.mantenidas", j.mant, j.mantTotal), x, 114, 39);
            p.textoC("COMBO " + j.maxCombo, x, 126, 7);
            p.textoC(Integer.toString(j.puntos), x, 138, 19);
        }
    }
}

package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Botones;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Mazmorra 3D: shooter en primera persona con motor de raycasting propio (muros, suelo y techo con textura, puertas
 * correderas, niebla por distancia y sprites con búfer de profundidad). Texturas, monstruos y armas de Freedoom (BSD).
 * Arriba/abajo: andar · izq/der: girar (con B mantenido, paso lateral) · A: disparar · B: abrir/usar · C: cambiar de arma.
 */
public class Mazmorra3D extends JuegoArcade {
    static final int VW = Pantalla.W, VH = 160, HUD = Pantalla.H - VH;
    static final int MURO_PUERTA = 13, PUERTA_ROJA = 14, PUERTA_AZUL = 15, PUERTA_AMARILLA = 16, SALIDA = 17;
    static final int LUCES = 8;
    static final byte[][] LUZ = new byte[LUCES][64];

    static {
        for (int l = 0; l < LUCES; l++) {
            float k = 1F - l * 0.105F;
            for (int c = 0; c < 64; c++) {
                int p = Pantalla.PALETA[c];
                int r = (int) (((p >> 16) & 255) * k), g = (int) (((p >> 8) & 255) * k), b = (int) ((p & 255) * k);
                LUZ[l][c] = (byte) Pantalla.cercano(0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
    }

    // ------------------------------------------------------------------ niveles (24x24)
    // muros 1-9 y W X V (10-12) · D puerta · R B Y puertas de llave · E salida · P jugador
    // z zombi · i diablillo · d demonio · s alma · c cacodemonio · k balas · h cartuchos · m botiquín · t estimulante
    // g escopeta · a armadura · b poción · r u y llaves roja/azul/amarilla · o columna · l antorcha · x barril
    static final String[][] NIVELES = {{
            "555555555555555555555555",
            "5P..k.5........5.......5",
            "5.....5..z..h..5..z.m..5",
            "5..o..D........D.......5",
            "5.....5..x.....5..i....5",
            "55D5555........555555D55",
            "5.....555D555555.....5.5",
            "5..z..5....5..i......5.5",
            "5.....5.g..5.........D.5",
            "5..t..5....5..x..z...5.5",
            "5555D55....55555D555.5.5",
            "1.....1z...1...l...1.5.5",
            "1..z..1....1.......1.5.5",
            "1.....D....D...r...1.5k5",
            "1..k..1....1.......1.5.5",
            "1111111111111..z...1.5.5",
            "1.............1....1.5.5",
            "1..l..o...l...1111R1.5.5",
            "1.............1....1...5",
            "1...z..i..z...1..i.1.555",
            "1.............1....1.5.5",
            "1..h.....m....1..a.R.E.5",
            "1.............1....1.5.5",
            "111111111111111111111555"}, {
            "333333333333333333333333",
            "3P....3.....l.....3....3",
            "3.....3..d.....d..3..m.3",
            "3..o..D...........D....3",
            "3.....3...WWWWW...3..s.3",
            "33D3333...W.u.W...333D33",
            "3.....3...W.d.W...3....3",
            "3..i..3...WWDWW...3.i..3",
            "3.....3...........3....3",
            "3..k..33333D3333333..h.3",
            "3.....3.......i.......53",
            "3..z..3..o.....o..d...53",
            "3.....D...............53",
            "33D3333..o.....o..i...53",
            "3.....3...............53",
            "3..h..33333333B33333..53",
            "3.....3....3.....3....53",
            "3.x...3.s..3..c..3.k..53",
            "3.....D....D.....D....53",
            "3..m..3.d..3.....3.i..53",
            "3.....3....3..a..3....53",
            "3..t..3....3.....3..b.E3",
            "3.....3....3..y..3....55",
            "333333333333333333333333"}, {
            "777777777777777777777777",
            "7P....7.......7........7",
            "7..m..7..i.i..7...d..h.7",
            "7.....D.......D......y.7",
            "7..l..7...x...7..s..s..7",
            "77D7777.......777777D777",
            "7.....777D7777.........7",
            "7..d..7....7...i...i...7",
            "7.....7.i..7...........7",
            "7..k..7....7..ooo.ooo..7",
            "7.....7.a..7...........7",
            "7777D777777777777Y777777",
            "8......................8",
            "8..l....i....d....i..l.8",
            "8......................8",
            "8..o...o...o...o...o...8",
            "8......................8",
            "8.h..s.....c......s..m.8",
            "8......................8",
            "8..o...o...o...o...o...8",
            "8......................8",
            "8..l.......b.......l...8",
            "8.........8E8..........8",
            "888888888888888888888888"}};
    static final int[][] SUELO_TECHO = {{0, 1}, {2, 3}, {4, 5}};

    // ------------------------------------------------------------------ estado
    static final class Cosa {
        int tipo;               // 0 zombi, 1 diablillo, 2 demonio, 3 alma, 4 cacodemonio, 10.. objetos, 30 columna, 31 antorcha, 32 barril
        float x, y, vx, vy;
        int hp, estado, t, cad, dolor;    // estado: 0 quieto, 1 persigue, 2 ataca, 3 muere, 4 muerto
        boolean despierto, solido;
    }

    static final class Bola {
        float x, y, vx, vy;
        int tipo, t, dano;
        boolean explota;
        int et;
    }

    int[][] mapa = new int[24][24];
    float[][] puerta = new float[24][24];        // 0 cerrada .. 1 abierta
    int[][] puertaT = new int[24][24];
    final List<Cosa> cosas = new ArrayList<>();
    final List<Bola> bolas = new ArrayList<>();
    final List<float[]> soplos = new ArrayList<>();   // x, y, t, tipo (0 humo de bala, 1 sangre)
    float px, py, ang;
    int nivel, salud, armadura, balas, cartuchos, arma, armas = 0b011, llaves, cad, dispT, dolorT, cogerT, bobT, t, nivelT, muerteT;
    boolean muerto, completado;
    float[] zbuf = new float[VW];
    Sprite[] muros, suelos;
    final Map<String, Sprite> spr = new HashMap<>();
    String aviso = "";
    int avisoT;

    public Mazmorra3D(Botones[] b, long semilla, int jugadores) {
        super(b, semilla, 1);
    }

    @Override
    public SoundEvent musica() {
        return ModSounds.MZ_MUSICA.get();
    }

    Sprite s(String n) {
        return spr.computeIfAbsent(n, k -> Sprite.uno("mazmorra/spr/" + k));
    }

    @Override
    public void reiniciar() {
        muros = Sprite.hoja("mazmorra/muros", 64, 64);
        suelos = Sprite.hoja("mazmorra/suelos", 64, 64);
        salud = 100;
        armadura = 0;
        balas = 50;
        cartuchos = 0;
        arma = 1;
        armas = 0b011;
        puntos = 0;
        fin = false;
        cargarNivel(0);
    }

    void cargarNivel(int n) {
        nivel = n;
        String[] m = NIVELES[n];
        cosas.clear();
        bolas.clear();
        soplos.clear();
        llaves = 0;
        completado = false;
        nivelT = 0;
        for (int y = 0; y < 24; y++)
            for (int x = 0; x < 24; x++) {
                char c = m[y].charAt(x);
                puerta[y][x] = 0;
                puertaT[y][x] = 0;
                int v = switch (c) {
                    case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> c - '0';
                    case 'W' -> 10;
                    case 'X' -> 11;
                    case 'V' -> 12;
                    case 'D' -> MURO_PUERTA;
                    case 'R' -> PUERTA_ROJA;
                    case 'B' -> PUERTA_AZUL;
                    case 'Y' -> PUERTA_AMARILLA;
                    case 'E' -> SALIDA;
                    default -> 0;
                };
                mapa[y][x] = v;
                float cx = x + 0.5F, cy = y + 0.5F;
                switch (c) {
                    case 'P' -> {
                        px = cx;
                        py = cy;
                        ang = 0;
                    }
                    case 'z' -> cosa(0, cx, cy, 20);
                    case 'i' -> cosa(1, cx, cy, 60);
                    case 'd' -> cosa(2, cx, cy, 150);
                    case 's' -> cosa(3, cx, cy, 100);
                    case 'c' -> cosa(4, cx, cy, n == 2 ? 600 : 400);
                    case 'k' -> cosa(10, cx, cy, 0);
                    case 'h' -> cosa(11, cx, cy, 0);
                    case 'm' -> cosa(12, cx, cy, 0);
                    case 't' -> cosa(13, cx, cy, 0);
                    case 'g' -> cosa(14, cx, cy, 0);
                    case 'a' -> cosa(15, cx, cy, 0);
                    case 'b' -> cosa(16, cx, cy, 0);
                    case 'r' -> cosa(17, cx, cy, 0);
                    case 'u' -> cosa(18, cx, cy, 0);
                    case 'y' -> cosa(19, cx, cy, 0);
                    case 'o' -> cosa(30, cx, cy, 0).solido = true;
                    case 'l' -> cosa(31, cx, cy, 0).solido = true;
                    case 'x' -> {
                        Cosa k = cosa(32, cx, cy, 20);
                        k.solido = true;
                    }
                    default -> {}
                }
            }
        avisar(I18n.get("mazmorra.miscelanea.nivel." + n), 150);
    }

    Cosa cosa(int tipo, float x, float y, int hp) {
        Cosa k = new Cosa();
        k.tipo = tipo;
        k.x = x;
        k.y = y;
        k.hp = hp;
        k.solido = tipo < 10;
        k.cad = 30 + rnd.nextInt(60);
        cosas.add(k);
        return k;
    }

    void avisar(String s, int d) {
        aviso = s;
        avisoT = d;
    }

    // ------------------------------------------------------------------ mapa
    boolean pared(int x, int y) {
        if (x < 0 || y < 0 || x >= 24 || y >= 24) return true;
        int v = mapa[y][x];
        if (v == 0) return false;
        if (v >= MURO_PUERTA && v <= PUERTA_AMARILLA) return puerta[y][x] < 0.85F;
        return true;
    }

    boolean libre(float x, float y, float r, Cosa yo) {
        for (int dy = -1; dy <= 1; dy += 2)
            for (int dx = -1; dx <= 1; dx += 2)
                if (pared((int) Math.floor(x + dx * r), (int) Math.floor(y + dy * r))) return false;
        for (Cosa c : cosas)
            if (c != yo && c.solido && c.estado < 3 && Math.abs(c.x - x) < r + 0.25F && Math.abs(c.y - y) < r + 0.25F) return false;
        return true;
    }

    /** ¿Se ve b desde a? (sin muros ni puertas cerradas en medio). */
    boolean visible(float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        int n = (int) (Math.hypot(dx, dy) * 6) + 1;
        for (int i = 1; i < n; i++) if (pared((int) (ax + dx * i / n), (int) (ay + dy * i / n))) return false;
        return true;
    }

    /** Distancia hasta el muro en una dirección. */
    float rayo(float ox, float oy, float dx, float dy) {
        for (float d = 0; d < 30; d += 0.05F) if (pared((int) (ox + dx * d), (int) (oy + dy * d))) return d;
        return 30;
    }

    void abrir(int x, int y) {
        int v = mapa[y][x];
        if (v < MURO_PUERTA || v > SALIDA) return;
        if (v == SALIDA) {
            if (!completado) {
                completado = true;
                nivelT = 0;
                sonido(ModSounds.MZ_SALIDA, 1F);
                puntos += 1000 + salud * 10;
                avisar(I18n.get("mazmorra.miscelanea.completado"), 150);
            }
            return;
        }
        int necesita = v == PUERTA_ROJA ? 1 : v == PUERTA_AZUL ? 2 : v == PUERTA_AMARILLA ? 4 : 0;
        if ((llaves & necesita) != necesita) {
            if (avisoT < 40) {
                avisar(I18n.get("mazmorra.miscelanea.llave_" + (necesita == 1 ? "roja" : necesita == 2 ? "azul" : "amarilla")), 70);
                sonido(ModSounds.MZ_DOLOR, 1.6F);
            }
            return;
        }
        if (puertaT[y][x] == 0) sonido(ModSounds.MZ_PUERTA, 1F);
        puertaT[y][x] = 240;
    }

    // ------------------------------------------------------------------ lógica
    @Override
    public void paso() {
        t++;
        nivelT++;
        if (avisoT > 0) avisoT--;
        if (dolorT > 0) dolorT--;
        if (cogerT > 0) cogerT--;
        if (cad > 0) cad--;
        if (dispT > 0) dispT--;
        // puertas
        for (int y = 0; y < 24; y++)
            for (int x = 0; x < 24; x++) {
                if (puertaT[y][x] > 0) {
                    boolean ocupada = (int) px == x && (int) py == y;
                    for (Cosa c : cosas) if (c.tipo < 10 && c.estado < 3 && (int) c.x == x && (int) c.y == y) ocupada = true;
                    if (!ocupada || puertaT[y][x] > 1) puertaT[y][x]--;
                    puerta[y][x] = Math.min(1F, puerta[y][x] + 0.03F);
                } else if (puerta[y][x] > 0) {
                    puerta[y][x] = Math.max(0F, puerta[y][x] - 0.03F);
                    if (puerta[y][x] > 0.95F) sonido(ModSounds.MZ_PUERTA, 0.8F);
                }
            }
        if (muerto) {
            if (++muerteT > 120) {
                fin = true;
                mensajeFin = I18n.get("mazmorra.miscelanea.muerto");
            }
            pasoCosas();
            return;
        }
        if (completado) {
            if (nivelT > 120) {
                if (nivel + 1 >= NIVELES.length) {
                    fin = true;
                    puntos += salud * 50;
                    mensajeFin = I18n.get("mazmorra.miscelanea.victoria");
                } else cargarNivel(nivel + 1);
            }
            return;
        }
        Botones bb = b[0];
        float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
        float av = 0, la = 0;
        if (bb.mantiene(Botones.ARRIBA)) av += 0.06F;
        if (bb.mantiene(Botones.ABAJO)) av -= 0.045F;
        boolean lateral = bb.mantiene(Botones.B);
        if (lateral) {
            if (bb.mantiene(Botones.IZQ)) la -= 0.05F;
            if (bb.mantiene(Botones.DER)) la += 0.05F;
        } else {
            if (bb.mantiene(Botones.IZQ)) ang -= 0.05F;
            if (bb.mantiene(Botones.DER)) ang += 0.05F;
        }
        float mx = dx * av - dy * la, my = dy * av + dx * la;
        if (mx != 0 || my != 0) bobT++;
        if (libre(px + mx, py, 0.25F, null)) px += mx;
        if (libre(px, py + my, 0.25F, null)) py += my;
        // abrir puertas: al pulsar B o al empujarlas andando
        int fx = (int) (px + dx * 0.8F), fy = (int) (py + dy * 0.8F);
        if (bb.pulsa(Botones.B) || (av > 0 && mapa[fy][fx] >= MURO_PUERTA)) abrir(fx, fy);
        // cambiar de arma
        if (bb.pulsa(Botones.C)) {
            for (int k = 1; k <= 3; k++) {
                int a = (arma + k) % 3;
                if ((armas >> a & 1) != 0) {
                    arma = a;
                    break;
                }
            }
            sonido(ModSounds.MZ_COGER, 1.5F);
        }
        if (bb.mantiene(Botones.A) && cad == 0) disparar();
        // recoger objetos
        for (Iterator<Cosa> it = cosas.iterator(); it.hasNext(); ) {
            Cosa c = it.next();
            if (c.tipo < 10 || c.tipo >= 30 || Math.abs(c.x - px) > 0.5F || Math.abs(c.y - py) > 0.5F) continue;
            boolean coge = true;
            switch (c.tipo) {
                case 10 -> balas = Math.min(200, balas + 10);
                case 11 -> cartuchos = Math.min(50, cartuchos + 4);
                case 12 -> {
                    if (salud >= 100) coge = false;
                    else salud = Math.min(100, salud + 25);
                }
                case 13 -> {
                    if (salud >= 100) coge = false;
                    else salud = Math.min(100, salud + 10);
                }
                case 14 -> {
                    armas |= 0b100;
                    arma = 2;
                    cartuchos += 8;
                    avisar(I18n.get("mazmorra.miscelanea.escopeta"), 80);
                }
                case 15 -> armadura = Math.max(armadura, 100);
                case 16 -> salud = Math.min(200, salud + 1);
                case 17 -> {
                    llaves |= 1;
                    avisar(I18n.get("mazmorra.miscelanea.llave_roja_ok"), 80);
                }
                case 18 -> {
                    llaves |= 2;
                    avisar(I18n.get("mazmorra.miscelanea.llave_azul_ok"), 80);
                }
                case 19 -> {
                    llaves |= 4;
                    avisar(I18n.get("mazmorra.miscelanea.llave_amarilla_ok"), 80);
                }
                default -> {}
            }
            if (coge) {
                it.remove();
                cogerT = 6;
                puntos += 10;
                sonido(ModSounds.MZ_COGER, 1F);
            }
        }
        pasoCosas();
        pasoBolas();
        for (Iterator<float[]> it = soplos.iterator(); it.hasNext(); ) if (++it.next()[2] > 16) it.remove();
    }

    void disparar() {
        dispT = 10;
        float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
        if (arma == 0) {
            cad = 22;
            for (Cosa c : cosas)
                if (c.tipo < 10 && c.estado < 3 && Math.hypot(c.x - px - dx * 0.7F, c.y - py - dy * 0.7F) < 0.6F) {
                    herir(c, 8 + rnd.nextInt(10));
                    sonido(ModSounds.CH_GOLPE, 0.8F);
                    return;
                }
            return;
        }
        if (arma == 1 && balas <= 0 || arma == 2 && cartuchos <= 0) {
            arma = arma == 2 && balas > 0 ? 1 : 0;
            return;
        }
        int perdigones = arma == 2 ? 7 : 1;
        if (arma == 1) balas--;
        else cartuchos--;
        cad = arma == 2 ? 50 : 16;
        sonido(arma == 2 ? ModSounds.MZ_ESCOPETA : ModSounds.CH_DISPARO, arma == 2 ? 1F : 0.85F);
        despertar(14);
        for (int k = 0; k < perdigones; k++) {
            float a = ang + (arma == 2 ? (rnd.nextFloat() - 0.5F) * 0.2F : (rnd.nextFloat() - 0.5F) * 0.03F);
            float rx = (float) Math.cos(a), ry = (float) Math.sin(a);
            float pared = rayo(px, py, rx, ry);
            Cosa mejor = null;
            float md = pared;
            for (Cosa c : cosas) {
                if (!(c.tipo < 10 && c.estado < 3) && c.tipo != 32) continue;
                if (c.tipo == 32 && c.estado >= 3) continue;
                float ox = c.x - px, oy = c.y - py;
                float along = ox * rx + oy * ry;
                if (along <= 0 || along > md) continue;
                float perp = Math.abs(ox * ry - oy * rx);
                if (perp < (c.tipo == 4 ? 0.45F : 0.3F)) {
                    md = along;
                    mejor = c;
                }
            }
            if (mejor != null) {
                herir(mejor, arma == 2 ? 5 + rnd.nextInt(10) : 6 + rnd.nextInt(10));
                soplos.add(new float[]{mejor.x - rx * 0.2F, mejor.y - ry * 0.2F, 0, mejor.tipo == 32 ? 0 : 1});
            } else soplos.add(new float[]{px + rx * (pared - 0.08F), py + ry * (pared - 0.08F), 0, 0});
        }
    }

    void despertar(float radio) {
        for (Cosa c : cosas) if (c.tipo < 10 && Math.hypot(c.x - px, c.y - py) < radio) c.despierto = true;
    }

    void herir(Cosa c, int d) {
        if (c.tipo == 32) {
            c.hp -= d;
            if (c.hp <= 0 && c.estado < 3) {
                c.estado = 3;
                c.t = 0;
                sonido(ModSounds.CH_EXPLOSION, 1F);
                for (Cosa o : cosas)
                    if (o != c && o.tipo < 10 && o.estado < 3 && Math.hypot(o.x - c.x, o.y - c.y) < 1.8) herir(o, 60);
                if (Math.hypot(px - c.x, py - c.y) < 1.8) recibir(40);
            }
            return;
        }
        c.hp -= d;
        c.despierto = true;
        if (c.hp <= 0) {
            c.estado = 3;
            c.t = 0;
            c.solido = false;
            puntos += switch (c.tipo) {
                case 0 -> 100;
                case 1 -> 200;
                case 2 -> 300;
                case 3 -> 250;
                default -> 2000;
            };
            sonido(ModSounds.MZ_MUERE, c.tipo == 4 ? 0.6F : 0.9F + rnd.nextFloat() * 0.3F);
        } else if (rnd.nextInt(3) == 0 || c.tipo == 0) {
            c.dolor = 8;
            if (rnd.nextInt(2) == 0) sonido(ModSounds.MZ_GRUNIDO, 1.3F);
        }
    }

    void recibir(int d) {
        if (muerto || completado) return;
        if (armadura > 0) {
            int a = Math.min(armadura, d / 3);
            armadura -= a;
            d -= a;
        }
        salud -= d;
        dolorT = 12;
        sonido(ModSounds.MZ_DOLOR, 1F);
        if (salud <= 0) {
            salud = 0;
            muerto = true;
            muerteT = 0;
        }
    }

    void pasoCosas() {
        for (Cosa c : cosas) {
            c.t++;
            if (c.tipo >= 10) continue;
            if (c.estado == 3) {
                if (c.t > 30) c.estado = 4;
                continue;
            }
            if (c.estado == 4) continue;
            if (c.dolor > 0) {
                c.dolor--;
                continue;
            }
            float dx = px - c.x, dy = py - c.y, d = (float) Math.hypot(dx, dy);
            boolean ve = d < 14 && visible(c.x, c.y, px, py);
            if (!c.despierto) {
                if (ve && t % 10 == 0) {
                    c.despierto = true;
                    sonido(ModSounds.MZ_GRUNIDO, c.tipo == 4 ? 0.6F : 0.9F);
                }
                continue;
            }
            if (muerto) continue;
            if (c.cad > 0) c.cad--;
            float vel = switch (c.tipo) {
                case 0 -> 0.022F;
                case 1 -> 0.026F;
                case 2 -> 0.045F;
                case 3 -> 0.03F;
                default -> 0.018F;
            };
            // atacar
            if (c.estado == 2) {
                if (c.t > 24) c.estado = 1;
                continue;
            }
            boolean cuerpo = c.tipo == 2 || c.tipo == 3;
            if (ve && c.cad == 0 && (cuerpo ? d < 1.1F : d < 12)) {
                c.estado = 2;
                c.t = 0;
                switch (c.tipo) {
                    case 0 -> {
                        sonido(ModSounds.CH_DISPARO, 0.75F);
                        if (rnd.nextFloat() < Math.max(0.25F, 0.85F - d * 0.06F)) recibir(3 + rnd.nextInt(10));
                        c.cad = 50 + rnd.nextInt(60);
                    }
                    case 1, 4 -> {
                        if (c.tipo == 1 && d < 1.2F) {
                            recibir(3 + rnd.nextInt(18));
                            c.cad = 50;
                        } else {
                            Bola bo = new Bola();
                            bo.tipo = c.tipo == 4 ? 1 : 0;
                            bo.x = c.x;
                            bo.y = c.y;
                            bo.vx = dx / d * 0.09F;
                            bo.vy = dy / d * 0.09F;
                            bo.dano = c.tipo == 4 ? 8 + rnd.nextInt(30) : 3 + rnd.nextInt(18);
                            bolas.add(bo);
                            sonido(ModSounds.MZ_BOLA, c.tipo == 4 ? 0.7F : 1F);
                            c.cad = c.tipo == 4 ? 40 + rnd.nextInt(30) : 70 + rnd.nextInt(50);
                        }
                    }
                    case 2 -> {
                        recibir(4 + rnd.nextInt(30));
                        sonido(ModSounds.MZ_GRUNIDO, 0.7F);
                        c.cad = 40;
                    }
                    case 3 -> {
                        recibir(3 + rnd.nextInt(20));
                        c.cad = 60;
                    }
                    default -> {}
                }
                continue;
            }
            // perseguir (el alma embiste de lejos)
            if (c.tipo == 3 && ve && c.cad == 0 && d < 6) vel = 0.09F;
            if (d > (cuerpo ? 0.7F : 2.5F) || !ve) {
                float mx = dx / d * vel, my = dy / d * vel;
                c.estado = 1;
                if (libre(c.x + mx, c.y, 0.25F, c)) c.x += mx;
                else if (libre(c.x, c.y + Math.signum(my) * vel, 0.25F, c)) c.y += Math.signum(my) * vel;
                if (libre(c.x, c.y + my, 0.25F, c)) c.y += my;
                else if (libre(c.x + Math.signum(mx) * vel, c.y, 0.25F, c)) c.x += Math.signum(mx) * vel;
                int ex = (int) (c.x + dx / d * 0.7F), ey = (int) (c.y + dy / d * 0.7F);
                if (mapa[ey][ex] == MURO_PUERTA) puertaT[ey][ex] = Math.max(puertaT[ey][ex], 120);
            }
        }
    }

    void pasoBolas() {
        for (Iterator<Bola> it = bolas.iterator(); it.hasNext(); ) {
            Bola bo = it.next();
            bo.t++;
            if (bo.explota) {
                if (++bo.et > 15) it.remove();
                continue;
            }
            bo.x += bo.vx;
            bo.y += bo.vy;
            if (pared((int) bo.x, (int) bo.y)) bo.explota = true;
            else if (Math.hypot(bo.x - px, bo.y - py) < 0.35F) {
                bo.explota = true;
                recibir(bo.dano);
            }
            if (bo.explota) sonido(ModSounds.CH_EXPLOSION, 1.6F);
        }
    }

    // ------------------------------------------------------------------ dibujo
    int luz(float d) {
        return Math.max(0, Math.min(LUCES - 1, (int) (d * 0.9F)));
    }

    @Override
    public void dibujar(Pantalla p) {
        byte[] buf = p.buf;
        float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
        float plx = -dy * 0.66F, ply = dx * 0.66F;
        int hor = VH / 2 + (int) (Math.sin(bobT * 0.18) * 1.5);
        // suelo y techo (proyección por filas)
        Sprite fs = suelos[SUELO_TECHO[nivel][0]], ts = suelos[SUELO_TECHO[nivel][1]];
        float rx0 = dx - plx, ry0 = dy - ply, rx1 = dx + plx, ry1 = dy + ply;
        for (int y = 0; y < VH; y++) {
            int dyp = y - hor;
            if (dyp == 0) continue;
            boolean suelo = dyp > 0;
            float dist = (VH / 2F) / Math.abs(dyp);
            float sx = (rx1 - rx0) * dist / VW, sy = (ry1 - ry0) * dist / VW;
            float fx = px + rx0 * dist, fy = py + ry0 * dist;
            byte[] tabla = LUZ[luz(dist)];
            Sprite tx = suelo ? fs : ts;
            int base = y * Pantalla.W;
            for (int x = 0; x < VW; x++) {
                int u = (int) ((fx - (float) Math.floor(fx)) * 64) & 63, v = (int) ((fy - (float) Math.floor(fy)) * 64) & 63;
                buf[base + x] = tabla[tx.px[v * 64 + u] & 63];
                fx += sx;
                fy += sy;
            }
        }
        // muros
        for (int x = 0; x < VW; x++) {
            float cam = 2F * x / VW - 1;
            float rdx = dx + plx * cam, rdy = dy + ply * cam;
            int mx = (int) px, my = (int) py;
            float ddx = Math.abs(1 / rdx), ddy = Math.abs(1 / rdy);
            int stx = rdx < 0 ? -1 : 1, sty = rdy < 0 ? -1 : 1;
            float sdx = rdx < 0 ? (px - mx) * ddx : (mx + 1 - px) * ddx, sdy = rdy < 0 ? (py - my) * ddy : (my + 1 - py) * ddy;
            int lado = 0, v = 0;
            float perp = 30, desp = 0;
            for (int k = 0; k < 64; k++) {
                if (sdx < sdy) {
                    sdx += ddx;
                    mx += stx;
                    lado = 0;
                } else {
                    sdy += ddy;
                    my += sty;
                    lado = 1;
                }
                if (mx < 0 || my < 0 || mx >= 24 || my >= 24) break;
                v = mapa[my][mx];
                if (v == 0) continue;
                if (v >= MURO_PUERTA && v <= PUERTA_AMARILLA) {
                    // puerta delgada en mitad de la casilla, que se desliza
                    float d = lado == 0 ? (sdx - ddx * 0.5F) : (sdy - ddy * 0.5F);
                    float wx = lado == 0 ? py + d * rdy : px + d * rdx;
                    wx -= (float) Math.floor(wx);
                    float limite = lado == 0 ? (sdy) : (sdx);
                    if (d > limite) continue;
                    if (wx < puerta[my][mx]) continue;
                    perp = d;
                    desp = wx - puerta[my][mx];
                    break;
                }
                perp = lado == 0 ? sdx - ddx : sdy - ddy;
                float wx = lado == 0 ? py + perp * rdy : px + perp * rdx;
                desp = wx - (float) Math.floor(wx);
                break;
            }
            zbuf[x] = perp;
            if (v == 0) continue;
            Sprite tex = muros[Math.min(muros.length - 1, v - 1)];
            int tu = (int) (desp * 64) & 63;
            if (lado == 0 && rdx > 0 || lado == 1 && rdy < 0) tu = 63 - tu;
            int h = (int) (VH / perp);
            int y0 = hor - h / 2, y1 = y0 + h;
            byte[] tabla = LUZ[Math.min(LUCES - 1, luz(perp) + lado)];
            float paso = 64F / h, tv = 0;
            int ya = Math.max(0, y0), yb = Math.min(VH, y1);
            tv = (ya - y0) * paso;
            for (int y = ya; y < yb; y++) {
                buf[y * Pantalla.W + x] = tabla[tex.px[((int) tv & 63) * 64 + tu] & 63];
                tv += paso;
            }
        }
        // sprites (de lejos a cerca)
        List<Object[]> lista = new ArrayList<>();
        for (Cosa c : cosas) {
            Sprite sp = spriteDe(c);
            if (sp != null) lista.add(new Object[]{c.x, c.y, sp, c.tipo == 4 ? 0.45F : c.tipo == 3 ? 0.35F : 0F, c.dolor > 0 && c.dolor % 2 == 0});
        }
        for (Bola bo : bolas) {
            String n = bo.tipo == 0 ? "bal1" : "bal2";
            Sprite sp = bo.explota ? s(n + (char) ('c' + Math.min(2, bo.et / 5)) + "0") : s(n + (bo.t / 6 % 2 == 0 ? "a0" : "b0"));
            lista.add(new Object[]{bo.x, bo.y, sp, 0.35F, false});
        }
        for (float[] so : soplos) {
            Sprite sp = so[3] == 0 ? s("puff" + (char) ('a' + Math.min(3, (int) so[2] / 4)) + "0") : s("blud" + (char) ('a' + Math.min(2, (int) so[2] / 5)) + "0");
            lista.add(new Object[]{so[0], so[1], sp, 0.4F, false});
        }
        lista.sort((a, c) -> Float.compare(dist2((float) c[0], (float) c[1]), dist2((float) a[0], (float) a[1])));
        float inv = 1F / (plx * dy - dx * ply);
        for (Object[] o : lista) dibujarSprite(p, (float) o[0], (float) o[1], (Sprite) o[2], (float) o[3], (boolean) o[4], dx, dy, plx, ply, inv, hor);
        arma(p);
        // destellos de daño y de recoger
        if (dolorT > 0 || muerto) for (int y = (t % 2); y < VH; y += 2) for (int x = (y / 2) % 2; x < VW; x += 2) buf[y * Pantalla.W + x] = 9;
        else if (cogerT > 0) for (int y = 0; y < VH; y += 3) for (int x = (y / 3) % 3; x < VW; x += 3) buf[y * Pantalla.W + x] = 20;
        hud(p);
    }

    float dist2(float x, float y) {
        return (x - px) * (x - px) + (y - py) * (y - py);
    }

    Sprite spriteDe(Cosa c) {
        int f = (c.t / 10) % 4;
        String[] nom = {"poss", "troo", "sarg", "skul", "head"};
        if (c.tipo < 10) {
            String n = nom[c.tipo];
            if (c.estado == 4) return s(n + switch (c.tipo) {
                case 0 -> "l0";
                case 1 -> "m0";
                case 2 -> "n0";
                case 3 -> "k0";
                default -> "l0";
            });
            if (c.estado == 3) {
                char[][] m = {{'h', 'i', 'j', 'k', 'l'}, {'i', 'j', 'k', 'l', 'm'}, {'i', 'j', 'k', 'l', 'm'}, {'f', 'g', 'h', 'i', 'j'}, {'g', 'h', 'i', 'j', 'k'}};
                return s(n + m[c.tipo][Math.min(4, c.t / 6)] + "0");
            }
            if (c.dolor > 0) return s(n + (c.tipo == 0 ? "g1" : c.tipo == 3 ? "e1" : c.tipo == 4 ? "f1" : "h1"));
            if (c.estado == 2) return s(n + switch (c.tipo) {
                case 0 -> c.t < 6 ? "f1" : "e1";
                case 1, 2 -> c.t < 8 ? "e1" : c.t < 16 ? "f1" : "g1";
                case 3 -> c.t < 12 ? "c1" : "d1";
                default -> c.t < 8 ? "b1" : c.t < 16 ? "c1" : "d1";
            });
            if (c.tipo == 3) return s(n + (c.t / 6 % 2 == 0 ? "a1" : "b1"));
            if (c.tipo == 4) return s(n + "a1");
            if (!c.despierto) return s(n + "a1");
            return s(n + (char) ('a' + f) + "1");
        }
        return switch (c.tipo) {
            case 10 -> s("clipa0");
            case 11 -> s("shela0");
            case 12 -> s("media0");
            case 13 -> s("stima0");
            case 14 -> s("shota0");
            case 15 -> s("arm1" + (t / 15 % 2 == 0 ? "a0" : "b0"));
            case 16 -> s("bon1" + (char) ('a' + (t / 8) % 4) + "0");
            case 17 -> s("rkeya0");
            case 18 -> s("bkeya0");
            case 19 -> s("ykeya0");
            case 30 -> s("colua0");
            case 31 -> s("tred" + (char) ('a' + (t / 6) % 4) + "0");
            case 32 -> c.estado >= 3 ? (c.t < 25 ? s("bexp" + (char) ('a' + Math.min(4, c.t / 5)) + "0") : null) : s("bar1" + (t / 15 % 2 == 0 ? "a0" : "b0"));
            default -> null;
        };
    }

    void dibujarSprite(Pantalla p, float sx, float sy, Sprite sp, float flota, boolean blanco, float dx, float dy, float plx, float ply, float inv, int hor) {
        float rx = sx - px, ry = sy - py;
        float tx = inv * (dy * rx - dx * ry), ty = inv * (-ply * rx + plx * ry);
        if (ty < 0.15F) return;
        int cx = (int) ((VW / 2F) * (1 + tx / ty));
        float escala = (VH / ty) / 80F;          // 80 texels de sprite = altura de un muro
        int w = (int) (sp.w * escala), h = (int) (sp.h * escala);
        if (w <= 0 || h <= 0) return;
        int suelo = hor + (int) (VH / ty / 2) - (int) (flota * VH / ty);
        int x0 = cx - w / 2, y0 = suelo - h;
        byte[] tabla = LUZ[luz(ty)];
        for (int x = Math.max(0, x0); x < Math.min(VW, x0 + w); x++) {
            if (ty >= zbuf[x]) continue;
            int u = (x - x0) * sp.w / w;
            for (int y = Math.max(0, y0); y < Math.min(VH, y0 + h); y++) {
                int v = (y - y0) * sp.h / h;
                byte c = sp.px[v * sp.w + u];
                if (c == (byte) 255) continue;
                p.buf[y * Pantalla.W + x] = blanco ? 7 : tabla[c & 63];
            }
        }
    }

    void arma(Pantalla p) {
        if (muerto) return;
        String n;
        String fl = null;
        if (arma == 0) n = dispT > 0 ? "pungb0" : "punga0";
        else if (arma == 1) {
            n = dispT > 6 ? "pisgb0" : dispT > 3 ? "pisgc0" : dispT > 0 ? "pisgd0" : "pisga0";
            if (dispT > 7) fl = "pisfa0";
        } else {
            n = cad > 40 ? "shtga0" : cad > 30 ? "shtgb0" : cad > 20 ? "shtgc0" : cad > 10 ? "shtgd0" : "shtga0";
            if (cad > 44) fl = cad > 47 ? "shtfa0" : "shtfb0";
        }
        Sprite sp = s(n);
        int bx = (int) (Math.sin(bobT * 0.09) * 4), by = (int) (Math.abs(Math.cos(bobT * 0.09)) * 3);
        float k = 0.8F;
        int w = (int) (Math.min(sp.w, 120) * k), h = (int) (sp.h * k);
        int x0 = VW / 2 - w / 2 + 12 + bx, y0 = VH - h + by + 2;
        if (fl != null) {
            Sprite f = s(fl);
            blit(p, f, VW / 2 - (int) (f.w * k) / 2 + 10 + bx, y0 - (int) (f.h * k) + 14, k);
        }
        blit(p, sp, x0, y0, k);
    }

    void blit(Pantalla p, Sprite sp, int x0, int y0, float k) {
        int w = (int) (sp.w * k), h = (int) (sp.h * k);
        for (int y = 0; y < h; y++) {
            int yy = y0 + y;
            if (yy < 0 || yy >= VH) continue;
            int v = (int) (y / k);
            for (int x = 0; x < w; x++) {
                int xx = x0 + x;
                if (xx < 0 || xx >= VW) continue;
                byte c = sp.px[v * sp.w + Math.min(sp.w - 1, (int) (x / k))];
                if (c != (byte) 255) p.buf[yy * Pantalla.W + xx] = c;
            }
        }
    }

    void hud(Pantalla p) {
        p.rect(0, VH, Pantalla.W, HUD, 2);
        p.rect(0, VH, Pantalla.W, 1, 5);
        for (int x = 0; x < Pantalla.W; x += 64) p.rect(x, VH + 1, 1, HUD, 1);
        p.texto(I18n.get("mazmorra.miscelanea.salud"), 6, VH + 4, 5);
        p.texto(salud + "%", 6, VH + 16, salud > 50 ? 29 : salud > 25 ? 19 : 11);
        p.texto(I18n.get("mazmorra.miscelanea.armadura"), 70, VH + 4, 5);
        p.texto(armadura + "%", 70, VH + 16, 39);
        String mun = arma == 0 ? "—" : arma == 1 ? Integer.toString(balas) : Integer.toString(cartuchos);
        p.texto(I18n.get("mazmorra.miscelanea.arma." + arma), 134, VH + 4, 5);
        p.texto(mun, 134, VH + 16, 20);
        int[] cols = {11, 36, 19};
        for (int k = 0; k < 3; k++) {
            p.rect(200 + k * 12, VH + 6, 9, 9, (llaves >> k & 1) != 0 ? cols[k] : 1);
            p.marco(200 + k * 12, VH + 6, 9, 9, 4);
        }
        p.texto(Integer.toString(puntos), 200, VH + 18, 7);
        if (avisoT > 0) p.textoC(aviso, 128, 6, 20);
        if (completado) {
            p.rect(40, 60, 176, 36, 0);
            p.marco(40, 60, 176, 36, 28);
            p.textoC(I18n.get("mazmorra.miscelanea.completado"), 128, 66, 29);
            p.textoC(I18n.get("consola.miscelanea.puntos") + " " + puntos, 128, 80, 7);
        }
    }
}

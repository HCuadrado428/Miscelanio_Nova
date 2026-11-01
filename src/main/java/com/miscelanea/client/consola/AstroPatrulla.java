package com.miscelanea.client.consola;

import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Astro Patrulla: matamarcianos vertical, exclusivo de la recreativa (paleta de 16 colores).
 * A (mantenido): disparar. B: bomba. Cruceta: mover.
 */
public class AstroPatrulla extends JuegoGB {
    public static final int[] PALETA = {0xFF05060F, 0xFF141A3A, 0xFF2E4FB8, 0xFF5FD8F0, 0xFFF4F4FF, 0xFF8890A8, 0xFF404660, 0xFFE83838,
            0xFFF88C28, 0xFFF8E048, 0xFF48D058, 0xFF1C7030, 0xFF9C48D8, 0xFFF870B8, 0xFF8A5030, 0xFFFFF8C0};

    static final String[] NAVE = {
            ".....3.....", "....434....", "....444....", "...44244...", "...42224...", "..4442444..",
            ".455424554.", "45554445554", "45.5...5.54", "4.........4"};
    static final String[] DARDO = {"7.......7", "77.....77", ".7744477.", "..74947..", "...777...", "....7...."};
    static final String[] DARDO2 = {".7.....7.", "77.....77", ".7744477.", "..74947..", "...777...", "....7...."};
    static final String[] PICADO = {"..c...c..", ".ccc.ccc.", "cc4ccc4cc", "ccccccccc", ".c.ccc.c.", "c..c.c..c"};
    static final String[] TORRETA = {"...66666...", "..6555556..", ".655999556.", "65559995556", "65555555556", ".666e6e666.", "..e.....e.."};
    static final String[] BALA_E = {".d.", "ddd", ".d."};
    static final String[] CAP_P = {".222.", "24442", "24242", "24422", ".222."};
    static final String[] CAP_B = {".777.", "74447", "74747", "74447", ".777."};
    static final String[] CAP_V = {".aaa.", "a4a4a", "a444a", "aa4aa", ".aaa."};

    static final class Enemigo {
        int tipo;      // 0 dardo, 1 picado, 2 torreta, 3 jefe
        float x, y, vx, vy, base;
        int hp, hpMax, t, golpe;
    }

    float nx, ny;
    int vidas, bombas, poder, invul, cad, ola, olaT, flash, muerto, aviso, cadena, cadenaT;
    final List<Enemigo> enemigos = new ArrayList<>();
    final List<float[]> balas = new ArrayList<>();      // propias: x, y, vx, vy
    final List<float[]> balasE = new ArrayList<>();     // enemigas
    final List<float[]> caps = new ArrayList<>();       // x, y, tipo
    final List<float[]> boom = new ArrayList<>();       // x, y, t, tam
    final float[][] estrellas = new float[60][3];
    int pendientes, spawnT, tick;

    public AstroPatrulla(Botones b) {
        super(b);
    }

    @Override
    public void reiniciar() {
        nx = 74;
        ny = 118;
        vidas = 3;
        bombas = 3;
        poder = 1;
        invul = 120;
        puntos = 0;
        fin = false;
        ola = 0;
        olaT = 60;
        muerto = 0;
        enemigos.clear();
        balas.clear();
        balasE.clear();
        caps.clear();
        boom.clear();
        for (float[] s : estrellas) {
            s[0] = rnd.nextInt(Lcd.W);
            s[1] = rnd.nextInt(Lcd.H);
            s[2] = rnd.nextInt(3);
        }
    }

    // ------------------------------------------------------------------ oleadas
    void nuevaOla() {
        ola++;
        if (ola % 4 == 0) {
            Enemigo j = new Enemigo();
            j.tipo = 3;
            j.x = 64;
            j.y = -24;
            j.vx = 0.5F;
            j.hp = j.hpMax = 70 + ola * 12;
            enemigos.add(j);
            aviso = 120;
            sonido(ModSounds.GB_INICIO, 0.6F);
            pendientes = 0;
            return;
        }
        pendientes = 6 + ola * 2;
        spawnT = 0;
    }

    void soltar() {
        Enemigo e = new Enemigo();
        int r = rnd.nextInt(10);
        e.tipo = ola >= 2 && r < 3 ? 1 : (ola >= 3 && r == 9 ? 2 : 0);
        e.x = 10 + rnd.nextInt(Lcd.W - 30);
        e.base = e.x;
        e.y = -10;
        e.vy = e.tipo == 2 ? 0.35F : (0.7F + ola * 0.05F);
        e.hp = e.hpMax = e.tipo == 0 ? 1 : e.tipo == 1 ? 2 : 6;
        e.t = rnd.nextInt(100);
        enemigos.add(e);
    }

    // ------------------------------------------------------------------ lógica
    @Override
    public void paso() {
        tick++;
        for (float[] s : estrellas) {
            s[1] += 0.3F + s[2] * 0.55F;
            if (s[1] > Lcd.H) {
                s[1] = -1;
                s[0] = rnd.nextInt(Lcd.W);
            }
        }
        if (flash > 0) flash--;
        if (aviso > 0) aviso--;
        if (cadenaT > 0 && --cadenaT == 0) cadena = 0;
        for (Iterator<float[]> it = boom.iterator(); it.hasNext(); ) if (++it.next()[2] > 22) it.remove();

        if (muerto > 0) {
            if (++muerto > 90) {
                muerto = 0;
                if (vidas <= 0) {
                    fin = true;
                    return;
                }
                nx = 74;
                ny = 118;
                invul = 150;
                balasE.clear();
            }
        } else {
            float v = 1.7F;
            if (b.mantiene(Botones.IZQ)) nx -= v;
            if (b.mantiene(Botones.DER)) nx += v;
            if (b.mantiene(Botones.ARRIBA)) ny -= v;
            if (b.mantiene(Botones.ABAJO)) ny += v;
            nx = Math.max(0, Math.min(Lcd.W - 11, nx));
            ny = Math.max(14, Math.min(Lcd.H - 11, ny));
            if (invul > 0) invul--;
            if (cad > 0) cad--;
            if (b.mantiene(Botones.A) && cad == 0) {
                cad = 7;
                float cx = nx + 5;
                balas.add(new float[]{cx, ny - 2, 0, -4.5F});
                if (poder >= 2) {
                    balas.add(new float[]{cx - 4, ny + 2, 0, -4.5F});
                    balas.add(new float[]{cx + 4, ny + 2, 0, -4.5F});
                }
                if (poder >= 3) {
                    balas.add(new float[]{cx - 3, ny, -1.2F, -4.2F});
                    balas.add(new float[]{cx + 3, ny, 1.2F, -4.2F});
                }
                if (tick % 2 == 0) sonido(ModSounds.GB_DISPARO, 1.5F);
            }
            if (b.pulsa(Botones.B) && bombas > 0) {
                bombas--;
                flash = 24;
                balasE.clear();
                for (Enemigo e : enemigos) danar(e, e.tipo == 3 ? 25 : 99);
                sonido(ModSounds.GB_EXPLOSION, 0.5F);
            }
        }

        // oleadas
        boolean jefe = enemigos.stream().anyMatch(e -> e.tipo == 3);
        if (pendientes > 0 && ++spawnT >= Math.max(14, 40 - ola * 2)) {
            spawnT = 0;
            pendientes--;
            soltar();
        }
        if (pendientes == 0 && enemigos.isEmpty() && !jefe && --olaT <= 0) {
            olaT = 90;
            nuevaOla();
        }

        // enemigos
        for (Iterator<Enemigo> it = enemigos.iterator(); it.hasNext(); ) {
            Enemigo e = it.next();
            e.t++;
            if (e.golpe > 0) e.golpe--;
            switch (e.tipo) {
                case 0 -> {
                    e.y += e.vy;
                    e.x = e.base + (float) Math.sin(e.t * 0.05) * 18;
                }
                case 1 -> {
                    if (e.t < 60) e.y += 0.9F;
                    else if (e.t < 80) e.y += 0.1F;
                    else {
                        if (e.t == 80) {
                            float dx = nx - e.x, dy = ny - e.y, l = (float) Math.max(1, Math.hypot(dx, dy));
                            e.vx = dx / l * 2.4F;
                            e.vy = dy / l * 2.4F;
                        }
                        e.x += e.vx;
                        e.y += e.vy;
                    }
                }
                case 2 -> {
                    e.y += e.vy;
                    if (e.t % Math.max(40, 90 - ola * 4) == 0) apuntar(e.x + 5, e.y + 7, 1.4F);
                }
                case 3 -> {
                    if (e.y < 18) e.y += 0.4F;
                    else {
                        e.x += e.vx;
                        if (e.x < 4 || e.x > Lcd.W - 36) e.vx = -e.vx;
                        int ciclo = e.t % 240;
                        if (ciclo < 120 && ciclo % 30 == 0)
                            for (int k = -3; k <= 3; k++) {
                                double a = Math.PI / 2 + k * 0.22;
                                balasE.add(new float[]{e.x + 16, e.y + 16, (float) Math.cos(a) * 1.3F, (float) Math.sin(a) * 1.3F});
                            }
                        if (ciclo >= 140 && ciclo % 12 == 0) apuntar(e.x + 16, e.y + 16, 2.0F);
                    }
                }
            }
            if (e.tipo == 0 && rnd.nextInt(Math.max(90, 260 - ola * 15)) == 0) apuntar(e.x + 4, e.y + 5, 1.2F);
            if (e.y > Lcd.H + 10 || e.x < -20 || e.x > Lcd.W + 20) {
                it.remove();
                continue;
            }
            if (muerto == 0 && invul == 0) {
                int w = e.tipo == 3 ? 32 : e.tipo == 2 ? 11 : 9, h = e.tipo == 3 ? 18 : 6;
                if (choca(nx + 2, ny + 2, 7, 7, e.x, e.y, w, h)) morir();
            }
        }

        // balas propias
        for (Iterator<float[]> it = balas.iterator(); it.hasNext(); ) {
            float[] s = it.next();
            s[0] += s[2];
            s[1] += s[3];
            if (s[1] < -6 || s[0] < -4 || s[0] > Lcd.W + 4) {
                it.remove();
                continue;
            }
            for (Enemigo e : enemigos) {
                int w = e.tipo == 3 ? 32 : e.tipo == 2 ? 11 : 9, h = e.tipo == 3 ? 18 : 6;
                if (e.hp > 0 && choca(s[0], s[1], 1, 4, e.x, e.y, w, h)) {
                    danar(e, 1);
                    it.remove();
                    break;
                }
            }
        }
        enemigos.removeIf(e -> e.hp <= 0);

        // balas enemigas
        for (Iterator<float[]> it = balasE.iterator(); it.hasNext(); ) {
            float[] s = it.next();
            s[0] += s[2];
            s[1] += s[3];
            if (s[1] > Lcd.H + 4 || s[1] < -8 || s[0] < -4 || s[0] > Lcd.W + 4) {
                it.remove();
                continue;
            }
            if (muerto == 0 && invul == 0 && choca(s[0], s[1], 3, 3, nx + 3, ny + 3, 5, 5)) {
                it.remove();
                morir();
            }
        }

        // cápsulas
        for (Iterator<float[]> it = caps.iterator(); it.hasNext(); ) {
            float[] c = it.next();
            c[1] += 0.6F;
            if (c[1] > Lcd.H) {
                it.remove();
                continue;
            }
            if (muerto == 0 && choca(c[0], c[1], 5, 5, nx, ny, 11, 10)) {
                switch ((int) c[2]) {
                    case 0 -> poder = Math.min(3, poder + 1);
                    case 1 -> bombas = Math.min(5, bombas + 1);
                    default -> vidas = Math.min(5, vidas + 1);
                }
                puntos += 200;
                sonido(ModSounds.GB_LINEA, 1.2F);
                it.remove();
            }
        }
    }

    void apuntar(float x, float y, float v) {
        float dx = nx + 5 - x, dy = ny + 5 - y, l = (float) Math.max(1, Math.hypot(dx, dy));
        balasE.add(new float[]{x, y, dx / l * v, dy / l * v});
    }

    void danar(Enemigo e, int d) {
        e.hp -= d;
        e.golpe = 4;
        if (e.hp > 0) return;
        int base = switch (e.tipo) {
            case 0 -> 100;
            case 1 -> 150;
            case 2 -> 300;
            default -> 5000;
        };
        cadena = Math.min(9, cadena + 1);
        cadenaT = 90;
        puntos += base * Math.max(1, cadena / 3 + 1);
        float cx = e.x + (e.tipo == 3 ? 16 : 5), cy = e.y + (e.tipo == 3 ? 9 : 3);
        boom.add(new float[]{cx, cy, 0, e.tipo == 3 ? 18 : 7});
        if (e.tipo == 3) {
            for (int k = 0; k < 6; k++) boom.add(new float[]{cx + rnd.nextInt(30) - 15, cy + rnd.nextInt(16) - 8, -k * 4, 9});
            caps.add(new float[]{cx, cy, 0});
            caps.add(new float[]{cx + 8, cy, 1});
            flash = 20;
        }
        sonido(ModSounds.GB_EXPLOSION, e.tipo == 3 ? 0.6F : 1.4F);
        int r = rnd.nextInt(100);
        if (r < (e.tipo == 2 ? 30 : 7)) caps.add(new float[]{cx - 2, cy, r < 3 && e.tipo != 0 ? 2 : (r % 3 == 0 ? 1 : 0)});
    }

    void morir() {
        vidas--;
        muerto = 1;
        poder = Math.max(1, poder - 1);
        cadena = 0;
        boom.add(new float[]{nx + 5, ny + 5, 0, 12});
        boom.add(new float[]{nx + 2, ny + 8, -6, 8});
        sonido(ModSounds.GB_EXPLOSION, 0.8F);
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void dibujar(Lcd l) {
        l.rect(0, 0, Lcd.W, Lcd.H, flash > 0 && (flash / 3) % 2 == 0 ? 6 : 0);
        // planeta lejano
        int py = (int) ((tick * 0.08F) % (Lcd.H + 80)) - 40;
        for (int j = -14; j <= 14; j++) {
            int w = (int) Math.sqrt(196 - j * j);
            l.rect(120 - w, py + j, w * 2, 1, (j + 14) / 7 % 2 == 0 ? 12 : 1);
        }
        l.rect(100, py - 2, 40, 1, 13);
        for (float[] s : estrellas) l.px((int) s[0], (int) s[1], s[2] == 2 ? 4 : s[2] == 1 ? 5 : 6);

        for (float[] c : caps) {
            String[] sp = c[2] == 0 ? CAP_P : c[2] == 1 ? CAP_B : CAP_V;
            if ((tick / 6) % 2 == 0 || c[1] < Lcd.H - 30) l.sprite(sp, (int) c[0], (int) c[1]);
        }
        for (Enemigo e : enemigos) {
            int x = Math.round(e.x), y = Math.round(e.y);
            switch (e.tipo) {
                case 0 -> l.sprite((e.t / 8) % 2 == 0 ? DARDO : DARDO2, x, y);
                case 1 -> l.sprite(PICADO, x, y);
                case 2 -> l.sprite(TORRETA, x, y);
                default -> jefe(l, e, x, y);
            }
            if (e.golpe > 0 && e.tipo != 3) l.rect(x + 3, y + 2, 3, 2, 4);
        }
        for (float[] s : balas) {
            l.rect(Math.round(s[0]), Math.round(s[1]), 1, 4, 9);
            l.px(Math.round(s[0]), Math.round(s[1]), 15);
        }
        for (float[] s : balasE) l.sprite(BALA_E, Math.round(s[0]), Math.round(s[1]));
        if (muerto == 0 && (invul == 0 || (invul / 4) % 2 == 0)) {
            int x = Math.round(nx), y = Math.round(ny);
            l.sprite(NAVE, x, y);
            int fuego = (tick / 3) % 3;
            l.rect(x + 2, y + 10, 1, 1 + fuego, fuego == 0 ? 9 : 8);
            l.rect(x + 8, y + 10, 1, 1 + fuego, fuego == 0 ? 9 : 8);
            l.rect(x + 5, y + 9, 1, 2 + fuego, 7);
        }
        for (float[] e : boom) {
            if (e[2] < 0) continue;
            float k = e[2] / 22F;
            int r = (int) (e[3] * (0.3F + k));
            int col = k < 0.25F ? 15 : k < 0.5F ? 9 : k < 0.75F ? 8 : 7;
            for (int j = -r; j <= r; j++) {
                int w = (int) Math.sqrt(r * r - j * j);
                int hueco = k > 0.4F ? (int) (w * (k - 0.3F)) : 0;
                l.rect((int) e[0] - w, (int) e[1] + j, w - hueco, 1, col);
                l.rect((int) e[0] + hueco, (int) e[1] + j, w - hueco, 1, col);
            }
        }
        // marcador
        l.rect(0, 0, Lcd.W, 11, 1);
        l.texto(cifras(puntos, 7), 2, 2, 4);
        for (int i = 0; i < vidas - (muerto > 0 ? 0 : 1) && i < 5; i++) l.sprite(new String[]{"..4..", ".444.", "44444"}, 118 + i * 6, 4);
        for (int i = 0; i < bombas; i++) l.sprite(new String[]{".7.", "777", ".7."}, 88 + i * 5, 4);
        if (cadena >= 3) l.textoD("x" + (cadena / 3 + 1), 158, 13, 9);
        if (aviso > 0 && (aviso / 10) % 2 == 0) {
            l.rect(0, 60, Lcd.W, 18, 7);
            l.textoC(I18n.get("consola.miscelanea.aviso_jefe"), 80, 65, 15);
        }
        if (ola > 0 && olaT > 30 && pendientes == 0 && enemigos.isEmpty()) l.textoC(I18n.get("consola.miscelanea.oleada") + " " + (ola + 1), 80, 66, 3);
    }

    void jefe(Lcd l, Enemigo e, int x, int y) {
        boolean g = e.golpe > 0;
        // casco
        for (int j = 0; j < 18; j++) {
            int w = (int) (16 * Math.sqrt(1 - Math.pow((j - 7) / 10.0, 2)));
            l.rect(x + 16 - w, y + j, w * 2, 1, g ? 4 : (j < 6 ? 5 : 6));
        }
        l.rect(x + 2, y + 8, 28, 3, g ? 4 : 14);
        for (int k = 0; k < 4; k++) l.rect(x + 4 + k * 7, y + 14, 3, 5, 6);
        int pulso = (e.t / 6) % 2;
        l.rect(x + 12, y + 3, 8, 6, pulso == 0 ? 7 : 13);
        l.rect(x + 14, y + 5, 4, 2, 15);
        l.rect(x, y + 6, 3, 8, 12);
        l.rect(x + 29, y + 6, 3, 8, 12);
        // barra de vida arriba
        int ancho = (int) (100F * e.hp / e.hpMax);
        l.rect(30, 13, 100, 3, 6);
        l.rect(30, 13, ancho, 3, 7);
    }
}

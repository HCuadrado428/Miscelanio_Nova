package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Botones;
import com.miscelanea.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Pistolero de Chatarra: recreativa de pistola de luz. Robots-zombi de chatarra salen de detrás de las cajas del
 * desguace y avanzan hacia ti; apuntas con el ratón y disparas con el clic. Cabeza = el doble de puntos. Recarga
 * con clic derecho, con B o disparando fuera de la pantalla. Latas al aire y bidones explosivos dan extra. Tres
 * oleadas y luego la Grúa, el jefe, al que solo se le hace daño en el núcleo. Si te alcanzan, pierdes una vida.
 */
public class PistolaChatarra extends JuegoArcade {
    static final int HORIZONTE = 96, SUELO = 176, BALAS = 6;
    static final class Robot {
        double x, z, vel;
        int vida, tipo, t, muere = -1, golpe;
        boolean jefe;
    }
    static final class Lata {
        double x, y, vx, vy;
        boolean bidon;
        int vida = 1, explota = -1;
    }
    final List<Robot> robots = new ArrayList<>();
    final List<Lata> latas = new ArrayList<>();
    int balas, vidas, oleada, porSalir, espera, recargando, flash, danio, t, combo, aciertos, tiros, mensajeT;
    String mensaje = "";
    int fx = -100, fy = -100;
    boolean jefeVivo;

    public PistolaChatarra(Botones[] b, long semilla, int jugadores) {
        super(b, semilla, 1);
    }

    @Override
    public SoundEvent musica() {
        return ModSounds.PI_MUSICA.get();
    }

    @Override
    public void reiniciar() {
        puntos = 0;
        vidas = 3;
        balas = BALAS;
        oleada = 0;
        robots.clear();
        latas.clear();
        fin = false;
        siguienteOleada();
    }

    void siguienteOleada() {
        oleada++;
        porSalir = oleada % 4 == 0 ? 0 : 6 + oleada * 3;
        espera = 90;
        if (oleada % 4 == 0) {
            Robot r = new Robot();
            r.jefe = true;
            r.x = 128;
            r.z = 1.0;
            r.vel = 0.0007 + oleada * 0.00005;
            r.vida = 26 + oleada * 2;
            robots.add(r);
            jefeVivo = true;
            avisar("¡LA GRÚA!");
        } else avisar("OLEADA " + oleada);
    }

    void avisar(String s) {
        mensaje = s;
        mensajeT = 120;
    }

    static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    static double escala(double z) {
        return 0.3 + (1 - z) * 1.1;
    }

    static int pie(double z) {
        return (int) (HORIZONTE + 8 + (1 - z) * (SUELO - HORIZONTE - 14));
    }

    @Override
    public void paso() {
        t++;
        if (mensajeT > 0) mensajeT--;
        if (flash > 0) flash--;
        if (danio > 0) danio--;
        // recarga
        if (recargando > 0 && --recargando == 0) {
            balas = BALAS;
            sonido(ModSounds.PI_RECARGA, 1F);
        }
        if (recargas > 0 || b[0].pulsa(Botones.B)) {
            recargas = 0;
            if (balas < BALAS && recargando == 0) {
                recargando = 22;
                sonido(ModSounds.PI_RECARGA, 0.8F);
            }
        }
        while (disparos > 0) {
            disparos--;
            disparar(ratonX, ratonY);
        }
        // salida de robots
        if (espera > 0) espera--;
        else if (porSalir > 0) {
            Robot r = new Robot();
            int cob = rnd.nextInt(3);
            r.x = new int[]{50, 128, 206}[cob] + rnd.nextInt(30) - 15;
            r.z = 0.95;
            int k = rnd.nextInt(10);
            r.tipo = k < 6 || oleada < 2 ? 0 : k < 9 ? 1 : 2;
            r.vida = r.tipo == 2 ? 4 : r.tipo == 1 ? 1 : 2;
            r.vel = (r.tipo == 1 ? 0.0042 : r.tipo == 2 ? 0.0016 : 0.0026) * (1 + oleada * 0.08);
            robots.add(r);
            porSalir--;
            espera = Math.max(25, 85 - oleada * 8) + rnd.nextInt(30);
            if (rnd.nextInt(5) == 0) lanzarLata();
            sonido(ModSounds.PI_ROBOT, 0.8F + rnd.nextFloat() * 0.4F);
        }
        // movimiento
        for (int i = robots.size() - 1; i >= 0; i--) {
            Robot r = robots.get(i);
            r.t++;
            if (r.golpe > 0) r.golpe--;
            if (r.muere >= 0) {
                if (++r.muere > 30) robots.remove(i);
                continue;
            }
            r.z -= r.golpe > 0 ? r.vel * 0.2 : r.vel;
            r.x += (128 - r.x) * 0.002 + Math.sin(r.t * 0.05 + i) * 0.15;
            if (r.z <= 0) {
                vidas--;
                danio = 30;
                sonido(ModSounds.PI_DANO, 1F);
                if (r.jefe) r.z = 0.5;
                else robots.remove(i);
                if (vidas <= 0) {
                    fin = true;
                    mensajeFin = "FIN · OLEADA " + oleada;
                    return;
                }
            }
        }
        for (int i = latas.size() - 1; i >= 0; i--) {
            Lata l = latas.get(i);
            if (l.explota >= 0) {
                if (++l.explota > 24) latas.remove(i);
                continue;
            }
            if (l.bidon && ++l.vida > 600) {
                latas.remove(i);
                continue;
            }
            l.x += l.vx;
            l.y += l.vy;
            if (!l.bidon) l.vy += 0.06;
            if (l.y > SUELO + 10 || l.x < -20 || l.x > 276) latas.remove(i);
        }
        if (!jefeVivo && porSalir == 0 && robots.isEmpty() && espera <= 0) {
            int bonus = tiros > 0 ? 1000 * aciertos / tiros : 0;
            puntos += bonus;
            avisar("¡OLEADA SUPERADA! +" + bonus);
            aciertos = tiros = 0;
            siguienteOleada();
        }
        if (oleada > 1 && t % 600 == 0 && latas.stream().filter(x -> x.bidon).count() < 2) {
            Lata l = new Lata();
            l.bidon = true;
            l.x = 30 + rnd.nextInt(196);
            l.y = pie(0.6) - 2;
            latas.add(l);
        }
    }

    void lanzarLata() {
        Lata l = new Lata();
        boolean izq = rnd.nextBoolean();
        l.x = izq ? -8 : 264;
        l.y = 120 + rnd.nextInt(30);
        l.vx = (izq ? 1 : -1) * (1.2 + rnd.nextFloat());
        l.vy = -3.2 - rnd.nextFloat();
        latas.add(l);
    }

    void disparar(int x, int y) {
        if (recargando > 0) return;
        if (x < 0 || x >= 256 || y < 0 || y >= SUELO) {
            recargas++;
            return;
        }
        if (balas <= 0) {
            sonido(ModSounds.PI_VACIO, 1F);
            return;
        }
        balas--;
        tiros++;
        flash = 4;
        fx = x;
        fy = y;
        sonido(ModSounds.PI_DISPARO, 0.9F + rnd.nextFloat() * 0.2F);
        // latas y bidones primero (están delante)
        for (Lata l : latas) {
            if (l.explota >= 0) continue;
            int r = l.bidon ? 7 : 4;
            if (Math.abs(x - l.x) <= r && Math.abs(y - l.y) <= (l.bidon ? 10 : 5)) {
                l.explota = 0;
                aciertos++;
                if (l.bidon) {
                    puntos += 200;
                    sonido(ModSounds.PI_EXPLOSION, 1F);
                    for (Robot rb : robots)
                        if (rb.muere < 0 && Math.abs(rb.x - l.x) < 50 && !rb.jefe) matar(rb, 150);
                } else {
                    puntos += 500;
                    avisar("¡LATA! +500");
                    sonido(ModSounds.PI_IMPACTO, 1.6F);
                }
                return;
            }
        }
        // el robot más cercano que esté debajo del punto
        Robot mejor = null;
        boolean cabeza = false;
        for (Robot r : robots) {
            if (r.muere >= 0) continue;
            double s = escala(r.z) * (r.jefe ? 2.4 : r.tipo == 2 ? 1.3 : 1);
            int py = pie(r.z);
            double ancho = 12 * s, alto = 40 * s;
            if (r.jefe) {
                // solo el núcleo
                double ny = py - alto * 0.55;
                if (Math.hypot(x - r.x, y - ny) <= 5 * s && (mejor == null || r.z < mejor.z)) {
                    mejor = r;
                    cabeza = true;
                }
                continue;
            }
            if (Math.abs(x - r.x) <= ancho / 2 + 1 && y <= py && y >= py - alto && (mejor == null || r.z < mejor.z)) {
                mejor = r;
                cabeza = y < py - alto * 0.78;
            }
        }
        if (mejor == null) {
            combo = 0;
            return;
        }
        aciertos++;
        combo++;
        sonido(ModSounds.PI_IMPACTO, cabeza ? 1.3F : 1F);
        if (mejor.jefe) {
            mejor.vida--;
            mejor.golpe = 8;
            puntos += 50;
            if (mejor.vida <= 0) {
                matar(mejor, 5000);
                jefeVivo = false;
                avisar("¡GRÚA DESGUAZADA! +5000");
                sonido(ModSounds.PI_EXPLOSION, 0.7F);
            }
            return;
        }
        mejor.vida -= cabeza ? 3 : 1;
        mejor.golpe = 10;
        if (mejor.vida <= 0) matar(mejor, (mejor.tipo == 2 ? 300 : mejor.tipo == 1 ? 150 : 100) * (cabeza ? 2 : 1) + Math.min(10, combo) * 10);
    }

    void matar(Robot r, int pts) {
        r.muere = 0;
        puntos += pts;
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void dibujar(Pantalla p) {
        // cielo de noche, luna y montañas de chatarra
        int[] cielo = {33, 41, 42, 45};
        for (int y = 0; y < HORIZONTE; y++) {
            double v = y / (double) HORIZONTE * 3.2;
            int k = (int) v;
            double fr = v - k;
            for (int x = 0; x < 256; x++) {
                boolean sig = fr > BAYER[(y & 3) * 4 + (x & 3)] / 16.0;
                p.px(x, y, cielo[Math.min(3, sig ? k + 1 : k)]);
            }
        }
        for (int i = 0; i < 40; i++) {
            int ex = (i * 97 + 13) % 256, ey = (i * 53 + 7) % 50;
            p.px(ex, ey, (t / 20 + i) % 7 == 0 ? 5 : 7);
        }
        for (int dy = -12; dy <= 12; dy++)
            for (int dx = -12; dx <= 12; dx++)
                if (dx * dx + dy * dy <= 144 && (dx + 6) * (dx + 6) + (dy + 4) * (dy + 4) > 100) p.px(206 + dx, 26 + dy, dx * dx + dy * dy > 110 ? 59 : 62);
        for (int x = 0; x < 256; x += 4) {
            int h = (int) (18 + 10 * Math.sin(x * 0.05) + 6 * Math.sin(x * 0.13 + 1));
            p.rect(x, HORIZONTE - h, 4, h, 1);
            if ((x / 4) % 7 == 0) p.rect(x + 1, HORIZONTE - h - 3, 2, 3, 2);
        }
        // grúa al fondo
        p.rect(30, 34, 3, 62, 2);
        p.rect(30, 34, 46, 3, 2);
        p.linea(33, 40, 44, 34, 2);
        p.rect(66, 37, 1, 18, 3);
        // suelo con charcos y valla
        for (int y = HORIZONTE; y < SUELO; y++) {
            double v = (y - HORIZONTE) / (double) (SUELO - HORIZONTE) * 2.2;
            int k = (int) v;
            double fr = v - k;
            for (int x = 0; x < 256; x++) p.px(x, y, 49 + Math.min(2, fr > BAYER[(y & 3) * 4 + (x & 3)] / 16.0 ? k + 1 : k));
        }
        for (int i = 0; i < 30; i++) p.px((i * 71 + 5) % 256, HORIZONTE + 6 + (i * 37) % (SUELO - HORIZONTE - 8), 48);
        for (int x = 0; x < 256; x += 12) p.rect(x, HORIZONTE - 14, 1, 14, 4);
        p.rect(0, HORIZONTE - 13, 256, 1, 4);
        p.rect(0, HORIZONTE - 7, 256, 1, 4);
        p.rect(70, 150, 30, 3, 38);
        p.rect(170, 160, 40, 3, 38);
        // robots de lejos a cerca
        robots.sort((a, c) -> Double.compare(c.z, a.z));
        for (Robot r : robots) if (r.z > 0.88 && !r.jefe) robot(p, r);
        // cajas de cobertura (delante de los que salen)
        for (int k = 0; k < 3; k++) {
            int cx = new int[]{50, 128, 206}[k];
            int py = pie(0.95) + 2;
            p.rect(cx - 16, py - 16, 32, 16, 51);
            p.marco(cx - 16, py - 16, 32, 16, 49);
            p.linea(cx - 16, py - 16, cx + 15, py - 1, 49);
            p.rect(cx - 16, py - 16, 32, 1, 52);
        }
        for (Robot r : robots) if (r.z <= 0.88 || r.jefe) robot(p, r);
        for (Lata l : latas) {
            int x = (int) l.x, y = (int) l.y;
            if (l.explota >= 0) {
                int r = l.bidon ? 4 + l.explota : 2 + l.explota / 3;
                p.circulo(x, y, r, l.bidon ? (l.explota < 8 ? 20 : 15) : 7);
                if (l.bidon) p.circulo(x, y, Math.max(0, r - 5), 7);
                continue;
            }
            if (l.bidon) {
                p.rect(x - 6, y - 9, 12, 18, 10);
                p.rect(x - 6, y - 4, 12, 2, 19);
                p.rect(x - 6, y + 3, 12, 2, 19);
            } else {
                p.rect(x - 3, y - 4, 6, 8, (t / 3) % 2 == 0 ? 6 : 36);
                p.rect(x - 3, y - 1, 6, 2, 11);
            }
        }
        // destello del disparo
        if (flash > 0) {
            p.circulo(fx, fy, 2 + flash, 20);
            p.circulo(fx, fy, Math.max(0, flash - 1), 7);
        }
        if (danio > 0 && (danio / 3) % 2 == 0) {
            p.rect(0, 0, 256, 3, 11);
            p.rect(0, SUELO - 3, 256, 3, 11);
            p.rect(0, 0, 3, SUELO, 11);
            p.rect(253, 0, 3, SUELO, 11);
        }
        // mira
        if (ratonX >= 0 && ratonX < 256 && ratonY >= 0 && ratonY < SUELO) {
            int c = recargando > 0 ? 4 : balas == 0 ? 11 : 19;
            p.rect(ratonX - 7, ratonY, 5, 1, c);
            p.rect(ratonX + 3, ratonY, 5, 1, c);
            p.rect(ratonX, ratonY - 7, 1, 5, c);
            p.rect(ratonX, ratonY + 3, 1, 5, c);
            p.px(ratonX, ratonY, 7);
        }
        // marcador
        p.rect(0, SUELO, 256, 16, 0);
        p.rect(0, SUELO, 256, 1, 3);
        for (int i = 0; i < BALAS; i++) {
            boolean hay = i < balas;
            p.rect(6 + i * 7, SUELO + 4, 4, 9, hay ? 19 : 2);
            p.rect(6 + i * 7, SUELO + 4, 4, 2, hay ? 15 : 2);
        }
        if (recargando > 0 || balas == 0) p.texto(recargando > 0 ? "RECARGA" : "¡RECARGA!", 52, SUELO + 5, (t / 8) % 2 == 0 ? 11 : 20);
        for (int i = 0; i < 3; i++) p.circulo(150 + i * 10, SUELO + 8, 3, i < vidas ? 11 : 2);
        p.textoD(Integer.toString(puntos), 252, SUELO + 5, 7);
        p.texto("OL " + oleada, 182, SUELO + 5, 39);
        if (mensajeT > 0 && (mensajeT > 30 || (mensajeT / 6) % 2 == 0)) p.textoC(mensaje, 128, 40, 20);
    }

    /** Rectángulo con contorno negro y brillo arriba e izquierda. */
    static void caja(Pantalla p, int x, int y, int w, int h, int c, int luz) {
        if (w <= 0 || h <= 0) return;
        p.rect(x - 1, y - 1, w + 2, h + 2, 0);
        p.rect(x, y, w, h, c);
        if (w > 2 && h > 2) {
            p.rect(x, y, w, 1, luz);
            p.rect(x, y, 1, h, luz);
        }
    }

    void robot(Pantalla p, Robot r) {
        double s = escala(r.z) * (r.jefe ? 2.4 : r.tipo == 2 ? 1.3 : 1);
        int py = pie(r.z), cx = (int) r.x;
        int w = (int) (12 * s), h = (int) (40 * s);
        boolean blanco = r.golpe > 0 && (r.golpe / 2) % 2 == 0;
        if (r.muere >= 0) {
            // se desmonta: piezas que caen
            int m = r.muere;
            for (int k = 0; k < 5; k++) {
                int dx = (int) ((k - 2) * m * 0.6 * s), dy = (int) (m * m * 0.04 * s);
                p.rect(cx + dx - w / 4, py - h / 2 + dy - k * 3, Math.max(1, w / 3), Math.max(1, h / 6), k % 2 == 0 ? 14 : 3);
            }
            return;
        }
        int cuerpo = r.jefe ? 17 : r.tipo == 1 ? 3 : r.tipo == 2 ? 13 : 14, oscuro = r.jefe ? 13 : r.tipo == 1 ? 2 : 49;
        if (blanco) cuerpo = oscuro = 7;
        int paso = (int) (Math.sin(r.t * (r.tipo == 1 ? 0.3 : 0.15)) * 3 * s);
        int luz = blanco ? 7 : cuerpo + 1;
        // sombra en el suelo
        p.rect(cx - w / 2 - 1, py - 1, w + 2, Math.max(1, (int) (2 * s)), 48);
        // piernas
        caja(p, cx - w / 3, py - h / 3, Math.max(1, w / 4), h / 3 + paso, oscuro, oscuro);
        caja(p, cx + w / 3 - Math.max(1, w / 4), py - h / 3, Math.max(1, w / 4), h / 3 - paso, oscuro, oscuro);
        // tronco
        caja(p, cx - w / 2, (int) (py - h * 0.75), w, (int) (h * 0.45), cuerpo, luz);
        p.rect(cx - w / 2, (int) (py - h * 0.75), w, Math.max(1, h / 14), oscuro);
        p.rect(cx - w / 4, (int) (py - h * 0.6), Math.max(1, w / 2), Math.max(1, h / 10), oscuro);
        // brazos estirados hacia ti
        int by = (int) (py - h * 0.68);
        p.rect(cx - w / 2 - (int) (5 * s), by + paso / 2, (int) (5 * s), Math.max(1, (int) (3 * s)), oscuro);
        p.rect(cx + w / 2, by - paso / 2, (int) (5 * s), Math.max(1, (int) (3 * s)), oscuro);
        // cabeza con ojos verdes
        int hw = (int) (w * 0.8), hh = (int) (h * 0.22);
        caja(p, cx - hw / 2, py - h, hw, hh, cuerpo, luz);
        p.rect(cx - hw / 2 + Math.max(1, hw / 6), py - h + hh / 3, Math.max(1, hw / 5), Math.max(1, hh / 4), 29);
        p.rect(cx + hw / 2 - Math.max(1, hw / 6) - Math.max(1, hw / 5), py - h + hh / 3, Math.max(1, hw / 5), Math.max(1, hh / 4), 29);
        p.rect(cx - 1, py - h - (int) (4 * s), Math.max(1, (int) s), (int) (4 * s), oscuro);
        if (r.jefe) {
            int ny = (int) (py - h * 0.55);
            p.circulo(cx, ny, (int) (5 * s / 2.4 * 2.4), (t / 4) % 2 == 0 ? 63 : 37);
            p.circulo(cx, ny, Math.max(1, (int) (2 * s)), 7);
            for (int i = 0; i < 20; i++) p.rect(cx - 30 + i * 3, py - h - 4, 2, 2, r.vida > i * 1.4 ? 11 : 2);
        }
    }
}

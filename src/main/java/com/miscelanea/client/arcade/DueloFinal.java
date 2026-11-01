package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Botones;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;

import static com.miscelanea.client.arcade.Lucha.*;

/**
 * Duelo Final: lucha uno contra uno a tres rondas. Un jugador recorre la escalera contra la máquina (tres rivales y el
 * jefe final); dos jugadores se enfrentan entre sí. A puño (A-A-A encadena), B patada, abajo+A gancho, abajo+B barrido,
 * en el aire A/B patada aérea, C especial; atrás para cubrirse (agachado para lo bajo).
 */
public class DueloFinal extends JuegoArcade {
    static final int ANCHO = 384, SUELO = 170;
    static final int SEL = 0, VS = 1, INTRO = 2, LUCHA = 3, KO = 4, FIN_RONDA = 5, CAMPEON = 6;
    // estados del luchador
    static final int E_QUIETO = 0, E_ANDAR = 1, E_AGACHADO = 2, E_SALTO = 3, E_ATAQUE = 4, E_DANO = 5, E_BLOQ = 6, E_CAIDA = 7, E_SUELO = 8,
            E_LEVANTA = 9, E_VICTORIA = 10, E_KO = 11;
    static final int ALTO = 0, BAJO = 1, AEREO = 2;

    /** Golpe: cuadros de preparación/acción, tiempos, daño, caja (x hacia delante, y hacia arriba desde los pies) y efecto. */
    record Mov(int fPrep, int fAct, int ini, int act, int rec, int dano, int x0, int x1, int y0, int y1, int tipo, boolean derriba, float empuje, int aturde) {}

    static final Mov PUNO_M = new Mov(PUNO_P, PUNO, 4, 3, 8, 6, 10, 38, 48, 64, ALTO, false, 1.4F, 15);
    static final Mov PUNO2_M = new Mov(PUNO_P, Lucha.PUNO2, 2, 3, 9, 7, 10, 38, 48, 64, ALTO, false, 1.6F, 16);
    static final Mov GANCHO_M = new Mov(AGACHADO, Lucha.GANCHO, 4, 6, 15, 11, 4, 30, 36, 86, ALTO, true, 2.0F, 20);
    static final Mov PATADA_M = new Mov(PATADA_P, PATADA, 6, 4, 13, 9, 14, 50, 30, 56, ALTO, false, 2.4F, 17);
    static final Mov BARRIDO_M = new Mov(AGACHADO, Lucha.BARRIDO, 6, 4, 16, 8, 8, 50, 0, 14, BAJO, true, 1.5F, 20);
    static final Mov AEREA_M = new Mov(SALTO2, Lucha.AEREA, 3, 200, 0, 9, 6, 40, 8, 40, AEREO, false, 1.6F, 16);
    // especiales: onda (Kai), remolino (Luna), embestida (Toro y el jefe), llave lanzada (Chispa)
    static final Mov ONDA_M = new Mov(ESP1, ESP2, 12, 4, 20, 0, 0, 0, 0, 0, ALTO, false, 0, 0);
    static final Mov REMOLINO_M = new Mov(ESP1, ESP2, 8, 40, 14, 5, 0, 40, 20, 70, ALTO, false, 1.2F, 12);
    static final Mov EMBESTIDA_M = new Mov(ESP1, ESP2, 10, 30, 16, 14, 4, 36, 10, 66, ALTO, true, 3F, 20);
    static final Mov LLAVE_M = new Mov(ESP1, ESP2, 10, 4, 18, 0, 0, 0, 0, 0, ALTO, false, 0, 0);

    static final float[] VEL = {1.4F, 1.7F, 1.1F, 1.5F, 1.4F, 1.4F, 1.1F, 1.2F};
    static final float[] FUERZA = {1F, 0.9F, 1.25F, 1F, 1F, 1F, 1.2F, 1.3F};
    static final int[] ANCHO_PJ = {13, 12, 16, 13, 13, 13, 16, 17};

    final class Pel {
        final int lado;
        int pj, est, t, dir = 1, hp = 100, flash, ganadas, cd, combo, invul, golpesT;
        float x, h, vx, vh, rastro = 100;
        Mov mov;
        boolean golpeo, cpu;
        final Mando man = new Mando();
        int cpuMask, cpuT;

        Pel(int lado) {
            this.lado = lado;
        }

        boolean suelo() {
            return h <= 0 && vh >= 0;
        }

        boolean libre() {
            return est == E_QUIETO || est == E_ANDAR || est == E_AGACHADO;
        }

        boolean atras() {
            return dir > 0 ? man.m[Botones.IZQ] : man.m[Botones.DER];
        }

        boolean delante() {
            return dir > 0 ? man.m[Botones.DER] : man.m[Botones.IZQ];
        }

        void poner(int e) {
            est = e;
            t = 0;
        }

        void atacar(Mov m) {
            mov = m;
            golpeo = false;
            poner(E_ATAQUE);
            sonido(ModSounds.LU_AIRE, m == PATADA_M || m == BARRIDO_M ? 0.8F : 1.1F);
        }

        int ancho() {
            return ANCHO_PJ[pj];
        }

        int alto() {
            return est == E_AGACHADO || (est == E_BLOQ && man.m[Botones.ABAJO]) || (est == E_ATAQUE && (mov == BARRIDO_M)) ? 42 : 64;
        }
    }

    static final class Proy {
        float x, h, vx, vh;
        int tipo, dueno, t;
    }

    final Pel[] p = {new Pel(0), new Pel(1)};
    final List<Proy> proys = new ArrayList<>();
    final List<Efecto> efs = new ArrayList<>();
    int estado, t, ronda, tiempo, pausa, camX, escenario, lenta;
    int[] sel = {0, 3};
    boolean[] listo = new boolean[2];
    int[] escalera;
    int peldano;
    String cartel = "";
    int cartelT, ganadorRonda = -1;
    Sprite[] fondos;

    public DueloFinal(Botones[] b, long semilla, int jugadores) {
        super(b, semilla, jugadores);
    }

    @Override
    public SoundEvent musica() {
        return ModSounds.LU_DUELO.get();
    }

    @Override
    public void reiniciar() {
        Lucha.cargar();
        fondos = new Sprite[]{Sprite.uno("lucha/esc_puerto"), Sprite.uno("lucha/esc_azotea"), Sprite.uno("lucha/esc_plaza")};
        estado = SEL;
        t = 0;
        puntos = 0;
        fin = false;
        listo[0] = listo[1] = false;
        sel[0] = 0;
        sel[1] = 3;
        p[1].cpu = jugadores < 2;
    }

    // ------------------------------------------------------------------ selección
    void pasoSeleccion() {
        t++;
        for (int i = 0; i < 2; i++) {
            if (i == 1 && jugadores < 2) break;
            Botones bb = b[i];
            if (listo[i]) continue;
            if (bb.pulsa(Botones.IZQ)) {
                sel[i] = (sel[i] + 3) % 4;
                sonido(ModSounds.LU_SELEC, 1F);
            }
            if (bb.pulsa(Botones.DER)) {
                sel[i] = (sel[i] + 1) % 4;
                sonido(ModSounds.LU_SELEC, 1F);
            }
            if (bb.pulsa(Botones.A) || bb.pulsa(Botones.START) || t > 60 * 20) {
                listo[i] = true;
                sonido(ModSounds.LU_RONDA, 1.3F);
            }
        }
        if (listo[0] && (jugadores < 2 || listo[1])) {
            if (jugadores < 2) {
                // escalera: los otros tres en orden aleatorio y el jefe al final
                int[] otros = new int[3];
                int k = 0;
                for (int i = 0; i < 4; i++) if (i != sel[0]) otros[k++] = i;
                for (int i = 2; i > 0; i--) {
                    int j = rnd.nextInt(i + 1), x = otros[i];
                    otros[i] = otros[j];
                    otros[j] = x;
                }
                escalera = new int[]{otros[0], otros[1], otros[2], JEFE};
                peldano = 0;
                sel[1] = escalera[0];
            }
            empezarCombate();
        }
    }

    void empezarCombate() {
        p[0].pj = sel[0];
        p[1].pj = sel[1];
        p[0].ganadas = p[1].ganadas = 0;
        ronda = 0;
        escenario = jugadores < 2 ? peldano % 3 : rnd.nextInt(3);
        if (jugadores < 2 && escalera[peldano] == JEFE) escenario = 0;
        estado = VS;
        t = 0;
    }

    void empezarRonda() {
        ronda++;
        for (Pel q : p) {
            q.hp = 100;
            q.rastro = 100;
            q.h = 0;
            q.vx = q.vh = 0;
            q.poner(E_QUIETO);
            q.cd = 0;
            q.invul = 0;
            q.flash = 0;
            q.man.vaciar();
        }
        p[0].x = ANCHO / 2F - 50;
        p[1].x = ANCHO / 2F + 50;
        p[0].dir = 1;
        p[1].dir = -1;
        proys.clear();
        efs.clear();
        tiempo = 60 * 60;
        estado = INTRO;
        t = 0;
        lenta = 0;
        ganadorRonda = -1;
        sonido(ModSounds.LU_RONDA, 1F);
    }

    // ------------------------------------------------------------------ paso
    @Override
    public void paso() {
        if (cartelT > 0) cartelT--;
        switch (estado) {
            case SEL -> pasoSeleccion();
            case VS -> {
                if (++t > 150 || (t > 30 && (b[0].pulsa(Botones.A) || b[0].pulsa(Botones.START)))) empezarRonda();
            }
            case INTRO -> {
                t++;
                if (t == 1) aviso(I18n.get("duelo.miscelanea.ronda", ronda), 70);
                if (t == 75) {
                    aviso(I18n.get("duelo.miscelanea.lucha"), 40);
                    sonido(ModSounds.LU_RONDA, 1.5F);
                }
                fisica();
                if (t > 90) {
                    estado = LUCHA;
                    t = 0;
                }
            }
            case LUCHA -> {
                if (pausa > 0) {
                    pausa--;
                    break;
                }
                t++;
                for (Pel q : p) entrada(q);
                for (Pel q : p) logica(q);
                fisica();
                choques();
                if (--tiempo <= 0) {
                    tiempo = 0;
                    finRonda(p[0].hp > p[1].hp ? 0 : p[1].hp > p[0].hp ? 1 : -1, true);
                }
            }
            case KO -> {
                t++;
                lenta++;
                if (t < 70 && lenta % 3 != 0) break;
                for (Pel q : p) {
                    q.man.vaciar();
                    logica(q);
                }
                fisica();
                if (t > 150) {
                    Pel g = ganadorRonda >= 0 ? p[ganadorRonda] : null;
                    if (g != null && g.est != E_VICTORIA && g.suelo()) {
                        g.poner(E_VICTORIA);
                        aviso(I18n.get("duelo.miscelanea.gana", nombre(g.pj)), 120);
                    }
                }
                if (t > 260) despuesDeRonda();
            }
            case CAMPEON -> {
                t++;
                for (Pel q : p) logica(q);
                if (t > 300) {
                    fin = true;
                    mensajeFin = I18n.get("duelo.miscelanea.campeon");
                }
            }
            default -> {}
        }
        for (Pel q : p) {
            if (q.rastro > q.hp) q.rastro -= 0.4F;
            else q.rastro = q.hp;
            if (q.flash > 0) q.flash--;
        }
        for (int i = efs.size() - 1; i >= 0; i--) {
            Efecto e = efs.get(i);
            e.x += e.vx;
            if (++e.t >= e.dur) efs.remove(i);
        }
        int medio = (int) ((p[0].x + p[1].x) / 2);
        int obj = Math.max(0, Math.min(ANCHO - Pantalla.W, medio - Pantalla.W / 2));
        camX += Integer.signum(obj - camX) * Math.min(3, Math.abs(obj - camX));
    }

    void aviso(String s, int d) {
        cartel = s;
        cartelT = d;
    }

    static String nombre(int pj) {
        return I18n.get("lucha.miscelanea.pj." + PJ[pj]);
    }

    void finRonda(int ganador, boolean porTiempo) {
        estado = KO;
        t = 0;
        lenta = 0;
        ganadorRonda = ganador;
        if (ganador >= 0) p[ganador].ganadas++;
        aviso(porTiempo ? I18n.get("duelo.miscelanea.tiempo") : I18n.get("duelo.miscelanea.ko"), 90);
        sonido(ModSounds.LU_KO, 1F);
        if (ganador == 0 && jugadores < 2) {
            puntos += 1000 + p[0].hp * 20 + tiempo / 60 * 20;
            if (p[0].hp == 100) {
                puntos += 5000;
                aviso(I18n.get("duelo.miscelanea.perfecto"), 120);
            }
        }
    }

    void despuesDeRonda() {
        int g0 = p[0].ganadas, g1 = p[1].ganadas;
        if (g0 >= 2 || g1 >= 2 || ronda >= 5) {
            int gana = g0 > g1 ? 0 : g1 > g0 ? 1 : (p[0].hp >= p[1].hp ? 0 : 1);
            if (jugadores >= 2) {
                fin = true;
                mensajeFin = I18n.get("duelo.miscelanea.gana_jugador", gana + 1, nombre(p[gana].pj));
                return;
            }
            if (gana == 1) {
                fin = true;
                mensajeFin = I18n.get("duelo.miscelanea.derrota");
                return;
            }
            puntos += 3000 * (peldano + 1);
            peldano++;
            if (peldano >= escalera.length) {
                estado = CAMPEON;
                t = 0;
                puntos += 20000;
                p[0].poner(E_VICTORIA);
                aviso(I18n.get("duelo.miscelanea.campeon"), 300);
                return;
            }
            sel[1] = escalera[peldano];
            empezarCombate();
            return;
        }
        empezarRonda();
    }

    // ------------------------------------------------------------------ mandos (jugador o máquina)
    void entrada(Pel q) {
        if (q.cpu) {
            q.man.desde(cerebro(q));
        } else q.man.desde(b[jugadores >= 2 ? q.lado : 0]);
    }

    /** Inteligencia de la máquina: decide los mandos cada pocos pasos según la distancia y lo que hace el rival. */
    int cerebro(Pel q) {
        Pel o = p[1 - q.lado];
        int nivel = jugadores < 2 ? peldano : 1;
        if (q.cpuT > 0) {
            q.cpuT--;
            // los botones de ataque se pulsan una vez; las direcciones se mantienen
            return q.cpuMask & ~((1 << Botones.A) | (1 << Botones.B) | (1 << Botones.C));
        }
        float d = Math.abs(o.x - q.x);
        int adelante = q.dir > 0 ? Botones.DER : Botones.IZQ, atras = q.dir > 0 ? Botones.IZQ : Botones.DER;
        int m = 0;
        int reaccion = 16 - nivel * 3;
        float r = rnd.nextFloat();
        boolean amenaza = (o.est == E_ATAQUE && o.t < (o.mov != null ? o.mov.ini() + 2 : 4) && d < 70) || cercaProyectil(q);
        if (amenaza && r < 0.35F + nivel * 0.13F) {
            m = 1 << atras;
            if (o.est == E_ATAQUE && o.mov == BARRIDO_M) m |= 1 << Botones.ABAJO;
            q.cpuT = 14;
        } else if (!o.suelo() && d < 70 && o.vh > -1 && r < 0.3F + nivel * 0.12F) {
            m = (1 << Botones.ABAJO) | (1 << Botones.A);
            q.cpuT = reaccion;
        } else if (d > 130) {
            if ((q.pj == KAI || q.pj == CHISPA) && q.cd == 0 && r < 0.35F) m = 1 << Botones.C;
            else m = 1 << adelante;
            q.cpuT = reaccion;
        } else if (d > 52) {
            if (r < 0.12F) m = (1 << Botones.ARRIBA) | (1 << adelante);
            else if (r < 0.2F && q.cd == 0) m = 1 << Botones.C;
            else if (r < 0.3F) m = 1 << atras;
            else m = 1 << adelante;
            q.cpuT = reaccion + rnd.nextInt(10);
        } else {
            if (r < 0.32F) m = 1 << Botones.A;
            else if (r < 0.5F) m = 1 << Botones.B;
            else if (r < 0.62F) m = (1 << Botones.ABAJO) | (1 << Botones.B);
            else if (r < 0.7F && q.cd == 0) m = 1 << Botones.C;
            else if (r < 0.85F) m = 1 << atras;
            else m = (1 << Botones.ABAJO) | (1 << atras);
            q.cpuT = reaccion / 2 + rnd.nextInt(8);
        }
        // si ya ha conectado un puñetazo, seguir el combo
        if (q.est == E_ATAQUE && q.golpeo && (q.mov == PUNO_M || q.mov == PUNO2_M) && rnd.nextFloat() < 0.3F + nivel * 0.15F) {
            m = 1 << Botones.A;
            q.cpuT = 3;
        }
        q.cpuMask = m;
        return m;
    }

    boolean cercaProyectil(Pel q) {
        for (Proy pr : proys) if (pr.dueno != q.lado && Math.abs(pr.x - q.x) < 80 && Math.signum(q.x - pr.x) == Math.signum(pr.vx)) return true;
        return false;
    }

    // ------------------------------------------------------------------ lógica de un luchador
    void logica(Pel q) {
        Pel o = p[1 - q.lado];
        q.t++;
        if (q.cd > 0) q.cd--;
        if (q.invul > 0) q.invul--;
        Mando m = q.man;
        boolean enCombate = estado == LUCHA;
        switch (q.est) {
            case E_QUIETO, E_ANDAR, E_AGACHADO -> {
                if (q.suelo() && estado != KO) q.dir = o.x >= q.x ? 1 : -1;
                if (!enCombate) {
                    q.vx = 0;
                    q.est = E_QUIETO;
                    break;
                }
                boolean abajo = m.m[Botones.ABAJO];
                if (m.usar(Botones.C) && q.cd == 0) {
                    especial(q);
                    break;
                }
                if (m.usar(Botones.A)) {
                    q.atacar(abajo ? GANCHO_M : PUNO_M);
                    q.combo = 0;
                    break;
                }
                if (m.usar(Botones.B)) {
                    q.atacar(abajo ? BARRIDO_M : PATADA_M);
                    break;
                }
                if (m.m[Botones.ARRIBA]) {
                    q.vh = -5.2F;
                    q.vx = q.delante() ? VEL[q.pj] * 1.3F : q.atras() ? -VEL[q.pj] * 1.3F : 0;
                    q.vx *= q.dir;
                    q.poner(E_SALTO);
                    sonido(ModSounds.LU_AIRE, 0.6F);
                    break;
                }
                if (abajo) {
                    q.est = E_AGACHADO;
                    q.vx = 0;
                } else if (q.delante()) {
                    q.est = E_ANDAR;
                    q.vx = VEL[q.pj] * q.dir;
                } else if (q.atras()) {
                    q.est = E_ANDAR;
                    q.vx = -VEL[q.pj] * 0.8F * q.dir;
                } else {
                    q.est = E_QUIETO;
                    q.vx = 0;
                }
            }
            case E_SALTO -> {
                if (enCombate && (m.usar(Botones.A) || m.usar(Botones.B))) {
                    q.mov = AEREA_M;
                    q.golpeo = false;
                    q.est = E_ATAQUE;
                    q.t = 0;
                    sonido(ModSounds.LU_AIRE, 1F);
                }
                if (q.suelo() && q.t > 2) {
                    q.poner(E_QUIETO);
                    q.vx = 0;
                }
            }
            case E_ATAQUE -> {
                Mov mv = q.mov;
                if (mv == AEREA_M) {
                    if (q.suelo() && q.t > 2) {
                        q.poner(E_QUIETO);
                        q.vx = 0;
                    }
                    break;
                }
                if (q.suelo()) q.vx *= 0.8F;
                if (mv == REMOLINO_M && q.t >= mv.ini() && q.t < mv.ini() + mv.act()) {
                    q.vx = 2.2F * q.dir;
                    if ((q.t - mv.ini()) % 10 == 0) {
                        q.golpeo = false;
                        sonido(ModSounds.LU_AIRE, 1.3F);
                    }
                }
                if (mv == EMBESTIDA_M && q.t >= mv.ini() && q.t < mv.ini() + mv.act()) {
                    q.vx = 4.2F * q.dir;
                    if (q.t % 6 == 0) efs.add(new Efecto(EF_POLVO, q.x - q.dir * 14, SUELO, 12));
                }
                if (mv == GANCHO_M && q.t == mv.ini()) q.vh = -2.2F;
                if (q.t == mv.ini() && (mv == ONDA_M || mv == LLAVE_M)) lanzar(q);
                // encadenar: A-A-A (puño, puño de atrás, gancho) si el golpe ha entrado
                if (q.golpeo && q.t > mv.ini() && (mv == PUNO_M || mv == PUNO2_M) && m.usar(Botones.A)) {
                    q.atacar(mv == PUNO_M ? PUNO2_M : GANCHO_M);
                    break;
                }
                if (q.golpeo && q.t > mv.ini() && mv != ONDA_M && mv != LLAVE_M && mv != REMOLINO_M && mv != EMBESTIDA_M && m.usar(Botones.C) && q.cd == 0) {
                    especial(q);
                    break;
                }
                if (q.t >= mv.ini() + mv.act() + mv.rec()) q.poner(E_QUIETO);
            }
            case E_DANO, E_BLOQ -> {
                q.vx *= 0.85F;
                if (q.t >= q.golpesT) q.poner(E_QUIETO);
            }
            case E_CAIDA -> {
                if (q.suelo() && q.t > 4) {
                    q.vx = 0;
                    sonido(ModSounds.LU_CAIDA, 1F);
                    efs.add(new Efecto(EF_POLVO, q.x, SUELO, 14));
                    q.poner(q.hp <= 0 ? E_KO : E_SUELO);
                }
            }
            case E_SUELO -> {
                if (q.t > 36) q.poner(E_LEVANTA);
            }
            case E_LEVANTA -> {
                if (q.t > 14) {
                    q.poner(E_QUIETO);
                    q.invul = 20;
                }
            }
            default -> q.vx = 0;
        }
    }

    void especial(Pel q) {
        q.cd = 75;
        q.atacar(switch (q.pj) {
            case KAI -> ONDA_M;
            case LUNA -> REMOLINO_M;
            case CHISPA -> LLAVE_M;
            default -> EMBESTIDA_M;
        });
        sonido(ModSounds.LU_ESPECIAL, q.pj == KAI ? 1F : 0.8F);
    }

    void lanzar(Pel q) {
        for (Proy pr : proys) if (pr.dueno == q.lado) return;
        Proy pr = new Proy();
        pr.dueno = q.lado;
        pr.tipo = q.pj == KAI ? 0 : 1;
        pr.x = q.x + q.dir * 30;
        pr.h = q.pj == KAI ? 50 : 62;
        pr.vx = q.dir * (q.pj == KAI ? 3.6F : 3.0F);
        pr.vh = q.pj == KAI ? 0 : -2.6F;
        proys.add(pr);
    }

    // ------------------------------------------------------------------ física y choques
    void fisica() {
        for (Pel q : p) {
            q.x += q.vx;
            if (q.h > 0 || q.vh < 0) {
                q.h -= q.vh;
                q.vh += 0.25F;
                if (q.h <= 0) {
                    q.h = 0;
                    q.vh = 0;
                }
            }
            q.x = Math.max(20, Math.min(ANCHO - 20, q.x));
        }
        // no pueden alejarse más que la pantalla ni atravesarse
        Pel a = p[0], c = p[1];
        float dist = c.x - a.x;
        if (Math.abs(dist) > 220) {
            float sobra = (Math.abs(dist) - 220) / 2 * Math.signum(dist);
            a.x += sobra;
            c.x -= sobra;
        }
        if (a.h < 40 && c.h < 40 && a.est != E_SUELO && c.est != E_SUELO && a.est != E_KO && c.est != E_KO) {
            float min = a.ancho() + c.ancho();
            dist = c.x - a.x;
            if (Math.abs(dist) < min) {
                float s = dist == 0 ? (a.dir > 0 ? 1 : -1) : Math.signum(dist);
                float emp = (min - Math.abs(dist)) / 2;
                a.x -= s * emp;
                c.x += s * emp;
                for (Pel q : p) q.x = Math.max(20, Math.min(ANCHO - 20, q.x));
            }
        }
        for (int i = proys.size() - 1; i >= 0; i--) {
            Proy pr = proys.get(i);
            pr.t++;
            pr.x += pr.vx;
            if (pr.tipo == 1) {
                pr.h -= pr.vh;
                pr.vh += 0.08F;
            }
            if (pr.x < camX - 20 || pr.x > camX + Pantalla.W + 20 || pr.h < 0) proys.remove(i);
        }
    }

    void choques() {
        for (Pel q : p) {
            if (q.est != E_ATAQUE || q.golpeo) continue;
            Mov mv = q.mov;
            boolean activo = mv == AEREA_M ? q.t >= mv.ini() : q.t >= mv.ini() && q.t < mv.ini() + mv.act();
            if (!activo || mv.dano() == 0) continue;
            Pel o = p[1 - q.lado];
            float hx0 = q.dir > 0 ? q.x + mv.x0() : q.x - mv.x1(), hx1 = q.dir > 0 ? q.x + mv.x1() : q.x - mv.x0();
            float hy0 = q.h + mv.y0(), hy1 = q.h + mv.y1();
            if (golpea(o, hx0, hx1, hy0, hy1)) {
                q.golpeo = true;
                impacto(q, o, mv, (int) (mv.dano() * FUERZA[q.pj]), q.dir, (hx0 + hx1) / 2, Math.min(hy1, o.h + o.alto() - 8));
            }
        }
        for (int i = proys.size() - 1; i >= 0; i--) {
            Proy pr = proys.get(i);
            Pel o = p[1 - pr.dueno];
            // dos proyectiles se anulan
            for (int j = proys.size() - 1; j >= 0; j--) {
                Proy x = proys.get(j);
                if (x != pr && x.dueno != pr.dueno && Math.abs(x.x - pr.x) < 16 && Math.abs(x.h - pr.h) < 20) {
                    efs.add(new Efecto(EF_GOLPE, (x.x + pr.x) / 2, SUELO - pr.h, 9));
                    proys.remove(Math.max(i, j));
                    proys.remove(Math.min(i, j));
                    sonido(ModSounds.LU_GOLPE, 1.5F);
                    return;
                }
            }
            if (golpea(o, pr.x - 8, pr.x + 8, pr.h - 8, pr.h + 8)) {
                proys.remove(i);
                impacto(p[pr.dueno], o, pr.tipo == 0 ? ONDA_PROY : LLAVE_PROY, pr.tipo == 0 ? 12 : 11, (int) Math.signum(pr.vx), pr.x, pr.h);
            }
        }
    }

    static final Mov ONDA_PROY = new Mov(0, 0, 0, 0, 0, 12, 0, 0, 0, 0, ALTO, false, 2.2F, 18);
    static final Mov LLAVE_PROY = new Mov(0, 0, 0, 0, 0, 11, 0, 0, 0, 0, ALTO, false, 2.0F, 18);

    boolean golpea(Pel o, float x0, float x1, float y0, float y1) {
        if (o.invul > 0 || o.est == E_SUELO || o.est == E_LEVANTA || o.est == E_KO || o.est == E_CAIDA) return false;
        float w = o.ancho();
        return x1 > o.x - w && x0 < o.x + w && y1 > o.h && y0 < o.h + o.alto();
    }

    void impacto(Pel q, Pel o, Mov mv, int dano, int dir, float ex, float ey) {
        boolean especial = mv == ONDA_PROY || mv == LLAVE_PROY || mv == REMOLINO_M || mv == EMBESTIDA_M;
        boolean puedeCubrir = o.suelo() && (o.libre() || o.est == E_BLOQ) && o.atras();
        boolean agachado = o.man.m[Botones.ABAJO];
        boolean cubre = puedeCubrir && (mv.tipo() == ALTO || (mv.tipo() == BAJO && agachado) || (mv.tipo() == AEREO && !agachado));
        if (cubre) {
            o.poner(E_BLOQ);
            o.golpesT = mv.aturde() - 4;
            o.vx = dir * mv.empuje() * 1.2F;
            if (especial) o.hp -= 2;
            efs.add(new Efecto(EF_BLOQUEO, ex, SUELO - ey, 8));
            sonido(ModSounds.LU_BLOQUEO, 1F);
            pausa = 3;
            if (o.hp <= 0) {
                o.hp = 0;
                ko(q, o, dir);
            }
            return;
        }
        o.hp -= dano;
        o.flash = 6;
        if (q.lado == 0 && jugadores < 2) puntos += dano * 10;
        efs.add(new Efecto(EF_GOLPE, ex, SUELO - ey, 9));
        sonido(dano >= 10 ? ModSounds.LU_GOLPE2 : ModSounds.LU_GOLPE, 0.9F + rnd.nextFloat() * 0.2F);
        pausa = dano >= 10 ? 7 : 5;
        if (o.hp <= 0) {
            o.hp = 0;
            ko(q, o, dir);
            return;
        }
        if (mv.derriba() || !o.suelo()) {
            o.poner(E_CAIDA);
            o.vh = -3.4F;
            o.h = Math.max(o.h, 1);
            o.vx = dir * 1.8F;
        } else {
            o.poner(E_DANO);
            o.golpesT = mv.aturde();
            o.vx = dir * mv.empuje();
        }
    }

    void ko(Pel q, Pel o, int dir) {
        o.poner(E_CAIDA);
        o.vh = -4F;
        o.h = Math.max(o.h, 1);
        o.vx = dir * 2.2F;
        pausa = 0;
        finRonda(q.lado, false);
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void dibujar(Pantalla pt) {
        if (estado == SEL) {
            dibujarSeleccion(pt);
            return;
        }
        if (estado == VS) {
            dibujarVs(pt);
            return;
        }
        Lucha.fondo(pt, fondos[escenario], camX, 0);
        for (Pel q : p) Lucha.sombra(pt, (int) q.x - camX, SUELO, q.ancho() + 4 - (int) (q.h / 8));
        // el que ataca se dibuja delante
        Pel delante = p[1].est == E_ATAQUE && p[0].est != E_ATAQUE ? p[1] : p[0];
        Pel detras = delante == p[0] ? p[1] : p[0];
        dibujarPel(pt, detras);
        dibujarPel(pt, delante);
        for (Proy pr : proys) {
            int f = pr.tipo == 0 ? EF_ONDA + (pr.t / 4) % 3 : EF_LLAVE + (pr.t / 3) % 4;
            pt.spr(efectos[f], (int) pr.x - camX - 16, SUELO - (int) pr.h - 16, pr.vx < 0);
        }
        for (Efecto e : efs) {
            int f = e.tipo + Math.min(e.tipo == EF_BLOQUEO ? 1 : 2, e.t * (e.tipo == EF_BLOQUEO ? 2 : 3) / e.dur);
            pt.spr(efectos[f], (int) e.x - camX - 16, (int) e.y - 16 - (e.tipo == EF_POLVO ? 12 : 0), false);
        }
        hud(pt);
    }

    void dibujarPel(Pantalla pt, Pel q) {
        int f = cuadroDe(q);
        if (q.invul > 0 && q.invul % 4 < 2 && q.est != E_VICTORIA) return;
        Lucha.dibujar(pt, q.pj, f, (int) q.x - camX, SUELO - (int) q.h, q.dir, q.flash);
    }

    int cuadroDe(Pel q) {
        return switch (q.est) {
            case E_QUIETO -> QUIETO + (q.t / 10) % 4;
            case E_ANDAR -> ANDAR + (q.vx * q.dir >= 0 ? (q.t / 6) % 6 : 5 - (q.t / 6) % 6);
            case E_AGACHADO -> AGACHADO;
            case E_SALTO -> q.vh < -1.5F ? SALTO : SALTO2;
            case E_ATAQUE -> {
                Mov mv = q.mov;
                if (mv == REMOLINO_M && q.t >= mv.ini() && q.t < mv.ini() + mv.act()) yield (q.t / 5) % 2 == 0 ? ESP2 : Lucha.PATADA;
                yield q.t < mv.ini() ? mv.fPrep() : mv.fAct();
            }
            case E_DANO -> DANO;
            case E_BLOQ -> q.man.m[Botones.ABAJO] ? BLOQ_AGACH : BLOQUEO;
            case E_CAIDA -> q.vh < 0 ? CAIDA1 : CAIDA2;
            case E_SUELO, E_KO -> TUMBADO;
            case E_LEVANTA -> Lucha.LEVANTA;
            case E_VICTORIA -> VICTORIA;
            default -> QUIETO;
        };
    }

    void hud(Pantalla pt) {
        for (int i = 0; i < 2; i++) {
            Pel q = p[i];
            int x = i == 0 ? 36 : 140;
            Lucha.barra(pt, x, 8, 80, q.hp / 100F, q.rastro / 100F, i == 1, 19);
            pt.spr(caras[q.pj], i == 0 ? 2 : 222, 2, i == 1);
            pt.marco(i == 0 ? 2 : 222, 2, 32, 32, i == 0 ? 11 : 36);
            String n = nombre(q.pj);
            if (i == 0) pt.texto(n, 37, 17, 7);
            else pt.textoD(n, 219, 17, 7);
            for (int k = 0; k < 2; k++) {
                int sx = i == 0 ? 37 + k * 8 : 213 - k * 8;
                pt.rect(sx, 28, 6, 6, k < q.ganadas ? 19 : 2);
                pt.marco(sx, 28, 6, 6, 0);
            }
        }
        pt.rect(119, 4, 18, 14, 0);
        pt.textoC(Integer.toString((tiempo + 59) / 60), 128, 7, tiempo < 600 && (tiempo / 15) % 2 == 0 ? 11 : 20);
        if (jugadores < 2) pt.textoC(Integer.toString(puntos), 128, 22, 6);
        if (cartelT > 0) {
            pt.rect(0, 74, Pantalla.W, 18, 0);
            pt.rect(0, 74, Pantalla.W, 1, 19);
            pt.rect(0, 91, Pantalla.W, 1, 19);
            pt.textoC(cartel, 128, 79, 20);
        }
    }

    void dibujarSeleccion(Pantalla pt) {
        for (int y = 0; y < Pantalla.H; y++) pt.rect(0, y, Pantalla.W, 1, y < 40 ? 41 : y < 120 ? 42 : 33);
        for (int k = 0; k < 12; k++) pt.linea(k * 24 - (t % 24), 192, k * 24 + 60 - (t % 24), 0, 41);
        pt.rect(0, 4, Pantalla.W, 16, 0);
        pt.textoC(I18n.get("duelo.miscelanea.elige"), 128, 8, 20);
        for (int i = 0; i < 4; i++) {
            int x = 128 - 2 * 38 + i * 38 + 2;
            pt.rect(x - 2, 128, 36, 36, 1);
            pt.spr(caras[i], x, 130, false);
            boolean s0 = sel[0] == i, s1 = jugadores >= 2 && sel[1] == i;
            if (s0) pt.marco(x - 3, 127, 38, 38, (t / 6) % 2 == 0 || listo[0] ? 11 : 12);
            if (s1) pt.marco(x - 5, 125, 42, 42, (t / 6) % 2 == 0 || listo[1] ? 36 : 39);
        }
        // luchadores grandes en guardia
        Lucha.dibujar(pt, sel[0], listo[0] ? VICTORIA : QUIETO + (t / 10) % 4, 58, 118, 1, 0);
        pt.textoC(nombre(sel[0]), 58, 26, 12);
        pt.textoC(I18n.get("lucha.miscelanea.desc." + PJ[sel[0]]), 58, 168, 6);
        if (jugadores >= 2) {
            Lucha.dibujar(pt, sel[1], listo[1] ? VICTORIA : QUIETO + (t / 10) % 4, 198, 118, -1, 0);
            pt.textoC(nombre(sel[1]), 198, 26, 39);
            pt.textoC(I18n.get("lucha.miscelanea.desc." + PJ[sel[1]]), 198, 170, 6);
        } else {
            pt.textoC("?", 198, 70, 7);
            pt.textoC(I18n.get("duelo.miscelanea.cpu"), 198, 26, 39);
        }
        pt.textoC(Integer.toString(Math.max(0, 20 - t / 60)), 128, 60, 7);
    }

    void dibujarVs(Pantalla pt) {
        pt.limpiar(0);
        for (int y = 0; y < Pantalla.H; y++) {
            int sep = 128 + (y - 96) / 4;
            pt.rect(0, y, sep, 1, 9);
            pt.rect(sep, y, Pantalla.W - sep, 1, 34);
        }
        int entra = Math.min(60, t * 3);
        Lucha.dibujar(pt, p[0].pj, QUIETO + (t / 10) % 4, -10 + entra, 150, 1, 0);
        Lucha.dibujar(pt, p[1].pj, QUIETO + (t / 10) % 4, 266 - entra, 150, -1, 0);
        pt.textoC(nombre(p[0].pj), 60, 160, 7);
        pt.textoC(nombre(p[1].pj), 196, 160, 7);
        pt.rect(108, 80, 40, 22, 0);
        pt.textoC("VS", 128, 86, (t / 4) % 2 == 0 ? 20 : 12);
        if (jugadores < 2) pt.textoC(I18n.get("duelo.miscelanea.combate", peldano + 1, escalera.length), 128, 20, 20);
    }
}

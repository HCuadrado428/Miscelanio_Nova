package com.miscelanea.juegos;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Gestos de los jugadores en los juegos de mesa (capa de playerAnimator del cliente, ver
 * {@code client.juegos.AnimGesto}). El servidor decide el gesto y lo manda a todos los que ven al jugador.
 */
public final class Gestos {
    /** Alarga el brazo hacia el punto (mover ficha, tocar casilla). */
    public static final int ALCANZAR = 0;
    /** Agita los dados en el puño y los lanza hacia el punto. */
    public static final int DADOS = 1;
    /** Echa una carta (o una ficha de dominó) con un golpe de muñeca hacia el punto. */
    public static final int CARTA = 2;
    /** Golpe de taco de billar hacia el punto (la bola blanca). */
    public static final int TACO = 3;
    /** Giro seco de la barra del futbolín (chut). */
    public static final int MANIVELA = 4;
    /** Empujón del mazo del air hockey hacia el punto. */
    public static final int PALA = 5;
    /** Celebraciones. */
    public static final int PUNO = 10, APLAUSO = 11, BAILE = 12;
    /** Reacciones al perder. */
    public static final int FACEPALM = 20, ENCOGERSE = 21;

    private Gestos() {}

    public static void hacer(@Nullable ServerPlayer p, int tipo, Vec3 objetivo) {
        if (p == null || objetivo == null) return;
        MesasRed.GestoPacket.enviar(p, tipo, objetivo);
    }

    /** Gesto de un jugador por su UUID (si está conectado en ese mundo). */
    public static void hacer(Level l, @Nullable UUID u, int tipo, Vec3 objetivo) {
        if (l == null || l.isClientSide || u == null) return;
        if (l.getPlayerByUUID(u) instanceof ServerPlayer sp) hacer(sp, tipo, objetivo);
    }

    /** Celebración al azar (puño, aplauso o bailecito). */
    public static void ganar(@Nullable ServerPlayer p, Vec3 mesa) {
        if (p == null) return;
        int[] t = {PUNO, APLAUSO, BAILE};
        hacer(p, t[p.getRandom().nextInt(t.length)], mesa);
    }

    /** Reacción al perder al azar (mano a la cara o encogerse de hombros). */
    public static void perder(@Nullable ServerPlayer p, Vec3 mesa) {
        if (p == null) return;
        hacer(p, p.getRandom().nextBoolean() ? FACEPALM : ENCOGERSE, mesa);
    }
}

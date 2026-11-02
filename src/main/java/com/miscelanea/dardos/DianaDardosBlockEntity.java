package com.miscelanea.dardos;

import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Partida de 301 para uno o dos jugadores por turnos: cada turno son 3 dardos y se resta lo que hagas; hay que llegar justo a 0 (si te pasas, el turno no
 * cuenta). Sectores, dobles, triples y diana como en una de verdad.
 */
public class DianaDardosBlockEntity extends BlockEntity {
    public static final int[] SECTOR = {20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5};
    /** Radios relativos (1 = borde de la zona que puntúa). */
    public static final float BULL = 0.07F, BULL25 = 0.15F, TRIPLE0 = 0.54F, TRIPLE1 = 0.62F, DOBLE0 = 0.9F, RADIO = 0.4F;
    /** Pasado este rato sin tirar (1 min), cualquiera puede empezar otra partida. */
    public static final int INACTIVA = 1200;
    /** Hasta dos jugadores por turnos (3 dardos cada uno). */
    @Nullable public UUID[] jug = new UUID[2];
    public String[] nom = {"", ""};
    public int[] puntos = {301, 301};
    public int jugadores, actual, turnoInicio = 301, dardos;
    public boolean fin;
    public long ultimaT;
    public float[] du = new float[3], dv = new float[3];
    public int[] dp = new int[3];
    public String ultimo = "";

    public DianaDardosBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.DIANA_DARDOS_BE.get(), p, s);
    }

    public Direction facing() {
        return getBlockState().getValue(DianaDardosBlock.FACING);
    }

    /** Puesto (0 o 1) del jugador en la partida, o -1 si no juega. */
    public int indice(Player p) {
        for (int i = 0; i < jugadores; i++) if (p.getUUID().equals(jug[i])) return i;
        return -1;
    }

    /** ¿Se puede empezar otra partida sin fastidiar a nadie? */
    boolean libre() {
        return jugadores == 0 || fin || level.getGameTime() - ultimaT > INACTIVA;
    }

    private void empezarCon(Player p) {
        jugadores = 1;
        jug[0] = p.getUUID();
        nom[0] = p.getGameProfile().getName();
        jug[1] = null;
        nom[1] = "";
        reiniciar(0);
    }

    /** Misma gente, marcadores a 301; empieza el jugador i. */
    private void reiniciar(int i) {
        puntos[0] = puntos[1] = 301;
        actual = i;
        turnoInicio = 301;
        dardos = 0;
        fin = false;
        ultimo = "";
        ultimaT = level.getGameTime();
    }

    void nueva(Player p) {
        int i = indice(p);
        if (!libre() && i < 0) {
            p.displayClientMessage(Component.translatable("miscelanea.dardos.ocupada", nombres()), true);
            return;
        }
        // quien ya juega reinicia la partida para los mismos jugadores; si no, partida nueva solo para él
        if (i >= 0 && !fin) reiniciar(i);
        else empezarCon(p);
        p.displayClientMessage(Component.translatable("miscelanea.dardos.nueva"), true);
        cambio();
    }

    public String nombres() {
        return jugadores >= 2 ? nom[0] + " / " + nom[1] : nom[0];
    }

    /** Puntos y texto ("T20", "D5", "BULL"...) de un punto (u, v) en la cara, en bloques desde el centro. */
    public static Object[] puntuar(double u, double v) {
        double r = Math.hypot(u, v) / RADIO;
        if (r > 1) return new Object[]{0, "FUERA"};
        if (r < BULL) return new Object[]{50, "BULL"};
        if (r < BULL25) return new Object[]{25, "25"};
        double ang = Math.toDegrees(Math.atan2(u, v));
        int s = SECTOR[Math.floorMod((int) Math.floor((ang + 9) / 18), 20)];
        if (r >= TRIPLE0 && r < TRIPLE1) return new Object[]{s * 3, "T" + s};
        if (r >= DOBLE0) return new Object[]{s * 2, "D" + s};
        return new Object[]{s, String.valueOf(s)};
    }

    /** Un dardo clavado en la diana en el punto del mundo dado. Devuelve false si no le tocaba tirar a ese jugador. */
    public boolean clavar(@Nullable Player p, Vec3 donde) {
        if (p == null) return false;
        long ahora = level.getGameTime();
        int i = indice(p);
        if (jugadores == 0 || i < 0 && libre()) {
            empezarCon(p);
            i = 0;
            p.displayClientMessage(Component.translatable("miscelanea.dardos.nueva"), true);
        } else if (fin && i >= 0) reiniciar(i);
        if (i < 0) {
            if (jugadores >= 2) {
                p.displayClientMessage(Component.translatable("miscelanea.dardos.ocupada", nombres()), true);
                return false;
            }
            // se apunta como segundo jugador y espera su turno
            jug[1] = p.getUUID();
            nom[1] = p.getGameProfile().getName();
            puntos[1] = 301;
            jugadores = 2;
            i = 1;
            for (Player o : level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(24)))
                o.displayClientMessage(Component.translatable("miscelanea.dardos.unido", nom[1]), true);
        }
        boolean parado = ahora - ultimaT > INACTIVA;
        if (dardos >= 3) {
            // el turno anterior ya acabó: le toca al siguiente (o a quien sea si el otro no tira en un minuto)
            int sig = (actual + 1) % jugadores;
            if (i != sig && !parado) {
                p.displayClientMessage(Component.translatable("miscelanea.dardos.no_turno", nom[sig]).withStyle(ChatFormatting.RED), true);
                return false;
            }
            actual = i;
            dardos = 0;
            turnoInicio = puntos[actual];
        } else if (i != actual) {
            if (!parado) {
                p.displayClientMessage(Component.translatable("miscelanea.dardos.no_turno", nom[actual]).withStyle(ChatFormatting.RED), true);
                return false;
            }
            actual = i;
            dardos = 0;
            turnoInicio = puntos[actual];
        }
        ultimaT = ahora;
        Direction f = facing(), u = f.getCounterClockWise();
        Vec3 c = Vec3.atCenterOf(worldPosition);
        double lu = (donde.x - c.x) * u.getStepX() + (donde.z - c.z) * u.getStepZ(), lv = donde.y - c.y;
        Object[] r = puntuar(lu, lv);
        int pts = (Integer) r[0];
        du[dardos] = (float) lu;
        dv[dardos] = (float) lv;
        dp[dardos] = pts;
        dardos++;
        ultimo = (String) r[1];
        level.playSound(null, worldPosition, ModSounds.DA_CLAVA.get(), SoundSource.BLOCKS, 1F, 0.9F + level.random.nextFloat() * 0.2F);
        int nuevo = puntos[actual] - pts;
        boolean finTurno = dardos == 3 || nuevo == 0;
        if (nuevo < 0 || nuevo == 1) {
            puntos[actual] = turnoInicio;
            dardos = 3;
            p.displayClientMessage(Component.translatable("miscelanea.dardos.pasado").withStyle(ChatFormatting.RED), true);
            avisarTurno();
        } else if (nuevo == 0) {
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) com.miscelanea.arcade.ArcadeRecords.registrar(sp, "dardos", turnoInicio);
            puntos[actual] = 0;
            level.playSound(null, worldPosition, ModSounds.FE_PREMIO_GORDO.get(), SoundSource.BLOCKS, 1F, 1F);
            for (Player o : level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(24)))
                o.displayClientMessage(Component.translatable("miscelanea.dardos.gana", nom[actual]).withStyle(ChatFormatting.GOLD), false);
            fin = true;
            dardos = 3;
        } else {
            puntos[actual] = nuevo;
            p.displayClientMessage(Component.translatable("miscelanea.dardos.tiro", r[1], pts, nuevo), true);
            if (pts >= 50) level.playSound(null, worldPosition, ModSounds.FE_PREMIO.get(), SoundSource.BLOCKS, 1F, 1.2F);
            if (finTurno) {
                if (p instanceof net.minecraft.server.level.ServerPlayer sp) com.miscelanea.arcade.ArcadeRecords.registrar(sp, "dardos", turnoInicio - nuevo);
                avisarTurno();
            }
        }
        cambio();
        return true;
    }

    /** Con dos jugadores, avisa a quien le toca ahora. */
    private void avisarTurno() {
        if (jugadores < 2) return;
        UUID sig = jug[(actual + 1) % jugadores];
        Player o = sig != null ? level.getPlayerByUUID(sig) : null;
        if (o != null) o.displayClientMessage(Component.translatable("miscelanea.dardos.turno", nom[(actual + 1) % jugadores]).withStyle(ChatFormatting.YELLOW), true);
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("nj", jugadores);
        for (int i = 0; i < 2; i++) {
            if (jug[i] != null) t.putUUID("j" + i, jug[i]);
            t.putString("nom" + i, nom[i]);
            t.putInt("q" + i, puntos[i]);
        }
        t.putInt("act", actual);
        t.putInt("ti", turnoInicio);
        t.putInt("n", dardos);
        t.putBoolean("fin", fin);
        t.putLong("ult_t", ultimaT);
        for (int i = 0; i < 3; i++) {
            t.putFloat("u" + i, du[i]);
            t.putFloat("v" + i, dv[i]);
            t.putInt("p" + i, dp[i]);
        }
        t.putString("ult", ultimo);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        jugadores = Math.max(0, Math.min(2, t.getInt("nj")));
        for (int i = 0; i < 2; i++) {
            jug[i] = t.hasUUID("j" + i) ? t.getUUID("j" + i) : null;
            nom[i] = t.getString("nom" + i);
            puntos[i] = t.contains("q" + i) ? t.getInt("q" + i) : 301;
        }
        // jugadores sin UUID guardado (partidas de antes): no cuentan
        while (jugadores > 0 && jug[jugadores - 1] == null) jugadores--;
        actual = Math.max(0, Math.min(Math.max(0, jugadores - 1), t.getInt("act")));
        turnoInicio = t.contains("ti") ? t.getInt("ti") : 301;
        dardos = t.getInt("n");
        fin = t.getBoolean("fin");
        ultimaT = t.getLong("ult_t");
        for (int i = 0; i < 3; i++) {
            du[i] = t.getFloat("u" + i);
            dv[i] = t.getFloat("v" + i);
            dp[i] = t.getInt("p" + i);
        }
        ultimo = t.getString("ult");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1);
    }
}

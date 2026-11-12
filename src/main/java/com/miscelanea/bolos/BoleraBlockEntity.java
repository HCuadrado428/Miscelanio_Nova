package com.miscelanea.bolos;

import com.miscelanea.feria.CasetaTiroBlockEntity;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bolos con reglas de verdad: 10 frames, plenos (X), semiplenos (/) y tiradas extra en el décimo. Los bolos caen por
 * el golpe de la bola y se tiran unos a otros. Coordenadas locales como la caseta de tiro (z hacia el fondo).
 */
public class BoleraBlockEntity extends BlockEntity {
    public static final int N = 10, CAIDA = 8;
    public static final float[] BX = new float[N], BZ = new float[N];
    public static final float RADIO_BOLO = 0.06F, RADIO_BOLA = 0.17F;

    static {
        int k = 0;
        for (int fila = 0; fila < 4; fila++)
            for (int i = 0; i <= fila; i++) {
                BX[k] = (i - fila / 2F) * 0.25F;
                BZ[k] = -0.3F + fila * 0.21F;
                k++;
            }
    }

    @Nullable public UUID jugador;
    public String nombre = "";
    public List<Integer> tiradas = new ArrayList<>();
    /** 0 de pie, 1 caído (sigue a la vista), 2 retirado. */
    public int[] estado = new int[N];
    public long[] caida = new long[N];
    public float[] dir = new float[N];
    public boolean enJuego;
    public long contacto = -1, lanzado;
    private int dePieAntes = N;
    private int bolaId = -1;
    private double[] qAntes;
    /** Última vez que alguien jugó en esta calle (para dejar empezar a otro tras un minuto sin tirar). */
    public long actividad;
    public static final int INACTIVA = 1200;

    public BoleraBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.BOLERA_BE.get(), p, s);
    }

    public Direction facing() {
        return getBlockState().getValue(BoleraBlock.FACING);
    }

    Vec3 mundo(double x, double y, double z) {
        double a = CasetaTiroBlockEntity.giro(facing()), c = Math.cos(a), s = Math.sin(a);
        return new Vec3(worldPosition.getX() + 0.5 + x * c + z * s, worldPosition.getY() + y, worldPosition.getZ() + 0.5 - x * s + z * c);
    }

    /** {x, z} locales de un punto del mundo. */
    double[] local(double wx, double wz) {
        double a = CasetaTiroBlockEntity.giro(facing()), c = Math.cos(a), s = Math.sin(a);
        double dx = wx - worldPosition.getX() - 0.5, dz = wz - worldPosition.getZ() - 0.5;
        return new double[]{dx * c - dz * s, dx * s + dz * c};
    }

    /** ¿Puede este jugador quitarle la calle al que está jugando? Solo si es suya, si acabó o si lleva 1 min sin tirar. */
    boolean puedeEmpezar(Player p) {
        if (jugador == null || p.getUUID().equals(jugador) || terminada()) return true;
        if (level.getGameTime() - actividad > INACTIVA) return true;
        Player dueno = level.getPlayerByUUID(jugador);
        return dueno == null || dueno.distanceToSqr(Vec3.atCenterOf(worldPosition)) > 48 * 48;
    }

    void nueva(Player p) {
        if (enJuego) return;
        if (!puedeEmpezar(p)) {
            p.displayClientMessage(Component.translatable("miscelanea.bolos.ocupada", nombre), true);
            return;
        }
        actividad = level.getGameTime();
        jugador = p.getUUID();
        nombre = p.getGameProfile().getName();
        tiradas.clear();
        rack();
        p.displayClientMessage(Component.translatable("miscelanea.bolos.nueva"), true);
        cambio();
    }

    private void rack() {
        for (int k = 0; k < N; k++) {
            estado[k] = 0;
            caida[k] = -1000;
        }
        dePieAntes = N;
    }

    public int dePie() {
        int n = 0;
        for (int e : estado) if (e == 0) n++;
        return n;
    }

    /** La bola de alguien sale hacia esta bolera. Devuelve false si no le toca. */
    boolean lanzamiento(Player p, int idBola) {
        if (enJuego) return false;
        if (jugador == null || terminada() || !p.getUUID().equals(jugador)) {
            if (!puedeEmpezar(p)) return false;
            jugador = p.getUUID();
            nombre = p.getGameProfile().getName();
            tiradas.clear();
            rack();
        }
        enJuego = true;
        contacto = -1;
        lanzado = level.getGameTime();
        actividad = lanzado;
        dePieAntes = dePie();
        bolaId = idBola;
        qAntes = null;
        cambio();
        return true;
    }

    public static void tick(Level l, BlockPos pos, BlockState s, BoleraBlockEntity be) {
        if (!be.enJuego) return;
        long ahora = l.getGameTime();
        boolean cambio = false;
        // la bola contra los bolos
        BolaBolos bola = l.getEntity(be.bolaId) instanceof BolaBolos b ? b : null;
        if (bola != null && bola.isAlive()) {
            double[] q = be.local(bola.getX(), bola.getZ());
            // solo cuenta la bola dentro de ESTA calle (antes el margen pillaba media calle de al lado)
            if (Math.abs(q[0]) < 0.5 && q[1] > -0.75 && q[1] < 0.75 && bola.getY() < pos.getY() + 0.8) {
                if (be.contacto < 0) be.contacto = ahora;
                Vec3 v = bola.getDeltaMovement();
                double[] vl = be.localDir(v.x, v.z);
                double[] q0 = be.qAntes != null ? be.qAntes : q;
                double sx = q[0] - q0[0], sz = q[1] - q0[1], l2 = sx * sx + sz * sz;
                for (int k = 0; k < N; k++) {
                    if (be.estado[k] != 0) continue;
                    double u = l2 < 1e-9 ? 1 : Math.max(0, Math.min(1, ((BX[k] - q0[0]) * sx + (BZ[k] - q0[1]) * sz) / l2));
                    double dx = BX[k] - (q0[0] + sx * u), dz = BZ[k] - (q0[1] + sz * u);
                    if (Math.hypot(dx, dz) < RADIO_BOLA + RADIO_BOLO + 0.02) {
                        double ang = Math.atan2(vl[1] * 2 + dz / (Math.hypot(dx, dz) + 1e-6), vl[0] * 2 + dx / (Math.hypot(dx, dz) + 1e-6));
                        be.tumbar(k, ahora, (float) ang);
                        bola.setDeltaMovement(v.scale(0.92));
                        cambio = true;
                    }
                }
            }
            be.qAntes = q;
            if (be.contacto < 0 && ahora - be.lanzado > 400) {
                bola.devolver();
                bola.discard();
            }
        }
        // los bolos que caen tiran a los de al lado
        for (int k = 0; k < N; k++) {
            if (be.estado[k] != 1 || ahora - be.caida[k] != 3) continue;
            for (int j = 0; j < N; j++) {
                if (be.estado[j] != 0) continue;
                double dx = BX[j] - BX[k], dz = BZ[j] - BZ[k], d = Math.hypot(dx, dz);
                if (d > 0.36) continue;
                double rel = Math.atan2(dz, dx) - be.dir[k];
                rel = Math.atan2(Math.sin(rel), Math.cos(rel));
                if (Math.abs(rel) < 0.95 && l.random.nextFloat() < 0.85F) {
                    be.tumbar(j, ahora, (float) (be.dir[k] + rel * 0.6 + (l.random.nextFloat() - 0.5) * 0.5));
                    cambio = true;
                }
            }
        }
        if (cambio) be.cambio();
        // fin de la tirada: un rato después del primer contacto, o la bola se perdió
        boolean perdida = bola == null || !bola.isAlive();
        if ((be.contacto >= 0 && ahora - be.contacto > 50) || (perdida && ahora - be.lanzado > 10 && (be.contacto < 0 || ahora - be.contacto > 30)))
            be.finTirada((ServerLevel) l, bola);
    }

    double[] localDir(double vx, double vz) {
        double a = CasetaTiroBlockEntity.giro(facing()), c = Math.cos(a), s = Math.sin(a);
        return new double[]{vx * c - vz * s, vx * s + vz * c};
    }

    private void tumbar(int k, long ahora, float ang) {
        estado[k] = 1;
        caida[k] = ahora;
        dir[k] = ang;
        if (level instanceof ServerLevel sl) {
            Vec3 c = mundo(BX[k], 0.3, BZ[k]);
            level.playSound(null, c.x, c.y, c.z, ModSounds.BO_BOLO.get(), SoundSource.BLOCKS, 0.9F, 0.85F + level.random.nextFloat() * 0.3F);
        }
    }

    private void finTirada(ServerLevel l, @Nullable BolaBolos bola) {
        enJuego = false;
        actividad = l.getGameTime();
        int tumbados = dePieAntes - dePie();
        tiradas.add(tumbados);
        Player p = jugador != null ? l.getPlayerByUUID(jugador) : null;
        // la bola vuelve a quien la tiró; si ya no está (se paró sola o se fue lejos) se devuelve ella misma al pararse
        if (bola != null && bola.isAlive()) {
            bola.devolver();
            bola.discard();
        }
        boolean pleno = tumbados == N && dePieAntes == N, semipleno = !pleno && dePie() == 0;
        Vec3 c = mundo(0, 1, 0);
        if (pleno || semipleno) {
            l.playSound(null, c.x, c.y, c.z, pleno ? ModSounds.BO_PLENO.get() : ModSounds.FE_PREMIO.get(), SoundSource.BLOCKS, 1F, 1F);
            l.sendParticles(ParticleTypes.FIREWORK, c.x, c.y + 0.5, c.z, pleno ? 30 : 12, 0.3, 0.3, 0.3, 0.1);
        }
        if (p != null) {
            String clave = pleno ? "miscelanea.bolos.pleno" : semipleno ? "miscelanea.bolos.semipleno" : tumbados == 0 ? "miscelanea.bolos.cero" : "miscelanea.bolos.tumbados";
            p.displayClientMessage(Component.translatable(clave, tumbados).withStyle(pleno ? ChatFormatting.GOLD : ChatFormatting.WHITE), true);
            if (pleno) com.miscelanea.logros.LogrosMiscelanea.dar(p, "bolos_pleno");
            else if (tumbados == 0 && dePieAntes == N) com.miscelanea.logros.LogrosMiscelanea.dar(p, "bolos_cero");
        }
        for (int k = 0; k < N; k++) if (estado[k] == 1) estado[k] = 2;
        if (terminada()) {
            int total = Marcador.total(tiradas);
            if (p != null) p.displayClientMessage(Component.translatable("miscelanea.bolos.fin", total).withStyle(ChatFormatting.GREEN), false);
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) com.miscelanea.arcade.ArcadeRecords.registrar(sp, "bolos", total);
            for (Player o : l.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(32)))
                if (o != p) o.displayClientMessage(Component.translatable("miscelanea.bolos.fin_otro", nombre, total), false);
            rack();
        } else if (Marcador.rackNuevo(tiradas)) rack();
        dePieAntes = dePie();
        cambio();
    }

    public boolean terminada() {
        return Marcador.terminada(tiradas);
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (jugador != null) t.putUUID("jug", jugador);
        t.putString("nom", nombre);
        t.putIntArray("tir", tiradas.stream().mapToInt(Integer::intValue).toArray());
        t.putIntArray("est", estado);
        t.putLongArray("caida", caida);
        int[] d = new int[N];
        for (int k = 0; k < N; k++) d[k] = Math.round(dir[k] * 1000);
        t.putIntArray("dir", d);
        t.putBoolean("juego", enJuego);
        t.putLong("activ", actividad);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        jugador = t.hasUUID("jug") ? t.getUUID("jug") : null;
        nombre = t.getString("nom");
        tiradas.clear();
        for (int v : t.getIntArray("tir")) tiradas.add(v);
        int[] e = t.getIntArray("est");
        if (e.length == N) estado = e;
        long[] c = t.getLongArray("caida");
        if (c.length == N) caida = c;
        int[] d = t.getIntArray("dir");
        if (d.length == N) for (int k = 0; k < N; k++) dir[k] = d[k] / 1000F;
        enJuego = t.getBoolean("juego") && level != null && level.isClientSide;
        actividad = t.getLong("activ");
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
        return new AABB(worldPosition).inflate(1, 2, 1);
    }
}

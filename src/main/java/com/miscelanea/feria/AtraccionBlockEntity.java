package com.miscelanea.feria;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Estado de una atracción: si gira, y el ángulo como función del tiempo de juego (ángulo de referencia + velocidad por
 * los tics desde que se cambió), así cliente y servidor lo calculan igual sin mandar nada cada tic.
 */
public class AtraccionBlockEntity extends BlockEntity {
    /** Noria: radio, altura del eje, cuánto cuelga la cabina (gancho → suelo) y media anchura de la rueda. */
    public static final double R_NORIA = 9, CY_NORIA = 13.5, ALTO_CABINA = 3.0, MEDIA_RUEDA = 1.35;
    public boolean gira;
    public float angulo0;
    public long t0;
    /** Velocidad que se va ajustando poco a poco al encender o parar (arranque y frenada suaves). */
    public float vel;

    public AtraccionBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.ATRACCION_BE.get(), p, s);
    }

    public int tipo() {
        return getBlockState().getBlock() instanceof AtraccionBlock a ? a.tipo : 0;
    }

    public Direction facing() {
        return getBlockState().getValue(AtraccionBlock.FACING);
    }

    public int asientos() {
        return tipo() == AtraccionBlock.NORIA ? 10 : 8;
    }

    public float velMax() {
        return tipo() == AtraccionBlock.NORIA ? (float) (2 * Math.PI / (20 * 60)) : (float) (2 * Math.PI / (20 * 12));
    }

    /** Ángulo en un instante (tics con fracción). */
    public float angulo(float tiempo) {
        return angulo0 + vel * (tiempo - t0);
    }

    void alternar() {
        if (level == null) return;
        float ahora = level.getGameTime();
        angulo0 = (float) (angulo(ahora) % (2 * Math.PI));
        t0 = level.getGameTime();
        gira = !gira;
        level.playSound(null, worldPosition, gira ? ModSounds.FE_ARRANCA.get() : ModSounds.FE_PARA.get(), SoundSource.BLOCKS, 1F, 1F);
        cambio();
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void tick(Level l, BlockPos pos, BlockState s, AtraccionBlockEntity be) {
        // arranque y frenada suaves: la velocidad cambia poco a poco (se rehace la referencia para que no salte)
        float objetivo = be.gira ? be.velMax() : 0;
        if (be.vel != objetivo) {
            float ahora = l.getGameTime();
            be.angulo0 = (float) (be.angulo(ahora) % (2 * Math.PI));
            be.t0 = l.getGameTime();
            float paso = be.velMax() / 80;
            be.vel = Math.abs(objetivo - be.vel) < paso ? objetivo : be.vel + Math.signum(objetivo - be.vel) * paso;
        }
        if (l.isClientSide) return;
        if (l.getGameTime() % 40 == 0) be.revisarAsientos();
        if (be.gira && be.tipo() == AtraccionBlock.TIOVIVO && l.getGameTime() % 200 == 0)
            l.playSound(null, pos, ModSounds.FE_TIOVIVO.get(), SoundSource.RECORDS, 1.2F, 1F);
        if (be.gira && be.tipo() == AtraccionBlock.NORIA && l.getGameTime() % 160 == 0)
            l.playSound(null, pos, ModSounds.FE_NORIA.get(), SoundSource.BLOCKS, 0.5F, 0.9F + l.random.nextFloat() * 0.2F);
    }

    void revisarAsientos() {
        boolean[] hay = new boolean[asientos()];
        for (AsientoFeria a : level.getEntitiesOfClass(AsientoFeria.class, new AABB(worldPosition).inflate(14)))
            if (a.atraccion().equals(worldPosition) && a.indice() < hay.length) {
                if (hay[a.indice()]) a.discard();
                hay[a.indice()] = true;
            }
        for (int i = 0; i < hay.length; i++) {
            if (hay[i]) continue;
            AsientoFeria a = new AsientoFeria(tipo() == AtraccionBlock.NORIA ? ModEntities.CABINA_NORIA.get() : ModEntities.ASIENTO_FERIA.get(), level);
            a.preparar(worldPosition, i);
            level.addFreshEntity(a);
        }
    }

    /** Posición del asiento i (dónde va la entidad) en un instante, y hacia dónde mira. {x, y, z, yaw} */
    public double[] puesto(int i, float tiempo) {
        Direction f = facing();
        Direction u = f.getClockWise();
        double a = angulo(tiempo) + 2 * Math.PI * i / asientos();
        Vec3 base = Vec3.atBottomCenterOf(worldPosition);
        if (tipo() == AtraccionBlock.NORIA) {
            double x = Math.cos(a) * R_NORIA, y = Math.sin(a) * R_NORIA;
            return new double[]{base.x + u.getStepX() * x, base.y + CY_NORIA + y - ALTO_CABINA, base.z + u.getStepZ() * x, f.toYRot()};
        }
        double R = 2.6;
        double bob = 0.62 + 0.3 * Math.sin(angulo(tiempo) * 5 + i * 1.7);
        double x = Math.cos(a) * R, z = Math.sin(a) * R;
        float yaw = (float) Math.toDegrees(a);
        return new double[]{base.x + x, base.y + bob, base.z + z, yaw};
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putBoolean("gira", gira);
        t.putFloat("a0", angulo0);
        t.putLong("t0", t0);
        t.putFloat("vel", vel);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        gira = t.getBoolean("gira");
        angulo0 = t.getFloat("a0");
        t0 = t.getLong("t0");
        vel = t.getFloat("vel");
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
        return new AABB(worldPosition).inflate(14, 26, 14);
    }
}

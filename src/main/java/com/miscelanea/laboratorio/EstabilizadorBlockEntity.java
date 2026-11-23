package com.miscelanea.laboratorio;

import com.miscelanea.glitch.OndaGlitch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Mira cada cuarto de segundo si hay ondas de glitch cerca y las para. */
public class EstabilizadorBlockEntity extends BlockEntity {
    public static final double RADIO = 16;

    private int calmadas;

    public EstabilizadorBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.ESTABILIZADOR_BE.get(), p, s);
    }

    public int calmadas() { return calmadas; }

    public static void tick(Level l, BlockPos p, BlockState s, EstabilizadorBlockEntity be) {
        if (!(l instanceof ServerLevel sl) || sl.getGameTime() % 5 != 0) return;
        Vec3 c = Vec3.atCenterOf(p);
        int n = OndaGlitch.calmar(sl, c, RADIO);
        if (n > 0) {
            be.calmadas += n;
            be.setChanged();
            sl.playSound(null, p, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 1.4F);
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.9, c.z, 24, 0.5, 0.5, 0.5, 0.05);
        } else if (sl.getGameTime() % 40 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.9, c.z, 1, 0.2, 0.2, 0.2, 0.0);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("Calmadas", calmadas);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        calmadas = t.getInt("Calmadas");
    }
}

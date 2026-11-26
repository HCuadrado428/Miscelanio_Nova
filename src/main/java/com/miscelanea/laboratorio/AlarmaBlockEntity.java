package com.miscelanea.laboratorio;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Lo que hace la alarma mientras está armada: mirar si hay alguien cerca y, si lo hay, sonar diez segundos. */
public class AlarmaBlockEntity extends BlockEntity {
    public static final double RADIO = 6.0;
    public static final int SONAR = 200;

    private int restante;

    public AlarmaBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.ALARMA_BE.get(), p, s);
    }

    /** ¿Debería saltar la alarma por este jugador? (no si va agachado, lleva bata, es espectador o está en creativo) */
    public static boolean intruso(Player p) {
        if (p.isSpectator() || p.isCreative() || p.isShiftKeyDown()) return false;
        return !p.getItemBySlot(EquipmentSlot.CHEST).is(LabRegistro.BATA.get());
    }

    public static void tick(Level l, BlockPos p, BlockState s, AlarmaBlockEntity be) {
        if (!(l instanceof ServerLevel sl)) return;
        if (be.restante > 0) {
            be.restante--;
            if (be.restante % 20 == 0) {
                sl.playSound(null, p, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 3.0F, 1.9F);
                sl.playSound(null, p, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 3.0F, 1.2F);
            } else if (be.restante % 20 == 10) {
                sl.playSound(null, p, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 3.0F, 1.2F);
            }
            if (be.restante == 0) sl.setBlock(p, s.setValue(AlarmaBlock.SONANDO, false), 3);
            return;
        }
        if (!s.getValue(AlarmaBlock.ARMADA) || sl.getGameTime() % 5 != 0) return;
        for (Player j : sl.getEntitiesOfClass(Player.class, new AABB(p).inflate(RADIO), AlarmaBlockEntity::intruso)) {
            if (j.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) > RADIO * RADIO) continue;
            be.restante = SONAR;
            sl.setBlock(p, s.setValue(AlarmaBlock.SONANDO, true), 3);
            return;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("Restante", restante);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        restante = t.getInt("Restante");
    }
}

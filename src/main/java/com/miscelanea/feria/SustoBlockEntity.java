package com.miscelanea.feria;

import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Cuándo saltó el muñeco por última vez (tiempo de juego); el cliente anima a partir de ahí. */
public class SustoBlockEntity extends BlockEntity {
    public static final int DURA = 50;
    /** Último susto visto por este cliente (para el destello y el temblor de cámara). */
    public static BlockPos ultimoPos = BlockPos.ZERO;
    public static long ultimoT = -1000;
    public long t = -1000;

    public SustoBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.SUSTO_BE.get(), p, s);
    }

    public int tipo() {
        return getBlockState().getValue(SustoBlock.TIPO);
    }

    public Direction facing() {
        return getBlockState().getValue(SustoBlock.FACING);
    }

    public static void tick(Level l, BlockPos pos, BlockState s, SustoBlockEntity be) {
        if (l.getGameTime() % 2 != 0 || l.getGameTime() - be.t < 80) return;
        AABB zona = new AABB(pos.relative(s.getValue(SustoBlock.FACING), 2)).inflate(2.2, 1.5, 2.2);
        for (VagonetaBruja v : l.getEntitiesOfClass(VagonetaBruja.class, zona))
            if (v.isVehicle()) {
                be.asustar(false);
                return;
            }
    }

    void asustar(boolean prueba) {
        if (level == null) return;
        t = level.getGameTime();
        SoundEvent snd = switch (tipo()) {
            case 0 -> ModSounds.FE_BRUJA.get();
            case 1 -> ModSounds.FE_HUESOS.get();
            case 2 -> ModSounds.FE_TRUENO.get();
            default -> ModSounds.FE_FANTASMA.get();
        };
        level.playSound(null, worldPosition, snd, SoundSource.BLOCKS, prueba ? 0.6F : 1.2F, 0.95F + level.random.nextFloat() * 0.1F);
        if (level instanceof ServerLevel sl) {
            Direction f = facing();
            double x = worldPosition.getX() + 0.5 + f.getStepX() * 0.8, z = worldPosition.getZ() + 0.5 + f.getStepZ() * 0.8;
            sl.sendParticles(tipo() == 2 ? ParticleTypes.FLAME : ParticleTypes.SOUL, x, worldPosition.getY() + 1, z, 12, 0.3, 0.4, 0.3, 0.02);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, worldPosition.getY() + 0.5, z, 8, 0.3, 0.2, 0.3, 0.01);
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("t", t);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        long nuevo = tag.getLong("t");
        if (level != null && level.isClientSide && nuevo != t && level.getGameTime() - nuevo < 10) {
            ultimoPos = worldPosition;
            ultimoT = nuevo;
        }
        t = nuevo;
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
        return new AABB(worldPosition).inflate(1.5);
    }
}

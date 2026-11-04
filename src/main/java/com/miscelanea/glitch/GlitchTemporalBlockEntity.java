package com.miscelanea.glitch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Guarda qué había antes en el sitio de un {@link GlitchTemporalBlock} y si al acabar hay que devolverlo (modo solo
 * visual) o dejar aire. Va en el propio bloque para que sobreviva a que se descargue el chunk o se pare el servidor.
 */
public class GlitchTemporalBlockEntity extends BlockEntity {
    /** El bloque original, tal cual se guarda en NBT (se lee al resolverlo, cuando ya hay mundo). */
    private CompoundTag original = new CompoundTag();
    private boolean destruir = true;

    public GlitchTemporalBlockEntity(BlockPos pos, BlockState st) {
        super(GlitchRegistro.GLITCH_TEMPORAL_BE.get(), pos, st);
    }

    public void preparar(BlockState orig, boolean destruir) {
        this.original = NbtUtils.writeBlockState(orig);
        this.destruir = destruir;
        setChanged();
    }

    public boolean destruir() {
        return destruir;
    }

    /** Lo que había antes (aire si no se sabe). */
    public BlockState original(Level level) {
        if (original.isEmpty()) return Blocks.AIR.defaultBlockState();
        try {
            return NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK), original);
        } catch (Exception e) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("original", original);
        tag.putBoolean("destruir", destruir);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        original = tag.getCompound("original");
        destruir = !tag.contains("destruir") || tag.getBoolean("destruir");
    }
}

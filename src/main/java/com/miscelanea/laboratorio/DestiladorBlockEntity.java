package com.miscelanea.laboratorio;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModPotions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lo que lleva dentro el destilador térmico: suavis seco que se está destilando y sedante ya listo (entre las dos
 * cosas, como mucho {@link #MAX}). Cada suavis tarda {@link #TICKS_POR_UNIDAD} ticks y solo avanza si hay fuego debajo.
 * Se saca con una botella de cristal: cada sedante listo es una Poción de sedante.
 */
public class DestiladorBlockEntity extends BlockEntity {
    public static final int MAX = 4;
    public static final int TICKS_POR_UNIDAD = 400;

    private int suavis, listo, progreso;

    public DestiladorBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.DESTILADOR_BE.get(), p, s);
    }

    public int suavis() { return suavis; }
    public int listo() { return listo; }

    /** ¿Hay una fuente de calor justo debajo? */
    public static boolean calor(Level l, BlockPos p) {
        BlockPos b = p.below();
        BlockState s = l.getBlockState(b);
        if (s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE) || s.is(Blocks.MAGMA_BLOCK)) return true;
        if (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) return true;
        if (s.getBlock() instanceof AbstractFurnaceBlock && s.getValue(AbstractFurnaceBlock.LIT)) return true;
        return l.getFluidState(b).is(FluidTags.LAVA);
    }

    /** Mete un suavis seco. Devuelve false si está lleno. */
    public boolean meter() {
        if (suavis + listo >= MAX) return false;
        suavis++;
        setChanged();
        return true;
    }

    /** Saca un sedante listo como poción (vacío si no hay ninguno). */
    public ItemStack sacar() {
        if (listo <= 0) return ItemStack.EMPTY;
        listo--;
        setChanged();
        return PotionUtils.setPotion(new ItemStack(Items.POTION), ModPotions.SEDANTE.get());
    }

    public static void tick(Level l, BlockPos p, BlockState s, DestiladorBlockEntity be) {
        if (l.isClientSide || be.suavis <= 0 || !calor(l, p)) return;
        be.progreso++;
        if (l instanceof ServerLevel sl && be.progreso % 10 == 0)
            sl.sendParticles(ParticleTypes.BUBBLE_POP, p.getX() + 0.5, p.getY() + 0.7, p.getZ() + 0.5, 2, 0.15, 0.1, 0.15, 0.02);
        if (be.progreso >= TICKS_POR_UNIDAD) {
            be.progreso = 0;
            be.suavis--;
            be.listo++;
            be.setChanged();
            l.playSound(null, p, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (l instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.02);
        }
    }

    /** Clic derecho: suavis seco para meter, botella de cristal para sacar y mano vacía para ver cómo va. */
    public void alInteractuar(Player p, InteractionHand h) {
        ItemStack st = p.getItemInHand(h);
        if (st.is(ModItems.SUAVIS_SECO.get())) {
            if (!meter()) p.displayClientMessage(Component.translatable("laboratorio.miscelanea.destilador.lleno").withStyle(ChatFormatting.RED), true);
            else if (!p.getAbilities().instabuild) st.shrink(1);
        } else if (st.is(Items.GLASS_BOTTLE) && listo > 0) {
            ItemStack pocion = sacar();
            if (!p.getAbilities().instabuild) st.shrink(1);
            if (!p.getInventory().add(pocion)) p.drop(pocion, false);
            level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.destilador.estado", suavis, listo,
                    Component.translatable(calor(level, worldPosition) ? "laboratorio.miscelanea.destilador.caliente" : "laboratorio.miscelanea.destilador.frio")), true);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("Suavis", suavis);
        t.putInt("Listo", listo);
        t.putInt("Progreso", progreso);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        suavis = t.getInt("Suavis");
        listo = t.getInt("Listo");
        progreso = t.getInt("Progreso");
    }
}

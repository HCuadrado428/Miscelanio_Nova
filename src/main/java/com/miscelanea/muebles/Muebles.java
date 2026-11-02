package com.miscelanea.muebles;

import com.miscelanea.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;

/** Utilidades compartidas: color de la tela (tintes) y sentarse. */
public final class Muebles {
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    private Muebles() {}

    /** Clic derecho con un tinte: cambia el color de la tela. */
    public static InteractionResult tenir(BlockState s, Level level, BlockPos pos, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!(st.getItem() instanceof DyeItem dye) || !s.hasProperty(COLOR)) return InteractionResult.PASS;
        if (s.getValue(COLOR) == dye.getDyeColor()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlock(pos, s.setValue(COLOR, dye.getDyeColor()), 3);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!p.getAbilities().instabuild) st.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Sienta al jugador en el mueble (altura del cojín en bloques) mirando hacia {@code mira}. */
    public static InteractionResult sentar(Level level, BlockPos pos, Player p, double altura, Direction mira) {
        if (p.isShiftKeyDown() || p.isPassenger()) return InteractionResult.PASS;
        if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 9) return InteractionResult.PASS;
        if (!level.getEntitiesOfClass(Asiento.class, new AABB(pos)).isEmpty()) return InteractionResult.PASS;   // ocupado
        if (!level.isClientSide) {
            Asiento a = new Asiento(ModEntities.ASIENTO.get(), level);
            a.setPos(pos.getX() + 0.5, pos.getY() + altura - Asiento.SOBRE_COJIN, pos.getZ() + 0.5);
            a.setYRot(mira.toYRot());
            level.addFreshEntity(a);
            p.startRiding(a);
            p.setYRot(mira.toYRot());
            p.setYHeadRot(mira.toYRot());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static int rgb(DyeColor c) {
        float[] f = c.getTextureDiffuseColors();
        return ((int) (f[0] * 255) << 16) | ((int) (f[1] * 255) << 8) | (int) (f[2] * 255);
    }
}

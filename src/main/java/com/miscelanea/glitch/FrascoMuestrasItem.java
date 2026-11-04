package com.miscelanea.glitch;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Frasco de muestras (solo de creativo): una botella vacía para recoger «el glitch». Clic derecho sobre un bloque
 * corrupto (del núcleo de error o de una onda de glitch), sobre alguien con el efecto «Glitch» o dentro de una onda que
 * siga viva: se llena y se vuelve una {@link MuestraGlitchItem muestra de glitch}. El bloque del que se saca se deshace.
 */
public class FrascoMuestrasItem extends Item {
    public FrascoMuestrasItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState st = level.getBlockState(pos);
        boolean corrupto = st.is(ModBlocks.BLOQUE_CORRUPTO.get());
        if (!corrupto && !st.is(GlitchRegistro.GLITCH_TEMPORAL.get())) return InteractionResult.PASS;
        Player p = ctx.getPlayer();
        if (p == null) return InteractionResult.PASS;
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            if (!llenar(sl, p, ctx.getHand(), pos.getCenter(), donde(pos))) return InteractionResult.FAIL;
            // el bloque se deshace (el del glitch vuelve a lo que fuera si el servidor está en modo visual)
            if (corrupto) {
                sl.levelEvent(2001, pos, Block.getId(st));
                sl.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else {
                GlitchTemporalBlock.resolver(sl, pos, false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack st, Player p, LivingEntity e, InteractionHand mano) {
        if (!e.hasEffect(GlitchRegistro.EFECTO.get())) return InteractionResult.PASS;
        if (p.level() instanceof ServerLevel sl) {
            if (!llenar(sl, p, mano, e.position().add(0, e.getBbHeight() * 0.5, 0), donde(e.blockPosition()))) return InteractionResult.FAIL;
            e.removeEffect(GlitchRegistro.EFECTO.get());
        }
        return InteractionResult.sidedSuccess(p.level().isClientSide);
    }

    /** En el aire, dentro de una onda que sigue viva. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand mano) {
        ItemStack st = p.getItemInHand(mano);
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            if (!OndaGlitch.dentro(level, p.position())) return InteractionResultHolder.pass(st);
            Vec3 m = p.getEyePosition().add(p.getViewVector(1.0F).scale(0.8));
            if (!llenar(sl, p, mano, m, donde(p.blockPosition()))) return InteractionResultHolder.fail(st);
            return InteractionResultHolder.success(p.getItemInHand(mano));
        }
        return InteractionResultHolder.pass(st);
    }

    private static String donde(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    /**
     * Cambia un frasco de la mano por una muestra. La muestra nunca se tira al suelo (estallaría): si no cabe en el
     * inventario, no se recoge.
     */
    private static boolean llenar(ServerLevel sl, Player p, InteractionHand mano, Vec3 de, String donde) {
        ItemStack frasco = p.getItemInHand(mano);
        ItemStack muestra = MuestraGlitchItem.recogida(donde);
        boolean creativo = p.getAbilities().instabuild;
        if (!creativo && frasco.getCount() == 1) {
            p.setItemInHand(mano, muestra);
        } else {
            if (p.getInventory().getFreeSlot() < 0) {
                p.displayClientMessage(Component.translatable("item.miscelanea.frasco_muestras.lleno").withStyle(ChatFormatting.RED), true);
                return false;
            }
            p.getInventory().add(muestra);
            if (!creativo) frasco.shrink(1);
        }
        sl.playSound(null, de.x, de.y, de.z, GlitchRegistro.LLENAR.get(), SoundSource.PLAYERS, 1.0F, 0.9F + sl.random.nextFloat() * 0.2F);
        // los píxeles se meten en el frasco
        Vec3 mano3 = p.getEyePosition().add(p.getViewVector(1.0F).scale(0.5)).add(0, -0.35, 0);
        for (int i = 0; i < 12; i++) {
            Vec3 a = de.add((sl.random.nextDouble() - 0.5) * 0.8, (sl.random.nextDouble() - 0.5) * 0.8, (sl.random.nextDouble() - 0.5) * 0.8);
            Vec3 v = mano3.subtract(a).scale(0.12);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, a.x, a.y, a.z, 0, v.x, v.y, v.z, 1.0);
        }
        sl.sendParticles(GlitchRegistro.PARTICULA.get(), de.x, de.y, de.z, 16, 0.35, 0.35, 0.35, 0.0);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag f) {
        tip.add(Component.translatable("item.miscelanea.frasco_muestras.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tip.add(Component.translatable("item.miscelanea.frasco_muestras.uso").withStyle(ChatFormatting.GRAY));
    }
}

package com.miscelanea.tortura.heridas;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Venda: se mantiene pulsado un momento (como un estimulante de Fallout / las vendas de Resident Evil) y cura la peor
 * herida, tuya o del jugador al que miras. La parte vendada deja de estar tullida mientras lleva la venda.
 */
public class VendaItem extends Item {
    private static final double ALCANCE = 3.5;
    private static final int DURACION = 24;

    public VendaItem(Properties p) {
        super(p);
    }

    @Override
    public int getUseDuration(ItemStack st) {
        return DURACION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack st) {
        return UseAnim.BRUSH;   // va y viene, como dando vueltas a la venda
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand mano) {
        p.startUsingItem(mano);
        return InteractionResultHolder.consume(p.getItemInHand(mano));
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack st, int quedan) {
        if (!level.isClientSide && (DURACION - quedan) % 8 == 0)
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.WOOL_HIT, SoundSource.PLAYERS, 0.7F, 1.1F + e.getRandom().nextFloat() * 0.3F);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack st, Level level, LivingEntity e) {
        if (level.isClientSide || !(e instanceof ServerPlayer p)) return st;
        if (!Heridas.activo(level)) {
            p.displayClientMessage(Component.translatable("miscelanea.heridas.desactivadas").withStyle(ChatFormatting.GRAY), true);
            return st;
        }
        ServerPlayer otro = objetivo(p);
        ServerPlayer paciente = otro != null ? otro : p;
        Parte parte = Heridas.vendar(paciente);
        if (parte == null) {
            p.displayClientMessage(Component.translatable("miscelanea.heridas.nada_que_vendar").withStyle(ChatFormatting.GRAY), true);
            return st;
        }
        Component zona = Component.translatable(parte.clave());
        if (paciente == p) {
            p.displayClientMessage(Component.translatable("miscelanea.heridas.vendado_yo", zona).withStyle(ChatFormatting.GREEN), true);
        } else {
            p.displayClientMessage(Component.translatable("miscelanea.heridas.vendado_otro", paciente.getDisplayName(), zona).withStyle(ChatFormatting.GREEN), true);
            paciente.displayClientMessage(Component.translatable("miscelanea.heridas.te_vendan", p.getDisplayName(), zona).withStyle(ChatFormatting.GREEN), true);
        }
        level.playSound(null, paciente.getX(), paciente.getY(), paciente.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.2F);
        if (level instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, paciente.getX(), paciente.getY() + paciente.getBbHeight() * 0.6, paciente.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
        if (!p.getAbilities().instabuild) st.shrink(1);
        return st;
    }

    /** El jugador herido al que estás mirando (de cerca). */
    @Nullable
    private static ServerPlayer objetivo(ServerPlayer p) {
        Vec3 ojo = p.getEyePosition();
        Vec3 dir = p.getViewVector(1.0F);
        Vec3 fin = ojo.add(dir.scale(ALCANCE));
        AABB caja = p.getBoundingBox().expandTowards(dir.scale(ALCANCE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, ojo, fin, caja,
                x -> x instanceof ServerPlayer o && o != p && o.isAlive() && !o.isSpectator() && !Heridas.leer(o).lista.isEmpty(),
                ALCANCE * ALCANCE);
        return hit != null && hit.getEntity() instanceof ServerPlayer o ? o : null;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.miscelanea.venda.desc").withStyle(ChatFormatting.GRAY));
    }
}

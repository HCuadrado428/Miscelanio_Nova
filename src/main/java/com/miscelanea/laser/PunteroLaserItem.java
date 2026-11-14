package com.miscelanea.laser;

import com.miscelanea.ConfigServidor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Puntero láser: mantén el clic derecho y sale un rayo fino (rojo, verde, azul o morado) hasta 64 bloques, con su punto
 * brillante donde da. Los gatos persiguen el punto, los mobs lo miran y si le das en los ojos a alguien le deslumbras
 * (eso lo calcula el cliente de la víctima: no hace daño).
 */
public class PunteroLaserItem extends Item {
    public PunteroLaserItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        p.startUsingItem(hand);
        if (!level.isClientSide)
            level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), LaserRegistro.ENCENDER.get(), SoundSource.PLAYERS, 0.6F, 0.95F + p.getRandom().nextFloat() * 0.1F);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int quedan) {
        if (level instanceof ServerLevel sl) tickServidor(sl, e);
    }

    @Override
    public void onStopUsing(ItemStack s, LivingEntity e, int quedan) {
        if (!e.level().isClientSide)
            e.level().playSound(null, e.getX(), e.getEyeY(), e.getZ(), LaserRegistro.APAGAR.get(), SoundSource.PLAYERS, 0.5F, 0.95F + e.getRandom().nextFloat() * 0.1F);
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.NONE;
    }

    @Override
    public boolean canContinueUsing(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem();
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean cambio) {
        return cambio || a.getItem() != b.getItem();
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        int c = Laser.color(s);
        tip.add(Component.translatable("item.miscelanea.puntero_laser.color",
                Component.translatable("item.miscelanea.puntero_laser.color." + Laser.NOMBRES[c]).withStyle(s2 -> s2.withColor(Laser.COLORES[c])))
                .withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.miscelanea.puntero_laser.ayuda").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("item.miscelanea.puntero_laser.tinte").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ------------------------------------------------------------------ servidor: gatos y mobs

    private static void tickServidor(ServerLevel sl, LivingEntity e) {
        Laser.Impacto im = Laser.rayo(e, 1.0F);
        if (!im.toca()) return;
        Vec3 p = im.punto();
        long t = sl.getGameTime();
        if (ConfigServidor.laserMobsMiran()) {
            for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(p, p).inflate(10), x -> x.isAlive() && x != e && !(x instanceof Cat || x instanceof Ocelot))) {
                if (m.distanceToSqr(p) > 100 || !m.hasLineOfSight(e) && m.distanceToSqr(p) > 16) continue;
                m.getLookControl().setLookAt(p.x, p.y, p.z, 30.0F, 30.0F);
            }
        }
        if (!ConfigServidor.laserGatos()) return;
        for (Animal g : sl.getEntitiesOfClass(Animal.class, new AABB(p, p).inflate(16), x -> (x instanceof Cat || x instanceof Ocelot) && x.isAlive())) {
            g.getLookControl().setLookAt(p.x, p.y, p.z, 60.0F, 60.0F);
            if (im.entidad() == g) continue;                                   // si apuntas al gato, solo se mira el punto encima
            if (g instanceof TamableAnimal ta && ta.isOrderedToSit()) continue; // sentado: lo sigue con la cabeza
            double d = g.position().distanceTo(p);
            if (d > 1.3 && (t + g.getId()) % 5 == 0)
                g.getNavigation().moveTo(p.x, p.y, p.z, d > 6 ? 1.45 : 1.15);
            // zarpazo: salto corto hacia el punto
            if (d < 2.4 && g.onGround() && g.getRandom().nextInt(10) == 0) {
                Vec3 h = new Vec3(p.x - g.getX(), 0, p.z - g.getZ());
                h = h.lengthSqr() < 1e-4 ? Vec3.ZERO : h.normalize().scale(Math.min(0.45, 0.2 + d * 0.12));
                g.setDeltaMovement(h.x, 0.38 + g.getRandom().nextDouble() * 0.12, h.z);
                com.miscelanea.logros.LogrosMiscelanea.dar(e, "laser_gato");
                g.hasImpulse = true;
                g.getNavigation().stop();
                if (g.getRandom().nextInt(3) == 0)
                    sl.playSound(null, g.getX(), g.getY(), g.getZ(), g instanceof Cat ? SoundEvents.CAT_PURREOW : SoundEvents.OCELOT_AMBIENT,
                            g.getSoundSource(), 0.7F, 1.0F + g.getRandom().nextFloat() * 0.3F);
            }
        }
    }

    // ------------------------------------------------------------------ cliente: poses

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(LivingEntity e, InteractionHand h, ItemStack st) {
                return com.miscelanea.client.laser.LaserCliente.pose(e, h);
            }

            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.player.LocalPlayer pl,
                                                   net.minecraft.world.entity.HumanoidArm brazo, ItemStack s, float pt, float equip, float swing) {
                return com.miscelanea.client.laser.LaserCliente.manoPrimeraPersona(ps, pl, brazo, pt);
            }
        });
    }
}

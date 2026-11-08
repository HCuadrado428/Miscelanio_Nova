package com.miscelanea.client.encantos;

import com.miscelanea.Miscelanea;
import com.miscelanea.encantos.Encantos;
import com.miscelanea.encantos.EncantosPacket;
import com.miscelanea.encantos.EncantoMisc;
import com.miscelanea.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Los encantamientos que dependen de las teclas del jugador (Ráfaga, Deslizar, Pegajoso), Percepción y las descripciones. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class EncantosCliente {
    private EncantosCliente() {}

    private static boolean saltoAntes, agacharAntes, rafagaUsada, soltoSalto;
    private static int deslizarEspera;

    /** Percepción: agachado, siluetas de lo que se mueve a 20 bloques (pasa a través de las paredes). */
    public static boolean silueta(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || e == p || !(e instanceof LivingEntity) || !p.isShiftKeyDown()) return false;
        if (!Encantos.tiene(p, EquipmentSlot.HEAD, Encantos.PERCEPCION)) return false;
        return e.distanceToSqr(p) < 20 * 20;
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !e.player.level().isClientSide) return;
        Minecraft mc = Minecraft.getInstance();
        if (e.player != mc.player || mc.screen != null) return;
        LocalPlayer p = mc.player;
        boolean salto = mc.options.keyJump.isDown(), agachar = mc.options.keyShift.isDown();
        boolean suelo = p.onGround();
        boolean libre = !p.getAbilities().flying && !p.isInWater() && !p.isPassenger() && !p.onClimbable() && !p.isFallFlying();
        // Ráfaga: un segundo salto en el aire
        if (suelo) {
            rafagaUsada = false;
            soltoSalto = false;
        } else if (!salto) soltoSalto = true;
        if (!suelo && libre && salto && !saltoAntes && soltoSalto && !rafagaUsada && Encantos.tiene(p, EquipmentSlot.LEGS, Encantos.RAFAGA)) {
            rafagaUsada = true;
            Vec3 d = p.getDeltaMovement();
            p.setDeltaMovement(d.x * 1.15, 0.58, d.z * 1.15);
            p.fallDistance = 0;
            for (int i = 0; i < 10; i++)
                p.level().addParticle(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), (p.getRandom().nextDouble() - 0.5) * 0.2, -0.05, (p.getRandom().nextDouble() - 0.5) * 0.2);
            p.playSound(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.4F, 1.6F);
            ModNetwork.CHANNEL.sendToServer(new EncantosPacket(1, BlockPos.ZERO, ""));
        }
        // Deslizar: corriendo, agacharse = un acelerón por el suelo
        if (deslizarEspera > 0) deslizarEspera--;
        if (suelo && libre && p.isSprinting() && agachar && !agacharAntes && deslizarEspera == 0 && Encantos.tiene(p, EquipmentSlot.LEGS, Encantos.DESLIZAR)) {
            Vec3 m = p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.0);
            p.setDeltaMovement(m.x, 0.05, m.z);
            deslizarEspera = 30;
            p.playSound(net.minecraft.sounds.SoundEvents.GRAVEL_STEP, 0.8F, 0.7F);
        }
        // Pegajoso: agachado contra una pared, te quedas pegado (saltar = trepar)
        if (!suelo && libre && agachar && p.horizontalCollision && Encantos.tiene(p, EquipmentSlot.FEET, Encantos.PEGAJOSO)) {
            Vec3 d = p.getDeltaMovement();
            p.setDeltaMovement(d.x * 0.5, salto ? 0.2 : 0.0, d.z * 0.5);
            p.fallDistance = 0;
        }
        saltoAntes = salto;
        agacharAntes = agachar;
    }

    /** Debajo del nombre de cada encantamiento nuevo, lo que hace. */
    @SubscribeEvent
    public static void descripcion(ItemTooltipEvent e) {
        var m = EnchantmentHelper.getEnchantments(e.getItemStack());
        if (m.isEmpty()) return;
        List<Component> l = e.getToolTip();
        for (Enchantment en : m.keySet()) {
            if (!(en instanceof EncantoMisc)) continue;
            String nombre = Component.translatable(en.getDescriptionId()).getString();
            for (int i = 0; i < l.size(); i++) {
                if (l.get(i).getString().startsWith(nombre)) {
                    MutableComponent d = Component.literal("  ").append(Component.translatable(en.getDescriptionId() + ".desc")).withStyle(ChatFormatting.DARK_GRAY);
                    l.add(i + 1, d);
                    break;
                }
            }
        }
    }
}

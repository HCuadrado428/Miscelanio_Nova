package com.miscelanea.client.armasfuego.escopeta;

import com.miscelanea.Miscelanea;
import com.miscelanea.armasfuego.escopeta.DispararPresionPacket;
import com.miscelanea.armasfuego.escopeta.EscopetaPresionItem;
import com.miscelanea.armasfuego.escopeta.Presion;
import com.miscelanea.armasfuego.escopeta.TipoCartucho;
import com.miscelanea.network.ModNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Cliente de la escopeta de presión: el clic izquierdo (disparar), el manómetro del HUD, la pose y el bombeo en la mano. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EscopetaCliente {
    private EscopetaCliente() {}

    private static final ResourceLocation MANOMETRO = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/escopeta_manometro.png");
    private static float agujaVista;
    private static long ultimoDisparo = -100;

    /** Registro en el bus del mod: los estados del modelo y el HUD. */
    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> {
                var item = com.miscelanea.registry.ModItems.ESCOPETA_PRESION.get();
                ItemProperties.register(item, new ResourceLocation(Miscelanea.MOD_ID, "escopeta_aguja"),
                        (s, l, ent, seed) -> Presion.agujaModelo(s) / 4F);
                ItemProperties.register(item, new ResourceLocation(Miscelanea.MOD_ID, "escopeta_oxido"),
                        (s, l, ent, seed) -> Presion.oxido(s) / 3F);
                ItemProperties.register(item, new ResourceLocation(Miscelanea.MOD_ID, "escopeta_reventada"),
                        (s, l, ent, seed) -> Presion.reventada(s, nivel(l, ent)) > 0 ? 1F : 0F);
            });
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("escopeta_presion", EscopetaCliente::hud);
        }
    }

    private static Level nivel(Level l, LivingEntity e) {
        if (l != null) return l;
        if (e != null) return e.level();
        return Minecraft.getInstance().level;
    }

    // ---------------------------------------------------------------- disparar con el clic izquierdo

    @SubscribeEvent
    public static void clic(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack s = mc.player.getMainHandItem();
        if (!(s.getItem() instanceof EscopetaPresionItem)) return;
        // sin aire es un palo: pega y rompe como siempre
        if (Presion.disparosQuedan(s) <= 0) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        disparar();
    }

    /** Pide el disparo al servidor (también lo usa la prueba del CI). */
    public static void disparar() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        ItemStack s = p.getMainHandItem();
        if (!(s.getItem() instanceof EscopetaPresionItem) || p.getCooldowns().isOnCooldown(s.getItem())) return;
        long ahora = mc.level.getGameTime();
        if (ahora - ultimoDisparo < 6) return;   // mantener el botón no manda un disparo por tick
        ultimoDisparo = ahora;
        ModNetwork.CHANNEL.sendToServer(new DispararPresionPacket());
        // retroceso en la mira
        float f = Math.min(Presion.presion(s) / 100F, 1.3F);
        com.miscelanea.client.armasfuego.comun.FuegoCliente.patada(Presion.tolva(s) == null ? 2F : 3F + 6F * f, 4F + 4F * f, 1.2F);
    }

    // ---------------------------------------------------------------- pose y bombeo

    public static HumanoidModel.ArmPose pose(LivingEntity e, InteractionHand h) {
        if (h != InteractionHand.MAIN_HAND) return null;
        // bombeando o aspirando: la pose de cargar la ballesta (la de vanilla)
        if (e.isUsingItem() && e.getUsedItemHand() == h) return null;
        return HumanoidModel.ArmPose.CROSSBOW_HOLD;
    }

    public static boolean mano(PoseStack ps, LocalPlayer pl, HumanoidArm brazo, ItemStack s, float pt, float equip) {
        if (!pl.isUsingItem() || pl.getUseItem() != s) {
            com.miscelanea.client.armasfuego.comun.FuegoCliente.culatazo(ps);
            return false;
        }
        int i = brazo == HumanoidArm.RIGHT ? 1 : -1;
        ps.translate(i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        float t = pl.getTicksUsingItem() + pt;
        if (pl.isShiftKeyDown()) {
            // aspirando: tiembla un poco
            ps.translate(Mth.sin(t * 2.1F) * 0.006F, Mth.cos(t * 1.7F) * 0.006F, 0.04F);
        } else {
            // bombeando: un empujón por bombeo
            float b = Mth.sin(t * Mth.PI / EscopetaPresionItem.RITMO);
            b *= b;
            ps.translate(0, -0.07F * b, 0.06F * b);
            ps.mulPose(Axis.XP.rotationDegrees(-6F * b));
        }
        return true;
    }

    // ---------------------------------------------------------------- manómetro en el HUD

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int ancho, int alto) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui || mc.level == null) return;
        ItemStack s = p.getMainHandItem();
        if (!(s.getItem() instanceof EscopetaPresionItem)) {
            agujaVista = 0;
            return;
        }
        // a la derecha de la barra (o de la mano izquierda si va a ese lado)
        int x = ancho / 2 + 91 + 6 + (p.getMainArm() == HumanoidArm.LEFT ? 29 : 0);
        int y = alto - 34;
        int rev = Presion.reventada(s, mc.level);
        g.blit(MANOMETRO, x, y, 0, 0, 32, 32, 32, 32);
        float objetivo = Presion.fraccion(s);
        agujaVista += (objetivo - agujaVista) * 0.25F;
        float f = agujaVista;
        long tick = mc.level.getGameTime();
        if (rev > 0) {
            f = -0.06F;   // aguja caída
            g.fill(x + 4, y + 4, x + 28, y + 28, 0x55303030);
        } else if (objetivo >= Presion.ZONA_ROJA) {
            f += Mth.sin((tick + pt) * 2.7F) * 0.012F;   // en la roja tiembla
        }
        double ang = Math.toRadians(225 - 270 * f);
        double cx = x + 16, cy = y + 16;
        for (int k = 0; k <= 22; k++) {
            double r = k * 0.5;
            int px = (int) Math.floor(cx + Math.cos(ang) * r), py = (int) Math.floor(cy - Math.sin(ang) * r);
            int col = r >= 8 ? 0xFFC42A1B : 0xFF2A1A09;
            g.fill(px, py, px + 1, py + 1, col);
        }
        g.fill(x + 15, y + 15, x + 17, y + 17, 0xFF6D3421);
        // disparos que quedan de esta bombeada (dos puntos con Doble cámara)
        int total = Presion.disparosPorBombeada(s), quedan = Presion.disparosQuedan(s);
        if (total > 1) {
            for (int k = 0; k < total; k++) {
                int dx = x + 12 + k * 6;
                g.fill(dx, y + 24, dx + 3, y + 27, k < quedan ? 0xFFE77C56 : 0xFF5A2A1B);
            }
        }
        // la tolva: el cartucho que lleva y cuántos
        TipoCartucho t = Presion.tolva(s);
        if (t != null) {
            ItemStack c = new ItemStack(t.item(), Presion.tolvaN(s));
            g.renderItem(c, x + 34, y + 8);
            g.renderItemDecorations(mc.font, c, x + 34, y + 8);
        }
    }
}

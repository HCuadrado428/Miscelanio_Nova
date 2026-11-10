package com.miscelanea.client.armasfuego.comun;

import com.miscelanea.Miscelanea;
import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.gatling.GatlingEntity;
import com.miscelanea.armasfuego.gatling.GatlingPacket;
import com.miscelanea.armasfuego.oeste.CanonManoItem;
import com.miscelanea.armasfuego.oeste.DerringerItem;
import com.miscelanea.armasfuego.oeste.MosqueteItem;
import com.miscelanea.armasfuego.oeste.ThompsonItem;
import com.miscelanea.armasfuego.oeste.WinchesterItem;
import com.miscelanea.network.ModNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * El «feel» de todas las armas de fuego en el cliente: el retroceso (la vista sube un poco y la cámara da un golpe
 * que se apaga en unas décimas, con algo de giro), el culatazo del arma en primera persona, las animaciones de
 * recarga, la palanca de la Winchester (con el giro a caballo), el contador de balas del HUD y los mandos de la
 * Gatling (mantener clic derecho = manivela).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class FuegoCliente {
    private FuegoCliente() {}

    // ------------------------------------------------------------------ retroceso

    private static float camPitch, camRoll;
    private static long tPatada;
    private static float manoPatada;
    /** Para la prueba: cuántos golpes de retroceso se han dado. */
    public static int patadas;

    /**
     * Retroceso: {@code sube} grados que la vista sube y se quedan (como en vanilla, apuntas más arriba),
     * {@code sacude} grados del golpe de cámara (se va solo en ~0,2 s) y {@code mano} cuánto culatea el arma.
     */
    public static void patada(float sube, float sacude, float mano) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (sube != 0) p.setXRot(Mth.clamp(p.getXRot() - sube, -90F, 90F));
        camPitch = Math.min(camPitch + sacude, 14F);
        camRoll = (p.getRandom().nextFloat() - 0.5F) * sacude * 0.6F;
        manoPatada = Math.max(manoPatada * fadeMano(), mano);
        tPatada = Util.getMillis();
        patadas++;
    }

    private static float fadeMano() {
        long ms = Util.getMillis() - tPatada;
        return ms > 220 ? 0 : (1 - ms / 220F) * (1 - ms / 220F);
    }

    /** El retroceso de cada arma de la segunda tanda al disparar (lo pide el objeto en el cliente). */
    public static void disparoLocal(ArmaFuegoItem arma, ItemStack s) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (arma instanceof WinchesterItem) patada(2.2F, 3.5F, 1F);
        else if (arma instanceof DerringerItem) patada(1.6F, 2.5F, 0.8F);
        else if (arma instanceof ThompsonItem) {
            // ráfaga: cada bala sube un poco y tira hacia un lado (cuanto más larga, más se va)
            int n = ThompsonItem.rafagaN(s);
            float k = ThompsonItem.compensador(s);
            patada((0.55F + Math.min(0.6F, n * 0.03F)) * k, 1.4F, 0.45F);
            p.setYRot(p.getYRot() + (p.getRandom().nextFloat() - 0.45F) * 1.1F * k);
        } else if (arma instanceof MosqueteItem) patada(4.5F, 7F, 1.6F);
        else if (arma instanceof CanonManoItem) patada(6F, 10F, 2F);
        else patada(2F, 3F, 1F);
    }

    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        long ms = Util.getMillis() - tPatada;
        if (ms > 260) {
            camPitch = 0;
            return;
        }
        float k = 1 - ms / 260F;
        k = k * k;
        e.setPitch(e.getPitch() - camPitch * k);
        e.setRoll(e.getRoll() + camRoll * k);
        // la Gatling vibra mientras dispara
    }

    // ------------------------------------------------------------------ en la mano

    /** Extensión de cliente común: la pose del brazo y las animaciones en primera persona. */
    public static class Mano implements IClientItemExtensions {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity e, InteractionHand h, ItemStack s) {
            if (h != InteractionHand.MAIN_HAND) return null;
            if (s.getItem() instanceof DerringerItem) {
                // el as en la manga: hasta que dispara, el brazo va como si no llevara nada
                if (!DerringerItem.visible(s, e.level()) && !(e.isUsingItem() && ArmaFuegoItem.modo(s) == ArmaFuegoItem.MODO_RECARGA))
                    return HumanoidModel.ArmPose.EMPTY;
                return com.miscelanea.client.armasfuego.pistola.PistolaCliente.POSE;
            }
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack ps, LocalPlayer p, HumanoidArm brazo, ItemStack s, float pt, float equip, float golpe) {
            mano(ps, p, brazo, s, pt);
            return false;
        }
    }

    /** Animaciones en primera persona de las armas de la segunda tanda. */
    static void mano(PoseStack ps, LocalPlayer p, HumanoidArm brazo, ItemStack s, float pt) {
        int lado = brazo == HumanoidArm.RIGHT ? 1 : -1;
        var l = p.level();
        boolean usando = p.isUsingItem() && p.getUseItem() == s;
        int modo = ArmaFuegoItem.modo(s);
        float tUso = usando ? p.getTicksUsingItem() + pt : 0;
        // recarga: el arma baja y se ladea; cada paso, un empujoncito
        if (usando && (modo == ArmaFuegoItem.MODO_RECARGA || modo == ArmaFuegoItem.MODO_DESATASCO) && s.getItem() instanceof ArmaFuegoItem a) {
            float entra = Math.min(1F, tUso / 5F);
            float paso = Math.max(1, a.ticksRecarga(s));
            float f = (tUso % paso) / paso;
            float golpe = Mth.sin(f * Mth.PI) * 0.6F;
            if (s.getItem() instanceof MosqueteItem || s.getItem() instanceof CanonManoItem) {
                // de pie, con la boca hacia arriba: la baqueta entra y sale
                ps.translate(lado * -0.12F * entra, 0.05F * entra, 0.08F * entra);
                girar(ps, lado, Axis.XP.rotationDegrees(55F * entra));
                girar(ps, lado, Axis.ZP.rotationDegrees(lado * 12F * entra));
                ps.translate(0, -0.03F * golpe, 0);
            } else if (modo == ArmaFuegoItem.MODO_DESATASCO) {
                ps.translate(0, -0.06F * entra + Mth.sin(tUso * 1.9F) * 0.015F, 0.02F * entra);
                girar(ps, lado, Axis.ZP.rotationDegrees(lado * 30F * entra));
            } else {
                // sube, se gira de costado (se ven la portilla y la palanca, o el brocal del tambor) y se ladea un poco
                ps.translate(lado * -0.15F * entra, 0.16F * entra - 0.02F * golpe, -0.04F * entra);
                girar(ps, lado, Axis.YP.rotationDegrees(lado * 35F * entra));
                girar(ps, lado, Axis.XP.rotationDegrees(-8F * entra + 4F * golpe));
                girar(ps, lado, Axis.ZP.rotationDegrees(lado * 12F * entra));
            }
        }
        // la palanca de la Winchester: a pie, el arma baja un poco; a caballo, un giro entero (spin-cock)
        if (s.getItem() instanceof WinchesterItem) {
            float d = ArmaFuegoItem.desdeDisparo(s, l, pt);
            int pal = WinchesterItem.palanca(p, s);
            if (d >= 0 && d < pal) {
                float t = d / pal;
                if (p.isPassenger()) {
                    ps.translate(0, 0.05F, -0.05F);
                    girar(ps, lado, Axis.XP.rotationDegrees(-360F * easeInOut(t)));
                    ps.translate(0, -0.05F, 0.05F);
                } else {
                    float k = Mth.sin(t * Mth.PI);
                    ps.translate(0, -0.05F * k, 0.02F * k);
                    girar(ps, lado, Axis.XP.rotationDegrees(6F * k));
                }
            }
        }
        // la mecha del cañón: tiembla mientras arde
        if (s.getItem() instanceof MosqueteItem && (balas == 0 || p.isUsingItem() && p.getUseItem() == s && ArmaFuegoItem.modo(s) == ArmaFuegoItem.MODO_RECARGA))
            hudMosquete(g, mc, p, a, s, x, y + 22, pt);
        if (s.getItem() instanceof CanonManoItem && CanonManoItem.mecha(s) >= 0) {
            float t = l.getGameTime() + pt;
            ps.translate(Mth.sin(t * 2.3F) * 0.006F, Mth.cos(t * 3.1F) * 0.006F, 0);
        }
        // culatazo
        float k = fadeMano() * manoPatada;
        if (k > 0) {
            ps.translate(0, 0.02F * k, 0.12F * k);
            girar(ps, lado, Axis.XP.rotationDegrees(7F * k));
        }
    }

    /**
     * Gira alrededor de la mano: applyForgeHandTransform va antes de que vanilla lleve el arma a la mano, así que un
     * giro a pelo sería alrededor de la cámara (y el arma se saldría de la pantalla).
     */
    public static void girar(PoseStack ps, int lado, org.joml.Quaternionf q) {
        ps.translate(lado * 0.56F, -0.52F, -0.72F);
        ps.mulPose(q);
        ps.translate(lado * -0.56F, 0.52F, 0.72F);
    }

    private static float easeInOut(float t) {
        return t < 0.5F ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
    }

    /** El culatazo también para las tres primeras (revólver y escopeta, que no tenían animación de disparo). */
    public static void culatazo(PoseStack ps) {
        float k = fadeMano() * manoPatada;
        if (k > 0) {
            ps.translate(0, 0.02F * k, 0.12F * k);
            girar(ps, 1, Axis.XP.rotationDegrees(7F * k));
        }
    }

    // ------------------------------------------------------------------ HUD

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || mc.level == null) return;
        GatlingEntity gat = GatlingEntity.deJugador(p);
        if (gat != null) {
            hudGatling(g, mc, gat, w, h);
            return;
        }
        ItemStack s = p.getMainHandItem();
        if (!(s.getItem() instanceof ArmaFuegoItem a)) return;
        int x = w / 2 + 12, y = h / 2 + 8;
        var f = mc.font;
        int balas = ArmaFuegoItem.balas(s), cap = a.capacidad(s);
        // balas: la Winchester, su depósito tubular con el color de cada bala; las demás, puntitos de latón si caben
        // (hasta 12), si no el número
        if (s.getItem() instanceof WinchesterItem) {
            hudDeposito(g, s, cap, x, y);
        } else if (cap <= 12) {
            for (int i = 0; i < cap; i++) {
                int bx = x + i * 3;
                g.fill(bx, y, bx + 2, y + 5, i < balas ? 0xFFD6A632 : 0x66403020);
                if (i < balas) g.fill(bx, y, bx + 2, y + 1, 0xFFB0B6BC);
            }
        } else {
            g.drawString(f, balas + "/" + cap, x, y - 1, balas == 0 ? 0xFFFF5555 : 0xFFF0D080, true);
        }
        if (ArmaFuegoItem.atascada(s)) g.drawString(f, Component.translatable("hud.miscelanea.armafuego.atascada").withStyle(ChatFormatting.GOLD), x, y + 7, 0xFFFFAA00, true);
        else if (balas == 0) g.drawString(f, Component.translatable("hud.miscelanea.armafuego.recarga"), x, y + 7, 0xFFAAAAAA, true);
        // barra de la recarga
        if (p.isUsingItem() && p.getUseItem() == s && ArmaFuegoItem.modo(s) != ArmaFuegoItem.MODO_RAFAGA) {
            int paso = ArmaFuegoItem.modo(s) == ArmaFuegoItem.MODO_DESATASCO ? ArmaFuegoItem.T_DESATASCO : Math.max(1, a.ticksRecarga(s));
            float fr = ((p.getTicksUsingItem() + pt) % paso) / paso;
            g.fill(x, y + 17, x + 30, y + 19, 0x88000000);
            g.fill(x, y + 17, x + (int) (30 * fr), y + 19, 0xFFE0C060);
        }
        if (s.getItem() instanceof MosqueteItem && (balas == 0 || p.isUsingItem() && p.getUseItem() == s && ArmaFuegoItem.modo(s) == ArmaFuegoItem.MODO_RECARGA))
            hudMosquete(g, mc, p, a, s, x, y + 22, pt);
        if (s.getItem() instanceof CanonManoItem && CanonManoItem.mecha(s) >= 0) {
            long q = CanonManoItem.mecha(s) - mc.level.getGameTime();
            float fr = 1F - q / (float) CanonManoItem.mechaTicks(s);
            g.fill(x, y + 17, x + 30, y + 19, 0x88000000);
            g.fill(x, y + 17, x + (int) (30 * Mth.clamp(fr, 0, 1)), y + 19, 0xFFFF7A20);
        }
    }

    /**
     * El depósito tubular de la Winchester junto a la mira: un tubo con cada bala de su color, la que va a salir
     * (la última que entró) pegada a la mira y marcada encima; los huecos vacíos, oscuros.
     */
    private static void hudDeposito(GuiGraphics g, ItemStack s, int cap, int x, int y) {
        java.util.List<net.minecraft.world.item.Item> dep = WinchesterItem.deposito(s);
        int n = dep.size();
        int ancho = cap * 3 + 1;
        g.fill(x - 1, y - 1, x + ancho, y + 6, 0xAA1A1208);
        g.fill(x - 1, y - 1, x + ancho, y, 0xAA5A4A30);
        for (int i = 0; i < cap; i++) {
            int bx = x + i * 3;
            if (i < n) {
                int c = WinchesterItem.color(dep.get(n - 1 - i));
                g.fill(bx, y, bx + 2, y + 5, c);
                g.fill(bx, y, bx + 2, y + 1, 0xFFFFFFFF & (c | 0xFF606060));
            } else g.fill(bx, y + 1, bx + 2, y + 4, 0x55302418);
        }
        if (n > 0) {
            // la siguiente: un triangulito blanco encima
            g.fill(x, y - 4, x + 2, y - 3, 0xFFFFFFFF);
            g.fill(x + 0, y - 3, x + 2, y - 2, 0xFFFFFFFF);
            g.fill(x - 1, y - 5, x + 3, y - 4, 0xFFFFFFFF);
        }
    }

    /** Los pasos de la recarga del mosquete (pólvora, bola, baqueta) con el que toca marcado y los hechos en verde. */
    private static void hudMosquete(GuiGraphics g, Minecraft mc, LocalPlayer p, ArmaFuegoItem a, ItemStack s, int x, int y, float pt) {
        boolean cargando = p.isUsingItem() && p.getUseItem() == s && ArmaFuegoItem.modo(s) == ArmaFuegoItem.MODO_RECARGA;
        float fr = cargando ? Mth.clamp((p.getTicksUsingItem() + pt) / Math.max(1, a.ticksRecarga(s)), 0, 1) : 0;
        int paso = cargando ? MosqueteItem.pasoRecarga(fr) : -1;
        ItemStack[] iconos = {new ItemStack(net.minecraft.world.item.Items.GUNPOWDER), a.iconoMunicion(s), new ItemStack(net.minecraft.world.item.Items.STICK)};
        for (int i = 0; i < 3; i++) {
            int ix = x + i * 14;
            int marco = i == paso ? 0xFFFFD040 : i < paso ? 0xFF50C050 : 0x66000000;
            g.fill(ix - 1, y - 1, ix + 13, y + 13, marco);
            g.fill(ix, y, ix + 12, y + 12, 0xAA101010);
            g.pose().pushPose();
            g.pose().translate(ix, y, 0);
            g.pose().scale(0.75F, 0.75F, 1F);
            g.renderItem(iconos[i], 0, 0);
            g.pose().popPose();
            if (i < 2) g.fill(ix + 13, y + 5, ix + 14, y + 7, 0x88FFFFFF);
        }
        // la baqueta: cuánto le queda al paso de ahora
        if (paso >= 0) {
            float dentro = fr * 3 - paso;
            g.fill(x + paso * 14, y + 13, x + paso * 14 + (int) (12 * Mth.clamp(dentro, 0, 1)), y + 14, 0xFFFFD040);
        }
    }

    private static void hudGatling(GuiGraphics g, Minecraft mc, GatlingEntity gat, int w, int h) {
        // debajo de la mira (abajo están el aviso de bajarse y los mensajes de la barra de acción)
        int x = w / 2 - 40, y = h / 2 + 24;
        var f = mc.font;
        float c = gat.calor() / 100F;
        int col = c < 0.5F ? 0xFF5FD35F : c < 0.8F ? 0xFFE8C840 : 0xFFE84030;
        g.fill(x - 1, y - 1, x + 81, y + 5, 0xAA000000);
        g.fill(x, y, x + (int) (80 * c), y + 4, col);
        g.drawString(f, Component.translatable("hud.miscelanea.gatling.calor"), x, y - 11, 0xFFE0E0E0, true);
        String b = gat.balas() + "/" + gat.balasCinta();
        g.drawString(f, b, x + 80 - f.width(b), y - 11, gat.balas() == 0 ? 0xFFFF5555 : 0xFFF0D080, true);
        if (gat.recarga() > 0) {
            Component t = Component.translatable("hud.miscelanea.gatling.recargando");
            g.drawString(f, t, w / 2 - f.width(t) / 2, y + 8, 0xFFE0C060, true);
        }
    }

    // ------------------------------------------------------------------ Gatling: la manivela

    private static boolean girandoEnviado;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        GatlingEntity gm = p == null ? null : GatlingEntity.deJugador(p);
        boolean en = gm != null;
        boolean gira = en && mc.screen == null && (mc.options.keyUse.isDown() || manivelaPrueba);
        if (gira != girandoEnviado && (en || girandoEnviado)) {
            girandoEnviado = gira;
            if (p != null) ModNetwork.CHANNEL.sendToServer(new GatlingPacket(gira));
        }
        // la Gatling disparando: la cámara vibra un poco
        if (en && gm.velocidad > 0.35F && gm.girando() && gm.balas() > 0 && gm.atasco() == 0 && p.tickCount % 2 == 0)
            patada(0F, 0.9F, 0F);
        mangaTick(mc, p);
    }

    /** El que va a los mandos de la Gatling: los dos brazos a los mandos (como con la ballesta cargada). */
    @SubscribeEvent
    public static void brazos(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
        if (e.getEntity().getVehicle() instanceof GatlingEntity) {
            var m = e.getRenderer().getModel();
            m.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
            m.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
    }

    // ------------------------------------------------------------------ Derringer: la manga

    /** La tecla de la manga (Opciones → Controles → Miscelánea; K por defecto). */
    public static final net.minecraft.client.KeyMapping MANGA = new net.minecraft.client.KeyMapping("key.miscelanea.derringer_manga",
            net.minecraftforge.client.settings.KeyConflictContext.IN_GAME, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_K, com.miscelanea.client.combate.LlaveCliente.CATEGORIA);

    private static void mangaTick(Minecraft mc, LocalPlayer p) {
        while (MANGA.consumeClick()) if (p != null && mc.screen == null) sacarDeLaManga();
    }

    /** Pide sacar la Derringer de la manga (también lo usa la prueba). */
    public static void sacarDeLaManga() {
        ModNetwork.CHANNEL.sendToServer(new com.miscelanea.armasfuego.oeste.MangaPacket());
    }

    /** Para la prueba del CI: girar la manivela sin el ratón. */
    public static boolean manivelaPrueba;

    /** Con la Gatling, el clic derecho es la manivela: que no use lo que se lleve en la mano. */
    @SubscribeEvent
    public static void clic(InputEvent.InteractionKeyMappingTriggered e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && e.isUseItem() && GatlingEntity.deJugador(p) != null) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }
}

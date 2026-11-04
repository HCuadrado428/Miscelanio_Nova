package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.qte.Dibujo;
import com.miscelanea.tortura.aparatos.AparatoAccionPacket;
import com.miscelanea.tortura.aparatos.AparatoBlock;
import com.miscelanea.tortura.aparatos.AparatoEfectoPacket;
import com.miscelanea.tortura.aparatos.AparatoJuegoPacket;
import com.miscelanea.tortura.aparatos.AparatosRed;
import com.miscelanea.tortura.aparatos.AparatosVictimas;
import com.miscelanea.tortura.aparatos.DoncellaBlockEntity;
import com.miscelanea.tortura.aparatos.PotroBlockEntity;
import com.miscelanea.tortura.aparatos.SillaElectricaBlock;
import com.miscelanea.tortura.aparatos.SillaElectricaBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cliente de los aparatos: minijuegos de la víctima (doncella y pulso), vista desde dentro de la doncella, desmayos,
 * temblor de las descargas, la víctima tumbada en el potro (y sacudida en la silla) al dibujarla, el HUD del captor
 * (aguja del potro, ayudas) y la rueda del ratón sobre el cuadro de la silla.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class AparatosCliente {
    private AparatosCliente() {}

    @Nullable private static JuegoDoncella doncella;
    @Nullable private static JuegoPulso pulso;
    /** entidad → {tick del cliente en que acaba, voltaje, duración}. */
    private static final Map<Integer, int[]> DESCARGAS = new HashMap<>();
    private static final Set<Integer> EMPUJADOS = new HashSet<>();
    private static int reloj, desmayo, desmayoMax;

    // ------------------------------------------------------------------ paquetes
    public static void juego(AparatoJuegoPacket p) {
        switch (p.juego) {
            case AparatoJuegoPacket.DONCELLA -> doncella = new JuegoDoncella(p.pos, p.semilla);
            case AparatoJuegoPacket.PULSO -> pulso = new JuegoPulso(p.pos, p.semilla, p.dificultad);
            case AparatoJuegoPacket.INMERSION -> PenduloInmersionCliente.juego(p);
            default -> {
                if (doncella != null && doncella.pos.equals(p.pos)) doncella = null;
                if (pulso != null && pulso.pos.equals(p.pos)) pulso = null;
                PenduloInmersionCliente.parar(p.pos);
            }
        }
    }

    public static void efecto(AparatoEfectoPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.efecto == AparatoEfectoPacket.DESMAYO) {
            if (mc.player != null && p.entidad == mc.player.getId()) {
                desmayo = desmayoMax = Math.max(10, p.valor);
                pulso = null;
            }
        } else if (p.efecto == AparatoEfectoPacket.DESCARGA) {
            int ticks = p.valor & 0xFFFF, v = p.valor >>> 16;
            DESCARGAS.put(p.entidad, new int[]{reloj + ticks, v, ticks});
        } else if (p.efecto == AparatoEfectoPacket.CORTE) {
            PenduloInmersionCliente.efecto(p);
        }
    }

    /** Intensidad de la descarga que está sufriendo (0 = ninguna). */
    public static float descarga(int id, float pt) {
        int[] d = DESCARGAS.get(id);
        if (d == null) return 0F;
        float quedan = d[0] - reloj - pt;
        if (quedan <= 0) return 0F;
        return Mth.clamp(quedan / 6F, 0F, 1F);
    }

    public static int voltajeDescarga(int id) {
        int[] d = DESCARGAS.get(id);
        return d == null ? 0 : d[1];
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            limpiar();
            return;
        }
        reloj++;
        if (desmayo > 0) desmayo--;
        DESCARGAS.values().removeIf(d -> d[0] < reloj);
        int yo = mc.player.getId();
        if (com.miscelanea.client.tortura.muertes.MuertesCliente.localActiva()) {
            doncella = null;
            pulso = null;
        }
        AparatosVictimas.Entrada en = AparatosVictimas.de(yo);
        if (doncella != null) {
            if (en == null || !en.pos().equals(doncella.pos) || !(mc.level.getBlockEntity(doncella.pos) instanceof DoncellaBlockEntity d)) {
                if (reloj % 20 == 0 && en == null) doncella = null;   // (margen: al entrar aún no ha llegado la sincronización)
            } else if (!mc.isPaused()) doncella.tick(mc, d.vis);
        }
        if (pulso != null) {
            if (en == null && reloj % 20 == 0) pulso = null;
            else if (!mc.isPaused()) {
                pulso.tick(mc);
                if (pulso.terminado) pulso = null;
            }
        }
    }

    private static void limpiar() {
        doncella = null;
        pulso = null;
        DESCARGAS.clear();
        EMPUJADOS.clear();
        desmayo = 0;
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        limpiar();
        AparatosVictimas.limpiar();
    }

    // ------------------------------------------------------------------ entrada
    @SubscribeEvent
    public static void tecla(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (pulso == null || mc.screen != null || e.getAction() != GLFW.GLFW_PRESS) return;
        if (e.getKey() == GLFW.GLFW_KEY_SPACE || mc.options.keyJump.matches(e.getKey(), e.getScanCode())) pulso.pulsar(mc);
    }

    @SubscribeEvent
    public static void movimiento(MovementInputUpdateEvent e) {
        if (!AparatosVictimas.en(e.getEntity().getId())) return;
        Input in = e.getInput();
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
    }

    @Nullable
    private static BlockPos miradoPrincipal(Minecraft mc) {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return null;
        BlockState s = mc.level.getBlockState(bh.getBlockPos());
        if (!(s.getBlock() instanceof AparatoBlock)) return null;
        return AparatoBlock.principal(s, bh.getBlockPos());
    }

    /** Rueda del ratón mirando el cuadro (o la silla): sube o baja el voltaje en vez de cambiar de objeto. */
    @SubscribeEvent
    public static void rueda(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || AparatosVictimas.en(mc.player.getId())) return;
        BlockPos p = miradoPrincipal(mc);
        if (p == null || !(mc.level.getBlockState(p).getBlock() instanceof SillaElectricaBlock)) return;
        if (e.getScrollDelta() == 0) return;
        AparatosRed.CANAL.sendToServer(new AparatoAccionPacket(AparatoAccionPacket.VOLTAJE, p, e.getScrollDelta() > 0 ? 1 : -1));
        e.setCanceled(true);
    }

    // ------------------------------------------------------------------ dibujar a la víctima
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity le = e.getEntity();
        AparatosVictimas.Entrada en = AparatosVictimas.de(le.getId());
        float pt = e.getPartialTick();
        float desc = descarga(le.getId(), pt);
        if (en == null && desc <= 0) return;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        EMPUJADOS.add(le.getId());
        if (desc > 0) {
            // sacudidas de la descarga
            float t = le.tickCount + pt, a = desc * (0.012F + voltajeDescarga(le.getId()) * 0.004F);
            ps.translate(Mth.sin(t * 7.7F) * a, Math.abs(Mth.sin(t * 5.3F)) * a, Mth.sin(t * 9.1F + 1F) * a);
        }
        if (en != null && en.tipo() == AparatosVictimas.POTRO && le.level().getBlockEntity(en.pos()) instanceof PotroBlockEntity pb) tumbar(le, pb, ps, pt);
    }

    /** Tumbado boca arriba a lo largo de la mesa (y estirado según la tensión). Ver las medidas en PotroBlockEntity. */
    private static void tumbar(LivingEntity le, PotroBlockEntity pb, PoseStack ps, float pt) {
        float yaw = pb.frente().toYRot();
        le.yBodyRot = le.yBodyRotO = yaw;
        Vec3 l = pb.eje();
        boolean jugador = le instanceof Player;
        if (jugador) {
            double k = PotroBlockEntity.OJOS_ATRAS;
            ps.translate(l.x * k, k - PotroBlockEntity.OJOS_ALTO, l.z * k);
        }
        float giro = 180F - yaw;
        float estira = 1F + 0.07F * Mth.lerp(pt, pb.tensionVisO, pb.tensionVis);
        // cinemática de muerte: se estira de más
        float c = com.miscelanea.client.tortura.muertes.MuertesCliente.canal(le.getId(), "estira", pt);
        if (!Float.isNaN(c)) estira = Math.max(estira, c);
        ps.mulPose(Axis.YP.rotationDegrees(giro));
        ps.translate(0, jugador ? PotroBlockEntity.ALZA : Math.min(0.4F, le.getBbWidth() * 0.45F), 0);
        ps.mulPose(Axis.XP.rotationDegrees(90F));
        ps.scale(1F, estira, 1F);
        ps.mulPose(Axis.YP.rotationDegrees(-giro));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void despues(RenderLivingEvent.Post<?, ?> e) {
        if (EMPUJADOS.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ HUD
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // en la cinemática de muerte se ve desde fuera: nada de minijuegos ni del interior de la doncella
        if (com.miscelanea.client.tortura.muertes.MuertesCliente.localActiva()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        AparatosVictimas.Entrada en = AparatosVictimas.de(mc.player.getId());
        boolean ocultar = mc.options.hideGui;
        if (en != null && en.tipo() == AparatosVictimas.DONCELLA && mc.level.getBlockEntity(en.pos()) instanceof DoncellaBlockEntity d) {
            interiorDoncella(mc, g, w, h, Mth.lerp(pt, d.visO, d.vis), pt);
            if (doncella != null && !ocultar) doncella.dibujar(mc, g, w, h, pt, d.vis);
        }
        if (pulso != null && !ocultar) pulso.dibujar(mc, g, w, h, pt);
        if (en == null && !ocultar) ayudasCaptor(mc, g, w, h);
        if (desmayo > 0) {
            float t = desmayoMax - desmayo + pt;
            float a = Math.min(1F, Math.min(t / 8F, (desmayo - pt) / 20F));
            g.fill(0, 0, w, h, ((int) (Mth.clamp(a, 0F, 1F) * 250) << 24));
        }
        RenderSystem.disableBlend();
    }

    /** Dentro de la doncella: oscuridad, la luz que entra por la rendija de las puertas y los pinchos acercándose. */
    private static void interiorDoncella(Minecraft mc, GuiGraphics g, int w, int h, float k, float pt) {
        float cx = w / 2F;
        float rendija = (1F - k) * w * 0.48F + 3F;
        int oscuro = (int) (150 + 95 * k);
        // las dos hojas (negras), con el borde de luz difuminado
        int x0 = (int) (cx - rendija / 2F), x1 = (int) (cx + rendija / 2F);
        g.fill(0, 0, Math.max(0, x0), h, oscuro << 24);
        g.fill(Math.min(w, x1), 0, w, h, oscuro << 24);
        for (int i = 0; i < 6; i++) {
            int a = (int) (oscuro * (1F - (i + 1) / 7F));
            g.fill(x0 + i * 2, 0, x0 + i * 2 + 2, h, a << 24);
            g.fill(x1 - i * 2 - 2, 0, x1 - i * 2, h, a << 24);
        }
        // polvo de luz en la rendija
        float t = mc.player.tickCount + pt;
        for (int i = 0; i < 10; i++) {
            float px = cx + Mth.sin(i * 12.9F + t * 0.03F) * rendija * 0.4F, py = (i * 97 + t * 0.6F * (1 + i % 3)) % h;
            g.fill((int) px, (int) py, (int) px + 1, (int) py + 1, 0x50FFF0C8);
        }
        // pinchos de las hojas: crecen hacia el centro según se cierran
        float largo = 18 + k * w * 0.16F;
        int filas = 5;
        for (int f = 0; f < filas; f++) {
            float y = h * (0.12F + 0.76F * f / (filas - 1)) + (f % 2) * 10;
            float grosor = 9 + k * 6;
            int c = Dibujo.mezcla(0xFF2C2A28, 0xFF7A7470, 0.35F + 0.4F * k);
            Dibujo.triangulo(g, x0, y - grosor, x0 + largo, y, x0, y + grosor, c);
            Dibujo.triangulo(g, x0, y - grosor, x0 + largo, y, x0 + largo * 0.25F, y - grosor * 0.15F, 0x40FFFFFF);
            Dibujo.triangulo(g, x1, y - grosor, x1, y + grosor, x1 - largo, y, c);
            if (k > 0.5F) {
                // sangre en las puntas cuando ya están muy cerca
                Dibujo.disco(g, x0 + largo - 2, y, 2F, Dibujo.alfa(0xFF7A0A08, (k - 0.5F) * 2));
                Dibujo.disco(g, x1 - largo + 2, y, 2F, Dibujo.alfa(0xFF7A0A08, (k - 0.5F) * 2));
            }
        }
        if (mc.player.hurtTime > 0) g.fill(0, 0, w, h, ((int) (mc.player.hurtTime * 9) << 24) | 0x880000);
    }

    /** Lo que ve el captor al mirar un aparato con alguien dentro. */
    private static void ayudasCaptor(Minecraft mc, GuiGraphics g, int w, int h) {
        BlockPos p = miradoPrincipal(mc);
        if (p == null) return;
        BlockEntity be = mc.level.getBlockEntity(p);
        float cx = w / 2F, cy = h / 2F;
        if (be instanceof PotroBlockEntity pb && pb.ocupado()) {
            aguja(mc, g, cx + 70, cy + 10, pb);
        } else if (be instanceof DoncellaBlockEntity d && d.ocupado()) {
            Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.doncella.captor", Math.round(d.vis * 100)), cx, cy + 14, 0.8F, 0xFFE8D0B0, true);
        } else if (be instanceof SillaElectricaBlockEntity s) {
            int v = s.voltaje() * SillaElectricaBlockEntity.VOLTIOS;
            Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.silla.captor", v), cx, cy + 14, 0.8F,
                    s.voltaje() >= SillaElectricaBlockEntity.MAX ? 0xFFFF5040 : 0xFFB0E0FF, true);
        }
    }

    /** Dial del ritmo de la manivela: gris (flojo), verde (estira), amarillo (deprisa), rojo (se desmaya). */
    private static void aguja(Minecraft mc, GuiGraphics g, float cx, float cy, PotroBlockEntity pb) {
        float r0 = 22, r1 = 30;
        Dibujo.panelMetal(g, (int) cx - 40, (int) cy - 40, (int) cx + 40, (int) cy + 26, 0xFF2A2420);
        // de -90º (izquierda) a +90º (derecha)
        Dibujo.arco(g, cx, cy, r0, r1, -90, -90 + 180 * PotroBlockEntity.VERDE_MIN, 0xFF5A5A5E);
        Dibujo.arco(g, cx, cy, r0, r1, -90 + 180 * PotroBlockEntity.VERDE_MIN, -90 + 180 * PotroBlockEntity.VERDE_MAX, 0xFF40C850);
        Dibujo.arco(g, cx, cy, r0, r1, -90 + 180 * PotroBlockEntity.VERDE_MAX, -90 + 180 * PotroBlockEntity.ROJO, 0xFFE8C040);
        Dibujo.arco(g, cx, cy, r0, r1, -90 + 180 * PotroBlockEntity.ROJO, 90, 0xFFE03028);
        float a = (float) Math.toRadians(-90 + 180 * pb.agujaVis);
        Dibujo.linea(g, cx, cy, cx + Mth.sin(a) * (r1 + 2), cy - Mth.cos(a) * (r1 + 2), 2F, 0xFFF4F0E8);
        Dibujo.disco(g, cx, cy, 3F, 0xFF888078);
        Component linea = pb.desmayado() ? Component.translatable("hud.miscelanea.potro.desmayado").withStyle(ChatFormatting.GRAY)
                : pb.victimaJugador() ? Component.translatable("hud.miscelanea.potro.tension", Math.round(pb.tension() * 100))
                : Component.translatable("hud.miscelanea.potro.conversion", Math.round(pb.conversion() * 100));
        Dibujo.texto(g, mc.font, linea, cx, cy + 6, 0.75F, 0xFFF0E0C8, true);
        Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.potro.ayuda"), cx, cy + 15, 0.6F, 0xFFB8B0A8, true);
    }
}

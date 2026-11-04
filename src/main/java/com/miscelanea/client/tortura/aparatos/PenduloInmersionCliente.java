package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.qte.Dibujo;
import com.miscelanea.tortura.aparatos.AparatoAccionPacket;
import com.miscelanea.tortura.aparatos.AparatoBlock;
import com.miscelanea.tortura.aparatos.AparatoEfectoPacket;
import com.miscelanea.tortura.aparatos.AparatoJuegoPacket;
import com.miscelanea.tortura.aparatos.AparatosRed;
import com.miscelanea.tortura.aparatos.AparatosVictimas;
import com.miscelanea.tortura.aparatos.InmersionBlockEntity;
import com.miscelanea.tortura.aparatos.PenduloBlock;
import com.miscelanea.tortura.aparatos.PenduloBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * Cliente del péndulo y la silla de inmersión:
 * <ul>
 *   <li>la víctima del péndulo, tumbada boca arriba al dibujarla (como en el potro) y con la cámara obligada a mirar
 *       arriba, a la cuchilla; viñeta que se cierra según baja, las correas que le quedan y el destello del corte;</li>
 *   <li>el minijuego AGUANTAR LA RESPIRACIÓN ({@link JuegoInmersion});</li>
 *   <li>la rueda del ratón sobre el péndulo (velocidad) y las ayudas del captor.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class PenduloInmersionCliente {
    private PenduloInmersionCliente() {}

    /** Lo más que se puede bajar la vista tumbado en el péndulo (grados; -90 = mirar justo arriba). */
    private static final float PITCH_MAX = -50F;
    @Nullable private static JuegoInmersion inmersion;
    private static final Set<Integer> TUMBADOS = new HashSet<>();
    private static boolean enPendulo;
    private static float corte, corteO;
    private static int reloj;

    // ------------------------------------------------------------------ paquetes (desde AparatosCliente)
    public static void juego(AparatoJuegoPacket p) {
        if (p.juego == AparatoJuegoPacket.INMERSION) inmersion = new JuegoInmersion(p.pos, p.semilla);
    }

    public static void parar(BlockPos pos) {
        if (inmersion != null && inmersion.pos.equals(pos)) inmersion = null;
    }

    public static void efecto(AparatoEfectoPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.efecto == AparatoEfectoPacket.CORTE && mc.player != null && p.entidad == mc.player.getId())
            corte = corteO = Math.min(1.6F, 0.8F + p.valor * 0.3F);
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            limpiar();
            return;
        }
        reloj++;
        corteO = corte;
        corte = Math.max(0F, corte - 0.05F);
        AparatosVictimas.Entrada en = AparatosVictimas.de(p.getId());
        boolean pendulo = en != null && en.tipo() == AparatosVictimas.PENDULO;
        if (pendulo) {
            // tumbado boca arriba: la vista, hacia arriba (a la cuchilla)
            float x = enPendulo ? Mth.clamp(p.getXRot(), -90F, PITCH_MAX) : -72F;
            p.setXRot(x);
            p.xRotO = Mth.clamp(p.xRotO, -90F, PITCH_MAX);
        }
        enPendulo = pendulo;
        if (inmersion != null) {
            if (en == null || en.tipo() != AparatosVictimas.INMERSION || !en.pos().equals(inmersion.pos)) {
                if (reloj % 20 == 0 && en == null) inmersion = null;   // (margen: al entrar aún no ha llegado la sincronización)
            } else if (!mc.isPaused()) inmersion.tick(mc);
        }
    }

    private static void limpiar() {
        inmersion = null;
        TUMBADOS.clear();
        enPendulo = false;
        corte = corteO = 0F;
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        limpiar();
    }

    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        if (!enPendulo) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        e.setPitch(Mth.clamp(e.getPitch(), -90F, PITCH_MAX));
        // el corte sacude la vista
        float k = Mth.lerp((float) e.getPartialTick(), corteO, corte);
        if (k > 0F) {
            float t = mc.player.tickCount + (float) e.getPartialTick();
            e.setRoll(e.getRoll() + Mth.sin(t * 2.9F) * 6F * k);
            e.setYaw(e.getYaw() + Mth.sin(t * 3.7F + 1F) * 3F * k);
        }
    }

    // ------------------------------------------------------------------ rueda del ratón: velocidad del péndulo
    @Nullable
    private static BlockPos miradoPrincipal(Minecraft mc) {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return null;
        BlockState s = mc.level.getBlockState(bh.getBlockPos());
        if (!(s.getBlock() instanceof AparatoBlock)) return null;
        return AparatoBlock.principal(s, bh.getBlockPos());
    }

    @SubscribeEvent
    public static void rueda(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || AparatosVictimas.en(mc.player.getId()) || e.getScrollDelta() == 0) return;
        BlockPos p = miradoPrincipal(mc);
        if (p == null || !(mc.level.getBlockState(p).getBlock() instanceof PenduloBlock)) return;
        AparatosRed.CANAL.sendToServer(new AparatoAccionPacket(AparatoAccionPacket.VELOCIDAD_PENDULO, p, e.getScrollDelta() > 0 ? 1 : -1));
        e.setCanceled(true);
    }

    // ------------------------------------------------------------------ la víctima del péndulo, tumbada
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity le = e.getEntity();
        AparatosVictimas.Entrada en = AparatosVictimas.de(le.getId());
        if (en == null || en.tipo() != AparatosVictimas.PENDULO || !(le.level().getBlockEntity(en.pos()) instanceof PenduloBlockEntity pb)) return;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        TUMBADOS.add(le.getId());
        float yaw = pb.frente().toYRot();
        le.yBodyRot = le.yBodyRotO = yaw;
        Vec3 l = pb.eje();
        boolean jugador = le instanceof Player;
        if (jugador) {
            double k = PenduloBlockEntity.OJOS_ATRAS;
            ps.translate(l.x * k, k - PenduloBlockEntity.OJOS_ALTO, l.z * k);
        }
        float giro = 180F - yaw;
        ps.mulPose(Axis.YP.rotationDegrees(giro));
        ps.translate(0, jugador ? PenduloBlockEntity.ALZA : Math.min(0.4F, le.getBbWidth() * 0.45F), 0);
        ps.mulPose(Axis.XP.rotationDegrees(90F));
        ps.mulPose(Axis.YP.rotationDegrees(-giro));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void despues(RenderLivingEvent.Post<?, ?> e) {
        if (TUMBADOS.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ HUD
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (com.miscelanea.client.tortura.muertes.MuertesCliente.localActiva()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        boolean ocultar = mc.options.hideGui;
        AparatosVictimas.Entrada en = AparatosVictimas.de(mc.player.getId());
        if (en != null && en.tipo() == AparatosVictimas.PENDULO && mc.level.getBlockEntity(en.pos()) instanceof PenduloBlockEntity pb)
            hudPendulo(mc, g, w, h, pt, pb, ocultar);
        if (inmersion != null && en != null && en.tipo() == AparatosVictimas.INMERSION && !ocultar) inmersion.dibujar(mc, g, w, h, pt);
        if (en == null && !ocultar) ayudasCaptor(mc, g, w, h);
        float k = Mth.lerp(pt, corteO, corte);
        if (k > 0F) g.fill(0, 0, w, h, ((int) (Math.min(1F, k) * 170) << 24) | 0x900000);
        RenderSystem.disableBlend();
    }

    /** Tumbado bajo la cuchilla: viñeta que se cierra según baja, latidos al final y las correas que quedan. */
    private static void hudPendulo(Minecraft mc, GuiGraphics g, int w, int h, float pt, PenduloBlockEntity pb, boolean ocultar) {
        float b = Mth.lerp(pt, pb.bajadaVisO, pb.bajadaVis);
        float t = mc.player.tickCount + pt;
        float late = b > 0.55F ? Math.max(0F, Mth.sin(t * (0.25F + b * 0.35F))) * (b - 0.55F) * 1.6F : 0F;
        int capas = 10;
        for (int i = 0; i < capas; i++) {
            float f = 1F - i / (float) capas;
            int a = (int) (Mth.clamp(0.15F + b * 0.55F + late * 0.3F, 0F, 0.9F) * 255 * f * 0.22F);
            int e = (int) ((8 + b * 30) * (i + 1) / capas * 2.2F);
            int col = (a << 24) | (late > 0.05F ? 0x200000 : 0);
            g.fill(0, 0, w, e, col);
            g.fill(0, h - e, w, h, col);
            g.fill(0, e, e, h - e, col);
            g.fill(w - e, e, w, h - e, col);
        }
        if (ocultar || !pb.ocupado()) return;
        // correas: muñecas arriba, tobillos abajo (rotas en rojo)
        int c = pb.correas();
        int x0 = w / 2 - 46, y0 = h - 74;
        Dibujo.panel(g, x0, y0, x0 + 92, y0 + 30, 0xFFB08050);
        Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.pendulo.correas"), w / 2F, y0 + 3, 0.65F, 0xFFE8D8C0, true);
        for (int i = 0; i < 4; i++) {
            int cx = x0 + 14 + i * 21, cy = y0 + 14;
            boolean entera = (c & (1 << i)) != 0;
            if (entera) {
                g.fill(cx - 7, cy, cx + 7, cy + 9, 0xFF5A3A22);
                g.fill(cx - 7, cy, cx + 7, cy + 1, 0xFF8A6040);
                g.fill(cx - 2, cy + 2, cx + 2, cy + 7, 0xFFC8A050);
                g.fill(cx - 1, cy + 3, cx + 1, cy + 6, 0xFF5A3A22);
            } else {
                g.fill(cx - 7, cy + 2, cx - 2, cy + 9, 0xFF7A2A20);
                g.fill(cx + 2, cy, cx + 7, cy + 7, 0xFF7A2A20);
                Dibujo.linea(g, cx - 3, cy + 1, cx + 3, cy + 8, 1.2F, 0xFFFF5040);
            }
        }
    }

    /** Lo que ve el captor al mirar el péndulo o la silla de inmersión. */
    private static void ayudasCaptor(Minecraft mc, GuiGraphics g, int w, int h) {
        BlockPos p = miradoPrincipal(mc);
        if (p == null) return;
        BlockEntity be = mc.level.getBlockEntity(p);
        float cx = w / 2F, cy = h / 2F;
        if (be instanceof PenduloBlockEntity pb) {
            int col = pb.bajada() >= 1F ? 0xFFFF5040 : pb.enMarcha() ? 0xFFFFC060 : 0xFFD0C8C0;
            Dibujo.texto(g, mc.font, Component.translatable(pb.enMarcha() ? "hud.miscelanea.pendulo.estado_marcha" : "hud.miscelanea.pendulo.estado_parado",
                    Math.round(pb.bajada() * 100), pb.velocidad()), cx, cy + 14, 0.8F, col, true);
            Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.pendulo.captor"), cx, cy + 24, 0.6F, 0xFFB8B0A8, true);
        } else if (be instanceof InmersionBlockEntity ib) {
            Component linea;
            Entity v = ib.victimaId() >= 0 ? mc.level.getEntity(ib.victimaId()) : null;
            if (v instanceof LivingEntity le) {
                int aire = Math.round(Mth.clamp(le.getAirSupply() / (float) le.getMaxAirSupply(), 0F, 1F) * 100);
                linea = Component.translatable("hud.miscelanea.inmersion.captor_aire", le.getDisplayName(), aire);
            } else linea = Component.translatable(ib.bloqueada() ? "hud.miscelanea.inmersion.bloqueada" : "hud.miscelanea.inmersion.suelta");
            Dibujo.texto(g, mc.font, linea, cx, cy + 14, 0.8F, ib.bloqueada() ? 0xFFFFC060 : 0xFFB0E0FF, true);
            Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.inmersion.captor"), cx, cy + 24, 0.6F, 0xFFB8B0A8, true);
        }
    }
}

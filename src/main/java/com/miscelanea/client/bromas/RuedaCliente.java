package com.miscelanea.client.bromas;

import com.miscelanea.Miscelanea;
import com.miscelanea.bromas.BromasRed;
import com.miscelanea.tortura.aparatos.AparatoBlock;
import com.miscelanea.tortura.aparatos.AparatosVictimas;
import com.miscelanea.tortura.aparatos.RuedaBlock;
import com.miscelanea.tortura.aparatos.RuedaBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * Cliente de la rueda de Catalina:
 * <ul>
 *   <li>la víctima se dibuja girando con la rueda (sobre el eje, que pasa por el centro de su cuerpo); un jugador tiene
 *       los ojos en el eje, así que en primera persona la cámara gira entera sobre sí misma (roll);</li>
 *   <li>minijuego del MAREO (rondas que manda el servidor): la vista se va sola en una dirección que gira con la rueda y
 *       hay que mantener la mirilla dentro del blanco (al frente) con el ratón; con la pantalla girada, el ratón no va
 *       hacia donde parece. Al acabar la ronda se manda el % que se ha estado dentro;</li>
 *   <li>captor: rueda del ratón mirando la rueda = empujón; HUD con las vueltas por minuto y las correas;</li>
 *   <li>fundido a negro antes de morir.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class RuedaCliente {
    private RuedaCliente() {}

    /** Altura (bloques, desde los pies) del punto del cuerpo de un jugador que va en el eje de la rueda. */
    public static final float CENTRO_JUGADOR = 1.0F;
    private static final Set<Integer> GIRADOS = new HashSet<>();
    @Nullable private static Mareo juego;
    private static int fundido, fundidoMax, reloj, esperaRueda;
    private static long ultimoFrame;
    /** Resultado de la última ronda (para el HUD un momento): 1 = bien, -1 = mal. */
    private static int ultimo, ultimoT;

    /** Una ronda del minijuego del mareo. */
    private static final class Mareo {
        final BlockPos pos;
        final int dur;
        final float dif;
        int t, dentro;
        float deriva;

        Mareo(BlockPos pos, int dur, float dif) {
            this.pos = pos;
            this.dur = Math.max(20, dur);
            this.dif = Mth.clamp(dif, 0F, 1F);
            this.deriva = (float) (Math.random() * Math.PI * 2);
        }

        float radio() {
            return 10F - 4.5F * dif;
        }
    }

    // ------------------------------------------------------------------ paquetes
    public static void paquete(BromasRed.RuedaS2C p) {
        switch (p.tipo) {
            case BromasRed.RuedaS2C.JUEGO -> juego = new Mareo(p.pos, p.valor, p.dificultad);
            case BromasRed.RuedaS2C.FUNDIDO -> {
                fundido = fundidoMax = Math.max(1, p.valor);
                juego = null;
            }
            default -> juego = null;
        }
    }

    public static void limpiar() {
        juego = null;
        fundido = 0;
        GIRADOS.clear();
    }

    // ------------------------------------------------------------------ consultas
    @Nullable
    private static RuedaBlockEntity rueda(LivingEntity le) {
        AparatosVictimas.Entrada en = AparatosVictimas.de(le.getId());
        if (en == null || en.tipo() != AparatosVictimas.RUEDA) return null;
        return le.level().getBlockEntity(en.pos()) instanceof RuedaBlockEntity r ? r : null;
    }

    @Nullable
    private static BlockPos miradaRueda(Minecraft mc) {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return null;
        BlockState s = mc.level.getBlockState(bh.getBlockPos());
        if (!(s.getBlock() instanceof RuedaBlock)) return null;
        return AparatoBlock.principal(s, bh.getBlockPos());
    }

    /** Ángulo (grados) que se apartaría la mirada del blanco: yaw y pitch respecto al frente de la rueda. */
    private static float[] desvio(LocalPlayer p, RuedaBlockEntity r, float pt) {
        float dy = Mth.wrapDegrees(r.frente().toYRot() - p.getViewYRot(pt));
        float dp = -p.getViewXRot(pt);
        return new float[]{dy, dp};
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
        if (mc.isPaused()) return;
        reloj++;
        if (esperaRueda > 0) esperaRueda--;
        if (ultimoT > 0) ultimoT--;
        if (fundido > 0 && rueda(mc.player) == null && !mc.player.isDeadOrDying()) fundido = Math.max(0, fundido - 2);
        else if (fundido > 0) fundido--;
        Mareo j = juego;
        if (j == null) return;
        RuedaBlockEntity r = rueda(mc.player);
        if (r == null || !r.getBlockPos().equals(j.pos)) {
            if (reloj % 20 == 0) juego = null;   // (margen: al entrar aún no ha llegado la sincronización)
            return;
        }
        float[] d = desvio(mc.player, r, 1F);
        if (d[0] * d[0] + d[1] * d[1] <= j.radio() * j.radio()) j.dentro++;
        if (++j.t >= j.dur) {
            int pct = Math.round(100F * j.dentro / j.dur);
            ultimo = pct >= RuedaBlockEntity.ACIERTO_PCT ? 1 : -1;
            ultimoT = 30;
            BromasRed.CANAL.sendToServer(new BromasRed.RuedaC2S(BromasRed.RuedaC2S.RESULTADO, j.pos, pct));
            juego = null;
        }
    }

    /** La vista se va sola (en cada fotograma, para que sea suave): dirección que gira con la rueda, más o menos fuerte. */
    @SubscribeEvent
    public static void fotograma(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        long ahora = Util.getNanos();
        float dt = ultimoFrame == 0 ? 0F : Mth.clamp((ahora - ultimoFrame) / 50_000_000F, 0F, 3F);
        ultimoFrame = ahora;
        Minecraft mc = Minecraft.getInstance();
        Mareo j = juego;
        if (j == null || mc.player == null || mc.isPaused() || mc.screen != null || dt <= 0F) return;
        RuedaBlockEntity r = rueda(mc.player);
        if (r == null) return;
        float t = j.t + e.renderTickTime;
        float ang = r.anguloVis(e.renderTickTime) * Mth.DEG_TO_RAD * 0.6F + j.deriva + Mth.sin(t * 0.13F) * 1.3F;
        float fuerza = (0.45F + 1.6F * j.dif) * (0.7F + 0.3F * Mth.sin(t * 0.21F + j.deriva));
        float dyaw = Mth.cos(ang) * fuerza * dt, dpitch = Mth.sin(ang) * fuerza * dt * 0.8F;
        // Entity.turn: grados = valor * 0.15 (y mueve también yRotO/xRotO, así no da saltos)
        mc.player.turn(dyaw / 0.15, dpitch / 0.15);
    }

    // ------------------------------------------------------------------ entrada
    @SubscribeEvent
    public static void raton(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || e.getScrollDelta() == 0 || AparatosVictimas.en(mc.player.getId())) return;
        BlockPos p = miradaRueda(mc);
        if (p == null) return;
        e.setCanceled(true);
        if (esperaRueda > 0) return;
        esperaRueda = 2;
        BromasRed.CANAL.sendToServer(new BromasRed.RuedaC2S(BromasRed.RuedaC2S.IMPULSO, p, e.getScrollDelta() > 0 ? 1 : -1));
    }

    // ------------------------------------------------------------------ cámara
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        RuedaBlockEntity r = rueda(mc.player);
        if (r == null) return;
        e.setRoll(e.getRoll() + r.anguloVis((float) e.getPartialTick()));
    }

    // ------------------------------------------------------------------ dibujar a la víctima
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity le = e.getEntity();
        RuedaBlockEntity r = rueda(le);
        if (r == null) return;
        float pt = e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        GIRADOS.add(le.getId());
        float yaw = r.frente().toYRot();
        le.yBodyRot = le.yBodyRotO = yaw;
        float pivote;
        if (le instanceof Player) {
            // su posición es el eje menos la altura de los ojos: se sube para que el centro del cuerpo quede en el eje
            ps.translate(0, le.getEyeHeight(Pose.STANDING) - CENTRO_JUGADOR, 0);
            pivote = CENTRO_JUGADOR;
        } else pivote = le.getBbHeight() * 0.5F;
        Vec3 f = r.eje();
        ps.translate(0, pivote, 0);
        ps.mulPose(Axis.of(new Vector3f((float) f.x, 0F, (float) f.z)).rotationDegrees(r.anguloVis(pt)));
        ps.translate(0, -pivote, 0);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void despues(RenderLivingEvent.Post<?, ?> e) {
        if (GIRADOS.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ HUD
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RuedaBlockEntity mia = rueda(mc.player);
        if (!mc.options.hideGui) {
            if (mia != null) victima(mc, g, w, h, pt, mia);
            else captor(mc, g, w, h);
        }
        if (fundido > 0) {
            float a = 1F - (fundido - pt) / fundidoMax;
            g.fill(0, 0, w, h, ((int) (Mth.clamp(a * 1.15F, 0F, 1F) * 255) << 24));
        }
        RenderSystem.disableBlend();
    }

    private static void victima(Minecraft mc, GuiGraphics g, int w, int h, float pt, RuedaBlockEntity r) {
        float cx = w / 2F, cy = h / 2F;
        // mareo: viñeta verdosa que late más cuanto más deprisa gira
        float k = Mth.clamp(Math.abs(r.velocidad()) / RuedaBlockEntity.VEL_MAX, 0F, 1F);
        if (k > 0.05F) {
            int a = (int) (k * (40 + 25 * Mth.sin((reloj + pt) * 0.3F)));
            for (int i = 0; i < 6; i++) {
                int b = (int) (a * (1F - i / 6F));
                int m = i * 6;
                int col = (Mth.clamp(b, 0, 255) << 24) | 0x1E3A12;
                g.fill(m, 0, m + 6, h, col);
                g.fill(w - m - 6, 0, w - m, h, col);
            }
        }
        Mareo j = juego;
        if (j != null && j.pos.equals(r.getBlockPos())) {
            // el blanco en pantalla: desvío en grados → píxeles, girado como la cámara
            float[] d = desvio(mc.player, r, pt);
            double fov = mc.options.fov().get();
            float px = h / (float) fov;
            float sx = d[0] * px, sy = d[1] * px;
            float roll = r.anguloVis(pt) * Mth.DEG_TO_RAD;
            float c = Mth.cos(roll), s = Mth.sin(roll);
            float x = cx + sx * c + sy * s, y = cy - sx * s + sy * c;
            boolean dentro = d[0] * d[0] + d[1] * d[1] <= j.radio() * j.radio();
            float rz = j.radio() * px;
            anillo(g, x, y, rz, 1.5F, dentro ? 0xC060E070 : 0xC0E05040);
            anillo(g, x, y, rz * 0.55F, 2.5F, 0xE0F0F0F0);
            anillo(g, x, y, rz * 0.25F, 3F, 0xF0E03030);
            // barra de la ronda: tiempo y lo que lleva dentro
            int bw = 120, bx = (int) cx - bw / 2, by = h - 62;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 6, 0xA0000000);
            g.fill(bx, by, bx + (int) (bw * (1F - (j.t + pt) / j.dur)), by + 2, 0xFFB0B0B0);
            int pct = Math.round(100F * j.dentro / Math.max(1, j.t));
            g.fill(bx, by + 3, bx + bw * Math.min(100, pct) / 100, by + 5, pct >= RuedaBlockEntity.ACIERTO_PCT ? 0xFF60E070 : 0xFFE0A040);
            int ux = bx + bw * RuedaBlockEntity.ACIERTO_PCT / 100;
            g.fill(ux, by + 2, ux + 1, by + 6, 0xFFFFFFFF);
            g.drawCenteredString(mc.font, Component.translatable("hud.miscelanea.rueda.mareo").withStyle(ChatFormatting.YELLOW), (int) cx, by - 11, 0xFFFFFF);
        }
        // correas: 4 hebillas (rojas atadas, verdes sueltas) y los aciertos que lleva
        int y0 = h - 50, x0 = (int) cx - 34;
        for (int i = 0; i < 4; i++) {
            boolean atada = (r.correas() & (1 << i)) != 0;
            int x = x0 + i * 18;
            g.fill(x, y0, x + 14, y0 + 8, atada ? 0xE05A3418 : 0x8040A050);
            g.fill(x + 4, y0 + 2, x + 10, y0 + 6, atada ? 0xFFC8A060 : 0xFF90F0A0);
            g.fill(x + 6, y0 + 3, x + 8, y0 + 5, atada ? 0xFF5A3418 : 0xFF40A050);
        }
        if (ultimoT > 0) {
            int a = Mth.clamp(ultimoT * 12, 0, 255);
            g.drawCenteredString(mc.font, Component.translatable(ultimo > 0 ? "hud.miscelanea.rueda.bien" : "hud.miscelanea.rueda.mal"),
                    (int) cx, (int) cy + 24, (a << 24) | (ultimo > 0 ? 0x60E070 : 0xE05040));
        }
    }

    private static void captor(Minecraft mc, GuiGraphics g, int w, int h) {
        BlockPos p = miradaRueda(mc);
        if (p == null || !(mc.level.getBlockEntity(p) instanceof RuedaBlockEntity r)) return;
        int cx = w / 2, cy = h / 2;
        g.drawCenteredString(mc.font, Component.translatable("hud.miscelanea.rueda.rpm", r.rpm()), cx, cy + 12, 0xFFE8C890);
        if (r.ocupado())
            g.drawCenteredString(mc.font, Component.translatable("hud.miscelanea.rueda.correas", Integer.bitCount(r.correas())), cx, cy + 23, 0xFFC8B8A8);
        g.drawCenteredString(mc.font, Component.translatable("hud.miscelanea.rueda.ayuda"), cx, cy + 34, 0xFF989088);
    }

    /** Anillo aproximado con cuadraditos (sin texturas). */
    private static void anillo(GuiGraphics g, float x, float y, float r, float grosor, int color) {
        int n = Math.max(16, (int) (r * 1.5F));
        for (int i = 0; i < n; i++) {
            float a = Mth.TWO_PI * i / n;
            float px = x + Mth.cos(a) * r, py = y + Mth.sin(a) * r;
            g.fill((int) (px - grosor / 2), (int) (py - grosor / 2), (int) (px + grosor / 2) + 1, (int) (py + grosor / 2) + 1, color);
        }
    }
}

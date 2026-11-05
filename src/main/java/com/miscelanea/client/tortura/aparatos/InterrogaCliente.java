package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.qte.Dibujo;
import com.miscelanea.client.tortura.qte.QteCliente;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.aparatos.AparatoBlock;
import com.miscelanea.tortura.aparatos.AparatosRed;
import com.miscelanea.tortura.aparatos.AparatosRegistro;
import com.miscelanea.tortura.aparatos.AparatosVictimas;
import com.miscelanea.tortura.aparatos.GanchoBlock;
import com.miscelanea.tortura.aparatos.GanchoBlockEntity;
import com.miscelanea.tortura.aparatos.InterrogaAccionPacket;
import com.miscelanea.tortura.aparatos.InterrogaEfectoPacket;
import com.miscelanea.tortura.aparatos.MaletinItem;
import com.miscelanea.tortura.aparatos.SillaInterrogatorioBlock;
import com.miscelanea.tortura.aparatos.SillaInterrogatorioBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
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
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * Cliente de la silla de interrogatorio y del gancho para colgar:
 * <ul>
 *   <li>interrogador: con el maletín en la mano, el clic derecho sobre la víctima (o la silla) abre la
 *       {@link RuedaHerramientas}; cerca de la silla ve el monitor cardíaco en el HUD (y la cuenta atrás del paro);</li>
 *   <li>víctima de la silla: la sala "nada" (la cámara se mece más cuanto más pulsaciones), oye su corazón al ritmo del
 *       monitor, viñeta que late; con el corazón parado, la pantalla se apaga; la adrenalina la despierta de golpe
 *       (destello). SALTAR: aguantar (MACHACAR);</li>
 *   <li>colgado: el cuerpo se dibuja boca abajo siguiendo la cuerda (para todos); en primera persona la cámara va cabeza
 *       abajo (roll 180º, los ojos en la cabeza: CameraMixin → {@link #ojoColgado}) y la sangre se le va a la cabeza
 *       (viñeta roja que crece, la vista se mece y emborrona). SALTAR: intentar soltarse. Rueda del ratón mirando el
 *       gancho: la manivela (captor).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class InterrogaCliente {
    private InterrogaCliente() {}

    private static final Set<Integer> VOLTEADOS = new HashSet<>();
    private static int reloj, ultimoLatido = -1, esperaTecla, latidoT = -100;
    private static float despertar, despertarO, golpe, golpeO;
    @Nullable private static BlockPos sillaCerca;

    // ------------------------------------------------------------------ paquetes
    public static void efecto(InterrogaEfectoPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || p.entidad != mc.player.getId()) return;
        switch (p.efecto) {
            case InterrogaEfectoPacket.DESPERTAR -> despertar = despertarO = 1F;
            case InterrogaEfectoPacket.GOLPE -> golpe = golpeO = 1F;
            case InterrogaEfectoPacket.SUELTA -> golpe = golpeO = 0.6F;
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ consultas
    @Nullable
    private static AparatosVictimas.Entrada mia(Minecraft mc, int tipo) {
        if (mc.player == null) return null;
        AparatosVictimas.Entrada en = AparatosVictimas.de(mc.player.getId());
        return en != null && en.tipo() == tipo ? en : null;
    }

    @Nullable
    private static GanchoBlockEntity gancho(LivingEntity le) {
        AparatosVictimas.Entrada en = AparatosVictimas.de(le.getId());
        if (en == null || en.tipo() != AparatosVictimas.COLGADO) return null;
        return le.level().getBlockEntity(en.pos()) instanceof GanchoBlockEntity g ? g : null;
    }

    @Nullable
    private static SillaInterrogatorioBlockEntity silla(Minecraft mc, @Nullable BlockPos p) {
        if (p == null || mc.level == null) return null;
        return mc.level.getBlockEntity(p) instanceof SillaInterrogatorioBlockEntity s ? s : null;
    }

    /**
     * Desde MouseHandlerMixin: colgado boca abajo y en primera persona la cámara va girada 180º (roll), así que el ratón
     * se invierte en los dos ejes para que mover a la derecha/arriba mueva la vista a la derecha/arriba de la pantalla.
     */
    public static boolean ratonInvertido() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && mc.options.getCameraType().isFirstPerson() && gancho(mc.player) != null;
    }

    /** Desde CameraMixin: en primera persona, colgado, los ojos van en la cabeza (abajo). null = cámara normal. */
    @Nullable
    public static double[] ojoColgado(Entity e, float pt) {
        if (!(e instanceof LivingEntity le) || le.level() == null || !le.level().isClientSide) return null;
        GanchoBlockEntity g = gancho(le);
        if (g == null) return null;
        double h = le.getBbHeight();
        Vec3 dir = GanchoBlockEntity.direccion(g.axVis(pt), g.azVis(pt));
        Vec3 c = le.getPosition(pt).add(0, h * 0.5, 0);
        Vec3 o = c.add(dir.scale(le.getEyeHeight() - h * 0.5));
        return new double[]{o.x, o.y, o.z};
    }

    /** Lo roja que tiene la cabeza el colgado (0..1, 1 = ya le hace daño). */
    private static float sangre(GanchoBlockEntity g) {
        return Mth.clamp(g.colgadoCli / (float) com.miscelanea.ConfigServidor.ganchoDanoDesdeTicks(), 0F, 1F);
    }

    /** Estrés de la víctima de la silla (0..1 según las pulsaciones). */
    private static float estres(SillaInterrogatorioBlockEntity s, float pt) {
        return Mth.clamp((Mth.lerp(pt, s.bpmVisO, s.bpmVis) - 85F) / 110F, 0F, 1F);
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
        if (esperaTecla > 0) esperaTecla--;
        despertarO = despertar;
        despertar = Math.max(0F, despertar - 0.035F);
        golpeO = golpe;
        golpe = Math.max(0F, golpe - 0.08F);
        // víctima de la silla: su corazón, al ritmo del monitor
        AparatosVictimas.Entrada en = mia(mc, AparatosVictimas.INTERROGATORIO);
        SillaInterrogatorioBlockEntity s = en == null ? null : silla(mc, en.pos());
        if (s != null && !mc.isPaused()) {
            if (ultimoLatido >= 0 && s.latidos != ultimoLatido && s.paroCli <= 0) {
                float k = estres(s, 1F);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(TorturaRegistro.TRAMPA_LATIDO.get(), 0.9F + k * 0.3F, 0.35F + k * 0.55F));
                latidoT = reloj;
            }
            ultimoLatido = s.latidos;
        } else ultimoLatido = -1;
        // colgado: cuando la sangre ya aprieta, se oye el pulso en las sienes
        GanchoBlockEntity g = gancho(p);
        if (g != null && !mc.isPaused()) {
            float k = sangre(g);
            int cada = Math.round(26 - 10 * k);
            if (k > 0.35F && reloj - latidoT >= cada) {
                latidoT = reloj;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(TorturaRegistro.TRAMPA_LATIDO.get(), 0.85F, 0.2F + 0.5F * k));
            }
        }
        // interrogador: la silla ocupada más cercana (para el monitor del HUD)
        if (reloj % 10 == 0) sillaCerca = buscarSilla(mc, p);
    }

    private static boolean conMaletin(LocalPlayer p) {
        return p.getMainHandItem().getItem() instanceof MaletinItem || p.getOffhandItem().getItem() instanceof MaletinItem;
    }

    @Nullable
    private static BlockPos buscarSilla(Minecraft mc, LocalPlayer p) {
        if (!conMaletin(p) || AparatosVictimas.en(p.getId())) return null;
        BlockPos c = p.blockPosition();
        BlockPos mejor = null;
        double dmin = 7 * 7;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-6, -3, -6), c.offset(6, 3, 6))) {
            if (!(mc.level.getBlockEntity(q) instanceof SillaInterrogatorioBlockEntity s) || !s.ocupado()) continue;
            double d = q.distToCenterSqr(p.position());
            if (d < dmin) {
                dmin = d;
                mejor = q.immutable();
            }
        }
        return mejor;
    }

    private static void limpiar() {
        VOLTEADOS.clear();
        ultimoLatido = -1;
        despertar = despertarO = golpe = golpeO = 0F;
        sillaCerca = null;
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        limpiar();
    }

    // ------------------------------------------------------------------ entrada
    /** SALTAR: la víctima de la silla aguanta; el colgado intenta soltarse. */
    @SubscribeEvent
    public static void tecla(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || e.getAction() != GLFW.GLFW_PRESS || esperaTecla > 0 || QteCliente.activo()) return;
        if (!com.miscelanea.client.TeclasMod.esAccion(e.getKey(), e.getScanCode())) return;
        AparatosVictimas.Entrada en = AparatosVictimas.de(mc.player.getId());
        if (en == null) return;
        if (en.tipo() == AparatosVictimas.INTERROGATORIO) {
            { SillaInterrogatorioBlockEntity s = silla(mc, en.pos()); if (s != null && s.paroCli > 0) return; }
            AparatosRed.CANAL.sendToServer(new InterrogaAccionPacket(InterrogaAccionPacket.AGUANTAR, en.pos(), 0));
            esperaTecla = 10;
        } else if (en.tipo() == AparatosVictimas.COLGADO) {
            AparatosRed.CANAL.sendToServer(new InterrogaAccionPacket(InterrogaAccionPacket.ESCAPAR, en.pos(), 0));
            esperaTecla = 10;
        }
    }

    /** Clic derecho con el maletín mirando a la víctima o a la silla: la rueda de herramientas. */
    @SubscribeEvent
    public static void interaccion(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (!e.isUseItem() || e.getHand() != InteractionHand.MAIN_HAND || mc.player == null || mc.level == null || mc.screen != null) return;
        if (!(mc.player.getMainHandItem().getItem() instanceof MaletinItem) || AparatosVictimas.en(mc.player.getId())) return;
        BlockPos p = null;
        HitResult hr = mc.hitResult;
        if (hr instanceof EntityHitResult eh) {
            AparatosVictimas.Entrada en = AparatosVictimas.de(eh.getEntity().getId());
            if (en != null && en.tipo() == AparatosVictimas.INTERROGATORIO) p = en.pos();
        } else if (hr instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            BlockState st = mc.level.getBlockState(bh.getBlockPos());
            if (st.getBlock() instanceof SillaInterrogatorioBlock) p = AparatoBlock.principal(st, bh.getBlockPos());
        }
        SillaInterrogatorioBlockEntity s = silla(mc, p);
        if (s == null || !s.ocupado()) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(AparatosRegistro.MALETIN_ABRIR.get(), 1F, 0.8F));
        mc.setScreen(new RuedaHerramientas(p));
    }

    /** Rueda del ratón mirando el gancho: la manivela. */
    @SubscribeEvent
    public static void rueda(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || e.getScrollDelta() == 0 || AparatosVictimas.en(mc.player.getId())) return;
        if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.level.getBlockState(bh.getBlockPos()).getBlock() instanceof GanchoBlock)) return;
        AparatosRed.CANAL.sendToServer(new InterrogaAccionPacket(InterrogaAccionPacket.MANIVELA, bh.getBlockPos(), e.getScrollDelta() > 0 ? 1 : -1));
        e.setCanceled(true);
    }

    // ------------------------------------------------------------------ cámara
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !mc.options.getCameraType().isFirstPerson()) return;
        float pt = (float) e.getPartialTick();
        float t = reloj + pt;
        GanchoBlockEntity g = gancho(p);
        if (g != null) {
            // cabeza abajo; la cuerda inclinada hacia su izquierda/derecha también gira la vista
            double yaw = Math.toRadians(p.getViewYRot(pt));
            float lat = (float) (g.axVis(pt) * Math.cos(yaw) + g.azVis(pt) * Math.sin(yaw));
            float k = sangre(g);
            float mece = 0.6F + 4.5F * k * k;
            e.setRoll(e.getRoll() + 180F - (float) Math.toDegrees(lat) * 0.8F + Mth.sin(t * 0.045F) * mece);
            e.setPitch(e.getPitch() + Mth.sin(t * 0.031F + 1.3F) * mece * 0.5F);
            e.setYaw(e.getYaw() + Mth.sin(t * 0.027F + 0.4F) * mece * 0.6F);
            return;
        }
        AparatosVictimas.Entrada en = mia(mc, AparatosVictimas.INTERROGATORIO);
        SillaInterrogatorioBlockEntity s = en == null ? null : silla(mc, en.pos());
        if (s != null) {
            // la sala "nada": se mece más cuanto más desbocado va el corazón
            float k = estres(s, pt);
            float m = 0.4F + k * 4.0F;
            float late = Math.max(0F, 1F - (reloj - latidoT + pt) / 5F) * (0.3F + k) * 1.2F;
            e.setRoll(e.getRoll() + Mth.sin(t * 0.061F) * m + Mth.sin(t * 0.17F + 2F) * m * 0.3F);
            e.setPitch(e.getPitch() + Mth.sin(t * 0.043F + 1F) * m * 0.5F + late);
            e.setYaw(e.getYaw() + Mth.sin(t * 0.052F + 0.5F) * m * 0.5F);
            float d = Mth.lerp(pt, despertarO, despertar);
            if (d > 0F) {
                e.setRoll(e.getRoll() + Mth.sin(t * 3.1F) * 6F * d);
                e.setPitch(e.getPitch() + Mth.sin(t * 2.3F + 1F) * 4F * d);
            }
            float gg = Mth.lerp(pt, golpeO, golpe);
            if (gg > 0F) e.setRoll(e.getRoll() + Mth.sin(t * 2.7F) * 4F * gg);
        }
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        Minecraft mc = Minecraft.getInstance();
        AparatosVictimas.Entrada en = mia(mc, AparatosVictimas.INTERROGATORIO);
        SillaInterrogatorioBlockEntity s = en == null ? null : silla(mc, en.pos());
        if (s == null || s.paroCli > 0) return;
        float pt = (float) e.getPartialTick();
        float k = estres(s, pt);
        float late = Math.max(0F, 1F - (reloj - latidoT + pt) / 4F);
        e.setFOV(e.getFOV() * (1.0 - late * (0.008 + 0.03 * k)));
    }

    // ------------------------------------------------------------------ el colgado, boca abajo
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity le = e.getEntity();
        GanchoBlockEntity g = gancho(le);
        if (g == null) return;
        float pt = e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        VOLTEADOS.add(le.getId());
        float h = le.getBbHeight();
        Vec3 d = GanchoBlockEntity.direccion(g.axVis(pt), g.azVis(pt));
        double yaw = Math.toRadians(Mth.lerp(pt, le.yBodyRotO, le.yBodyRot));
        // girar 180º alrededor de la cadera (el centro de la caja) sobre el eje de delante (no se pone de espaldas) y
        // luego inclinar con la cuerda
        ps.translate(0, h * 0.5F, 0);
        ps.mulPose(new Quaternionf().rotationTo(0F, -1F, 0F, (float) d.x, (float) d.y, (float) d.z));
        ps.mulPose(new Quaternionf().rotationAxis(Mth.PI, (float) -Math.sin(yaw), 0F, (float) Math.cos(yaw)));
        ps.translate(0, -h * 0.5F, 0);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void despues(RenderLivingEvent.Post<?, ?> e) {
        if (VOLTEADOS.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ HUD
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (com.miscelanea.client.tortura.muertes.MuertesCliente.localActiva()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        boolean ocultar = mc.options.hideGui;
        GanchoBlockEntity gb = gancho(p);
        AparatosVictimas.Entrada en = mia(mc, AparatosVictimas.INTERROGATORIO);
        SillaInterrogatorioBlockEntity sv = en == null ? null : silla(mc, en.pos());
        if (gb != null) hudColgado(mc, g, w, h, pt, gb, ocultar);
        else if (sv != null) hudVictima(mc, g, w, h, pt, sv, ocultar);
        else if (!ocultar) {
            SillaInterrogatorioBlockEntity s = silla(mc, sillaCerca);
            if (s != null && s.ocupado() && conMaletin(p)) hudInterrogador(mc, g, w, h, pt, s);
            ayudaGancho(mc, g, w, h);
        }
        RenderSystem.disableBlend();
    }

    /** Viñeta por capas (bordes) de un color. */
    private static void vineta(GuiGraphics g, int w, int h, float fuerza, float ancho, int rgb) {
        int capas = 12;
        for (int i = 0; i < capas; i++) {
            float f = 1F - i / (float) capas;
            int a = (int) (Mth.clamp(fuerza, 0F, 1F) * 255 * f * 0.2F);
            int e = (int) (ancho * (i + 1) / capas);
            int col = (a << 24) | (rgb & 0xFFFFFF);
            g.fill(0, 0, w, e, col);
            g.fill(0, h - e, w, h, col);
            g.fill(0, e, e, h - e, col);
            g.fill(w - e, e, w, h - e, col);
        }
    }

    private static void hudVictima(Minecraft mc, GuiGraphics g, int w, int h, float pt, SillaInterrogatorioBlockEntity s, boolean ocultar) {
        float k = estres(s, pt);
        float late = Math.max(0F, 1F - (reloj - latidoT + pt) / 6F);
        vineta(g, w, h, 0.25F + k * 0.55F + late * 0.25F * (0.3F + k), Math.min(w, h) * (0.16F + 0.1F * k), k > 0.6F ? 0x2A0000 : 0x000000);
        if (s.paroCli > 0) {
            // el corazón se ha parado: todo se apaga
            float a = Mth.clamp((SillaInterrogatorioBlockEntity.PARO_MAX - s.paroCli + pt) / 45F, 0F, 1F);
            g.fill(0, 0, w, h, ((int) (a * 245) << 24));
        }
        float d = Mth.lerp(pt, despertarO, despertar);
        if (d > 0F) g.fill(0, 0, w, h, ((int) (Math.min(1F, d * 1.3F) * 235) << 24) | 0xFFFFFF);
        float gg = Mth.lerp(pt, golpeO, golpe);
        if (gg > 0F) g.fill(0, 0, w, h, ((int) (gg * 110) << 24) | 0x8A0000);
        if (ocultar || s.paroCli > 0) return;
        if (!QteCliente.activo()) {
            Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.interrogatorio.victima", mc.options.keyJump.getTranslatedKeyMessage()),
                    w / 2F, h - 72, 0.75F, 0xFFE0D0C0, true);
            Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.interrogatorio.confesar").withStyle(ChatFormatting.ITALIC),
                    w / 2F, h - 62, 0.6F, 0xFFA8A098, true);
        }
    }

    private static void hudColgado(Minecraft mc, GuiGraphics g, int w, int h, float pt, GanchoBlockEntity gb, boolean ocultar) {
        float k = sangre(gb);
        float t = reloj + pt;
        float late = k > 0.35F ? Math.max(0F, 1F - (reloj - latidoT + pt) / 7F) * k : 0F;
        // la sangre baja a la cabeza: los bordes se ponen rojos y se emborronan (capas desplazadas que "respiran")
        vineta(g, w, h, 0.12F + k * 0.75F + late * 0.2F, Math.min(w, h) * (0.12F + 0.22F * k), 0x5A0000);
        if (k > 0.25F) {
            float b = (k - 0.25F) / 0.75F;
            int capas = 4;
            for (int i = 0; i < capas; i++) {
                float o = 6F + i * 5F + Mth.sin(t * 0.05F + i) * 4F * b;
                int a = (int) (b * 34 * (1F - i / (float) capas));
                vineta(g, w, h, b * 0.35F, o + Math.min(w, h) * 0.08F * b, 0x3A0000);
                g.fill(0, 0, w, h, (a << 24) | 0x400000);
            }
        }
        if (gb.colgadoCli >= com.miscelanea.ConfigServidor.ganchoDanoDesdeTicks() && mc.player != null && mc.player.hurtTime > 0)
            g.fill(0, 0, w, h, ((int) (mc.player.hurtTime * 8) << 24) | 0x900000);
        float gg = Mth.lerp(pt, golpeO, golpe);
        if (gg > 0F) g.fill(0, 0, w, h, ((int) (gg * 90) << 24) | 0x8A0000);
        if (ocultar || QteCliente.activo()) return;
        Component c = gb.agarrado() ? Component.translatable("hud.miscelanea.gancho.agarrado")
                : Component.translatable("hud.miscelanea.gancho.victima", mc.options.keyJump.getTranslatedKeyMessage());
        // (la pantalla va del revés, pero el HUD no)
        Dibujo.texto(g, mc.font, c, w / 2F, h - 72, 0.75F, 0xFFE8D0C0, true);
    }

    private static void hudInterrogador(Minecraft mc, GuiGraphics g, int w, int h, float pt, SillaInterrogatorioBlockEntity s) {
        int ancho = 150, alto = 46;
        MonitorEcg.dibujarGui(mc, g, w - ancho - 14, 26, ancho, alto, s, pt);
        if (s.paroCli > 0) {
            float seg = Math.max(0F, (s.paroCli - pt) / 20F);
            boolean on = (reloj / 4) % 2 == 0;
            Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.interrogatorio.paro").withStyle(ChatFormatting.BOLD), w / 2F, h / 2F - 40, 1.4F,
                    on ? 0xFFFF3830 : 0xFFB01810, true);
            Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.interrogatorio.paro_cuenta", String.format(java.util.Locale.ROOT, "%.1f", seg)),
                    w / 2F, h / 2F - 26, 0.9F, 0xFFFFE0D0, true);
            vineta(g, w, h, 0.25F + (on ? 0.15F : 0F), Math.min(w, h) * 0.1F, 0x900000);
        } else {
            Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.interrogatorio.captor"), w - ancho / 2F - 14, 26 + alto + 10, 0.6F, 0xFFB8B0A8, true);
        }
    }

    /** Lo que ve el captor mirando el gancho. */
    private static void ayudaGancho(Minecraft mc, GuiGraphics g, int w, int h) {
        if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.level.getBlockEntity(bh.getBlockPos()) instanceof GanchoBlockEntity gb)) return;
        float cx = w / 2F, cy = h / 2F;
        Component linea = Component.translatable("hud.miscelanea.gancho.cuerda", String.format(java.util.Locale.ROOT, "%.2f", gb.largoVis(1F)));
        Dibujo.texto(g, mc.font, linea, cx, cy + 14, 0.8F, 0xFFE0C8A0, true);
        Dibujo.texto(g, mc.font, Component.translatable(gb.ocupado() ? "hud.miscelanea.gancho.captor" : "hud.miscelanea.gancho.vacio"), cx, cy + 24, 0.6F,
                0xFFB8B0A8, true);
    }
}

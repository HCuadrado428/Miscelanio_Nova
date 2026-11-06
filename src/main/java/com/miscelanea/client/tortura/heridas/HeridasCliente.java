package com.miscelanea.client.tortura.heridas;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.heridas.Herida;
import com.miscelanea.tortura.heridas.HeridasDatos;
import com.miscelanea.tortura.heridas.Parte;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lado cliente de las heridas: guarda las de cada jugador visible, las va "curando" a ojo entre sincronizaciones,
 * y hace los efectos tipo Fallout del jugador local (temblor de puntería, cojera, viñeta roja) y la silueta del HUD.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HeridasCliente {
    private static final Map<Integer, HeridasDatos> DATOS = new HashMap<>();
    private static final ResourceLocation VINETA = new ResourceLocation(Miscelanea.MOD_ID, "textures/heridas/vineta.png");
    /** Destello de cada parte en la silueta del HUD cuando recibe una herida nueva. */
    private static final int[] DESTELLO = new int[Parte.V.length];
    private static final Set<Integer> COJEANDO = new HashSet<>();
    private static float swayYaw, swayPitch;
    private static PlayerModel<AbstractClientPlayer> brazoAncho, brazoFino;
    private static boolean avisoCpm;

    private HeridasCliente() {}

    // ------------------------------------------------------------------ datos

    public static void recibir(int id, HeridasDatos d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == id) {
            HeridasDatos antes = DATOS.get(id);
            for (Parte p : Parte.V) {
                int a = antes == null ? 0 : antes.cuantas(p);
                if (d.cuantas(p) > a || (antes != null && peso(d, p) > peso(antes, p) + 0.2F)) DESTELLO[p.ordinal()] = 14;
            }
        }
        if (d.vacio()) {
            DATOS.remove(id);
            HeridasTextura.soltar(id);
        } else {
            DATOS.put(id, d);
        }
    }

    private static float peso(HeridasDatos d, Parte p) {
        float s = 0;
        for (Herida h : d.lista) if (h.parte == p) s += h.peso();
        return s;
    }

    public static HeridasDatos datos(Player p) {
        return DATOS.get(p.getId());
    }

    private static HeridasDatos mias() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? null : DATOS.get(p.getId());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!DATOS.isEmpty()) {
                DATOS.clear();
                HeridasTextura.soltarTodo();
            }
            return;
        }
        if (mc.isPaused()) return;
        for (int i = 0; i < DESTELLO.length; i++) if (DESTELLO[i] > 0) DESTELLO[i]--;
        // curación a ojo (el servidor manda la cuenta buena cada 10 s)
        for (HeridasDatos d : DATOS.values()) {
            for (Herida h : d.lista) h.restante = Math.max(1, h.restante - (d.vendada(h.parte) ? 2 : 1));
            for (int i = 0; i < d.vendas.length; i++) if (d.vendas[i] > 1) d.vendas[i]--;
        }
        // limpiar jugadores que ya no se ven
        if (mc.level.getGameTime() % 100 == 0) {
            DATOS.keySet().removeIf(id -> {
                Entity en = mc.level.getEntity(id);
                return !(en instanceof Player) || en.isRemoved();
            });
            for (int id : HeridasTextura.ids()) if (!DATOS.containsKey(id)) HeridasTextura.soltar(id);
            if (!avisoCpm && ModList.get().isLoaded("cpm")) {
                avisoCpm = true;
                Miscelanea.LOGGER.info("Heridas: Customizable Player Models está cargado; las heridas se siguen dibujando en su propia capa sobre el modelo normal");
            }
        }
        temblorPunteria(mc);
    }

    @SubscribeEvent
    public static void descargar(LevelEvent.Unload e) {
        if (e.getLevel().isClientSide()) {
            DATOS.clear();
            HeridasTextura.soltarTodo();
            COJEANDO.clear();
        }
    }

    // ------------------------------------------------------------------ brazo tullido: la mira tiembla

    private static float ruido(float t) {
        return Mth.sin(t * 0.071F) * 0.55F + Mth.sin(t * 0.137F + 1.3F) * 0.3F + Mth.sin(t * 0.29F + 4.1F) * 0.15F;
    }

    private static void temblorPunteria(Minecraft mc) {
        LocalPlayer p = mc.player;
        HeridasDatos d = mias();
        float nivel = 0;
        if (p != null && d != null) {
            Parte principal = p.getMainArm() == HumanoidArm.RIGHT ? Parte.BRAZO_DER : Parte.BRAZO_IZQ;
            Parte otro = principal == Parte.BRAZO_DER ? Parte.BRAZO_IZQ : Parte.BRAZO_DER;
            nivel = Math.min(1F, d.nivel(principal) + d.nivel(otro) * 0.4F);
        }
        if (p == null || nivel < 0.15F || mc.screen != null) {
            swayYaw = swayPitch = 0;
            return;
        }
        // grados: poco al ir con las manos libres, bastante más al apuntar con arco/ballesta/tridente
        float amp = nivel * (p.isUsingItem() ? 2.6F : 0.7F);
        float t = p.tickCount;
        float yaw = ruido(t) * amp, pitch = ruido(t * 1.3F + 50F) * amp * 0.7F;
        // se aplica la diferencia con el tick anterior: la mira deriva alrededor de donde apuntas, sin acumularse
        p.setYRot(p.getYRot() + (yaw - swayYaw));
        p.setXRot(Mth.clamp(p.getXRot() + (pitch - swayPitch), -90F, 90F));
        swayYaw = yaw;
        swayPitch = pitch;
    }

    // ------------------------------------------------------------------ pierna tullida: cojera

    /** Inclinación de la cojera (grados) y hacia qué lado: el golpe de cadera al apoyar la pierna mala. */
    private static float cojera(Player p, HeridasDatos d, float partial) {
        if (d == null || !p.onGround() || p.isPassenger() || p.isSwimming() || p.isFallFlying()) return 0;
        float der = d.nivel(Parte.PIERNA_DER), izq = d.nivel(Parte.PIERNA_IZQ);
        float n = Math.max(der, izq);
        if (n < 0.15F) return 0;
        float vel = Math.min(1F, p.walkAnimation.speed(partial));
        if (vel < 0.05F) return 0;
        float pos = p.walkAnimation.position(partial);
        float fase = der >= izq ? 0F : Mth.PI;
        float paso = Math.max(0F, Mth.sin(pos * 0.6662F + fase));
        return paso * paso * n * vel * 7F * (der >= izq ? 1F : -1F);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void antesJugador(RenderPlayerEvent.Pre e) {
        Player p = e.getEntity();
        float ang = cojera(p, datos(p), e.getPartialTick());
        if (Math.abs(ang) < 0.05F) return;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        COJEANDO.add(p.getId());
        float yaw = Mth.rotLerp(e.getPartialTick(), p.yBodyRotO, p.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.translate(0, -Math.abs(ang) * 0.006F, 0);       // se hunde un poco al apoyar
        ps.mulPose(Axis.ZP.rotationDegrees(ang));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void despuesJugador(RenderPlayerEvent.Post e) {
        if (COJEANDO.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !mc.options.getCameraType().isFirstPerson()) return;
        HeridasDatos d = mias();
        if (d == null) return;
        float partial = (float) e.getPartialTick();
        float ang = cojera(p, d, partial);
        if (ang != 0) {
            e.setRoll(e.getRoll() + ang * 0.35F);
            e.setPitch(e.getPitch() + Math.abs(ang) * 0.12F);
        }
        // cabeza tullida: la vista se balancea un poco, como mareado
        float cab = d.nivel(Parte.CABEZA);
        if (cab >= HeridasDatos.LISIADO) {
            float t = p.tickCount + partial;
            e.setRoll(e.getRoll() + Mth.sin(t * 0.05F) * 1.6F * cab);
            e.setYaw(e.getYaw() + Mth.sin(t * 0.037F + 2F) * 0.8F * cab);
        }
    }

    // ------------------------------------------------------------------ primera persona: heridas en el brazo

    @SubscribeEvent
    public static void brazo(RenderArmEvent e) {
        AbstractClientPlayer p = e.getPlayer();
        HeridasDatos d = datos(p);
        if (d == null || p.isInvisible()) return;
        Parte parte = e.getArm() == HumanoidArm.RIGHT ? Parte.BRAZO_DER : Parte.BRAZO_IZQ;
        if (!HeridasLayer.tiene(d, parte)) return;
        boolean fino = "slim".equals(p.getModelName());
        PlayerModel<AbstractClientPlayer> m;
        if (fino) {
            if (brazoFino == null) brazoFino = HeridasLayer.modelo(HeridasLayer.INFLADO, true);
            m = brazoFino;
        } else {
            if (brazoAncho == null) brazoAncho = HeridasLayer.modelo(HeridasLayer.INFLADO, false);
            m = brazoAncho;
        }
        // lo mismo que PlayerRenderer.renderHand con el brazo de verdad
        m.attackTime = 0.0F;
        m.crouching = false;
        m.swimAmount = 0.0F;
        m.setupAnim(p, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        ModelPart b = parte == Parte.BRAZO_DER ? m.rightArm : m.leftArm;
        b.xRot = 0.0F;
        b.visible = true;
        ResourceLocation tex = HeridasTextura.de(p.getId(), d, fino);
        b.render(e.getPoseStack(), e.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(tex)), e.getPackedLight(), OverlayTexture.NO_OVERLAY);
    }

    // ------------------------------------------------------------------ HUD

    /** Viñeta roja que late (cabeza herida). */
    public static void vineta(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        HeridasDatos d = mias();
        if (d == null || mc.options.hideGui || mc.player == null) return;
        float cab = d.nivel(Parte.CABEZA);
        if (cab < 0.1F) return;
        float t = mc.player.tickCount + partial;
        // latido: dos golpes seguidos y una pausa
        float ciclo = (t % 24F) / 24F;
        float latido = Math.max(0F, 1F - Math.abs(ciclo - 0.1F) * 12F) + 0.7F * Math.max(0F, 1F - Math.abs(ciclo - 0.3F) * 12F);
        float a = Math.min(0.85F, cab * (0.45F + 0.3F * latido));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        g.setColor(1F, 1F, 1F, a);
        g.blit(VINETA, 0, 0, 0, 0, w, h, w, h);
        g.setColor(1F, 1F, 1F, 1F);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static final int VERDE = 0x1AFF80, VERDE_OSCURO = 0x0B6E3A, AMBAR = 0xFFC23A, ROJO = 0xFF3A2E;

    /** Silueta del cuerpo abajo a la izquierda, con el estado de cada extremidad. */
    public static void silueta(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        HeridasDatos d = mias();
        if (d == null || mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        boolean algo = false;
        for (Parte p : Parte.V) if (d.dano(p) > 0.02F || d.vendada(p)) algo = true;
        if (!algo) return;
        // a la izquierda de la barra rápida (y de la mano secundaria), donde no tapa el chat ni los corazones
        int x0 = Math.max(2, w / 2 - 91 - 29 - 36), y0 = h - 54;
        float t = mc.player.tickCount + partial;
        // marco tipo pantalla de fósforo
        g.fill(x0, y0, x0 + 30, y0 + 52, 0x99041A0E);
        g.fill(x0, y0, x0 + 30, y0 + 1, 0xCC000000 | VERDE_OSCURO);
        g.fill(x0, y0 + 51, x0 + 30, y0 + 52, 0xCC000000 | VERDE_OSCURO);
        g.fill(x0, y0, x0 + 1, y0 + 52, 0xCC000000 | VERDE_OSCURO);
        g.fill(x0 + 29, y0, x0 + 30, y0 + 52, 0xCC000000 | VERDE_OSCURO);
        // líneas de barrido
        for (int y = y0 + 2; y < y0 + 50; y += 3) g.fill(x0 + 1, y, x0 + 29, y + 1, 0x14000000 | VERDE);
        int cx = x0 + 15, cy = y0 + 4;
        pieza(g, d, Parte.CABEZA, cx - 4, cy, 8, 8, t);
        pieza(g, d, Parte.TORSO, cx - 4, cy + 9, 8, 12, t);
        pieza(g, d, Parte.BRAZO_DER, cx - 9, cy + 9, 4, 12, t);   // a la izquierda de la pantalla: le miras de frente
        pieza(g, d, Parte.BRAZO_IZQ, cx + 5, cy + 9, 4, 12, t);
        pieza(g, d, Parte.PIERNA_DER, cx - 4, cy + 22, 4, 12, t);
        pieza(g, d, Parte.PIERNA_IZQ, cx, cy + 22, 4, 12, t);
        // aviso de "LISIADO"
        boolean lisiado = false;
        for (Parte p : Parte.V) if (d.lisiado(p)) lisiado = true;
        if (lisiado && ((int) (t / 10)) % 2 == 0) {
            Component txt = Component.translatable("miscelanea.heridas.hud.lisiado");
            g.pose().pushPose();
            g.pose().translate(cx, y0 + 44, 0);
            g.pose().scale(0.5F, 0.5F, 1F);
            g.drawString(mc.font, txt, -mc.font.width(txt) / 2, 0, 0xFF000000 | ROJO, false);
            g.pose().popPose();
        }
    }

    private static void pieza(GuiGraphics g, HeridasDatos d, Parte p, int x, int y, int w, int h, float t) {
        float n = d.dano(p);
        int col;
        if (d.vendada(p)) col = 0xF2EEE2;
        else if (n >= HeridasDatos.LISIADO) col = ((int) (t / 6)) % 2 == 0 ? ROJO : 0xB0201A;
        else if (n > 0.12F) col = AMBAR;
        else if (n > 0.02F) col = 0xB8FF6A;
        else col = VERDE;
        if (DESTELLO[p.ordinal()] > 0 && DESTELLO[p.ordinal()] % 4 < 2) col = 0xFFFFFF;
        g.fill(x, y, x + w, y + h, 0xE0000000 | col);
        // sombreado de un píxel abajo-derecha para que no quede plano
        g.fill(x + w - 1, y + 1, x + w, y + h, 0x50000000);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0x50000000);
        if (d.vendada(p)) {   // cruz roja de botiquín
            int mx = x + w / 2, my = y + h / 2;
            g.fill(mx - 1, my - 2, mx + 1, my + 2, 0xFFD02428);
            if (w > 4) g.fill(mx - 2, my - 1, mx + 2, my + 1, 0xFFD02428);
        }
    }

    // ------------------------------------------------------------------ registro (bus del mod)

    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void capas(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> r = e.getSkin(skin);
                if (r != null) r.addLayer(new HeridasLayer(r, "slim".equals(skin)));
            }
        }

        @SubscribeEvent
        public static void hud(RegisterGuiOverlaysEvent e) {
            e.registerBelowAll("heridas_vineta", HeridasCliente::vineta);
            e.registerAboveAll("heridas_silueta", HeridasCliente::silueta);
        }
    }
}

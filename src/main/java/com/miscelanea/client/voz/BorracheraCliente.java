package com.miscelanea.client.voz;

import com.miscelanea.Miscelanea;
import com.miscelanea.voz.VozRegistro;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.util.Random;

/**
 * Borrachera, lado del cliente (solo para el jugador local):
 * <ul>
 *   <li>La cámara se mece (balanceo y cabeceo suaves, varias ondas sumadas) y el campo de visión late.</li>
 *   <li>Los controles se van: al andar, un empuje lateral que cambia despacio (paseo aleatorio) y, de vez en
 *       cuando, un traspiés hacia delante.</li>
 *   <li>Visión doble: un shader de pantalla (shaders/post/borrachera_N.json) pinta la imagen dos veces desplazada y
 *       con un poco de desenfoque, con tres niveles según lo "cargado" que vaya; si el shader falla (tarjeta rara),
 *       capa de respaldo: velo cálido que late.</li>
 * </ul>
 * Lo demás de la borrachera (hipo, vómito, chat, zigzag de mobs) lo hace el servidor; la voz, {@code ProcLenguaTrapo}
 * en el cliente de quien escucha. Todo esto se escala con los ajustes de accesibilidad "Efectos de distorsión"
 * (cámara/shader) y "Efectos de FOV" (campo de visión).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class BorracheraCliente {
    private BorracheraCliente() {}

    private static final ResourceLocation[] POST = {
            new ResourceLocation(Miscelanea.MOD_ID, "shaders/post/borrachera_1.json"),
            new ResourceLocation(Miscelanea.MOD_ID, "shaders/post/borrachera_2.json"),
            new ResourceLocation(Miscelanea.MOD_ID, "shaders/post/borrachera_3.json")};
    private static final PostChain[] CADENAS = new PostChain[3];
    private static final int[][] TAM = new int[3][2];
    private static boolean shaderFallo, shaderEsteFotograma;

    private static final Random RNG = new Random();
    /** Intensidad 0..1 (sube y baja suave) y su valor del tick anterior, para interpolar. */
    private static float k, kAntes;
    private static int amp, tick;
    /** Deriva lateral de los controles (-1..1) y su objetivo; traspiés: ticks que quedan. */
    private static float deriva, derivaObjetivo;
    private static int traspies, proximoTraspies = 100;

    static float intensidad(float pt) {
        return Mth.lerp(pt, kAntes, k);
    }

    private static float distorsion() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options == null ? 1F : mc.options.screenEffectScale().get().floatValue();
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        kAntes = k;
        MobEffectInstance ef = p == null ? null : p.getEffect(VozRegistro.BORRACHERA.get());
        if (ef == null || mc.isPaused()) {
            if (ef == null) {
                k = Math.max(0F, k - 0.02F);
                if (k <= 0F) cerrarShaders();
            }
            return;
        }
        amp = Mth.clamp(ef.getAmplifier(), 0, 2);
        float objetivo = 0.7F + 0.15F * amp;
        k = Math.min(objetivo, k + 0.01F);
        tick++;
        // deriva de los controles: paseo aleatorio lento hacia un objetivo que cambia cada poco
        if (tick % 25 == 0 || Math.abs(deriva - derivaObjetivo) < 0.02F) derivaObjetivo = (RNG.nextFloat() * 2 - 1) * (0.45F + 0.2F * amp);
        deriva += (derivaObjetivo - deriva) * 0.04F;
        if (traspies > 0) traspies--;
        else if (--proximoTraspies <= 0) {
            traspies = 6 + RNG.nextInt(6);
            proximoTraspies = 80 + RNG.nextInt(140) / (amp + 1);
        }
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        k = kAntes = 0;
        cerrarShaders();
        shaderFallo = false;
    }

    // ------------------------------------------------------------------ controles
    @SubscribeEvent
    public static void movimiento(MovementInputUpdateEvent e) {
        if (k < 0.05F) return;
        Player p = e.getEntity();
        var in = e.getInput();
        boolean andando = Math.abs(in.forwardImpulse) > 0.01F || Math.abs(in.leftImpulse) > 0.01F;
        if (traspies > 0 && p.onGround()) {
            // traspiés: un empujón hacia delante que no se controla
            in.forwardImpulse = Math.max(in.forwardImpulse, 0.95F);
            in.leftImpulse += deriva * 0.5F;
        } else if (andando) {
            in.leftImpulse += deriva * k;
        }
        in.leftImpulse = Mth.clamp(in.leftImpulse, -1F, 1F);
    }

    // ------------------------------------------------------------------ cámara
    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        float kk = intensidad((float) e.getPartialTick()) * distorsion();
        if (kk <= 0.001F) return;
        float t = tick + (float) e.getPartialTick();
        float f = 1F + 0.25F * amp;
        e.setRoll(e.getRoll() + (Mth.sin(t * 0.052F * f) * 5.5F + Mth.sin(t * 0.137F * f + 0.8F) * 1.6F) * kk);
        e.setPitch(e.getPitch() + (Mth.sin(t * 0.041F * f + 2.1F) * 2.2F + Mth.sin(t * 0.19F * f) * 0.5F) * kk);
        e.setYaw(e.getYaw() + Mth.sin(t * 0.033F * f + 1.1F) * 1.8F * kk);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        Minecraft mc = Minecraft.getInstance();
        float escala = mc.options == null ? 1F : mc.options.fovEffectScale().get().floatValue();
        float kk = intensidad((float) e.getPartialTick()) * escala;
        if (kk <= 0.001F) return;
        float t = tick + (float) e.getPartialTick();
        e.setFOV(e.getFOV() * (1.0 + 0.06 * Mth.sin(t * 0.07F) * kk));
    }

    // ------------------------------------------------------------------ visión doble (shader antes del HUD, como los de vanilla)
    @SubscribeEvent
    public static void antesHud(RenderGuiEvent.Pre e) {
        shaderEsteFotograma = false;
        float kk = intensidad(e.getPartialTick());
        float dist = distorsion();
        if (kk < 0.05F || shaderFallo || dist <= 0.01F) return;
        Minecraft mc = Minecraft.getInstance();
        int nivel = kk < 0.45F ? 0 : kk < 0.8F ? 1 : 2;
        nivel = Math.min(nivel, dist < 0.4F ? 0 : dist < 0.75F ? 1 : 2);
        try {
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            PostChain c = CADENAS[nivel];
            if (c == null) {
                c = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), POST[nivel]);
                c.resize(w, h);
                CADENAS[nivel] = c;
                TAM[nivel][0] = w;
                TAM[nivel][1] = h;
            } else if (TAM[nivel][0] != w || TAM[nivel][1] != h) {
                c.resize(w, h);
                TAM[nivel][0] = w;
                TAM[nivel][1] = h;
            }
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), prof = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            c.process(e.getPartialTick());
            mc.getMainRenderTarget().bindWrite(true);
            if (blend) RenderSystem.enableBlend();
            if (prof) RenderSystem.enableDepthTest();
            shaderEsteFotograma = true;
        } catch (Exception ex) {
            shaderFallo = true;
            cerrarShaders();
            mc.getMainRenderTarget().bindWrite(true);
            Miscelanea.LOGGER.warn("[miscelanea] No se pudo usar el shader de la borrachera; se usa la capa de respaldo", ex);
        }
    }

    private static void cerrarShaders() {
        for (int i = 0; i < CADENAS.length; i++) {
            if (CADENAS[i] != null) {
                try {
                    CADENAS[i].close();
                } catch (Exception ignored) {
                    // ya estaba cerrado
                }
                CADENAS[i] = null;
            }
        }
    }

    /** Capa de respaldo (si el shader no va): velo cálido que late con el mareo. */
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        float kk = intensidad(pt) * distorsion();
        if (kk <= 0.01F || shaderEsteFotograma) return;
        float t = tick + pt;
        float a = (0.10F + 0.06F * Mth.sin(t * 0.07F)) * kk;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fill(0, 0, w, h, ((int) (a * 255) << 24) | 0xD8A050);
        RenderSystem.disableBlend();
    }
}

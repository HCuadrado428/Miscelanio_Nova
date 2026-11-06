package com.miscelanea.client.tortura.efectos;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.camara.Reloj;
import com.miscelanea.client.camara.Ruido;
import com.miscelanea.client.camara.Sacudida;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * La cámara de la toxina del miedo. Todo va con {@link Reloj} (segundos reales, parado en pausa) y ruido suave
 * ({@link Ruido}), así que no tiembla con los FPS ni se repite a ojos vista:
 * <ul>
 *   <li>DERIVA: la vista se va despacio de lado (alabeo), arriba y abajo, como mareado; encima, la RESPIRACIÓN (un
 *       cabeceo que se acelera con el pánico) y un respingo pequeño con cada LATIDO (y un pulso del campo de visión,
 *       "pum-pum").</li>
 *   <li>TENSIÓN (la pone {@link MiedoSusto} antes de un susto): la cámara se acerca despacio (el campo de visión se
 *       cierra), se inclina hacia un lado, deja de respirar (la deriva se calma) y los bordes se desenfocan.</li>
 *   <li>VÉRTIGO (dolly zoom, el de Vértigo / Tiburón): el campo de visión se abre mientras la cámara avanza hacia lo que
 *       te asusta (el warden que carga), así que eso se queda del mismo tamaño y el fondo se estira. Se mueve la cámara
 *       de verdad (con {@code CameraMixin}), sin atravesar paredes.</li>
 *   <li>GOLPE (el susto): sacudida con trauma ({@link #SACUDIDA}), la cabeza que se va hacia atrás, el campo de visión
 *       que da un tirón, un fogonazo rojo cortito (≤ 0,2 s), viñeta oscura y separación de colores en el shader.</li>
 *   <li>INCLINACIÓN (el mundo se tuerce y se estira) y PESADILLA (la imagen se invierte en rojo y negro, ~1 s).</li>
 * </ul>
 * Lo que mueve la cámara se escala con "Efectos de distorsión"; lo que cambia el campo de visión, con "Efectos de
 * campo de visión"; los fogonazos, con "Ocultar destellos de rayos".
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class MiedoCamara {
    private MiedoCamara() {}

    /** Sacudida de los sustos y de las pisadas del warden. */
    static final Sacudida SACUDIDA = new Sacudida(2.4F, 3.4F, 5.5F, 12F, 1.5F);

    private static double tUlt;
    // eventos (segundos de Reloj)
    private static double tLatido = -100, tGolpe = -100, tPesadilla = -100, tInclina = -100;
    private static float fuerzaGolpe, inclinaGrados, inclinaDur = 1F;
    // tensión y vértigo (suavizados en tiempo real)
    private static float tension, tensionObj, vertigo, vertigoObj, lado = 1F;
    private static double sujeto = 6.0;
    // respiración: fase integrada (cambia de ritmo sin saltar)
    private static double faseResp;

    // ------------------------------------------------------------------ órdenes (desde el resto del miedo)
    static void latido() {
        tLatido = Reloj.t();
    }

    /** El susto: 0..1. */
    static void golpe(float f) {
        tGolpe = Reloj.t();
        fuerzaGolpe = Mth.clamp(f, 0F, 1F);
    }

    static void pesadilla() {
        tPesadilla = Reloj.t();
    }

    /** El mundo se tuerce {@code grados} (con signo) durante {@code segundos}. */
    static void inclinar(float grados, float segundos) {
        tInclina = Reloj.t();
        inclinaGrados = grados;
        inclinaDur = Math.max(0.5F, segundos);
    }

    /** Tensión objetivo (0..1); el lado al que se inclina se elige al empezar a subir. */
    static void tension(float objetivo, float ladoSiEmpieza) {
        if (tensionObj <= 0.01F && objetivo > 0.01F) lado = ladoSiEmpieza;
        tensionObj = Mth.clamp(objetivo, 0F, 1F);
    }

    /** Vértigo objetivo (0..1) y la distancia a lo que se mira (lo que debe quedarse del mismo tamaño). */
    static void vertigo(float objetivo, double distanciaSujeto) {
        vertigoObj = Mth.clamp(objetivo, 0F, 1F);
        sujeto = Mth.clamp(distanciaSujeto, 1.5, 12.0);
    }

    /** Se acabó el miedo: lo que dura se apaga suave; lo que es un golpe, termina solo. */
    static void relajar() {
        tensionObj = 0F;
        vertigoObj = 0F;
    }

    static void limpiar() {
        relajar();
        tension = vertigo = 0F;
        tGolpe = tPesadilla = tInclina = tLatido = -100;
        SACUDIDA.parar();
    }

    // ------------------------------------------------------------------ evolución
    private static void avanzar() {
        double ahora = Reloj.t();
        double dt = ahora - tUlt;
        tUlt = ahora;
        if (dt <= 0 || dt > 0.25) return;
        // la tensión sube despacio (se acerca) y se suelta deprisa; el vértigo igual
        float kt = (float) (1 - Math.exp(-dt * (tensionObj > tension ? 0.9 : 3.5)));
        tension += (tensionObj - tension) * kt;
        float kv = (float) (1 - Math.exp(-dt * (vertigoObj > vertigo ? 1.1 : 4.0)));
        vertigo += (vertigoObj - vertigo) * kv;
        // respiración: 0,22 Hz en calma, hasta 0,6 Hz con pánico (y casi parada aguantando la respiración)
        float panico = Math.max(MiedoSusto.panico() ? 1F : 0F, MiedoWarden.cargando() ? 0.8F : 0F);
        double hz = Mth.lerp(panico, 0.22F, 0.6F) * (1F - 0.75F * tension);
        faseResp += dt * hz;
    }

    private static float desde(double t0) {
        return (float) (Reloj.t() - t0);
    }

    /** Pulso del latido, "pum-pum" (0..1). */
    static float pulsoLatido() {
        float d = desde(tLatido);
        if (d < 0 || d > 1.2F) return 0F;
        float p = (float) Math.exp(-d * 10.0);
        if (d > 0.2F) p += 0.6F * (float) Math.exp(-(d - 0.2F) * 10.0);
        return p;
    }

    /** Lo que queda del golpe (separación de colores, viñeta): decae en ~0,7 s. */
    static float golpeShader() {
        float d = desde(tGolpe);
        if (d < 0 || d > 1.5F) return 0F;
        return fuerzaGolpe * (float) Math.exp(-d * 4.2);
    }

    /** El fogonazo rojo: cortito (≤ 0,2 s visibles). */
    private static float fogonazo() {
        float d = desde(tGolpe);
        if (d < 0 || d > 0.4F) return 0F;
        return fuerzaGolpe * (float) Math.exp(-d * 16.0);
    }

    /** Tirón del campo de visión con el susto: se cierra en ~40 ms y vuelve. */
    private static float punetazo() {
        float d = desde(tGolpe);
        if (d < 0 || d > 1F) return 0F;
        return fuerzaGolpe * (float) ((1 - Math.exp(-d * 45.0)) * Math.exp(-d * 6.5));
    }

    static float pesadillaShader() {
        float d = desde(tPesadilla);
        if (d < 0 || d > 1.2F) return 0F;
        float e = d < 0.12F ? d / 0.12F : d < 0.45F ? 1F : 1F - (d - 0.45F) / 0.75F;
        return Mth.clamp(e * e * (3F - 2F * e), 0F, 1F);
    }

    /** Lo torcido que está el mundo (0..1, con el signo de la inclinación). */
    private static float inclinacion() {
        float d = desde(tInclina);
        if (d < 0 || d > inclinaDur) return 0F;
        float s = d / inclinaDur;
        float e = s < 0.35F ? s / 0.35F : s < 0.6F ? 1F : 1F - (s - 0.6F) / 0.4F;
        return e * e * (3F - 2F * e);
    }

    static float estiraShader() {
        avanzar();
        return Math.signum(inclinaGrados) * inclinacion() * 0.9F + vertigo * 0.35F;
    }

    static float desenfoqueShader() {
        avanzar();
        return Mth.clamp(Math.max(tension * 0.9F, Math.max(vertigo * 0.55F, golpeShader() * 0.6F)), 0F, 1F);
    }

    static float tension() {
        avanzar();
        return tension;
    }

    /** ¿Hace falta el shader aunque la intensidad sea baja? */
    static boolean necesitaShader() {
        return golpeShader() > 0.01F || pesadillaShader() > 0.01F || tension > 0.02F || vertigo > 0.02F || inclinacion() > 0.01F;
    }

    // ------------------------------------------------------------------ ángulos
    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        avanzar();
        float dist = Sacudida.escalaPantalla();
        float kk = MiedoCliente.intensidad((float) e.getPartialTick()) * dist;
        double t = Reloj.t();
        if (kk > 0.001F) {
            // deriva lenta (se calma aguantando la respiración)
            float calma = 1F - 0.65F * tension;
            float roll = Ruido.fbm(1101, t * 0.13, 3) * 5.0F * calma;
            float pitch = Ruido.fbm(1102, t * 0.11, 2) * 1.7F * calma;
            float yaw = Ruido.fbm(1103, t * 0.09, 2) * 2.3F * calma;
            // respiración y latido
            float resp = (float) Math.sin(faseResp * Math.PI * 2);
            pitch += resp * 0.65F + pulsoLatido() * 0.35F;
            roll += resp * 0.25F;
            e.setRoll(e.getRoll() + roll * kk);
            e.setPitch(e.getPitch() + pitch * kk);
            e.setYaw(e.getYaw() + yaw * kk);
        }
        // tensión (plano holandés) e inclinación: escalan con la distorsión, no con la intensidad
        e.setRoll(e.getRoll() + (tension * 5.5F * lado + inclinacion() * inclinaGrados) * dist);
        SACUDIDA.aplicar(e, dist);
    }

    // ------------------------------------------------------------------ campo de visión
    private static float multVertigo() {
        return 1F + 0.34F * vertigo * Sacudida.escalaFov();
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        // solo el del mundo: la mano no se deforma
        if (!e.usedConfiguredFov()) return;
        avanzar();
        float esc = Sacudida.escalaFov();
        if (esc <= 0F) return;
        float kk = MiedoCliente.intensidad((float) e.getPartialTick()) * esc;
        double t = Reloj.t();
        double m = 1.0;
        if (kk > 0.001F) m *= 1.0 + (0.045 * Ruido.fbm(1104, t * 0.12, 2) + 0.03 * pulsoLatido()) * kk;
        m *= 1.0 - 0.11 * tension * esc;
        m *= multVertigo();
        m *= 1.0 - 0.13 * punetazo() * esc;
        if (m != 1.0) e.setFOV(e.getFOV() * m);
    }

    /**
     * El dolly del vértigo: la cámara avanza lo justo para que lo que está a {@link #sujeto} bloques no cambie de tamaño
     * al abrirse el campo de visión. Lo llama {@code CameraMixin} (primera persona). Null si no hay que moverla.
     */
    @Nullable
    public static Vec3 dolly(BlockGetter nivel, Camera c) {
        avanzar();
        if (vertigo < 0.003F || Sacudida.escalaFov() <= 0F || Sacudida.escalaPantalla() <= 0F) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        double f0 = (mc.options == null ? 70 : mc.options.fov().get()) * Mth.DEG_TO_RAD;
        double f1 = Math.min(f0 * multVertigo(), 160 * Mth.DEG_TO_RAD);
        double avance = sujeto * (1.0 - Math.tan(f0 / 2) / Math.tan(f1 / 2));
        avance = Math.min(avance, Math.min(1.8, sujeto - 1.0));
        if (avance <= 0.01) return null;
        Vector3f mira = c.getLookVector();
        Vec3 desde = c.getPosition();
        Vec3 dir = new Vec3(mira.x(), mira.y(), mira.z());
        // sin atravesar nada
        BlockHitResult bh = nivel.clip(new ClipContext(desde, desde.add(dir.scale(avance + 0.4)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                mc.player));
        if (bh.getType() != HitResult.Type.MISS) avance = Math.min(avance, bh.getLocation().distanceTo(desde) - 0.4);
        if (avance <= 0.01) return null;
        return desde.add(dir.scale(avance));
    }

    // ------------------------------------------------------------------ HUD (encima de todo)
    /** Cuánto rojo hay ahora en pantalla (fogonazo y viñeta del golpe), para apartar el verde. */
    static float rojo() {
        return Math.max(fogonazo(), golpeShader());
    }

    static boolean hayHud() {
        return fogonazo() > 0.003F || golpeShader() > 0.01F || pesadillaShader() > 0.01F;
    }

    static void hud(GuiGraphics g, int w, int h, float pt, boolean conShader) {
        float f = fogonazo() * 0.5F * MiedoCliente.escalaDestello();
        if (f > 0.003F) g.fill(0, 0, w, h, ((int) (f * 255) << 24) | 0x9A0000);
        // viñeta oscura del golpe (dura más que el fogonazo)
        float v = golpeShader();
        if (v > 0.01F) {
            RenderSystem.enableBlend();
            g.setColor(0.25F, 0.0F, 0.0F, Math.min(1F, v * 1.1F));
            g.blit(MiedoCliente.VINETA, 0, 0, 0, 0, w, h, w, h);
            g.setColor(1F, 1F, 1F, 1F);
        }
        // sin shader, la pesadilla es un velo rojo oscuro
        float p = pesadillaShader();
        if (!conShader && p > 0.01F) g.fill(0, 0, w, h, ((int) (p * 0.55F * 255) << 24) | 0x3A0000);
    }
}

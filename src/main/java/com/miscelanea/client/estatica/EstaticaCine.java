package com.miscelanea.client.estatica;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.muertes.Pista;
import com.miscelanea.estatica.EstaticaRed;
import com.miscelanea.estatica.EstaticaRegistro;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La transformación en estática (no mata): fotogramas clave de {@link EstaticaDatos} (generados con
 * scratchpad/gen_estatica.py), el cuerpo para todos ({@link AnimEstatica}) y, para quien bebe, la cámara de frente en
 * tercera persona, sin control ni HUD, con la pantalla llenándose de nieve y líneas de VHS hasta el estallido. Si la
 * cara se vuelve estática por la izquierda, la animación va en espejo (la mano que se lleva a la cara es la de ese lado).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class EstaticaCine {
    private EstaticaCine() {}

    static final String[] PARTES = {"pos", "body", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg"};
    private static final float[] RUIDO = {0F, 0F, 0.5F, 0.6F, 1F, 1F, 0.45F, 0.45F};
    private static final Map<String, Pista> PISTAS = new HashMap<>();

    static {
        for (int i = 0; i < EstaticaDatos.N.length; i++) PISTAS.put(EstaticaDatos.N[i], new Pista(EstaticaDatos.K[i]));
    }

    public static final class Activa {
        final long inicio;
        final Vec3 foco, frente, derecha;
        final boolean espejo;
        final int semilla;
        int hecho = -1;

        Activa(long inicio, Vec3 foco, float yaw, int semilla) {
            this.inicio = inicio;
            this.foco = foco;
            this.semilla = semilla;
            this.espejo = Mascara.lado(semilla) < 0;
            float r = yaw * Mth.DEG_TO_RAD;
            this.frente = new Vec3(-Mth.sin(r), 0, Mth.cos(r));
            this.derecha = new Vec3(-Mth.cos(r), 0, -Mth.sin(r));
        }

        Vec3 punto(double der, double arriba, double delante) {
            return foco.add(derecha.scale(der)).add(frente.scale(delante)).add(0, arriba, 0);
        }
    }

    private static final Map<Integer, Activa> ACT = new HashMap<>();
    @Nullable private static CameraType camaraPrevia;
    private static long destelloHasta;

    // ------------------------------------------------------------------ estado
    static void empezar(EstaticaRed.Cine p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ACT.put(p.id, new Activa(mc.level.getGameTime() - p.transcurrido, new Vec3(p.x, p.y, p.z), p.yaw, p.semilla));
        if (mc.player != null && p.id == mc.player.getId()) {
            if (camaraPrevia == null) camaraPrevia = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if (mc.screen instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
        }
    }

    @Nullable
    public static Activa de(int id) {
        return ACT.isEmpty() ? null : ACT.get(id);
    }

    public static boolean activa(int id) {
        return de(id) != null;
    }

    public static boolean localActiva() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && activa(mc.player.getId());
    }

    public static float tiempo(Activa a, float pt) {
        var l = Minecraft.getInstance().level;
        return l == null ? 0F : Math.max(0F, l.getGameTime() - a.inicio + pt);
    }

    public static float x(String n, float t, float def) {
        Pista p = PISTAS.get(n);
        return p == null ? def : p.x(t);
    }

    /** Cobertura de estática (0..1) y blancura de los ojos de alguien en plena transformación (o NaN si no lo está). */
    public static float cobertura(int id, float pt) {
        Activa a = de(id);
        return a == null ? Float.NaN : Mth.clamp(x("cobertura", tiempo(a, pt), 1F), 0F, 1F);
    }

    public static float ojos(int id, float pt) {
        Activa a = de(id);
        return a == null ? Float.NaN : Mth.clamp(x("ojos", tiempo(a, pt), 1F), 0F, 1F);
    }

    /** Valor de una pista de HUD para el jugador local (0 si no está en la cinemática). */
    static float hud(String n, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Activa a = mc.player == null ? null : de(mc.player.getId());
        return a == null ? 0F : Mth.clamp(x(n, tiempo(a, pt), 0F), 0F, 1F);
    }

    static float destello(float pt) {
        var l = Minecraft.getInstance().level;
        if (l == null) return 0F;
        float k = (destelloHasta - l.getGameTime() - pt) / 10F;
        return Mth.clamp(k, 0F, 1F);
    }

    private static void restaurar() {
        Minecraft mc = Minecraft.getInstance();
        if (camaraPrevia != null) {
            mc.options.setCameraType(camaraPrevia);
            camaraPrevia = null;
        }
    }

    // ------------------------------------------------------------------ cuerpo (deltas con temblor y convulsión, en espejo si toca)
    private static int indice(String n) {
        for (int i = 0; i < PARTES.length; i++) if (PARTES[i].equals(n)) return i;
        return -1;
    }

    private static float[] crudo(int i, float t) {
        Pista p = PISTAS.get(PARTES[i]);
        float[] v = p == null ? new float[3] : p.v(t);
        float f = RUIDO[i];
        if (f > 0) {
            float tr = x("temblor", t, 0F), cv = x("convulsion", t, 0F);
            if (tr > 0) {
                float q = t * 4F;
                v[0] += Pista.ruido(q, i * 3 + 1) * tr * f;
                v[1] += Pista.ruido(q, i * 3 + 2) * 0.6F * tr * f;
                v[2] += Pista.ruido(q, i * 3 + 3) * tr * f;
            }
            if (cv > 0) {
                float g = cv * f * 0.45F;
                v[0] += Pista.sacudida(t, i * 3 + 1) * g;
                v[1] += Pista.sacudida(t, i * 3 + 2) * 0.5F * g;
                v[2] += Pista.sacudida(t, i * 3 + 3) * 0.7F * g;
            }
        }
        return v;
    }

    /** Deltas de una parte ("pos", "body", "head", "rightArm"...) en el tick t. */
    static float[] parte(Activa a, String n, float t) {
        int i = indice(n);
        if (i < 0) return new float[3];
        if (!a.espejo) return crudo(i, t);
        // espejo: derecha ↔ izquierda, y los giros en y/z (y la x de la posición) cambian de signo
        int j = switch (n) {
            case "rightArm" -> 5;
            case "leftArm" -> 4;
            case "rightLeg" -> 7;
            case "leftLeg" -> 6;
            default -> i;
        };
        float[] v = crudo(j, t);
        if (n.equals("pos")) return new float[]{-v[0], v[1], v[2]};
        return new float[]{v[0], -v[1], -v[2]};
    }

    /** Nombre de la pieza del otro lado (para el espejo). */
    private static String otroLado(String n) {
        if (n.startsWith("right")) return "left" + n.substring(5);
        if (n.startsWith("left")) return "right" + n.substring(4);
        return n;
    }

    /** Retoque de la pose con bendy-lib ("&lt;pieza&gt;Aj", "posAj"; ver Articulaciones), o ceros. */
    static float[] ajuste(Activa a, String n, float t) {
        Pista p = PISTAS.get((a.espejo ? otroLado(n) : n) + "Aj");
        if (p == null) return new float[3];
        float[] v = p.v(t);
        if (!a.espejo) return v;
        return n.equals("pos") ? new float[]{-v[0], v[1], v[2]} : new float[]{v[0], -v[1], -v[2]};
    }

    /** Flexión natural (rad) del codo/rodilla de la pieza ("&lt;pieza&gt;Dobla"). */
    static float dobla(Activa a, String n, float t) {
        return x((a.espejo ? otroLado(n) : n) + "Dobla", t, 0F);
    }

    // ------------------------------------------------------------------ ticks: sucesos con tiempo
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!ACT.isEmpty() || camaraPrevia != null) {
                ACT.clear();
                restaurar();
            }
            return;
        }
        if (mc.player != null && activa(mc.player.getId()) && mc.options.getCameraType() != CameraType.THIRD_PERSON_FRONT) {
            if (camaraPrevia == null) camaraPrevia = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if (e.phase != TickEvent.Phase.END || mc.isPaused() || ACT.isEmpty()) return;
        List<Integer> fuera = new ArrayList<>();
        for (Map.Entry<Integer, Activa> en : ACT.entrySet()) {
            Activa a = en.getValue();
            int ti = (int) tiempo(a, 0F);
            Entity ent = mc.level.getEntity(en.getKey());
            if (ent != null) for (int k = Math.max(a.hecho + 1, ti - 3); k <= ti; k++) suceso(mc, a, ent, k);
            a.hecho = ti;
            if (ti > EstaticaDatos.FIN || ent == null && ti > 20) fuera.add(en.getKey());
        }
        for (int id : fuera) {
            ACT.remove(id);
            if (mc.player != null && id == mc.player.getId()) restaurar();
        }
    }

    private static void suceso(Minecraft mc, Activa a, Entity ent, int k) {
        var l = mc.level;
        if (l == null || k < 0) return;
        RandomSource r = l.getRandom();
        boolean local = mc.player != null && ent == mc.player;
        Vec3 c = ent.position().add(0, 1.2, 0);
        // crujidos cada vez más seguidos y más fuertes
        if (k >= 8 && k < EstaticaDatos.ESTALLIDO) {
            float f = (k - 8F) / (EstaticaDatos.ESTALLIDO - 8F);
            int cada = Math.max(1, Math.round(9 - 7 * f));
            if (k % cada == 0)
                l.playLocalSound(c.x, c.y, c.z, EstaticaRegistro.CRUJIDO.get(), SoundSource.PLAYERS, 0.25F + 0.7F * f, 0.8F + r.nextFloat() * 0.5F + f * 0.3F, false);
        }
        if (k == 2 && local) mc.getSoundManager().play(SimpleSoundInstance.forUI(EstaticaRegistro.PROPIA.get(), 0.7F, 0.6F));
        if (k == EstaticaDatos.ESTALLIDO) {
            l.playLocalSound(c.x, c.y, c.z, EstaticaRegistro.ESTALLIDO.get(), SoundSource.PLAYERS, 1.2F, 0.9F, false);
            EfectosMundo.estallido(c, 1.8F);
            if (local) {
                destelloHasta = l.getGameTime() + 10;
                com.miscelanea.client.armas.TemblorCliente.empezar(1.2F, 12);
            } else if (mc.player != null && mc.player.distanceTo(ent) < 12) {
                com.miscelanea.client.armas.TemblorCliente.empezar(0.4F, 8);
            }
        }
        if (k == EstaticaDatos.FIN - 4 && local) mc.getSoundManager().play(SimpleSoundInstance.forUI(EstaticaRegistro.CRUJIDO.get(), 1.4F, 0.4F));
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        ACT.clear();
        restaurar();
    }

    @SubscribeEvent
    public static void reaparecer(ClientPlayerNetworkEvent.Clone e) {
        ACT.remove(e.getOldPlayer().getId());
        ACT.remove(e.getNewPlayer().getId());
        restaurar();
    }

    // ------------------------------------------------------------------ sin control ni HUD
    @SubscribeEvent
    public static void movimiento(MovementInputUpdateEvent e) {
        if (!localActiva()) return;
        Input in = e.getInput();
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interaccion(InputEvent.InteractionKeyMappingTriggered e) {
        if (!localActiva()) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void sinHud(RenderGuiOverlayEvent.Pre e) {
        if (localActiva() && !EstaticaClienteMod.HUD.equals(e.getOverlay().id())) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ cámara (CameraMixin, tercera persona de frente)
    @Nullable
    public static double[] colocarCamara(BlockGetter level, Entity entity, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player) return null;
        Activa a = de(entity.getId());
        if (a == null) return null;
        float t = Math.min(tiempo(a, pt), EstaticaDatos.FIN);
        Pista pc = PISTAS.get("cam"), pm = PISTAS.get("mira");
        float az = pc.en(t, 0) * Mth.DEG_TO_RAD * (a.espejo ? -1 : 1), el = pc.en(t, 1) * Mth.DEG_TO_RAD, dist = pc.en(t, 2);
        Vec3 centro = a.punto(pm.en(t, 0), pm.en(t, 1), pm.en(t, 2));
        Vec3 dir = a.frente.scale(Mth.cos(az)).add(a.derecha.scale(Mth.sin(az))).scale(Mth.cos(el)).add(0, Mth.sin(el), 0);
        Vec3 cam = centro.add(dir.scale(dist));
        Vec3 desde = centro.add(dir.scale(Math.min(0.35, dist * 0.5)));
        BlockHitResult hit = level.clip(new ClipContext(desde, cam, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
        if (hit.getType() != HitResult.Type.MISS) cam = desde.add(hit.getLocation().subtract(desde).scale(0.85));
        // sacudidas de la estática: la cámara tiembla a golpes con la convulsión
        float tr = x("convulsion", t, 0F) * 0.03F + x("ruido", t, 0F) * 0.02F;
        if (tr > 0) cam = cam.add(Pista.ruido(t * 7F, 3) * tr, Pista.ruido(t * 7F, 5) * tr, Pista.ruido(t * 7F, 7) * tr);
        Vec3 d = centro.subtract(cam);
        double hz = Math.sqrt(d.x * d.x + d.z * d.z);
        return new double[]{cam.x, cam.y, cam.z, Math.toDegrees(Math.atan2(-d.x, d.z)), -Math.toDegrees(Math.atan2(d.y, Math.max(1.0E-4, hz)))};
    }
}

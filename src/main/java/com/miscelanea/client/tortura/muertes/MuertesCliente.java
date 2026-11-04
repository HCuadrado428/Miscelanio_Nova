package com.miscelanea.client.tortura.muertes;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.muertes.Muertes;
import com.miscelanea.tortura.muertes.MuertePacket;
import com.miscelanea.tortura.muertes.TipoMuerte;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cliente de las cinemáticas de muerte (ver {@link Muertes} y {@link Cinematica}): el estado de cada víctima, los
 * sucesos con tiempo, la cámara de cine de la víctima (tercera persona, por fotogramas clave alrededor del aparato), la
 * víctima sin control ni HUD, los efectos de pantalla, el cuerpo de los mobs (humanoides: las mismas pistas que los
 * jugadores; el resto, el cuerpo entero y los temblores) y los canales que leen los dibujos de los aparatos.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class MuertesCliente {
    private MuertesCliente() {}

    private static final ResourceLocation VINETA = new ResourceLocation(Miscelanea.MOD_ID, "textures/heridas/vineta.png");
    /** Viñeta verde y negra del veneno (canal "veneno", latiendo con el canal "pulso"). */
    private static final ResourceLocation VINETA_VENENO = new ResourceLocation(Miscelanea.MOD_ID, "textures/heridas/vineta_veneno.png");
    /** Pistas del cuerpo, en el orden de las semillas del ruido (= scratchpad/muertes.py). */
    static final String[] PARTES = {"pos", "body", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg"};
    private static final float[] RUIDO = {0F, 0F, 0.5F, 0.6F, 1F, 1F, 0.45F, 0.45F};

    public static final class Activa {
        public final Cinematica def;
        final long inicio;
        public final Vec3 foco, aux;
        public final float yaw;
        final Vec3 frente, derecha;
        int hecho;

        Activa(Cinematica def, long inicio, Vec3 foco, float yaw, Vec3 aux, int hecho) {
            this.def = def;
            this.inicio = inicio;
            this.foco = foco;
            this.yaw = yaw;
            this.aux = aux;
            this.hecho = hecho;
            float r = yaw * Mth.DEG_TO_RAD;
            this.frente = new Vec3(-Mth.sin(r), 0, Mth.cos(r));
            this.derecha = new Vec3(-Mth.cos(r), 0, -Mth.sin(r));
        }

        /** Punto (derecha, arriba, delante) respecto al foco. */
        public Vec3 punto(double der, double arriba, double delante) {
            return foco.add(derecha.scale(der)).add(frente.scale(delante)).add(0, arriba, 0);
        }
    }

    private static final class Destello {
        final long inicio;
        final int dur, argb;

        Destello(long inicio, int dur, int argb) {
            this.inicio = inicio;
            this.dur = dur;
            this.argb = argb;
        }
    }

    private static final Map<Integer, Activa> ACT = new HashMap<>();
    private static final List<Destello> DESTELLOS = new ArrayList<>();
    private static final Set<Integer> EMPUJADOS = new HashSet<>();
    @Nullable private static CameraType camaraPrevia;
    @Nullable private static ClientLevel nivel;

    /** Desde el arranque del cliente (TorturaClienteMod): el código común pregunta por aquí. */
    public static void iniciar() {
        Muertes.cliente = MuertesCliente::activa;
    }

    // ------------------------------------------------------------------ estado
    public static void recibir(MuertePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (nivel != mc.level) {
            limpiarTodo();
            nivel = mc.level;
        }
        TipoMuerte tipo = TipoMuerte.de(p.tipo);
        Cinematica def = tipo == null ? null : Cinematicas.de(tipo);
        if (def == null) {
            quitar(p.entidad);
            return;
        }
        ACT.put(p.entidad, new Activa(def, mc.level.getGameTime() - p.transcurrido, new Vec3(p.fx, p.fy, p.fz), p.yaw,
                new Vec3(p.ax, p.ay, p.az), p.transcurrido - 1));
        if (mc.player != null && p.entidad == mc.player.getId()) empezarLocal(mc);
    }

    @Nullable
    public static Activa de(int id) {
        return ACT.isEmpty() ? null : ACT.get(id);
    }

    public static boolean activa(int id) {
        return de(id) != null;
    }

    @Nullable
    private static Activa local() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : de(mc.player.getId());
    }

    public static boolean localActiva() {
        return local() != null;
    }

    public static float tiempo(Activa a, float pt) {
        ClientLevel l = Minecraft.getInstance().level;
        return l == null ? 0F : Math.max(0F, l.getGameTime() - a.inicio + pt);
    }

    /** Valor de un canal de aparato ("puertas", "pendulo", "estira", "manivela") para esa víctima, o NaN si no toca. */
    public static float canal(int victima, String n, float pt) {
        if (victima < 0) return Float.NaN;
        Activa a = de(victima);
        if (a == null || !a.def.tiene(n)) return Float.NaN;
        return a.def.x(n, tiempo(a, pt), 0F);
    }

    /** ¿Se dibuja sin cabeza? (el collar ya ha reventado). */
    public static boolean sinCabeza(int id, float pt) {
        Activa a = de(id);
        return a != null && a.def.sinCabeza >= 0 && tiempo(a, pt) >= a.def.sinCabeza;
    }

    static void destello(int argb, int dur) {
        ClientLevel l = Minecraft.getInstance().level;
        if (l != null) DESTELLOS.add(new Destello(l.getGameTime(), Math.max(1, dur), argb));
    }

    private static boolean esLocal(int id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == id;
    }

    private static void quitar(int id) {
        ACT.remove(id);
        if (esLocal(id)) restaurar();
    }

    private static void limpiarTodo() {
        ACT.clear();
        DESTELLOS.clear();
        EMPUJADOS.clear();
        restaurar();
    }

    private static void empezarLocal(Minecraft mc) {
        if (camaraPrevia == null) camaraPrevia = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (mc.player != null && mc.screen instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
    }

    private static void restaurar() {
        Minecraft mc = Minecraft.getInstance();
        if (camaraPrevia != null) {
            mc.options.setCameraType(camaraPrevia);
            camaraPrevia = null;
        }
        DESTELLOS.clear();
    }

    // ------------------------------------------------------------------ ticks: sucesos y limpieza
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!ACT.isEmpty() || camaraPrevia != null) limpiarTodo();
            nivel = null;
            return;
        }
        if (mc.level != nivel) {
            limpiarTodo();
            nivel = mc.level;
        }
        Activa yo = local();
        if (yo != null && mc.options.getCameraType() != CameraType.THIRD_PERSON_FRONT) {
            if (camaraPrevia == null) camaraPrevia = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if (yo != null && mc.player != null) fijarGiro(mc.player, yo);
        if (e.phase != TickEvent.Phase.END || mc.isPaused() || ACT.isEmpty()) return;
        long ahora = mc.level.getGameTime();
        List<Integer> fuera = null;
        for (Map.Entry<Integer, Activa> en : ACT.entrySet()) {
            Activa a = en.getValue();
            float t = tiempo(a, 0F);
            int ti = (int) t;
            Entity ent = mc.level.getEntity(en.getKey());
            boolean borrar;
            if (ent == null) {
                borrar = t > a.def.fin + 5;
                a.hecho = ti;
            } else {
                if (ti > a.hecho) sucesos(mc, a, ent, Math.max(a.hecho + 1, ti - 3), ti);
                a.hecho = ti;
                // vivo bastante después de la hora de la muerte: ha reaparecido (mismo id) o se ha anulado
                borrar = t > a.def.muerte + 30 && ent.isAlive() && !(ent instanceof LivingEntity le && le.isDeadOrDying());
            }
            if (t > a.def.muerte + 20 * 60 * 5) borrar = true;
            if (borrar) {
                if (fuera == null) fuera = new ArrayList<>();
                fuera.add(en.getKey());
            }
        }
        if (fuera != null) for (int id : fuera) quitar(id);
        DESTELLOS.removeIf(d -> ahora - d.inicio > d.dur);
    }

    /** Las que mueren tumbadas (el veneno, sin aparato que la sujete): el cuerpo no gira con el ratón. */
    private static void fijarGiro(Player p, Activa a) {
        if (!a.def.yace || p.isPassenger()) return;
        p.setYRot(a.yaw);
        p.yRotO = a.yaw;
        p.setXRot(0F);
        p.xRotO = 0F;
        p.setYHeadRot(a.yaw);
        p.yHeadRotO = a.yaw;
        p.yBodyRot = a.yaw;
        p.yBodyRotO = a.yaw;
    }

    @SubscribeEvent
    public static void fotograma(TickEvent.RenderTickEvent e) {
        // después de que el ratón gire al jugador y antes de dibujar
        if (e.phase != TickEvent.Phase.START || ACT.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Activa yo = local();
        if (yo != null && mc.player != null) fijarGiro(mc.player, yo);
    }

    private static void sucesos(Minecraft mc, Activa a, Entity ent, int desde, int hasta) {
        Cinematica.Ctx c = new Cinematica.Ctx(mc, mc.level, ent instanceof LivingEntity le ? le : null, a, esLocal(ent.getId()));
        for (int k = desde; k <= hasta; k++) {
            if (k < 0) continue;
            c.tick = k;
            for (Cinematica.Suceso s : a.def.sucesos) {
                if (s.tick() != k) continue;
                c.k = 0F;
                s.a().hacer(c);
            }
            for (Cinematica.Emisor em : a.def.emisores) {
                if (k < em.desde() || k > em.hasta() || (k - em.desde()) % em.cada() != 0) continue;
                c.k = em.hasta() > em.desde() ? (k - em.desde()) / (float) (em.hasta() - em.desde()) : 1F;
                em.a().hacer(c);
            }
        }
    }

    @SubscribeEvent
    public static void reaparecer(ClientPlayerNetworkEvent.Clone e) {
        quitar(e.getNewPlayer().getId());
        quitar(e.getOldPlayer().getId());
        restaurar();
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        limpiarTodo();
        nivel = null;
    }

    // ------------------------------------------------------------------ sin control
    @SubscribeEvent
    public static void movimiento(MovementInputUpdateEvent e) {
        if (local() == null) return;
        Input in = e.getInput();
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interaccion(InputEvent.InteractionKeyMappingTriggered e) {
        if (local() == null) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }

    // ------------------------------------------------------------------ cámara (CameraMixin, tercera persona de frente)
    /** Posición y giro de la cámara {x, y, z, yaw, pitch}, o null si no toca. */
    @Nullable
    public static double[] colocarCamara(BlockGetter level, Entity entity, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player) return null;
        Activa a = de(entity.getId());
        if (a == null) return null;
        float t = Math.min(tiempo(a, pt), a.def.fin);
        Pista pc = a.def.pista("cam");
        if (pc == null) return null;
        float az = pc.en(t, 0) * Mth.DEG_TO_RAD, el = pc.en(t, 1) * Mth.DEG_TO_RAD, dist = pc.en(t, 2);
        Pista pm = a.def.pista("mira");
        Vec3 centro = pm == null ? a.foco : a.punto(pm.en(t, 0), pm.en(t, 1), pm.en(t, 2));
        float w = Mth.clamp(a.def.x("aux", t, 0F), 0F, 1F);
        if (w > 0) centro = centro.lerp(a.aux, w);
        Vec3 dir = a.frente.scale(Mth.cos(az)).add(a.derecha.scale(Mth.sin(az))).scale(Mth.cos(el)).add(0, Mth.sin(el), 0);
        Vec3 cam = centro.add(dir.scale(dist));
        // colisión: desde un poco fuera del centro (el propio aparato no cuenta) hasta la cámara
        double m = Math.min(a.def.margen, dist * 0.9);
        Vec3 desde = centro.add(dir.scale(m));
        BlockHitResult hit = level.clip(new ClipContext(desde, cam, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
        if (hit.getType() != HitResult.Type.MISS) cam = desde.add(hit.getLocation().subtract(desde).scale(0.85));
        Vec3 d = centro.subtract(cam);
        double hz = Math.sqrt(d.x * d.x + d.z * d.z);
        double camYaw = Math.toDegrees(Math.atan2(-d.x, d.z));
        double camPitch = -Math.toDegrees(Math.atan2(d.y, Math.max(1.0E-4, hz)));
        return new double[]{cam.x, cam.y, cam.z, camYaw, camPitch};
    }

    // ------------------------------------------------------------------ cuerpo
    /** Deltas de la pieza i de {@link #PARTES} en el tick t, con el temblor y la convulsión (= muertes.py). */
    static float[] parte(Activa a, int i, float t) {
        Pista p = a.def.pista(PARTES[i]);
        float[] v = p == null ? new float[3] : p.v(t);
        float f = RUIDO[i];
        if (f > 0) {
            float tr = a.def.x("temblor", t, 0F), cv = a.def.x("convulsion", t, 0F);
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

    /** Desplazamiento (píxeles) del pivote de una pieza ("headPos", "rightArmPos"...), o null. */
    @Nullable
    static float[] posicion(Activa a, String n, float t) {
        Pista p = a.def.pista(n);
        return p == null ? null : p.v(t);
    }

    /** Lo que pesa la animación en un mob: entra en 3 ticks y se apaga al morir. */
    private static float pesoMob(LivingEntity e, Activa a, float pt) {
        float k = Math.min(1F, tiempo(a, pt) / 3F);
        if (e.deathTime > 0) k *= Math.max(0F, 1F - (e.deathTime + pt) / 10F);
        return k;
    }

    private static void sumar(ModelPart m, float[] r, float k) {
        m.xRot += r[0] * k;
        m.yRot += r[1] * k;
        m.zRot += r[2] * k;
    }

    private static void mover(ModelPart m, @Nullable float[] p, float k) {
        if (p == null) return;
        m.x += p[0] * k;
        m.y += p[1] * k;
        m.z += p[2] * k;
    }

    /** Mobs humanoides (LivingEntityRendererMixin, después de todas sus animaciones). Los jugadores usan {@link AnimMuerte}. */
    public static void posar(EntityModel<?> m, LivingEntity e, float pt) {
        if (ACT.isEmpty() || e instanceof Player || !(m instanceof HumanoidModel<?> hm)) return;
        Activa a = ACT.get(e.getId());
        if (a == null) return;
        float t = tiempo(a, pt), k = pesoMob(e, a, pt);
        if (k <= 0) return;
        sumar(hm.body, parte(a, 2, t), k);
        sumar(hm.head, parte(a, 3, t), k);
        sumar(hm.rightArm, parte(a, 4, t), k);
        sumar(hm.leftArm, parte(a, 5, t), k);
        sumar(hm.rightLeg, parte(a, 6, t), k);
        sumar(hm.leftLeg, parte(a, 7, t), k);
        mover(hm.head, posicion(a, "headPos", t), k);
        mover(hm.rightArm, posicion(a, "rightArmPos", t), k);
        mover(hm.leftArm, posicion(a, "leftArmPos", t), k);
        mover(hm.rightLeg, posicion(a, "rightLegPos", t), k);
        mover(hm.leftLeg, posicion(a, "leftLegPos", t), k);
        hm.hat.copyFrom(hm.head);
    }

    /** Mobs: el cuerpo entero (pos, body) y, los que no son humanoides, también los temblores y convulsiones. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        if (ACT.isEmpty()) return;
        LivingEntity le = e.getEntity();
        if (le instanceof Player) return;
        Activa a = ACT.get(le.getId());
        if (a == null) return;
        float pt = e.getPartialTick(), t = tiempo(a, pt), k = pesoMob(le, a, pt);
        if (k <= 0) return;
        float[] pos = parte(a, 0, t), rot = parte(a, 1, t);
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        EMPUJADOS.add(le.getId());
        float yaw = Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot);
        float r = yaw * Mth.DEG_TO_RAD;
        // (x a la derecha, z + = hacia atrás, como la posición del cuerpo de playerAnimator)
        double dx = -Mth.cos(r) * pos[0] + Mth.sin(r) * pos[2], dz = -Mth.sin(r) * pos[0] - Mth.cos(r) * pos[2];
        ps.translate(dx * k, pos[1] * k, dz * k);
        if (!(e.getRenderer().getModel() instanceof HumanoidModel<?>)) {
            float tr = a.def.x("temblor", t, 0F) + a.def.x("convulsion", t, 0F) * 0.6F, x = t;
            if (tr > 0) {
                ps.translate(Pista.ruido(x * 4F, 1) * tr * 0.5F * k, 0, Pista.ruido(x * 4F, 2) * tr * 0.5F * k);
                rot = new float[]{rot[0] + Pista.ruido(x * 4F, 3) * tr * 0.5F, rot[1] + Pista.ruido(x * 4F, 4) * tr, rot[2] + Pista.ruido(x * 4F, 5) * tr * 0.5F};
            }
        }
        float piv = le.getBbHeight() * 0.4F;
        ps.translate(0, piv, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.YP.rotation(-rot[1] * k));
        ps.mulPose(Axis.XP.rotation(-rot[0] * k));
        ps.mulPose(Axis.ZP.rotation(rot[2] * k));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(0, -piv, 0);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void despues(RenderLivingEvent.Post<?, ?> e) {
        if (!EMPUJADOS.isEmpty() && EMPUJADOS.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ pantalla
    private static final ResourceLocation NUESTRO = new ResourceLocation(Miscelanea.MOD_ID, "tortura_muerte");

    /** La víctima ve su muerte "en cine": sin HUD (ni barra, ni mira, ni corazones...), solo los efectos de la cinemática. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void sinHud(RenderGuiOverlayEvent.Pre e) {
        if (local() != null && !NUESTRO.equals(e.getOverlay().id())) e.setCanceled(true);
    }

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ACT.isEmpty()) return;
        Activa yo = local();
        // las luces parpadean también para los que están cerca (la silla eléctrica)
        float parpadeo = 0F;
        for (Map.Entry<Integer, Activa> en : ACT.entrySet()) {
            Activa a = en.getValue();
            if (!a.def.tiene("parpadeo")) continue;
            float p = a.def.x("parpadeo", tiempo(a, pt), 0F);
            if (a == yo) parpadeo = Math.max(parpadeo, p);
            else {
                double d = mc.player.position().distanceTo(a.foco);
                if (d < 16) parpadeo = Math.max(parpadeo, p * 0.55F * (float) (1 - d / 16));
            }
        }
        if (yo == null && parpadeo <= 0) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        if (yo != null) {
            float t = tiempo(yo, pt);
            float vin = Mth.clamp(yo.def.x("vineta", t, 0F), 0F, 1F);
            if (vin > 0) {
                g.setColor(1F, 1F, 1F, vin);
                g.blit(VINETA, 0, 0, 0, 0, w, h, w, h);
                g.setColor(1F, 1F, 1F, 1F);
            }
            float ven = Mth.clamp(yo.def.x("veneno", t, 0F), 0F, 1F);
            if (ven > 0) {
                // el veneno: viñeta verde y negra que late con el corazón y un velo verde por encima de todo
                float pu = Mth.clamp(yo.def.x("pulso", t, 0F), 0F, 1F);
                g.setColor(1F, 1F, 1F, Mth.clamp(ven * (0.72F + 0.28F * pu), 0F, 1F));
                g.blit(VINETA_VENENO, 0, 0, 0, 0, w, h, w, h);
                g.setColor(1F, 1F, 1F, 1F);
                int al = (int) Mth.clamp(ven * 0.16F * (1F + 0.5F * pu) * 255F, 0F, 255F);
                if (al > 0) g.fill(0, 0, w, h, (al << 24) | 0x2E5A1C);
            }
            float rojo = Mth.clamp(yo.def.x("rojo", t, 0F), 0F, 1F);
            if (rojo > 0) g.fill(0, 0, w, h, ((int) (rojo * 200) << 24) | 0x800000);
            float negro = Mth.clamp(yo.def.x("negro", t, 0F), 0F, 1F);
            if (negro > 0) g.fill(0, 0, w, h, ((int) (negro * 250) << 24) | 0x050000);
            long ahora = mc.level.getGameTime();
            for (Destello d : DESTELLOS) {
                float k = 1F - (ahora - d.inicio + pt) / d.dur;
                if (k <= 0) continue;
                int al = (int) (((d.argb >>> 24) & 0xFF) * Math.min(1F, k * 1.3F));
                if (al > 0) g.fill(0, 0, w, h, (al << 24) | (d.argb & 0xFFFFFF));
            }
        }
        if (parpadeo > 0) {
            // parpadeo de las luces: a ratos se va (negro) y a ratos un fogonazo blanco
            long q = mc.level.getGameTime() / 2;
            float n = (float) ((q * 2654435761L >>> 8) & 0xFFFF) / 65535F;
            if (n < parpadeo * 0.45F) g.fill(0, 0, w, h, ((int) (190 * parpadeo) << 24));
            else if (n > 1F - parpadeo * 0.1F) g.fill(0, 0, w, h, ((int) (90 * parpadeo) << 24) | 0xE8F0FF);
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Desenfoque de la pantalla de la víctima (canal "borroso", 0..1; 0 si no toca). Lo dibuja {@link Borroso}. */
    public static float borroso(float pt) {
        Activa yo = local();
        if (yo == null || !yo.def.tiene("borroso")) return 0F;
        return Mth.clamp(yo.def.x("borroso", tiempo(yo, pt), 0F), 0F, 1F);
    }

    /** Sin cinemáticas que dibujar (para quien quiera saberlo deprisa). */
    public static boolean vacio() {
        return ACT.isEmpty();
    }
}

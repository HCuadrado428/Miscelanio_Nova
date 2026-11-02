package com.miscelanea.client.tortura;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.EjecucionPacket;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.TrampaOso;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lado cliente de la ejecución de la trampa de oso (ver {@link TrampaOso}):
 * <ul>
 *   <li>0 .. {@link #ESTALLIDO} ticks: forcejeo (la animación la hace {@link AnimEjecucion} en los jugadores y
 *       {@link #posar} en los mobs humanoides; la trampa tiembla en {@link TorturaLayer}).</li>
 *   <li>{@link #ESTALLIDO}: las mandíbulas revientan: chorro de sangre (polvo rojo + trozos de bloque de redstone y de
 *       verruga del Nether), salpicaduras en los bloques de alrededor que se van borrando, y desde ahí la entidad se
 *       dibuja SIN CABEZA (cabeza, sombrero, casco y cabezas de mob ocultos) hasta que reaparece o desaparece el cuerpo.</li>
 *   <li>La víctima (jugador local): cámara en tercera persona de frente, más cerca y más baja; sin control (ni
 *       moverse, ni ratón, ni usar nada); viñeta roja con latidos cada vez más rápidos; destello rojo y negro al
 *       reventar. La cámara de antes se recupera al reaparecer (o al salir del mundo).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class EjecucionCliente {
    private EjecucionCliente() {}

    public static final int ESTALLIDO = TrampaOso.EJEC_ESTALLIDO, MUERTE = TrampaOso.EJEC_MUERTE;
    private static final ResourceLocation VINETA = new ResourceLocation(Miscelanea.MOD_ID, "textures/heridas/vineta.png");
    private static final ResourceLocation SALPICADURA = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/tortura/salpicadura.png");
    /** Cuánto baja el cuerpo del jugador al caer de rodillas (bloques; = AnimEjecucion). */
    public static final float BAJADA_RODILLAS = 0.42F;

    public static final class Ejec {
        /** Hora del nivel del cliente a la que empezó. */
        long inicio;
        boolean estallo;
    }

    private static final class Salpicadura {
        final Vec3 pos;
        final Direction cara;
        final BlockPos soporte;
        final float tam, giro;
        final int variante;
        final long nace;

        Salpicadura(Vec3 pos, Direction cara, BlockPos soporte, float tam, float giro, int variante, long nace) {
            this.pos = pos;
            this.cara = cara;
            this.soporte = soporte;
            this.tam = tam;
            this.giro = giro;
            this.variante = variante;
            this.nace = nace;
        }
    }

    private static final Map<Integer, Ejec> EJ = new HashMap<>();
    private static final List<Salpicadura> SALPICADURAS = new ArrayList<>();
    private static final int VIDA_SALPICADURA = 1200, MAX_SALPICADURAS = 220;
    /** Partes ocultas en este fotograma (para devolverlas en el Post): id -> {cabeza, sombrero}. */
    private static final Map<Integer, boolean[]> OCULTOS = new HashMap<>();
    private static final Set<Integer> TEMBLANDO = new HashSet<>();
    @Nullable
    private static CameraType camaraPrevia;
    private static long latidoProx, ultimoLatido = -1000;
    @Nullable
    private static ClientLevel nivel;

    // ------------------------------------------------------------------ estado
    public static void recibir(EjecucionPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (nivel != mc.level) {
            // llega antes del primer tick en este nivel: que el tick no lo borre por "nivel nuevo"
            limpiarTodo();
            nivel = mc.level;
        }
        if (!p.activa) {
            quitar(p.entidad);
            return;
        }
        Ejec e = new Ejec();
        e.inicio = mc.level.getGameTime() - p.transcurrido;
        // quien llega tarde no ve el estallido entero (pero sí la entidad sin cabeza)
        e.estallo = p.transcurrido > ESTALLIDO + 3;
        EJ.put(p.entidad, e);
        if (mc.player != null && p.entidad == mc.player.getId()) empezarLocal(mc);
    }

    @Nullable
    public static Ejec de(int id) {
        return EJ.isEmpty() ? null : EJ.get(id);
    }

    public static boolean activa(int id) {
        return de(id) != null;
    }

    /** Ticks (con fracción) desde que empezó. */
    public static float tiempo(Ejec e, float pt) {
        ClientLevel l = Minecraft.getInstance().level;
        return l == null ? 0F : Math.max(0F, l.getGameTime() - e.inicio + pt);
    }

    /** ¿Ya ha reventado (sin cabeza)? */
    public static boolean sinCabeza(int id, float pt) {
        Ejec e = de(id);
        return e != null && tiempo(e, pt) >= ESTALLIDO;
    }

    /** De 0 a 1: cuánto ha caído de rodillas (los jugadores). */
    public static float rodillas(float t) {
        float k = Mth.clamp((t - 38F) / 5F, 0F, 1F);   // = AnimEjecucion: cae de rodillas entre los ticks 38 y 43
        return k * k * (3F - 2F * k);
    }

    /** Cuánto ha bajado la cabeza de su sitio normal (bloques). */
    public static float bajada(Entity en, float t) {
        return en instanceof Player ? BAJADA_RODILLAS * rodillas(t) : 0F;
    }

    private static boolean esLocal(int id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == id;
    }

    @Nullable
    private static Ejec local() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : de(mc.player.getId());
    }

    private static void quitar(int id) {
        EJ.remove(id);
        if (esLocal(id)) restaurarCamara();
    }

    private static void limpiarTodo() {
        EJ.clear();
        SALPICADURAS.clear();
        OCULTOS.clear();
        TEMBLANDO.clear();
        restaurarCamara();
    }

    private static void empezarLocal(Minecraft mc) {
        if (camaraPrevia == null) camaraPrevia = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        latidoProx = 0;
        if (mc.player != null && mc.screen instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
    }

    private static void restaurarCamara() {
        if (camaraPrevia == null) return;
        Minecraft.getInstance().options.setCameraType(camaraPrevia);
        camaraPrevia = null;
    }

    // ------------------------------------------------------------------ ticks
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!EJ.isEmpty() || !SALPICADURAS.isEmpty() || camaraPrevia != null) limpiarTodo();
            nivel = null;
            return;
        }
        if (mc.level != nivel) {
            // otro nivel (dimensión, mundo): los ids ya no valen
            limpiarTodo();
            nivel = mc.level;
        }
        Ejec yo = local();
        // la cámara de frente no se puede cambiar mientras dure (F5 incluido)
        if (yo != null && mc.options.getCameraType() != CameraType.THIRD_PERSON_FRONT) {
            if (camaraPrevia == null) camaraPrevia = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if (e.phase != TickEvent.Phase.END || mc.isPaused()) return;
        long ahora = mc.level.getGameTime();
        List<Integer> fuera = null;
        for (Map.Entry<Integer, Ejec> en : EJ.entrySet()) {
            Ejec ej = en.getValue();
            float t = tiempo(ej, 0F);
            Entity ent = mc.level.getEntity(en.getKey());
            boolean borrar;
            if (ent == null) {
                // fuera de vista o el cuerpo ya ha desaparecido
                borrar = t > MUERTE + 5;
            } else {
                if (!ej.estallo && t >= ESTALLIDO) {
                    ej.estallo = true;
                    estallido(mc, ent, t);
                }
                if (ej.estallo && t < ESTALLIDO + 45) chorro(mc, ent, t);
                // vivo bastante después de la hora de la muerte: ha reaparecido (mismo id) o se ha anulado
                borrar = t > MUERTE + 30 && ent.isAlive() && !(ent instanceof LivingEntity le && le.isDeadOrDying());
            }
            if (t > MUERTE + 20 * 60 * 5) borrar = true;   // por si acaso
            if (borrar) {
                if (fuera == null) fuera = new ArrayList<>();
                fuera.add(en.getKey());
            }
        }
        if (fuera != null) for (int id : fuera) quitar(id);
        // latidos de la víctima, cada vez más seguidos
        yo = local();
        if (yo != null) {
            float t = tiempo(yo, 0F);
            if (t < ESTALLIDO && ahora >= latidoProx) {
                float k = t / ESTALLIDO;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(TorturaRegistro.TRAMPA_LATIDO.get(), 0.95F + k * 0.25F, 0.9F + k * 0.3F));
                ultimoLatido = ahora;
                latidoProx = ahora + Math.round(Mth.lerp(k, 15F, 4F));
            }
        }
        // salpicaduras viejas fuera
        if (!SALPICADURAS.isEmpty()) SALPICADURAS.removeIf(s -> ahora - s.nace > VIDA_SALPICADURA);
    }

    @SubscribeEvent
    public static void reaparecer(ClientPlayerNetworkEvent.Clone e) {
        // reaparecer o cambiar de dimensión: el jugador nuevo conserva el id
        quitar(e.getNewPlayer().getId());
        quitar(e.getOldPlayer().getId());
        restaurarCamara();
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

    /** Desde MouseHandlerMixin: true = el ratón no gira al jugador. */
    public static boolean raton() {
        return local() != null;
    }

    // ------------------------------------------------------------------ cámara (CameraMixin, tercera persona de frente)
    /** Posición y giro de la cámara {x, y, z, yaw, pitch}, o null si no toca. */
    @Nullable
    public static double[] colocarCamara(BlockGetter level, Entity entity, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player) return null;
        Ejec ej = de(entity.getId());
        if (ej == null) return null;
        float t = tiempo(ej, pt);
        float yaw = TorturaCliente.yawCuerpo(entity, pt);
        Vec3 frente = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
        Vec3 ojo = entity.getEyePosition(pt);
        double dist, alto;
        Vec3 objetivo;
        if (t < ESTALLIDO) {
            // se va acercando a la cara, desde un poco más abajo (mirando hacia arriba)
            float k = t / ESTALLIDO;
            objetivo = ojo.subtract(0, bajada(entity, t) + 0.05, 0);
            dist = Mth.lerp(k * k, 2.3F, 1.35F);
            alto = Mth.lerp(k, -0.2F, -0.3F);
        } else {
            // al reventar se aparta y mira al cuello y al cuerpo de rodillas
            float k = Mth.clamp((t - ESTALLIDO) / 10F, 0F, 1F);
            k = k * k * (3F - 2F * k);
            objetivo = ojo.subtract(0, bajada(entity, t) + 0.05 + 0.45 * k, 0);
            dist = Mth.lerp(k, 1.35F, 2.7F);
            alto = Mth.lerp(k, -0.3F, 0.55F);
        }
        Vec3 cam = objetivo.add(frente.scale(dist)).add(0, alto, 0);
        BlockHitResult hit = level.clip(new ClipContext(objetivo, cam, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
        if (hit.getType() != HitResult.Type.MISS) cam = objetivo.add(hit.getLocation().subtract(objetivo).scale(0.85));
        Vec3 d = objetivo.subtract(cam);
        double hz = Math.sqrt(d.x * d.x + d.z * d.z);
        double camYaw = Math.toDegrees(Math.atan2(-d.x, d.z));
        double camPitch = -Math.toDegrees(Math.atan2(d.y, Math.max(1.0E-4, hz)));
        return new double[]{cam.x, cam.y, cam.z, camYaw, camPitch};
    }

    // ------------------------------------------------------------------ sangre
    private static final DustParticleOptions SANGRE = new DustParticleOptions(new Vector3f(0.62F, 0.02F, 0.03F), 2.2F);
    private static final DustParticleOptions SANGRE_OSCURA = new DustParticleOptions(new Vector3f(0.30F, 0.0F, 0.01F), 1.5F);
    private static final BlockParticleOption TROZO_ROJO = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
    private static final BlockParticleOption TROZO_OSCURO = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState());

    private static Vec3 cuello(Entity en, float t) {
        return en.getEyePosition().subtract(0, bajada(en, t) + 0.22, 0);
    }

    @Nullable
    private static Particle particula(Minecraft mc, ParticleOptions o, Vec3 p, double vx, double vy, double vz) {
        Particle q = mc.particleEngine.createParticle(o, p.x, p.y, p.z, 0, 0, 0);
        if (q != null) q.setParticleSpeed(vx, vy, vz);
        return q;
    }

    private static void estallido(Minecraft mc, Entity en, float t) {
        RandomSource r = en.level().getRandom();
        Vec3 c = en.getEyePosition().subtract(0, bajada(en, t), 0);
        // nube de sangre que sale disparada de la cabeza
        for (int i = 0; i < 80; i++) {
            // (el polvo no cae y frena poco: velocidades pequeñas o se va a varios bloques)
            double ang = r.nextDouble() * Math.PI * 2, fuerza = 0.04 + r.nextDouble() * 0.14;
            particula(mc, i % 3 == 0 ? SANGRE_OSCURA : SANGRE, c.add(r.nextGaussian() * 0.12, r.nextGaussian() * 0.12, r.nextGaussian() * 0.12),
                    Math.cos(ang) * fuerza, 0.03 + r.nextDouble() * 0.12, Math.sin(ang) * fuerza);
        }
        // trozos que salen volando y caen al suelo
        for (int i = 0; i < 90; i++) {
            double ang = r.nextDouble() * Math.PI * 2, fuerza = 0.05 + r.nextDouble() * 0.32;
            particula(mc, i % 2 == 0 ? TROZO_ROJO : TROZO_OSCURO, c.add(r.nextGaussian() * 0.1, r.nextGaussian() * 0.1, r.nextGaussian() * 0.1),
                    Math.cos(ang) * fuerza, 0.1 + r.nextDouble() * 0.3, Math.sin(ang) * fuerza);
        }
        for (int i = 0; i < 10; i++)
            particula(mc, ParticleTypes.CRIT, c, r.nextGaussian() * 0.4, r.nextDouble() * 0.4, r.nextGaussian() * 0.4);
        // salpicaduras: rayos desde la cabeza, sobre todo hacia abajo
        long ahora = mc.level == null ? 0 : mc.level.getGameTime();
        salpicar(en, c, new Vec3(0, -4, 0), 1.05F + r.nextFloat() * 0.3F, ahora + 6, r);
        for (int i = 0; i < 18; i++) {
            Vec3 dir = new Vec3(r.nextGaussian(), -Math.abs(r.nextGaussian()) * 1.1 - 0.25, r.nextGaussian()).normalize()
                    .scale(2.2 + r.nextDouble() * 2.8);
            salpicar(en, c, dir, 0.25F + r.nextFloat() * 0.5F, ahora + 3 + (long) (dir.length() * 2.5), r);
        }
    }

    /** El cuello sigue echando sangre un rato, cada vez menos. */
    private static void chorro(Minecraft mc, Entity en, float t) {
        float k = 1F - (t - ESTALLIDO) / 45F;
        if (k <= 0) return;
        RandomSource r = en.level().getRandom();
        Vec3 c = cuello(en, t);
        int n = Math.round(1 + 3 * k);
        for (int i = 0; i < n; i++)
            particula(mc, r.nextBoolean() ? TROZO_ROJO : TROZO_OSCURO, c, r.nextGaussian() * 0.06 * k, 0.12 + r.nextDouble() * 0.22 * k,
                    r.nextGaussian() * 0.06 * k);
        if (r.nextFloat() < k)
            particula(mc, SANGRE, c.add(0, 0.1, 0), r.nextGaussian() * 0.05, 0.08 + r.nextDouble() * 0.1, r.nextGaussian() * 0.05);
    }

    private static void salpicar(Entity en, Vec3 desde, Vec3 dir, float tam, long nace, RandomSource r) {
        BlockHitResult hit = en.level().clip(new ClipContext(desde, desde.add(dir), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, en));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        Direction cara = hit.getDirection();
        Vec3 n = Vec3.atLowerCornerOf(cara.getNormal());
        if (SALPICADURAS.size() >= MAX_SALPICADURAS) SALPICADURAS.remove(0);
        SALPICADURAS.add(new Salpicadura(hit.getLocation().add(n.scale(0.004 + r.nextFloat() * 0.004)), cara, hit.getBlockPos(), tam,
                r.nextFloat() * Mth.TWO_PI, r.nextInt(4), nace));
    }

    @SubscribeEvent
    public static void dibujarSalpicaduras(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || SALPICADURAS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = e.getCamera().getPosition();
        long ahora = level.getGameTime();
        PoseStack ps = e.getPoseStack();
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        RenderType tipo = RenderType.entityTranslucent(SALPICADURA);   // (memorizado por el juego)
        VertexConsumer vc = buf.getBuffer(tipo);
        for (Salpicadura s : SALPICADURAS) {
            long edad = ahora - s.nace;
            if (edad < 0 || s.pos.distanceToSqr(cam) > 64 * 64) continue;
            if (level.getBlockState(s.soporte).isAir()) continue;   // el bloque ya no está
            float a = edad < VIDA_SALPICADURA - 400 ? 1F : (VIDA_SALPICADURA - edad) / 400F;
            float crece = Math.min(1F, 0.35F + edad / 6F);           // se extiende al caer
            if (a <= 0) continue;
            Vector3f n = new Vector3f(s.cara.getStepX(), s.cara.getStepY(), s.cara.getStepZ());
            Vector3f u = s.cara.getAxis() == Direction.Axis.X ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0);
            Vector3f v = new Vector3f(n).cross(u);
            float co = Mth.cos(s.giro), si = Mth.sin(s.giro), h = s.tam * 0.5F * crece;
            Vector3f uu = new Vector3f(u).mul(co).add(new Vector3f(v).mul(si)).mul(h);
            Vector3f vv = new Vector3f(v).mul(co).sub(new Vector3f(u).mul(si)).mul(h);
            float cx = (float) (s.pos.x - cam.x), cy = (float) (s.pos.y - cam.y), cz = (float) (s.pos.z - cam.z);
            int luz = LevelRenderer.getLightColor(level, s.soporte.relative(s.cara));
            float u0 = (s.variante % 2) * 0.5F, v0 = (s.variante / 2) * 0.5F;
            float[][] esq = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
            int alfa = Math.round(a * 235);
            for (float[] q : esq) {
                vc.vertex(m, cx + uu.x * q[0] + vv.x * q[1], cy + uu.y * q[0] + vv.y * q[1], cz + uu.z * q[0] + vv.z * q[1])
                        .color(255, 255, 255, alfa).uv(u0 + (q[0] + 1) * 0.25F, v0 + (q[1] + 1) * 0.25F)
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(nm, n.x, n.y, n.z).endVertex();
            }
        }
        buf.endBatch(tipo);
    }

    // ------------------------------------------------------------------ sin cabeza y temblores (todas las entidades)
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        if (EJ.isEmpty()) return;
        LivingEntity le = e.getEntity();
        Ejec ej = EJ.get(le.getId());
        if (ej == null) return;
        float t = tiempo(ej, e.getPartialTick());
        EntityModel<?> m = e.getRenderer().getModel();
        if (t >= ESTALLIDO) {
            if (m instanceof HumanoidModel<?> hm) {
                OCULTOS.put(le.getId(), new boolean[]{hm.head.visible, hm.hat.visible});
                hm.head.visible = false;
                hm.hat.visible = false;
            } else if (m instanceof HeadedModel hd) {
                OCULTOS.put(le.getId(), new boolean[]{hd.getHead().visible, false});
                hd.getHead().visible = false;
            }
        } else if (!(le instanceof Player)) {
            // los mobs forcejean temblando entero (los jugadores ya tienen su animación)
            float a = 0.5F + 0.5F * t / ESTALLIDO, x = le.tickCount + e.getPartialTick();
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            TEMBLANDO.add(le.getId());
            ps.translate(Mth.sin(x * 3.1F) * 0.04F * a, 0, Mth.sin(x * 2.3F + 1F) * 0.04F * a);
            ps.mulPose(Axis.YP.rotationDegrees(Mth.sin(x * 2.7F) * 6F * a));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void despues(RenderLivingEvent.Post<?, ?> e) {
        if (OCULTOS.isEmpty() && TEMBLANDO.isEmpty()) return;
        int id = e.getEntity().getId();
        boolean[] o = OCULTOS.remove(id);
        if (o != null) {
            EntityModel<?> m = e.getRenderer().getModel();
            if (m instanceof HumanoidModel<?> hm) {
                hm.head.visible = o[0];
                hm.hat.visible = o[1];
            } else if (m instanceof HeadedModel hd) {
                hd.getHead().visible = o[0];
            }
        }
        if (TEMBLANDO.remove(id)) e.getPoseStack().popPose();
    }

    /** Desde los mixins del casco y de las cabezas de mob: ¿no se dibuja nada en la cabeza de esta entidad? */
    public static boolean ocultarCabeza(LivingEntity e) {
        return !EJ.isEmpty() && sinCabeza(e.getId(), Minecraft.getInstance().getFrameTime());
    }

    /**
     * Mobs humanoides (LivingEntityRendererMixin, después de todas sus animaciones): forcejean con las manos en la
     * trampa y sacudiendo la cabeza; al reventar, los brazos caen. Los jugadores usan {@link AnimEjecucion}.
     */
    public static void posar(EntityModel<?> m, LivingEntity e, float pt) {
        if (EJ.isEmpty() || e instanceof Player) return;
        Ejec ej = EJ.get(e.getId());
        if (ej == null) return;
        float t = tiempo(ej, pt), x = e.tickCount + pt;
        if (m instanceof HumanoidModel<?> hm) {
            if (t < ESTALLIDO) {
                float a = 0.6F + 0.4F * t / ESTALLIDO;
                float tiron = Mth.square(Math.max(0F, Mth.sin(x * 1.3F)));
                hm.head.yRot = Mth.sin(x * 1.9F) * 0.5F * a;
                hm.head.zRot = Mth.sin(x * 3.7F + 2F) * 0.2F * a;
                hm.head.xRot = 0.2F + Mth.sin(x * 2.7F + 1F) * 0.15F * a;
                hm.hat.copyFrom(hm.head);
                hm.rightArm.xRot = -2.8F + Mth.sin(x * 2.7F + 1F) * 0.12F * a;
                hm.rightArm.yRot = 0F;
                hm.rightArm.zRot = -0.08F - 0.25F * tiron * a;
                hm.leftArm.xRot = -2.8F - Mth.sin(x * 2.7F + 1F) * 0.12F * a;
                hm.leftArm.yRot = 0F;
                hm.leftArm.zRot = 0.08F + 0.25F * tiron * a;
                hm.body.yRot = Mth.sin(x * 1.9F) * 0.2F * a;
            } else {
                float s = t - ESTALLIDO, sacudida = (float) Math.exp(-s * 0.4F);
                hm.rightArm.xRot = Mth.lerp(1F - sacudida, -2.8F, -0.25F);
                hm.leftArm.xRot = Mth.lerp(1F - sacudida, -2.8F, -0.3F);
                hm.rightArm.yRot = hm.leftArm.yRot = 0F;
                hm.rightArm.zRot = 0.1F + 0.5F * sacudida + Mth.sin(s * 7F) * 0.1F * sacudida;
                hm.leftArm.zRot = -0.1F - 0.5F * sacudida - Mth.sin(s * 7F + 1F) * 0.1F * sacudida;
                hm.body.yRot = 0F;
                // el muñón, alineado con el cuerpo
                hm.head.xRot = hm.body.xRot;
                hm.head.yRot = hm.body.yRot;
                hm.head.zRot = 0F;
                hm.hat.copyFrom(hm.head);
            }
        } else if (m instanceof HeadedModel hd && t < ESTALLIDO) {
            hd.getHead().yRot += Mth.sin(x * 1.9F) * 0.45F;
            hd.getHead().zRot += Mth.sin(x * 3.7F + 2F) * 0.2F;
        }
    }

    // ------------------------------------------------------------------ HUD de la víctima
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Ejec ej = local();
        if (ej == null || mc.level == null) return;
        float t = tiempo(ej, pt);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        if (t < ESTALLIDO) {
            // viñeta roja que late con el corazón
            float desde = mc.level.getGameTime() - ultimoLatido + pt;
            float latido = (float) Math.exp(-desde * 0.45F);
            float a = Mth.clamp(0.3F + 0.3F * t / ESTALLIDO + 0.45F * latido, 0F, 1F);
            g.setColor(1F, 1F, 1F, a);
            g.blit(VINETA, 0, 0, 0, 0, w, h, w, h);
            g.setColor(1F, 1F, 1F, 1F);
            if (latido > 0.5F) g.fill(0, 0, w, h, ((int) ((latido - 0.5F) * 70) << 24) | 0x600000);
        } else {
            float s = t - ESTALLIDO;
            g.setColor(1F, 1F, 1F, 0.95F);
            g.blit(VINETA, 0, 0, 0, 0, w, h, w, h);
            g.setColor(1F, 1F, 1F, 1F);
            if (s < 5F) {
                // destello rojo que se va a negro
                float k = s / 5F;
                g.fill(0, 0, w, h, ((int) (230 * (1F - k)) << 24) | 0xC00000);
                g.fill(0, 0, w, h, ((int) (235 * k) << 24));
            } else {
                // negro y, al rato, se aclara un poco para ver el cuerpo
                float k = Mth.clamp((s - 10F) / 30F, 0F, 1F);
                g.fill(0, 0, w, h, ((int) (235 - 145 * k) << 24) | 0x080000);
            }
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}

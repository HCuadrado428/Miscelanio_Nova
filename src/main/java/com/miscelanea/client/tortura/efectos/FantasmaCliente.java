package com.miscelanea.client.tortura.efectos;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import com.miscelanea.tortura.efectos.FantasmaPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Fantasma en el cliente. El servidor dice quién lo es ({@link FantasmaPacket}); aquí se decide, cada tick y para
 * cada fantasma, si asoma o no según DÓNDE cae en la pantalla del que mira:
 * <ul>
 *   <li>Se calcula el ángulo entre la mirada de la cámara y la dirección al fantasma, y el ángulo al que está el
 *       borde de la pantalla en esa misma dirección (con el FOV real de este fotograma y la proporción de la
 *       ventana). Solo asoma si el ángulo queda entre el 75 % y el 105 % de ese borde: el rabillo del ojo.</li>
 *   <li>Al mirarlo más de frente (o perderlo del todo) se desvanece en ~3 ticks con unas partículas de alma y
 *       ceniza donde estaba; al volver al borde reaparece con un soplo (como mucho uno cada 4 s por fantasma).</li>
 *   <li>Mientras se ve va medio transparente y pálido (ver {@code LivingEntityRendererMixin}): sin capas (armadura,
 *       objetos, flechas) y con el modelo al 80 % de opacidad por el fundido. Detrás de la cámara nunca se ve.</li>
 *   <li>Uno mismo se ve en tercera persona como un fantasma tenue (30 %), para saber que está activo; en el HUD, el
 *       icono del efecto latiendo abajo a la izquierda. Un espectador lo ve todo como siempre.</li>
 * </ul>
 * La sombra, el fuego y las partículas de poción las corta {@code EntityRenderDispatcherMixin} / {@code LivingEntityMixin}.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class FantasmaCliente {
    private FantasmaCliente() {}

    private static final ResourceLocation ICONO = new ResourceLocation(Miscelanea.MOD_ID, "textures/mob_effect/fantasma.png");

    /** Fracción del borde de la pantalla entre la que asoma (rabillo del ojo). */
    public static final float BORDE_INTERIOR = 0.75F, BORDE_EXTERIOR = 1.05F;
    /** Ticks del fundido y margen entre dos soplos del mismo fantasma. */
    private static final float PASO_FUNDIDO = 1F / 3F;
    private static final int MARGEN_SOPLO = 80, MARGEN_PARTICULAS = 20;
    /** Opacidad en el rabillo del ojo y la de uno mismo en tercera persona. */
    public static final float ALFA_RABILLO = 0.8F, ALFA_PROPIO = 0.3F;

    /** Estado de cada fantasma para quien mira (no para uno mismo). */
    public static final class Estado {
        float alfa, alfaAnt;
        boolean asoma;
        long ultimoSoplo = -MARGEN_SOPLO, ultimasParticulas = -MARGEN_PARTICULAS;
    }

    private static final Map<Integer, Estado> FANTASMAS = new HashMap<>();
    private static long reloj;
    private static ClientLevel nivel;
    /** FOV vertical (grados) del último fotograma del mundo, con todo lo que lo altere ya aplicado. */
    private static double fov = 70;

    // ------------------------------------------------------------------ red
    public static void recibir(FantasmaPacket p) {
        if (p.activo) FANTASMAS.computeIfAbsent(p.entidad, k -> new Estado());
        else {
            Estado e = FANTASMAS.remove(p.entidad);
            Minecraft mc = Minecraft.getInstance();
            if (e != null && e.alfa > 0 && mc.level != null && mc.level.getEntity(p.entidad) instanceof LivingEntity le) particulas(mc.level, le);
        }
    }

    // ------------------------------------------------------------------ consultas (las usan los mixins y los eventos)
    /** ¿Es fantasma? Uno mismo lo sabe por el efecto (se sincroniza); de los demás lo dice el servidor. */
    public static boolean esFantasma(Entity e) {
        if (e == null) return false;
        if (FANTASMAS.containsKey(e.getId())) return true;
        Minecraft mc = Minecraft.getInstance();
        return e == mc.player && e instanceof LivingEntity le && le.hasEffect(EfectosRegistro.FANTASMA.get());
    }

    /** Opacidad con que se dibuja ahora (1 = normal, 0 = no se dibuja). */
    public static float alfa(Entity e, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer yo = mc.player;
        if (yo == null || !esFantasma(e)) return 1F;
        if (yo.isSpectator()) return 1F;
        if (e == yo) return ALFA_PROPIO;
        Estado s = FANTASMAS.get(e.getId());
        if (s == null) return 1F;
        return ALFA_RABILLO * Mth.lerp(pt, s.alfaAnt, s.alfa);
    }

    /** Ni modelo, ni sombra, ni fuego, ni caja: del todo escondido ahora mismo. */
    public static boolean oculto(Entity e) {
        return alfa(e, 1F) <= 0.01F;
    }

    /** Si se dibuja distinto (translúcido y pálido, sin capas): fantasma visto por otro, o uno mismo en tercera persona. */
    public static boolean dibujoFantasma(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && !mc.player.isSpectator() && esFantasma(e);
    }

    // ------------------------------------------------------------------ tick: dónde cae cada fantasma en la pantalla
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov e) {
        if (e.usedConfiguredFov()) fov = e.getFOV();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer yo = mc.player;
        if (level == null || yo == null) {
            FANTASMAS.clear();
            nivel = null;
            return;
        }
        if (level != nivel) {
            // mundo nuevo (dimensión, reaparecer): los ids de entidad ya no valen; el servidor vuelve a avisar al verlos
            FANTASMAS.clear();
            nivel = level;
        }
        if (mc.isPaused()) return;
        reloj++;
        Camera cam = mc.gameRenderer.getMainCamera();
        for (Iterator<Map.Entry<Integer, Estado>> it = FANTASMAS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Estado> en = it.next();
            Estado s = en.getValue();
            s.alfaAnt = s.alfa;
            Entity ent = level.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) {
                // entidad que ya no está (descargada, muerta): se olvida a los pocos segundos
                if (reloj % 100 == 0) it.remove();
                continue;
            }
            if (le == yo) continue;
            boolean asoma = yo.isSpectator() || enRabillo(cam, le);
            if (asoma != s.asoma) {
                s.asoma = asoma;
                if (asoma) {
                    if (reloj - s.ultimoSoplo >= MARGEN_SOPLO) {
                        s.ultimoSoplo = reloj;
                        level.playLocalSound(le.getX(), le.getEyeY(), le.getZ(), EfectosRegistro.FANTASMA_ALIENTO.get(), SoundSource.PLAYERS,
                                0.35F, 0.85F + level.getRandom().nextFloat() * 0.3F, false);
                    }
                } else if (s.alfa > 0 && reloj - s.ultimasParticulas >= MARGEN_PARTICULAS) {
                    s.ultimasParticulas = reloj;
                    particulas(level, le);
                }
            }
            float meta = asoma ? 1F : 0F;
            s.alfa = meta > s.alfa ? Math.min(meta, s.alfa + PASO_FUNDIDO) : Math.max(meta, s.alfa - PASO_FUNDIDO);
        }
    }

    /**
     * ¿Está el fantasma en el borde del campo de visión de la cámara? Se mide el ángulo entre la mirada y la dirección
     * al centro del fantasma, y el ángulo al que queda el borde de la pantalla en esa dirección (el rectángulo de la
     * pantalla a distancia 1: medio FOV vertical arriba/abajo, y a los lados el que da la proporción de la ventana).
     */
    private static boolean enRabillo(Camera cam, LivingEntity le) {
        Vec3 d = le.getBoundingBox().getCenter().subtract(cam.getPosition());
        double dist = d.length();
        if (dist < 1.0E-3) return false;
        d = d.scale(1 / dist);
        Vector3f mira = cam.getLookVector(), arriba = cam.getUpVector(), izq = cam.getLeftVector();
        double z = d.x * mira.x() + d.y * mira.y() + d.z * mira.z();
        if (z <= 0) return false;      // detrás: nunca
        double x = -(d.x * izq.x() + d.y * izq.y() + d.z * izq.z());
        double y = d.x * arriba.x() + d.y * arriba.y() + d.z * arriba.z();
        double angulo = Math.toDegrees(Math.acos(Mth.clamp(z, -1, 1)));
        Minecraft mc = Minecraft.getInstance();
        double alto = Math.max(1, mc.getWindow().getHeight()), ancho = Math.max(1, mc.getWindow().getWidth());
        double medioV = Mth.clamp(fov, 10, 170) / 2;
        double tanV = Math.tan(Math.toRadians(medioV)), tanH = tanV * (ancho / alto);
        double r = Math.sqrt(x * x + y * y);
        double radioBorde;
        if (r < 1.0E-6) radioBorde = tanV;
        else {
            double cx = Math.abs(x) / r, cy = Math.abs(y) / r;
            radioBorde = Math.min(cx < 1.0E-6 ? Double.MAX_VALUE : tanH / cx, cy < 1.0E-6 ? Double.MAX_VALUE : tanV / cy);
        }
        double borde = Math.toDegrees(Math.atan(radioBorde));
        return angulo >= borde * BORDE_INTERIOR && angulo <= borde * BORDE_EXTERIOR;
    }

    /** Se desvanece: unas almas y cenizas donde estaba. Solo en este cliente. */
    private static void particulas(ClientLevel level, LivingEntity le) {
        RandomSource r = level.getRandom();
        AABB c = le.getBoundingBox();
        for (int i = 0; i < 5; i++) {
            double px = c.minX + r.nextDouble() * (c.maxX - c.minX), py = c.minY + r.nextDouble() * (c.maxY - c.minY),
                    pz = c.minZ + r.nextDouble() * (c.maxZ - c.minZ);
            level.addParticle(ParticleTypes.SOUL, px, py, pz, r.nextGaussian() * 0.01, 0.02 + r.nextDouble() * 0.03, r.nextGaussian() * 0.01);
        }
        for (int i = 0; i < 6; i++) {
            double px = c.minX + r.nextDouble() * (c.maxX - c.minX), py = c.minY + r.nextDouble() * (c.maxY - c.minY),
                    pz = c.minZ + r.nextDouble() * (c.maxZ - c.minZ);
            level.addParticle(ParticleTypes.ASH, px, py, pz, 0, 0, 0);
        }
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        FANTASMAS.clear();
        nivel = null;
    }

    // ------------------------------------------------------------------ dibujo
    /** Del todo escondido: no se dibuja nada (ni capas, ni nombre, ni flechas clavadas). Antes que las demás poses del mod. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        if (oculto(e.getEntity())) e.setCanceled(true);
    }

    /** El nombre no se ve nunca (tampoco cuando asoma). */
    @SubscribeEvent
    public static void nombre(RenderNameTagEvent e) {
        if (dibujoFantasma(e.getEntity()) && e.getEntity() != Minecraft.getInstance().player) e.setResult(Event.Result.DENY);
    }

    // ------------------------------------------------------------------ HUD: el icono latiendo para el que lo lleva
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer yo = mc.player;
        if (yo == null || mc.options.hideGui || yo.isSpectator() || !yo.hasEffect(EfectosRegistro.FANTASMA.get())) return;
        float a = 0.55F + 0.3F * Mth.sin((yo.tickCount + pt) * 0.12F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1F, 1F, 1F, a);
        g.blit(ICONO, 6, h - 24, 0, 0, 18, 18, 18, 18);
        g.setColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }
}

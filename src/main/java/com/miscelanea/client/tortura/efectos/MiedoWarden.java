package com.miscelanea.client.tortura.efectos;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Los wardens que vienen a por ti (toxina del miedo, desde la etapa 2; uno cada 20-40 s). Es el warden de vanilla de
 * verdad (su modelo, sus texturas, sus capas que brillan y sus animaciones), pero solo existe en tu cliente: no está en
 * el mundo, nadie más lo ve y no hace daño.
 * <ol>
 *   <li>AVISO: chilla un sensor de sculk donde va a salir y suben almas de sculk del suelo; la oscuridad empieza a latir.</li>
 *   <li>EMERGE: sale del suelo con su animación de vanilla, el sonido y la tierra saltando.</li>
 *   <li>OLFATEA: se gira hacia ti olfateando; el corazón del pecho late (y se oye).</li>
 *   <li>RUGE: ruge con los zarcillos vibrando.</li>
 *   <li>CARGA: corre derecho a por ti (atraviesa lo que haga falta: es una alucinación), cada vez más deprisa (las
 *       piernas al ritmo de la velocidad), retumbando: cada pisada es un golpe de cámara (trauma y la cabeza que se
 *       hunde un poco). La cámara hace un VÉRTIGO (dolly zoom): él no cambia de tamaño y el mundo se estira.</li>
 *   <li>SUSTO (0,35 s): se abalanza encima de ti, la cabeza agachada hacia tu cara (en su sitio del mundo, iluminado,
 *       no una cara pegada a la pantalla), golpe de sonido y rugido, sacudida con trauma, la cabeza hacia atrás, el
 *       vértigo que se suelta de golpe y un fogonazo rojo cortito. Al tocarte se deshace en tinta, humo y almas.</li>
 * </ol>
 * Mientras hay un warden, pide la oscuridad de vanilla (efecto "Oscuridad" solo en el cliente: la niebla y la luz
 * laten como con un warden de verdad y respeta la opción "Pulso de oscuridad"). Respeta los 15 s entre sustos: si no
 * toca, llega, ruge y se deshace sin susto de pantalla.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class MiedoWarden {
    private MiedoWarden() {}

    private static final int AVISO = 0, EMERGE = 1, OLFATEA = 2, RUGE = 3, CARGA = 4, SUSTO = 5;
    private static final int T_AVISO = 40, T_EMERGE = 118, T_OLFATEA = 46, T_RUGE = 52, T_CARGA_MAX = 160, T_SUSTO = 7;
    /** Altura de los ojos del warden sobre los pies (para colocar la cara delante de la cámara). */
    private static final double OJOS = 2.5;

    /**
     * Warden de mentira: el de vanilla, pero el latido del pecho y los zarcillos los mueve esta clase (no se le hace
     * tick: no está en el mundo). Sin @Override a propósito: si la firma cambiara, compila igual y solo se pierde el latido.
     */
    static final class WardenFalso extends Warden {
        float corazon, corazonO, zarcillo, zarcilloO;

        WardenFalso(ClientLevel l) {
            super(EntityType.WARDEN, l);
        }

        public float getHeartAnimation(float pt) {
            return Mth.lerp(pt, corazonO, corazon);
        }

        public float getTendrilAnimation(float pt) {
            return Mth.lerp(pt, zarcilloO, zarcillo);
        }
    }

    private static WardenFalso ent;
    private static int fase = -1, t, proxWarden = -1, idN, activoVisto;
    private static double x, y, z, xo, yo, zo, vel;
    private static float yaw, yawO;
    /** Dirección (horizontal) de la embestida final y de dónde sale. */
    private static double susX, susZ, susD0;

    static boolean activo() {
        return ent != null;
    }

    static boolean cargando() {
        return ent != null && (fase == CARGA || fase == SUSTO);
    }

    static void limpiar() {
        if (ent != null) ent.discard();
        ent = null;
        fase = -1;
        proxWarden = -1;
        MiedoCamara.vertigo(0F, 6);
    }

    // ------------------------------------------------------------------ tick (lo llama MiedoCliente con el miedo puesto)
    static void tick(Minecraft mc, LocalPlayer p, RandomSource r, int activo, int etapa, int dur) {
        activoVisto = activo;
        ClientLevel l = mc.level;
        if (l == null) return;
        if (ent == null) {
            MiedoCamara.vertigo(0F, 6);
            if (etapa < 2) return;
            if (proxWarden < 0) proxWarden = activo + 100 + r.nextInt(140);
            // hace falta tiempo para que llegue antes de que se acabe el efecto, y no pisar la cara del Espantapájaros
            if (activo >= proxWarden && dur > 300 && MiedoCliente.reloj() - MiedoCliente.ultimoSusto() > 100 && !MiedoSusto.enCurso())
                proxWarden = aparecer(l, p, r) ? Integer.MAX_VALUE : activo + 40;
            return;
        }
        t++;
        xo = x;
        yo = y;
        zo = z;
        yawO = yaw;
        WardenFalso w = ent;
        w.tickCount++;
        w.yBodyRotO = w.yBodyRot;
        w.yHeadRotO = w.yHeadRot;
        w.yRotO = w.getYRot();
        w.xRotO = w.getXRot();
        w.corazonO = w.corazon;
        w.zarcilloO = w.zarcillo;
        w.corazon = Math.max(0F, w.corazon - 0.1F);
        w.zarcillo = Math.max(0F, w.zarcillo - 0.1F);
        MiedoMundo.pedirOscuridad(80);
        double dx = p.getX() - x, dz = p.getZ() - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float hacia = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float andar = 0F;
        switch (fase) {
            case AVISO -> {
                // almas de sculk que suben donde va a salir; a la mitad, el "se acerca" de los sensores
                if (t % 3 == 0)
                    l.addParticle(ParticleTypes.SCULK_SOUL, x + r.nextGaussian() * 0.4, y + 0.1, z + r.nextGaussian() * 0.4, 0, 0.05, 0);
                if (t == 22) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_NEARBY_CLOSEST, 1F, 0.9F));
                yaw = hacia;
                if (t >= T_AVISO) cambiar(EMERGE);
            }
            case EMERGE -> {
                if (t < 92) tierra(l, r);
                yaw = hacia;
                if (t % 20 == 10) latir(mc, 0.6F);
                if (t >= T_EMERGE) cambiar(OLFATEA);
            }
            case OLFATEA -> {
                yaw = Mth.approachDegrees(yaw, hacia, 5F);
                if (t == 24) MiedoCliente.sonar(mc, SoundEvents.WARDEN_SNIFF, pos(1.5), 2F, 1F);
                if (t % 20 == 0) latir(mc, 1F);
                if (t % 12 == 0) w.zarcillo = 1F;
                if (t >= T_OLFATEA) cambiar(RUGE);
            }
            case RUGE -> {
                yaw = Mth.approachDegrees(yaw, hacia, 10F);
                if (t % 3 == 0) w.zarcillo = 1F;
                if (t % 12 == 0) latir(mc, 1F);
                if (t >= T_RUGE) cambiar(CARGA);
            }
            case CARGA -> {
                yaw = hacia;
                vel = Math.min(0.95, vel + 0.045);
                // las piernas al ritmo de lo que corre (como el walkAnimation de vanilla: distancia por tick × 4)
                andar = (float) Math.min(1.0, vel * 2.2);
                // vértigo: él se queda del mismo tamaño y el fondo se estira (más cuanto más cerca)
                MiedoCamara.vertigo((float) Mth.clamp((22.0 - dist) / 12.0, 0.25, 1.0), Math.max(dist, 2.0));
                if (dist > 0.01) {
                    double paso = Math.min(vel, dist);
                    double nx = x + dx / dist * paso, nz = z + dz / dist * paso;
                    double ny = MiedoCliente.suelo(l, nx, y, nz);
                    x = nx;
                    z = nz;
                    // sigue el suelo si lo hay cerca; si no (paredes, huecos), va hacia tu altura
                    y = !Double.isNaN(ny) && Math.abs(ny - y) < 1.6 ? ny : Mth.lerp(0.3, y, p.getY());
                }
                // pisadas cada vez más seguidas con la velocidad
                int cada = vel > 0.7 ? 4 : 5;
                if (t % cada == 0) {
                    MiedoCliente.sonar(mc, SoundEvents.WARDEN_STEP, pos(0.2), 2.5F, 0.75F);
                    float cerca = (float) Mth.clamp(1.0 - dist / 20.0, 0.15, 1.0);
                    MiedoCamara.SACUDIDA.golpe(0.2F * cerca + 0.05F);
                    MiedoCamara.SACUDIDA.patada(0F, 1.4F * cerca, 0F);
                }
                if (t % 8 == 0) latir(mc, 1F);
                if (t % 4 == 0) w.zarcillo = 1F;
                if (t == 1) MiedoCliente.sonar(mc, SoundEvents.WARDEN_ANGRY, pos(OJOS), 3F, 0.9F);
                if (dist < 2.6) {
                    if (MiedoCliente.puedeSusto()) {
                        susD0 = Math.max(dist, 1.2);
                        susX = -dx / Math.max(dist, 1e-3);
                        susZ = -dz / Math.max(dist, 1e-3);
                        cambiar(SUSTO);
                    } else {
                        MiedoCliente.sonar(mc, SoundEvents.WARDEN_ROAR, pos(OJOS), 1.5F, 1.1F);
                        humo(mc, l, r, x, y, z);
                        return;
                    }
                } else if (t > T_CARGA_MAX) {
                    humo(mc, l, r, x, y, z);
                    return;
                }
            }
            case SUSTO -> {
                w.zarcillo = 1F;
                w.corazon = 1F;
                // se abalanza: de donde estaba hasta tenerte debajo (su pecho a medio bloque de tus ojos)
                float s = Mth.clamp(t / (float) T_SUSTO, 0F, 1F);
                double d = Mth.lerp(s * s, susD0, 0.75);
                x = p.getX() + susX * d;
                z = p.getZ() + susZ * d;
                y = Mth.lerp(0.5, y, p.getY());
                yaw = (float) (Mth.atan2(-susZ, -susX) * Mth.RAD_TO_DEG) - 90F;
                if (t >= T_SUSTO) {
                    // al tocarte, se deshace
                    humo(mc, l, r, x, y, z);
                    return;
                }
            }
            default -> {
            }
        }
        w.setYRot(yaw);
        w.yBodyRot = yaw;
        w.yHeadRot = yaw;
        // la cabeza te busca (arriba/abajo)
        double dy = p.getEyeY() - (y + OJOS);
        w.setXRot(Mth.clamp((float) -(Mth.atan2(dy, Math.max(dist, 0.5)) * Mth.RAD_TO_DEG), -40F, 40F));
        w.walkAnimation.update(andar, 0.4F);
        w.setPos(x, y, z);
        w.xo = xo;
        w.yo = yo;
        w.zo = zo;
    }

    private static Vec3 pos(double alto) {
        return new Vec3(x, y + alto, z);
    }

    /** El latido del pecho: el corazón se enciende y se oye. */
    private static void latir(Minecraft mc, float vol) {
        if (ent == null) return;
        ent.corazon = 1F;
        MiedoCliente.sonar(mc, SoundEvents.WARDEN_HEARTBEAT, pos(1.8), 1.6F * vol, 1F);
    }

    /** La tierra que salta al salir (como el clientDiggingParticles de vanilla). */
    private static void tierra(ClientLevel l, RandomSource r) {
        BlockState b = l.getBlockState(BlockPos.containing(x, y - 0.3, z));
        if (b.isAir() || b.getRenderShape() == RenderShape.INVISIBLE) return;
        BlockParticleOption op = new BlockParticleOption(ParticleTypes.BLOCK, b);
        for (int i = 0; i < 14; i++)
            l.addParticle(op, x + Mth.randomBetween(r, -0.7F, 0.7F), y, z + Mth.randomBetween(r, -0.7F, 0.7F), 0, 0, 0);
    }

    private static void cambiar(int nueva) {
        Minecraft mc = Minecraft.getInstance();
        WardenFalso w = ent;
        if (w == null) return;
        fase = nueva;
        t = 0;
        switch (nueva) {
            case EMERGE -> {
                // la pose arranca la animación de vanilla (Warden.onSyncedDataUpdated)
                w.setPose(Pose.EMERGING);
                MiedoCliente.sonar(mc, SoundEvents.WARDEN_EMERGE, pos(0.5), 3F, 1F);
            }
            case OLFATEA -> {
                w.emergeAnimationState.stop();
                w.setPose(Pose.SNIFFING);
                MiedoCliente.sonar(mc, SoundEvents.WARDEN_SNIFF, pos(OJOS), 2F, 1F);
            }
            case RUGE -> {
                w.sniffAnimationState.stop();
                w.setPose(Pose.ROARING);
                w.zarcillo = 1F;
                MiedoCliente.sonar(mc, SoundEvents.WARDEN_ROAR, pos(OJOS), 3.5F, 1F);
            }
            case CARGA -> {
                w.roarAnimationState.stop();
                w.setPose(Pose.STANDING);
                vel = 0.3;
            }
            case SUSTO -> {
                MiedoCliente.marcarSusto();
                // vuelve a rugir, encima de ti (la pose reinicia la animación del rugido)
                w.setPose(Pose.ROARING);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_GOLPE.get(), 0.9F, 1F));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_ROAR, 1.15F, 1F));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_SONIC_BOOM, 1.3F, 0.4F));
                MiedoCamara.golpe(1F);
                MiedoCamara.SACUDIDA.golpe(0.9F);
                float lado = Minecraft.getInstance().level != null && Minecraft.getInstance().level.random.nextBoolean() ? 1F : -1F;
                MiedoCamara.SACUDIDA.patada(3F * lado, -8F, 5F * lado);
                // el vértigo se suelta de golpe
                MiedoCamara.vertigo(0F, 2);
                MiedoCamara.latido();
            }
            default -> {
            }
        }
    }

    /**
     * Se deshace donde esté: tinta y humo por todo el cuerpo (de los pies a la cabeza, no una bola), almas de sculk que
     * suben y su sonido; el siguiente, dentro de 20-40 s (algo antes con más dosis).
     */
    private static void humo(Minecraft mc, ClientLevel l, RandomSource r, double hx, double hy, double hz) {
        for (int i = 0; i < 40; i++) {
            double h = r.nextDouble() * 2.9;
            double ancho = 0.25 + 0.25 * Math.sin(Math.PI * h / 2.9);
            l.addParticle(i % 2 == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.LARGE_SMOKE, hx + r.nextGaussian() * ancho, hy + h,
                    hz + r.nextGaussian() * ancho, r.nextGaussian() * 0.03, 0.02 + r.nextDouble() * 0.04, r.nextGaussian() * 0.03);
        }
        for (int i = 0; i < 16; i++)
            l.addParticle(ParticleTypes.SCULK_SOUL, hx + r.nextGaussian() * 0.4, hy + r.nextDouble() * 2.6, hz + r.nextGaussian() * 0.4, 0, 0.05, 0);
        MiedoCliente.sonar(mc, EfectosRegistro.MIEDO_DISOLVER.get(), new Vec3(hx, hy + 1.6, hz), 1.2F, 0.8F);
        MiedoCamara.vertigo(0F, 2);
        if (ent != null) ent.discard();
        ent = null;
        fase = -1;
        float dosis = MiedoCliente.ritmo() > 1.5F ? 0.8F : 1F;
        proxWarden = activoVisto + (int) ((400 + r.nextInt(400)) * dosis);
    }

    private static boolean aparecer(ClientLevel l, LocalPlayer p, RandomSource r) {
        // casi siempre delante (para que lo veas salir); a veces detrás (lo oyes cavar y te giras)
        boolean delante = r.nextFloat() < 0.7F;
        for (int i = 0; i < 8; i++) {
            float a = p.getYRot() + (delante ? -55F + r.nextFloat() * 110F : 125F + r.nextFloat() * 110F);
            double d = 13 + r.nextDouble() * 7;
            float rad = a * Mth.DEG_TO_RAD;
            double fx = p.getX() - Mth.sin(rad) * d, fz = p.getZ() + Mth.cos(rad) * d;
            double fy = MiedoCliente.suelo(l, fx, p.getY(), fz);
            if (Double.isNaN(fy)) continue;
            WardenFalso w;
            try {
                w = new WardenFalso(l);
            } catch (Exception ex) {
                Miscelanea.LOGGER.warn("[miscelanea] No se pudo crear el warden del miedo", ex);
                proxWarden = Integer.MAX_VALUE;
                return false;
            }
            w.setId(-3000 - (idN++ & 0xFFF));
            w.setPos(fx, fy, fz);
            ent = w;
            x = xo = fx;
            y = yo = fy;
            z = zo = fz;
            yaw = yawO = (float) (Mth.atan2(p.getZ() - fz, p.getX() - fx) * Mth.RAD_TO_DEG) - 90F;
            vel = 0;
            fase = AVISO;
            t = 0;
            MiedoCliente.sonar(Minecraft.getInstance(), SoundEvents.SCULK_SHRIEKER_SHRIEK, new Vec3(fx, fy + 0.5, fz), 2.5F, 0.85F);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ dibujo
    @SubscribeEvent
    public static void dibujar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ent == null || fase == AVISO) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        WardenFalso w = ent;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        double rx = Mth.lerp(pt, xo, x), ry = Mth.lerp(pt, yo, y), rz = Mth.lerp(pt, zo, z);
        float rot = Mth.rotLerp(pt, yawO, yaw);
        int l = LevelRenderer.getLightColor(mc.level, BlockPos.containing(rx, ry + 1.5, rz));
        // un mínimo de luz: en la oscuridad total se ve sólido (y lo que brilla de él, siempre); encima de ti, con luz
        int luz = fase == SUSTO ? LightTexture.pack(13, 0) : LightTexture.pack(Math.max(LightTexture.block(l), 5), LightTexture.sky(l));
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
        d.setRenderShadow(false);
        try {
            d.render(w, rx - cam.x, ry - cam.y, rz - cam.z, rot, pt, ps, bs, luz);
        } catch (Exception ex) {
            // algún mod de render que no se lleva bien con entidades fuera del mundo: se acabó el warden
            Miscelanea.LOGGER.warn("[miscelanea] No se pudo dibujar el warden del miedo", ex);
            ent = null;
            fase = -1;
            proxWarden = Integer.MAX_VALUE;
        } finally {
            d.setRenderShadow(true);
            bs.endBatch();
        }
    }
}

package com.miscelanea.client.tortura.efectos;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Los sustos de la toxina del miedo, montados como en una película (en lugar de una imagen plana que salta):
 * <ol>
 *   <li>ANTICIPACIÓN (2,5-4 s): el mundo se queda sin sonido (se cortan música, ambiente y tiempo; solo queda lo del
 *       miedo), sube un dron de tensión, el latido se oye más, la cámara se acerca despacio, se inclina y se calma, y
 *       los bordes se desenfocan.</li>
 *   <li>SILENCIO (0,3-0,5 s): se corta TODO, también el dron.</li>
 *   <li>REVELACIÓN (0,4 s): una figura de verdad en 3D (el Espantapájaros: esqueleto con calabaza tallada y hoz; o tu
 *       doble) se te echa encima hasta la cara, iluminada, inclinada hacia ti; golpe de sonido, sacudida con trauma,
 *       la cabeza hacia atrás, tirón del campo de visión, fogonazo rojo cortito y separación de colores.</li>
 *   <li>DISOLUCIÓN: se deshace en tinta, humo y almas, y vuelve el sonido.</li>
 * </ol>
 * Tres montajes, al azar: DE FRENTE (la ves quieta a lo lejos en la oscuridad mientras sube la tensión; en el
 * silencio desaparece... y está en tu cara), DETRÁS (respira detrás de ti; si te giras, está ahí; si no te giras, al
 * final te sale por delante) y APAGÓN (las luces fallan, negro, y al volver la luz está delante; negro otra vez y ya
 * no). A veces es un AMAGO: todo sube, silencio... y solo un susurro al oído (el siguiente susto llega antes).
 * Como mucho uno cada {@link MiedoCliente#SUSTO_MIN} ticks, nunca con un warden en marcha.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class MiedoSusto {
    private MiedoSusto() {}

    private static final int FRENTE = 0, DETRAS = 1, APAGON = 2;
    private static final int NADA = -1, ANTICIPA = 0, SILENCIO = 1, REVELA = 2, DISUELVE = 3, VISTAZO = 4;
    private static final int T_REVELA = 8, T_DISUELVE = 16;

    private static int fase = NADA, t, tipo, durAnticipa, durSilencio, proxSusto = -1, ultimoReal = -100000, idN;
    private static boolean amago, ultimoAmago, girado, visible;
    private static LivingEntity ent;
    /** Pies de la figura (mundo), su dirección hacia la cámara y lo lejos que llega (distancia final a los ojos). */
    private static double fx, fy, fz, fxo, fyo, fzo;
    private static double dirX, dirZ, dRevela0, dFinal;
    private static float escala = 1F, tinte = 1F, inclina;
    private static int apagonIni = -1, apagonFin = -1, apagon2Ini = -1, apagon2Fin = -1;
    private static SoundInstance dron;
    private static final Set<SoundInstance> NUESTROS = Collections.newSetFromMap(new IdentityHashMap<>());

    // ------------------------------------------------------------------ estado
    static boolean enCurso() {
        return fase >= ANTICIPA && fase <= DISUELVE;
    }

    /** Corazón desbocado: durante el susto y los 5 s de después. */
    static boolean panico() {
        return fase == REVELA || fase == DISUELVE || MiedoCliente.reloj() - ultimoReal < 100;
    }

    /** ¿Se está tragando el sonido del mundo? */
    static boolean anticipando() {
        return fase == ANTICIPA;
    }

    /** Negro del apagón (0..1). */
    static float negro(float pt) {
        int r = MiedoCliente.reloj();
        float a = 0F;
        if (apagonIni >= 0) a = Math.max(a, tramo(r + pt, apagonIni, apagonFin));
        if (apagon2Ini >= 0) a = Math.max(a, tramo(r + pt, apagon2Ini, apagon2Fin));
        return a;
    }

    /** 0 fuera de [ini, fin], 1 dentro, con un tick de fundido a cada lado (no es un destello: es oscuridad). */
    private static float tramo(float x, int ini, int fin) {
        if (x < ini - 1 || x > fin + 1) return 0F;
        if (x < ini) return x - (ini - 1);
        if (x > fin) return 1F - (x - fin);
        return 1F;
    }

    static void limpiar() {
        terminarSonido();
        ent = null;
        visible = false;
        if (fase != VISTAZO) fase = NADA;
        proxSusto = -1;
        apagonIni = apagonFin = apagon2Ini = apagon2Fin = -1;
        MiedoCamara.tension(0F, 1F);
    }

    private static void terminarSonido() {
        if (dron != null) Minecraft.getInstance().getSoundManager().stop(dron);
        dron = null;
        NUESTROS.clear();
    }

    private static void sonarNuestro(Minecraft mc, SoundInstance s) {
        NUESTROS.add(s);
        mc.getSoundManager().play(s);
    }

    // ------------------------------------------------------------------ figuras
    /** El Espantapájaros: esqueleto wither (alto y flaco) con su saco de arpillera por cabeza y una hoz (azada de hierro). */
    static LivingEntity espantapajaros(ClientLevel l) {
        WitherSkeleton w = new WitherSkeleton(EntityType.WITHER_SKELETON, l);
        w.setItemSlot(EquipmentSlot.HEAD, new ItemStack(com.miscelanea.tortura.efectos.EfectosRegistro.CABEZA_ESPANTAPAJAROS.get()));
        w.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        // brazos por delante (pose de ataque del modelo de esqueleto sin arco)
        w.setAggressive(true);
        w.setId(-7000 - (idN++ & 0xFFF));
        return w;
    }

    private static boolean crearFigura(ClientLevel l, LocalPlayer p, RandomSource r) {
        try {
            boolean doble = r.nextInt(10) < 3;
            ent = doble ? MiedoMundo.crearDoble(l, p, -7600 - (idN++ & 0xFF)) : espantapajaros(l);
            escala = doble ? 1F : 1.08F;
            tinte = doble ? 0.42F : 0.8F;
            // la cara se queda a esta distancia de los ojos (lo justo para llenar la pantalla sin meterte dentro)
            dFinal = doble ? 0.42 : 0.56;
            return ent != null;
        } catch (Exception ex) {
            Miscelanea.LOGGER.warn("[miscelanea] No se pudo crear la figura del susto", ex);
            ent = null;
            proxSusto = Integer.MAX_VALUE;
            return false;
        }
    }

    private static double ojos() {
        return ent == null ? 1.6 : ent.getEyeHeight() * escala;
    }

    private static void ponerFigura(double x, double y, double z) {
        fxo = fx;
        fyo = fy;
        fzo = fz;
        fx = x;
        fy = y;
        fz = z;
    }

    // ------------------------------------------------------------------ tick (lo llama MiedoCliente)
    static void tick(Minecraft mc, LocalPlayer p, RandomSource r, int activo, int etapa, int dur) {
        ClientLevel l = mc.level;
        if (l == null) return;
        if (ent != null) {
            ent.tickCount++;
            ent.yBodyRotO = ent.yBodyRot;
            ent.yHeadRotO = ent.yHeadRot;
            ent.yRotO = ent.getYRot();
            ent.xRotO = ent.getXRot();
        }
        fxo = fx;
        fyo = fy;
        fzo = fz;
        if (fase == VISTAZO) {
            tickVistazo(mc, p);
            return;
        }
        if (fase == NADA) {
            if (etapa < 2) return;
            if (proxSusto < 0) proxSusto = activo + 160 + r.nextInt(120);
            if (activo >= proxSusto && dur > 140 && MiedoCliente.puedeSusto() && !MiedoWarden.activo()) empezar(mc, l, p, r);
            return;
        }
        t++;
        switch (fase) {
            case ANTICIPA -> anticipa(mc, l, p, r, activo);
            case SILENCIO -> {
                if (t >= durSilencio) {
                    if (amago) terminarAmago(mc, p, r, activo);
                    else revelar(mc, p, r);
                }
            }
            case REVELA -> revela(mc, l, p, r);
            case DISUELVE -> {
                if (t >= T_DISUELVE) {
                    fase = NADA;
                    proxSusto = activo + Math.max(MiedoCliente.SUSTO_MIN, (int) ((420 + r.nextInt(320)) / MiedoCliente.ritmo()));
                }
            }
            default -> {
            }
        }
        orientar(p);
    }

    private static void empezar(Minecraft mc, ClientLevel l, LocalPlayer p, RandomSource r) {
        int x = r.nextInt(100);
        tipo = x < 40 ? FRENTE : x < 75 ? DETRAS : APAGON;
        amago = !ultimoAmago && r.nextInt(4) == 0;
        ultimoAmago = amago;
        girado = false;
        visible = false;
        if (!amago && !crearFigura(l, p, r)) return;
        durAnticipa = 50 + r.nextInt(31);
        durSilencio = 6 + r.nextInt(5);
        // ¿dónde está?
        if (tipo == FRENTE && !amago && !colocarLejos(l, p, r)) tipo = APAGON;
        if (tipo == DETRAS && !amago) {
            float rad = (p.getYRot() + 180F + (r.nextFloat() - 0.5F) * 40F) * Mth.DEG_TO_RAD;
            double d = 1.6;
            double x0 = p.getX() - Mth.sin(rad) * d, z0 = p.getZ() + Mth.cos(rad) * d;
            ponerFigura(x0, p.getY(), z0);
            fxo = fx;
            fyo = fy;
            fzo = fz;
            visible = true;
        }
        fase = ANTICIPA;
        t = 0;
        // el mundo se queda sin sonido; sube el dron
        mc.getSoundManager().stop(null, SoundSource.MUSIC);
        mc.getSoundManager().stop(null, SoundSource.AMBIENT);
        mc.getSoundManager().stop(null, SoundSource.WEATHER);
        dron = SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_TENSION.get(), 0.95F + r.nextFloat() * 0.1F, 0.8F);
        sonarNuestro(mc, dron);
        MiedoCamara.tension(0.05F, r.nextBoolean() ? 1F : -1F);
        if (tipo != DETRAS) MiedoMundo.pedirOscuridad(durAnticipa + 40);
    }

    /** De frente: quieta a 9-13 bloques, delante, donde se pueda ver. */
    private static boolean colocarLejos(ClientLevel l, LocalPlayer p, RandomSource r) {
        for (int i = 0; i < 8; i++) {
            float rad = (p.getYRot() + (r.nextFloat() - 0.5F) * 40F) * Mth.DEG_TO_RAD;
            double d = 9 + r.nextDouble() * 4;
            double x0 = p.getX() - Mth.sin(rad) * d, z0 = p.getZ() + Mth.cos(rad) * d;
            double y0 = MiedoCliente.suelo(l, x0, p.getY(), z0);
            if (Double.isNaN(y0)) continue;
            Vec3 cabeza = new Vec3(x0, y0 + ojos(), z0);
            if (l.clip(new ClipContext(p.getEyePosition(), cabeza, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS)
                continue;
            ponerFigura(x0, y0, z0);
            fxo = fx;
            fyo = fy;
            fzo = fz;
            visible = true;
            return true;
        }
        return false;
    }

    private static void anticipa(Minecraft mc, ClientLevel l, LocalPlayer p, RandomSource r, int activo) {
        float s = t / (float) durAnticipa;
        MiedoCamara.tension(Math.min(1F, 0.15F + s * 1.05F), 1F);
        Vec3 ojo = p.getEyePosition();
        switch (tipo) {
            case DETRAS -> {
                // a mitad, respira detrás de ti (en su sitio); si te giras y la ves, ya está
                if (t == durAnticipa / 2 && !amago)
                    sonarNuestro(mc, new SimpleSoundInstance(EfectosRegistro.MIEDO_RESPIRACION.get(), SoundSource.HOSTILE, 1F, 0.9F,
                            RandomSource.create(), fx, fy + ojos(), fz));
                if (!amago && t > 12) {
                    Vec3 hacia = new Vec3(fx, fy + ojos() * 0.85, fz).subtract(ojo).normalize();
                    if (p.getViewVector(1F).dot(hacia) > 0.55) {
                        girado = true;
                        revelar(mc, p, r);
                        return;
                    }
                }
            }
            case APAGON -> {
                // las luces empiezan a fallar a mitad
                if (t == durAnticipa / 3) MiedoMundo.pedirApagon(durAnticipa);
            }
            default -> {
            }
        }
        if (t >= durAnticipa) {
            fase = SILENCIO;
            t = 0;
            // se corta todo: el dron, lo que suena en el mundo
            terminarSonido();
            mc.getSoundManager().stop(null, SoundSource.HOSTILE);
            mc.getSoundManager().stop(null, SoundSource.NEUTRAL);
            mc.getSoundManager().stop(null, SoundSource.AMBIENT);
            // la de lejos desaparece en el silencio; la de detrás, también (no te giraste: ahora sale por delante)
            visible = false;
            if (tipo == APAGON) {
                apagonIni = MiedoCliente.reloj();
                apagonFin = apagonIni + durSilencio;
            }
        }
    }

    private static void terminarAmago(Minecraft mc, LocalPlayer p, RandomSource r, int activo) {
        // nada... solo un susurro pegado al oído; el de verdad vendrá antes de lo que crees
        sonarNuestro(mc, new SimpleSoundInstance(EfectosRegistro.MIEDO_FINAL.get(), SoundSource.HOSTILE, 0.9F, 1F, RandomSource.create(),
                p.getX() - Mth.sin((p.getYRot() + 100F) * Mth.DEG_TO_RAD) * 0.5, p.getEyeY(),
                p.getZ() + Mth.cos((p.getYRot() + 100F) * Mth.DEG_TO_RAD) * 0.5));
        MiedoCamara.tension(0F, 1F);
        MiedoCamara.SACUDIDA.patada(0F, 0F, 2F);
        fase = NADA;
        NUESTROS.clear();
        proxSusto = activo + 140 + r.nextInt(100);
    }

    private static void revelar(Minecraft mc, LocalPlayer p, RandomSource r) {
        if (ent == null) {
            fase = NADA;
            return;
        }
        fase = REVELA;
        t = 0;
        terminarSonido();
        MiedoCliente.marcarSusto();
        ultimoReal = MiedoCliente.reloj();
        Vec3 ojo = p.getEyePosition();
        if (girado) {
            // estaba detrás: la dirección es la de la figura
            double hx = fx - ojo.x, hz = fz - ojo.z, hl = Math.max(1e-3, Math.sqrt(hx * hx + hz * hz));
            dirX = hx / hl;
            dirZ = hz / hl;
            dRevela0 = Math.max(hl, 1.0);
        } else {
            Vec3 m = p.getViewVector(1F);
            double hl = Math.sqrt(m.x * m.x + m.z * m.z);
            if (hl < 1e-3) {
                float yr = p.getYRot() * Mth.DEG_TO_RAD;
                dirX = -Mth.sin(yr);
                dirZ = Mth.cos(yr);
            } else {
                dirX = m.x / hl;
                dirZ = m.z / hl;
            }
            dRevela0 = tipo == APAGON ? 1.1 : 1.8;
        }
        inclina = tipo == APAGON ? 4F : 16F;
        visible = true;
        colocarRevela(ojo, 0F);
        fxo = fx;
        fyo = fy;
        fzo = fz;
        // el golpe
        SoundInstance golpe = SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_GOLPE.get(), 0.94F + r.nextFloat() * 0.12F, 1F);
        sonarNuestro(mc, golpe);
        MiedoCamara.golpe(tipo == APAGON ? 0.8F : 1F);
        MiedoCamara.SACUDIDA.golpe(tipo == APAGON ? 0.6F : 0.85F);
        float lado = r.nextBoolean() ? 1F : -1F;
        MiedoCamara.SACUDIDA.patada(2.5F * lado, -7F, 5F * lado);
        MiedoCamara.tension(0F, 1F);
        MiedoCamara.latido();
        if (tipo == APAGON) {
            apagonIni = apagonFin = -1;
            apagon2Ini = MiedoCliente.reloj() + T_REVELA;
            apagon2Fin = apagon2Ini + 4;
        }
    }

    /** Coloca la figura para la revelación: la cara a {@code d} de los ojos, inclinada hacia ti, un pelo por encima. */
    private static void colocarRevela(Vec3 ojo, float s) {
        double d = tipo == APAGON ? Mth.lerp(s, dRevela0, dRevela0 - 0.15) : Mth.lerp(s * s * s, dRevela0, dFinal);
        double hx = ojo.x + dirX * d, hy = ojo.y + 0.08, hz = ojo.z + dirZ * d;
        double e = ojos();
        double a = inclina * s * Mth.DEG_TO_RAD;
        // inclinada hacia la cámara: los pies quedan más lejos que la cabeza
        ponerFigura(hx + dirX * e * Math.sin(a), hy - e * Math.cos(a), hz + dirZ * e * Math.sin(a));
    }

    private static void revela(Minecraft mc, ClientLevel l, LocalPlayer p, RandomSource r) {
        Vec3 ojo = p.getEyePosition();
        int fin = tipo == APAGON ? T_REVELA + 4 : T_REVELA;
        colocarRevela(ojo, Mth.clamp(t / (float) T_REVELA, 0F, 1F));
        if (tipo == APAGON && t == T_REVELA) visible = false;   // negro otra vez... y ya no está
        if (t >= fin) {
            // primero se cambia de fase: el sonido de disolverse no debe tragárselo el silencio
            fase = DISUELVE;
            if (tipo != APAGON) disolver(mc, l, r);
            else {
                sonarNuestro(mc, SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_SUSURROS.get(), 0.8F, 0.5F));
                ent = null;
                visible = false;
            }
            fase = DISUELVE;
            t = 0;
            apagon2Ini = apagon2Fin = -1;
            NUESTROS.clear();
        }
    }

    /** Se deshace en tinta, humo y almas donde está, con su sonido. */
    private static void disolver(Minecraft mc, ClientLevel l, RandomSource r) {
        double alto = ojos() + 0.3;
        for (int i = 0; i < 26; i++) {
            double h = r.nextDouble() * alto;
            l.addParticle(ParticleTypes.SQUID_INK, fx + r.nextGaussian() * 0.25, fy + h, fz + r.nextGaussian() * 0.25,
                    r.nextGaussian() * 0.03, 0.01 + r.nextDouble() * 0.03, r.nextGaussian() * 0.03);
        }
        for (int i = 0; i < 18; i++)
            l.addParticle(ParticleTypes.LARGE_SMOKE, fx + r.nextGaussian() * 0.3, fy + r.nextDouble() * alto, fz + r.nextGaussian() * 0.3,
                    0, 0.03 + r.nextDouble() * 0.03, 0);
        for (int i = 0; i < 8; i++)
            l.addParticle(ParticleTypes.SCULK_SOUL, fx + r.nextGaussian() * 0.3, fy + r.nextDouble() * alto, fz + r.nextGaussian() * 0.3, 0, 0.05, 0);
        MiedoCliente.sonar(mc, EfectosRegistro.MIEDO_DISOLVER.get(), new Vec3(fx, fy + alto * 0.7, fz), 1F, 0.95F + r.nextFloat() * 0.1F);
        ent = null;
        visible = false;
    }

    /** La figura te mira (cabeza y cuerpo hacia los ojos). */
    private static void orientar(LocalPlayer p) {
        if (ent == null) return;
        double dx = p.getX() - fx, dz = p.getZ() - fz;
        double dist = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        if (fase == REVELA) yaw = (float) (Mth.atan2(-dirZ, -dirX) * Mth.RAD_TO_DEG) - 90F;
        ent.setYRot(yaw);
        ent.yBodyRot = yaw;
        ent.yHeadRot = yaw;
        double dy = p.getEyeY() - (fy + ojos());
        ent.setXRot(Mth.clamp((float) -(Mth.atan2(dy, dist) * Mth.RAD_TO_DEG), -45F, 45F));
        ent.setPos(fx, fy, fz);
        ent.xo = fxo;
        ent.yo = fyo;
        ent.zo = fzo;
        if (ent.tickCount <= 1) {
            ent.yBodyRotO = ent.yHeadRotO = ent.yRotO = yaw;
            ent.xRotO = ent.getXRot();
        }
    }

    // ------------------------------------------------------------------ el último vistazo (al acabarse el miedo)
    /** Con el susurro del final: la figura, de reojo, a un lado; si la miras, ya no está. */
    static void vistazo(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || enCurso()) return;
        RandomSource r = p.getRandom();
        try {
            ent = espantapajaros(mc.level);
        } catch (Exception ex) {
            return;
        }
        escala = 1.08F;
        tinte = 0.3F;
        float rad = (p.getYRot() + (r.nextBoolean() ? 52F : -52F)) * Mth.DEG_TO_RAD;
        double d = 2.6;
        double x0 = p.getX() - Mth.sin(rad) * d, z0 = p.getZ() + Mth.cos(rad) * d;
        double y0 = MiedoCliente.suelo(mc.level, x0, p.getY(), z0);
        ponerFigura(x0, Double.isNaN(y0) ? p.getY() : y0, z0);
        fxo = fx;
        fyo = fy;
        fzo = fz;
        visible = true;
        fase = VISTAZO;
        t = 0;
        orientar(p);
    }

    private static void tickVistazo(Minecraft mc, LocalPlayer p) {
        t++;
        orientar(p);
        Vec3 hacia = new Vec3(fx, fy + ojos() * 0.85, fz).subtract(p.getEyePosition()).normalize();
        if (t > 40 || p.getViewVector(1F).dot(hacia) > 0.9) {
            ent = null;
            visible = false;
            fase = NADA;
        }
    }

    // ------------------------------------------------------------------ sonido: el mundo se calla
    @SubscribeEvent
    public static void sonido(PlaySoundEvent e) {
        if (fase != ANTICIPA && fase != SILENCIO && fase != REVELA) return;
        SoundInstance s = e.getSound();
        if (s == null || NUESTROS.contains(s)) return;
        if (fase == ANTICIPA) {
            // en la anticipación siguen el latido y lo demás del miedo (y los clics de la interfaz)
            if (Miscelanea.MOD_ID.equals(s.getLocation().getNamespace()) || s.getSource() == SoundSource.MASTER) return;
        }
        e.setSound(null);
    }

    /** Tu doble no lleva el nombre encima. */
    @SubscribeEvent
    public static void nombre(RenderNameTagEvent e) {
        if (ent != null && e.getEntity() == ent) e.setResult(Event.Result.DENY);
    }

    // ------------------------------------------------------------------ dibujo
    @SubscribeEvent
    public static void dibujar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ent == null || !visible) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        Camera c = e.getCamera();
        Vec3 cam = c.getPosition();
        double x = Mth.lerp(pt, fxo, fx), y = Mth.lerp(pt, fyo, fy), z = Mth.lerp(pt, fzo, fz);
        int luz;
        float tn = tinte;
        float lean = 0F;
        if (fase == REVELA) {
            // en la cara, iluminada (en el apagón, una luz que tiembla un poco)
            luz = LightTexture.pack(tipo == APAGON ? 11 : 14, 0);
            float s = Mth.clamp((t + pt) / T_REVELA, 0F, 1F);
            lean = inclina * s;
            // con la cara pegada, la posición se recalcula con la cámara de este fotograma (sin retraso de un tick)
            if (tipo != APAGON) {
                double d = Mth.lerp(s * s * s, dRevela0, dFinal);
                double ee = ojos(), a = lean * Mth.DEG_TO_RAD;
                x = cam.x + dirX * d + dirX * ee * Math.sin(a);
                y = cam.y + 0.08 - ee * Math.cos(a);
                z = cam.z + dirZ * d + dirZ * ee * Math.sin(a);
            }
        } else {
            int l = LevelRenderer.getLightColor(mc.level, BlockPos.containing(x, y + 1.0, z));
            luz = LightTexture.pack(Math.max(LightTexture.block(l), 2), LightTexture.sky(l));
            // de lejos, casi una sombra
            if (fase == ANTICIPA && tipo == FRENTE) tn = 0.22F;
        }
        float ftn = tn;
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        MultiBufferSource tintado = rt -> new MiedoCliente.Tinte(bs.getBuffer(rt), ftn, ftn * 0.97F, ftn * 0.9F);
        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
        d.setRenderShadow(false);
        ps.pushPose();
        try {
            ps.translate(x - cam.x, y - cam.y, z - cam.z);
            if (lean != 0F) {
                // eje horizontal perpendicular a la dirección hacia la cámara: la cabeza se viene hacia ti
                Vector3f eje = new Vector3f((float) -dirZ, 0F, (float) dirX);
                ps.mulPose(new Quaternionf().rotateAxis(lean * Mth.DEG_TO_RAD, eje.x, eje.y, eje.z));
            }
            ps.scale(escala, escala, escala);
            d.render(ent, 0, 0, 0, ent.getYRot(), pt, ps, tintado, luz);
        } catch (Exception ex) {
            Miscelanea.LOGGER.warn("[miscelanea] No se pudo dibujar la figura del susto", ex);
            ent = null;
            visible = false;
            proxSusto = Integer.MAX_VALUE;
        } finally {
            ps.popPose();
            d.setRenderShadow(true);
            bs.endBatch();
        }
    }

    /** Sonidos que no son del mundo para el silencio (los que pone otra clase del miedo durante un susto). */
    static void permitir(SoundInstance s) {
        if (enCurso()) NUESTROS.add(s);
    }
}

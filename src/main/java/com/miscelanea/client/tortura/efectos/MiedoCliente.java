package com.miscelanea.client.tortura.efectos;

import com.miscelanea.Miscelanea;
import com.miscelanea.mixin.PostChainAccessor;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Toxina del miedo (Espantapájaros, Batman: Arkham) en el cliente del envenenado. Todo es local y no hace daño:
 * <ul>
 *   <li>ESCALADA: el miedo va por etapas (1 sutil → 2 → 3 horror total) según el tiempo que lleva y la dosis
 *       (amplificador: cada dosis acumulada sube una etapa). La intensidad, la frecuencia de los sustos y lo que puede
 *       pasar dependen de la etapa.</li>
 *   <li>La cámara ({@link MiedoCamara}): deriva suave, respiración, latido, tensión, vértigo, el mundo que se tuerce y
 *       la paleta de pesadilla. Si la tarjeta lo permite, un shader propio (shaders/post/miedo_vivo.json, con
 *       uniformes que se cambian en cada fotograma) ondula la imagen, separa colores con los golpes y desenfoca los
 *       bordes; todo lo que mueve la cámara o deforma la imagen se escala con la opción de accesibilidad "Efectos de
 *       distorsión" (y el campo de visión con "Efectos de campo de visión").</li>
 *   <li>Viñeta verde que late con el corazón, susurros, zumbido, sonidos falsos (pasos que se acercan por detrás y se
 *       paran en cuanto te giras, respiración en la nuca, un creeper que sisea...).</li>
 *   <li>Mobs falsos (siluetas) desde la etapa 2; los SUSTOS montados ({@link MiedoSusto}: anticipación, silencio y
 *       una figura en 3D que se te echa encima) en la 2 y la 3; susurros que te rodean (se mueven en 3D).</li>
 *   <li>Los wardens que vienen a por ti ({@link MiedoWarden}), el engaño del inventario y del HUD ({@link MiedoEngano})
 *       y el mundo que se deforma ({@link MiedoMundo}).</li>
 *   <li>Al acabarse: silencio de golpe (se corta todo, solo queda un pitido de oídos) y un último susurro al oído.</li>
 * </ul>
 * SEGURIDAD: ningún efecto de pantalla completa pasa de 3 Hz (todos son fundidos únicos o ondas lentas), los
 * fogonazos duran menos de 0,2 s y los sustos (figura, warden) van como mucho uno cada {@link #SUSTO_MIN} ticks (15 s).
 * Con "Ocultar destellos de rayos" activado, los fogonazos se quedan en un tercio.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class MiedoCliente {
    private MiedoCliente() {}

    static final ResourceLocation VINETA = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/tortura/vineta_miedo.png");
    private static final ResourceLocation POST = new ResourceLocation(Miscelanea.MOD_ID, "shaders/post/miedo_vivo.json");
    /** Mínimo entre dos sustos de pantalla (figura, warden encima): 15 s. */
    static final int SUSTO_MIN = 300;
    /** Final: ticks de silencio antes del susurro y duración total. */
    private static final int FINAL_SUSURRO = 46, FINAL_FIN = 100;

    private static float k, kAntes;
    private static int activo, tick, reloj, etapa, amp, durRestante = 100000;
    private static int ultimoSusto = -100000;
    private static int proxSusurro, proxZumbido, proxFalso, proxFantasma, proxInclina, proxPesadilla, ultimoLatido;
    private static int idFalso, serieN;
    private static int finalT = -1;
    private static SoundInstance sonidoFinal, sonidoPitido;
    private static ClientLevel nivelVisto;
    private static final List<Fantasma> FANTASMAS = new ArrayList<>();
    private static final List<Pendiente> PENDIENTES = new ArrayList<>();
    // shader
    private static PostChain cadena;
    private static int tamW, tamH;
    private static boolean shaderFallo, shaderEsteFotograma;

    /** Sonido programado; los de una "serie" (pasos que se acercan) se cortan todos si miras hacia ellos. */
    private record Pendiente(int tick, SoundEvent s, double x, double y, double z, float vol, float tono, int serie) {}

    /** Mob falso: una entidad de verdad que no está en el mundo (solo se dibuja), oscurecida como una sombra. */
    private static final class Fantasma {
        static final int ZOMBI = 0, CREEPER = 1, ENDERMAN = 2, ESQUELETO = 3;
        final int tipo;
        final LivingEntity ent;
        double x, y, z, xo, yo, zo;
        float yaw, yawO;
        int vida, oculto, visto, mirada, desvanece = -1, siseo = -1;
        boolean seVio;

        Fantasma(int tipo, LivingEntity ent, double x, double y, double z) {
            this.tipo = tipo;
            this.ent = ent;
            this.x = xo = x;
            this.y = yo = y;
            this.z = zo = z;
        }
    }

    // ------------------------------------------------------------------ estado que usan las demás clases del miedo
    public static float intensidad(float pt) {
        return Mth.lerp(pt, kAntes, k);
    }

    /** 0 = sin miedo; 1 sutil, 2 fuerte, 3 horror. */
    static int etapa() {
        return etapa;
    }

    static int reloj() {
        return reloj;
    }

    static int ultimoSusto() {
        return ultimoSusto;
    }

    static boolean puedeSusto() {
        return reloj - ultimoSusto >= SUSTO_MIN;
    }

    static void marcarSusto() {
        ultimoSusto = reloj;
    }

    /** Lo deprisa que pasan las cosas: sube con la etapa y con la dosis. */
    static float ritmo() {
        return 1F + 0.3F * Math.max(0, etapa - 1) + 0.25F * amp;
    }

    /** Opción de accesibilidad "Efectos de distorsión" (0..1): escala el balanceo, las sacudidas y el shader. */
    static float distorsion() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options == null ? 1F : mc.options.screenEffectScale().get().floatValue();
    }

    /** "Ocultar destellos de rayos": los fogonazos de pantalla se quedan en un tercio. */
    static float escalaDestello() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options != null && mc.options.hideLightningFlash().get() ? 0.33F : 1F;
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            limpiar();
            k = kAntes = 0;
            finalT = -1;
            return;
        }
        if (mc.level != nivelVisto) {
            limpiar();
            MiedoMundo.olvidar();
            finalT = -1;
            nivelVisto = mc.level;
        }
        if (mc.isPaused()) return;
        reloj++;
        kAntes = k;
        RandomSource r = p.getRandom();
        MobEffectInstance ef = p.getEffect(EfectosRegistro.MIEDO.get());
        if (ef == null || !p.isAlive()) {
            if (activo > 0) {
                // se acabó (leche, se agotó...): si llevaba un rato y sigue vivo, silencio y un último susurro
                if (p.isAlive() && activo > 200) empezarFinal(mc);
                limpiar();
            }
            k = Math.max(0F, k - 0.06F);
            if (k <= 0F) cerrarShaders();
            tickFinal(mc);
            MiedoMundo.tick(mc, p, r, 0, 0, 1F);
            MiedoSusto.tick(mc, p, r, 0, 0, 0);
            return;
        }
        finalT = -1;
        activo++;
        tick++;
        int dur = ef.isInfiniteDuration() ? 100000 : ef.getDuration();
        durRestante = dur;
        amp = Mth.clamp(ef.getAmplifier(), 0, 2);
        // escalada: por el tiempo que lleva (o lo que le queda) y por la dosis; nunca baja mientras dura
        float progreso = activo / (float) Math.max(1, activo + dur);
        int porTiempo = 1 + (progreso > 0.28F || activo > 240 ? 1 : 0) + (progreso > 0.6F || activo > 560 ? 1 : 0);
        int nueva = Math.min(3, porTiempo + amp);
        if (nueva > etapa) {
            if (etapa > 0) subirEtapa(mc, p, r, nueva);
            etapa = nueva;
        }
        float tope = etapa == 1 ? 0.5F : etapa == 2 ? 0.78F : 1F;
        float objetivo = Mth.clamp(activo / 50F, 0F, 1F) * Mth.clamp(dur / 80F, 0F, 1F) * tope;
        k += (objetivo - k) * 0.12F;
        if (activo == 1) {
            proxSusurro = 30 + r.nextInt(40);
            proxZumbido = 10;
            proxFalso = 100 + r.nextInt(80);
            proxFantasma = 60 + r.nextInt(40);
            proxInclina = 300 + r.nextInt(300);
            proxPesadilla = 500 + r.nextInt(400);
        }
        sonidos(mc, p, r);
        fantasmas(mc, p, r);
        MiedoWarden.tick(mc, p, r, activo, etapa, dur);
        MiedoEngano.tick(mc, p, r, etapa);
        MiedoMundo.tick(mc, p, r, activo, etapa, ritmo());
        // los sustos montados (etapas 2 y 3; nunca con un warden en marcha ni antes de 15 s del último susto)
        MiedoSusto.tick(mc, p, r, activo, etapa, dur);
        boolean libre = !MiedoSusto.enCurso() && !MiedoWarden.cargando();
        // el mundo se tuerce y se estira un momento (etapa 2+)
        if (activo >= proxInclina && etapa >= 2) {
            proxInclina = activo + (int) ((420 + r.nextInt(420)) / ritmo());
            if (libre) {
                MiedoCamara.inclinar((r.nextBoolean() ? 1F : -1F) * (9F + 5F * (etapa - 2)), 2.2F + r.nextFloat());
                mc.getSoundManager().play(SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_ZUMBIDO.get(), 0.7F, 0.6F));
            }
        }
        // la paleta de pesadilla: un segundo (etapa 3)
        if (activo >= proxPesadilla && etapa >= 3) {
            proxPesadilla = activo + (int) ((700 + r.nextInt(600)) / ritmo());
            if (libre) {
                MiedoCamara.pesadilla();
                mc.getSoundManager().play(SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_INVERSION.get(), 1F, 0.9F));
            }
        }
    }

    /** Cambio de etapa: se nota (susurro pegado al oído o el chillido de un sensor de sculk a lo lejos). */
    private static void subirEtapa(Minecraft mc, LocalPlayer p, RandomSource r, int nueva) {
        if (nueva == 2) sonar(mc, EfectosRegistro.MIEDO_SUSURROS.get(), alrededor(p, r, 0.6, 1.0, 150F, 210F), 1F, 0.75F);
        else sonar(mc, SoundEvents.SCULK_SHRIEKER_SHRIEK, alrededor(p, r, 14, 20, 120F, 240F), 1F, 0.8F);
    }

    private static void limpiar() {
        activo = 0;
        etapa = 0;
        amp = 0;
        durRestante = 100000;
        FANTASMAS.clear();
        PENDIENTES.clear();
        MiedoWarden.limpiar();
        MiedoEngano.limpiar();
        MiedoMundo.limpiar();
        MiedoSusto.limpiar();
        MiedoCamara.relajar();
    }

    private static void cerrarShaders() {
        if (cadena != null) {
            try {
                cadena.close();
            } catch (Exception ignored) {
                // ya estaba cerrado
            }
            cadena = null;
        }
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        limpiar();
        MiedoMundo.olvidar();
        k = kAntes = 0;
        finalT = -1;
        cerrarShaders();
        shaderFallo = false;
        MiedoCamara.limpiar();
    }

    // ------------------------------------------------------------------ el final: silencio y un último susurro
    private static void empezarFinal(Minecraft mc) {
        finalT = 0;
        // silencio de golpe: se corta todo (también la música) y solo queda un pitido de oídos muy bajo
        mc.getSoundManager().stop();
        sonidoPitido = SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_PITIDO.get(), 1F, 0.22F);
        mc.getSoundManager().play(sonidoPitido);
    }

    private static void tickFinal(Minecraft mc) {
        if (finalT < 0) return;
        finalT++;
        if (finalT == FINAL_SUSURRO) {
            sonidoFinal = SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_FINAL.get(), 1F, 1F);
            mc.getSoundManager().play(sonidoFinal);
            // de reojo, una última vez (si hace bastante del último susto); si la miras, ya no está
            if (puedeSusto()) MiedoSusto.vistazo(mc);
            marcarSusto();
        }
        if (finalT > FINAL_FIN) {
            finalT = -1;
            sonidoFinal = sonidoPitido = null;
        }
    }

    /** Durante el silencio del final no suena nada más (salvo el pitido y el susurro). */
    @SubscribeEvent
    public static void silencio(PlaySoundEvent e) {
        if (finalT < 0 || finalT > FINAL_SUSURRO + 2) return;
        SoundInstance s = e.getSound();
        if (s == null || s == sonidoFinal || s == sonidoPitido) return;
        e.setSound(null);
    }

    // ------------------------------------------------------------------ sonidos
    static Vec3 alrededor(LocalPlayer p, RandomSource r, double d0, double d1, float angDesde, float angHasta) {
        float yaw = p.getYRot() + angDesde + r.nextFloat() * (angHasta - angDesde);
        double d = d0 + r.nextDouble() * (d1 - d0);
        float rad = yaw * Mth.DEG_TO_RAD;
        return new Vec3(p.getX() - Mth.sin(rad) * d, p.getEyeY() - 0.2 + r.nextGaussian() * 0.2, p.getZ() + Mth.cos(rad) * d);
    }

    static void sonar(Minecraft mc, SoundEvent s, Vec3 v, float vol, float tono) {
        mc.getSoundManager().play(new SimpleSoundInstance(s, SoundSource.HOSTILE, vol, tono, RandomSource.create(), v.x, v.y, v.z));
    }

    /** Programa un sonido para dentro de unos ticks (sin serie: no se corta al mirar). */
    static void despues(int ticks, SoundEvent s, Vec3 v, float vol, float tono) {
        PENDIENTES.add(new Pendiente(tick + ticks, s, v.x, v.y, v.z, vol, tono, 0));
    }

    private static void sonidos(Minecraft mc, LocalPlayer p, RandomSource r) {
        float ritmo = ritmo();
        // latido: más rápido cuanto más fuerte (0,8 → 1,3 por segundo); desbocado con un warden a la carga o con la vida falsa
        boolean panico = MiedoWarden.cargando() || MiedoEngano.vidaFalsaVisible() || MiedoSusto.panico();
        float tension = MiedoCamara.tension();
        int periodo = panico ? 9 : Math.round(Mth.lerp(Math.max(k, tension), 26F, 13F));
        if (tick - ultimoLatido >= periodo) {
            ultimoLatido = tick;
            MiedoCamara.latido();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_LATIDO.get(), panico ? 1.08F : 1F,
                    Math.min(1F, 0.25F + 0.55F * k + (panico ? 0.3F : 0F) + 0.35F * tension)));
        }
        // durante un susto, nada de sonidos sueltos: que no se pisen con la tensión
        if (MiedoSusto.enCurso()) {
            proxSusurro = Math.max(proxSusurro, activo + 60);
            proxFalso = Math.max(proxFalso, activo + 80);
        }
        if (activo >= proxZumbido) {
            proxZumbido = activo + 170 + r.nextInt(70);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_ZUMBIDO.get(), 0.9F + r.nextFloat() * 0.15F, 0.45F * k));
        }
        if (activo >= proxSusurro) {
            proxSusurro = activo + (int) ((80 + r.nextInt(110)) / ritmo);
            sonar(mc, EfectosRegistro.MIEDO_SUSURROS.get(), alrededor(p, r, 1.2, 2.6, 60F, 300F), 0.6F + 0.4F * k, 0.85F + r.nextFloat() * 0.25F);
        }
        if (activo >= proxFalso && k > 0.3F) {
            proxFalso = activo + (int) ((120 + r.nextInt(150)) / ritmo);
            int opciones = etapa >= 3 ? 10 : etapa == 2 ? 9 : 4;
            switch (r.nextInt(opciones)) {
                case 8 -> mc.getSoundManager().play(new Rodea(p, r));
                case 0, 1 -> pasosDetras(p, r);
                case 2 -> sonar(mc, EfectosRegistro.MIEDO_RESPIRACION.get(), alrededor(p, r, 0.8, 1.4, 160F, 200F), 0.8F, 0.9F + r.nextFloat() * 0.15F);
                case 3 -> sonar(mc, SoundEvents.WOODEN_DOOR_OPEN, alrededor(p, r, 3, 5, 120F, 240F), 0.7F, 0.6F);
                case 4 -> sonar(mc, SoundEvents.CREEPER_PRIMED, alrededor(p, r, 1.4, 2.2, 150F, 210F), 1F, 0.5F);
                case 5 -> sonar(mc, SoundEvents.ZOMBIE_AMBIENT, alrededor(p, r, 5, 8, 0F, 360F), 0.9F, 0.8F + r.nextFloat() * 0.2F);
                case 6 -> sonar(mc, SoundEvents.ENDERMAN_AMBIENT, alrededor(p, r, 4, 7, 90F, 270F), 0.8F, 0.7F);
                case 7 -> sonar(mc, SoundEvents.SKELETON_AMBIENT, alrededor(p, r, 4, 7, 0F, 360F), 0.8F, 0.9F);
                default -> {
                    // el aviso de que un warden se acerca (como el de los sensores de sculk), sin warden... todavía
                    SoundEvent s = r.nextBoolean() ? SoundEvents.WARDEN_NEARBY_CLOSER : SoundEvents.WARDEN_NEARBY_CLOSEST;
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(s, 1F, 0.8F));
                }
            }
        }
        // primero: ¿te has girado hacia alguna serie de pasos? se cortan todos de golpe
        Set<Integer> cortar = null;
        Vec3 mira = p.getViewVector(1F);
        double ml = Math.sqrt(mira.x * mira.x + mira.z * mira.z);
        for (Pendiente pe : PENDIENTES) {
            if (pe.serie() == 0 || ml < 1e-3) continue;
            double hx = pe.x() - p.getX(), hz = pe.z() - p.getZ(), hl = Math.sqrt(hx * hx + hz * hz);
            if (hl < 1e-3) continue;
            if ((mira.x * hx + mira.z * hz) / (ml * hl) > 0.35) {
                if (cortar == null) cortar = new HashSet<>();
                cortar.add(pe.serie());
            }
        }
        for (Iterator<Pendiente> it = PENDIENTES.iterator(); it.hasNext(); ) {
            Pendiente pe = it.next();
            if (cortar != null && cortar.contains(pe.serie())) {
                it.remove();
                continue;
            }
            if (pe.tick() > tick) continue;
            it.remove();
            sonar(mc, pe.s(), new Vec3(pe.x(), pe.y(), pe.z()), pe.vol(), pe.tono());
        }
    }

    /** Pasos que se acercan por detrás (con el sonido del suelo que pisas); en cuanto te giras, paran. A veces, al final, respira. */
    private static void pasosDetras(LocalPlayer p, RandomSource r) {
        BlockState bajo = p.level().getBlockState(p.blockPosition().below());
        SoundEvent paso = bajo.isAir() ? SoundEvents.STONE_STEP : bajo.getSoundType().getStepSound();
        float ang = p.getYRot() + 160F + r.nextFloat() * 40F;
        float rad = ang * Mth.DEG_TO_RAD;
        int serie = ++serieN;
        int n = 5 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            double d = 5.5 - i * (4.3 / n);
            PENDIENTES.add(new Pendiente(tick + i * 8, paso, p.getX() - Mth.sin(rad) * d, p.getY(), p.getZ() + Mth.cos(rad) * d,
                    0.35F + i * 0.06F, 0.95F + r.nextFloat() * 0.1F, serie));
        }
        if (r.nextInt(5) < 2)
            PENDIENTES.add(new Pendiente(tick + n * 8 + 6, EfectosRegistro.MIEDO_RESPIRACION.get(), p.getX() - Mth.sin(rad) * 0.9, p.getEyeY(),
                    p.getZ() + Mth.cos(rad) * 0.9, 0.9F, 0.95F, serie));
    }

    // ------------------------------------------------------------------ mobs falsos
    /** Altura del suelo cerca de (x, y, z), buscando de arriba abajo (NaN si no hay). */
    static double suelo(ClientLevel l, double x, double y, double z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(y + 0.5);
        for (int dy = 3; dy >= -5; dy--) {
            m.set(bx, by + dy, bz);
            BlockPos abajo = m.below();
            BlockState s = l.getBlockState(m), sa = l.getBlockState(abajo);
            if (!s.getCollisionShape(l, m).isEmpty()) continue;
            VoxelShape sh = sa.getCollisionShape(l, abajo);
            if (sh.isEmpty()) continue;
            return abajo.getY() + sh.max(Direction.Axis.Y);
        }
        return Double.NaN;
    }

    private static void fantasmas(Minecraft mc, LocalPlayer p, RandomSource r) {
        ClientLevel l = mc.level;
        if (l == null) return;
        if (activo >= proxFantasma && etapa >= 2) {
            proxFantasma = activo + (int) ((70 + r.nextInt(80)) / ritmo());
            if (FANTASMAS.size() < etapa) aparecer(l, p, r);
        }
        Vec3 ojo = p.getEyePosition();
        Vec3 mira = p.getViewVector(1F);
        for (Iterator<Fantasma> it = FANTASMAS.iterator(); it.hasNext(); ) {
            Fantasma f = it.next();
            f.vida++;
            f.xo = f.x;
            f.yo = f.y;
            f.zo = f.z;
            f.yawO = f.yaw;
            LivingEntity ent = f.ent;
            ent.tickCount++;
            ent.yBodyRotO = ent.yBodyRot;
            ent.yHeadRotO = ent.yHeadRot;
            ent.yRotO = ent.getYRot();
            if (f.desvanece >= 0) {
                // se hunde y se deshace en humo
                f.desvanece++;
                f.y -= 0.12;
                if (f.desvanece % 2 == 0)
                    l.addParticle(ParticleTypes.LARGE_SMOKE, f.x + r.nextGaussian() * 0.25, f.y + 1.0 + r.nextDouble(), f.z + r.nextGaussian() * 0.25, 0, 0.03, 0);
                if (f.desvanece > 10) it.remove();
                continue;
            }
            double dx = p.getX() - f.x, dz = p.getZ() - f.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            Vec3 hacia = new Vec3(f.x, f.y + ent.getBbHeight() * 0.7, f.z).subtract(ojo).normalize();
            double dot = mira.dot(hacia);
            boolean enVista = dot > 0.62;
            if (enVista && ++f.visto > 8) f.seVio = true;
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
            float vel = 0F;
            switch (f.tipo) {
                case Fantasma.ENDERMAN -> {
                    // quieto, mirándote; si le miras a los ojos, chilla y se esfuma
                    if (dot > 0.985) {
                        if (++f.mirada == 4) sonar(mc, SoundEvents.ENDERMAN_STARE, new Vec3(f.x, f.y + 2.5, f.z), 0.6F, 1F);
                        if (f.mirada > 14) {
                            desvanecer(mc, l, f, SoundEvents.ENDERMAN_TELEPORT);
                            continue;
                        }
                    }
                }
                case Fantasma.CREEPER -> {
                    // solo avanza cuando no lo miras; al llegar, sisea... y no hay nada
                    if (f.siseo >= 0) {
                        if (++f.siseo > 18) {
                            desvanecer(mc, l, f, null);
                            continue;
                        }
                    } else if (!enVista) vel = 0.11F;
                    if (f.siseo < 0 && dist < 3.0) {
                        f.siseo = 0;
                        sonar(mc, SoundEvents.CREEPER_PRIMED, new Vec3(f.x, f.y + 1, f.z), 1F, 0.5F);
                    }
                }
                default -> vel = f.tipo == Fantasma.ZOMBI ? 0.065F : 0.075F;
            }
            if (vel > 0 && dist > 1.5) {
                double nx = f.x + dx / dist * vel, nz = f.z + dz / dist * vel;
                double ny = suelo(l, nx, f.y, nz);
                if (!Double.isNaN(ny) && Math.abs(ny - f.y) < 1.2) {
                    f.x = nx;
                    f.z = nz;
                    f.y = ny;
                } else vel = 0F;
            }
            f.yaw = yaw;
            ent.setYRot(yaw);
            ent.yBodyRot = yaw;
            ent.yHeadRot = yaw;
            ent.walkAnimation.update(vel > 0 ? Math.min(1F, vel * 6F) : 0F, 0.4F);
            ent.setPos(f.x, f.y, f.z);
            ent.xo = f.xo;
            ent.yo = f.yo;
            ent.zo = f.zo;
            // parpadeo suelto (un objeto pequeño, pocas veces: no es un destello de pantalla)
            if (f.oculto > 0) f.oculto--;
            else if (r.nextInt(45) == 0) f.oculto = 2 + r.nextInt(3);
            boolean seVa = (f.tipo != Fantasma.CREEPER && dist < 2.8) || f.vida > 420
                    || (f.seVio && !enVista && f.tipo != Fantasma.CREEPER && f.tipo != Fantasma.ENDERMAN)
                    || (f.tipo == Fantasma.ENDERMAN && f.seVio && !enVista && f.vida > 120);
            if (seVa) desvanecer(mc, l, f, null);
        }
    }

    private static void desvanecer(Minecraft mc, ClientLevel l, Fantasma f, SoundEvent s) {
        f.desvanece = 0;
        if (s != null) sonar(mc, s, new Vec3(f.x, f.y + 1.5, f.z), 0.7F, 1F);
        else if (mc.player != null && mc.player.getRandom().nextInt(3) == 0)
            sonar(mc, EfectosRegistro.MIEDO_SUSURROS.get(), new Vec3(f.x, f.y + 1.6, f.z), 0.5F, 0.7F);
        if (f.tipo == Fantasma.ENDERMAN && mc.player != null)
            for (int i = 0; i < 24; i++) l.addParticle(ParticleTypes.PORTAL, f.x, f.y + mc.player.getRandom().nextDouble() * 2.8, f.z, 0, 0, 0);
    }

    private static void aparecer(ClientLevel l, LocalPlayer p, RandomSource r) {
        int x = r.nextInt(100);
        int tipo = x < 38 ? Fantasma.ZOMBI : x < 62 ? Fantasma.CREEPER : x < 82 ? Fantasma.ENDERMAN : Fantasma.ESQUELETO;
        float a0, a1;
        double d0, d1;
        switch (tipo) {
            case Fantasma.ENDERMAN -> {
                a0 = -35F;
                a1 = 35F;
                d0 = 14;
                d1 = 22;
            }
            case Fantasma.CREEPER -> {
                a0 = 140F;
                a1 = 220F;
                d0 = 7;
                d1 = 10;
            }
            default -> {
                boolean delante = r.nextBoolean();
                a0 = delante ? -60F : 70F;
                a1 = delante ? 60F : 290F;
                d0 = delante ? 12 : 9;
                d1 = delante ? 18 : 14;
            }
        }
        for (int intento = 0; intento < 6; intento++) {
            float yaw = (p.getYRot() + a0 + r.nextFloat() * (a1 - a0)) * Mth.DEG_TO_RAD;
            double d = d0 + r.nextDouble() * (d1 - d0);
            double fx = p.getX() - Mth.sin(yaw) * d, fz = p.getZ() + Mth.cos(yaw) * d;
            double fy = suelo(l, fx, p.getY(), fz);
            if (Double.isNaN(fy)) continue;
            LivingEntity ent = switch (tipo) {
                case Fantasma.CREEPER -> new Creeper(EntityType.CREEPER, l);
                case Fantasma.ENDERMAN -> new EnderMan(EntityType.ENDERMAN, l);
                case Fantasma.ESQUELETO -> new Skeleton(EntityType.SKELETON, l);
                default -> new Zombie(EntityType.ZOMBIE, l);
            };
            if (ent instanceof Zombie z) z.setAggressive(true);   // brazos por delante
            // id negativo: no puede coincidir con el de una entidad de verdad (las capas buscan su estado por id)
            ent.setId(-1000 - (idFalso++ & 0xFFFF));
            ent.setPos(fx, fy, fz);
            FANTASMAS.add(new Fantasma(tipo, ent, fx, fy, fz));
            return;
        }
    }

    @SubscribeEvent
    public static void dibujarFantasmas(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || FANTASMAS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
        MultiBufferSource sombra = rt -> new Tinte(bs.getBuffer(rt), 0.11F, 0.17F, 0.10F);
        d.setRenderShadow(false);
        try {
            for (Fantasma f : FANTASMAS) {
                if (f.oculto > 0) continue;
                double x = Mth.lerp(pt, f.xo, f.x), y = Mth.lerp(pt, f.yo, f.y), z = Mth.lerp(pt, f.zo, f.z);
                float yaw = Mth.rotLerp(pt, f.yawO, f.yaw);
                d.render(f.ent, x - cam.x, y - cam.y, z - cam.z, yaw, pt, ps, sombra, 0xF000F0);
            }
        } catch (Exception ex) {
            // algún mod de render que no se lleva bien con entidades fuera del mundo: se dejan de dibujar
            FANTASMAS.clear();
        } finally {
            d.setRenderShadow(true);
            bs.endBatch();
        }
    }

    /** Envoltorio que oscurece (y tiñe) todo lo que se dibuja a través de él: siluetas. */
    record Tinte(VertexConsumer d, float r, float g, float b) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            d.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int cr, int cg, int cb, int ca) {
            d.color((int) (cr * r), (int) (cg * g), (int) (cb * b), ca);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            d.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            d.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            d.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            d.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            d.endVertex();
        }

        @Override
        public void defaultColor(int cr, int cg, int cb, int ca) {
            d.defaultColor((int) (cr * r), (int) (cg * g), (int) (cb * b), ca);
        }

        @Override
        public void unsetDefaultColor() {
            d.unsetDefaultColor();
        }
    }

    // ------------------------------------------------------------------ shader (antes del HUD, como los de vanilla)
    @SubscribeEvent
    public static void antesHud(RenderGuiEvent.Pre e) {
        shaderEsteFotograma = false;
        float kk = intensidad(e.getPartialTick());
        float dist = distorsion();
        if ((kk < 0.05F && !MiedoCamara.necesitaShader()) || shaderFallo || dist <= 0.01F) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            PostChain c = cadena;
            if (c == null) {
                c = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), POST);
                c.resize(w, h);
                cadena = c;
                tamW = w;
                tamH = h;
            } else if (tamW != w || tamH != h) {
                c.resize(w, h);
                tamW = w;
                tamH = h;
            }
            // uniformes de este fotograma (todo lo que deforma, escalado por "Efectos de distorsión")
            float fl = MiedoCliente.escalaDestello();
            for (PostPass pp : ((PostChainAccessor) c).miscelanea$pasadas()) {
                var ef = pp.getEffect();
                ef.safeGetUniform("Intensidad").set(kk * dist);
                ef.safeGetUniform("Golpe").set(MiedoCamara.golpeShader() * dist * (0.5F + 0.5F * fl));
                ef.safeGetUniform("Pesadilla").set(MiedoCamara.pesadillaShader() * (0.4F + 0.6F * fl));
                ef.safeGetUniform("Estira").set(MiedoCamara.estiraShader() * dist);
                ef.safeGetUniform("Desenfoque").set(MiedoCamara.desenfoqueShader() * dist);
                ef.safeGetUniform("Reloj").set((float) (com.miscelanea.client.camara.Reloj.t() % 3600.0));
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
            Miscelanea.LOGGER.warn("[miscelanea] No se pudo usar el shader del miedo; se usa la capa de respaldo", ex);
        }
    }

    // ------------------------------------------------------------------ HUD (por debajo de todo)
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        float kk = intensidad(pt);
        if (kk < 0.01F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        float t = tick + pt;
        // sin shader: velo gris verdoso (desatura un poco y apaga el contraste)
        if (!shaderEsteFotograma) g.fill(0, 0, w, h, ((int) (0.26F * kk * 255) << 24) | 0x4E5E48);
        // las luces que flojean (onda lenta, < 1,5 Hz)
        float apagon = MiedoMundo.parpadeoLuz(pt);
        if (apagon > 0.004F) g.fill(0, 0, w, h, ((int) (apagon * 255) << 24));
        // viñeta verde que late con el corazón (suave: sube y baja, no destella)
        float latido = (float) Math.exp(-(tick - ultimoLatido + pt) * 0.22F);
        float tension = MiedoCamara.tension();
        float a = Mth.clamp(0.42F * kk + 0.28F * kk * latido + 0.4F * tension, 0F, 0.95F);
        float esc = 1F + 0.05F * latido * kk + 0.025F * Mth.sin(t * 0.05F);
        int ew = Math.round(w * esc), eh = Math.round(h * esc);
        g.setColor(1F, 1F, 1F, a);
        g.blit(VINETA, (w - ew) / 2, (h - eh) / 2, 0, 0, ew, eh, ew, eh);
        // segunda viñeta, girada, para que "repte"
        g.pose().pushPose();
        g.pose().translate(w / 2F, h / 2F, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180F + Mth.sin(t * 0.021F) * 6F));
        g.setColor(1F, 1F, 1F, a * 0.45F);
        int ew2 = Math.round(w * (esc + 0.08F)), eh2 = Math.round(h * (esc + 0.08F));
        g.blit(VINETA, -ew2 / 2, -eh2 / 2, 0, 0, ew2, eh2, ew2, eh2);
        g.pose().popPose();
        // con la vida falsa, los bordes se enrojecen al ritmo del latido
        if (MiedoEngano.vidaFalsaVisible()) {
            g.setColor(0.75F, 0.05F, 0.05F, 0.35F + 0.35F * latido);
            g.blit(VINETA, (w - ew) / 2, (h - eh) / 2, 0, 0, ew, eh, ew, eh);
        }
        g.setColor(1F, 1F, 1F, 1F);
        RenderSystem.depthMask(true);
    }

    // ------------------------------------------------------------------ HUD (por encima de todo): manos, sustos
    public static void hudEncima(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        float negro = MiedoSusto.negro(pt);
        if (etapa <= 0 && !MiedoCamara.hayHud() && negro <= 0F && !MiedoMundo.hayManos()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        MiedoMundo.dibujarManos(g, w, h, pt);
        MiedoCamara.hud(g, w, h, pt, shaderEsteFotograma);
        // el apagón de los sustos: negro (oscuridad, no un destello)
        if (negro > 0.003F) g.fill(0, 0, w, h, ((int) (Math.min(1F, negro) * 250) << 24));
        RenderSystem.depthMask(true);
    }

    // ------------------------------------------------------------------ susurros que te rodean
    /** Susurros que dan vueltas alrededor de tu cabeza (sonido en 3D que se mueve) y se van acercando. */
    private static final class Rodea extends AbstractTickableSoundInstance {
        private final LocalPlayer p;
        private final float ang0, vel;
        private int t;
        private static final int DUR = 88;

        Rodea(LocalPlayer p, RandomSource r) {
            super(EfectosRegistro.MIEDO_RODEA.get(), SoundSource.HOSTILE, RandomSource.create());
            this.p = p;
            this.ang0 = p.getYRot() + 90F + r.nextFloat() * 180F;
            this.vel = (r.nextBoolean() ? 1F : -1F) * (4.5F + r.nextFloat() * 2.5F);
            this.volume = 0.85F;
            this.pitch = 0.92F + r.nextFloat() * 0.16F;
            this.relative = false;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            mover();
        }

        private void mover() {
            float a = (ang0 + vel * t) * Mth.DEG_TO_RAD;
            double radio = 1.6 - 0.9 * t / (double) DUR;
            this.x = p.getX() - Mth.sin(a) * radio;
            this.y = p.getEyeY() + 0.1;
            this.z = p.getZ() + Mth.cos(a) * radio;
        }

        @Override
        public void tick() {
            if (++t > DUR || p.isRemoved() || etapa == 0) {
                stop();
                return;
            }
            mover();
        }
    }
}

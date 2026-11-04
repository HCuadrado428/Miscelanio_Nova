package com.miscelanea.client.tortura.efectos;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Toxina del miedo: el mundo se deforma (todo en tu cliente; nada cambia de verdad en el mundo).
 * <ul>
 *   <li>NIEBLA que se va cerrando con la etapa (en la 3, a ratos, hasta unos pocos bloques) y CIELO de sangre (o negro)
 *       a ratos; la niebla se espesa con un warden cerca.</li>
 *   <li>OSCURIDAD de vanilla (efecto "Oscuridad" puesto solo en el cliente, sin icono): la luz y la niebla laten como con
 *       un warden; la piden los wardens y los apagones. Respeta la opción "Pulso de oscuridad".</li>
 *   <li>APAGONES: las luces flojean (capa oscura que sube y baja despacio, menos de 1,5 Hz).</li>
 *   <li>SANGRE: gotea de los techos y resbala por las paredes cerca de ti, con su goteo.</li>
 *   <li>PUERTAS: la puerta (trampilla, puerta de valla) que miras se abre sola (sonido y, si está lejos, también se ve
 *       abierta un momento solo en tu cliente) y luego se cierra.</li>
 *   <li>TU DOBLE: una copia de ti (tu skin, lo que llevas en la mano) muy lejos, quieta, mirándote. Si te fijas (la
 *       miras fijamente, te acercas o usas el catalejo), ya no está. Si dejas de mirarla, a veces está más cerca.</li>
 *   <li>MANOS fantasma que asoman por los bordes de la pantalla y se retiran.</li>
 *   <li>SILUETA: el Espantapájaros quieto a lo lejos, en el borde de la vista (de reojo). En cuanto lo miras de frente,
 *       ya no está (sin humo: simplemente no estaba).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class MiedoMundo {
    private MiedoMundo() {}

    static final ResourceLocation MANO = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/tortura/mano_fantasma.png");

    // niebla y cielo (0..1, suavizados)
    private static float niebla, nieblaO, cielo, cieloO;
    private static int nieblaHasta, cieloHasta, proxNiebla = -1, proxCielo = -1;
    private static boolean cieloNegro;
    // oscuridad y apagones
    private static MobEffectInstance oscuridad;
    private static int oscuridadHasta, apagonDesde = -1, apagonHasta = -1, proxApagon = -1;
    // sangre, puertas
    private static int proxGoteo, proxPuerta = -1;
    private static Puerta puerta;
    // el doble
    private static Doble doble;
    private static int proxDoble = -1;
    // manos
    private static final List<Mano> MANOS = new ArrayList<>();
    private static int proxManos = -1;
    // la silueta de reojo
    private static LivingEntity silueta;
    private static double sx, sy, sz;
    private static int proxSilueta = -1, siluetaVida;

    private record Mano(int lado, int ini, int dur, float pos, boolean espejo) {}

    /** Puerta que "se abre sola": dónde, qué bloques había y cuáles pusimos (solo si se cambió la vista). */
    private record Puerta(BlockPos abajo, BlockState viejo, BlockState nuevo, BlockPos arriba, BlockState viejoA, BlockState nuevoA,
                          int hasta, SoundEvent cierra) {}

    /** Tu doble: un jugador de mentira con tu perfil (tu skin y tus capas), que solo se dibuja. */
    static final class Doble extends RemotePlayer {
        double x, y, z;
        int vida, mirada, sinVer;

        Doble(ClientLevel l, GameProfile g, byte partes) {
            super(l, g);
            this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, partes);
        }
    }

    static boolean hayManos() {
        return !MANOS.isEmpty();
    }

    /** Las luces fallan ya (lo pide un susto de apagón). */
    static void pedirApagon(int ticks) {
        int reloj = MiedoCliente.reloj();
        apagonDesde = reloj;
        apagonHasta = reloj + Math.max(20, ticks);
        pedirOscuridad(ticks + 20);
    }

    static void pedirOscuridad(int ticks) {
        oscuridadHasta = Math.max(oscuridadHasta, MiedoCliente.reloj() + ticks);
    }

    /** Al acabarse el miedo: fuera el doble, las manos y la puerta (la niebla y la oscuridad se apagan solas). */
    static void limpiar() {
        doble = null;
        silueta = null;
        MANOS.clear();
        restaurarPuerta();
        proxNiebla = proxCielo = proxApagon = proxPuerta = proxDoble = proxManos = proxSilueta = -1;
        nieblaHasta = cieloHasta = 0;
        apagonDesde = apagonHasta = -1;
        oscuridadHasta = 0;
    }

    /** Cambio de mundo / salir: se olvida todo (la oscuridad era del jugador viejo). */
    static void olvidar() {
        limpiar();
        puerta = null;
        oscuridad = null;
        niebla = nieblaO = cielo = cieloO = 0;
    }

    // ------------------------------------------------------------------ tick (siempre: con etapa 0 solo se apaga todo)
    static void tick(Minecraft mc, LocalPlayer p, RandomSource r, int activo, int etapa, float ritmo) {
        ClientLevel l = mc.level;
        if (l == null) return;
        int reloj = MiedoCliente.reloj();
        // ---- niebla
        nieblaO = niebla;
        cieloO = cielo;
        float objNiebla = etapa == 0 ? 0F : etapa == 1 ? 0.12F : etapa == 2 ? 0.3F : 0.45F;
        // con un warden, más niebla, pero no tanta que se lo trague: tiene que verse sólido
        if (etapa > 0 && MiedoWarden.activo()) objNiebla = Math.max(objNiebla, 0.5F);
        if (etapa >= 3) {
            if (proxNiebla < 0) proxNiebla = reloj + 80 + r.nextInt(160);
            if (reloj >= proxNiebla) {
                nieblaHasta = reloj + 120 + r.nextInt(100);
                proxNiebla = reloj + (int) ((500 + r.nextInt(400)) / ritmo);
            }
            if (reloj < nieblaHasta) objNiebla = 0.93F;
        }
        niebla += (objNiebla - niebla) * 0.035F;
        // ---- cielo de sangre (o negro, uno de cada tres)
        if (etapa >= 2) {
            if (proxCielo < 0) proxCielo = reloj + 120 + r.nextInt(200);
            if (reloj >= proxCielo) {
                cieloHasta = reloj + 100 + r.nextInt(80);
                cieloNegro = r.nextInt(3) == 0;
                proxCielo = reloj + (int) ((600 + r.nextInt(500)) / ritmo);
            }
        }
        float objCielo = etapa >= 2 && reloj < cieloHasta ? 1F : 0F;
        cielo += (objCielo - cielo) * 0.045F;
        // ---- apagones (etapa 2+): las luces flojean y la oscuridad late
        if (etapa >= 2) {
            if (proxApagon < 0) proxApagon = reloj + 160 + r.nextInt(200);
            if (reloj >= proxApagon) {
                apagonDesde = reloj;
                apagonHasta = reloj + 90 + r.nextInt(70);
                proxApagon = reloj + (int) ((420 + r.nextInt(400)) / ritmo);
                pedirOscuridad(apagonHasta - reloj);
            }
        }
        oscuridad(p, etapa);
        restaurarPuertaSiToca(reloj);
        if (etapa <= 0) return;
        sangre(mc, l, p, r, etapa, reloj);
        puertas(mc, l, p, r, reloj);
        doble(mc, l, p, r, etapa, reloj, ritmo);
        manos(mc, r, etapa, reloj, ritmo);
        silueta(mc, l, p, r, etapa, reloj, ritmo);
    }

    /** Pone o quita la oscuridad de vanilla en el jugador (solo en el cliente). Nunca toca una que haya puesto el servidor. */
    private static void oscuridad(LocalPlayer p, int etapa) {
        MobEffectInstance actual = p.getEffect(MobEffects.DARKNESS);
        if (oscuridad != null && actual != oscuridad) oscuridad = null;   // el servidor puso la suya o ya no está
        if (oscuridad != null && oscuridad.getDuration() <= 0) {
            // en el cliente los efectos no se quitan solos al agotarse: se quita la nuestra
            p.removeEffect(MobEffects.DARKNESS);
            oscuridad = null;
        }
        boolean quiero = etapa > 0 && MiedoCliente.reloj() < oscuridadHasta;
        if (quiero && oscuridad == null && actual == null) {
            // dura 7 s y se renueva mientras se pida: así entra y sale con el fundido de vanilla
            oscuridad = new MobEffectInstance(MobEffects.DARKNESS, 140, 0, false, false, false);
            p.forceAddEffect(oscuridad, null);
        }
    }

    /** Las luces flojean: alfa de la capa oscura (0..~0,4), con una onda lenta y suave (no destella). */
    static float parpadeoLuz(float pt) {
        int reloj = MiedoCliente.reloj();
        if (apagonDesde < 0 || reloj > apagonHasta) return 0F;
        float tt = reloj - apagonDesde + pt;
        float dur = apagonHasta - apagonDesde;
        float env = Mth.clamp(Math.min(tt / 15F, (dur - tt) / 20F), 0F, 1F);
        // como una bombilla que falla: un fondo que respira (ruido suave, ~1,5 Hz) y bajones sueltos e irregulares
        // (crestas del ruido); todo oscurece, nada destella, y nada pasa de ~3 Hz
        float onda = 0.5F + 0.5F * com.miscelanea.client.camara.Ruido.perlin(9011, tt * 0.075);
        float bajon = Math.max(0F, com.miscelanea.client.camara.Ruido.perlin(9012, tt * 0.14) - 0.25F) * 1.6F;
        return env * Math.min(0.62F, 0.1F + 0.22F * onda + 0.45F * bajon);
    }

    // ------------------------------------------------------------------ niebla y cielo
    @SubscribeEvent
    public static void niebla(ViewportEvent.RenderFog e) {
        float pt = (float) e.getPartialTick();
        float n = Mth.lerp(pt, nieblaO, niebla), c = Mth.lerp(pt, cieloO, cielo);
        if ((n < 0.004F && c < 0.004F) || e.getType() != FogType.NONE) return;
        float lejos = e.getFarPlaneDistance(), cerca = e.getNearPlaneDistance();
        float k = e.getMode() == FogRenderer.FogMode.FOG_SKY ? Math.max(n, c) : n;
        if (k < 0.004F) return;
        // interpolación exponencial: 0,12 → ~2/3 de la distancia; 0,45 → ~1/5; 0,93 → ~6 bloques
        float hasta = e.getMode() == FogRenderer.FogMode.FOG_SKY ? 2F : 5F;
        float nl = lejos * (float) Math.pow(Math.max(hasta, 0.01F) / Math.max(lejos, hasta), k);
        // con un warden, la niebla empieza más lejos (se ve entero hasta que está lejos de verdad)
        float nc = Math.min(cerca, nl * (MiedoWarden.activo() ? 0.35F : 0.12F));
        e.setFarPlaneDistance(Math.min(lejos, nl));
        e.setNearPlaneDistance(nc);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void colorNiebla(ViewportEvent.ComputeFogColor e) {
        float pt = (float) e.getPartialTick();
        float n = Mth.lerp(pt, nieblaO, niebla), c = Mth.lerp(pt, cieloO, cielo);
        if ((n < 0.004F && c < 0.004F) || e.getCamera().getFluidInCamera() != FogType.NONE) return;
        float r = e.getRed(), g = e.getGreen(), b = e.getBlue();
        // la niebla espesa es casi negra, un pelo verdosa
        float kn = n * 0.8F;
        r = Mth.lerp(kn, r, 0.035F);
        g = Mth.lerp(kn, g, 0.05F);
        b = Mth.lerp(kn, b, 0.04F);
        // cielo de sangre (o negro del todo)
        float cr = cieloNegro ? 0.012F : 0.36F, cg = cieloNegro ? 0.0F : 0.012F, cb = cieloNegro ? 0.004F : 0.018F;
        e.setRed(Mth.lerp(c, r, cr));
        e.setGreen(Mth.lerp(c, g, cg));
        e.setBlue(Mth.lerp(c, b, cb));
    }

    // ------------------------------------------------------------------ sangre que gotea
    private static void sangre(Minecraft mc, ClientLevel l, LocalPlayer p, RandomSource r, int etapa, int reloj) {
        if (etapa < 2) return;
        BlockParticleOption gota = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.REDSTONE_BLOCK.defaultBlockState());
        DustParticleOptions resbala = new DustParticleOptions(new Vector3f(0.42F, 0.015F, 0.02F), 0.9F);
        BlockPos base = p.blockPosition();
        int intentos = etapa == 2 ? 3 : 8;
        for (int i = 0; i < intentos; i++) {
            BlockPos q = base.offset(r.nextInt(13) - 6, r.nextInt(8) - 2, r.nextInt(13) - 6);
            BlockState s = l.getBlockState(q);
            if (!s.isSolidRender(l, q)) continue;
            if (l.getBlockState(q.below()).isAir()) {
                // del techo: gotas que caen
                l.addParticle(gota, q.getX() + 0.1 + r.nextDouble() * 0.8, q.getY() - 0.04, q.getZ() + 0.1 + r.nextDouble() * 0.8, 0, 0, 0);
                continue;
            }
            Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            if (!l.getBlockState(q.relative(dir)).isAir()) continue;
            // por la pared: resbala hacia abajo pegada a la cara
            double px = q.getX() + 0.5 + dir.getStepX() * 0.53 + (dir.getStepX() == 0 ? (r.nextDouble() - 0.5) * 0.85 : 0);
            double pz = q.getZ() + 0.5 + dir.getStepZ() * 0.53 + (dir.getStepZ() == 0 ? (r.nextDouble() - 0.5) * 0.85 : 0);
            l.addParticle(resbala, px, q.getY() + 0.25 + r.nextDouble() * 0.7, pz, 0, -0.4, 0);
        }
        if (reloj >= proxGoteo) {
            proxGoteo = reloj + (etapa >= 3 ? 50 : 90) + r.nextInt(90);
            MiedoCliente.sonar(mc, EfectosRegistro.MIEDO_GOTEO.get(), MiedoCliente.alrededor(p, r, 1.5, 4.5, 0F, 360F), 0.45F, 0.85F + r.nextFloat() * 0.3F);
        }
    }

    // ------------------------------------------------------------------ puertas que se abren solas
    private static void puertas(Minecraft mc, ClientLevel l, LocalPlayer p, RandomSource r, int reloj) {
        if (proxPuerta < 0) proxPuerta = reloj + 100;
        if (reloj < proxPuerta || puerta != null || reloj % 5 != 0) return;
        HitResult hr = p.pick(24.0, 1F, false);
        if (!(hr instanceof BlockHitResult bh) || hr.getType() != HitResult.Type.BLOCK) return;
        BlockPos q = bh.getBlockPos();
        BlockState s = l.getBlockState(q);
        SoundEvent abre, cierra;
        if (s.is(BlockTags.DOORS)) {
            boolean hierro = s.is(Blocks.IRON_DOOR);
            abre = hierro ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.WOODEN_DOOR_OPEN;
            cierra = hierro ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.WOODEN_DOOR_CLOSE;
        } else if (s.is(BlockTags.TRAPDOORS)) {
            boolean hierro = s.is(Blocks.IRON_TRAPDOOR);
            abre = hierro ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.WOODEN_TRAPDOOR_OPEN;
            cierra = hierro ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.WOODEN_TRAPDOOR_CLOSE;
        } else if (s.is(BlockTags.FENCE_GATES)) {
            abre = SoundEvents.FENCE_GATE_OPEN;
            cierra = SoundEvents.FENCE_GATE_CLOSE;
        } else return;
        double d = Math.sqrt(p.getEyePosition().distanceToSqr(Vec3.atCenterOf(q)));
        if (d < 2.5 || r.nextInt(3) != 0) return;
        proxPuerta = reloj + 240 + r.nextInt(240);
        Vec3 c = Vec3.atCenterOf(q);
        MiedoCliente.sonar(mc, abre, c, 1F, 0.75F + r.nextFloat() * 0.15F);
        int cuando = 30 + r.nextInt(40);
        // si está lejos (no la vas a cruzar), también se ve abierta un momento: solo en tu cliente
        if (d > 5 && s.hasProperty(BlockStateProperties.OPEN) && !s.getValue(BlockStateProperties.OPEN)) {
            BlockPos abajo = q, arriba = null;
            BlockState viejoA = null, nuevoA = null;
            if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                boolean esArriba = s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
                abajo = esArriba ? q.below() : q;
                arriba = esArriba ? q : q.above();
                viejoA = l.getBlockState(arriba);
                if (!viejoA.is(s.getBlock()) || !viejoA.hasProperty(BlockStateProperties.OPEN)) arriba = null;
                else nuevoA = viejoA.setValue(BlockStateProperties.OPEN, true);
            }
            BlockState viejo = l.getBlockState(abajo);
            if (viejo.is(s.getBlock()) && viejo.hasProperty(BlockStateProperties.OPEN)) {
                BlockState nuevo = viejo.setValue(BlockStateProperties.OPEN, true);
                // 2 | 16: solo avisar al render, sin actualizar formas vecinas
                l.setBlock(abajo, nuevo, 18);
                if (arriba != null) l.setBlock(arriba, nuevoA, 18);
                puerta = new Puerta(abajo, viejo, nuevo, arriba, viejoA, nuevoA, reloj + cuando, cierra);
                return;
            }
        }
        MiedoCliente.despues(cuando, cierra, c, 0.9F, 0.8F);
    }

    private static void restaurarPuertaSiToca(int reloj) {
        if (puerta != null && reloj >= puerta.hasta()) {
            Minecraft mc = Minecraft.getInstance();
            MiedoCliente.sonar(mc, puerta.cierra(), Vec3.atCenterOf(puerta.abajo()), 0.9F, 0.8F);
            restaurarPuerta();
        }
    }

    /** Vuelve a poner la puerta como estaba (solo si nadie la ha tocado mientras: si el servidor la cambió, manda él). */
    private static void restaurarPuerta() {
        Puerta pu = puerta;
        puerta = null;
        ClientLevel l = Minecraft.getInstance().level;
        if (pu == null || l == null) return;
        if (l.getBlockState(pu.abajo()) == pu.nuevo()) l.setBlock(pu.abajo(), pu.viejo(), 18);
        if (pu.arriba() != null && l.getBlockState(pu.arriba()) == pu.nuevoA()) l.setBlock(pu.arriba(), pu.viejoA(), 18);
    }

    // ------------------------------------------------------------------ tu doble
    private static void doble(Minecraft mc, ClientLevel l, LocalPlayer p, RandomSource r, int etapa, int reloj, float ritmo) {
        if (etapa < 2) return;
        if (doble == null) {
            if (proxDoble < 0) proxDoble = reloj + 200 + r.nextInt(300);
            if (reloj >= proxDoble) {
                proxDoble = reloj + (int) ((700 + r.nextInt(600)) / ritmo);
                aparecerDoble(l, p, r);
            }
            return;
        }
        Doble d = doble;
        d.vida++;
        d.tickCount++;
        d.yBodyRotO = d.yBodyRot;
        d.yHeadRotO = d.yHeadRot;
        d.yRotO = d.getYRot();
        d.xRotO = d.getXRot();
        double dx = p.getX() - d.x, dz = p.getZ() - d.z, dist = Math.sqrt(dx * dx + dz * dz);
        Vec3 ojo = p.getEyePosition();
        Vec3 hacia = new Vec3(d.x, d.y + 1.6, d.z).subtract(ojo).normalize();
        double dot = p.getViewVector(1F).dot(hacia);
        if (dot > 0.985) d.mirada++;
        else d.mirada = Math.max(0, d.mirada - 1);
        d.sinVer = dot < 0.3 ? d.sinVer + 1 : 0;
        // si te fijas, ya no está (sin humo, sin ruido... casi)
        if (d.mirada > 14 || dist < 9 || (p.isScoping() && dot > 0.95) || d.vida > 700) {
            if (r.nextInt(2) == 0)
                MiedoCliente.sonar(mc, EfectosRegistro.MIEDO_SUSURROS.get(), new Vec3(d.x, d.y + 1.6, d.z), 0.5F, 0.6F);
            doble = null;
            return;
        }
        // cuando no miras, a veces se ha acercado
        if (d.sinVer > 50 && dist > 16 && r.nextInt(3) == 0) {
            d.sinVer = 0;
            double nx = d.x + dx * 0.3, nz = d.z + dz * 0.3;
            double ny = MiedoCliente.suelo(l, nx, d.y, nz);
            if (!Double.isNaN(ny)) {
                d.x = nx;
                d.z = nz;
                d.y = ny;
                d.xo = d.x;
                d.yo = d.y;
                d.zo = d.z;
            }
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        d.setYRot(yaw);
        d.yBodyRot = yaw;
        d.yHeadRot = yaw;
        double dy = p.getEyeY() - (d.y + 1.62);
        d.setXRot(Mth.clamp((float) -(Mth.atan2(dy, Math.max(dist, 0.5)) * Mth.RAD_TO_DEG), -50F, 50F));
        d.setPos(d.x, d.y, d.z);
        d.xo = d.x;
        d.yo = d.y;
        d.zo = d.z;
    }

    private static void aparecerDoble(ClientLevel l, LocalPlayer p, RandomSource r) {
        // delante (lejos, para que lo descubras) o detrás (para cuando te gires)
        boolean delante = r.nextBoolean();
        for (int i = 0; i < 8; i++) {
            float a = p.getYRot() + (delante ? -35F + r.nextFloat() * 70F : 150F + r.nextFloat() * 60F);
            double dd = 20 + r.nextDouble() * 10;
            float rad = a * Mth.DEG_TO_RAD;
            double fx = p.getX() - Mth.sin(rad) * dd, fz = p.getZ() + Mth.cos(rad) * dd;
            double fy = MiedoCliente.suelo(l, fx, p.getY(), fz);
            if (Double.isNaN(fy)) continue;
            // tiene que verse desde donde estás
            BlockHitResult bh = l.clip(new ClipContext(p.getEyePosition(), new Vec3(fx, fy + 1.6, fz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (bh.getType() != HitResult.Type.MISS) continue;
            Doble d;
            try {
                d = crearDoble(l, p, -5000 - (MiedoCliente.reloj() & 0xFFF));
            } catch (Exception ex) {
                Miscelanea.LOGGER.warn("[miscelanea] No se pudo crear el doble del miedo", ex);
                proxDoble = Integer.MAX_VALUE;
                return;
            }
            d.x = fx;
            d.y = fy;
            d.z = fz;
            d.setPos(fx, fy, fz);
            d.xo = fx;
            d.yo = fy;
            d.zo = fz;
            doble = d;
            return;
        }
    }

    /** Una copia de ti (tu skin, tus capas, lo que llevas en la mano) que solo se dibuja. */
    static Doble crearDoble(ClientLevel l, LocalPlayer p, int id) {
        byte partes = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) if (p.isModelPartShown(part)) partes |= (byte) part.getMask();
        Doble d = new Doble(l, p.getGameProfile(), partes);
        d.setId(id);
        d.setItemSlot(EquipmentSlot.MAINHAND, p.getMainHandItem().copy());
        return d;
    }

    // ------------------------------------------------------------------ la silueta de reojo
    private static void silueta(Minecraft mc, ClientLevel l, LocalPlayer p, RandomSource r, int etapa, int reloj, float ritmo) {
        if (etapa < 2) return;
        if (silueta == null) {
            if (proxSilueta < 0) proxSilueta = reloj + 240 + r.nextInt(300);
            if (reloj < proxSilueta || MiedoSusto.enCurso()) return;
            proxSilueta = reloj + (int) ((520 + r.nextInt(420)) / ritmo);
            for (int i = 0; i < 8; i++) {
                // en el borde de la vista, a un lado
                float a = p.getYRot() + (r.nextBoolean() ? 1F : -1F) * (38F + r.nextFloat() * 14F);
                double d = 16 + r.nextDouble() * 12;
                float rad = a * Mth.DEG_TO_RAD;
                double fx = p.getX() - Mth.sin(rad) * d, fz = p.getZ() + Mth.cos(rad) * d;
                double fy = MiedoCliente.suelo(l, fx, p.getY(), fz);
                if (Double.isNaN(fy)) continue;
                BlockHitResult bh = l.clip(new ClipContext(p.getEyePosition(), new Vec3(fx, fy + 2.0, fz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                if (bh.getType() != HitResult.Type.MISS) continue;
                try {
                    silueta = MiedoSusto.espantapajaros(l);
                } catch (Exception ex) {
                    proxSilueta = Integer.MAX_VALUE;
                    return;
                }
                sx = fx;
                sy = fy;
                sz = fz;
                siluetaVida = 0;
                break;
            }
            return;
        }
        siluetaVida++;
        LivingEntity s = silueta;
        s.tickCount++;
        double dx = p.getX() - sx, dz = p.getZ() - sz, dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        s.setYRot(yaw);
        s.yRotO = yaw;
        s.yBodyRot = s.yBodyRotO = yaw;
        s.yHeadRot = s.yHeadRotO = yaw;
        s.setPos(sx, sy, sz);
        s.xo = sx;
        s.yo = sy;
        s.zo = sz;
        Vec3 hacia = new Vec3(sx, sy + 2.0, sz).subtract(p.getEyePosition()).normalize();
        // mirada directa (a menos de ~22°), muy cerca o mucho rato: ya no está
        if (p.getViewVector(1F).dot(hacia) > 0.927 || dist < 9 || siluetaVida > 360 || MiedoSusto.enCurso()) {
            if (siluetaVida > 5 && r.nextInt(3) == 0)
                MiedoCliente.sonar(mc, EfectosRegistro.MIEDO_SUSURROS.get(), new Vec3(sx, sy + 2, sz), 0.35F, 0.6F);
            silueta = null;
        }
    }

    @SubscribeEvent
    public static void dibujarSilueta(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || silueta == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        // una sombra recortada contra el fondo
        MultiBufferSource sombra = rt -> new MiedoCliente.Tinte(bs.getBuffer(rt), 0.1F, 0.12F, 0.1F);
        EntityRenderDispatcher rd = mc.getEntityRenderDispatcher();
        rd.setRenderShadow(false);
        try {
            rd.render(silueta, sx - cam.x, sy - cam.y, sz - cam.z, silueta.getYRot(), e.getPartialTick(), ps, sombra, 0xF000F0);
        } catch (Exception ex) {
            silueta = null;
            proxSilueta = Integer.MAX_VALUE;
        } finally {
            rd.setRenderShadow(true);
            bs.endBatch();
        }
    }

    @SubscribeEvent
    public static void dibujarDoble(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || doble == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Doble d = doble;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        // algo más oscuro de lo que debería, como si no le diera la luz
        MultiBufferSource apagado = rt -> new MiedoCliente.Tinte(bs.getBuffer(rt), 0.62F, 0.6F, 0.62F);
        EntityRenderDispatcher rd = mc.getEntityRenderDispatcher();
        int luz = LevelRenderer.getLightColor(mc.level, BlockPos.containing(d.x, d.y + 1.0, d.z));
        rd.setRenderShadow(false);
        try {
            rd.render(d, d.x - cam.x, d.y - cam.y, d.z - cam.z, d.getYRot(), pt, ps, apagado, luz);
        } catch (Exception ex) {
            doble = null;
            proxDoble = Integer.MAX_VALUE;
        } finally {
            rd.setRenderShadow(true);
            bs.endBatch();
        }
    }

    /** Tu doble no lleva nombre encima. */
    @SubscribeEvent
    public static void nombre(RenderNameTagEvent e) {
        if (doble != null && e.getEntity() == doble) e.setResult(Event.Result.DENY);
    }

    // ------------------------------------------------------------------ manos fantasma
    private static void manos(Minecraft mc, RandomSource r, int etapa, int reloj, float ritmo) {
        MANOS.removeIf(m -> reloj - m.ini() > m.dur());
        if (etapa < 2) return;
        if (proxManos < 0) proxManos = reloj + 140 + r.nextInt(200);
        if (reloj < proxManos) return;
        proxManos = reloj + (int) ((320 + r.nextInt(320)) / ritmo);
        int n = etapa >= 3 && r.nextInt(2) == 0 ? 2 : 1;
        int primero = r.nextInt(3);
        for (int i = 0; i < n; i++) {
            int lado = (primero + i) % 3;
            MANOS.add(new Mano(lado, reloj + i * 6, 70 + r.nextInt(40), 0.3F + r.nextFloat() * 0.4F, r.nextBoolean()));
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_MANOS.get(), 0.9F + r.nextFloat() * 0.2F, 0.75F));
    }

    /** Las manos entran despacio desde el borde (0,4 del tiempo), se quedan tanteando y se retiran. */
    static void dibujarManos(GuiGraphics g, int w, int h, float pt) {
        if (MANOS.isEmpty()) return;
        int reloj = MiedoCliente.reloj();
        int mh = Math.round(h * 0.8F), mw = mh / 2;
        for (Mano m : MANOS) {
            float tt = reloj - m.ini() + pt;
            if (tt < 0) continue;
            float s = tt / m.dur();
            float sale = s < 0.4F ? s / 0.4F : s < 0.7F ? 1F : Math.max(0F, 1F - (s - 0.7F) / 0.3F);
            sale = sale * sale * (3F - 2F * sale);
            if (sale <= 0.001F) continue;
            // cuánto entra la punta de los dedos (tanteando un poco)
            float alcance = mh * (0.34F * sale + 0.025F * Mth.sin(tt * 0.12F) * sale);
            float giro = Mth.sin(tt * 0.09F + m.pos() * 9F) * 6F;
            g.pose().pushPose();
            switch (m.lado()) {
                case 0 -> {
                    g.pose().translate(-mh + alcance, h * m.pos(), 0);
                    g.pose().mulPose(Axis.ZP.rotationDegrees(90F + giro));
                }
                case 1 -> {
                    g.pose().translate(w + mh - alcance, h * m.pos(), 0);
                    g.pose().mulPose(Axis.ZP.rotationDegrees(-90F + giro));
                }
                default -> {
                    g.pose().translate(w * m.pos(), h + mh - alcance, 0);
                    g.pose().mulPose(Axis.ZP.rotationDegrees(giro));
                }
            }
            // la mano izquierda es la derecha en espejo (sin descartar caras, que al voltearla quedan del revés)
            if (m.espejo()) {
                g.pose().scale(-1F, 1F, 1F);
                RenderSystem.disableCull();
            }
            g.setColor(1F, 1F, 1F, 0.8F * sale);
            g.blit(MANO, -mw / 2, -mh, 0, 0, mw, mh, mw, mh);
            if (m.espejo()) RenderSystem.enableCull();
            g.pose().popPose();
        }
        g.setColor(1F, 1F, 1F, 1F);
    }
}

package com.miscelanea.client.tortura.efectos;

import com.miscelanea.Miscelanea;
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
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
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
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Toxina del miedo (Espantapájaros, Batman: Arkham) en el cliente del envenenado. Todo es local y no hace daño:
 * <ul>
 *   <li>La vista se mece (balanceo, guiñadas, el campo de visión respira) y, si la tarjeta lo permite, un shader propio
 *       (shaders/post/miedo_N.json) ondula la imagen, separa los colores, la desatura y la tiñe de verde; si no carga,
 *       se aproxima con una capa grisácea.</li>
 *   <li>Viñeta verde que late con el corazón (latido audible), susurros que salen de alrededor, un zumbido grave y
 *       sonidos falsos (un creeper que sisea detrás, pasos que se acercan, gruñidos...).</li>
 *   <li>Mobs falsos (siluetas oscuras de zombis, creepers, esqueletos y endermen) que aparecen cerca, se acercan, parpadean
 *       y se desvanecen al llegar o al dejar de mirarlos; el creeper solo avanza cuando no lo miras y el enderman chilla
 *       y se esfuma si lo miras fijamente.</li>
 *   <li>De vez en cuando, la cara del Espantapájaros ocupa la pantalla un instante (entra y sale con fundido: nada de
 *       destellos rápidos; ningún efecto de pantalla completa pasa de ~1,5 Hz).</li>
 * </ul>
 * La intensidad sube en 2,5 s, baja en los últimos 4 s y se apaga sola al quitar el efecto (leche, morir...).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class MiedoCliente {
    private MiedoCliente() {}

    static final ResourceLocation VINETA = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/tortura/vineta_miedo.png");
    static final ResourceLocation CARA = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/tortura/espantapajaros.png");
    private static final ResourceLocation[] POST = {
            new ResourceLocation(Miscelanea.MOD_ID, "shaders/post/miedo_1.json"),
            new ResourceLocation(Miscelanea.MOD_ID, "shaders/post/miedo_2.json"),
            new ResourceLocation(Miscelanea.MOD_ID, "shaders/post/miedo_3.json")};
    private static final int CARA_TICKS = 30;

    private static float k, kAntes;
    private static int activo, tick;
    private static int proxSusurro, proxZumbido, proxFalso, proxFantasma, proxCara, ultimoLatido;
    private static int caraT = -1, idFalso;
    private static ClientLevel nivelVisto;
    private static final List<Fantasma> FANTASMAS = new ArrayList<>();
    private static final List<Pendiente> PENDIENTES = new ArrayList<>();
    // shader
    private static final PostChain[] CADENAS = new PostChain[3];
    private static final int[][] TAM = new int[3][2];
    private static boolean shaderFallo, shaderEsteFotograma;

    private record Pendiente(int tick, SoundEvent s, double x, double y, double z, float vol, float tono) {}

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

    public static float intensidad(float pt) {
        return Mth.lerp(pt, kAntes, k);
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
            return;
        }
        if (mc.level != nivelVisto) {
            limpiar();
            nivelVisto = mc.level;
        }
        if (mc.isPaused()) return;
        kAntes = k;
        MobEffectInstance ef = p.getEffect(EfectosRegistro.MIEDO.get());
        if (ef == null || !p.isAlive()) {
            if (activo > 0) limpiar();
            k = Math.max(0F, k - 0.06F);
            if (k <= 0F) cerrarShaders();
            return;
        }
        activo++;
        tick++;
        int dur = ef.isInfiniteDuration() ? 100000 : ef.getDuration();
        float objetivo = Mth.clamp(activo / 50F, 0F, 1F) * Mth.clamp(dur / 80F, 0F, 1F);
        k += (objetivo - k) * 0.12F;
        RandomSource r = p.getRandom();
        if (activo == 1) {
            proxSusurro = 30 + r.nextInt(40);
            proxZumbido = 10;
            proxFalso = 140 + r.nextInt(80);
            proxFantasma = 60 + r.nextInt(40);
            proxCara = 140 + r.nextInt(120);
        }
        sonidos(mc, p, r);
        fantasmas(mc, p, r);
        if (caraT >= 0 && ++caraT > CARA_TICKS) caraT = -1;
        if (activo >= proxCara && k > 0.6F && dur > 50) {
            caraT = 0;
            proxCara = activo + 300 + r.nextInt(220);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_SUSTO.get(), 0.95F + r.nextFloat() * 0.1F, 0.9F));
        }
    }

    private static void limpiar() {
        activo = 0;
        caraT = -1;
        FANTASMAS.clear();
        PENDIENTES.clear();
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

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        limpiar();
        k = kAntes = 0;
        cerrarShaders();
        shaderFallo = false;
    }

    // ------------------------------------------------------------------ sonidos
    private static Vec3 alrededor(LocalPlayer p, RandomSource r, double d0, double d1, float angDesde, float angHasta) {
        float yaw = p.getYRot() + angDesde + r.nextFloat() * (angHasta - angDesde);
        double d = d0 + r.nextDouble() * (d1 - d0);
        float rad = yaw * Mth.DEG_TO_RAD;
        return new Vec3(p.getX() - Mth.sin(rad) * d, p.getEyeY() - 0.2 + r.nextGaussian() * 0.2, p.getZ() + Mth.cos(rad) * d);
    }

    private static void sonar(Minecraft mc, SoundEvent s, Vec3 v, float vol, float tono) {
        mc.getSoundManager().play(new SimpleSoundInstance(s, SoundSource.HOSTILE, vol, tono, RandomSource.create(), v.x, v.y, v.z));
    }

    private static void sonidos(Minecraft mc, LocalPlayer p, RandomSource r) {
        // latido: más rápido cuanto más fuerte (0,8 → 1,3 por segundo)
        int periodo = Math.round(Mth.lerp(k, 26F, 15F));
        if (tick - ultimoLatido >= periodo) {
            ultimoLatido = tick;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_LATIDO.get(), 1F, 0.25F + 0.55F * k));
        }
        if (activo >= proxZumbido) {
            proxZumbido = activo + 170 + r.nextInt(70);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(EfectosRegistro.MIEDO_ZUMBIDO.get(), 0.9F + r.nextFloat() * 0.15F, 0.45F * k));
        }
        if (activo >= proxSusurro) {
            proxSusurro = activo + 80 + r.nextInt(110);
            sonar(mc, EfectosRegistro.MIEDO_SUSURROS.get(), alrededor(p, r, 1.2, 2.6, 60F, 300F), 0.6F + 0.4F * k, 0.85F + r.nextFloat() * 0.25F);
        }
        if (activo >= proxFalso && k > 0.4F) {
            proxFalso = activo + 120 + r.nextInt(150);
            switch (r.nextInt(6)) {
                case 0 -> sonar(mc, SoundEvents.CREEPER_PRIMED, alrededor(p, r, 1.4, 2.2, 150F, 210F), 1F, 0.5F);
                case 1 -> {
                    // pasos que se acercan por detrás (con el sonido del suelo que pisas)
                    BlockState bajo = p.level().getBlockState(p.blockPosition().below());
                    SoundEvent paso = bajo.isAir() ? SoundEvents.STONE_STEP : bajo.getSoundType().getStepSound();
                    float ang = p.getYRot() + 160F + r.nextFloat() * 40F;
                    for (int i = 0; i < 5; i++) {
                        double d = 4.5 - i * 0.7;
                        float rad = ang * Mth.DEG_TO_RAD;
                        PENDIENTES.add(new Pendiente(tick + i * 8, paso, p.getX() - Mth.sin(rad) * d, p.getY(), p.getZ() + Mth.cos(rad) * d,
                                0.35F + i * 0.06F, 0.95F + r.nextFloat() * 0.1F));
                    }
                }
                case 2 -> sonar(mc, SoundEvents.ZOMBIE_AMBIENT, alrededor(p, r, 5, 8, 0F, 360F), 0.9F, 0.8F + r.nextFloat() * 0.2F);
                case 3 -> sonar(mc, SoundEvents.ENDERMAN_AMBIENT, alrededor(p, r, 4, 7, 90F, 270F), 0.8F, 0.7F);
                case 4 -> sonar(mc, SoundEvents.SKELETON_AMBIENT, alrededor(p, r, 4, 7, 0F, 360F), 0.8F, 0.9F);
                default -> sonar(mc, SoundEvents.WOODEN_DOOR_OPEN, alrededor(p, r, 3, 5, 120F, 240F), 0.7F, 0.6F);
            }
        }
        for (Iterator<Pendiente> it = PENDIENTES.iterator(); it.hasNext(); ) {
            Pendiente pe = it.next();
            if (pe.tick() > tick) continue;
            it.remove();
            sonar(mc, pe.s(), new Vec3(pe.x(), pe.y(), pe.z()), pe.vol(), pe.tono());
        }
    }

    // ------------------------------------------------------------------ mobs falsos
    /** Altura del suelo cerca de (x, y, z), buscando de arriba abajo (NaN si no hay). */
    private static double suelo(ClientLevel l, double x, double y, double z) {
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
        if (activo >= proxFantasma && k > 0.35F) {
            proxFantasma = activo + 70 + r.nextInt(80);
            if (FANTASMAS.size() < 3) aparecer(l, p, r);
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
        if (f.tipo == Fantasma.ENDERMAN)
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

    /** Envoltorio que oscurece (y tiñe de verde) todo lo que se dibuja a través de él: siluetas. */
    private record Tinte(VertexConsumer d, float r, float g, float b) implements VertexConsumer {
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

    // ------------------------------------------------------------------ cámara
    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        float kk = intensidad((float) e.getPartialTick());
        if (kk <= 0.001F) return;
        float t = tick + (float) e.getPartialTick();
        e.setRoll(e.getRoll() + (Mth.sin(t * 0.071F) * 4.2F + Mth.sin(t * 0.23F + 1.3F) * 1.1F) * kk);
        e.setYaw(e.getYaw() + Mth.sin(t * 0.047F + 2F) * 1.6F * kk);
        e.setPitch(e.getPitch() + Mth.sin(t * 0.061F + 0.5F) * 1.3F * kk);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        float kk = intensidad((float) e.getPartialTick());
        if (kk <= 0.001F) return;
        float t = tick + (float) e.getPartialTick();
        float latido = (float) Math.exp(-(tick - ultimoLatido + e.getPartialTick()) * 0.3);
        e.setFOV(e.getFOV() * (1.0 + (0.07 * Mth.sin(t * 0.09F) + 0.035 * latido) * kk));
    }

    // ------------------------------------------------------------------ shader (antes del HUD, como los de vanilla)
    @SubscribeEvent
    public static void antesHud(RenderGuiEvent.Pre e) {
        shaderEsteFotograma = false;
        float kk = intensidad(e.getPartialTick());
        if (kk < 0.05F || shaderFallo) return;
        Minecraft mc = Minecraft.getInstance();
        int nivel = kk < 0.4F ? 0 : kk < 0.75F ? 1 : 2;
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
            Miscelanea.LOGGER.warn("[miscelanea] No se pudo usar el shader del miedo; se usa la capa de respaldo", ex);
        }
    }

    // ------------------------------------------------------------------ HUD
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        float kk = intensidad(pt);
        if (kk < 0.01F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        float t = tick + pt;
        // sin shader: velo gris verdoso (desatura un poco y apaga el contraste)
        if (!shaderEsteFotograma) g.fill(0, 0, w, h, ((int) (0.26F * kk * 255) << 24) | 0x4E5E48);
        // viñeta verde que late con el corazón (suave: sube y baja, no destella)
        float latido = (float) Math.exp(-(tick - ultimoLatido + pt) * 0.22F);
        float a = Mth.clamp(0.42F * kk + 0.28F * kk * latido, 0F, 0.92F);
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
        g.setColor(1F, 1F, 1F, 1F);
        // la cara del Espantapájaros: fundido de entrada (0,2 s), se queda y se va (0,8 s); se acerca un poco
        if (caraT >= 0) {
            float ct = caraT + pt;
            float env = ct < 4F ? ct / 4F : ct < 14F ? 1F : Math.max(0F, 1F - (ct - 14F) / (CARA_TICKS - 14F));
            env = env * env * (3F - 2F * env);
            g.fill(0, 0, w, h, ((int) (env * 0.5F * 255) << 24));
            float s = h * (1.0F + 0.14F * ct / CARA_TICKS);
            float jx = Mth.sin(ct * 1.7F) * 2.5F * env, jy = Mth.sin(ct * 2.3F + 1F) * 2F * env;
            int cs = Math.round(s);
            g.setColor(1F, 1F, 1F, env * 0.92F);
            g.blit(CARA, Math.round(w / 2F - s / 2F + jx), Math.round(h / 2F - s / 2F + jy), 0, 0, cs, cs, cs, cs);
            g.setColor(1F, 1F, 1F, 1F);
        }
        RenderSystem.depthMask(true);
    }
}

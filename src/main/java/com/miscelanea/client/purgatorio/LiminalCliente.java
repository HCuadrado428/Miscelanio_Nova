package com.miscelanea.client.purgatorio;

import com.miscelanea.Miscelanea;
import com.miscelanea.purgatorio.Purgatorio;
import com.miscelanea.purgatorio.PurgatorioGenerador;
import com.miscelanea.purgatorio.liminal.AtrezoBE;
import com.miscelanea.purgatorio.liminal.Bloques;
import com.miscelanea.purgatorio.liminal.LiminalRegistro;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * El espacio liminal en el cliente: el dibujo del atrezo con GeckoLib (escalera mecánica, maniquí, ventilador y
 * flotador), los colores (con tinte) de los coches, las sillas y las máquinas, y el eco de los pasos en las salas
 * grandes y vacías (piscina, garaje, centro comercial, colegio): dos copias más bajas del mismo sonido, con retraso.
 */
public final class LiminalCliente {
    private LiminalCliente() {}

    /** Colores de los coches: rojo, azul marino, blanco, negro, gris plata, amarillo taxi, azul claro y verde botella. */
    static final int[] COCHES = {0xB0201C, 0x1E3A78, 0xE8E8E4, 0x242428, 0x9EA2A8, 0xE0B020, 0x5C9CCC, 0x1F5A3A};
    static final int[] SILLAS = {0x2F5FA8, 0xD8742A, 0x3D8C5A, 0x8E8E94};
    static final int[] MAQUINAS = {0xC42A26, 0x2A58B8};

    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registro {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerBlockEntityRenderer(LiminalRegistro.ATREZO_BE.get(), AtrezoRenderer::new);
        }

        @SubscribeEvent
        public static void colores(RegisterColorHandlersEvent.Block e) {
            e.register((s, l, p, i) -> i == 0 ? COCHES[s.getValue(Bloques.COLOR8)] : -1, LiminalRegistro.COCHE.get());
            e.register((s, l, p, i) -> i == 0 ? SILLAS[s.getValue(Bloques.COLOR4)] : -1, LiminalRegistro.SILLA_ESPERA.get());
            e.register((s, l, p, i) -> i == 0 ? MAQUINAS[s.getValue(Bloques.COLOR2)] : -1, LiminalRegistro.MAQUINA.get());
        }

        @SubscribeEvent
        public static void coloresObjeto(RegisterColorHandlersEvent.Item e) {
            e.register((st, i) -> i == 0 ? SILLAS[0] : -1, LiminalRegistro.SILLA_ESPERA.get());
            e.register((st, i) -> i == 0 ? MAQUINAS[0] : -1, LiminalRegistro.MAQUINA.get());
        }
    }

    // ------------------------------------------------------------------ GeckoLib
    static final class Modelo extends GeoModel<AtrezoBE> {
        private static final Map<String, ResourceLocation[]> RL = new HashMap<>();

        private static ResourceLocation[] de(AtrezoBE a) {
            return RL.computeIfAbsent(a.tipo(), t -> new ResourceLocation[]{
                    new ResourceLocation(Miscelanea.MOD_ID, "geo/block/" + t + ".geo.json"),
                    new ResourceLocation(Miscelanea.MOD_ID, "textures/block/liminal/" + t + ".png"),
                    new ResourceLocation(Miscelanea.MOD_ID, "animations/block/" + t + ".animation.json")});
        }

        @Override
        public ResourceLocation getModelResource(AtrezoBE a) {
            return de(a)[0];
        }

        @Override
        public ResourceLocation getTextureResource(AtrezoBE a) {
            return de(a)[1];
        }

        @Override
        public ResourceLocation getAnimationResource(AtrezoBE a) {
            return de(a)[2];
        }

        @Override
        public RenderType getRenderType(AtrezoBE a, ResourceLocation tex) {
            // la escalera lleva cristal (translúcido); lo demás, recortado
            return a.getBlockState().getBlock() instanceof AtrezoBE.Motor ? RenderType.entityTranslucent(tex) : RenderType.entityCutoutNoCull(tex);
        }
    }

    public static class AtrezoRenderer extends GeoBlockRenderer<AtrezoBE> {
        public AtrezoRenderer(BlockEntityRendererProvider.Context c) {
            super(new Modelo());
        }

        @Override
        protected void rotateBlock(Direction facing, PoseStack ps) {
            AtrezoBE be = getAnimatable();
            if (be != null && be.getBlockState().getBlock() instanceof AtrezoBE.Maniqui) {
                girarManiqui(be);
                ps.mulPose(Axis.YP.rotationDegrees(be.giro));
                return;
            }
            super.rotateBlock(facing, ps);
        }

        @Override
        public void render(AtrezoBE be, float pt, PoseStack ps, net.minecraft.client.renderer.MultiBufferSource buf, int luz, int overlay) {
            dibujados++;
            super.render(be, pt, ps, buf, luz, overlay);
        }

        @Override
        public int getViewDistance() {
            return 40;
        }

        @Override
        public boolean shouldRenderOffScreen(AtrezoBE be) {
            return be.getBlockState().getBlock() instanceof AtrezoBE.Motor;
        }
    }

    /** Ángulo (como el de GeckoLib: norte 0, oeste 90, sur 180, este 270) para mirar en la dirección (dx, dz). */
    static float angulo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, -dz));
    }

    /**
     * El maniquí: mientras lo miras no se mueve; en cuanto no está a la vista (y estás cerca), se gira de golpe hacia ti.
     * Así, cada vez que vuelves la vista, te está mirando.
     */
    static void girarManiqui(AtrezoBE be) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        var p = be.getBlockPos();
        if (Float.isNaN(be.giro)) be.giro = 180 - be.getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot();
        double dx = cam.getPosition().x - (p.getX() + 0.5), dy = cam.getPosition().y - (p.getY() + 1.5), dz = cam.getPosition().z - (p.getZ() + 0.5);
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        var mira = cam.getLookVector();
        double cos = d < 1e-3 ? 1 : -(mira.x() * dx + mira.y() * dy + mira.z() * dz) / d;
        be.mirado = cos > 0.5;
        if (!be.mirado && d < 22) {
            float a = angulo(dx, dz);
            float dif = Math.abs(((a - be.giro) % 360 + 540) % 360 - 180);
            if (dif > 4) {
                be.giro = a;
                be.giros++;
            }
        }
    }

    // ------------------------------------------------------------------ eco de los pasos
    /** Copia (más baja y con retraso) de un sonido: no se le vuelve a hacer eco. */
    static final class Eco extends SimpleSoundInstance {
        Eco(ResourceLocation rl, net.minecraft.sounds.SoundSource src, float v, float p, int retraso, double x, double y, double z) {
            super(rl, src, v, p, RandomSource.create(), false, retraso, SoundInstance.Attenuation.LINEAR, x, y, z, false);
        }
    }

    /** Cuánto eco hay en cada zona (0 = ninguno: la moqueta se lo come). */
    static float eco(PurgatorioGenerador.Zona z) {
        return switch (z) {
            case PISCINA -> 1.0F;
            case APARCAMIENTO -> 0.85F;
            case CENTRO -> 0.75F;
            case COLEGIO -> 0.5F;
            default -> 0F;
        };
    }

    /** Para la prueba: cuántas veces se ha dibujado algún atrezo con GeckoLib. */
    public static long dibujados;

    /** Para la prueba: cuántos ecos han sonado. */
    public static int ecos;

    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
    public static final class Sonidos {
        @SubscribeEvent
        public static void sonido(PlaySoundEvent e) {
            SoundInstance s = e.getSound();
            if (s == null || s instanceof Eco) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || !Purgatorio.en(mc.level)) return;
            String n = s.getLocation().getPath();
            if (!(n.endsWith(".step") || n.endsWith(".fall") || n.endsWith("_close") || n.endsWith("_open") || n.endsWith(".land"))) return;
            if (mc.player.distanceToSqr(s.getX(), s.getY(), s.getZ()) > 36) return;
            float k = eco(PurgatorioGenerador.zonaEn(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            if (k <= 0) return;
            var sm = mc.getSoundManager();
            sm.play(new Eco(s.getLocation(), s.getSource(), 0.30F * k, 0.96F, 4, s.getX(), s.getY() + 1, s.getZ()));
            sm.play(new Eco(s.getLocation(), s.getSource(), 0.14F * k, 0.93F, 9, s.getX(), s.getY() + 1, s.getZ()));
            ecos++;
        }
    }
}

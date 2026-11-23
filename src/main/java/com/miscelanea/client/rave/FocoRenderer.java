package com.miscelanea.client.rave;

import com.miscelanea.Miscelanea;
import com.miscelanea.muebles.Muebles;
import com.miscelanea.rave.FocoBlockEntity;
import com.miscelanea.rave.LucesRave;
import com.miscelanea.rave.Neon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * El foco móvil: modelo de GeckoLib (base, horquilla que gira y cabeza que se inclina) y, encendido, un haz de luz de color
 * que sale de la lente y deja una mancha donde da. Colgado del techo se dibuja boca abajo.
 */
public class FocoRenderer implements net.minecraft.client.renderer.blockentity.BlockEntityRenderer<FocoBlockEntity> {
    /** El modelo de GeckoLib (este renderizador lo dibuja y luego añade la luz). */
    private final GeoBlockRenderer<FocoBlockEntity> geo;
    private static final float LARGO = 22F;
    /** Altura del eje de la cabeza y distancia del eje a la lente (en bloques), como en el modelo. */
    private static final float EJE = 8F / 16F, LENTE = 4.6F / 16F;

    public FocoRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx) {
        geo = new GeoBlockRenderer<>(new Modelo());
        geo.addRenderLayer(new AutoGlowingGeoLayer<>(geo));
    }

    static float pan(FocoBlockEntity e, float pt) {
        var s = e.getBlockState();
        if (!s.getValue(LucesRave.ENCENDIDA)) return 0;
        return LucesRave.panFoco(e.getLevel().getGameTime() + pt, s.getValue(LucesRave.MODO), LucesRave.semilla(e.getBlockPos()));
    }

    static float tilt(FocoBlockEntity e, float pt) {
        var s = e.getBlockState();
        if (!s.getValue(LucesRave.ENCENDIDA)) return 0.3F;
        return LucesRave.tiltFoco(e.getLevel().getGameTime() + pt, s.getValue(LucesRave.MODO), LucesRave.semilla(e.getBlockPos()));
    }

    static class Modelo extends GeoModel<FocoBlockEntity> {
        private static final ResourceLocation GEO = new ResourceLocation(Miscelanea.MOD_ID, "geo/block/foco_movil.geo.json"),
                TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/rave/foco_movil.png"),
                ANIM = new ResourceLocation(Miscelanea.MOD_ID, "animations/block/rave.animation.json");

        @Override
        public ResourceLocation getModelResource(FocoBlockEntity e) {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(FocoBlockEntity e) {
            return TEX;
        }

        @Override
        public ResourceLocation getAnimationResource(FocoBlockEntity e) {
            return ANIM;
        }

        @Override
        public void setCustomAnimations(FocoBlockEntity e, long id, AnimationState<FocoBlockEntity> st) {
            if (e.getLevel() == null) return;
            float pan = pan(e, st.getPartialTick()), tilt = tilt(e, st.getPartialTick());
            getBone("horquilla").ifPresent(b -> b.setRotY(pan));
            getBone("cabeza").ifPresent(b -> b.setRotX(tilt));
        }
    }

    @Override
    public boolean shouldRenderOffScreen(FocoBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(FocoBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        boolean colgado = be.getBlockState().getValue(LucesRave.COLGADO);
        ps.pushPose();
        if (colgado) {
            ps.translate(0.5, 0.5, 0.5);
            ps.mulPose(Axis.XP.rotationDegrees(180));
            ps.translate(-0.5, -0.5, -0.5);
        }
        geo.render(be, pt, ps, buf, luz, overlay);
        ps.popPose();
        var s = be.getBlockState();
        if (!s.getValue(LucesRave.ENCENDIDA) || be.getLevel() == null) return;
        // dirección de la cabeza: como en el modelo, primero la inclinación (eje X) y luego el giro de la horquilla (eje Y)
        float pan = pan(be, pt), tilt = tilt(be, pt);
        Vector3f d = new Vector3f(0, 1, 0).rotateX(tilt).rotateY(pan);
        Vector3f o = new Vector3f(0, EJE, 0).add(new Vector3f(d).mul(LENTE));
        if (colgado) {
            d.set(d.x, -d.y, -d.z);
            o.set(o.x, 1 - o.y, -o.z);
        }
        o.add(0.5F, 0, 0.5F);
        // hasta dónde llega: hasta el primer bloque
        BlockPos p = be.getBlockPos();
        Vec3 ini = new Vec3(p.getX() + o.x, p.getY() + o.y, p.getZ() + o.z);
        Vec3 fin = ini.add(d.x * LARGO, d.y * LARGO, d.z * LARGO);
        BlockHitResult r = be.getLevel().clip(new ClipContext(ini.add(d.x * 0.3, d.y * 0.3, d.z * 0.3), fin, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, Minecraft.getInstance().player));
        float largo = r.getType() == HitResult.Type.BLOCK ? (float) r.getLocation().distanceTo(ini) : LARGO;
        int rgb = Neon.rgb(s.getValue(Muebles.COLOR));
        float rFin = 0.08F + largo * 0.06F;
        Luces.haz(ps, buf, o, d, largo, 0.08F, rFin, rgb, 0.55F);
        if (r.getType() == HitResult.Type.BLOCK) {
            var n = r.getDirection().getNormal();
            Vec3 h = r.getLocation();
            Luces.mancha(ps, buf, new Vector3f((float) (h.x - p.getX()), (float) (h.y - p.getY()), (float) (h.z - p.getZ())),
                    new Vector3f(n.getX(), n.getY(), n.getZ()), rFin * 1.3F, rgb, 0.9F);
        }
    }
}

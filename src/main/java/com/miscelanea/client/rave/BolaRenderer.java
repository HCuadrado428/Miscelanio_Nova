package com.miscelanea.client.rave;

import com.miscelanea.Miscelanea;
import com.miscelanea.muebles.Muebles;
import com.miscelanea.rave.BolaBlockEntity;
import com.miscelanea.rave.LucesRave;
import com.miscelanea.rave.Neon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.HashMap;
import java.util.Map;

/**
 * La bola de discoteca: modelo de GeckoLib que gira y, encendida, reparte manchas de luz por suelo, paredes y techo (se lanzan
 * rayos desde la bola que giran con ella y donde chocan se dibuja una mancha).
 */
public class BolaRenderer implements net.minecraft.client.renderer.blockentity.BlockEntityRenderer<BolaBlockEntity> {
    /** El modelo de GeckoLib (este renderizador lo dibuja y luego añade la luz). */
    private final GeoBlockRenderer<BolaBlockEntity> geo;
    private static final int RAYOS = 48;
    private static final float ALCANCE = 20F;
    /** Centro de la bola (por debajo de su bloque) y su radio, en bloques, como en el modelo. */
    private static final float CENTRO = -8F / 16F, RADIO = 13.5F / 16F;
    private static final Vector3f[] DIRS = new Vector3f[RAYOS];

    static {
        // reparto de Fibonacci por la esfera, con más rayos hacia abajo y hacia los lados que hacia el techo
        for (int i = 0; i < RAYOS; i++) {
            float y = 0.55F - 1.5F * (i + 0.5F) / RAYOS;
            float r = (float) Math.sqrt(Math.max(0, 1 - y * y));
            float a = i * 2.39996F;
            DIRS[i] = new Vector3f(Mth.cos(a) * r, y, Mth.sin(a) * r).normalize();
        }
    }

    private record Impactos(long tick, Vec3[] puntos, Vec3[] normales) {}

    private static final Map<BlockPos, Impactos> CACHE = new HashMap<>();

    public BolaRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx) {
        geo = new GeoBlockRenderer<>(new Modelo());
        geo.addRenderLayer(new AutoGlowingGeoLayer<>(geo));
    }

    static class Modelo extends GeoModel<BolaBlockEntity> {
        private static final ResourceLocation GEO = new ResourceLocation(Miscelanea.MOD_ID, "geo/block/bola_discoteca.geo.json"),
                TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/rave/bola_discoteca.png"),
                ANIM = new ResourceLocation(Miscelanea.MOD_ID, "animations/block/rave.animation.json");

        @Override
        public ResourceLocation getModelResource(BolaBlockEntity e) {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(BolaBlockEntity e) {
            return TEX;
        }

        @Override
        public ResourceLocation getAnimationResource(BolaBlockEntity e) {
            return ANIM;
        }

        @Override
        public void setCustomAnimations(BolaBlockEntity e, long id, AnimationState<BolaBlockEntity> st) {
            if (e.getLevel() == null) return;
            float t = e.getLevel().getGameTime() + st.getPartialTick();
            var s = e.getBlockState();
            float g = s.getValue(LucesRave.ENCENDIDA) ? LucesRave.giroBola(t, s.getValue(LucesRave.MODO)) : 0;
            getBone("bola").ifPresent(b -> b.setRotY(g));
        }
    }

    @Override
    public boolean shouldRenderOffScreen(BolaBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public void render(BolaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        geo.render(be, pt, ps, buf, luz, overlay);
        var s = be.getBlockState();
        if (!s.getValue(LucesRave.ENCENDIDA) || be.getLevel() == null) return;
        Impactos im = impactos(be, s.getValue(LucesRave.MODO), pt);
        int rgb = Neon.rgb(s.getValue(Muebles.COLOR));
        BlockPos p = be.getBlockPos();
        for (int i = 0; i < im.puntos().length; i++) {
            Vec3 h = im.puntos()[i];
            if (h == null) continue;
            Vector3f c = new Vector3f((float) (h.x - p.getX()), (float) (h.y - p.getY()), (float) (h.z - p.getZ()));
            Vec3 nn = im.normales()[i];
            float dist = c.distance(0.5F, CENTRO, 0.5F);
            float fuerza = (1 - dist / (ALCANCE + 4)) * ((i % 3 == 0) ? 1.2F : 0.9F);
            Luces.mancha(ps, buf, c, new Vector3f((float) nn.x, (float) nn.y, (float) nn.z), 0.22F + dist * 0.02F, rgb, fuerza);
        }
    }

    /** Puntos donde dan los rayos (se recalcula cada tick: cada fotograma solo se dibuja). */
    private static Impactos impactos(BolaBlockEntity be, int modo, float pt) {
        long tick = be.getLevel().getGameTime();
        BlockPos p = be.getBlockPos();
        Impactos c = CACHE.get(p);
        if (c != null && c.tick() == tick) return c;
        if (CACHE.size() > 64) CACHE.clear();
        float giro = LucesRave.giroBola(tick, modo);
        Vec3 o = new Vec3(p.getX() + 0.5, p.getY() + CENTRO, p.getZ() + 0.5);
        Vec3[] pts = new Vec3[RAYOS], nor = new Vec3[RAYOS];
        var quien = Minecraft.getInstance().player;
        for (int i = 0; i < RAYOS; i++) {
            Vector3f d = new Vector3f(DIRS[i]).rotateY(-giro);
            Vec3 ini = o.add(d.x * RADIO, d.y * RADIO, d.z * RADIO), fin = o.add(d.x * ALCANCE, d.y * ALCANCE, d.z * ALCANCE);
            BlockHitResult r = be.getLevel().clip(new ClipContext(ini, fin, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, quien));
            if (r.getType() == HitResult.Type.BLOCK) {
                pts[i] = r.getLocation();
                var n = r.getDirection().getNormal();
                nor[i] = new Vec3(n.getX(), n.getY(), n.getZ());
            }
        }
        c = new Impactos(tick, pts, nor);
        CACHE.put(p.immutable(), c);
        return c;
    }
}

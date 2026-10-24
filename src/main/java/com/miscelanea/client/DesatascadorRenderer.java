package com.miscelanea.client;

import com.miscelanea.Miscelanea;
import com.miscelanea.aspira.DesatascadorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Ventosa en 3D (GeckoLib) + cuerda con textura hasta la boca de la boquilla.
 * La cuerda cuelga floja y se va poniendo recta según la tensión que manda el servidor.
 */
public class DesatascadorRenderer extends GeoEntityRenderer<DesatascadorEntity> {
    private static final ResourceLocation CUERDA = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/cuerda.png");
    private static final float ALTURA = 0.2F; // centro de la hitbox
    private static final float ARGOLLA = -1.2F / 16.0F;

    public DesatascadorRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, "desatascador")));
        this.shadowRadius = 0.15F;
    }

    private static float yaw(DesatascadorEntity e, float pt) {
        return Mth.rotLerp(pt, e.yRotO, e.getYRot());
    }

    private static float pitch(DesatascadorEntity e, float pt) {
        return Mth.lerp(pt, e.xRotO, e.getXRot());
    }

    @Override
    protected void applyRotations(DesatascadorEntity e, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick) {
        // El modelo mira hacia +Z (la boca de la ventosa delante)
        pose.translate(0.0F, ALTURA, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(yaw(e, partialTick)));
        pose.mulPose(Axis.XP.rotationDegrees(-pitch(e, partialTick)));
    }

    @Override
    public void render(DesatascadorEntity e, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        super.render(e, entityYaw, partialTick, pose, buffers, light);

        Entity owner = e.getOwner();
        if (owner == null) return;

        double ex = Mth.lerp(partialTick, e.xo, e.getX());
        double ey = Mth.lerp(partialTick, e.yo, e.getY());
        double ez = Mth.lerp(partialTick, e.zo, e.getZ());

        // Argolla de la ventosa (relativa al origen de la entidad)
        Vector3f a = new Vector3f(0.0F, 0.0F, ARGOLLA)
                .rotateX(-pitch(e, partialTick) * Mth.DEG_TO_RAD)
                .rotateY(yaw(e, partialTick) * Mth.DEG_TO_RAD)
                .add(0.0F, ALTURA, 0.0F);

        // Boca de la boquilla en el mundo
        Vec3 boca = bocaDelDueno(owner, partialTick);
        Vector3f b = new Vector3f((float) (boca.x - ex), (float) (boca.y - ey), (float) (boca.z - ez));

        // Comba de la cuerda: floja = cuelga mucho; tensa = recta
        float tension = e.tension();
        float largo = a.distance(b);
        float comba = (1.0F - tension) * Math.min(largo * 0.35F, 3.0F);
        // un pequeño vaivén cuando está floja
        float t = e.tickCount + partialTick;
        float vaiven = (1.0F - tension) * 0.08F * Mth.sin(t * 0.25F);

        Vector3f d = new Vector3f(b).sub(a);
        Vector3f lateral = new Vector3f(d.z, 0.0F, -d.x);
        if (lateral.lengthSquared() > 1.0E-6F) lateral.normalize().mul(vaiven * largo);
        Vector3f p1 = new Vector3f(d).mul(1.0F / 3.0F).add(a).add(0.0F, -comba * 1.33F, 0.0F).add(lateral);
        Vector3f p2 = new Vector3f(d).mul(2.0F / 3.0F).add(a).add(0.0F, -comba * 1.33F, 0.0F).sub(lateral);

        int segmentos = Mth.clamp((int) (largo * 3.0F), 8, 40);
        Tubo.dibujar(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(CUERDA)), light,
                a, p1, p2, b, 0.35F / 16.0F, segmentos, 0.5F);
    }

    /** Boca de la boquilla: la que calcula MangueraLayer en tercera persona, o una aproximación en primera. */
    private static Vec3 bocaDelDueno(Entity owner, float pt) {
        Minecraft mc = Minecraft.getInstance();
        boolean primera = owner == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        if (!primera) {
            Vec3 b = MangueraLayer.boca(owner.getId());
            if (b != null) return b;
        }
        Vec3 ojo = owner.getEyePosition(pt);
        Vec3 look = owner.getViewVector(pt);
        float yaw = Mth.lerp(pt, owner.yRotO, owner.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 derecha = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return primera
                ? ojo.add(look.scale(1.1)).add(derecha.scale(0.22)).add(0, -0.22, 0)
                : ojo.add(look.scale(0.9)).add(derecha.scale(0.3)).add(0, -0.45, 0);
    }

    @Override
    public boolean shouldRender(DesatascadorEntity entity, Frustum frustum, double x, double y, double z) {
        return true; // la cuerda puede verse aunque la ventosa esté fuera de pantalla
    }
}

package com.miscelanea.client.medigun;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.PoseAspiradora;
import com.miscelanea.client.Tubo;
import com.miscelanea.medigun.MedigunItem;
import com.miscelanea.medigun.MochilaMedicaItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Manguera de la mochila médica a la medigun (tercera persona) y posición de la boca para el rayo. */
public class MedigunLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURA = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/manguera_medica.png");
    /** Puntos del modelo geo de la medigun (px de Blockbench). */
    private static final Vector3f SALIDA = new Vector3f(0, 6.75F, 9.0F);
    private static final Vector3f BOCA = new Vector3f(0, 6.6F, -10.9F);

    private static final Map<Integer, Vec3> BOCAS = new HashMap<>();
    private static final Map<Integer, Long> BOCAS_T = new HashMap<>();

    public MedigunLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static Vec3 boca(int id) {
        Long t = BOCAS_T.get(id);
        if (t == null || System.currentTimeMillis() - t > 200L) return null;
        return BOCAS.get(id);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        HumanoidArm brazo;
        if (player.getMainHandItem().getItem() instanceof MedigunItem) brazo = player.getMainArm();
        else if (player.getOffhandItem().getItem() instanceof MedigunItem) brazo = player.getMainArm().getOpposite();
        else return;

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        boolean izquierda = brazo == HumanoidArm.LEFT;
        Matrix4f geo = PoseAspiradora.geoAModelo(izquierda ? model.leftArm : model.rightArm, izquierda);

        if (MochilaMedicaItem.lleva(player)) {
            ModelPart body = model.body;
            Matrix4f cuerpo = new Matrix4f()
                    .translate(body.x / 16.0F, body.y / 16.0F, body.z / 16.0F)
                    .rotateZYX(body.zRot, body.yRot, body.xRot);
            // salida de la mochila: abajo, del lado derecho (geo x=+1.7 → modelo x=-1.7)
            Vector3f ini = cuerpo.transformPosition(new Vector3f(-1.7F / 16.0F, 12.2F / 16.0F, 4.6F / 16.0F));
            Vector3f fin = PoseAspiradora.punto(geo, SALIDA);
            float lado = izquierda ? 1.0F : -1.0F;
            Vector3f c1 = new Vector3f(ini).add(lado * 2.5F / 16.0F, 5.0F / 16.0F, 2.5F / 16.0F);
            Vector3f c2 = new Vector3f(fin).add(0.0F, 6.0F / 16.0F, 2.5F / 16.0F);
            Tubo.dibujar(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURA)), light,
                    ini, c1, c2, fin, 0.8F / 16.0F, 20, 3.0F / 16.0F);
        }

        // Boca en coordenadas del mundo (igual que la manguera de la aspiradora)
        Vector3f boca = PoseAspiradora.punto(geo, BOCA);
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        Vector3f w = new Vector3f(boca).add(0.0F, -1.501F, 0.0F).mul(-0.9375F, -0.9375F, 0.9375F);
        w.rotateY((180.0F - bodyYaw) * Mth.DEG_TO_RAD);
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double py = Mth.lerp(partialTick, player.yo, player.getY()) + (player.isCrouching() ? -0.125 : 0.0);
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());
        BOCAS.put(player.getId(), new Vec3(px + w.x, py + w.y, pz + w.z));
        BOCAS_T.put(player.getId(), System.currentTimeMillis());
    }
}

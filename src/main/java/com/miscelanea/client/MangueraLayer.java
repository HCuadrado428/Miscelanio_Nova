package com.miscelanea.client;

import com.miscelanea.Miscelanea;
import com.miscelanea.aspira.AspirafantasmasItem;
import com.miscelanea.aspira.Deposito;
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

/**
 * Capa del jugador que dibuja la manguera (con textura) desde la mochila hasta la boquilla en tercera persona.
 * También apunta dónde está la boca de la boquilla en el mundo, para la cuerda del desatascador.
 */
public class MangueraLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURA = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/manguera.png");

    /** Boca de la boquilla en coordenadas del mundo, por id de jugador (se actualiza cada frame que se dibuja). */
    private static final Map<Integer, Vec3> BOCAS = new HashMap<>();
    private static final Map<Integer, Long> BOCAS_T = new HashMap<>();

    public MangueraLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** Última posición conocida de la boca (si se dibujó hace poco), o null. */
    public static Vec3 boca(int entityId) {
        Long t = BOCAS_T.get(entityId);
        if (t == null || System.currentTimeMillis() - t > 200L) return null;
        return BOCAS.get(entityId);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!Deposito.llevaMochila(player)) return;

        HumanoidArm brazo;
        if (player.getMainHandItem().getItem() instanceof AspirafantasmasItem) brazo = player.getMainArm();
        else if (player.getOffhandItem().getItem() instanceof AspirafantasmasItem) brazo = player.getMainArm().getOpposite();
        else return;

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        boolean izquierda = brazo == HumanoidArm.LEFT;
        ModelPart parteBrazo = izquierda ? model.leftArm : model.rightArm;
        Matrix4f geo = PoseAspiradora.geoAModelo(parteBrazo, izquierda);

        // Salida de la mochila (parte baja del depósito, en la espalda)
        ModelPart body = model.body;
        Matrix4f cuerpo = new Matrix4f()
                .translate(body.x / 16.0F, body.y / 16.0F, body.z / 16.0F)
                .rotateZYX(body.zRot, body.yRot, body.xRot);
        Vector3f ini = cuerpo.transformPosition(new Vector3f(0.0F, 13.2F / 16.0F, 5.0F / 16.0F));
        Vector3f fin = PoseAspiradora.punto(geo, PoseAspiradora.SALIDA_MANGUERA);

        float lado = izquierda ? 1.0F : -1.0F;
        Vector3f c1 = new Vector3f(ini).add(lado * 3.0F / 16.0F, 6.0F / 16.0F, 3.0F / 16.0F);
        Vector3f c2 = new Vector3f(fin).add(0.0F, 6.0F / 16.0F, 2.5F / 16.0F);

        Tubo.dibujar(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURA)), light,
                ini, c1, c2, fin, 1.0F / 16.0F, 20, 4.0F / 16.0F);

        // Guardar la boca en coordenadas del mundo
        Vector3f boca = PoseAspiradora.punto(geo, PoseAspiradora.BOCA);
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

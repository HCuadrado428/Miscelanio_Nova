package com.miscelanea.client.tinta;

import com.miscelanea.Miscelanea;
import com.miscelanea.tinta.ArmaTinta;
import com.miscelanea.tinta.Calamar;
import com.miscelanea.tinta.Deposito;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forma de calamar: en vez del jugador se dibuja un calamar de Splatoon (teñido de su color) que va dando saltitos.
 * Nadando en su tinta no se dibuja nada (solo las salpicaduras). También el medidor de tinta junto a la mira.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class CalamarRender {
    public static final ModelLayerLocation CAPA = new ModelLayerLocation(new ResourceLocation(Miscelanea.MOD_ID, "calamar"), "main");
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/calamar.png");
    private static final ResourceLocation OJOS = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/calamar_ojos.png");
    private static ModelPart modelo;

    private CalamarRender() {}

    public static LayerDefinition crear() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition raiz = mesh.getRoot();
        PartDefinition c = raiz.addOrReplaceChild("cuerpo", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5, 8, -3, 10, 2, 8)          // manto (aletas)
                .texOffs(0, 10).addBox(-4, 6, -2, 8, 2, 6)
                .texOffs(36, 0).addBox(-2, 4, -1, 4, 2, 4)          // punta del manto
                .texOffs(0, 18).addBox(-3, 10, -2, 6, 8, 5), PartPose.ZERO);   // cuerpo con los ojos
        for (int k = 0; k < 4; k++) {
            c.addOrReplaceChild("tentaculo" + k, CubeListBuilder.create().texOffs(36, 6).addBox(-0.5F, 0, -0.5F, 1, 5, 1),
                    PartPose.offset(-2.2F + k * 1.45F, 18, 0.5F));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static ModelPart modelo() {
        if (modelo == null) modelo = Minecraft.getInstance().getEntityModels().bakeLayer(CAPA);
        return modelo;
    }

    @SubscribeEvent
    public static void jugador(RenderPlayerEvent.Pre e) {
        AbstractClientPlayer p = (AbstractClientPlayer) e.getEntity();
        if (!Calamar.es(p)) return;
        e.setCanceled(true);
        if (Calamar.enSuTinta(p)) return;   // sumergido: invisible
        float partial = e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        int c = ArmaTinta.colorJugador(p);
        float t = p.tickCount + partial;
        boolean anda = p.getDeltaMovement().horizontalDistanceSqr() > 0.0005;
        ModelPart m = modelo();
        ps.pushPose();
        float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        if (anda) ps.translate(0, Math.abs(Mth.sin(t * 0.55F)) * 0.25, 0);   // saltitos
        ps.scale(-0.8F, -0.8F, 0.8F);
        ps.translate(0, -1.501, 0);
        for (int k = 0; k < 4; k++) {
            ModelPart te = m.getChild("cuerpo").getChild("tentaculo" + k);
            te.xRot = Mth.sin(t * 0.4F + k) * 0.25F + (anda ? 0.3F : 0);
            te.zRot = (k - 1.5F) * 0.15F;
        }
        VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(TEX));
        m.render(ps, vc, e.getPackedLight(), OverlayTexture.NO_OVERLAY, ((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F, 1);
        VertexConsumer vo = e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(OJOS));
        m.render(ps, vo, e.getPackedLight(), OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    @SubscribeEvent
    public static void manos(RenderHandEvent e) {
        var p = Minecraft.getInstance().player;
        if (p != null && Calamar.es(p)) e.setCanceled(true);
    }

    /** Medidor de tinta a la derecha de la mira (como el depósito de Splatoon). */
    public static void hud(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        var arma = ArmaTinta.enMano(mc.player);
        if (arma.isEmpty()) return;
        float nivel = Deposito.nivel(mc.player) / Deposito.MAX;
        int c = ArmaTinta.color(arma);
        int x = w / 2 + 12, y0 = h / 2 - 14, alto = 28;
        g.fill(x - 1, y0 - 1, x + 5, y0 + alto + 1, 0x90000000);
        int lleno = (int) (alto * nivel);
        boolean bajo = nivel < 0.1F && (mc.player.tickCount / 5) % 2 == 0;
        g.fill(x, y0 + alto - lleno, x + 4, y0 + alto, bajo ? 0xFFFF3030 : (0xFF000000 | c));
        g.fill(x, y0 + alto - lleno, x + 1, y0 + alto, 0x50FFFFFF);   // brillo
    }
}

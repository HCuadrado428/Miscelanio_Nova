package com.miscelanea.client.armasfuego.pistola;

import com.miscelanea.Miscelanea;
import com.miscelanea.armasfuego.pistola.Carga;
import com.miscelanea.armasfuego.pistola.Revolver;
import com.miscelanea.armasfuego.pistola.Tambor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * El tambor del revólver, dibujado por código encima del armazón (que es el modelo 3D del objeto, sin tambor): así gira de
 * verdad (hasta la recámara que toca, con un frenazo suave; en la ruleta rusa da vueltas) y cada recámara cargada lleva el
 * culote de su tipo de bala, tintado del mismo color que en el HUD. Con el tambor girado no se enseña ninguna bala.
 * <p>
 * Se llama desde el mixin de ItemRenderer.render, así que vale en la mano (primera y tercera persona), en el suelo y en
 * los marcos. El tambor y el culote son modelos adicionales (ModelEvent.RegisterAdditional); la geometría (el centro del
 * tambor y el anillo de recámaras) es la de herramientas/generar_tambor.py.
 */
public final class RevolverTambor {
    private RevolverTambor() {}

    private static ResourceLocation rl(String n) {
        return new ResourceLocation(Miscelanea.MOD_ID, "item/armas3d/" + n);
    }

    /** Un tambor por nivel de óxido (cobre nuevo, expuesto, erosionado, oxidado). */
    private static final ResourceLocation[] CILINDRO = {rl("revolver_cilindro_0"), rl("revolver_cilindro_1"), rl("revolver_cilindro_2"), rl("revolver_cilindro_3")};
    private static final ResourceLocation CULOTE = rl("revolver_culote");
    public static final ResourceLocation[] MODELOS = {CILINDRO[0], CILINDRO[1], CILINDRO[2], CILINDRO[3], CULOTE};

    // la geometría del modelo (píxeles de modelo): centro del tambor, anillo de recámaras y cara de atrás
    private static final double CX = 8.0, CY = 8.9, ANILLO = 1.0, Z_ATRAS = 11.0, DESPLAZA_ABIERTO = 3.4;

    /** Animación del giro (solo la mano de primera persona): el ángulo que se ve y cuándo se dibujó la última vez. */
    private static float angVisto = Float.NaN;
    private static long tAntes, giroVisto = Long.MIN_VALUE, giroDesdeMs;

    public static void dibujar(ItemStack s, ItemDisplayContext ctx, boolean izq, PoseStack ps, MultiBufferSource buf, int luz, int overlay, BakedModel m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean primera = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        int pos = Tambor.pos(s);
        boolean girado = Tambor.girado(s);
        float objetivo = 60F * pos, ang = objetivo;
        if (primera) {
            long ahora = System.nanoTime();
            float dt = tAntes == 0 ? 0F : Math.min(0.1F, (ahora - tAntes) / 1e9F);
            tAntes = ahora;
            if (Float.isNaN(angVisto)) angVisto = objetivo;
            // cada paso de recámara: el tambor gira hasta la que toca y frena (suavizado independiente de los fps)
            angVisto += Mth.wrapDegrees(objetivo - angVisto) * (1F - (float) Math.exp(-dt * 16F));
            ang = angVisto;
            // la ruleta rusa: gira rápido y frena (como el tambor del HUD)
            long giroT = Tambor.giroT(s), edad = mc.level.getGameTime() - giroT;
            if (giroT != giroVisto) {
                giroVisto = giroT;
                giroDesdeMs = Util.getMillis() - Math.max(0L, edad) * 50L;
            }
            float prog = (Util.getMillis() - giroDesdeMs) / (Tambor.GIRO_TICKS * 50F);
            // solo si el giro es reciente (el valor inicial, 0, no cuenta como giro)
            if (edad >= 0 && edad < Tambor.GIRO_TICKS + 2 && prog >= 0F && prog < 1F) {
                float k = 1F - prog;
                ang += 720F * k * k;
            }
        }
        boolean abierto = s.hasTag() && mc.level.getGameTime() - s.getTag().getLong(Revolver.CARGA_T) < 12 && mc.level.getGameTime() - s.getTag().getLong(Revolver.CARGA_T) >= 0;
        BakedModel real = m.applyTransform(ctx, new com.mojang.blaze3d.vertex.PoseStack(), false);
        ps.pushPose();
        // la misma colocación que hace el ItemRenderer con el modelo: la transformación de la perspectiva y el origen en la esquina
        real.getTransforms().getTransform(ctx).apply(izq, ps);
        ps.translate(-0.5, -0.5, -0.5);
        if (abierto) ps.translate(-DESPLAZA_ABIERTO / 16.0, 0, 0);
        ps.translate(CX / 16.0, CY / 16.0, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(ang));
        ps.translate(-CX / 16.0, -CY / 16.0, 0);
        modelo(CILINDRO[Mth.clamp(Tambor.oxido(s), 0, 3)], ps, buf, luz, overlay, 1F, 1F, 1F);
        if (!girado) {
            for (int j = 0; j < Tambor.N; j++) {
                Carga c = Tambor.en(s, j);
                if (c == Carga.VACIA) continue;
                double a = Math.toRadians(60.0 * j);
                ps.pushPose();
                ps.translate((CX + ANILLO * Math.sin(a)) / 16.0, (CY + ANILLO * Math.cos(a)) / 16.0, Z_ATRAS / 16.0);
                modelo(CULOTE, ps, buf, luz, overlay, (c.color >> 16 & 255) / 255F, (c.color >> 8 & 255) / 255F, (c.color & 255) / 255F);
                ps.popPose();
            }
        }
        ps.popPose();
    }

    private static void modelo(ResourceLocation id, PoseStack ps, MultiBufferSource buf, int luz, int overlay, float r, float g, float b) {
        BakedModel bm = Minecraft.getInstance().getModelManager().getModel(id);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, bm, r, g, b, luz, overlay);
    }
}

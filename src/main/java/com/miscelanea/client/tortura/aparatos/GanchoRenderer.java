package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.Cuerdas;
import com.miscelanea.tortura.aparatos.GanchoBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Gancho para colgar: el tambor y la manivela del torno (giran con la cuerda que se recoge o se suelta) y la cuerda, del
 * tambor a la argolla y de ahí a los tobillos del colgado (dos vueltas de cuerda alrededor). Sin nadie, la cuerda cuelga
 * con el lazo vacío al final.
 */
public class GanchoRenderer implements BlockEntityRenderer<GanchoBlockEntity> {
    public static final ResourceLocation TAMBOR = new ResourceLocation(Miscelanea.MOD_ID, "block/tortura/gancho_tambor");
    public static final ResourceLocation MANIVELA = new ResourceLocation(Miscelanea.MOD_ID, "block/tortura/gancho_manivela");
    private static final float GROSOR = 0.065F;

    public GanchoRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(GanchoBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        float largo = be.largoVis(pt);
        float ax = be.axVis(pt), az = be.azVis(pt);
        // torno: la cuerda que sale hace girar el tambor (y la manivela con él)
        float giro = (float) Math.toDegrees(largo * 16F / GanchoBlockEntity.TAMBOR_R);
        ps.pushPose();
        ModelosAparatos.orientar(ps, be.frente());
        ps.pushPose();
        ModelosAparatos.girarX(ps, GanchoBlockEntity.TORNO_Y, GanchoBlockEntity.TORNO_Z, giro);
        ModelosAparatos.dibujar(ps, buf, TAMBOR, luz);
        ModelosAparatos.dibujar(ps, buf, MANIVELA, luz);
        ps.popPose();
        ps.popPose();

        VertexConsumer vc = buf.getBuffer(Cuerdas.TIPO);
        float fase = -largo / (GROSOR * 4F);
        Vector3f arriba = new Vector3f(0.5F, (GanchoBlockEntity.TORNO_Y - GanchoBlockEntity.TAMBOR_R) / 16F, 0.5F);
        Vector3f piv = new Vector3f(0.5F, GanchoBlockEntity.PIVOTE_PX / 16F, 0.5F);
        Vector3f abajo = new Vector3f(0, -1, 0);
        Cuerdas.dibujar(ps, vc, arriba, piv, abajo, 0F, luz, GROSOR, fase);
        Vec3 d = GanchoBlockEntity.direccion(ax, az);
        Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z);
        Vector3f fin = new Vector3f(dir).mul(largo).add(piv);
        BlockPos bp = be.getBlockPos();
        int luzFin = be.getLevel() == null ? luz
                : LevelRenderer.getLightColor(be.getLevel(), BlockPos.containing(bp.getX() + fin.x, bp.getY() + fin.y, bp.getZ() + fin.z));
        Cuerdas.dibujar(ps, vc, piv, fin, abajo, 0F, luzFin, GROSOR, fase);
        Entity e = be.ocupado() && be.getLevel() != null && be.victimaId() >= 0 ? be.getLevel().getEntity(be.victimaId()) : null;
        if (e instanceof LivingEntity le) {
            // dos vueltas alrededor de los tobillos (a lo ancho de las dos piernas)
            float yaw = (float) Math.toRadians(Mth.lerp(pt, le.yBodyRotO, le.yBodyRot));
            Vector3f lado = new Vector3f(Mth.cos(yaw), 0, Mth.sin(yaw));
            Vector3f frente = new Vector3f(-Mth.sin(yaw), 0, Mth.cos(yaw));
            // perpendiculares a la cuerda
            lado.sub(new Vector3f(dir).mul(lado.dot(dir))).normalize();
            frente.sub(new Vector3f(dir).mul(frente.dot(dir))).sub(new Vector3f(lado).mul(frente.dot(lado))).normalize();
            float ancho = Math.max(0.12F, le.getBbWidth() * 0.46F);
            for (int i = 0; i < 2; i++) {
                Vector3f c = new Vector3f(dir).mul(0.07F + i * 0.075F).add(fin);
                Cuerdas.anillo(ps, vc, c, lado, frente, ancho, ancho * 0.52F, luzFin, GROSOR * 0.9F, i * 1.3F);
            }
        } else {
            // lazo vacío
            Vector3f c = new Vector3f(dir).mul(0.16F).add(fin);
            Cuerdas.anillo(ps, vc, c, new Vector3f(1, 0, 0), new Vector3f(dir).mul(-1F), 0.12F, 0.16F, luzFin, GROSOR * 0.9F, 0F);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(GanchoBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}

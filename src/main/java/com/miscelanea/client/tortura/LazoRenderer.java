package com.miscelanea.client.tortura;

import com.miscelanea.tortura.LazoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * El lazo volando: el aro abierto (inclinado, de cara a donde va; plano al caer) y la cuerda desde la mano de quien
 * lo ha lanzado hasta el nudo. El PoseStack de un EntityRenderer solo está trasladado a la entidad (ejes del mundo).
 */
public class LazoRenderer extends EntityRenderer<LazoEntity> {
    public LazoRenderer(EntityRendererProvider.Context c) {
        super(c);
    }

    @Override
    public ResourceLocation getTextureLocation(LazoEntity e) {
        return Cuerdas.TEX;
    }

    @Override
    public boolean shouldRender(LazoEntity e, Frustum f, double x, double y, double z) {
        return true;   // la cuerda va hasta la mano: aunque el aro no se vea, la cuerda sí
    }

    @Override
    public void render(LazoEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        Vec3 pos = e.getPosition(pt);
        Entity o = e.getOwner();
        Vec3 manoW = o != null ? TorturaCliente.manoMundo(o, pt) : pos.add(0, 0.3, 0);
        Vector3f mano = new Vector3f((float) (manoW.x - pos.x), (float) (manoW.y - pos.y), (float) (manoW.z - pos.z));
        Vector3f c = new Vector3f();
        Vector3f dir = new Vector3f(c).sub(mano);
        if (dir.lengthSquared() < 1.0E-6F) dir.set(0, 0, 1);
        dir.normalize();
        Vector3f plano = new Vector3f(dir.x, 0, dir.z);
        if (plano.lengthSquared() < 1.0E-6F) plano.set(0, 0, 1);
        plano.normalize();
        // u: horizontal y perpendicular al vuelo; v: hacia delante, a medio camino entre "de cara" y "plano"
        Vector3f u = new Vector3f(-plano.z, 0, plano.x);
        Vector3f deCara = new Vector3f(u).cross(dir).normalize();
        if (deCara.dot(plano) < 0) deCara.negate();
        Vector3f v = e.cayendo() ? new Vector3f(plano) : new Vector3f(deCara).lerp(plano, 0.55F).normalize();
        float r = 0.4F + 0.3F * e.carga(), rv = r * 0.92F;
        float giro = (e.tickCount + pt) * 0.45F;
        VertexConsumer vc = buf.getBuffer(Cuerdas.TIPO);
        Cuerdas.anillo(ps, vc, c, u, v, r, rv, luz, 0.05F, giro);
        // el nudo, en el lado del aro que mira al que lo lanzó
        Vector3f honda = new Vector3f(v).mul(-rv);
        float dist = mano.distance(honda);
        Cuerdas.dibujar(ps, vc, mano, honda, new Vector3f(0, -1, 0), Math.min(0.8F, 0.05F + dist * 0.035F), luz, 0.045F, -giro * 0.2F);
        super.render(e, yaw, pt, ps, buf, luz);
    }
}

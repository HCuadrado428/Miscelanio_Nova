package com.miscelanea.glitch;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Muestra de glitch lanzada (se ve como una poción arrojadiza con el líquido del glitch). Al chocar con lo que sea se
 * rompe al momento y empieza la {@link OndaGlitch} en el punto del impacto.
 */
public class MuestraLanzadaEntity extends ThrowableItemProjectile {
    public MuestraLanzadaEntity(EntityType<? extends MuestraLanzadaEntity> t, Level l) {
        super(t, l);
    }

    public MuestraLanzadaEntity(Level l, LivingEntity quien) {
        super(GlitchRegistro.MUESTRA_LANZADA.get(), quien, l);
    }

    @Override
    protected Item getDefaultItem() {
        return GlitchRegistro.MUESTRA_LANZADA_ITEM.get();
    }

    @Override
    protected float getGravity() {
        return 0.05F; // como una poción arrojadiza
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && random.nextInt(2) == 0)
            level().addParticle(GlitchRegistro.PARTICULA.get(), getX(), getY() + 0.1, getZ(), 0, 0, 0);
    }

    @Override
    protected void onHit(HitResult r) {
        super.onHit(r);
        if (level().isClientSide || isRemoved() || !(level() instanceof ServerLevel sl)) return;
        Vec3 p = r.getLocation();
        // contra un bloque, el centro se queda justo delante de la cara que ha tocado
        if (r instanceof BlockHitResult b) p = p.add(Vec3.atLowerCornerOf(b.getDirection().getNormal()).scale(0.3));
        sl.levelEvent(2002, net.minecraft.core.BlockPos.containing(p), GlitchRegistro.COLOR);
        Entity o = getOwner();
        OndaGlitch.detonar(sl, p, o != null ? o.getUUID() : null);
        discard();
    }
}

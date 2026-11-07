package com.miscelanea.tortura.castigo;

import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.heridas.Parte;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Tomate o huevo podrido volando. Al chocar revienta (partículas y sonido); a un jugador le deja una mancha en la piel
 * justo donde le dio (ver {@link Manchas}). No hace daño (solo el empujoncito y el destello rojo).
 * <p>Al del cepo: la cabeza y las manos asoman por delante de la tabla, fuera de su caja de choque, así que se le da con
 * esferas propias ({@link CepoEntity#puntosGolpe}). Si el tomate va a darle en la cara, se le avisa para que lo esquive
 * ({@link Escarnio#avisarJugador}); si lo esquiva, el tomate pasa de largo.
 */
public class ProyectilPodrido extends ThrowableItemProjectile {
    /** De una lluvia de tomates (no avisa para esquivar ni cuenta para otra lluvia). */
    public boolean lluvia;
    private boolean avisado;

    public ProyectilPodrido(EntityType<? extends ProyectilPodrido> t, Level l) {
        super(t, l);
    }

    public ProyectilPodrido(Level l, LivingEntity quien, boolean huevo) {
        super(huevo ? TorturaRegistro.HUEVO_LANZADO.get() : TorturaRegistro.TOMATE_LANZADO.get(), quien, l);
    }

    public ProyectilPodrido(Level l, double x, double y, double z, boolean huevo) {
        super(huevo ? TorturaRegistro.HUEVO_LANZADO.get() : TorturaRegistro.TOMATE_LANZADO.get(), x, y, z, l);
    }

    public boolean huevo() {
        return getType() == TorturaRegistro.HUEVO_LANZADO.get();
    }

    @Override
    protected Item getDefaultItem() {
        return huevo() ? TorturaRegistro.HUEVO_PODRIDO.get() : TorturaRegistro.TOMATE_PODRIDO.get();
    }

    @Override
    protected float getGravity() {
        return 0.03F;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (!super.canHitEntity(e)) return false;
        // atraviesa los barrotes (le da al de dentro), no choca con el asiento invisible del cepo ni con la bola
        if (e instanceof JaulaEntity || e instanceof CepoEntity || e instanceof BolaPresoEntity) return false;
        // al del cepo solo se le da con las esferas (cabeza, manos, espalda)
        return !(e instanceof LivingEntity le && Escarnio.enCepo(le));
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            if (!lluvia && !avisado && tickCount % 2 == 0) avisar();
            if (golpeCepo()) return;
        }
        super.tick();
    }

    private List<CepoEntity> ceposCerca(double r) {
        return level().getEntitiesOfClass(CepoEntity.class, getBoundingBox().inflate(r), c -> c.victima() != null);
    }

    /** Si el vuelo va a acabar en la cara de alguien del cepo, se le avisa (una vez por proyectil). */
    private void avisar() {
        for (CepoEntity c : ceposCerca(22)) {
            LivingEntity v = c.victima();
            if (v == null || v == getOwner() || Escarnio.avisado(v)) continue;
            if (v instanceof ServerPlayer sp && com.miscelanea.tortura.qte.Qte.activo(sp)) continue;
            Vec3 cab = c.puntosGolpe()[0];
            Vec3 p = position(), vel = getDeltaMovement();
            for (int k = 1; k <= 34; k++) {
                Vec3 sig = p.add(vel);
                if (Golpes.esfera(p, sig, cab, 0.42) >= 0) {
                    if (k < 4) {
                        avisado = true;   // demasiado cerca: ya no da tiempo
                        return;
                    }
                    // ¿por qué lado de su cara viene? (izquierda de la víctima = su yaw + 90º)
                    Vec3 izq = Vec3.directionFromRotation(0, c.getYRot() - 90F);
                    boolean porIzq = position().subtract(cab).dot(izq) > 0;
                    if (Math.abs(position().subtract(cab).dot(izq)) < 0.3) porIzq = v.getRandom().nextBoolean();
                    if (v instanceof ServerPlayer sp) Escarnio.avisarJugador(sp, this, k, porIzq);
                    else Escarnio.avisarMob(v, this, k, porIzq);
                    avisado = true;
                    return;
                }
                p = sig;
                vel = vel.scale(0.99).add(0, -getGravity(), 0);
            }
        }
    }

    /** Choque con la cabeza, las manos o la espalda de alguien del cepo en este tick. */
    private boolean golpeCepo() {
        Vec3 a = position(), b = a.add(getDeltaMovement());
        for (CepoEntity c : ceposCerca(4)) {
            LivingEntity v = c.victima();
            if (v == null || v == getOwner() || Escarnio.esquivando(v, getId())) continue;
            Vec3[] pts = c.puntosGolpe();
            double[] radios = {0.36, 0.22, 0.22, 0.34};
            double mejor = 2;
            int cual = -1;
            for (int i = 0; i < pts.length; i++) {
                double t = Golpes.esfera(a, b, pts[i], radios[i]);
                if (t >= 0 && t < mejor) {
                    mejor = t;
                    cual = i;
                }
            }
            if (cual < 0) continue;
            Vec3 punto = a.add(b.subtract(a).scale(mejor));
            v.hurt(damageSources().thrown(this, getOwner()), 0.0F);
            Parte parte = c.manchar(v, cual, punto, huevo());
            Escarnio.impacto(this, v, parte, lluvia);
            reventar(punto);
            return true;
        }
        return false;
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        super.onHitEntity(r);
        if (level().isClientSide || !(r.getEntity() instanceof LivingEntity t)) return;
        Vec3 v = getDeltaMovement();
        Vec3 punto = t.getBoundingBox().inflate(0.15).clip(position(), position().add(v.scale(2))).orElse(position());
        t.hurt(damageSources().thrown(this, getOwner()), 0.0F);
        Parte parte = null;
        if (t instanceof ServerPlayer sp) {
            Golpes.Golpe g = Golpes.desdePunto(sp, punto, position().subtract(v.scale(4)));
            Manchas.agregarEn(sp, g.parte(), g.cara(), g.fx(), g.fy(), huevo());
            parte = g.parte();
        }
        Escarnio.impacto(this, t, parte, lluvia);
    }

    @Override
    protected void onHit(HitResult r) {
        super.onHit(r);
        if (!level().isClientSide && !isRemoved()) reventar(r.getLocation());
    }

    private void reventar(Vec3 punto) {
        setPos(punto.x, punto.y, punto.z);
        level().playSound(null, punto.x, punto.y, punto.z, huevo() ? TorturaRegistro.HUEVO_SPLAT.get() : TorturaRegistro.TOMATE_SPLAT.get(),
                SoundSource.PLAYERS, 0.8F, 0.9F + random.nextFloat() * 0.25F);
        level().broadcastEntityEvent(this, (byte) 3);
        discard();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id != 3) {
            super.handleEntityEvent(id);
            return;
        }
        boolean cara = net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()
                && com.miscelanea.client.tortura.castigo.SalpicadurasPantalla.enMiCara(getX(), getY(), getZ(), huevo());
        // reventado en mi cara: la mancha va en la pantalla; aquí solo unas gotitas que caen (lejos de la cámara)
        if (cara) {
            for (int i = 0; i < 4; i++) level().addParticle(new DustParticleOptions(huevo() ? new Vector3f(0.98F, 0.78F, 0.16F) : new Vector3f(0.72F, 0.08F, 0.05F), 1.0F),
                    getX() + (random.nextDouble() - 0.5) * 0.8, getY() - 0.9, getZ() + (random.nextDouble() - 0.5) * 0.8, 0, -0.05, 0);
            return;
        }
        ItemStack st = getItemRaw();
        ItemParticleOption trozos = new ItemParticleOption(ParticleTypes.ITEM, st.isEmpty() ? new ItemStack(getDefaultItem()) : st);
        boolean h = huevo();
        DustParticleOptions pulpa = new DustParticleOptions(h ? new Vector3f(0.98F, 0.78F, 0.16F) : new Vector3f(0.72F, 0.08F, 0.05F), 1.3F);
        DustParticleOptions jugo = new DustParticleOptions(h ? new Vector3f(0.95F, 0.94F, 0.86F) : new Vector3f(0.9F, 0.32F, 0.12F), 0.9F);
        for (int i = 0; i < 10; i++) {
            level().addParticle(trozos, getX(), getY(), getZ(), (random.nextDouble() - 0.5) * 0.25, random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.25);
        }
        for (int i = 0; i < 14; i++) {
            level().addParticle(i % 2 == 0 ? pulpa : jugo, getX() + (random.nextDouble() - 0.5) * 0.3, getY() + (random.nextDouble() - 0.5) * 0.3,
                    getZ() + (random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putBoolean("Lluvia", lluvia);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        lluvia = t.getBoolean("Lluvia");
    }

    /** (para el renderer de objetos lanzados: que el tomate se vea aunque esté pegado a la cámara al salir) */
    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 64 * 64;
    }

}

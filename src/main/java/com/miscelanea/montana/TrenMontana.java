package com.miscelanea.montana;

import com.miscelanea.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Tren de la montaña rusa: de 1 a 4 vagones de 2 plazas que van por la {@link Via}. Es UNA sola entidad: el primer vagón
 * lleva la física (gravedad por la pendiente, rozamiento, cadena, estación y freno) y los demás se colocan detrás a lo
 * largo de la vía, sin física propia. Al cliente solo le llega el trozo, la fracción y la velocidad (tres números por
 * tick), y el propio cliente coloca los vagones y adelanta un poco cada fotograma: se ve suave sin gastar red ni
 * procesador.
 */
public class TrenMontana extends Entity {
    public static final int MAX_VAGONES = 4;
    /** Separación entre vagones a lo largo de la vía (bloques). */
    public static final double SEPARACION = 1.55;
    /** Gravedad a lo largo de la pendiente, rozamiento y velocidades (bloques por tick). */
    static final double G = 0.035, ROCE = 0.9993, RODADURA = 0.0004, MAX = 1.5, CADENA = 0.12, SALIDA = 0.18, FRENO = 0.3;
    /** Ticks que espera en la estación con gente montada antes de salir solo. */
    static final int ESPERA = 100;

    private static final EntityDataAccessor<BlockPos> PIEZA = SynchedEntityData.defineId(TrenMontana.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> T = SynchedEntityData.defineId(TrenMontana.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> V = SynchedEntityData.defineId(TrenMontana.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> VAGONES = SynchedEntityData.defineId(TrenMontana.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CUENTA = SynchedEntityData.defineId(TrenMontana.class, EntityDataSerializers.INT);

    private int espera;
    private float yawAntes;

    public TrenMontana(EntityType<?> t, Level l) {
        super(t, l);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PIEZA, BlockPos.ZERO);
        entityData.define(T, 0.5F);
        entityData.define(V, 0F);
        entityData.define(VAGONES, 2);
        entityData.define(CUENTA, -1);
    }

    public void colocar(BlockPos pieza, double t, int vagones) {
        entityData.set(PIEZA, pieza.immutable());
        entityData.set(T, (float) t);
        entityData.set(VAGONES, Mth.clamp(vagones, 1, MAX_VAGONES));
        Via.Pieza p = Via.en(level(), pieza);
        if (p != null) {
            Vec3 v = p.punto(t);
            setPos(v.x, v.y, v.z);
        }
    }

    /** Le da una velocidad (bloques por tic, hacia delante si es positiva). Para las pruebas y los comandos. */
    public void lanzar(double v) {
        entityData.set(V, (float) Mth.clamp(v, -MAX, MAX));
    }

    public int vagones() {
        return entityData.get(VAGONES);
    }

    public float velocidad() {
        return entityData.get(V);
    }

    /** Segundos que faltan para salir de la estación (o -1). */
    public int cuenta() {
        return entityData.get(CUENTA);
    }

    @Nullable
    public Via.Sitio sitio() {
        Via.Pieza p = Via.en(level(), entityData.get(PIEZA));
        return p == null ? null : new Via.Sitio(p, entityData.get(T));
    }

    /** El sitio del vagón k (0 = el primero), adelantando "adelanto" bloques (para dibujar entre ticks). */
    @Nullable
    public Via.Sitio sitioVagon(int k, double adelanto) {
        Via.Sitio s = sitio();
        if (s == null) return null;
        double d = adelanto - k * SEPARACION;
        return d == 0 ? s : Via.avanzar(level(), s, d, null);
    }

    // ------------------------------------------------------------------ física
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            sonidoCliente();
            return;
        }
        Via.Sitio s = sitio();
        if (s == null) {
            // le han quitado la vía de debajo
            romper(true);
            return;
        }
        Via.Pieza p = s.pieza();
        double v = entityData.get(V);
        Vec3 tg = p.tangente(s.t());
        v -= G * tg.y;
        v *= ROCE;
        if (Math.abs(v) <= RODADURA) v = 0;
        else v -= Math.signum(v) * RODADURA;
        int cuenta = -1;
        switch (p.tipo()) {
            case CADENA -> {
                if (v < CADENA) v = Math.min(CADENA, v + 0.03);
                if (tickCount % 4 == 0) level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.5F, 0.8F);
            }
            case FRENO -> {
                if (Math.abs(v) > FRENO) v *= 0.85;
            }
            case ESTACION -> {
                if (Math.abs(v) > 0.03 && espera == 0) v *= 0.75;
                else {
                    v = 0;
                    boolean gente = isVehicle();
                    if (gente || p.encendida()) espera++;
                    else espera = 0;
                    if (p.encendida() || espera >= ESPERA) {
                        v = SALIDA;
                        espera = -20;    // un segundo sin volver a frenar mientras sale
                        level().playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6F, 0.7F);
                    } else if (gente) cuenta = (ESPERA - espera + 19) / 20;
                }
                if (espera < 0) espera++;
            }
            default -> espera = 0;
        }
        if (p.tipo() != ViaMontanaBlock.Tipo.ESTACION) espera = 0;
        v = Mth.clamp(v, -MAX, MAX);
        boolean[] tope = {false};
        Via.Sitio n = Via.avanzar(level(), s, v, tope);
        if (tope[0]) {
            // final de la vía: rebota un poco
            if (Math.abs(v) > 0.05) level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3F, 1.6F);
            v = -v * 0.25;
        }
        entityData.set(PIEZA, n.pieza().pos());
        entityData.set(T, (float) n.t());
        entityData.set(V, (float) v);
        entityData.set(CUENTA, cuenta);
        Vec3 pos = n.punto();
        setPos(pos.x, pos.y, pos.z);
        // en el rizo el giro es el del trozo (si no, en lo alto daría media vuelta de golpe)
        if (n.pieza().clase() == Via.Clase.RIZO) setYRot(n.pieza().dir().toYRot());
        else {
            Vec3 t2 = n.pieza().tangente(n.t());
            setYRot((float) (Mth.atan2(-t2.x, t2.z) * Mth.RAD_TO_DEG));
        }
    }

    private Object sonido;

    private void sonidoCliente() {
        if (sonido == null || tickCount % 20 == 0) {
            Object antes = sonido;
            sonido = net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.miscelanea.client.montana.MontanaCliente.sonar(this, antes));
        }
    }

    // ------------------------------------------------------------------ pasajeros
    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().size() < vagones() * 2;
    }

    @Override
    protected void positionRider(Entity e, MoveFunction f) {
        int i = getPassengers().indexOf(e);
        if (i < 0) return;
        Via.Sitio s = sitioVagon(i / 2, 0);
        if (s == null) return;
        Vec3 tg = s.pieza().tangente(s.t()), up = s.pieza().arriba(s.t());
        Vec3 lado = tg.cross(up).normalize().scale(i % 2 == 0 ? -0.27 : 0.27);
        // sentado DENTRO del vagón (las piernas quedan tapadas por los lados), no encima
        Vec3 asiento = s.punto().add(up.scale(0.3 + e.getMyRidingOffset())).add(lado).subtract(tg.scale(0.12));
        f.accept(e, asiento.x, asiento.y, asiento.z);
        // los que van montados giran con el vagón en las curvas
        float yaw = (float) (Mth.atan2(-tg.x, tg.z) * Mth.RAD_TO_DEG);
        if (e instanceof LivingEntity le) le.setYBodyRot(yaw);
    }

    /** El cambio de giro del tren en este tick (lo aplica el cliente a la cámara de quien va montado). */
    public float giroTick() {
        float y = getYRot(), d = Mth.wrapDegrees(y - yawAntes);
        yawAntes = y;
        return d;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        Via.Sitio s = sitio();
        if (s == null) return super.getDismountLocationForPassenger(e);
        Vec3 tg = s.pieza().tangente(s.t());
        Vec3 lado = tg.cross(new Vec3(0, 1, 0)).normalize().scale(1.3);
        return s.punto().add(lado).add(0, 0.3, 0);
    }

    /** Con la barra de seguridad bajada: solo se puede bajar con el tren parado. */
    public boolean puedeBajar() {
        return Math.abs(velocidad()) < 0.05;
    }

    @Override
    public InteractionResult interact(Player pl, InteractionHand h) {
        ItemStack st = pl.getItemInHand(h);
        if (st.is(ModItems.TREN_MONTANA.get()) && pl.isShiftKeyDown()) {
            if (vagones() >= MAX_VAGONES) return InteractionResult.FAIL;
            if (!level().isClientSide) {
                entityData.set(VAGONES, vagones() + 1);
                if (!pl.getAbilities().instabuild) st.shrink(1);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (pl.isSecondaryUseActive() || !canAddPassenger(pl)) return InteractionResult.PASS;
        if (!level().isClientSide) pl.startRiding(this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || isRemoved()) return true;
        if (src.getEntity() instanceof Player pl && !isVehicle()) {
            romper(!pl.getAbilities().instabuild);
            return true;
        }
        return false;
    }

    /** El objeto pone un tren de 2 vagones y cada vagón de más gasta otro: al romperlo se devuelven todos. */
    private void romper(boolean soltar) {
        if (soltar) spawnAtLocation(new ItemStack(ModItems.TREN_MONTANA.get(), 1 + Math.max(0, vagones() - 2)));
        ejectPassengers();
        discard();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.TREN_MONTANA.get());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    // ------------------------------------------------------------------ guardar
    @Override
    protected void readAdditionalSaveData(CompoundTag c) {
        entityData.set(PIEZA, BlockPos.of(c.getLong("pieza")));
        entityData.set(T, c.getFloat("t"));
        entityData.set(V, c.getFloat("v"));
        entityData.set(VAGONES, Mth.clamp(c.getInt("vagones"), 1, MAX_VAGONES));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag c) {
        c.putLong("pieza", entityData.get(PIEZA).asLong());
        c.putFloat("t", entityData.get(T));
        c.putFloat("v", entityData.get(V));
        c.putInt("vagones", vagones());
    }
}

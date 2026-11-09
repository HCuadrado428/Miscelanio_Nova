package com.miscelanea.montana;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
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
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Tren de la montaña rusa: de 1 a 4 vagones de 2 plazas que van por la {@link Via}. Es UNA sola entidad: lleva la
 * física del tren entero (la gravedad es la media de la pendiente de todos los vagones; la cadena tira y el freno frena
 * en cuanto un vagón está encima; la estación para el primero) y los vagones se colocan a lo largo de la vía, uno tras
 * otro, una vez por tic (no en cada fotograma).
 * <p>
 * Al cliente le llegan el trozo, la fracción y la velocidad. El cliente no los usa tal cual: lleva su propio tren, que
 * avanza solo a la velocidad que le llega y se va corrigiendo poco a poco hacia lo que dice el servidor (si los paquetes
 * llegan a trompicones, el tren no da tirones); y entre tic y tic se dibuja deslizando cada vagón por la vía.
 */
public class TrenMontana extends Entity {
    public static final int MAX_VAGONES = 4;
    /** Separación entre vagones a lo largo de la vía (bloques). */
    public static final double SEPARACION = 1.55;
    /** Gravedad a lo largo de la pendiente, rozamiento y velocidades (bloques por tick). */
    static final double G = 0.035, ROCE = 0.9993, RODADURA = 0.0004, MAX = 1.5, CADENA = 0.12, SALIDA = 0.18, FRENO = 0.3, LENTO = 0.2;
    /** Ticks que espera en la estación con gente montada antes de salir solo. */
    static final int ESPERA = 100;

    private static final EntityDataAccessor<BlockPos> PIEZA = SynchedEntityData.defineId(TrenMontana.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> T = SynchedEntityData.defineId(TrenMontana.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> V = SynchedEntityData.defineId(TrenMontana.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> VAGONES = SynchedEntityData.defineId(TrenMontana.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CUENTA = SynchedEntityData.defineId(TrenMontana.class, EntityDataSerializers.INT);
    /** Asiento de cada pasajero (id de entidad → plaza), para que nadie cambie de sitio cuando otro se baja. */
    private static final EntityDataAccessor<CompoundTag> ASIENTOS = SynchedEntityData.defineId(TrenMontana.class, EntityDataSerializers.COMPOUND_TAG);

    // ---- servidor
    /** Ticks parado en la estación con gente (o -1 si no está parado en ella). */
    private int espera;
    /** Acaba de salir de la estación: no vuelve a frenar en ella hasta dejarla atrás. */
    private boolean saliendo;
    /** Lo tiene parado un freno con redstone (al quitarla, sale). */
    private boolean retenido;
    /** La estación tenía redstone en el tic anterior (sale al encenderla, no mientras sigue encendida). */
    private boolean redAntes;
    private int gritoEspera;
    @Nullable
    private Via.Clase claseAntes;
    /** Dónde lo ha tocado el que se va a montar (para sentarle en ese vagón). */
    @Nullable
    private Vec3 pedido;

    // ---- los dos lados
    /** Sitio de cada vagón en este tic (el primero es la cabeza). */
    @Nullable
    private Via.Sitio[] vag;

    // ---- cliente
    /** El tren del cliente (la cabeza) y lo que ha avanzado en el último tic. */
    @Nullable
    private Via.Sitio vis;
    private double paso;
    @Nullable
    private Via.Sitio[] dib;
    private float dibPt = -1;
    private int dibTick = -1;
    private float yawAntes = Float.NaN;

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
        entityData.define(ASIENTOS, new CompoundTag());
    }

    public void colocar(BlockPos pieza, double t, int vagones) {
        entityData.set(PIEZA, pieza.immutable());
        entityData.set(T, (float) t);
        entityData.set(VAGONES, Mth.clamp(vagones, 1, MAX_VAGONES));
        Via.Pieza p = Via.en(level(), pieza);
        if (p != null) {
            vag = vagones(new Via.Sitio(p, t));
            Vec3 v = p.punto(t);
            setPos(v.x, v.y, v.z);
        }
    }

    /** Le da una velocidad (bloques por tic, hacia delante si es positiva). Para las pruebas y los comandos. */
    public void lanzar(double v) {
        entityData.set(V, (float) Mth.clamp(v, -MAX, MAX));
        saliendo = v > 0;
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

    /** El sitio que dice el servidor (la cabeza del tren). */
    @Nullable
    public Via.Sitio sitio() {
        Via.Pieza p = Via.en(level(), entityData.get(PIEZA));
        return p == null ? null : new Via.Sitio(p, entityData.get(T));
    }

    /** Los vagones colocados uno detrás de otro a partir de la cabeza. */
    private Via.Sitio[] vagones(Via.Sitio cabeza) {
        Via.Sitio[] r = new Via.Sitio[vagones()];
        r[0] = cabeza;
        for (int k = 1; k < r.length; k++) r[k] = Via.avanzar(level(), r[k - 1], -SEPARACION, null);
        return r;
    }

    /** Sitio del vagón k en este tic (0 = el primero), o null. */
    @Nullable
    public Via.Sitio sitioVagon(int k) {
        if (vag == null || vag.length != vagones()) {
            Via.Sitio s = level().isClientSide && vis != null ? vis : sitio();
            if (s == null) return null;
            vag = vagones(s);
        }
        return k >= 0 && k < vag.length ? vag[k] : null;
    }

    /** Sitio del vagón k para dibujarlo en este fotograma (entre el tic anterior y este); se calcula una vez por fotograma. */
    @Nullable
    public Via.Sitio sitioDibujo(int k, float pt) {
        Via.Sitio base = sitioVagon(k);
        if (base == null) return null;
        if (dib == null || dib.length != vag.length || pt != dibPt || tickCount != dibTick) {
            dib = new Via.Sitio[vag.length];
            dibPt = pt;
            dibTick = tickCount;
        }
        Via.Sitio s = dib[k];
        if (s == null) {
            double a = paso * (pt - 1);
            s = Math.abs(a) < 1e-5 ? base : Via.avanzar(level(), base, a, null);
            dib[k] = s;
        }
        return s;
    }

    // ------------------------------------------------------------------ física
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickCliente();
            return;
        }
        Via.Sitio s = sitio();
        if (s == null) {
            // le han quitado la vía de debajo
            romper(true);
            return;
        }
        Via.Sitio[] cs = vagones(s);
        Via.Pieza p = s.pieza();
        double v = entityData.get(V), antes = v;
        // gravedad: tira el tren entero (la media de la pendiente de todos los vagones)
        double gy = 0;
        boolean cadena = false, freno = false, retener = false;
        for (Via.Sitio c : cs) {
            gy += c.pieza().tangente(c.t()).y;
            switch (c.pieza().tipo()) {
                case CADENA -> cadena = true;
                case FRENO -> {
                    if (c.pieza().encendida()) retener = true;
                    else freno = true;
                }
                default -> {}
            }
        }
        v -= G * gy / cs.length;
        v *= ROCE;
        if (Math.abs(v) <= RODADURA) v = 0;
        else v -= Math.signum(v) * RODADURA;
        // la cadena sube el tren mientras quede algún vagón enganchado (si no, se quedaría a medias en lo alto)
        if (cadena && v < CADENA) v = Math.min(CADENA, v + 0.03);
        if (freno && Math.abs(v) > FRENO) v *= 0.85;
        if (retener) {
            // freno con redstone: para el tren y lo retiene
            v *= 0.6;
            if (Math.abs(v) < 0.03) v = 0;
            retenido = true;
        } else if (retenido) {
            retenido = false;
            if (Math.abs(v) < 0.03) v = SALIDA;
        }
        // estación: para la cabeza en el centro de la última estación seguida; espera y sale
        int cuenta = -1;
        boolean enEstacion = p.tipo() == ViaMontanaBlock.Tipo.ESTACION;
        if (!enEstacion) saliendo = false;
        espera = enEstacion ? espera : 0;
        if (enEstacion && !saliendo) {
            Via.Pieza sig = Via.siguiente(level(), p);
            boolean ultima = sig == null || sig.tipo() != ViaMontanaBlock.Tipo.ESTACION;
            double d = (0.5 - s.t()) * p.longitud();
            if (v < 0) {
                v *= 0.7;
                if (v > -0.02) v = 0;
            } else if (!ultima) {
                // estación de varios bloques: va despacio hasta la última
                if (v > LENTO) v = Math.max(LENTO, v * 0.7);
                else if (v < 0.06) v = 0.06;
            } else if (d > 0.03) {
                v = Math.min(v, Math.max(0.03, d * 0.4));
            } else if (d >= 0 || v < 0.02) {
                // ya está en el centro (o se ha pasado un poco y ya va despacio): parado
                if (d >= 0 && d <= 0.03 && v > 0) s = new Via.Sitio(p, 0.5);
                v = 0;
            } else v *= 0.5;
            if (v == 0 && ultima) {
                boolean gente = isVehicle();
                boolean red = p.encendida();
                // sale con gente a los 5 s, o al dar la redstone (al encenderse: si ya llegó encendida, no sale de golpe)
                if (gente) espera++;
                else espera = 0;
                if ((red && !redAntes) || espera >= ESPERA) {
                    v = SALIDA;
                    saliendo = true;
                    espera = 0;
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6F, 0.7F);
                } else if (gente) cuenta = (ESPERA - espera + 19) / 20;
            }
        }
        redAntes = enEstacion && p.encendida();
        // frenazo fuerte (estación o freno): suena
        if (Math.abs(antes) - Math.abs(v) > 0.04 && tickCount % 3 == 0)
            level().playSound(null, getX(), getY(), getZ(), ModSounds.MONTANA_FRENO.get(), SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
        v = choques(cs, v);
        v = Mth.clamp(v, -MAX, MAX);
        boolean[] tope = {false};
        Via.Sitio n = Via.avanzar(level(), s, v, tope, !saliendo);
        if (tope[0]) {
            // final de la vía: rebota un poco
            if (Math.abs(v) > 0.05) level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3F, 1.6F);
            v = -v * 0.25;
        }
        entityData.set(PIEZA, n.pieza().pos());
        entityData.set(T, (float) n.t());
        entityData.set(V, (float) v);
        entityData.set(CUENTA, cuenta);
        vag = vagones(n);
        Vec3 pos = n.punto();
        setPos(pos.x, pos.y, pos.z);
        setYRot(yaw(n));
        // ¡uuuh! al empezar una bajada (o el rizo) deprisa y con gente
        Via.Clase cl = n.pieza().clase();
        if (gritoEspera > 0) gritoEspera--;
        if (cl != claseAntes && (cl == Via.Clase.BAJADA || cl == Via.Clase.RIZO) && v > 0.25 && isVehicle() && gritoEspera == 0) {
            level().playSound(null, getX(), getY(), getZ(), ModSounds.MONTANA_GRITO.get(), SoundSource.NEUTRAL, 1.0F, 0.9F + random.nextFloat() * 0.25F);
            gritoEspera = 80;
        }
        claseAntes = cl;
    }

    /** Giro horizontal de un vagón (en el rizo, el del trozo: si no, en lo alto daría media vuelta de golpe). */
    static float yaw(Via.Sitio s) {
        if (s.pieza().clase() == Via.Clase.RIZO) return s.pieza().dir().toYRot();
        Vec3 t2 = s.pieza().tangente(s.t());
        return (float) (Mth.atan2(-t2.x, t2.z) * Mth.RAD_TO_DEG);
    }

    /** ¿Está quieto y anclado (parado en la estación o retenido por un freno)? Los choques no lo mueven. */
    private boolean anclado() {
        return retenido || (velocidad() == 0 && vag != null && vag[0].pieza().tipo() == ViaMontanaBlock.Tipo.ESTACION);
    }

    /**
     * Choques con otros trenes de la misma vía: si la punta que va delante alcanza un vagón de otro tren, rebotan los dos
     * (el de delante se lleva el empujón, salvo que esté parado en la estación o en un freno).
     */
    private double choques(Via.Sitio[] cs, double v) {
        if (Math.abs(v) < 1e-4) return v;
        boolean adelante = v > 0;
        Via.Sitio punta = adelante ? cs[0] : cs[cs.length - 1];
        Vec3 pp = punta.punto(), dir = punta.pieza().tangente(punta.t()).scale(adelante ? 1 : -1);
        for (TrenMontana o : level().getEntitiesOfClass(TrenMontana.class, getBoundingBox().inflate(3), x -> x != this && x.vag != null && !x.isRemoved())) {
            double vo = o.velocidad();
            for (Via.Sitio c : o.vag) {
                Vec3 d = c.punto().subtract(pp);
                if (d.lengthSqr() > 1.6 * 1.6 || d.dot(dir) < 0.1) continue;
                double cierre = adelante ? v - vo : vo - v;
                if (cierre <= 0) break;
                if (cierre > 0.08) level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, (float) Math.min(0.6, cierre * 2), 1.4F);
                if (o.anclado()) return -v * 0.25;
                double m = (v + vo) / 2, dv = (v - vo) / 2;
                o.entityData.set(V, (float) Mth.clamp(m + 0.3 * dv, -MAX, MAX));
                return m - 0.3 * dv;
            }
        }
        return v;
    }

    // ------------------------------------------------------------------ cliente
    private int clac;

    private void tickCliente() {
        Via.Sitio srv = sitio();
        if (srv == null) {
            vis = null;
            vag = null;
            paso = 0;
            return;
        }
        double v = velocidad();
        if (vis == null) {
            vis = srv;
            paso = 0;
        } else {
            // el tren del cliente sigue a su paso y se corrige poco a poco hacia el del servidor
            Via.Sitio pred = Via.avanzar(level(), vis, v, null);
            double err = Via.distancia(level(), pred, srv, 4);
            if (Double.isNaN(err) || Math.abs(err) > 3) {
                vis = srv;
                paso = 0;
            } else {
                double c = Math.abs(err) < 0.01 ? err : err * 0.3;
                vis = Math.abs(c) < 1e-6 ? pred : Via.avanzar(level(), pred, c, null);
                paso = v + c;
            }
        }
        vag = vagones(vis);
        dib = null;
        // traqueteo (un sonido en bucle por tren) y el clac-clac de la cadena
        if (sonido == null || tickCount % 20 == 0) {
            Object antes = sonido;
            sonido = net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.miscelanea.client.montana.MontanaCliente.sonar(this, antes));
        }
        boolean cadena = false;
        for (Via.Sitio c : vag) cadena |= c.pieza().tipo() == ViaMontanaBlock.Tipo.CADENA;
        if (cadena && Math.abs(v) > 0.01 && ++clac >= 7) {
            clac = 0;
            level().playLocalSound(getX(), getY(), getZ(), ModSounds.MONTANA_CADENA.get(), SoundSource.BLOCKS, 0.55F, 0.95F + random.nextFloat() * 0.1F, false);
        }
    }

    private Object sonido;

    /** Lo que ha girado el vagón k desde la última vez que se preguntó (para girar con él la cámara de quien va dentro). */
    public float giroVagon(int k) {
        Via.Sitio s = sitioVagon(k);
        if (s == null) return 0;
        float y = yaw(s);
        float d = Float.isNaN(yawAntes) ? 0 : Mth.wrapDegrees(y - yawAntes);
        yawAntes = y;
        return d;
    }

    /** Olvida el giro anterior (al subir no se gira la cámara: solo cuenta lo que gire a partir de ahora). */
    public void reiniciarGiro() {
        yawAntes = Float.NaN;
    }

    @Override
    protected AABB makeBoundingBox() {
        // la caja abarca todos los vagones (para poder subirse o romperlo tocando cualquiera)
        AABB b = super.makeBoundingBox();
        if (vag != null) for (Via.Sitio c : vag) {
            Vec3 p = c.punto();
            b = b.minmax(new AABB(p.x - 0.6, p.y - 0.05, p.z - 0.6, p.x + 0.6, p.y + 0.85, p.z + 0.6));
        }
        return b;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    // ------------------------------------------------------------------ pasajeros
    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().size() < vagones() * 2;
    }

    /** La plaza de un pasajero (0..vagones*2-1): las pares a la izquierda, las impares a la derecha. */
    public int asiento(Entity e) {
        CompoundTag t = entityData.get(ASIENTOS);
        String k = Integer.toString(e.getId());
        if (t.contains(k)) return t.getInt(k);
        return Math.max(0, getPassengers().indexOf(e));
    }

    @Override
    protected void addPassenger(Entity e) {
        super.addPassenger(e);
        if (level().isClientSide) return;
        // la plaza libre más cerca de donde ha tocado (o de donde está)
        CompoundTag t = entityData.get(ASIENTOS).copy();
        boolean[] ocupada = new boolean[MAX_VAGONES * 2];
        for (Entity o : getPassengers()) {
            String k = Integer.toString(o.getId());
            if (o != e && t.contains(k)) ocupada[Mth.clamp(t.getInt(k), 0, ocupada.length - 1)] = true;
        }
        Vec3 cerca = pedido != null ? pedido : e.position();
        pedido = null;
        int mejor = -1;
        double md = Double.MAX_VALUE;
        for (int a = 0; a < vagones() * 2; a++) {
            if (ocupada[a]) continue;
            Via.Sitio s = sitioVagon(a / 2);
            double d = (s == null ? a : s.punto().distanceToSqr(cerca)) + (a % 2) * 0.01;
            if (d < md) {
                md = d;
                mejor = a;
            }
        }
        t.putInt(Integer.toString(e.getId()), Math.max(0, mejor));
        entityData.set(ASIENTOS, t);
    }

    @Override
    protected void removePassenger(Entity e) {
        super.removePassenger(e);
        if (level().isClientSide) return;
        CompoundTag t = entityData.get(ASIENTOS).copy();
        t.remove(Integer.toString(e.getId()));
        entityData.set(ASIENTOS, t);
    }

    @Override
    protected void positionRider(Entity e, MoveFunction f) {
        if (!hasPassenger(e)) return;
        int i = asiento(e);
        Via.Sitio s = sitioVagon(i / 2);
        if (s == null) return;
        Vec3 tg = s.pieza().tangente(s.t()), up = s.pieza().arriba(s.t());
        Vec3 lado = tg.cross(up).normalize().scale(i % 2 == 0 ? -0.27 : 0.27);
        // sentado DENTRO del vagón (las piernas quedan tapadas por los lados), no encima
        Vec3 asiento = s.punto().add(up.scale(0.3 + e.getMyRidingOffset())).add(lado).subtract(tg.scale(0.12));
        f.accept(e, asiento.x, asiento.y, asiento.z);
        // los que van montados giran con el vagón en las curvas
        if (e instanceof LivingEntity le) le.setYBodyRot(yaw(s));
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        Via.Sitio s = sitioVagon(asiento(e) / 2);
        if (s == null) return super.getDismountLocationForPassenger(e);
        Vec3 tg = s.pieza().tangente(s.t());
        Vec3 der = tg.cross(new Vec3(0, 1, 0));
        if (der.lengthSqr() < 1e-4) return super.getDismountLocationForPassenger(e);
        der = der.normalize();
        // primero por su lado; si no hay sitio (o está en el aire), por el otro
        Vec3 p = s.punto();
        double lado = asiento(e) % 2 == 0 ? -1 : 1;
        for (double k : new double[]{lado, -lado}) {
            Vec3 c = p.add(der.scale(1.3 * k));
            for (int dy = 0; dy >= -1; dy--) {
                Vec3 sitio = DismountHelper.findSafeDismountLocation(e.getType(), level(), BlockPos.containing(c).above(dy), true);
                if (sitio != null) return sitio;
            }
        }
        return p.add(der.scale(1.3 * lado)).add(0, 0.3, 0);
    }

    /** Con la barra de seguridad bajada: solo se puede bajar con el tren parado. */
    public boolean puedeBajar() {
        return Math.abs(velocidad()) < 0.05;
    }

    @Override
    public InteractionResult interactAt(Player pl, Vec3 donde, InteractionHand h) {
        if (!level().isClientSide) pedido = position().add(donde);
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interact(Player pl, InteractionHand h) {
        ItemStack st = pl.getItemInHand(h);
        if (st.is(ModItems.TREN_MONTANA.get()) && pl.isShiftKeyDown()) {
            if (vagones() >= MAX_VAGONES) return InteractionResult.FAIL;
            if (!level().isClientSide) {
                entityData.set(VAGONES, vagones() + 1);
                vag = null;
                if (!pl.getAbilities().instabuild) st.shrink(1);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (pl.isSecondaryUseActive() || !canAddPassenger(pl) || pl.isPassenger()) return InteractionResult.PASS;
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

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> k) {
        super.onSyncedDataUpdated(k);
        if (VAGONES.equals(k)) vag = null;
    }

    // ------------------------------------------------------------------ guardar
    @Override
    protected void readAdditionalSaveData(CompoundTag c) {
        entityData.set(PIEZA, BlockPos.of(c.getLong("pieza")));
        entityData.set(T, c.getFloat("t"));
        entityData.set(V, c.getFloat("v"));
        entityData.set(VAGONES, Mth.clamp(c.getInt("vagones"), 1, MAX_VAGONES));
        espera = c.getInt("espera");
        saliendo = c.getBoolean("saliendo");
        retenido = c.getBoolean("retenido");
        redAntes = c.getBoolean("red");
        vag = null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag c) {
        c.putLong("pieza", entityData.get(PIEZA).asLong());
        c.putFloat("t", entityData.get(T));
        c.putFloat("v", entityData.get(V));
        c.putInt("vagones", vagones());
        c.putInt("espera", espera);
        c.putBoolean("saliendo", saliendo);
        c.putBoolean("retenido", retenido);
        c.putBoolean("red", redAntes);
    }
}

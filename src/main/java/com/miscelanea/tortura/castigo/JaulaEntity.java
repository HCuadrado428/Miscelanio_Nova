package com.miscelanea.tortura.castigo;

import com.miscelanea.tortura.TorturaRegistro;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Jaula colgante (gibbet). Cuelga de la argolla {@link JaulaAnclaBlock} con una cadena de {@link #largo()} bloques y se
 * balancea como un péndulo (dos ángulos, AX hacia +x y AZ hacia +z). Dentro cabe un jugador o un mob (va "montado",
 * de pie). La puerta: 0 abierta, 1 cerrada, 2 cerrada con la llave del carcelero.
 * <ul>
 *   <li>Meter a alguien: clic derecho a la jaula llevando a un cautivo (a hombros, con la cadena o enlazado). Bajada
 *       hasta el suelo y con la puerta abierta, quien entre andando queda atrapado (la puerta se cierra de golpe).</li>
 *   <li>Clic derecho con la mano vacía: abrir / cerrar la puerta. Con la llave: echar / quitar la llave. Shift + mano
 *       vacía: bajarla al suelo / subirla otra vez. Desde la argolla: un bloque arriba o abajo.</li>
 *   <li>BALANCEO: el preso empuja con A / D (izquierda / derecha según hacia dónde mira). Empujar a favor del vaivén
 *       lo hace crecer (resonancia); a destiempo lo frena. Por encima de ~26º la cadena se va fatigando; al romperse, la
 *       jaula cae (daño de caída según la altura) y se revienta: el preso queda libre.</li>
 * </ul>
 * La física va en el servidor (usa la entrada del jugador montado, xxa); el cliente recibe los ángulos y la recoloca.
 */
public class JaulaEntity extends Entity {
    private static final EntityDataAccessor<Optional<BlockPos>> ANCLA = SynchedEntityData.defineId(JaulaEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Float> LARGO = SynchedEntityData.defineId(JaulaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AX = SynchedEntityData.defineId(JaulaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AZ = SynchedEntityData.defineId(JaulaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FATIGA = SynchedEntityData.defineId(JaulaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> PUERTA = SynchedEntityData.defineId(JaulaEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> ESTADO = SynchedEntityData.defineId(JaulaEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> EN_SUELO = SynchedEntityData.defineId(JaulaEntity.class, EntityDataSerializers.BOOLEAN);

    public static final byte ABIERTA = 0, CERRADA = 1, CON_LLAVE = 2;
    public static final byte COLGANDO = 0, CAYENDO = 1, ROTA = 2;
    /** Alto total de la jaula (del suelo a la argolla de arriba), grosor del suelo; = gen_tortura3a.py. */
    public static final double ALTO = 42 / 16.0, SUELO = 1.5 / 16.0, ANCLA_Y = 8.5 / 16.0;
    public static final double LARGO_MIN = 0.4, LARGO_MAX = 40, LARGO_INICIAL = 2.0, VEL_TORNO = 0.06;
    /** Gravedad (bloques/tick²), rozamiento por tick y fuerza del empujo (en múltiplos de ω0²: sube ~2·EMPUJE rad cada medio vaivén). */
    static final double G = 0.065, ROCE = 0.9975, EMPUJE = 0.03;
    /** Por encima de este ángulo (rad) la cadena se fatiga. */
    public static final double UMBRAL = 0.45, FATIGA_K = 0.06;

    // ---- servidor
    private double wx, wz, objetivo = LARGO_INICIAL, largoArriba = LARGO_INICIAL, maxLargo = LARGO_MAX, caidaY;
    private int rotaT, radialSigno, avisoFatiga;
    private boolean permitir;
    private final Map<UUID, Long> salidas = new HashMap<>();
    // ---- cliente (interpolación)
    public float axO, azO, axC, azC, largoO, largoC, puertaO, puertaA;

    public JaulaEntity(EntityType<? extends JaulaEntity> t, Level l) {
        super(t, l);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ANCLA, Optional.empty());
        entityData.define(LARGO, (float) LARGO_INICIAL);
        entityData.define(AX, 0F);
        entityData.define(AZ, 0F);
        entityData.define(FATIGA, 0F);
        entityData.define(PUERTA, ABIERTA);
        entityData.define(ESTADO, COLGANDO);
        entityData.define(EN_SUELO, false);
    }

    // ------------------------------------------------------------------ consultas
    @Nullable
    public BlockPos ancla() {
        return entityData.get(ANCLA).orElse(null);
    }

    public float largo() {
        return entityData.get(LARGO);
    }

    public float ax() {
        return entityData.get(AX);
    }

    public float az() {
        return entityData.get(AZ);
    }

    public float fatiga() {
        return entityData.get(FATIGA);
    }

    public byte puerta() {
        return entityData.get(PUERTA);
    }

    public byte estado() {
        return entityData.get(ESTADO);
    }

    public boolean dejaSalir() {
        return permitir || puerta() == ABIERTA || estado() != COLGANDO;
    }

    /** ¿Está bajada hasta el suelo? (se apoya: no se balancea y se puede entrar andando) */
    public boolean enSuelo() {
        return entityData.get(EN_SUELO);
    }

    private boolean sueloServidor() {
        return largo() >= maxLargo - 0.06;
    }

    @Nullable
    public LivingEntity preso() {
        for (Entity e : getPassengers()) if (e instanceof LivingEntity le) return le;
        return null;
    }

    /** Punto del que cuelga la cadena (bajo la argolla). */
    public static Vec3 punto(BlockPos a) {
        return new Vec3(a.getX() + 0.5, a.getY() + ANCLA_Y, a.getZ() + 0.5);
    }

    /** Dirección de la cadena (unitaria, hacia abajo) con esos ángulos. */
    public static Vec3 direccion(double ax, double az) {
        Vec3 u = new Vec3(Math.sin(ax), -Math.cos(ax) * Math.cos(az), Math.sin(az));
        return u.normalize();
    }

    /** Suelo de la jaula (posición de la entidad) colgando con esos valores. */
    public static Vec3 base(BlockPos a, double largo, double ax, double az) {
        return punto(a).add(direccion(ax, az).scale(largo + ALTO));
    }

    @Nullable
    public static JaulaEntity de(Level l, BlockPos ancla) {
        AABB zona = new AABB(ancla.getX() - 4, ancla.getY() - LARGO_MAX - 4, ancla.getZ() - 4, ancla.getX() + 5, ancla.getY() + 1, ancla.getZ() + 5);
        List<JaulaEntity> js = l.getEntitiesOfClass(JaulaEntity.class, zona, j -> ancla.equals(j.ancla()) && j.estado() == COLGANDO);
        return js.isEmpty() ? null : js.get(0);
    }

    public static JaulaEntity colgar(Level l, BlockPos ancla, Direction frente) {
        JaulaEntity j = new JaulaEntity(TorturaRegistro.JAULA.get(), l);
        j.entityData.set(ANCLA, Optional.of(ancla));
        j.setYRot(frente.toYRot());
        j.yRotO = j.getYRot();
        j.calcularMax();
        double L = Math.min(LARGO_INICIAL, j.maxLargo);
        j.objetivo = j.largoArriba = L;
        j.entityData.set(LARGO, (float) L);
        Vec3 b = base(ancla, L, 0, 0);
        j.setPos(b.x, b.y, b.z);
        l.addFreshEntity(j);
        l.playSound(null, ancla, TorturaRegistro.JAULA_CADENA.get(), SoundSource.BLOCKS, 1F, 0.9F);
        return j;
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickCliente();
            return;
        }
        byte est = estado();
        if (est == ROTA) {
            if (++rotaT > 600) discard();
            return;
        }
        if (est == CAYENDO) {
            tickCaida();
            return;
        }
        BlockPos a = ancla();
        if (a == null || (tickCount % 10 == 0 && !(level().getBlockState(a).getBlock() instanceof JaulaAnclaBlock))) {
            if (a == null) {
                discard();
                return;
            }
            romperCadena();
            return;
        }
        if (tickCount % 10 == 0 || maxLargo >= LARGO_MAX) calcularMax();
        // torno: la cadena sube o baja hacia el objetivo
        double L = largo(), obj = Mth.clamp(objetivo, LARGO_MIN, maxLargo);
        if (Math.abs(obj - L) > 1.0E-3) {
            L += Mth.clamp(obj - L, -VEL_TORNO, VEL_TORNO);
            entityData.set(LARGO, (float) L);
            if (tickCount % 5 == 0) level().playSound(null, getX(), getY() + ALTO, getZ(), TorturaRegistro.JAULA_CADENA.get(), SoundSource.BLOCKS, 0.5F,
                    obj < L ? 1.1F : 0.9F);
        }
        if (L > maxLargo) {
            L = maxLargo;
            entityData.set(LARGO, (float) L);
        }
        entityData.set(EN_SUELO, sueloServidor());
        pendulo(L);
        Vec3 b = base(a, L, ax(), az());
        setPos(b.x, b.y, b.z);
        LivingEntity p = preso();
        if (p instanceof Mob m) {
            m.setTarget(null);
            m.getNavigation().stop();
        }
        if (p == null && puerta() == ABIERTA && enSuelo() && tickCount % 4 == 0) trampa();
        if (p instanceof ServerPlayer sp && puerta() != ABIERTA && tickCount % 60 == 0 && !enSuelo())
            sp.displayClientMessage(Component.translatable(puerta() == CON_LLAVE ? "hud.miscelanea.jaula.llave" : "hud.miscelanea.jaula.cerrada", Component.keybind("key.left"), Component.keybind("key.right"))
                    .withStyle(ChatFormatting.GOLD), true);
    }

    private void tickCliente() {
        axO = axC;
        azO = azC;
        largoO = largoC;
        axC = ax();
        azC = az();
        largoC = largo();
        if (tickCount <= 1) {
            axO = axC;
            azO = azC;
            largoO = largoC;
        }
        puertaO = puertaA;
        float meta = puerta() == ABIERTA || estado() == ROTA ? 1F : 0F;
        puertaA += Mth.clamp(meta - puertaA, -0.12F, 0.12F);
        BlockPos a = ancla();
        if (a != null && estado() == COLGANDO) {
            Vec3 b = base(a, largoC, axC, azC);
            setPos(b.x, b.y, b.z);
        }
    }

    /** Péndulo con dos ángulos, rozamiento, el empujo del preso y la fatiga de la cadena. */
    private void pendulo(double L) {
        double ax = ax(), az = az();
        if (enSuelo()) {
            // apoyada en el suelo: se queda quieta
            wx *= 0.5;
            wz *= 0.5;
            ax *= 0.6;
            az *= 0.6;
        } else {
            double brazo = L + ALTO * 0.55;
            double w0 = G / brazo;
            double alx = -w0 * Math.sin(ax), alz = -w0 * Math.sin(az);
            LivingEntity p = preso();
            if (p instanceof Player pl && !pl.isSpectator()) {
                float xxa = Mth.clamp(pl.xxa, -1F, 1F);
                double yaw = Math.toRadians(pl.getYRot());
                double ix = Math.cos(yaw), iz = Math.sin(yaw);       // izquierda del jugador (A)
                double k = EMPUJE * w0 * xxa;
                alx += k * ix;
                alz += k * iz;
            } else if (p != null && tickCount % 20 == 0) {
                // un mob dentro se mueve un poco
                wx += (random.nextDouble() - 0.5) * 0.004;
                wz += (random.nextDouble() - 0.5) * 0.004;
            }
            wx = (wx + alx) * ROCE;
            wz = (wz + alz) * ROCE;
            ax = Mth.clamp(ax + wx, -1.35, 1.35);
            az = Mth.clamp(az + wz, -1.35, 1.35);
            sonidos(ax, az, brazo);
            fatigar(ax, az);
        }
        entityData.set(AX, (float) ax);
        entityData.set(AZ, (float) az);
    }

    private void sonidos(double ax, double az, double brazo) {
        double radial = wx * ax + wz * az;
        int s = radial > 1.0E-5 ? 1 : radial < -1.0E-5 ? -1 : radialSigno;
        double amp = Math.sqrt(ax * ax + az * az), vel = Math.sqrt(wx * wx + wz * wz) * brazo;
        if (s != radialSigno) {
            if (s < 0 && amp > 0.12) {
                // en el extremo: la cadena cruje
                level().playSound(null, getX(), getY() + ALTO, getZ(), TorturaRegistro.JAULA_CRUJIDO.get(), SoundSource.BLOCKS,
                        (float) Math.min(1.2, 0.25 + amp), 0.8F + random.nextFloat() * 0.3F + (float) fatiga() * 0.2F);
            } else if (s > 0 && vel > 0.07) {
                // pasando por abajo deprisa: zumbido del aire
                level().playSound(null, getX(), getY() + 1, getZ(), TorturaRegistro.JAULA_BALANCEO.get(), SoundSource.BLOCKS,
                        (float) Math.min(1.0, vel * 4), 0.9F + random.nextFloat() * 0.2F);
            }
            radialSigno = s;
        }
    }

    private void fatigar(double ax, double az) {
        double amp = Math.sqrt(ax * ax + az * az);
        float f = fatiga();
        if (amp > UMBRAL) f += (float) ((amp - UMBRAL) * FATIGA_K);
        else f = Math.max(0F, f - 0.0006F);
        entityData.set(FATIGA, Math.min(1F, f));
        LivingEntity p = preso();
        if (p instanceof ServerPlayer sp) {
            int aviso = f >= 0.8F ? 2 : f >= 0.45F ? 1 : 0;
            if (aviso > avisoFatiga)
                sp.displayClientMessage(Component.translatable(aviso == 2 ? "msg.miscelanea.jaula.cede" : "msg.miscelanea.jaula.cruje")
                        .withStyle(aviso == 2 ? ChatFormatting.RED : ChatFormatting.YELLOW), true);
            avisoFatiga = aviso;
        }
        if (f >= 1F) romperCadena();
    }

    private void calcularMax() {
        BlockPos a = ancla();
        if (a == null) return;
        Vec3 p = punto(a).add(0, -0.05, 0);
        BlockHitResult r = level().clip(new ClipContext(p, p.add(0, -(LARGO_MAX + ALTO + 1), 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double hasta = r.getType() == HitResult.Type.MISS ? LARGO_MAX + ALTO : p.y + 0.05 - r.getLocation().y;
        maxLargo = Math.max(LARGO_MIN, Math.min(LARGO_MAX, hasta - ALTO - 0.02));
    }

    /** Bajada hasta el suelo y abierta: el primero que entra andando queda atrapado. */
    private void trampa() {
        Vec3 b = position();
        AABB dentro = new AABB(b.x - 0.42, b.y - 0.1, b.z - 0.42, b.x + 0.42, b.y + 1.0, b.z + 0.42);
        long ahora = level().getGameTime();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, dentro, x -> x.isAlive() && !x.isPassenger() && !x.isSpectator())) {
            if (e instanceof Player pl && pl.isShiftKeyDown()) continue;
            if (e.getBbWidth() > 1.1F || e.getBbHeight() > 2.4F) continue;
            Long sale = salidas.get(e.getUUID());
            if (sale != null && ahora - sale < 60) continue;
            if (!e.position().closerThan(b, 0.45)) continue;
            meter(e, null);
            level().playSound(null, getX(), getY() + 1, getZ(), TorturaRegistro.JAULA_PUERTA.get(), SoundSource.BLOCKS, 1.2F, 0.8F);
            if (e instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("msg.miscelanea.jaula.atrapado").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            return;
        }
    }

    // ------------------------------------------------------------------ caer
    /** La cadena se rompe (o se arranca la argolla): la jaula cae. */
    public void romperCadena() {
        if (estado() != COLGANDO) return;
        double brazo = largo() + ALTO;
        entityData.set(ESTADO, CAYENDO);
        caidaY = getY();
        noPhysics = false;
        setDeltaMovement(wx * brazo, 0.02, wz * brazo);
        BlockPos a = ancla();
        Vec3 p = a == null ? position().add(0, ALTO, 0) : punto(a);
        level().playSound(null, p.x, p.y, p.z, TorturaRegistro.CADENA_ROTA.get(), SoundSource.BLOCKS, 1.4F, 0.9F);
        if (level() instanceof ServerLevel sl)
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CHAIN.defaultBlockState()), p.x, p.y - 0.3, p.z, 16, 0.1, 0.3, 0.1, 0.1);
        if (preso() instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.translatable("msg.miscelanea.jaula.rota").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
    }

    private void tickCaida() {
        Vec3 v = getDeltaMovement().scale(0.98).add(0, -0.08, 0);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        if (onGround() || (verticalCollision && v.y < 0)) {
            impacto();
            return;
        }
        if (tickCount > 20 * 60 * 60 || getY() < level().getMinBuildHeight() - 40) discard();
    }

    private void impacto() {
        double h = caidaY - getY();
        LivingEntity p = preso();
        entityData.set(ESTADO, ROTA);
        entityData.set(PUERTA, ABIERTA);
        setDeltaMovement(Vec3.ZERO);
        level().playSound(null, getX(), getY() + 0.5, getZ(), TorturaRegistro.JAULA_ROTA.get(), SoundSource.BLOCKS, 1.6F, 0.9F);
        if (level() instanceof ServerLevel sl)
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BARS.defaultBlockState()), getX(), getY() + 0.6, getZ(), 60, 0.5, 0.5, 0.5, 0.2);
        if (p != null) {
            float dano = h <= 1.0 ? 1F : (float) Math.min(40.0, 2.0 + (h - 1.0) * 2.5);
            permitir = true;
            ejectPassengers();
            permitir = false;
            p.hurt(damageSources().fall(), dano);
            if (p instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("msg.miscelanea.jaula.libre").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        }
    }

    // ------------------------------------------------------------------ montar
    @Override
    protected void positionRider(Entity e, Entity.MoveFunction mover) {
        if (!hasPassenger(e)) return;
        Vec3 pie;
        BlockPos a = ancla();
        if (estado() == COLGANDO && a != null) {
            float ax = level().isClientSide ? axC : ax(), az = level().isClientSide ? azC : az(), L = level().isClientSide ? largoC : largo();
            Vec3 u = direccion(ax, az);
            pie = punto(a).add(u.scale(L + ALTO - SUELO));
        } else {
            pie = position().add(0, SUELO, 0);
        }
        mover.accept(e, pie.x, pie.y, pie.z);
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    public boolean canRiderInteract() {
        return true;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty() && estado() == COLGANDO;
    }

    @Override
    protected void removePassenger(Entity e) {
        super.removePassenger(e);
        if (!level().isClientSide) salidas.put(e.getUUID(), level().getGameTime());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        Vec3 fuera = position().add(f.scale(0.95));
        if (level().noCollision(e, e.getBoundingBox().move(fuera.subtract(e.position())))) return fuera;
        return position().add(0, SUELO, 0);
    }

    /** Lo mete dentro y cierra la puerta. */
    public boolean meter(LivingEntity v, @Nullable Player captor) {
        CepoEntity.desatar(v);
        if (!v.startRiding(this, true)) return false;
        entityData.set(PUERTA, CERRADA);
        if (v instanceof Mob m) {
            m.setTarget(null);
            m.setAggressive(false);
        }
        if (v instanceof ServerPlayer sp) {
            com.miscelanea.tortura.qte.Qte.cancelar(sp);
            sp.sendSystemMessage(Component.translatable("msg.miscelanea.jaula.dentro").withStyle(ChatFormatting.RED));
        }
        if (captor != null)
            captor.displayClientMessage(Component.translatable("msg.miscelanea.jaula.metido", v.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        level().playSound(null, getX(), getY() + 1, getZ(), TorturaRegistro.JAULA_PUERTA.get(), SoundSource.BLOCKS, 1F, 1F);
        return true;
    }

    public void cerrarConLlave() {
        entityData.set(PUERTA, CON_LLAVE);
    }

    /** Lo saca (aunque la puerta esté cerrada): al desconectarse, morir... */
    public void sacar(Entity e) {
        permitir = true;
        e.stopRiding();
        permitir = false;
    }

    /** Sube (delta < 0) o baja (delta > 0) la jaula 'delta' bloques. */
    public void mover(Player p, double delta) {
        calcularMax();
        double antes = objetivo;
        objetivo = Mth.clamp(objetivo + delta, LARGO_MIN, maxLargo);
        if (delta < 0) largoArriba = objetivo;
        String clave = Math.abs(objetivo - antes) < 1.0E-3 ? (delta > 0 ? "msg.miscelanea.jaula.tope_abajo" : "msg.miscelanea.jaula.tope_arriba")
                : delta > 0 ? "msg.miscelanea.jaula.baja" : "msg.miscelanea.jaula.sube";
        p.displayClientMessage(Component.translatable(clave, String.format("%.1f", objetivo)).withStyle(ChatFormatting.GRAY), true);
    }

    // ------------------------------------------------------------------ uso (clic derecho)
    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (estado() != COLGANDO) return InteractionResult.PASS;
        ItemStack st = p.getItemInHand(hand);
        byte pu = puerta();
        // desde dentro
        if (hasPassenger(p)) {
            if (pu == CON_LLAVE && !(st.getItem() instanceof LlaveCarceleroItem)) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.jaula.encerrado", Component.keybind("key.left"), Component.keybind("key.right")).withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            entityData.set(PUERTA, ABIERTA);
            sacar(p);
            level().playSound(null, getX(), getY() + 1, getZ(), TorturaRegistro.JAULA_PUERTA.get(), SoundSource.BLOCKS, 0.8F, 1.15F);
            return InteractionResult.CONSUME;
        }
        if (st.getItem() instanceof LlaveCarceleroItem) {
            if (pu == CON_LLAVE) {
                entityData.set(PUERTA, CERRADA);
                p.displayClientMessage(Component.translatable("msg.miscelanea.jaula.sin_llave").withStyle(ChatFormatting.GRAY), true);
            } else {
                entityData.set(PUERTA, CON_LLAVE);
                p.displayClientMessage(Component.translatable("msg.miscelanea.jaula.con_llave").withStyle(ChatFormatting.GOLD), true);
                if (preso() instanceof ServerPlayer sp)
                    sp.displayClientMessage(Component.translatable("msg.miscelanea.jaula.balanceo", Component.keybind("key.left"), Component.keybind("key.right")).withStyle(ChatFormatting.YELLOW), false);
            }
            level().playSound(null, getX(), getY() + 1, getZ(), TorturaRegistro.JAULA_CERROJO.get(), SoundSource.BLOCKS, 1F, pu == CON_LLAVE ? 1.1F : 0.9F);
            return InteractionResult.CONSUME;
        }
        if (st.isEmpty() && p.isShiftKeyDown()) {
            calcularMax();
            if (largo() >= maxLargo - 0.3) objetivo = Math.min(largoArriba, maxLargo);
            else {
                largoArriba = largo();
                objetivo = maxLargo;
            }
            p.displayClientMessage(Component.translatable(objetivo >= maxLargo - 0.01 ? "msg.miscelanea.jaula.al_suelo" : "msg.miscelanea.jaula.arriba")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        LivingEntity cautivo = CepoEntity.cautivoDe(p);
        if (cautivo != null && !(cautivo.getVehicle() instanceof JaulaEntity)) {
            if (preso() != null) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.jaula.ocupada").withStyle(ChatFormatting.RED), true);
            } else if (pu == CON_LLAVE) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.jaula.abre_primero").withStyle(ChatFormatting.RED), true);
            } else if (cautivo.getBbHeight() > 2.4F || cautivo.getBbWidth() > 1.1F) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.cepo.no_cabe").withStyle(ChatFormatting.GRAY), true);
            } else {
                meter(cautivo, p);
            }
            return InteractionResult.CONSUME;
        }
        if (st.isEmpty()) {
            if (pu == CON_LLAVE) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.jaula.cerrada_llave").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            entityData.set(PUERTA, pu == ABIERTA ? CERRADA : ABIERTA);
            level().playSound(null, getX(), getY() + 1, getZ(), TorturaRegistro.JAULA_PUERTA.get(), SoundSource.BLOCKS, 0.9F, pu == ABIERTA ? 0.95F : 1.15F);
            // con la puerta abierta, los mobs salen solos
            if (pu != ABIERTA && preso() instanceof Mob m) sacar(m);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource s, float d) {
        // creativo + Shift + golpe: quitar la jaula
        if (!level().isClientSide && s.getEntity() instanceof Player p && p.getAbilities().instabuild && p.isShiftKeyDown()) {
            permitir = true;
            ejectPassengers();
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    // ------------------------------------------------------------------ guardar
    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        if (t.contains("Ancla")) entityData.set(ANCLA, Optional.of(NbtUtils.readBlockPos(t.getCompound("Ancla"))));
        entityData.set(LARGO, t.getFloat("Largo"));
        objetivo = t.getDouble("Objetivo");
        largoArriba = t.contains("Arriba") ? t.getDouble("Arriba") : LARGO_INICIAL;
        entityData.set(PUERTA, t.getByte("Puerta"));
        entityData.set(ESTADO, t.getByte("Estado"));
        entityData.set(FATIGA, t.getFloat("Fatiga"));
        caidaY = t.getDouble("CaidaY");
        if (estado() != COLGANDO) noPhysics = false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        BlockPos a = ancla();
        if (a != null) t.put("Ancla", NbtUtils.writeBlockPos(a));
        t.putFloat("Largo", largo());
        t.putDouble("Objetivo", objetivo);
        t.putDouble("Arriba", largoArriba);
        t.putByte("Puerta", puerta());
        t.putByte("Estado", estado());
        t.putFloat("Fatiga", fatiga());
        t.putDouble("CaidaY", caidaY);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

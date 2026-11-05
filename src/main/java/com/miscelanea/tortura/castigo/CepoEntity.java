package com.miscelanea.tortura.castigo;

import com.miscelanea.tortura.Cautivos;
import com.miscelanea.tortura.Grilletes;
import com.miscelanea.tortura.Lazo;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.heridas.Herida;
import com.miscelanea.tortura.heridas.Parte;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Asiento invisible del cepo: la víctima va "montada" en él (de pie, sin poder moverse ni bajarse) con la cabeza y las
 * manos asomando por los agujeros de la tabla. Se suelta sola a los 60 s, o la suelta quien la encerró (o cualquiera con
 * la llave del carcelero, o en creativo). Si rompen el cepo, queda libre.
 * <p>Geometría (en bloques, desde el centro del bloque; f = hacia donde mira la víctima): la cara delantera de la tabla
 * está a {@link #TABLA} por delante del centro; los pies de verdad, {@link #PIES} por detrás de ella; el cuerpo se dobla
 * 70º hacia delante (AnimCastigo / PoseCastigo) y la cabeza queda atravesando la tabla. La entidad del jugador (y su
 * cámara) va en la cabeza: {@link #ADELANTE} por delante de los pies y {@link #BAJADA} más abajo, y la animación devuelve
 * el modelo a su sitio. Medidas = scratchpad/gen_tortura3a.py.
 */
public class CepoEntity extends Entity {
    private static final EntityDataAccessor<Optional<BlockPos>> BLOQUE = SynchedEntityData.defineId(CepoEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    public static final int DURACION = 60 * 20;
    public static final double TABLA = (8 - 6.5) / 16.0, PIES = 0.80, ADELANTE = 0.92, BAJADA = 0.37;

    private int quedan = com.miscelanea.ConfigServidor.cepoTicks();
    @Nullable private UUID captor;
    private boolean permitir;

    public CepoEntity(EntityType<? extends CepoEntity> t, Level l) {
        super(t, l);
        noPhysics = true;
    }

    CepoEntity(Level l, BlockPos pos, Direction mira) {
        this(TorturaRegistro.CEPO_ASIENTO.get(), l);
        entityData.set(BLOQUE, Optional.of(pos));
        setYRot(mira.toYRot());
        Vec3 c = Vec3.atBottomCenterOf(pos);
        setPos(c.x, c.y, c.z);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(BLOQUE, Optional.empty());
    }

    @Nullable
    public BlockPos bloque() {
        return entityData.get(BLOQUE).orElse(null);
    }

    @Nullable
    public LivingEntity victima() {
        for (Entity e : getPassengers()) if (e instanceof LivingEntity le) return le;
        return null;
    }

    public boolean dejaBajar() {
        return permitir;
    }

    /** Jugadores y mobs con modelo humanoide (zombis, esqueletos, piglins): se doblan con la cabeza por el agujero. Los demás solo se quedan quietos detrás. */
    public static boolean humanoide(Entity e) {
        return e instanceof Player || e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof AbstractPiglin;
    }

    public Vec3 frente() {
        return Vec3.directionFromRotation(0, getYRot());
    }

    public Vec3 derecha() {
        Vec3 f = frente();
        return new Vec3(-f.z, 0, f.x);
    }

    /** Los pies de verdad de la víctima (doblada detrás de la tabla). */
    public Vec3 pies() {
        BlockPos b = bloque();
        Vec3 c = b == null ? position() : Vec3.atBottomCenterOf(b);
        return c.add(frente().scale(TABLA - PIES));
    }

    /** Dónde va la entidad montada (sus pies "lógicos"). */
    Vec3 sitio(Entity e) {
        Vec3 f = frente();
        if (humanoide(e)) return pies().add(f.scale(ADELANTE)).add(0, -BAJADA, 0);
        BlockPos b = bloque();
        Vec3 c = b == null ? position() : Vec3.atBottomCenterOf(b);
        return c.add(f.scale(TABLA - 0.12 - e.getBbWidth() * 0.5));
    }

    /** Puntos donde se le puede dar (cabeza, mano derecha, mano izquierda, espalda), en el mundo. */
    public Vec3[] puntosGolpe() {
        LivingEntity v = victima();
        Vec3 f = frente(), d = derecha();
        if (v != null && !humanoide(v)) {
            Vec3 cab = v.getEyePosition().add(f.scale(0.15));
            Vec3 lejos = cab.add(0, -50, 0);
            return new Vec3[]{cab, lejos, lejos, v.position().add(0, v.getBbHeight() * 0.6, 0)};
        }
        Vec3 p = pies();
        Vec3 cab = p.add(f.scale(0.75)).add(0, 1.22, 0);
        Vec3 manoD = p.add(f.scale(1.09)).add(d.scale(0.8)).add(0, 0.92, 0);
        Vec3 manoI = p.add(f.scale(1.09)).add(d.scale(-0.8)).add(0, 0.92, 0);
        Vec3 esp = p.add(f.scale(0.38)).add(0, 0.95, 0);
        return new Vec3[]{cab, manoD, manoI, esp};
    }

    /**
     * Deja la mancha del impacto 'cual' (0 cabeza, 1 mano derecha, 2 mano izquierda, 3 espalda) en el punto dado y
     * devuelve la parte del cuerpo.
     */
    Parte manchar(LivingEntity v, int cual, Vec3 punto, boolean huevo) {
        Vec3 c = puntosGolpe()[cual], rel = punto.subtract(c);
        double lat = rel.dot(derecha()), alto = rel.y;
        Parte parte;
        int cara, fx, fy;
        switch (cual) {
            case 0 -> {
                parte = Parte.CABEZA;
                cara = Herida.FRENTE;
                fx = (int) (Mth.clamp(0.5 - lat / 0.5, 0, 1) * 255);     // de frente: su derecha es nuestra izquierda
                fy = (int) (Mth.clamp(0.5 - alto / 0.5, 0, 1) * 255);
            }
            case 1, 2 -> {
                parte = cual == 1 ? Parte.BRAZO_DER : Parte.BRAZO_IZQ;
                cara = cual == 1 ? Herida.DERECHA : Herida.IZQUIERDA;
                fx = v.getRandom().nextInt(256);
                fy = 190 + v.getRandom().nextInt(60);
            }
            default -> {
                parte = Parte.TORSO;
                cara = Herida.ESPALDA;
                fx = (int) (Mth.clamp(0.5 + lat / 0.5, 0, 1) * 255);
                fy = 40 + v.getRandom().nextInt(170);
            }
        }
        if (v instanceof ServerPlayer sp) Manchas.agregarEn(sp, parte, cara, fx, fy, huevo);
        return parte;
    }

    // ------------------------------------------------------------------ montar
    @Override
    protected void positionRider(Entity e, Entity.MoveFunction mover) {
        if (!hasPassenger(e)) return;
        Vec3 s = sitio(e);
        mover.accept(e, s.x, s.y, s.z);
        encarar(e);
    }

    /** El cuerpo mira hacia delante del cepo; la cabeza apenas gira (está en el agujero). */
    private void encarar(Entity e) {
        e.setYBodyRot(getYRot());
        float f = Mth.wrapDegrees(e.getYRot() - getYRot());
        float g = Mth.clamp(f, -35.0F, 35.0F);
        e.yRotO += g - f;
        e.setYRot(e.getYRot() + g - f);
        e.setYHeadRot(e.getYRot());
    }

    @Override
    public void onPassengerTurned(Entity e) {
        encarar(e);
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        return pies().add(frente().scale(-0.25));
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        BlockPos b = bloque();
        if (b == null || !(level().getBlockState(b).getBlock() instanceof CepoBlock)) {
            liberar(null, false);
            discard();
            return;
        }
        LivingEntity v = victima();
        if (v == null) {
            if (tickCount > 4) {
                cerrarBloque(false);
                discard();
            }
            return;
        }
        if (v instanceof Mob m) {
            if (m.getTarget() != null) m.setTarget(null);
            if (m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            m.getNavigation().stop();
        }
        if (--quedan <= 0) {
            liberar(null, true);
            return;
        }
        if (v instanceof ServerPlayer sp && quedan % 20 == 0)
            sp.displayClientMessage(Component.translatable("hud.miscelanea.cepo", (quedan + 19) / 20).withStyle(ChatFormatting.GOLD), true);
    }

    private void cerrarBloque(boolean cerrado) {
        BlockPos b = bloque();
        if (b == null) return;
        BlockState st = level().getBlockState(b);
        if (st.getBlock() instanceof CepoBlock && st.getValue(CepoBlock.CERRADO) != cerrado) level().setBlock(b, st.setValue(CepoBlock.CERRADO, cerrado), 3);
    }

    /** Suelta a la víctima (quien: el que abre, o null si se acaba el tiempo / se rompe el cepo). */
    public void liberar(@Nullable Player quien, boolean conSonido) {
        LivingEntity v = victima();
        permitir = true;
        ejectPassengers();
        permitir = false;
        cerrarBloque(false);
        if (conSonido) level().playSound(null, getX(), getY() + 1, getZ(), TorturaRegistro.CEPO_ABRIR.get(), SoundSource.BLOCKS, 1F, 1F);
        if (v != null) {
            Escarnio.terminar(v);
            if (v instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("msg.miscelanea.cepo.libre").withStyle(ChatFormatting.GREEN), true);
            if (quien != null && quien != v)
                quien.displayClientMessage(Component.translatable("msg.miscelanea.cepo.soltado", v.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
        }
        discard();
    }

    // ------------------------------------------------------------------ uso del bloque (servidor)
    @Nullable
    static CepoEntity buscar(Level l, BlockPos pos) {
        List<CepoEntity> l2 = l.getEntitiesOfClass(CepoEntity.class, new AABB(pos).inflate(2.5), c -> pos.equals(c.bloque()));
        return l2.isEmpty() ? null : l2.get(0);
    }

    /** Un cautivo que 'p' lleva (a hombros, a rastras con la cadena o enlazado) o null. */
    @Nullable
    public static LivingEntity cautivoDe(Player p) {
        LivingEntity c = Lazo.cargado(p);
        if (c != null) return c;
        if (!(p.level() instanceof ServerLevel sl)) return null;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(12), x -> x != p && x.isAlive())) {
            UUID cad = Cautivos.cadena(e);
            if (cad != null && cad.equals(p.getUUID())) return e;
            if (Lazo.estado(e) == Lazo.ENLAZADO && Lazo.esCaptor(e, p)) return e;
        }
        return null;
    }

    /** Lo suelta de lo que le tenga sujeto (hombro, cadena, lazo) para meterlo en el cepo o en la jaula. */
    public static void desatar(LivingEntity v) {
        if (v.isPassenger()) {
            Entity veh = v.getVehicle();
            v.stopRiding();
            if (veh instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetPassengersPacket(sp));
        }
        if (Cautivos.cadena(v) != null) Grilletes.cadena(v, null);
        if (Lazo.estado(v) != 0) Lazo.limpiar(v);
    }

    static boolean valido(LivingEntity v) {
        return v.isAlive() && !v.isSpectator() && v.getBbHeight() <= 2.3F && v.getBbWidth() <= 1.2F && !(v.getVehicle() instanceof CepoEntity)
                && !(v.getVehicle() instanceof JaulaEntity) && !com.miscelanea.tortura.TrampaOso.ejecutando(v);
    }

    public static void usar(ServerPlayer p, BlockPos pos, BlockState st, ItemStack it) {
        Level l = p.level();
        CepoEntity c = buscar(l, pos);
        LivingEntity dentro = c == null ? null : c.victima();
        if (dentro != null) {
            if (dentro == p) {
                p.displayClientMessage(Component.translatable("msg.miscelanea.cepo.atrapado").withStyle(ChatFormatting.RED), true);
                return;
            }
            boolean puede = it.getItem() instanceof LlaveCarceleroItem || p.getAbilities().instabuild || (c.captor != null && c.captor.equals(p.getUUID()));
            if (puede) c.liberar(p, true);
            else p.displayClientMessage(Component.translatable("msg.miscelanea.cepo.candado").withStyle(ChatFormatting.RED), true);
            return;
        }
        Direction mira = st.getValue(CepoBlock.FACING);
        LivingEntity v = cautivoDe(p);
        if (v == null) {
            // alguien empujado al sitio de la víctima (detrás de la tabla)
            CepoEntity tmp = new CepoEntity(l, pos, mira);
            Vec3 pie = tmp.pies();
            AABB zona = new AABB(pie.x - 0.5, pie.y - 0.2, pie.z - 0.5, pie.x + 0.5, pie.y + 1.2, pie.z + 0.5);
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, zona, x -> x != p && valido(x) && !x.isPassenger())) {
                v = e;
                break;
            }
        }
        if (v == null) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.cepo.nadie").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!valido(v)) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.cepo.no_cabe").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        encerrar(l, pos, mira, v, p);
    }

    static void encerrar(Level l, BlockPos pos, Direction mira, LivingEntity v, @Nullable Player captor) {
        desatar(v);
        CepoEntity c = new CepoEntity(l, pos, mira);
        if (captor != null) c.captor = captor.getUUID();
        l.addFreshEntity(c);
        if (!v.startRiding(c, true)) {
            c.discard();
            return;
        }
        c.cerrarBloque(true);
        if (v instanceof Mob m) {
            m.setTarget(null);
            m.setAggressive(false);
        }
        if (v instanceof ServerPlayer sp) com.miscelanea.tortura.qte.Qte.cancelar(sp);
        Escarnio.iniciar(v);
        l.playSound(null, pos, TorturaRegistro.CEPO_CERRAR.get(), SoundSource.BLOCKS, 1F, 1F);
        if (v instanceof ServerPlayer sp) sp.sendSystemMessage(Component.translatable("msg.miscelanea.cepo.encerrado_tu", Component.keybind("key.left"), Component.keybind("key.right")).withStyle(ChatFormatting.RED));
        if (captor != null)
            captor.displayClientMessage(Component.translatable("msg.miscelanea.cepo.encerrado", v.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
    }

    // ------------------------------------------------------------------ guardar
    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        if (t.contains("Bloque")) entityData.set(BLOQUE, Optional.of(NbtUtils.readBlockPos(t.getCompound("Bloque"))));
        quedan = t.contains("Quedan") ? t.getInt("Quedan") : com.miscelanea.ConfigServidor.cepoTicks();
        captor = t.hasUUID("Captor") ? t.getUUID("Captor") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        BlockPos b = bloque();
        if (b != null) t.put("Bloque", NbtUtils.writeBlockPos(b));
        t.putInt("Quedan", quedan);
        if (captor != null) t.putUUID("Captor", captor);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

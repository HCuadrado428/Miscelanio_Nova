package com.miscelanea.tortura.aparatos;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Base de los aparatos: quién está dentro (UUID e id de entidad sincronizado), quién lo metió (el captor), el asiento
 * invisible que lo sujeta y el ciclo meter / tick / sacar. Cada aparato añade su lógica en {@link #tickAparato},
 * {@link #tickVictima} y {@link #usarExtra} y sus datos en {@link #guardar}/{@link #cargar} (se guardan y se sincronizan).
 * <ul>
 *   <li>Meter: clic derecho al aparato con un cautivo (a hombros, con la cadena o enlazado) o las correas sobre alguien.</li>
 *   <li>Sacar: Shift + clic derecho con la mano vacía (el captor o alguien en creativo), romper el aparato, o al morir,
 *       desconectarse o cambiar de dimensión la víctima.</li>
 * </ul>
 */
public abstract class AparatoBlockEntity extends BlockEntity {
    @Nullable protected UUID victima, captor;
    protected int victimaId = -1;
    protected boolean victimaJugador;
    private int perdida;
    private boolean pendiente;
    private int esperaSync;
    private int fallosMontar;

    protected AparatoBlockEntity(BlockEntityType<?> t, BlockPos p, BlockState s) {
        super(t, p, s);
    }

    /** {@link AparatosVictimas#DONCELLA}, {@link AparatosVictimas#POTRO} o {@link AparatosVictimas#SILLA}. */
    public abstract int tipo();

    /** Dónde quedan los pies de la víctima (posición del asiento). */
    public abstract Vec3 sitioVictima(LivingEntity v);

    /** Hacia dónde mira el cuerpo de la víctima. */
    public float yawVictima() {
        return frente().toYRot();
    }

    /** ¿La víctima va sentada (pose de montar)? */
    public abstract boolean sentado();

    /** Lo que puede girar la cabeza respecto al cuerpo (grados). */
    public float giroCabeza() {
        return 90F;
    }

    protected abstract void usarExtra(Player p, boolean anexo, BlockHitResult hit);

    protected void tickAparato(ServerLevel sl) {}

    protected abstract void tickVictima(ServerLevel sl, LivingEntity v);

    protected void alMeter(LivingEntity v, @Nullable Player quien) {}

    /** Al salir (v puede ser null si ya no está cargada). */
    protected void alSacar(@Nullable LivingEntity v) {}

    protected void tickVisual() {}

    protected void guardar(CompoundTag t) {}

    protected void cargar(CompoundTag t) {}

    // ------------------------------------------------------------------ consultas
    public Direction frente() {
        BlockState s = getBlockState();
        return s.hasProperty(AparatoBlock.FACING) ? s.getValue(AparatoBlock.FACING) : Direction.NORTH;
    }

    public Vec3 centro() {
        return Vec3.atBottomCenterOf(worldPosition);
    }

    public boolean ocupado() {
        return victima != null;
    }

    public int victimaId() {
        return victimaId;
    }

    public boolean victimaJugador() {
        return victimaJugador;
    }

    @Nullable
    public UUID captor() {
        return captor;
    }

    public boolean esVictima(Entity e) {
        return victima != null && victima.equals(e.getUUID());
    }

    @Nullable
    public LivingEntity victimaEntidad() {
        if (victima == null || !(level instanceof ServerLevel sl)) return null;
        return sl.getEntity(victima) instanceof LivingEntity le ? le : null;
    }

    public boolean puedeManejar(Player p) {
        return p.getAbilities().instabuild || captor == null || captor.equals(p.getUUID());
    }

    /** Donde se deja a la víctima al salir: delante del aparato si cabe; si no, encima. */
    public Vec3 salida(LivingEntity v) {
        if (level == null) return v.position();
        BlockPos f = worldPosition.relative(frente());
        Vec3 delante = Vec3.atBottomCenterOf(f);
        if (level.noCollision(v, v.getDimensions(v.getPose()).makeBoundingBox(delante))) return delante;
        BlockPos arriba = worldPosition.above(tipo() == AparatosVictimas.DONCELLA ? 2 : 1);
        return Vec3.atBottomCenterOf(arriba);
    }

    // ------------------------------------------------------------------ uso
    public void usar(Player p, boolean anexo, BlockHitResult hit) {
        if (esVictima(p)) return;
        if (p.isShiftKeyDown()) {
            if (!ocupado()) return;
            if (puedeManejar(p)) {
                LivingEntity v = victimaEntidad();
                sacar(true);
                p.displayClientMessage(Component.translatable("msg.miscelanea.aparato.sacado",
                        v == null ? Component.literal("?") : v.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            } else {
                p.displayClientMessage(Component.translatable("msg.miscelanea.aparato.no_tuyo").withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        if (!ocupado()) {
            LivingEntity c = Aparatos.cautivoDe(p);
            if (c != null) {
                meter(c, p);
                return;
            }
        }
        usarExtra(p, anexo, hit);
    }

    // ------------------------------------------------------------------ meter / sacar
    public boolean meter(LivingEntity v, @Nullable Player quien) {
        if (!(level instanceof ServerLevel sl) || ocupado() || Aparatos.en(v) || !Aparatos.admisible(v)) {
            if (quien != null)
                quien.displayClientMessage(Component.translatable("msg.miscelanea.aparato.no_cabe").withStyle(ChatFormatting.RED), true);
            return false;
        }
        Aparatos.prepararCautivo(v);
        victima = v.getUUID();
        victimaId = v.getId();
        victimaJugador = v instanceof Player;
        captor = quien == null ? null : quien.getUUID();
        perdida = 0;
        Aparatos.marcar(v, worldPosition);
        montar(sl, v);
        if (v instanceof Mob m) {
            m.setTarget(null);
            m.getNavigation().stop();
            m.setPersistenceRequired();
        }
        alMeter(v, quien);
        if (v instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.translatable("msg.miscelanea.aparato.dentro_" + tipo()).withStyle(ChatFormatting.DARK_RED), true);
        if (quien != null)
            quien.displayClientMessage(Component.translatable("msg.miscelanea.aparato.metido_" + tipo(), v.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        sonido(com.miscelanea.tortura.TorturaRegistro.GRILLETES_PONER.get(), 0.9F, 0.8F);
        sincronizar();
        return true;
    }

    private void montar(ServerLevel sl, LivingEntity v) {
        if (v.isPassenger()) {
            Aparatos.LIBERANDO.add(v.getUUID());
            try {
                v.stopRiding();
            } finally {
                Aparatos.LIBERANDO.remove(v.getUUID());
            }
        }
        Vec3 s = sitioVictima(v);
        AsientoAparato a = new AsientoAparato(sl, worldPosition, s, yawVictima(), sentado(), giroCabeza());
        sl.addFreshEntity(a);
        v.setYRot(yawVictima());
        v.setYBodyRot(yawVictima());
        v.setYHeadRot(yawVictima());
        if (!v.startRiding(a, true)) {
            a.discard();
            fallosMontar++;
        } else fallosMontar = 0;
    }

    /** Saca a la víctima (si la hay) y la deja delante. */
    public void sacar(boolean conSonido) {
        if (victima == null) return;
        LivingEntity v = victimaEntidad();
        victima = null;
        victimaId = -1;
        captor = null;
        alSacar(v);
        if (v != null) {
            v.getPersistentData().remove(Aparatos.CLAVE);
            Entity veh = v.getVehicle();
            Aparatos.LIBERANDO.add(v.getUUID());
            try {
                v.stopRiding();
            } finally {
                Aparatos.LIBERANDO.remove(v.getUUID());
            }
            if (veh instanceof AsientoAparato) veh.discard();
            if (v.isAlive()) {
                Vec3 s = salida(v);
                if (v instanceof ServerPlayer sp) sp.connection.teleport(s.x, s.y, s.z, sp.getYRot(), sp.getXRot());
                else v.teleportTo(s.x, s.y, s.z);
            }
        }
        if (level instanceof ServerLevel sl) {
            for (AsientoAparato a : sl.getEntitiesOfClass(AsientoAparato.class, new AABB(worldPosition).inflate(3), x -> x.esDe(worldPosition)))
                a.discard();
        }
        if (conSonido) sonido(com.miscelanea.tortura.TorturaRegistro.GRILLETES_QUITAR.get(), 0.9F, 0.8F);
        sincronizar();
    }

    // ------------------------------------------------------------------ tick
    public static void tickServidor(Level l, BlockPos p, BlockState s, AparatoBlockEntity be) {
        if (l instanceof ServerLevel sl) be.tickS(sl);
    }

    public static void tickCliente(Level l, BlockPos p, BlockState s, AparatoBlockEntity be) {
        if (be.victimaId >= 0) AparatosVictimas.poner(be.victimaId, p, be.tipo());
        be.tickVisual();
    }

    private void tickS(ServerLevel sl) {
        if (victima != null) {
            Entity e = sl.getEntity(victima);
            if (e instanceof LivingEntity v && v.isAlive() && !v.isRemoved()) {
                perdida = 0;
                if (v.getId() != victimaId) {
                    victimaId = v.getId();
                    marcar();
                }
                if (!v.getPersistentData().contains(Aparatos.CLAVE)) Aparatos.marcar(v, worldPosition);
                if (!(v.getVehicle() instanceof AsientoAparato a) || !a.esDe(worldPosition)) {
                    if (fallosMontar > 20) {
                        sacar(false);   // algo (otro mod) no le deja montar: mejor soltarlo que crear asientos sin fin
                        return;
                    }
                    if (v.tickCount % 5 == 0) montar(sl, v);
                }
                if (v.tickCount % 40 == 0) marcar();   // por si alguien acaba de cargar el aparato
                tickVictima(sl, v);
            } else {
                // jugador que se ha ido (se suelta al desconectarse, pero por si acaso) o mob muerto / fuera de carga
                boolean jugadorFuera = victimaJugador && sl.getServer().getPlayerList().getPlayer(victima) == null;
                if (jugadorFuera || (e != null && !e.isAlive()) || ++perdida > 200) sacar(false);
            }
        }
        tickAparato(sl);
        if (esperaSync > 0) esperaSync--;
        if (pendiente && esperaSync <= 0) sincronizar();
    }

    /** Sincronizar con los clientes en cuanto se pueda (como mucho cada 2 ticks). */
    protected void marcar() {
        pendiente = true;
    }

    protected void sincronizar() {
        pendiente = false;
        esperaSync = 2;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    protected void sonido(SoundEvent s, float vol, float tono) {
        if (level == null) return;
        Vec3 c = centro();
        level.playSound(null, c.x, c.y + 0.8, c.z, s, SoundSource.BLOCKS, vol, tono);
    }

    // ------------------------------------------------------------------ guardar / sincronizar
    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (victima != null) t.putUUID("Victima", victima);
        if (captor != null) t.putUUID("Captor", captor);
        t.putInt("VictimaId", victimaId);
        t.putBoolean("Jugador", victimaJugador);
        guardar(t);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        victima = t.hasUUID("Victima") ? t.getUUID("Victima") : null;
        captor = t.hasUUID("Captor") ? t.getUUID("Captor") : null;
        int antes = victimaId;
        victimaId = victima == null ? -1 : t.getInt("VictimaId");
        if (antes >= 0 && antes != victimaId && level != null && level.isClientSide) AparatosVictimas.quitar(antes);
        victimaJugador = t.getBoolean("Jugador");
        cargar(t);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.5);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide && victimaId >= 0) AparatosVictimas.quitar(victimaId);
    }
}

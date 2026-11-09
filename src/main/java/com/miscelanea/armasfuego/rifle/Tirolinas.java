package com.miscelanea.armasfuego.rifle;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tirolinas tendidas con la bala de cuerda. No son entidades ni bloques: cada mundo (dimensión) guarda su lista en
 * {@code data/miscelanea_tirolinas_rifle.dat} (sobrevive a cerrar el mundo) y se manda entera a los jugadores de esa
 * dimensión cuando cambia (son pocas y pequeñas). Solo existe una entidad mientras alguien baja colgado
 * ({@link GanchoTirolina}).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public class Tirolinas extends SavedData {
    private static final String NOMBRE = "miscelanea_tirolinas_rifle";
    /** Tope de tirolinas por dimensión (para que nadie llene el mundo de cuerdas). */
    public static final int MAX = 256;

    /** Una tirolina: puntas a (donde estaba el tirador) y b (donde dio la bala), y los bloques a los que se atan. */
    public record Linea(int id, Vec3 a, Vec3 b, @Nullable BlockPos anclaA, BlockPos anclaB, int nivel) {
        public double largo() {
            return a.distanceTo(b);
        }
    }

    private final List<Linea> lineas = new ArrayList<>();
    private int siguiente = 1;

    public static Tirolinas de(ServerLevel l) {
        return l.getDataStorage().computeIfAbsent(Tirolinas::cargar, Tirolinas::new, NOMBRE);
    }

    public List<Linea> lineas() {
        return lineas;
    }

    @Nullable
    public static Linea buscar(ServerLevel l, int id) {
        for (Linea x : de(l).lineas) if (x.id == id) return x;
        return null;
    }

    // ------------------------------------------------------------------ guardar / cargar

    public static Tirolinas cargar(CompoundTag t) {
        Tirolinas d = new Tirolinas();
        d.siguiente = Math.max(1, t.getInt("Siguiente"));
        ListTag l = t.getList("Lineas", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            Vec3 a = new Vec3(c.getDouble("ax"), c.getDouble("ay"), c.getDouble("az"));
            Vec3 b = new Vec3(c.getDouble("bx"), c.getDouble("by"), c.getDouble("bz"));
            BlockPos pa = c.contains("anclaA") ? BlockPos.of(c.getLong("anclaA")) : null;
            d.lineas.add(new Linea(c.getInt("id"), a, b, pa, BlockPos.of(c.getLong("anclaB")), c.getInt("nivel")));
            d.siguiente = Math.max(d.siguiente, c.getInt("id") + 1);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        for (Linea x : lineas) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", x.id);
            c.putDouble("ax", x.a.x);
            c.putDouble("ay", x.a.y);
            c.putDouble("az", x.a.z);
            c.putDouble("bx", x.b.x);
            c.putDouble("by", x.b.y);
            c.putDouble("bz", x.b.z);
            if (x.anclaA != null) c.putLong("anclaA", x.anclaA.asLong());
            c.putLong("anclaB", x.anclaB.asLong());
            c.putInt("nivel", x.nivel);
            l.add(c);
        }
        t.put("Lineas", l);
        t.putInt("Siguiente", siguiente);
        return t;
    }

    // ------------------------------------------------------------------ crear / quitar

    /** La bala de cuerda ha dado en un bloque: tiende la tirolina si la distancia vale. */
    public static boolean crear(ServerLevel l, ServerPlayer p, Vec3 a, @Nullable BlockPos anclaA, Vec3 b, BlockPos anclaB, int nivel, double max) {
        Tirolinas d = de(l);
        double largo = a.distanceTo(b);
        if (largo < 3) return false;
        if (largo > max + 0.5) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.rifle.cuerda_lejos", (int) max).withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (d.lineas.size() >= MAX) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.rifle.cuerda_tope", MAX).withStyle(ChatFormatting.RED), true);
            return false;
        }
        d.lineas.add(new Linea(d.siguiente++, a, b, anclaA, anclaB, nivel));
        d.setDirty();
        l.playSound(null, b.x, b.y, b.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
        l.playSound(null, a.x, a.y, a.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
        p.displayClientMessage(Component.translatable("msg.miscelanea.rifle.cuerda_lista", Math.round(largo)).withStyle(ChatFormatting.GREEN), true);
        com.miscelanea.logros.LogrosMiscelanea.dar(p, "rifle_cuerda");
        enviar(l);
        return true;
    }

    private void quitar(ServerLevel l, Linea x, @Nullable Vec3 soltarEn) {
        lineas.remove(x);
        setDirty();
        if (soltarEn != null) {
            ItemEntity e = new ItemEntity(l, soltarEn.x, soltarEn.y, soltarEn.z, new ItemStack(ModItems.BALA_CUERDA.get()));
            l.addFreshEntity(e);
        }
        l.playSound(null, x.b.x, x.b.y, x.b.z, SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Unas tijeras cortan la tirolina y devuelven la bala de cuerda. */
    public static void cortar(ServerPlayer p, int id) {
        ServerLevel l = p.serverLevel();
        Linea x = buscar(l, id);
        if (x == null) return;
        InteractionHand mano = esTijeras(p.getMainHandItem()) ? InteractionHand.MAIN_HAND : esTijeras(p.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
        if (mano == null) return;
        Vec3 ojo = p.getEyePosition();
        if (Cuerda.punto(x.a, x.b, Cuerda.cercano(x.a, x.b, ojo)).distanceTo(ojo) > 7) return;
        de(l).quitar(l, x, null);
        ItemStack bala = new ItemStack(ModItems.BALA_CUERDA.get());
        if (!p.getInventory().add(bala)) p.drop(bala, false);
        p.getItemInHand(mano).hurtAndBreak(1, p, q -> q.broadcastBreakEvent(mano));
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.2F);
        enviar(l);
    }

    public static boolean esTijeras(ItemStack s) {
        return s.getItem() instanceof ShearsItem || s.is(Tags.Items.SHEARS);
    }

    /** Colgarse de una tirolina (clic derecho en ella o saltando debajo). */
    public static void colgar(ServerPlayer p, int id) {
        if (p.isPassenger() || p.isSpectator() || p.isSleeping()) return;
        ServerLevel l = p.serverLevel();
        Linea x = buscar(l, id);
        if (x == null) return;
        Vec3 manos = p.position().add(0, p.getBbHeight() + 0.5, 0);
        double t = Cuerda.cercano(x.a, x.b, manos);
        Vec3 q = Cuerda.punto(x.a, x.b, t);
        if (q.distanceTo(manos) > 3.2 && q.distanceTo(p.getEyePosition()) > 3.6) return;
        // se baja hacia la punta más baja; si está casi plana, hacia donde mira
        double dy = x.b.y - x.a.y;
        int sentido;
        if (Math.abs(dy) > 0.4) sentido = dy < 0 ? 1 : -1;
        else sentido = p.getViewVector(1F).dot(x.b.subtract(x.a)) >= 0 ? 1 : -1;
        t = Math.max(0.01, Math.min(0.99, t));
        GanchoTirolina g = new GanchoTirolina(ModEntities.GANCHO_TIROLINA.get(), l);
        g.preparar(x, (float) t, sentido);
        l.addFreshEntity(g);
        p.startRiding(g, true);
        com.miscelanea.logros.LogrosMiscelanea.dar(p, "rifle_cuerda");
        l.playSound(null, q.x, q.y, q.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8F, 1.3F);
    }

    // ------------------------------------------------------------------ sincronizar

    public static void enviar(ServerLevel l) {
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(l::dimension), new TirolinasPacket(de(l).lineas));
    }

    public static void enviar(ServerPlayer p) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new TirolinasPacket(de(p.serverLevel()).lineas));
    }

    @SubscribeEvent
    public static void entra(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) enviar(p);
    }

    @SubscribeEvent
    public static void cambiaDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) enviar(p);
    }

    @SubscribeEvent
    public static void reaparece(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) enviar(p);
    }

    /** Si se rompe el bloque donde está atada una punta, la cuerda se suelta (y deja la bala en el suelo). */
    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel l) || l.getGameTime() % 20 != 7) return;
        Tirolinas d = de(l);
        if (d.lineas.isEmpty()) return;
        boolean cambio = false;
        for (Iterator<Linea> it = new ArrayList<>(d.lineas).iterator(); it.hasNext(); ) {
            Linea x = it.next();
            BlockPos suelta = null;
            if (l.isLoaded(x.anclaB) && l.getBlockState(x.anclaB).getCollisionShape(l, x.anclaB).isEmpty()) suelta = x.anclaB;
            else if (x.anclaA != null && l.isLoaded(x.anclaA) && l.getBlockState(x.anclaA).getCollisionShape(l, x.anclaA).isEmpty()) suelta = x.anclaA;
            if (suelta != null) {
                d.quitar(l, x, Vec3.atCenterOf(suelta));
                cambio = true;
            }
        }
        if (cambio) enviar(l);
    }
}

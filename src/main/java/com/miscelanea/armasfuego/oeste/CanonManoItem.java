package com.miscelanea.armasfuego.oeste;

import com.miscelanea.armasfuego.comun.ArmaFuegoItem;
import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.logros.LogrosMiscelanea;
import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

/**
 * Cañón de mano del siglo XIV: un tubo de hierro atado a un palo. Se carga con dos de pólvora y un bolaño de piedra
 * (un bloque de roca), clic derecho prende la mecha y un rato después (si sigue en la mano) sale el bolaño, que vuela
 * en parábola y estalla donde da (sin romper bloques, salvo que lo diga la config). Es tecnología punta... del siglo
 * XIV: a veces revienta en las manos, y con lluvia la pólvora se moja.
 */
public class CanonManoItem extends ArmaFuegoItem {
    public static final ResourceKey<DamageType> DANO = Balistica.clave("canon_mano"), REVENTON = Balistica.clave("canon_mano_reventon");
    /** Hora del mundo en que la mecha llega a la pólvora. */
    public static final String MECHA = "Mecha";

    public CanonManoItem(Properties p) {
        super(p);
    }

    @Override
    public int capacidad(ItemStack s) {
        return 1;
    }

    @Override
    public int ticksRecarga(ItemStack s) {
        return EquilibrioArmas.canonRecarga();
    }

    @Override
    protected boolean acepta(ItemStack m) {
        return piedra(m) || m.is(com.miscelanea.registry.ModItems.BOLANO_INCENDIARIO.get()) || m.is(com.miscelanea.registry.ModItems.BOLANO_FUEGOS.get());
    }

    @Override
    protected net.minecraft.world.item.Item municionNormal() {
        return Items.COBBLESTONE;
    }

    /** Cualquier roca es el bolaño normal. */
    @Override
    protected net.minecraft.world.item.Item tipoDe(ItemStack m) {
        return piedra(m) ? Items.COBBLESTONE : m.getItem();
    }

    @Override
    protected int polvora() {
        return 2;
    }

    @Override
    protected boolean femenino() {
        return false;
    }

    /** Ticks de la mecha (Mecha corta: un 30 % menos por nivel). */
    public static int mechaTicks(ItemStack s) {
        return Math.max(4, Math.round(EquilibrioArmas.canonMecha() * (1 - 0.3F * nivel(com.miscelanea.registry.ModEnchantments.CANON_MECHA_CORTA, s))));
    }

    private static boolean piedra(ItemStack s) {
        return s.is(Tags.Items.COBBLESTONE) || s.is(Items.STONE) || s.is(Items.COBBLED_DEEPSLATE);
    }


    @Override
    protected SoundEvent sonidoRecarga() {
        return SoundEvents.STONE_PLACE;
    }

    @Override
    protected int enfriamiento(Player p, ItemStack s) {
        return mechaTicks(s) + 10;
    }

    public static long mecha(ItemStack s) {
        return s.hasTag() && s.getTag().contains(MECHA) ? s.getTag().getLong(MECHA) : -1;
    }

    @Override
    protected InteractionResultHolder<ItemStack> apretarGatillo(Level level, Player p, ItemStack s) {
        if (p.getCooldowns().isOnCooldown(this) || mecha(s) >= 0) return InteractionResultHolder.fail(s);
        if (balas(s) <= 0) {
            intentar(level, p, s);
            return InteractionResultHolder.fail(s);
        }
        s.getOrCreateTag().putLong(MECHA, level.getGameTime() + mechaTicks(s));
        if (!level.isClientSide)
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        p.getCooldowns().addCooldown(this, enfriamiento(p, s));
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        super.inventoryTick(s, level, e, slot, sel);
        long m = mecha(s);
        if (m < 0 || !(e instanceof Player p)) return;
        long ahora = level.getGameTime();
        boolean enMano = sel && p.getMainHandItem() == s;
        if (!level.isClientSide && ahora % 2 == 0) {
            // chispas de la mecha, en el oído del cañón
            Vec3 c = boca(p, 0.3, 0.35, 0.05);
            ((ServerLevel) level).sendParticles(ParticleTypes.SMALL_FLAME, c.x, c.y, c.z, 1, 0.02, 0.02, 0.02, 0.005);
            ((ServerLevel) level).sendParticles(ParticleTypes.SMOKE, c.x, c.y + 0.05, c.z, 1, 0.02, 0.02, 0.02, 0.005);
            if (ahora % 10 == 0) level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.CANON_MECHA.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
        }
        if (ahora < m) return;
        s.getTag().remove(MECHA);
        if (!enMano) {
            // la mecha se ha apagado en el bolsillo
            if (!level.isClientSide) level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.4F);
            return;
        }
        if (!level.isClientSide) {
            ServerPlayer sp = (ServerPlayer) p;
            if (MosqueteItem.mojada(p) && !(EquilibrioArmas.sinAtascosPrueba)) {
                ponerBalas(s, 0);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.0F);
                p.displayClientMessage(Component.translatable("msg.miscelanea.armafuego.mojada").withStyle(ChatFormatting.AQUA), true);
                LogrosMiscelanea.dar(sp, "polvora_mojada");
                return;
            }
            if (nivel(com.miscelanea.registry.ModEnchantments.CANON_BIEN_FUNDIDO, s) == 0 && p.getRandom().nextFloat() < EquilibrioArmas.canonRevienta()) {
                reventar(sp, s);
                return;
            }
        }
        intentar(level, p, s);
    }

    /** Revienta en las manos: era de esperar. */
    private void reventar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        ponerBalas(s, 0);
        Vec3 b = boca(p, 0.8, 0.3, 0.2);
        l.sendParticles(ParticleTypes.EXPLOSION, b.x, b.y, b.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, b.x, b.y, b.z, 10, 0.2, 0.2, 0.2, 0.03);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.CANON_DISPARO.get(), SoundSource.PLAYERS, 3.0F, 0.7F);
        l.gameEvent(p, GameEvent.EXPLODE, b);
        p.hurt(Balistica.fuente(p, REVENTON, null, p), 6F);
        Vec3 atras = p.getViewVector(1F).scale(-0.9).add(0, 0.3, 0);
        p.setDeltaMovement(p.getDeltaMovement().add(atras));
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        s.hurtAndBreak(60, p, x -> x.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        LogrosMiscelanea.dar(p, "canon_revienta");
    }

    @Override
    protected void disparar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        Vec3 b = boca(p, 1.2, 0.3, 0.2);
        BolaCanonEntity bola = new BolaCanonEntity(ModEntities.BOLA_CANON.get(), p, l);
        var tipo = tipoActual(s);
        bola.setItem(new ItemStack(tipo));
        bola.setPos(b.x, b.y, b.z);
        bola.shootFromRotation(p, p.getXRot(), p.getYRot(), 0F, 2.2F, 4.0F);
        l.addFreshEntity(bola);
        efectos(l, p, b, ModSounds.CANON_DISPARO.get(), 6.0F, 0.9F, 0, false);
        Vec3 m = p.getViewVector(1F);
        for (int i = 0; i < 7; i++) {
            Vec3 q = b.add(m.scale(0.3 + i * 0.3));
            l.sendParticles(ParticleTypes.LARGE_SMOKE, q.x, q.y, q.z, 1, 0.15 + i * 0.03, 0.12, 0.15 + i * 0.03, 0.004);
        }
        l.sendParticles(ParticleTypes.FLAME, b.x, b.y, b.z, 5, 0.08, 0.08, 0.08, 0.04);
        p.setDeltaMovement(p.getDeltaMovement().add(m.scale(-0.35)));
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        LogrosMiscelanea.dar(p, "canon_disparo");
    }
}

package com.miscelanea.bromas;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que hacen las bromas en el servidor:
 * <ul>
 *   <li>uwu: el mensaje de chat de quien tiene el hechizo se reescribe antes de enviarse a todos;</li>
 *   <li>cartel de "PATÉAME": se guarda en los datos persistentes del jugador (cuándo caduca) y se manda a quien lo ve;
 *       cada golpe que recibe suelta confeti y un "boing" (sin daño extra); se cae al morir, al caducar, o la víctima se
 *       lo quita (agachada + clic derecho con la mano vacía, que manda el cliente);</li>
 *   <li>duende: cada cierto tiempo revuelve las 27 casillas de la mochila (no la barra rápida, ni la armadura, ni la
 *       mano izquierda) y a veces cambia lo que lleva en la mano por otra casilla de la barra. Nunca borra nada.</li>
 * </ul>
 * Los controles cambiados y la pantalla del revés son solo del cliente (miran el efecto).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Bromas {
    private Bromas() {}

    public static final String CLAVE_CARTEL = "miscelanea_cartel";
    /** Duende: cuándo (tiempo de juego) le toca la próxima travesura a cada uno. */
    private static final Map<UUID, Long> PROXIMO = new HashMap<>();
    /** Cartel: último "boing" (para no repetirlo en el mismo golpe). */
    private static final Map<UUID, Long> ULTIMO_BOING = new HashMap<>();

    public static boolean activa(BromaItem.Broma b) {
        return switch (b) {
            case UWU -> ConfigServidor.bromaUwu();
            case CARTEL -> ConfigServidor.bromaCartel();
            case DUENDE -> ConfigServidor.bromaDuende();
            case REVES -> ConfigServidor.bromaReves();
        };
    }

    // ------------------------------------------------------------------ aplicar
    /** Gasta la broma: false si no se ha podido (p. ej. ya lleva un cartel). */
    public static boolean aplicar(BromaItem.Broma b, ServerPlayer v, ServerPlayer autor) {
        ServerLevel sl = v.serverLevel();
        double x = v.getX(), y = v.getY() + v.getBbHeight() * 0.6, z = v.getZ();
        switch (b) {
            case UWU -> {
                v.addEffect(new MobEffectInstance(BromasRegistro.UWU.get(), ConfigServidor.bromaUwuTicks(), 0, false, false, true), autor);
                sl.playSound(null, x, y, z, BromasRegistro.MAGIA_UWU.get(), SoundSource.PLAYERS, 1F, 1F);
                sl.sendParticles(ParticleTypes.HEART, x, y + 0.4, z, 6, 0.4, 0.4, 0.4, 0.1);
                sl.sendParticles(BromasRegistro.CONFETI.get(), x, y, z, 24, 0.3, 0.4, 0.3, 0.12);
                v.displayClientMessage(Component.translatable("msg.miscelanea.broma.uwu.victima").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
                autor.displayClientMessage(Component.translatable("msg.miscelanea.broma.uwu.autor", v.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            case CARTEL -> {
                if (tieneCartel(v)) {
                    autor.displayClientMessage(Component.translatable("msg.miscelanea.broma.cartel.ya", v.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
                    return false;
                }
                v.getPersistentData().putLong(CLAVE_CARTEL, sl.getGameTime() + ConfigServidor.bromaCartelTicks());
                BromasRed.alrededor(v, new BromasRed.Cartel(v.getId(), true));
                sl.playSound(null, x, y, z, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.7F, 1.4F);
                sl.playSound(null, x, y, z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1F, 1.2F);
                sl.sendParticles(ParticleTypes.CLOUD, x, y, z, 3, 0.2, 0.2, 0.2, 0.01);
                v.displayClientMessage(Component.translatable("msg.miscelanea.broma.cartel.victima").withStyle(ChatFormatting.YELLOW), true);
                autor.displayClientMessage(Component.translatable("msg.miscelanea.broma.cartel.autor", v.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
            }
            case DUENDE -> {
                v.addEffect(new MobEffectInstance(BromasRegistro.DUENDE.get(), ConfigServidor.bromaDuendeTicks(), 0, false, false, true), autor);
                PROXIMO.put(v.getUUID(), sl.getGameTime() + Math.min(200, ConfigServidor.bromaDuendeCadaTicks()));
                risita(sl, v);
                v.displayClientMessage(Component.translatable("msg.miscelanea.broma.duende.victima").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                autor.displayClientMessage(Component.translatable("msg.miscelanea.broma.duende.autor", v.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
            }
            case REVES -> {
                v.addEffect(new MobEffectInstance(BromasRegistro.REVES.get(), ConfigServidor.bromaRevesTicks(), 0, false, false, true), autor);
                sl.playSound(null, x, y, z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1F, 1.1F);
                sl.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 0.7F);
                sl.sendParticles(ParticleTypes.END_ROD, x, y, z, 14, 0.35, 0.5, 0.35, 0.03);
                v.displayClientMessage(Component.translatable("msg.miscelanea.broma.reves.victima").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
                autor.displayClientMessage(Component.translatable("msg.miscelanea.broma.reves.autor", v.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
            }
        }
        return true;
    }

    private static void risita(ServerLevel sl, Player v) {
        sl.playSound(null, v.getX(), v.getY() + 1, v.getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 0.8F, 1.75F + sl.random.nextFloat() * 0.2F);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1, v.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
    }

    // ------------------------------------------------------------------ uwu
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void chat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        if (p == null || !p.hasEffect(BromasRegistro.UWU.get()) || !ConfigServidor.bromaUwu()) return;
        e.setMessage(Component.literal(Uwu.traducir(e.getRawText(), p.getRandom())));
    }

    // ------------------------------------------------------------------ cartel
    public static boolean tieneCartel(Player p) {
        return p.getPersistentData().contains(CLAVE_CARTEL);
    }

    private static void quitarCartel(ServerPlayer p) {
        p.getPersistentData().remove(CLAVE_CARTEL);
        ULTIMO_BOING.remove(p.getUUID());
        BromasRed.alrededor(p, new BromasRed.Cartel(p.getId(), false));
    }

    /** La víctima se lo quita ella sola (agachada + clic derecho con la mano vacía). */
    static void quitarCartelSolo(ServerPlayer p) {
        if (!tieneCartel(p) || !p.isShiftKeyDown() || !p.getMainHandItem().isEmpty()) return;
        quitarCartel(p);
        p.serverLevel().playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1F, 0.8F);
        p.displayClientMessage(Component.translatable("msg.miscelanea.broma.cartel.quitado").withStyle(ChatFormatting.GRAY), true);
    }

    @SubscribeEvent
    public static void golpe(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer v) || !tieneCartel(v)) return;
        Entity a = e.getSource().getEntity();
        if (a == null || a == v) return;
        ServerLevel sl = v.serverLevel();
        long ahora = sl.getGameTime();
        Long antes = ULTIMO_BOING.get(v.getUUID());
        if (antes != null && ahora - antes < 6) return;
        ULTIMO_BOING.put(v.getUUID(), ahora);
        double y = v.getY() + v.getBbHeight() * 0.7;
        sl.playSound(null, v.getX(), y, v.getZ(), BromasRegistro.BOING.get(), SoundSource.PLAYERS, 1F, 0.9F + sl.random.nextFloat() * 0.3F);
        sl.playSound(null, v.getX(), y, v.getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 0.35F, 1.6F);
        sl.sendParticles(BromasRegistro.CONFETI.get(), v.getX(), y, v.getZ(), 45, 0.25, 0.3, 0.25, 0.3);
        v.displayClientMessage(Component.translatable("msg.miscelanea.broma.cartel.golpe").withStyle(ChatFormatting.YELLOW), true);
    }

    @SubscribeEvent
    public static void muerte(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && tieneCartel(p)) quitarCartel(p);
    }

    @SubscribeEvent
    public static void verAlguien(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof Player t && tieneCartel(t) && e.getEntity() instanceof ServerPlayer yo)
            BromasRed.a(yo, new BromasRed.Cartel(t.getId(), true));
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && tieneCartel(p)) BromasRed.alrededor(p, new BromasRed.Cartel(p.getId(), true));
    }

    @SubscribeEvent
    public static void cambiarDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && tieneCartel(p)) BromasRed.alrededor(p, new BromasRed.Cartel(p.getId(), true));
    }

    @SubscribeEvent
    public static void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && tieneCartel(p)) quitarCartel(p);
    }

    @SubscribeEvent
    public static void salir(PlayerEvent.PlayerLoggedOutEvent e) {
        PROXIMO.remove(e.getEntity().getUUID());
        ULTIMO_BOING.remove(e.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ tick (cartel que caduca, duende)
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        long ahora = p.serverLevel().getGameTime();
        if (p.tickCount % 20 == 0 && tieneCartel(p) && ahora >= p.getPersistentData().getLong(CLAVE_CARTEL)) {
            quitarCartel(p);
            p.displayClientMessage(Component.translatable("msg.miscelanea.broma.cartel.caido").withStyle(ChatFormatting.GRAY), true);
        }
        if (!p.hasEffect(BromasRegistro.DUENDE.get())) {
            if (!PROXIMO.isEmpty()) PROXIMO.remove(p.getUUID());
            return;
        }
        Long toca = PROXIMO.get(p.getUUID());
        if (toca == null) {
            PROXIMO.put(p.getUUID(), ahora + ConfigServidor.bromaDuendeCadaTicks());
            return;
        }
        if (ahora < toca) return;
        PROXIMO.put(p.getUUID(), ahora + ConfigServidor.bromaDuendeCadaTicks());
        if (!p.isSpectator() && ConfigServidor.bromaDuende()) revolver(p);
    }

    /** Revuelve la mochila (casillas 9..35) y a veces cambia el objeto de la mano por otro de la barra. */
    private static void revolver(ServerPlayer p) {
        Inventory inv = p.getInventory();
        List<ItemStack> l = new ArrayList<>(27);
        for (int i = 9; i < 36; i++) l.add(inv.items.get(i));
        for (int i = l.size() - 1; i > 0; i--) {
            int j = p.getRandom().nextInt(i + 1);
            ItemStack t = l.get(i);
            l.set(i, l.get(j));
            l.set(j, t);
        }
        for (int i = 9; i < 36; i++) inv.items.set(i, l.get(i - 9));
        if (p.getRandom().nextFloat() < 0.5F) {
            int s = inv.selected;
            int o = (s + 1 + p.getRandom().nextInt(8)) % 9;
            ItemStack t = inv.items.get(s);
            inv.items.set(s, inv.items.get(o));
            inv.items.set(o, t);
        }
        inv.setChanged();
        p.containerMenu.broadcastChanges();
        p.inventoryMenu.broadcastChanges();
        risita(p.serverLevel(), p);
        p.displayClientMessage(Component.translatable("msg.miscelanea.broma.duende.revuelto").withStyle(ChatFormatting.GREEN), true);
    }
}

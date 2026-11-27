package com.miscelanea.rave;

import com.miscelanea.Miscelanea;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Pulseras y collares de neón: accesorios que se llevan puestos sin ocupar armadura. Clic derecho con uno en la mano: te lo
 * pones (si llevabas otro, vuelve al inventario); agachado + clic: te quitas el que llevas. Lo que lleva cada uno se guarda
 * con el jugador (también al morir) y se manda a quien lo ve, que lo dibuja brillando en la muñeca o en el cuello.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Accesorios {
    private Accesorios() {}

    public static final int PULSERA = 0, COLLAR = 1;
    private static final String[] CLAVE = {"RavePulsera", "RaveCollar"};

    private static CompoundTag datos(Player p) {
        CompoundTag t = p.getPersistentData();
        if (!t.contains(Player.PERSISTED_NBT_TAG)) t.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return t.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Color (id de tinte) del accesorio que lleva, o -1. */
    public static int puesto(Player p, int tipo) {
        CompoundTag t = datos(p);
        return t.contains(CLAVE[tipo]) ? t.getInt(CLAVE[tipo]) : -1;
    }

    public static void poner(ServerPlayer p, int tipo, int color) {
        if (color < 0) datos(p).remove(CLAVE[tipo]);
        else datos(p).putInt(CLAVE[tipo], color);
        RaveRed.CANAL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), paquete(p));
    }

    private static RaveRed.Puesto paquete(Player p) {
        return new RaveRed.Puesto(p.getId(), puesto(p, PULSERA), puesto(p, COLLAR));
    }

    /**
     * Quitarse lo que lleva sin tener otro en la mano (agachado + clic derecho al aire con la mano vacía): primero la pulsera y,
     * si no lleva, el collar. Vuelve al inventario. Antes solo se podía con otro accesorio en la mano, y como al ponérselo se
     * gasta, quien se quedaba sin ninguno ya no podía quitárselo.
     *
     * @return si se ha quitado algo
     */
    public static boolean quitarAMano(ServerPlayer p) {
        if (!p.getMainHandItem().isEmpty()) return false;
        int tipo = puesto(p, PULSERA) >= 0 ? PULSERA : puesto(p, COLLAR) >= 0 ? COLLAR : -1;
        if (tipo < 0) return false;
        int color = puesto(p, tipo);
        poner(p, tipo, -1);
        guardarFueraDeLaMano(p, objeto(tipo, DyeColor.byId(color)));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.8F, 1.5F);
        return true;
    }

    /**
     * Mete en el inventario lo que se acaba de quitar sin dejarlo en la mano: si cayera en la mano (que está vacía, es el primer
     * hueco libre muchas veces), el siguiente clic ya no sería con la mano vacía y no se podría quitar el collar. Primero se junta
     * con uno igual que ya tenga; si no, al primer hueco que no sea la mano; y si solo queda la mano, ahí, y si no cabe, al suelo.
     */
    private static void guardarFueraDeLaMano(ServerPlayer p, ItemStack s) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack h = inv.items.get(i);
            if (!h.isEmpty() && ItemStack.isSameItemSameTags(h, s) && h.getCount() < h.getMaxStackSize()) {
                h.grow(s.getCount());
                return;
            }
        }
        for (int i = 0; i < inv.items.size(); i++) {
            if (i != inv.selected && inv.items.get(i).isEmpty()) {
                inv.items.set(i, s);
                return;
            }
        }
        if (!inv.add(s)) p.drop(s, false);
    }

    public static ItemStack objeto(int tipo, DyeColor c) {
        ItemStack s = new ItemStack(tipo == PULSERA ? RaveRegistro.PULSERA.get() : RaveRegistro.COLLAR.get());
        s.getOrCreateTag().putString("color", c.getName());
        return s;
    }

    @SubscribeEvent
    public static void empiezaAVer(PlayerEvent.StartTracking e) {
        // siempre, también si no lleva nada: si no, quien lo vio con la pulsera y volvió después de que se la quitara la seguía viendo
        if (e.getTarget() instanceof Player otro && e.getEntity() instanceof ServerPlayer yo)
            RaveRed.CANAL.send(PacketDistributor.PLAYER.with(() -> yo), paquete(otro));
    }

    private static void aMi(Player p) {
        if (p instanceof ServerPlayer sp) RaveRed.CANAL.send(PacketDistributor.PLAYER.with(() -> sp), paquete(sp));
    }

    @SubscribeEvent
    public static void entra(PlayerEvent.PlayerLoggedInEvent e) {
        aMi(e.getEntity());
    }

    @SubscribeEvent
    public static void reaparece(PlayerEvent.PlayerRespawnEvent e) {
        aMi(e.getEntity());
    }

    @SubscribeEvent
    public static void cambiaDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        aMi(e.getEntity());
    }

    public static class Objeto extends Item {
        private final int tipo;

        public Objeto(int tipo) {
            super(new Item.Properties().stacksTo(16));
            this.tipo = tipo;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level l, Player j, InteractionHand m) {
            ItemStack s = j.getItemInHand(m);
            if (!(j instanceof ServerPlayer sp)) return InteractionResultHolder.success(s);
            int antes = puesto(sp, tipo);
            if (j.isShiftKeyDown()) {
                // quitarse el que lleva
                if (antes < 0) return InteractionResultHolder.pass(s);
                poner(sp, tipo, -1);
                dar(sp, objeto(tipo, DyeColor.byId(antes)));
            } else {
                int color = Neon.de(s).getId();
                if (color == antes) return InteractionResultHolder.pass(s);
                poner(sp, tipo, color);
                if (!j.getAbilities().instabuild) s.shrink(1);
                if (antes >= 0) dar(sp, objeto(tipo, DyeColor.byId(antes)));
            }
            l.playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.8F, 1.5F);
            return InteractionResultHolder.consume(s);
        }

        private static void dar(ServerPlayer p, ItemStack s) {
            if (!p.getInventory().add(s)) p.drop(s, false);
        }

        @Override
        public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> t, TooltipFlag f) {
            t.add(Component.translatable("color.minecraft." + Neon.de(s).getName()).withStyle(ChatFormatting.GRAY));
            t.add(Component.translatable("rave.miscelanea.accesorio.ayuda").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

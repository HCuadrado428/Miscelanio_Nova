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

    public static ItemStack objeto(int tipo, DyeColor c) {
        ItemStack s = new ItemStack(tipo == PULSERA ? RaveRegistro.PULSERA.get() : RaveRegistro.COLLAR.get());
        s.getOrCreateTag().putString("color", c.getName());
        return s;
    }

    @SubscribeEvent
    public static void empiezaAVer(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof Player otro && e.getEntity() instanceof ServerPlayer yo && (puesto(otro, PULSERA) >= 0 || puesto(otro, COLLAR) >= 0))
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

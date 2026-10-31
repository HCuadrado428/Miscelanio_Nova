package com.miscelanea.client.pipboy;

import com.miscelanea.Miscelanea;
import com.miscelanea.pipboy.PipBoyAccionPacket;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModSounds;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pip-Boy en el cliente: quién lo lleva/mira, tecla para abrirlo, tus notas y marcadores, y la radio. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class PipBoyCliente {
    public static final KeyMapping ABRIR = new KeyMapping("key.miscelanea.pipboy", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B, "key.categories." + Miscelanea.MOD_ID);

    private static final Map<Integer, Byte> FLAGS = new HashMap<>();
    public static final List<String> NOTAS = new ArrayList<>();
    public record Marca(int x, int z, String nombre, String dim) {}
    public static final List<Marca> MARCAS = new ArrayList<>();
    private static int abriendo = -1;
    private static boolean mirandoYo;
    // radio
    public static int emisora = -1;
    private static RadioSonido sonando;

    private PipBoyCliente() {}

    public static void estado(int id, byte f) {
        FLAGS.put(id, f);
    }

    public static boolean lleva(AbstractClientPlayer p) {
        return (FLAGS.getOrDefault(p.getId(), (byte) 0) & 1) != 0;
    }

    public static boolean mirando(AbstractClientPlayer p) {
        if (p == Minecraft.getInstance().player) return mirandoYo;
        return (FLAGS.getOrDefault(p.getId(), (byte) 0) & 2) != 0;
    }

    public static void datos(CompoundTag d) {
        NOTAS.clear();
        ListTag n = d.getList(com.miscelanea.pipboy.PipBoy.NOTAS, Tag.TAG_STRING);
        for (int i = 0; i < n.size(); i++) NOTAS.add(n.getString(i));
        MARCAS.clear();
        ListTag m = d.getList(com.miscelanea.pipboy.PipBoy.MARCAS, Tag.TAG_COMPOUND);
        for (int i = 0; i < m.size(); i++) {
            CompoundTag c = m.getCompound(i);
            MARCAS.add(new Marca(c.getInt("x"), c.getInt("z"), c.getString("n"), c.getString("d")));
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null) {
            FLAGS.clear();
            emisora = -1;
            sonando = null;
            return;
        }
        boolean llevo = lleva(p);
        while (ABRIR.consumeClick()) {
            if (!llevo) {
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.miscelanea.pipboy_no"), true);
            } else if (mc.screen == null && abriendo < 0) {
                abriendo = 7;
                mirandoYo = true;
                ModNetwork.CHANNEL.sendToServer(PipBoyAccionPacket.simple(PipBoyAccionPacket.MIRAR));
            }
        }
        if (abriendo > 0 && --abriendo == 0) {
            abriendo = -1;
            mc.setScreen(new PipBoyScreen());
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.PIPBOY_ABRIR.get(), 1.0F, 0.7F));
        }
        // radio: sigue sonando con la pantalla cerrada; si la para el motor de sonido, vuelve a empezar
        if (!llevo && emisora >= 0) radio(-1);
        if (emisora >= 0 && (sonando == null || !mc.getSoundManager().isActive(sonando))) {
            sonando = new RadioSonido(ModSounds.RADIO[emisora].get());
            mc.getSoundManager().play(sonando);
        }
    }

    /** Al cerrar la pantalla: deja de mirar el Pip-Boy. */
    static void cerrado() {
        mirandoYo = false;
        ModNetwork.CHANNEL.sendToServer(PipBoyAccionPacket.simple(PipBoyAccionPacket.DEJAR_MIRAR));
    }

    public static void radio(int i) {
        var mc = Minecraft.getInstance();
        if (sonando != null) mc.getSoundManager().stop(sonando);
        sonando = null;
        emisora = i;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.RADIO_ESTATICA.get(), 1.0F, 0.6F));
    }
}

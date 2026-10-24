package com.miscelanea.client;

import com.miscelanea.Miscelanea;
import com.miscelanea.aspira.AspirafantasmasItem;
import com.miscelanea.aspira.Calor;
import com.miscelanea.aspira.Modo;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente de la aspiradora: zumbido continuo (sube de tono con el calor y cambia con el modo)
 * y temblor/tirón de cámara en primera persona mientras se usa.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class AspiraCliente {
    private static final Map<Integer, Zumbido> ZUMBIDOS = new HashMap<>();
    private static float intensidad; // 0–1, suavizado, para la cámara

    private AspiraCliente() {}

    /** Modo en uso o null si no está aspirando/soplando. */
    static Modo modoEnUso(Player p) {
        if (!p.isUsingItem() || !(p.getUseItem().getItem() instanceof AspirafantasmasItem)) return null;
        ItemStack s = p.getUseItem();
        if (!AspirafantasmasItem.activa(s)) return null;
        Modo m = AspirafantasmasItem.modoActivo(s);
        return switch (m) {
            case ASPIRAR, ALQUIMISTA, SOPLAR, SOPLIDO, LANZALLAMAS -> m;
            default -> null;
        };
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ZUMBIDOS.clear();
            return;
        }
        for (Player p : mc.level.players()) {
            if (modoEnUso(p) == null) continue;
            Zumbido z = ZUMBIDOS.get(p.getId());
            if (z == null || z.isStopped()) {
                z = new Zumbido(p);
                ZUMBIDOS.put(p.getId(), z);
                mc.getSoundManager().play(z);
            }
        }
        ZUMBIDOS.values().removeIf(Zumbido::isStopped);
        float objetivo = mc.player != null && modoEnUso(mc.player) != null ? 1.0F : 0.0F;
        intensidad += (objetivo - intensidad) * 0.2F;
    }

    @SubscribeEvent
    public static void onCamara(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (intensidad < 0.01F || mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        Modo m = modoEnUso(mc.player);
        float fuerza = m == Modo.LANZALLAMAS ? 0.5F : m == Modo.SOPLAR || m == Modo.SOPLIDO ? 0.35F : 0.25F;
        float calor = mc.player.getUseItem().getItem() instanceof AspirafantasmasItem ? Calor.fraccion(mc.player.getUseItem()) : 0;
        fuerza *= intensidad * (1.0F + calor);
        float t = (float) (mc.player.tickCount + e.getPartialTick());
        e.setPitch(e.getPitch() + (Mth.sin(t * 3.1F) + Mth.sin(t * 5.3F) * 0.5F) * fuerza);
        e.setYaw(e.getYaw() + Mth.sin(t * 4.3F) * fuerza * 0.6F);
    }

    /** Aspirar mete un poco la vista hacia dentro; soplar y el lanzallamas la empujan hacia fuera. */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        Minecraft mc = Minecraft.getInstance();
        if (intensidad < 0.01F || mc.player == null) return;
        Modo m = modoEnUso(mc.player);
        double k = m == Modo.ASPIRAR || m == Modo.ALQUIMISTA ? -2.5 : 2.0;
        e.setFOV(e.getFOV() + k * intensidad);
    }

    private static final class Zumbido extends AbstractTickableSoundInstance {
        private final Player p;

        Zumbido(Player p) {
            super(ModSounds.ASPIRAR.get(), SoundSource.PLAYERS, RandomSource.create());
            this.p = p;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.05F;
            this.x = p.getX();
            this.y = p.getY() + 1.2;
            this.z = p.getZ();
        }

        @Override
        public void tick() {
            Modo m = p.isRemoved() ? null : modoEnUso(p);
            if (m == null) {
                // se apaga en unos ticks en vez de cortarse en seco
                volume -= 0.12F;
                if (volume <= 0.0F) stop();
                return;
            }
            x = p.getX();
            y = p.getY() + 1.2;
            z = p.getZ();
            float calor = Calor.fraccion(p.getUseItem());
            float base = switch (m) {
                case SOPLAR, SOPLIDO -> 1.25F;
                case LANZALLAMAS -> 0.8F;
                default -> 1.0F;
            };
            pitch = base + calor * 0.45F;
            volume = Math.min(0.65F, volume + 0.1F);
        }
    }
}

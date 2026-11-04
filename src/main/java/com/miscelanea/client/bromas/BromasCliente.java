package com.miscelanea.client.bromas;

import com.miscelanea.Miscelanea;
import com.miscelanea.bromas.BromasRed;
import com.miscelanea.bromas.BromasRegistro;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Cliente de las bromas:
 * <ul>
 *   <li>controles cambiados: W↔S y A↔D (se cambian las teclas y los impulsos ya calculados);</li>
 *   <li>pantalla del revés: la cámara gira 180º (roll) con medio segundo de transición al empezar y al acabar; el ratón
 *       sigue normal (esa es la gracia);</li>
 *   <li>cartel: quién lo lleva (para dibujarlo, {@link CartelLayer}); agachado + clic derecho al aire con la mano vacía,
 *       la víctima se lo quita.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class BromasCliente {
    private BromasCliente() {}

    private static final Set<Integer> CARTELES = new HashSet<>();
    /** Pantalla del revés: 0 = normal, 1 = boca abajo (sube y baja en 10 ticks). */
    private static float vuelta, vueltaO;
    private static int esperaQuitar;

    // ------------------------------------------------------------------ cartel
    public static void cartel(int id, boolean puesto) {
        if (puesto) CARTELES.add(id);
        else CARTELES.remove(id);
    }

    public static boolean tieneCartel(int id) {
        return CARTELES.contains(id);
    }

    @SubscribeEvent
    public static void clicVacio(PlayerInteractEvent.RightClickEmpty e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || e.getEntity() != p || e.getHand() != InteractionHand.MAIN_HAND || !p.isShiftKeyDown()) return;
        if (!tieneCartel(p.getId()) || esperaQuitar > 0) return;
        esperaQuitar = 10;
        BromasRed.CANAL.sendToServer(new BromasRed.QuitarCartel());
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        CARTELES.clear();
        vuelta = vueltaO = 0F;
        RuedaCliente.limpiar();
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (esperaQuitar > 0) esperaQuitar--;
        vueltaO = vuelta;
        if (mc.player == null || mc.level == null) {
            vuelta = vueltaO = 0F;
            return;
        }
        if (mc.isPaused()) return;
        MobEffectInstance r = mc.player.getEffect(BromasRegistro.REVES.get());
        boolean boca = r != null && (r.isInfiniteDuration() || r.getDuration() > 10);
        vuelta = Mth.clamp(vuelta + (boca ? 0.1F : -0.1F), 0F, 1F);
    }

    // ------------------------------------------------------------------ controles cambiados
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void movimiento(MovementInputUpdateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getEntity() != mc.player || !mc.player.hasEffect(BromasRegistro.CONTROLES.get())) return;
        Input in = e.getInput();
        boolean b = in.up;
        in.up = in.down;
        in.down = b;
        b = in.left;
        in.left = in.right;
        in.right = b;
        in.forwardImpulse = -in.forwardImpulse;
        in.leftImpulse = -in.leftImpulse;
    }

    // ------------------------------------------------------------------ pantalla del revés
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        float k = Mth.lerp((float) e.getPartialTick(), vueltaO, vuelta);
        if (k <= 0F) return;
        float s = k * k * (3F - 2F * k);   // suave al empezar y al acabar
        e.setRoll(e.getRoll() + 180F * s);
    }
}

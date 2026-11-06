package com.miscelanea.client.pociones;

import com.miscelanea.Miscelanea;
import com.miscelanea.pociones.PocionesRegistro;
import com.miscelanea.pociones.Tamanos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Gigante / enano: el modelo se dibuja a escala (la caja y los ojos van por EntityEvent.Size en los dos lados). La
 * escala DIBUJADA no salta: crece o encoge en unos 12 ticks con un pequeño rebote, y al cambiar sale un anillo de humo
 * a los pies con chispas del color de la poción. La sombra redonda de vanilla se escala igual (ver
 * {@code EntityRenderDispatcherMixin}); antes un gigante x4 llevaba la sombra de un jugador normal.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class TamanoCliente {
    private TamanoCliente() {}

    private static final Set<Integer> ESCALADOS = new HashSet<>();

    /** Escala que se ve de cada entidad dibujada hace poco (y la del tick anterior, para interpolar). */
    private static final class Visto {
        float desde, ahora, antes, objetivo;
        int t = 99, ultimoDibujo;
    }

    private static final Map<Integer, Visto> VISTOS = new HashMap<>();
    private static final int DURA = 12;
    private static int reloj;

    /** Escala a la que se dibuja ahora (la del efecto, suavizada en los cambios). */
    public static float escalaVista(Entity e, float pt) {
        if (!(e instanceof LivingEntity v)) return 1F;
        Visto s = VISTOS.get(e.getId());
        if (s == null) return Tamanos.escala(v);
        return Mth.lerp(pt, s.antes, s.ahora);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity v = e.getEntity();
        float objetivo = Tamanos.escala(v);
        Visto s = VISTOS.get(v.getId());
        if (s == null) {
            // primera vez que se ve: ya a su tamaño, sin animación
            s = new Visto();
            s.desde = s.ahora = s.antes = s.objetivo = objetivo;
            VISTOS.put(v.getId(), s);
        }
        s.ultimoDibujo = reloj;
        float esc = Mth.lerp(e.getPartialTick(), s.antes, s.ahora);
        if (esc == 1F) return;
        e.getPoseStack().pushPose();
        e.getPoseStack().scale(esc, esc, esc);
        ESCALADOS.add(v.getId());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void despues(RenderLivingEvent.Post<?, ?> e) {
        if (ESCALADOS.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel l = mc.level;
        if (l == null) {
            VISTOS.clear();
            return;
        }
        if (mc.isPaused()) return;
        reloj++;
        for (Iterator<Map.Entry<Integer, Visto>> it = VISTOS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Visto> en = it.next();
            Visto s = en.getValue();
            Entity ent = l.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity v) || reloj - s.ultimoDibujo > 100) {
                it.remove();
                continue;
            }
            s.antes = s.ahora;
            float objetivo = Tamanos.escala(v);
            if (objetivo != s.objetivo) {
                s.desde = s.ahora;
                s.objetivo = objetivo;
                s.t = 0;
                anillo(l, v, objetivo > s.desde);
            }
            if (s.t < DURA) {
                s.t++;
                // de "desde" a "objetivo" en escala logarítmica (crecer x4 y encoger a 1/4 se sienten igual), con rebote
                float x = s.t / (float) DURA;
                float c = 1.70158F * 1.2F;
                float f = 1F + (c + 1F) * (float) Math.pow(x - 1F, 3) + c * (float) Math.pow(x - 1F, 2);
                double ld = Math.log(s.desde), lo = Math.log(s.objetivo);
                s.ahora = (float) Math.exp(ld + (lo - ld) * f);
            } else s.ahora = s.objetivo;
        }
    }

    /** Anillo de humo a los pies y chispas del color de la poción (naranja al crecer, verde al encoger). */
    private static void anillo(ClientLevel l, LivingEntity v, boolean crece) {
        RandomSource r = l.getRandom();
        float radio = Math.max(0.6F, v.getBbWidth() * 0.9F);
        int col = crece ? PocionesRegistro.COLOR_GIGANTE : PocionesRegistro.COLOR_ENANO;
        DustParticleOptions chispa = new DustParticleOptions(new Vector3f(((col >> 16) & 255) / 255F, ((col >> 8) & 255) / 255F, (col & 255) / 255F), 1.2F);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24 + r.nextDouble() * 0.2;
            double vx = Math.cos(a) * 0.12, vz = Math.sin(a) * 0.12;
            l.addParticle(ParticleTypes.POOF, v.getX() + Math.cos(a) * radio * 0.5, v.getY() + 0.1, v.getZ() + Math.sin(a) * radio * 0.5,
                    crece ? vx : vx * 0.5, 0.01, crece ? vz : vz * 0.5);
        }
        double alto = v.getBbHeight();
        for (int i = 0; i < 18; i++)
            l.addParticle(chispa, v.getX() + r.nextGaussian() * radio * 0.4, v.getY() + r.nextDouble() * alto, v.getZ() + r.nextGaussian() * radio * 0.4, 0, 0, 0);
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        VISTOS.clear();
        ESCALADOS.clear();
    }
}

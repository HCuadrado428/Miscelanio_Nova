package com.miscelanea.compat;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * Compatibilidad con Pehkui (el mod que cambia el tamaño de las entidades; lo usa Customizable Player Models para los
 * modelos de otra talla). Los aparatos de la sala de juegos, las cápsulas del laboratorio, el cepo, la jaula y las
 * atracciones de la feria están hechos para un jugador de tamaño normal: mientras alguien está metido en uno, se le pone
 * la talla que cabe (1 por defecto) y al salir se le devuelve la suya.
 * <p>Se toca la altura y la anchura de Pehkui ({@code pehkui:height} y {@code pehkui:width}), que multiplican tanto el modelo
 * como la caja de colisión. CPM no escribe en esos dos: engancha sus propias escalas como multiplicadores, así que aquí se
 * mide la talla que de verdad tiene (con todo multiplicado) y se corrige la diferencia; al salir se vuelve a poner lo que
 * había. Lo que había se guarda en los datos de la entidad, para devolverlo aunque se reinicie el servidor.
 * <p>Todo por reflexión: Pehkui no es una dependencia y sin él esta clase no hace nada. Comprobado contra Pehkui 3.8.2 para
 * Forge 1.20.1.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class TallaPehkui {
    private TallaPehkui() {}

    /** Donde se guarda la altura y la anchura que tenía antes de entrar. */
    private static final String CLAVE = "MiscelaneaTallaAntes";
    private static ForgeConfigSpec.BooleanValue ACTIVADO, ATRACCIONES;
    private static ForgeConfigSpec.DoubleValue TALLA;

    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("Pehkui (cambia el tamaño de los jugadores; lo usa Customizable Player Models). Solo hace algo si Pehkui está instalado.").push("pehkui");
        ACTIVADO = b.comment("Mientras alguien está en un aparato de la sala de juegos, una cápsula del laboratorio, el cepo o la jaula, se le pone",
                "la talla que cabe en ellos y al salir se le devuelve la suya.").define("ajustar_talla", true);
        ATRACCIONES = b.comment("Lo mismo en las atracciones de la feria (noria, tiovivo, coches de choque...).").define("ajustar_en_feria", true);
        TALLA = b.comment("La talla que se pone (1 = un jugador normal).").defineInRange("talla", 1.0, 0.25, 2.0);
        b.pop();
    }

    private static <T> T leer(ForgeConfigSpec.ConfigValue<T> v) {
        try {
            return ConfigServidor.SPEC.isLoaded() ? v.get() : v.getDefault();
        } catch (Exception e) {
            return v.getDefault();
        }
    }

    // ------------------------------------------------------------------ Pehkui por reflexión
    private static boolean mirado, hay;
    private static Object altura, anchura;
    private static Method datos, base, objetivo, poner, altoModelo, anchoModelo;

    private static boolean pehkui() {
        if (mirado) return hay;
        mirado = true;
        if (!ModList.get().isLoaded("pehkui")) return false;
        try {
            Class<?> tipos = Class.forName("virtuoel.pehkui.api.ScaleTypes");
            Class<?> tipo = Class.forName("virtuoel.pehkui.api.ScaleType");
            Class<?> dato = Class.forName("virtuoel.pehkui.api.ScaleData");
            Class<?> util = Class.forName("virtuoel.pehkui.util.ScaleUtils");
            altura = tipos.getField("HEIGHT").get(null);
            anchura = tipos.getField("WIDTH").get(null);
            datos = tipo.getMethod("getScaleData", Entity.class);
            base = dato.getMethod("getBaseScale");
            objetivo = dato.getMethod("getTargetScale");
            poner = dato.getMethod("setTargetScale", float.class);
            altoModelo = util.getMethod("getModelHeightScale", Entity.class);
            anchoModelo = util.getMethod("getModelWidthScale", Entity.class);
            hay = true;
            Miscelanea.LOGGER.info("[miscelanea] Pehkui encontrado: la talla se ajusta en los aparatos");
        } catch (Throwable t) {
            Miscelanea.LOGGER.warn("[miscelanea] Pehkui está instalado pero no se ha podido usar (¿otra versión?): no se ajusta la talla", t);
        }
        return hay;
    }

    /** ¿Va en algo del mod hecho para un jugador de tamaño normal? */
    private static boolean dentro(LivingEntity v) {
        Entity veh = v.getVehicle();
        if (veh == null) return false;
        if (veh instanceof com.miscelanea.tortura.aparatos.AsientoAparato || veh instanceof com.miscelanea.tortura.castigo.CepoEntity
                || veh instanceof com.miscelanea.tortura.castigo.JaulaEntity) return true;
        return leer(ATRACCIONES) && (veh instanceof com.miscelanea.feria.AsientoFeria || veh instanceof com.miscelanea.feria.CocheChoque);
    }

    /** Dos veces por segundo: al entrar se ajusta y al salir (como sea: suelto, muerto, desconectado y vuelto) se devuelve. */
    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || v.tickCount % 10 != 3) return;
        boolean guardado = v.getPersistentData().contains(CLAVE);
        boolean dentro = v.getVehicle() != null && dentro(v);
        if (!guardado && !dentro) return;
        if (!pehkui()) return;
        try {
            if (dentro && !guardado) {
                if (leer(ACTIVADO)) ajustar(v, leer(TALLA).floatValue());
            } else if (!dentro) devolver(v);
        } catch (Throwable t) {
            hay = false;
            Miscelanea.LOGGER.warn("[miscelanea] Fallo al cambiar la talla con Pehkui: se deja de ajustar", t);
        }
    }

    private static void ajustar(LivingEntity v, float talla) throws ReflectiveOperationException {
        float alto = (Float) altoModelo.invoke(null, v), ancho = (Float) anchoModelo.invoke(null, v);
        if (alto < 1.0E-3F || ancho < 1.0E-3F) return;
        if (Math.abs(alto - talla) < 0.03F && Math.abs(ancho - talla) < 0.03F) return;      // ya cabe
        Object da = datos.invoke(altura, v), dw = datos.invoke(anchura, v);
        CompoundTag antes = new CompoundTag();
        antes.putFloat("Alto", (Float) objetivo.invoke(da));
        antes.putFloat("Ancho", (Float) objetivo.invoke(dw));
        v.getPersistentData().put(CLAVE, antes);
        // la escala propia de la altura y la anchura, corregida por lo que sobra o falta en la talla que se ve
        poner.invoke(da, (Float) base.invoke(da) * talla / alto);
        poner.invoke(dw, (Float) base.invoke(dw) * talla / ancho);
    }

    private static void devolver(LivingEntity v) throws ReflectiveOperationException {
        CompoundTag antes = v.getPersistentData().getCompound(CLAVE);
        v.getPersistentData().remove(CLAVE);
        if (!antes.contains("Alto")) return;
        poner.invoke(datos.invoke(altura, v), antes.getFloat("Alto"));
        poner.invoke(datos.invoke(anchura, v), antes.getFloat("Ancho"));
    }
}

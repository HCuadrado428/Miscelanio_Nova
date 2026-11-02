package com.miscelanea.tortura;

import com.miscelanea.Miscelanea;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Compatibilidad con "Incapacitated" (CartoonishVillain, modid "incapacitated"): las muertes de la trampa tienen que
 * ser instantáneas, no dejar al jugador "derribado".
 * <ul>
 *   <li>Versiones nuevas: la etiqueta de tipos de daño {@code incapacitated:no_incap} (data/incapacitated/tags/damage_type/no_incap.json).</li>
 *   <li>1.9.3 LTS (1.20.1): en su LivingDeathEvent recorre {@code Incapacitated.instantKillDamageSourcesMessageID}
 *       (un {@code public static ArrayList<String>} que rehace al cargar su config "InstantKills") y mata del tirón si
 *       alguna entrada contiene el msgId del daño. Aquí se añade nuestro msgId por reflexión (sin depender del mod al
 *       compilar), al arrancar el servidor y otra vez justo antes de que la trampa mate (por si un
 *       {@code /incap config reload} ha rehecho la lista).</li>
 * </ul>
 * Cualquier error de reflexión se ignora (se avisa una vez en el log de depuración).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class IncapCompat {
    private IncapCompat() {}

    public static final String MODID = "incapacitated";
    private static final String CLASE = "com.cartoonishvillain.incapacitated.Incapacitated";
    private static final String CAMPO = "instantKillDamageSourcesMessageID";
    /** msgId de todos los tipos de daño de la sala de juegos que deben matar del tirón (añadir aquí los futuros). */
    public static final List<String> MSG_IDS = List.of("miscelanea.trampa_oso");

    private static Boolean cargado;
    private static Field campo;
    private static boolean fallo, avisado;

    @SubscribeEvent
    public static void servidorArrancado(ServerStartedEvent e) {
        asegurar();
    }

    /** Añade nuestros msgId a la lista de muertes instantáneas de Incapacitated (si está y si la lista existe). */
    @SuppressWarnings("unchecked")
    public static void asegurar() {
        if (cargado == null) cargado = ModList.get() != null && ModList.get().isLoaded(MODID);
        if (!cargado || fallo) return;
        try {
            if (campo == null) {
                Class<?> c = Class.forName(CLASE);
                campo = c.getField(CAMPO);
            }
            Object v = campo.get(null);
            if (!(v instanceof List<?> lista)) {
                // la versión instalada no tiene (o aún no ha creado) la lista: se reintenta la próxima vez
                avisar("lista no disponible (" + (v == null ? "null" : v.getClass().getName()) + ")", null);
                return;
            }
            List<Object> l = (List<Object>) lista;
            synchronized (l) {
                for (String id : MSG_IDS) if (!l.contains(id)) l.add(id);
            }
        } catch (ClassNotFoundException | NoSuchFieldException ex) {
            // otra versión del mod (usa la etiqueta no_incap): no hace falta insistir
            fallo = true;
            avisar("sin " + CLASE + "." + CAMPO, ex);
        } catch (Throwable ex) {
            avisar("error de reflexión", ex);
        }
    }

    private static void avisar(String que, Throwable ex) {
        if (avisado) return;
        avisado = true;
        if (ex != null) Miscelanea.LOGGER.debug("[miscelanea] Compatibilidad con Incapacitated: {}", que, ex);
        else Miscelanea.LOGGER.debug("[miscelanea] Compatibilidad con Incapacitated: {}", que);
    }
}

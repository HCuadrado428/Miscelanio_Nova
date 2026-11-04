package com.miscelanea.client.voz;

import com.miscelanea.client.voz.procesadores.ProcAgudo;
import com.miscelanea.client.voz.procesadores.ProcAmortiguado;
import com.miscelanea.client.voz.procesadores.ProcFantasma;
import com.miscelanea.client.voz.procesadores.ProcGrave;
import com.miscelanea.client.voz.procesadores.ProcGutural;
import com.miscelanea.client.voz.procesadores.ProcHelio;
import com.miscelanea.client.voz.procesadores.ProcLenguaTrapo;
import com.miscelanea.client.voz.procesadores.ProcSusurro;
import com.miscelanea.client.voz.procesadores.ProcTartamudeo;
import com.miscelanea.client.voz.procesadores.ProcVentilador;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registro de voces: id (cadena) → fábrica de {@link ProcesadorVoz}. Es la mitad del cliente del sistema genérico
 * de voces; la otra mitad es {@code com.miscelanea.voz.VozPorEfecto} (servidor: qué efecto de poción da qué voz).
 *
 * <h3>Cómo añadir una voz nueva desde otro paquete</h3>
 * <ol>
 *   <li>Escribe una clase que implemente {@link ProcesadorVoz} (recibe fotogramas de 960 muestras, 16 bits, 48 kHz,
 *       y los cambia en el sitio; guarda su propio estado, se crea una por hablante).</li>
 *   <li>Regístrala con un id: {@code Voces.registrar("mi_voz", MiProcesador::new)} (en el cliente, p. ej. en un
 *       FMLClientSetupEvent; vale cualquier momento antes de que alguien hable).</li>
 *   <li>Apunta qué efecto la provoca (en común): {@code VozPorEfecto.poner(new ResourceLocation("miscelanea",
 *       "mi_efecto"), "mi_voz")}. Varias voces para un efecto: {@code "grave,amortiguado"}.</li>
 * </ol>
 * Las voces de un mismo jugador se encadenan en el orden en que llegan del servidor (el de sus efectos). Si un id no
 * está registrado se ignora sin más (por ejemplo, un efecto de otra rama que aún no tiene procesador). Todo esto solo
 * actúa si está instalado Simple Voice Chat; sin él, los efectos siguen existiendo pero la voz no cambia.
 */
public final class Voces {
    private Voces() {}

    private static final Map<String, Supplier<ProcesadorVoz>> REGISTRO = new LinkedHashMap<>();

    static {
        registrar("helio", ProcHelio::new);
        registrar("tartamudeo", ProcTartamudeo::new);
        registrar("ventilador", ProcVentilador::new);
        registrar("lengua_trapo", ProcLenguaTrapo::new);
        registrar("grave", ProcGrave::new);
        registrar("agudo", ProcAgudo::new);
        registrar("susurro", ProcSusurro::new);
        registrar("fantasma", ProcFantasma::new);
        registrar("gutural", ProcGutural::new);
        registrar("amortiguado", ProcAmortiguado::new);
        registrar("estatica", com.miscelanea.client.voz.procesadores.ProcEstatica::new);
    }

    public static synchronized void registrar(String id, Supplier<ProcesadorVoz> fabrica) {
        REGISTRO.put(id, fabrica);
    }

    /** Instancia nueva del procesador de ese id, o {@code null} si no hay. */
    public static synchronized ProcesadorVoz crear(String id) {
        Supplier<ProcesadorVoz> f = REGISTRO.get(id);
        return f == null ? null : f.get();
    }

    public static synchronized boolean existe(String id) {
        return REGISTRO.containsKey(id);
    }
}

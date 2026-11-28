package com.miscelanea.client.rave;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Lo que suena por los altavoces del ordenador, en Windows, sin configurar nada: se le pide a Windows (WASAPI, en modo
 * "loopback") una copia de lo que sale por el dispositivo de sonido por defecto. Es lo que hace falta para emitir Spotify
 * por la mesa de DJ: el sonido de Java solo sabe grabar de entradas (micrófonos), y el sonido del ordenador no es una
 * entrada salvo que se active la "Mezcla estéreo" o se instale un cable virtual.
 *
 * <p>Se usa entero desde un solo hilo (el de la captura): se crea, se llama a {@link #leer} en bucle y se cierra ahí.
 * Recoge TODO lo que suena en el ordenador, también el propio juego.
 */
final class SonidoPcWindows implements AutoCloseable {
    private static final GUID CLSID_ENUMERADOR = new GUID("{BCDE0395-E52F-467C-8E3D-C4579291692E}");
    private static final GUID IID_ENUMERADOR = new GUID("{A95664D2-9614-4F35-A746-DE8DB63617E6}");
    private static final GUID IID_CLIENTE = new GUID("{1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}");
    private static final GUID IID_CAPTURA = new GUID("{C8ADBD64-E71E-48A0-A4DE-185C395CD317}");
    private static final int CLSCTX_ALL = 0x17, COMPARTIDO = 0, LOOPBACK = 0x00020000, SILENCIO = 0x2;
    private static final int RPC_E_CHANGED_MODE = 0x80010106;

    /** ¿Se puede usar en este ordenador? (solo Windows) */
    static boolean disponible() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private Pointer enumerador, dispositivo, cliente, captura, formato;
    private boolean com;
    private int canales, frecuencia, bytesMuestra, bloque;
    private boolean flotante;
    // remuestreo a 48 kHz (interpolando entre la muestra anterior y la nueva)
    private double pos;
    private float anterior;
    /** Muestras ya convertidas (mono, 48 kHz) que esperan a salir de 960 en 960; cabe un segundo. */
    private final short[] cola = new short[48000];
    private int enCola;

    /** Llama a un método de un objeto COM por su posición en la tabla. Devuelve el HRESULT (negativo = error). */
    private static int llamar(Pointer objeto, int indice, Object... args) {
        Pointer tabla = objeto.getPointer(0);
        Function f = Function.getFunction(tabla.getPointer((long) indice * Native.POINTER_SIZE), Function.ALT_CONVENTION);
        Object[] a = new Object[args.length + 1];
        a[0] = objeto;
        System.arraycopy(args, 0, a, 1, args.length);
        return f.invokeInt(a);
    }

    private static void comprobar(int hr, String que) {
        if (hr < 0) throw new IllegalStateException(que + " (0x" + Integer.toHexString(hr) + ")");
    }

    private static Pointer de(GUID g) {
        g.write();
        return g.getPointer();
    }

    /** Abre la captura del dispositivo de salida por defecto. Lanza una excepción si Windows no deja. */
    SonidoPcWindows() {
        try {
            int hr = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, 0).intValue();
            com = hr >= 0;
            if (hr < 0 && hr != RPC_E_CHANGED_MODE) comprobar(hr, "CoInitializeEx");
            PointerByReference p = new PointerByReference();
            comprobar(Ole32.INSTANCE.CoCreateInstance(CLSID_ENUMERADOR, Pointer.NULL, CLSCTX_ALL, IID_ENUMERADOR, p).intValue(), "enumerador de sonido");
            enumerador = p.getValue();
            // 4 = GetDefaultAudioEndpoint(eRender, eConsole)
            p = new PointerByReference();
            comprobar(llamar(enumerador, 4, 0, 0, p), "dispositivo de salida por defecto");
            dispositivo = p.getValue();
            // 3 = IMMDevice::Activate
            p = new PointerByReference();
            comprobar(llamar(dispositivo, 3, de(IID_CLIENTE), CLSCTX_ALL, Pointer.NULL, p), "cliente de audio");
            cliente = p.getValue();
            // 8 = IAudioClient::GetMixFormat
            p = new PointerByReference();
            comprobar(llamar(cliente, 8, p), "formato de mezcla");
            formato = p.getValue();
            int etiqueta = formato.getShort(0) & 0xFFFF;
            canales = formato.getShort(2) & 0xFFFF;
            frecuencia = formato.getInt(4);
            bloque = formato.getShort(12) & 0xFFFF;
            int bits = formato.getShort(14) & 0xFFFF;
            if (etiqueta == 0xFFFE && (formato.getShort(16) & 0xFFFF) >= 22) etiqueta = formato.getInt(24) & 0xFFFF;   // WAVEFORMATEXTENSIBLE: el tipo va en SubFormat
            flotante = etiqueta == 3;
            bytesMuestra = canales <= 0 ? 0 : bloque / canales;
            boolean vale = canales >= 1 && canales <= 16 && frecuencia >= 8000 && frecuencia <= 384000
                    && (flotante ? bytesMuestra == 4 && bits == 32 : etiqueta == 1 && (bytesMuestra == 2 || bytesMuestra == 3 || bytesMuestra == 4));
            if (!vale) throw new IllegalStateException("formato de sonido no admitido (tipo " + etiqueta + ", " + canales + " canales, " + bits + " bits, " + frecuencia + " Hz)");
            // 3 = IAudioClient::Initialize(compartido, loopback, 200 ms de búfer, 0, formato, sin sesión)
            comprobar(llamar(cliente, 3, COMPARTIDO, LOOPBACK, 2_000_000L, 0L, formato, Pointer.NULL), "abrir la captura del sonido del PC");
            // 14 = IAudioClient::GetService
            p = new PointerByReference();
            comprobar(llamar(cliente, 14, de(IID_CAPTURA), p), "servicio de captura");
            captura = p.getValue();
            // 10 = IAudioClient::Start
            comprobar(llamar(cliente, 10), "empezar la captura");
        } catch (Throwable t) {
            close();
            throw t;
        }
    }

    String descripcion() {
        return canales + " canales, " + frecuencia + " Hz, " + (flotante ? "coma flotante" : (bytesMuestra * 8) + " bits");
    }

    /**
     * Rellena {@code mono} (960 muestras: 20 ms a 48 kHz) con lo siguiente que ha sonado. Devuelve true si lo ha rellenado
     * y false si aún no hay bastante (cuando no suena nada en el ordenador, Windows no manda nada): entonces hay que esperar
     * un poco y volver a llamar.
     */
    boolean leer(short[] mono) {
        while (enCola < 960) {
            IntByReference n = new IntByReference();
            comprobar(llamar(captura, 5, n), "tamaño del paquete");      // 5 = GetNextPacketSize
            if (n.getValue() <= 0) return false;
            PointerByReference datos = new PointerByReference();
            IntByReference cuantos = new IntByReference(), marcas = new IntByReference();
            comprobar(llamar(captura, 3, datos, cuantos, marcas, Pointer.NULL, Pointer.NULL), "leer el sonido");   // 3 = GetBuffer
            int tramas = cuantos.getValue();
            if (tramas <= 0) {
                llamar(captura, 4, 0);
                return false;
            }
            try {
                boolean silencio = (marcas.getValue() & SILENCIO) != 0 || datos.getValue() == null;
                ByteBuffer b = silencio ? null : datos.getValue().getByteBuffer(0, (long) tramas * bloque).order(ByteOrder.LITTLE_ENDIAN);
                double paso = frecuencia / 48000.0;
                int usados = Math.min(2, canales);
                for (int i = 0; i < tramas; i++) {
                    float s = 0;
                    if (b != null) {
                        int k = i * bloque;
                        // solo los dos primeros canales (izquierdo y derecho): con un equipo 5.1 o 7.1 la música va por esos dos,
                        // y hacer la media con los demás (vacíos) la dejaría bajísima
                        for (int c = 0; c < usados; c++, k += bytesMuestra) {
                            if (flotante) s += b.getFloat(k);
                            else if (bytesMuestra == 2) s += b.getShort(k) / 32768F;
                            else if (bytesMuestra == 3) s += (((b.get(k) & 0xFF) | ((b.get(k + 1) & 0xFF) << 8) | (b.get(k + 2) << 16))) / 8388608F;
                            else s += b.getInt(k) / 2147483648F;
                        }
                        s /= usados;
                    }
                    while (pos < 1.0) {
                        float v = anterior + (s - anterior) * (float) pos;
                        if (enCola < cola.length) cola[enCola++] = (short) Math.max(-32768, Math.min(32767, Math.round(v * 32767F)));
                        pos += paso;
                    }
                    pos -= 1.0;
                    anterior = s;
                }
            } finally {
                llamar(captura, 4, tramas);      // 4 = ReleaseBuffer
            }
        }
        System.arraycopy(cola, 0, mono, 0, 960);
        enCola -= 960;
        System.arraycopy(cola, 960, cola, 0, enCola);
        return true;
    }

    private static void soltar(Pointer p) {
        if (p == null) return;
        try {
            llamar(p, 2);     // 2 = Release
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void close() {
        try {
            if (cliente != null) llamar(cliente, 11);     // 11 = IAudioClient::Stop
        } catch (Throwable ignored) {
        }
        soltar(captura);
        soltar(cliente);
        soltar(dispositivo);
        soltar(enumerador);
        captura = cliente = dispositivo = enumerador = null;
        try {
            if (formato != null) Ole32.INSTANCE.CoTaskMemFree(formato);
            formato = null;
            if (com) Ole32.INSTANCE.CoUninitialize();
            com = false;
        } catch (Throwable ignored) {
        }
    }
}

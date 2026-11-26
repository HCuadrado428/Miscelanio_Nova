package com.miscelanea.laboratorio;

import com.miscelanea.tortura.aparatos.AparatoBlockEntity;
import com.miscelanea.tortura.aparatos.AparatosVictimas;
import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La parte común de las estaciones nuevas del laboratorio (microgravedad, cabina de voz, test de reflejos, sala de
 * interferencias y cámara de contención de glitch). El sujeto va de pie dentro de la cabina y el panel está en el bloque
 * de detrás: clic derecho arranca y para; agachado cambia el nivel (el extremo pide pulsar dos veces). Cada estación
 * pone sus efectos, tiene su propio QTE a la mitad del experimento y su castigo si no se aguanta, y al acabar apunta la
 * ficha en el cuaderno y da su muestra.
 */
public abstract class EstacionBlockEntity extends AparatoBlockEntity implements ConsolaLab {
    protected int nivel;
    protected boolean encendida, terminado, resistio, qteHecho;
    protected int tiempo, aguante;
    protected long confirma = -1000, castigoEn;

    protected EstacionBlockEntity(BlockEntityType<?> t, BlockPos p, BlockState s) {
        super(t, p, s);
    }

    // ------------------------------------------------------------------ lo que pone cada estación
    protected abstract String clave();
    /** Cada segundo, mientras el sujeto está dentro y la estación en marcha. */
    protected abstract void efectos(ServerLevel sl, LivingEntity v);
    /** Ticks de experimento hasta el QTE (según el nivel). */
    protected abstract int hastaQte();
    protected QteTipo tipoQte() { return QteTipo.SECUENCIA; }
    protected abstract Component tituloQte();
    /** Lo que le pasa al que no aguanta (después de apuntar la ficha). */
    protected abstract void castigo(ServerLevel sl, LivingEntity v);
    protected abstract String resultadoCastigo();
    protected void lecturasExtra(Map<String, String> m) {}
    protected abstract Item muestra();
    protected void alArrancar(ServerLevel sl) {}
    protected void cadaTick(ServerLevel sl, LivingEntity v) {}

    // ------------------------------------------------------------------ cabina por defecto
    @Override
    public int tipo() {
        return AparatosVictimas.DONCELLA;
    }

    @Override
    public Vec3 sitioVictima(LivingEntity v) {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.1, worldPosition.getZ() + 0.5);
    }

    @Override
    public boolean sentado() {
        return false;
    }

    public int nivel() { return nivel; }
    public boolean encendida() { return encendida; }

    // ------------------------------------------------------------------ manejo
    @Override
    protected void usarExtra(Player p, boolean anexo, BlockHitResult hit) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!LabConfig.estaciones()) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.estacion.apagada").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!anexo) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.estacion.ayuda"), true);
            return;
        }
        // agachado: siguiente nivel sin abrir el panel; sin agacharse, el panel se abre en el cliente (ver ConsolaLab)
        if (p.isShiftKeyDown()) cambiarNivel(sl, p);
    }

    // ------------------------------------------------------------------ consola (panel) y mensajes
    @Override
    protected boolean agachadoSaca(boolean anexo) {
        return !anexo;   // en la consola, agachado cambia el nivel; en la cabina, saca al sujeto
    }

    @Override
    protected String claveMensajes() {
        return "lab";
    }

    @Override
    public int ajuste() {
        return nivel;
    }

    @Override
    public boolean enMarcha() {
        return encendida;
    }

    @Override
    public void ponerAjuste(ServerPlayer p, int valor) {
        nivel = Math.max(0, Math.min(ConsolaLab.nivelMaximo(), valor));
        ConsolaLab.avisarNivel(p, nivel);
        sonido(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 1.0F);
        marcar();
    }

    @Override
    public void arrancarDesdeConsola(ServerPlayer p) {
        if (encendida || !(level instanceof ServerLevel sl)) return;
        if (!ocupado()) {   // antes de puedeArrancar: la de contención gasta la muestra de glitch
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.sin_sujeto").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (puedeArrancar(sl, p)) encender(sl, p);
    }

    @Override
    public void pararDesdeConsola(ServerPlayer p) {
        if (encendida) apagar(p, true);
    }

    /** Para estaciones que piden algo para empezar (la de contención pide una muestra de glitch). */
    protected boolean puedeArrancar(ServerLevel sl, Player p) {
        return true;
    }

    /** Para las pruebas: fija el nivel sin la confirmación. */
    public void fijarNivel(int n) {
        nivel = Math.max(0, Math.min(2, n));
        marcar();
    }

    private void cambiarNivel(ServerLevel sl, Player p) {
        int siguiente = (nivel + 1) % (LabConfig.nivelMax() + 1);
        if (siguiente == 2 && sl.getGameTime() - confirma > 100) {
            confirma = sl.getGameTime();
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.extremo.confirmar").withStyle(ChatFormatting.RED), true);
            return;
        }
        nivel = siguiente;
        p.displayClientMessage(Component.translatable("laboratorio.miscelanea.estacion.nivel", Component.translatable("laboratorio.miscelanea.nivel." + nivel))
                .withStyle(nivel == 2 ? ChatFormatting.RED : ChatFormatting.YELLOW), true);
        sonido(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 1.0F);
        marcar();
    }

    public void encender(ServerLevel sl, Player p) {
        if (!ocupado()) {
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.sin_sujeto").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!terminado && tiempo > 0) {
            terminado = true;
            cerrarFicha(sl, "paro");
        }
        if (nivel > LabConfig.nivelMax()) nivel = LabConfig.nivelMax();
        encendida = true;
        terminado = false;
        qteHecho = resistio = false;
        tiempo = aguante = 0;
        castigoEn = 0;
        captor = p.getUUID();
        alArrancar(sl);
        p.displayClientMessage(Component.translatable("laboratorio.miscelanea.estacion.arranca").withStyle(ChatFormatting.YELLOW), true);
        sonido(SoundEvents.IRON_DOOR_CLOSE, 1.0F, 0.6F);
        marcar();
    }

    public void apagar(@Nullable Player quien, boolean manual) {
        if (encendida && !terminado && tiempo > 0 && level instanceof ServerLevel sl) {
            terminado = true;
            cerrarFicha(sl, resistio ? "aguanto" : "paro");
        }
        encendida = false;
        castigoEn = 0;
        if (manual) cancelarQte(victimaEntidad());
        if (manual && quien != null) quien.displayClientMessage(Component.translatable("laboratorio.miscelanea.estacion.para").withStyle(ChatFormatting.GRAY), true);
        marcar();
    }

    // ------------------------------------------------------------------ el experimento
    @Override
    protected void tickVictima(ServerLevel sl, LivingEntity v) {
        if (!encendida || terminado) return;
        tiempo++;
        aguante++;
        cadaTick(sl, v);
        if (v.tickCount % 20 == 0) efectos(sl, v);
        if (!qteHecho && hastaQte() > 0 && tiempo >= hastaQte()) {
            qteHecho = true;
            if (v instanceof ServerPlayer vp) {
                if (Qte.activo(vp)) Qte.cancelar(vp);
                Qte.iniciar(vp, tipoQte(), QteParams.de(0.3F + 0.2F * nivel).titulo(tituloQte()), (pl, r) -> {
                    resistio = r.exito();
                    if (r.exito()) {
                        if (pl != null) pl.displayClientMessage(Component.translatable("laboratorio.miscelanea.estacion.aguantas").withStyle(ChatFormatting.GREEN), true);
                        apagar(null, false);
                    } else {
                        fallar(sl, v);
                    }
                });
            } else {
                castigoEn = sl.getGameTime() + 100;   // un mob no puede pulsar nada
            }
        }
        if (castigoEn > 0 && sl.getGameTime() >= castigoEn) {
            castigoEn = 0;
            fallar(sl, v);
        }
    }

    /** El sujeto no aguanta: se apunta la ficha y se le aplica el castigo del nivel. */
    protected void fallar(ServerLevel sl, LivingEntity v) {
        // ya parada o con el sujeto fuera: no hay castigo (el QTE puede acabar después de que el científico la pare o lo saque)
        if (terminado || !encendida || !esVictima(v)) return;
        resistio = false;
        terminado = true;
        cerrarFicha(sl, resultadoCastigo());
        encendida = false;
        castigo(sl, v);
        marcar();
    }

    /** El experimento se da por bueno (para los que terminan sin QTE o con varios). */
    protected void terminarBien(ServerLevel sl) {
        if (terminado) return;
        resistio = true;
        terminado = true;
        cerrarFicha(sl, "aguanto");
        encendida = false;
        LivingEntity v = victimaEntidad();
        if (v instanceof ServerPlayer vp) vp.displayClientMessage(Component.translatable("laboratorio.miscelanea.estacion.aguantas").withStyle(ChatFormatting.GREEN), true);
        marcar();
    }

    @Override
    protected void alSacar(@Nullable LivingEntity v) {
        // lo de esta vuelta ya no cuenta: ni castigo cuando acabe el QTE ni una ficha falsa al volver a arrancar
        terminado = true;
        encendida = false;
        castigoEn = 0;
        cancelarQte(v);
    }

    /** Quita el QTE de la pantalla del sujeto (su resultado ya no hace nada: la estación está parada). */
    protected static void cancelarQte(@Nullable LivingEntity v) {
        if (v instanceof ServerPlayer vp && Qte.activo(vp)) Qte.cancelar(vp);
    }

    protected void cerrarFicha(ServerLevel sl, String resultado) {
        if (captor == null) return;
        Player cientifico = sl.getServer().getPlayerList().getPlayer(captor);
        LivingEntity v = victimaEntidad();
        if (cientifico == null) return;
        Map<String, String> m = new LinkedHashMap<>();
        lecturasExtra(m);
        m.put("aguante", (aguante / 20) + " s");
        m.put("resultado", resultado);
        if (v != null) m.put("pulso", Experimentos.pulso(v) + " ppm");
        Experimentos.anotar(cientifico, clave(), v instanceof Player pl ? pl : null, nivel, m);
        if (Experimentos.darMuestra(cientifico, muestra()))
            cientifico.displayClientMessage(Component.translatable("laboratorio.miscelanea.muestra_obtenida", Component.translatable(muestra().getDescriptionId())), true);
    }

    // ------------------------------------------------------------------ guardar
    @Override
    protected void guardar(CompoundTag t) {
        t.putInt("Nivel", nivel);
        t.putBoolean("Encendida", encendida);
        guardarExtra(t);
    }

    @Override
    protected void cargar(CompoundTag t) {
        nivel = t.getInt("Nivel");
        encendida = t.getBoolean("Encendida");
        cargarExtra(t);
    }

    protected void guardarExtra(CompoundTag t) {}
    protected void cargarExtra(CompoundTag t) {}
}

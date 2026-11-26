package com.miscelanea.laboratorio;

import com.miscelanea.tortura.aparatos.AparatoBlock;
import com.miscelanea.tortura.aparatos.Aparatos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * La consola de una estación del laboratorio (el bloque de detrás de la cabina): lo que maneja el panel que se abre con clic
 * derecho en ella. Lo implementan las cinco estaciones, la cámara de temperatura, el tanque de privación y la centrífuga.
 * Las órdenes llegan del cliente por {@link com.miscelanea.tortura.aparatos.AparatoAccionPacket} y allí se comprueban (distancia,
 * que pueda manejarla, que no sea el sujeto).
 */
public interface ConsolaLab {
    /** Lo que está elegido: el nivel (0 suave, 1 intenso, 2 extremo) o, en la cámara de temperatura, el dial (de -3 a +3). */
    int ajuste();

    /** ¿Se elige con el dial de temperatura en vez de con los tres niveles? */
    default boolean conDial() {
        return false;
    }

    boolean enMarcha();

    /** Pone el ajuste elegido en el panel (se recorta al máximo de la configuración). */
    void ponerAjuste(ServerPlayer p, int valor);

    void arrancarDesdeConsola(ServerPlayer p);

    void pararDesdeConsola(ServerPlayer p);

    /** El nivel más alto que deja la configuración (0 a 2). */
    static int nivelMaximo() {
        return LabConfig.nivelMax();
    }

    /** Aviso común al cambiar de nivel. */
    static void avisarNivel(Player p, int nivel) {
        p.displayClientMessage(Component.translatable("laboratorio.miscelanea.estacion.nivel", Component.translatable("laboratorio.miscelanea.nivel." + nivel))
                .withStyle(nivel == 2 ? ChatFormatting.RED : ChatFormatting.YELLOW), true);
    }

    /** Clic derecho en la consola sin agacharse: en el cliente, abre el panel. La orden la sigue procesando el bloque en el servidor. */
    static void abrirPanel(BlockState s, Level l, BlockPos pos, Player p, InteractionHand mano) {
        if (!l.isClientSide || mano != InteractionHand.MAIN_HAND || !s.getValue(AparatoBlock.ANEXO) || p.isShiftKeyDown() || Aparatos.en(p)) return;
        BlockPos principal = AparatoBlock.principal(s, pos);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.laboratorio.ConsolaScreen.abrir(principal));
    }
}

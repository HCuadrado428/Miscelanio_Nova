package com.miscelanea.network;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOLO = "57";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Miscelanea.MOD_ID, "main"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(FlashbangPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FlashbangPacket::encode)
                .decoder(FlashbangPacket::new)
                .consumerMainThread(FlashbangPacket::handle)
                .add();
        CHANNEL.messageBuilder(SoltarCapturaPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SoltarCapturaPacket::encode)
                .decoder(SoltarCapturaPacket::new)
                .consumerMainThread(SoltarCapturaPacket::handle)
                .add();
        CHANNEL.messageBuilder(AgarrePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AgarrePacket::encode)
                .decoder(AgarrePacket::new)
                .consumerMainThread(AgarrePacket::handle)
                .add();
        CHANNEL.messageBuilder(ForcejeoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ForcejeoPacket::encode)
                .decoder(ForcejeoPacket::new)
                .consumerMainThread(ForcejeoPacket::handle)
                .add();
        CHANNEL.messageBuilder(MedigunPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MedigunPacket::encode)
                .decoder(MedigunPacket::new)
                .consumerMainThread(MedigunPacket::handle)
                .add();
        CHANNEL.messageBuilder(MochilaAccionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MochilaAccionPacket::encode)
                .decoder(MochilaAccionPacket::new)
                .consumerMainThread(MochilaAccionPacket::handle)
                .add();
        CHANNEL.messageBuilder(CapturaPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CapturaPacket::encode)
                .decoder(CapturaPacket::new)
                .consumerMainThread(CapturaPacket::handle)
                .add();
        CHANNEL.messageBuilder(LazoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LazoPacket::encode)
                .decoder(LazoPacket::new)
                .consumerMainThread(LazoPacket::handle)
                .add();
        CHANNEL.messageBuilder(NucleoActivarPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NucleoActivarPacket::encode)
                .decoder(NucleoActivarPacket::new)
                .consumerMainThread(NucleoActivarPacket::handle)
                .add();
        CHANNEL.messageBuilder(NucleoEstadoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NucleoEstadoPacket::encode)
                .decoder(NucleoEstadoPacket::new)
                .consumerMainThread(NucleoEstadoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.tinta.TintaPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.tinta.TintaPacket::encode)
                .decoder(com.miscelanea.tinta.TintaPacket::new)
                .consumerMainThread(com.miscelanea.tinta.TintaPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.tinta.DepositoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.tinta.DepositoPacket::encode)
                .decoder(com.miscelanea.tinta.DepositoPacket::new)
                .consumerMainThread(com.miscelanea.tinta.DepositoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.gravedad.GravedadPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.gravedad.GravedadPacket::encode)
                .decoder(com.miscelanea.gravedad.GravedadPacket::new)
                .consumerMainThread(com.miscelanea.gravedad.GravedadPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.armas.GanchoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.armas.GanchoPacket::encode)
                .decoder(com.miscelanea.armas.GanchoPacket::new)
                .consumerMainThread(com.miscelanea.armas.GanchoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.llave.AccionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.llave.AccionPacket::encode)
                .decoder(com.miscelanea.llave.AccionPacket::new)
                .consumerMainThread(com.miscelanea.llave.AccionPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.llave.AnimPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.llave.AnimPacket::encode)
                .decoder(com.miscelanea.llave.AnimPacket::new)
                .consumerMainThread(com.miscelanea.llave.AnimPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.llave.PmPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.llave.PmPacket::encode)
                .decoder(com.miscelanea.llave.PmPacket::new)
                .consumerMainThread(com.miscelanea.llave.PmPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.hacha.HachaPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.hacha.HachaPacket::encode)
                .decoder(com.miscelanea.hacha.HachaPacket::new)
                .consumerMainThread(com.miscelanea.hacha.HachaPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.hacha.PosturaPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.hacha.PosturaPacket::encode)
                .decoder(com.miscelanea.hacha.PosturaPacket::new)
                .consumerMainThread(com.miscelanea.hacha.PosturaPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.hacha.PosturaOtroPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.hacha.PosturaOtroPacket::encode)
                .decoder(com.miscelanea.hacha.PosturaOtroPacket::new)
                .consumerMainThread(com.miscelanea.hacha.PosturaOtroPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.bichos.ColeccionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.bichos.ColeccionPacket::encode)
                .decoder(com.miscelanea.bichos.ColeccionPacket::new)
                .consumerMainThread(com.miscelanea.bichos.ColeccionPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.bichos.PescaPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.bichos.PescaPacket::encode)
                .decoder(com.miscelanea.bichos.PescaPacket::new)
                .consumerMainThread(com.miscelanea.bichos.PescaPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.bichos.PescaResultadoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.bichos.PescaResultadoPacket::encode)
                .decoder(com.miscelanea.bichos.PescaResultadoPacket::new)
                .consumerMainThread(com.miscelanea.bichos.PescaResultadoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.consola.RecordsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.consola.RecordsPacket::encode)
                .decoder(com.miscelanea.consola.RecordsPacket::new)
                .consumerMainThread(com.miscelanea.consola.RecordsPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.consola.RecordPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.consola.RecordPacket::encode)
                .decoder(com.miscelanea.consola.RecordPacket::new)
                .consumerMainThread(com.miscelanea.consola.RecordPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.ocarina.NotaPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.ocarina.NotaPacket::encode)
                .decoder(com.miscelanea.ocarina.NotaPacket::new)
                .consumerMainThread(com.miscelanea.ocarina.NotaPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.ocarina.CancionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.ocarina.CancionPacket::encode)
                .decoder(com.miscelanea.ocarina.CancionPacket::new)
                .consumerMainThread(com.miscelanea.ocarina.CancionPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.arcade.ArcadeAbrirPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.arcade.ArcadeAbrirPacket::encode)
                .decoder(com.miscelanea.arcade.ArcadeAbrirPacket::new)
                .consumerMainThread(com.miscelanea.arcade.ArcadeAbrirPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.arcade.ArcadeAccionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.arcade.ArcadeAccionPacket::encode)
                .decoder(com.miscelanea.arcade.ArcadeAccionPacket::new)
                .consumerMainThread(com.miscelanea.arcade.ArcadeAccionPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.arcade.ArcadeFramePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.arcade.ArcadeFramePacket::encode)
                .decoder(com.miscelanea.arcade.ArcadeFramePacket::new)
                .consumerMainThread(com.miscelanea.arcade.ArcadeFramePacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.arcade.ArcadeFrameS2CPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.arcade.ArcadeFrameS2CPacket::encode)
                .decoder(com.miscelanea.arcade.ArcadeFrameS2CPacket::new)
                .consumerMainThread(com.miscelanea.arcade.ArcadeFrameS2CPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.feria.GanchoPacket.class, id++)
                .encoder(com.miscelanea.feria.GanchoPacket::encode)
                .decoder(com.miscelanea.feria.GanchoPacket::new)
                .consumerMainThread(com.miscelanea.feria.GanchoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.cartas.CartasPacket.class, id++)
                .encoder(com.miscelanea.cartas.CartasPacket::encode)
                .decoder(com.miscelanea.cartas.CartasPacket::new)
                .consumerMainThread(com.miscelanea.cartas.CartasPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.mesas.AirHockeyMandoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.mesas.AirHockeyMandoPacket::encode)
                .decoder(com.miscelanea.mesas.AirHockeyMandoPacket::new)
                .consumerMainThread(com.miscelanea.mesas.AirHockeyMandoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.armas.TemblorPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.armas.TemblorPacket::encode)
                .decoder(com.miscelanea.armas.TemblorPacket::new)
                .consumerMainThread(com.miscelanea.armas.TemblorPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.feria.ChoquePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.feria.ChoquePacket::encode)
                .decoder(com.miscelanea.feria.ChoquePacket::new)
                .consumerMainThread(com.miscelanea.feria.ChoquePacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.baile.BailePacket.class, id++)
                .encoder(com.miscelanea.baile.BailePacket::encode)
                .decoder(com.miscelanea.baile.BailePacket::new)
                .consumerMainThread(com.miscelanea.baile.BailePacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.arcade.ArcadeInputPacket.class, id++)
                .encoder(com.miscelanea.arcade.ArcadeInputPacket::encode)
                .decoder(com.miscelanea.arcade.ArcadeInputPacket::new)
                .consumerMainThread(com.miscelanea.arcade.ArcadeInputPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.arcade.ArcadeRecordsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.arcade.ArcadeRecordsPacket::encode)
                .decoder(com.miscelanea.arcade.ArcadeRecordsPacket::new)
                .consumerMainThread(com.miscelanea.arcade.ArcadeRecordsPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.tesoros.DetectorPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.tesoros.DetectorPacket::encode)
                .decoder(com.miscelanea.tesoros.DetectorPacket::new)
                .consumerMainThread(com.miscelanea.tesoros.DetectorPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.juegos.TableroClicPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.juegos.TableroClicPacket::encode)
                .decoder(com.miscelanea.juegos.TableroClicPacket::new)
                .consumerMainThread(com.miscelanea.juegos.TableroClicPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.trampolin.TrucoPacket.class, id++)
                .encoder(com.miscelanea.trampolin.TrucoPacket::encode)
                .decoder(com.miscelanea.trampolin.TrucoPacket::new)
                .consumerMainThread(com.miscelanea.trampolin.TrucoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.imperio.ImperioClicPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.imperio.ImperioClicPacket::encode)
                .decoder(com.miscelanea.imperio.ImperioClicPacket::new)
                .consumerMainThread(com.miscelanea.imperio.ImperioClicPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.mesas.MesaFisicaPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.mesas.MesaFisicaPacket::encode)
                .decoder(com.miscelanea.mesas.MesaFisicaPacket::new)
                .consumerMainThread(com.miscelanea.mesas.MesaFisicaPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.mesas.BillarTiroPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.mesas.BillarTiroPacket::encode)
                .decoder(com.miscelanea.mesas.BillarTiroPacket::new)
                .consumerMainThread(com.miscelanea.mesas.BillarTiroPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.mesas.FutbolinMandoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.mesas.FutbolinMandoPacket::encode)
                .decoder(com.miscelanea.mesas.FutbolinMandoPacket::new)
                .consumerMainThread(com.miscelanea.mesas.FutbolinMandoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.parchis.ParchisClicPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.parchis.ParchisClicPacket::encode)
                .decoder(com.miscelanea.parchis.ParchisClicPacket::new)
                .consumerMainThread(com.miscelanea.parchis.ParchisClicPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.oca.OcaClicPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.oca.OcaClicPacket::encode)
                .decoder(com.miscelanea.oca.OcaClicPacket::new)
                .consumerMainThread(com.miscelanea.oca.OcaClicPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.cuatro.CuatroClicPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.cuatro.CuatroClicPacket::encode)
                .decoder(com.miscelanea.cuatro.CuatroClicPacket::new)
                .consumerMainThread(com.miscelanea.cuatro.CuatroClicPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.flota.FlotaClicPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.flota.FlotaClicPacket::encode)
                .decoder(com.miscelanea.flota.FlotaClicPacket::new)
                .consumerMainThread(com.miscelanea.flota.FlotaClicPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.domino.DominoClicPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.domino.DominoClicPacket::encode)
                .decoder(com.miscelanea.domino.DominoClicPacket::new)
                .consumerMainThread(com.miscelanea.domino.DominoClicPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.mesas.PrivadoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.mesas.PrivadoPacket::encode)
                .decoder(com.miscelanea.mesas.PrivadoPacket::new)
                .consumerMainThread(com.miscelanea.mesas.PrivadoPacket::handle)
                .add();
        id = com.miscelanea.tortura.TorturaRed.registrar(CHANNEL, id);
        CHANNEL.messageBuilder(com.miscelanea.armas.BallestaRecargaPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.armas.BallestaRecargaPacket::encode)
                .decoder(com.miscelanea.armas.BallestaRecargaPacket::new)
                .consumerMainThread(com.miscelanea.armas.BallestaRecargaPacket::handle)
                .add();
        // ---- Pistola
        CHANNEL.messageBuilder(com.miscelanea.armasfuego.pistola.GirarPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.armasfuego.pistola.GirarPacket::encode)
                .decoder(com.miscelanea.armasfuego.pistola.GirarPacket::new)
                .consumerMainThread(com.miscelanea.armasfuego.pistola.GirarPacket::handle)
                .add();
        // ---- Escopeta de presión
        CHANNEL.messageBuilder(com.miscelanea.armasfuego.escopeta.DispararPresionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.armasfuego.escopeta.DispararPresionPacket::encode)
                .decoder(com.miscelanea.armasfuego.escopeta.DispararPresionPacket::new)
                .consumerMainThread(com.miscelanea.armasfuego.escopeta.DispararPresionPacket::handle)
                .add();
        // ---- Rifle
        CHANNEL.messageBuilder(com.miscelanea.armasfuego.rifle.RifleAccionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.armasfuego.rifle.RifleAccionPacket::encode)
                .decoder(com.miscelanea.armasfuego.rifle.RifleAccionPacket::new)
                .consumerMainThread(com.miscelanea.armasfuego.rifle.RifleAccionPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.armasfuego.rifle.TirolinasPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.armasfuego.rifle.TirolinasPacket::encode)
                .decoder(com.miscelanea.armasfuego.rifle.TirolinasPacket::new)
                .consumerMainThread(com.miscelanea.armasfuego.rifle.TirolinasPacket::handle)
                .add();
        // ---- Gatling (al final: paquete nuevo)
        CHANNEL.messageBuilder(com.miscelanea.armasfuego.gatling.GatlingPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.armasfuego.gatling.GatlingPacket::encode)
                .decoder(com.miscelanea.armasfuego.gatling.GatlingPacket::new)
                .consumerMainThread(com.miscelanea.armasfuego.gatling.GatlingPacket::handle)
                .add();
        // ---- Derringer: sacarla de la manga (al final: paquete nuevo)
        CHANNEL.messageBuilder(com.miscelanea.armasfuego.oeste.MangaPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.armasfuego.oeste.MangaPacket::encode)
                .decoder(com.miscelanea.armasfuego.oeste.MangaPacket::new)
                .consumerMainThread(com.miscelanea.armasfuego.oeste.MangaPacket::handle)
                .add();
    }
}
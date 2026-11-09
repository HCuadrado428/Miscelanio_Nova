package com.miscelanea.arcade;

import com.miscelanea.ranking.Ranking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Récords de las recreativas y de la feria: ahora van al {@link Ranking} del servidor (la mejor marca de cada jugador, con
 * desempate por quién la hizo antes). Esta clase queda como entrada para los juegos y para leer las tablas antiguas.
 */
public class ArcadeRecords extends SavedData {
    private static final String NOMBRE = "miscelanea_arcade";
    private CompoundTag tablas = new CompoundTag();

    private static ArcadeRecords cargar(CompoundTag t) {
        ArcadeRecords r = new ArcadeRecords();
        r.tablas = t.getCompound("tablas");
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.put("tablas", tablas);
        return t;
    }

    /** Las tablas guardadas antes del ranking (o vacío), para pasarlas al ranking una vez. */
    public static CompoundTag viejas(MinecraftServer s) {
        ArcadeRecords r = s.overworld().getDataStorage().get(ArcadeRecords::cargar, NOMBRE);
        return r == null ? new CompoundTag() : r.tablas.copy();
    }

    /** Puntos de una partida de recreativa o de una caseta de la feria (id de {@link ArcadeJuegos}). */
    public static void registrar(ServerPlayer p, String juego, int puntos) {
        if (!ArcadeJuegos.valido(juego) || puntos <= 0 || puntos > 99_999_999) return;
        Ranking.mejor(p, juego, puntos);
        if (ArcadeJuegos.esArcade(juego)) com.miscelanea.logros.LogrosMiscelanea.dar(p, "arcade");
        com.miscelanea.logros.LogrosMiscelanea.dar(p, "puntos_" + juego);
    }
}

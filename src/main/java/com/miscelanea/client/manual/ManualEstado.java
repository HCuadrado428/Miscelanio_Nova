package com.miscelanea.client.manual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lo que recuerda el manual de cada jugador (en su ordenador: {@code config/miscelanea-manual.json}): la última página
 * que tenía abierta y sus marcadores. Se guardan "anclas" (capítulo/entrada y página dentro de ella), no números de
 * página, para que sigan valiendo aunque se añadan entradas nuevas.
 */
final class ManualEstado {
    private ManualEstado() {}

    static final int MAX_MARCADORES = 6;
    static String ultima;
    static final List<String> MARCADORES = new ArrayList<>();
    private static boolean leido;

    private static Path archivo() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("miscelanea-manual.json");
    }

    static void leer() {
        if (leido) return;
        leido = true;
        try {
            Path p = archivo();
            if (!Files.exists(p)) return;
            JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("ultima")) ultima = o.get("ultima").getAsString();
            if (o.has("marcadores"))
                for (JsonElement e : o.getAsJsonArray("marcadores"))
                    if (MARCADORES.size() < MAX_MARCADORES) MARCADORES.add(e.getAsString());
        } catch (Exception ignored) {
        }
    }

    static void guardar() {
        try {
            JsonObject o = new JsonObject();
            if (ultima != null) o.addProperty("ultima", ultima);
            JsonArray a = new JsonArray();
            for (String m : MARCADORES) a.add(m);
            o.add("marcadores", a);
            Path p = archivo();
            Files.createDirectories(p.getParent());
            Files.writeString(p, o.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}

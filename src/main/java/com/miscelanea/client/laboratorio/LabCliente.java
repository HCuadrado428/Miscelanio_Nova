package com.miscelanea.client.laboratorio;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.ListTag;

/** Puntos de entrada del cliente del laboratorio (el código común solo llega aquí con DistExecutor). */
public final class LabCliente {
    private LabCliente() {}

    public static void abrirCuaderno(ListTag fichas) {
        Minecraft.getInstance().setScreen(new CuadernoScreen(fichas));
    }
}

package com.miscelanea.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * El Espera es una dimensión nueva, y Minecraft trata cualquier mundo con dimensiones que no son las de siempre
 * como "experimental": al crearlo o al abrirlo saca un aviso de "configuración experimental" (y pregunta si hacer copia
 * de seguridad) cada vez. Aquí se salta ese aviso. Si en otra versión cambian los métodos, no pasa nada: simplemente
 * vuelve a salir el aviso (require = 0).
 */
@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsMixin {
    /** Al crear un mundo: como si se hubiera pulsado "seguir" en el aviso. */
    @ModifyVariable(method = "confirmWorldCreation", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static boolean miscelanea$sinAvisoAlCrear(boolean saltarAvisos) {
        return true;
    }

    /** Al abrir un mundo guardado: sin la pregunta de la copia de seguridad por ser experimental. */
    @ModifyVariable(method = "doLoadLevel", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private boolean miscelanea$sinAvisoAlAbrir(boolean preguntar) {
        return false;
    }
}

package com.miscelanea.logros;

import com.google.gson.JsonObject;
import com.miscelanea.Miscelanea;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * El criterio propio de los logros, {@code miscelanea:accion}: se cumple cuando el código del mod avisa de que el
 * jugador ha hecho esa acción ({@link LogrosMiscelanea#dar}). En el JSON: {@code {"trigger": "miscelanea:accion",
 * "conditions": {"accion": "foto"}}}.
 */
public class LogroTrigger extends SimpleCriterionTrigger<LogroTrigger.Instancia> {
    public static final ResourceLocation ID = new ResourceLocation(Miscelanea.MOD_ID, "accion");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Instancia createInstance(JsonObject json, ContextAwarePredicate jugador, DeserializationContext ctx) {
        return new Instancia(jugador, GsonHelper.getAsString(json, "accion"));
    }

    public void trigger(ServerPlayer p, String accion) {
        trigger(p, i -> i.accion.equals(accion));
    }

    public static class Instancia extends AbstractCriterionTriggerInstance {
        private final String accion;

        public Instancia(ContextAwarePredicate jugador, String accion) {
            super(ID, jugador);
            this.accion = accion;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx) {
            JsonObject o = super.serializeToJson(ctx);
            o.addProperty("accion", accion);
            return o;
        }
    }
}

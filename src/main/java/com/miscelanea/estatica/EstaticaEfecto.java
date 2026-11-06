package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * «Estática»: parte del cuerpo hecha de nieve de televisión. Por sí mismo solo cambia el movimiento (más rápido, menos
 * gravedad: saltos más largos); nunca suelta partículas (Estatica.tickJugador lo pone sin ellas); los poderes y el resto viven en {@link EstaticaEventos} y en el cliente.
 * Los modificadores se ponen al aplicar el efecto (no en el constructor: la gravedad de Forge se registra después que los
 * efectos) y leen la configuración en ese momento. La leche no lo quita salvo que se active en la configuración.
 */
public class EstaticaEfecto extends MobEffect {
    private static final UUID VELOCIDAD = UUID.fromString("5f1c2b7e-3d4a-4e8f-9a60-1e7a2c3b4d01");
    private static final UUID GRAVEDAD = UUID.fromString("5f1c2b7e-3d4a-4e8f-9a60-1e7a2c3b4d02");

    public EstaticaEfecto() {
        super(MobEffectCategory.NEUTRAL, EstaticaRegistro.COLOR);
    }

    @Override
    public void addAttributeModifiers(LivingEntity e, AttributeMap m, int amp) {
        super.addAttributeModifiers(e, m, amp);
        poner(m.getInstance(Attributes.MOVEMENT_SPEED), VELOCIDAD, "estatica_velocidad", ConfigServidor.estaticaVelocidad());
        poner(m.getInstance(ForgeMod.ENTITY_GRAVITY.get()), GRAVEDAD, "estatica_gravedad", ConfigServidor.estaticaGravedad());
    }

    @Override
    public void removeAttributeModifiers(LivingEntity e, AttributeMap m, int amp) {
        super.removeAttributeModifiers(e, m, amp);
        AttributeInstance v = m.getInstance(Attributes.MOVEMENT_SPEED);
        if (v != null) v.removeModifier(VELOCIDAD);
        AttributeInstance g = m.getInstance(ForgeMod.ENTITY_GRAVITY.get());
        if (g != null) g.removeModifier(GRAVEDAD);
    }

    private static void poner(AttributeInstance a, UUID id, String n, double v) {
        if (a == null) return;
        a.removeModifier(id);
        if (v != 0) a.addTransientModifier(new AttributeModifier(id, n, v, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        return ConfigServidor.estaticaLecheCura() ? super.getCurativeItems() : new ArrayList<>();
    }
}

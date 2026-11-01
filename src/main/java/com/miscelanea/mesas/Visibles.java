package com.miscelanea.mesas;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Mesas cargadas en el cliente (para apuntar, sonidos y mandos). */
public final class Visibles {
    public static final Set<BillarBlockEntity> BILLAR = Collections.newSetFromMap(new WeakHashMap<>());
    public static final Set<FutbolinBlockEntity> FUTBOLIN = Collections.newSetFromMap(new WeakHashMap<>());

    private Visibles() {}
}

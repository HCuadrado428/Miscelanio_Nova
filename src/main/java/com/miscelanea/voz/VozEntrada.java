package com.miscelanea.voz;

/** Una voz activa: id (ver {@link VozPorEfecto}) y amplificador del efecto que la provoca (0 = nivel I). */
public record VozEntrada(String id, int amp) {}

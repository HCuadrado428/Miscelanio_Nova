#version 150

// Borrachera: visión doble. La imagen se pinta tres veces (la de verdad y dos "fantasmas" desplazados que giran
// despacio), con un poco de desenfoque, los colores más cálidos y los bordes algo oscuros. 'Time' va de 0 a 1 cada
// segundo (PostChain): solo se usan vueltas enteras para que no salte.
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;
uniform float Intensidad;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

const float TAU = 6.2831853;

vec3 muestra(vec2 uv) {
    return texture(DiffuseSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

vec3 borroso(vec2 uv, float r) {
    vec2 d = oneTexel * r;
    vec3 c = muestra(uv) * 0.4;
    c += muestra(uv + vec2(d.x, 0.0)) * 0.15;
    c += muestra(uv - vec2(d.x, 0.0)) * 0.15;
    c += muestra(uv + vec2(0.0, d.y)) * 0.15;
    c += muestra(uv - vec2(0.0, d.y)) * 0.15;
    return c;
}

void main() {
    float k = Intensidad;
    vec2 c = texCoord - 0.5;
    float r = length(c);
    float t = Time * TAU;
    // los fantasmas se separan y se juntan despacio, girando
    float sep = (0.55 + 0.45 * sin(t)) * 0.028 * k;
    vec2 dir = vec2(cos(t * 0.5 + 1.0) * 0.6 + 0.8, sin(t * 0.5 + 1.0) * 0.4);
    vec2 d1 = dir * sep;
    vec2 d2 = -dir * sep * 0.7 + vec2(0.0, 0.004 * k * sin(t * 2.0));
    vec3 base = borroso(texCoord, 1.0 + 2.0 * k);
    vec3 f1 = borroso(texCoord + d1, 2.0 + 2.0 * k);
    vec3 f2 = borroso(texCoord + d2, 2.0 + 2.0 * k);
    vec3 col = base * (1.0 - 0.5 * k) + f1 * 0.3 * k + f2 * 0.2 * k;
    // más cálido y menos nítido en los bordes
    col *= vec3(1.0 + 0.10 * k, 1.0 + 0.02 * k, 1.0 - 0.08 * k);
    float v = smoothstep(0.45, 0.9, r);
    col = mix(col, col * 0.55, v * 0.6 * k);
    fragColor = vec4(col, 1.0);
}

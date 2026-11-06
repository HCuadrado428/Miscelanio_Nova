#version 150

// Toxina del miedo (versión con uniformes que cambia el juego en cada fotograma):
//  Intensidad  0..1  ondula, separa colores, desatura a verde enfermizo y oscurece los bordes.
//  Golpe       0..1  el susto: separación cromática radial fuerte, "puñetazo" de lente y rojo en los bordes.
//  Pesadilla   0..1  paleta de pesadilla: luminancia invertida en rojo y negro, con mucho contraste.
//  Estira      -1..1 el mundo se estira en vertical (y se estrecha) desde el centro.
//  Desenfoque  0..1  visión de túnel: los bordes se desenfocan (enfoque que se va al centro).
//  Reloj       segundos (tiempo real, sin vueltas cada segundo).
uniform sampler2D DiffuseSampler;
uniform float Intensidad;
uniform float Golpe;
uniform float Pesadilla;
uniform float Estira;
uniform float Desenfoque;
uniform float Reloj;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

const vec2 DIRS[8] = vec2[8](vec2(1.0, 0.0), vec2(0.707, 0.707), vec2(0.0, 1.0), vec2(-0.707, 0.707),
                             vec2(-1.0, 0.0), vec2(-0.707, -0.707), vec2(0.0, -1.0), vec2(0.707, -0.707));

vec2 dentro(vec2 uv) {
    return clamp(uv, vec2(0.001), vec2(0.999));
}

void main() {
    float k = Intensidad;
    float t = Reloj;
    vec2 c = texCoord - 0.5;
    float r = length(c);

    // geometría: estirón vertical, "puñetazo" de lente (barril) con el golpe y ondulación lenta
    vec2 cc = c;
    cc.y *= 1.0 - 0.16 * Estira * (1.0 - r);
    cc.x *= 1.0 + 0.05 * Estira;
    cc *= 1.0 - 0.07 * Golpe * (1.0 - r * 1.2);
    vec2 onda = vec2(sin(texCoord.y * 13.0 + t * 1.7), cos(texCoord.x * 11.0 + t * 2.3)) * 0.003 * k;
    onda += c * sin(t * 0.9) * 0.014 * k * r;
    vec2 uv = dentro(0.5 + cc + onda);

    // separación de colores (radial), mucho más fuerte con el golpe
    vec2 sep = c * (0.008 * k * (0.35 + r) + 0.04 * Golpe * (0.25 + r));
    vec3 col;
    col.r = texture(DiffuseSampler, dentro(uv + sep)).r;
    col.g = texture(DiffuseSampler, uv).g;
    col.b = texture(DiffuseSampler, dentro(uv - sep)).b;

    // visión de túnel: desenfoque que crece hacia los bordes
    float rad = Desenfoque * smoothstep(0.12, 0.65, r) * 0.014;
    if (rad > 0.0002) {
        vec3 acc = col;
        for (int i = 0; i < 8; i++) {
            acc += texture(DiffuseSampler, dentro(uv + DIRS[i] * rad)).rgb;
            acc += texture(DiffuseSampler, dentro(uv + DIRS[i] * rad * 0.5)).rgb;
        }
        col = acc / 17.0;
    }

    // eco (imagen fantasma que se separa despacio)
    vec2 desp = vec2(sin(t * 0.7), cos(t * 0.53)) * 0.011 * k;
    vec3 eco = texture(DiffuseSampler, dentro(uv + desp)).rgb;
    col = mix(col, eco, 0.2 * k);

    // verde enfermizo
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 enfermo = vec3(l) * vec3(0.80, 1.06, 0.74);
    col = mix(col, enfermo, 0.6 * k);

    // golpe: los bordes se encienden de rojo y el centro se quema un poco
    float borde = smoothstep(0.15, 0.75, r);
    col = mix(col, col * vec3(1.35, 0.3, 0.25) + vec3(0.10, 0.0, 0.0), Golpe * (0.35 + 0.5 * borde));

    // pesadilla: el negativo en rojo y negro, contraste a tope
    float lp = dot(col, vec3(0.299, 0.587, 0.114));
    float inv = 1.0 - lp;
    inv = smoothstep(0.25, 0.95, inv);
    vec3 pes = vec3(inv * 1.05, inv * inv * 0.16, inv * inv * 0.12);
    col = mix(col, pes, clamp(Pesadilla, 0.0, 1.0));

    // viñeta (más cerrada con el túnel y el golpe)
    float v = smoothstep(0.32 - 0.12 * Desenfoque, 0.82, r);
    col = mix(col, col * vec3(0.22, 0.48, 0.18), v * 0.75 * k);
    col *= 1.0 - v * 0.5 * max(Golpe, Desenfoque * 0.6);
    fragColor = vec4(col, 1.0);
}

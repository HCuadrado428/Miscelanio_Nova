#version 150

// Toxina del miedo: la imagen ondula y late, los colores se separan, se desatura hacia un verde enfermizo y los
// bordes se oscurecen. 'Time' va de 0 a 1 cada segundo (PostChain): solo se usan vueltas enteras, así no salta.
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;
uniform float Intensidad;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

const float TAU = 6.2831853;

void main() {
    float k = Intensidad;
    vec2 c = texCoord - 0.5;
    float r = length(c);
    float t = Time * TAU;
    vec2 onda = vec2(sin(texCoord.y * 13.0 + t), cos(texCoord.x * 11.0 + t * 2.0)) * 0.0035 * k;
    onda += c * sin(t) * 0.018 * k * r;
    vec2 uv = clamp(texCoord + onda, vec2(0.001), vec2(0.999));
    vec2 sep = c * 0.009 * k * (0.35 + r);
    vec3 col;
    col.r = texture(DiffuseSampler, clamp(uv + sep, vec2(0.001), vec2(0.999))).r;
    col.g = texture(DiffuseSampler, uv).g;
    col.b = texture(DiffuseSampler, clamp(uv - sep, vec2(0.001), vec2(0.999))).b;
    vec2 desp = vec2(sin(t) * 0.012, cos(t) * 0.007) * k;
    vec3 eco = texture(DiffuseSampler, clamp(uv + desp, vec2(0.001), vec2(0.999))).rgb;
    col = mix(col, eco, 0.22 * k);
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 enfermo = vec3(l) * vec3(0.80, 1.06, 0.74);
    col = mix(col, enfermo, 0.62 * k);
    float v = smoothstep(0.32, 0.82, r);
    col = mix(col, col * vec3(0.22, 0.48, 0.18), v * 0.75 * k);
    fragColor = vec4(col, 1.0);
}

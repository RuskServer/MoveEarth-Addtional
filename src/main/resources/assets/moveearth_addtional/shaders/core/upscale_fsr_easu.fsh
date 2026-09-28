#version 330
#define A_GPU 1
#define A_GLSL 1
#define FSR_EASU_F 1
#moj_import <moveearth_addtional:ffx_fsr1.glsl>

uniform sampler2D InputTex;
// FsrEasuCon() constants computed on the CPU; the float bits are passed as-is and reinterpreted below.
uniform vec4 Con0;
uniform vec4 Con1;
uniform vec4 Con2;
uniform vec4 Con3;
out vec4 fragColor;

// EASU asks for red, green and blue gathers of the same 2x2 footprint in that order. GLSL 3.30 has no
// textureGather, so the footprint is fetched once on the red call with textureGather's texel order
// (x: i0,j1  y: i1,j1  z: i1,j0  w: i0,j0) and green and blue are returned from it.
vec4 gatheredG;
vec4 gatheredB;

AF4 FsrEasuRF(AF2 p) {
    ivec2 size = textureSize(InputTex, 0);
    ivec2 base = ivec2(floor(p * vec2(size) - 0.5));
    ivec2 last = size - 1;
    vec3 x = texelFetch(InputTex, clamp(base + ivec2(0, 1), ivec2(0), last), 0).rgb;
    vec3 y = texelFetch(InputTex, clamp(base + ivec2(1, 1), ivec2(0), last), 0).rgb;
    vec3 z = texelFetch(InputTex, clamp(base + ivec2(1, 0), ivec2(0), last), 0).rgb;
    vec3 w = texelFetch(InputTex, clamp(base, ivec2(0), last), 0).rgb;
    gatheredG = vec4(x.g, y.g, z.g, w.g);
    gatheredB = vec4(x.b, y.b, z.b, w.b);
    return vec4(x.r, y.r, z.r, w.r);
}
AF4 FsrEasuGF(AF2 p) { return gatheredG; }
AF4 FsrEasuBF(AF2 p) { return gatheredB; }

void main() {
    AF3 color;
    FsrEasuF(color, AU2(gl_FragCoord.xy), floatBitsToUint(Con0), floatBitsToUint(Con1),
            floatBitsToUint(Con2), floatBitsToUint(Con3));
    fragColor = vec4(color, 1.0);
}

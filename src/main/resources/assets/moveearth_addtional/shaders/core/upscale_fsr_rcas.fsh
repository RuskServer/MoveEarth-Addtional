#version 330
#define A_GPU 1
#define A_GLSL 1
#define FSR_RCAS_F 1
#moj_import <moveearth_addtional:ffx_fsr1.glsl>

uniform sampler2D InputTex;
// FsrRcasCon(): x = exp2(-sharpness stops); float bits reinterpreted below.
uniform vec4 Con;
out vec4 fragColor;

AF4 FsrRcasLoadF(ASU2 p) {
    return texelFetch(InputTex, clamp(p, ivec2(0), textureSize(InputTex, 0) - 1), 0);
}
void FsrRcasInputF(inout AF1 r, inout AF1 g, inout AF1 b) { }

void main() {
    AF1 r, g, b;
    FsrRcasF(r, g, b, AU2(gl_FragCoord.xy), floatBitsToUint(Con));
    fragColor = vec4(r, g, b, 1.0);
}

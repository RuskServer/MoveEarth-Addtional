#version 150
#define SMAA_GLSL_3
#define SMAA_PRESET_HIGH
#define SMAA_INCLUDE_VS 0
uniform vec4 RtMetrics;
#define SMAA_RT_METRICS RtMetrics
#moj_import <moveearth_addtional:smaa.glsl>

uniform sampler2D EdgesTex;
uniform sampler2D AreaTex;
uniform sampler2D SearchTex;
in vec2 texcoord;
in vec2 pixcoord;
in vec4 offset[3];
out vec4 fragColor;

void main() {
    fragColor = SMAABlendingWeightCalculationPS(texcoord, pixcoord, offset, EdgesTex, AreaTex, SearchTex, vec4(0.0));
}

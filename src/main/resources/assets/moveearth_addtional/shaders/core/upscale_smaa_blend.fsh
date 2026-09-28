#version 150
#define SMAA_GLSL_3
#define SMAA_PRESET_HIGH
#define SMAA_INCLUDE_VS 0
uniform vec4 RtMetrics;
#define SMAA_RT_METRICS RtMetrics
#moj_import <moveearth_addtional:smaa.glsl>

uniform sampler2D ColorTex;
uniform sampler2D BlendTex;
in vec2 texcoord;
in vec4 offset;
out vec4 fragColor;

void main() {
    fragColor = SMAANeighborhoodBlendingPS(texcoord, offset, ColorTex, BlendTex);
}

#version 150
#define SMAA_GLSL_3
#define SMAA_PRESET_HIGH
#define SMAA_INCLUDE_VS 0
uniform vec4 RtMetrics;
#define SMAA_RT_METRICS RtMetrics
#moj_import <moveearth_addtional:smaa.glsl>

uniform sampler2D ColorTex;
in vec2 texcoord;
in vec4 offset[3];
out vec4 fragColor;

void main() {
    // Discards pixels without edges; the target is cleared to zero first.
    fragColor = vec4(SMAALumaEdgeDetectionPS(texcoord, offset, ColorTex), 0.0, 0.0);
}

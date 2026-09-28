#version 150
#define SMAA_GLSL_3
#define SMAA_PRESET_HIGH
#define SMAA_INCLUDE_PS 0
uniform vec4 RtMetrics;
#define SMAA_RT_METRICS RtMetrics
#moj_import <moveearth_addtional:smaa.glsl>

in vec3 Position;
out vec2 texcoord;
out vec4 offset;

void main() {
    texcoord = Position.xy * 0.5 + 0.5;
    SMAANeighborhoodBlendingVS(texcoord, offset);
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}

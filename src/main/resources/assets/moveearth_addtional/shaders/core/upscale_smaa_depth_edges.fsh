#version 330
// SMAA edge detection from world depth instead of luma. Luma detection treated the pixel-art detail
// inside every block texture as edges, so the expensive weight pass ran almost everywhere and blurred
// textures; only geometry silhouettes and steps are marked here. Output matches SMAA's edge texture:
// .r = edge with the left neighbour, .g = edge with the neighbour at texcoord.y - 1 (SMAA's "top").
uniform sampler2D DepthTex;
// (m22, m32) of the world projection: view distance = m32 / (ndc_z + m22).
uniform vec2 DepthParams;
// Minimum jump relative to the nearer distance.
uniform float Threshold;
out vec4 fragColor;

float distanceAt(ivec2 p) {
    float depth = texelFetch(DepthTex, clamp(p, ivec2(0), textureSize(DepthTex, 0) - 1), 0).r;
    return DepthParams.y / (depth * 2.0 - 1.0 + DepthParams.x);
}

// c: this pixel, n: the neighbour across the edge, beyond: the pixel past n, opposite: c's other side.
// A plane seen at a grazing angle changes distance smoothly, so a real edge must also jump by more than
// twice the smaller neighbouring step; the smaller one keeps single-pixel posts and bars detectable.
bool depthEdge(float c, float n, float beyond, float opposite) {
    float jump = abs(c - n);
    return jump > Threshold * min(c, n) && jump > 2.0 * min(abs(n - beyond), abs(opposite - c));
}

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    float c = distanceAt(p);
    vec2 edges = vec2(
            depthEdge(c, distanceAt(p + ivec2(-1, 0)), distanceAt(p + ivec2(-2, 0)), distanceAt(p + ivec2(1, 0))) ? 1.0 : 0.0,
            depthEdge(c, distanceAt(p + ivec2(0, -1)), distanceAt(p + ivec2(0, -2)), distanceAt(p + ivec2(0, 1))) ? 1.0 : 0.0);
    if (dot(edges, vec2(1.0)) == 0.0) discard;
    fragColor = vec4(edges, 0.0, 0.0);
}

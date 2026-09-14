#version 150

uniform vec2 ScreenSize;
uniform float MaskAlpha;
uniform float FogStrength;
uniform float PanicStrength;
uniform float Time;

out vec4 fragColor;

float lensDistance(vec2 uv, vec2 center, float aspect) {
    vec2 delta = uv - center;
    delta.x *= aspect;
    return length(delta / vec2(0.59, 0.49));
}

void main() {
    vec2 size = max(ScreenSize, vec2(1.0));
    vec2 uv = gl_FragCoord.xy / size;
    float aspect = size.x / size.y;
    float distanceToLens = min(
        lensDistance(uv, vec2(0.31, 0.50), aspect),
        lensDistance(uv, vec2(0.69, 0.50), aspect)
    );

    float outside = smoothstep(0.78, 1.04, distanceToLens);
    float rim = smoothstep(0.67, 0.82, distanceToLens)
              * (1.0 - smoothstep(0.91, 1.06, distanceToLens));
    float lensInterior = 1.0 - smoothstep(0.92, 1.02, distanceToLens);

    float broadMist = 0.5 + 0.5 * sin(uv.x * 15.0 + Time * 0.23)
                              * sin(uv.y * 12.0 - Time * 0.17);
    float condensation = FogStrength * lensInterior
                       * smoothstep(0.20, 0.90, broadMist) * 0.24;

    float heartbeat = 0.68 + 0.32 * sin(Time * 6.3);
    float panicEdge = PanicStrength * heartbeat
                    * smoothstep(0.30, 1.02, distanceToLens) * 0.38;

    float alpha = MaskAlpha * clamp(outside * 0.90 + rim * 0.32
                                  + condensation + panicEdge, 0.0, 0.96);
    vec3 color = vec3(0.012, 0.016, 0.020);
    color = mix(color, vec3(0.62, 0.68, 0.70), condensation * 1.8);
    color = mix(color, vec3(0.72, 0.025, 0.035), clamp(panicEdge * 2.0, 0.0, 0.72));
    fragColor = vec4(color, alpha);
}

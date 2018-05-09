package rndr.studio.demo.shading


val shadowOrthoFunction = """
float shadowOrtho(sampler2D lightMap,
            vec3 worldFragmentPosition,
            vec3 worldFragmentNormal,
            mat4 lightProjection,
            mat4 lightView) {

    vec4 lightClip = lightProjection * lightView * vec4(worldFragmentPosition, 1.0);
    vec3 lightPersp = (lightClip.xyz / lightClip.w) * 0.5 + 0.5;
    vec4 lightRef  = lightView * vec4(worldFragmentPosition, 1.0);

    vec4 positionOfLight = inverse(lightView)* vec4(0.0,0.0,0.0,1.0);
    vec3 d = normalize(positionOfLight.xyz);

    float dd = max(0.0, dot(d, normalize(v_worldNormal)));
    float bias = max(0.5* (1.0 - dd), 0.1);

    vec2 step = 1.0 / textureSize(lightMap, 0 );


    vec2 c = abs(lightPersp.xy - vec2(0.5));
    float f = smoothstep(0.5, 0.48, c.x) * smoothstep(0.5, 0.48, c.y)  ;

    float sum = 0;
    for (int j = -2; j <=2 ;++j) {
        for (int i = -2; i <=2; ++i) {
            vec3 lightPos = texture(lightMap,  lightPersp.xy + vec2(i,j)*step).rgb;
            if (lightPos.z < 0 && lightPos.z-bias > lightRef.z) {
                sum += 1.0;
            }
        }
    }
    return 1.0 - (sum/25.0) * f;
}

"""
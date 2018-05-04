#version 330

out vec4 o_output;
in vec2 v_texCoord0;
uniform sampler2D tex0;
uniform sampler2D tex1;
uniform sampler2D tex2;

uniform float gain;
uniform vec4 bias;
uniform float time;

float nrand(vec2 n) {
	return fract(sin(dot(n.xy, vec2(12.9898, 78.233))) * 43758.5453);
}


void main()
{

    vec2 delta =v_texCoord0-vec2(0.5);
    float phi = atan(delta.y, delta.x)+time*0.1;
    float n = nrand(vec2(phi-0.4, phi-0.4));

    float w = n*0.5 + cos(phi*512.0)*0.125+0.125+ cos(phi*256.0+ time*0.032143)*0.325+0.325;;

    vec4 dirt = texture(tex2, v_texCoord0)*0.2 + vec4(0.05) ;
	o_output = texture(tex0, v_texCoord0) +  min (vec4(0.1),  max(vec4(0.0), texture(tex1, v_texCoord0)*0.1-bias)*w*dirt);
	o_output.a = 1.0;
}
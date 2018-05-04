#version 330

out vec4 o_output;
in vec2 v_texCoord0;
uniform sampler2D tex0;

void main() {
	o_output = texture(tex0, v_texCoord0);
	o_output.a = floor(o_output.a*20.0)/20.0;
}
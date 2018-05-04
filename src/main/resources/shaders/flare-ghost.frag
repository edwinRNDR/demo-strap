#version 330

uniform sampler2D tex0;

uniform int ghosts; // number of ghost samples
uniform float dispersal; // dispersion factor
uniform float haloRadius;

in vec2 v_texCoord0;

out vec4 o_output;

 vec3 textureDistorted(
      in sampler2D tex,
      in vec2 texcoord,
      in vec2 direction, // direction of distortion
      in vec3 distortion // per-channel distortion factor
   ) {
      return vec3(
         texture(tex, texcoord + direction * distortion.r).r,
         texture(tex, texcoord + direction * distortion.g).g,
         texture(tex, texcoord + direction * distortion.b).b
      );
   }

void main() {
  //vec2 texcoord = -v_texCoord0 + vec2(1.0);
  vec2 texcoord = v_texCoord0;
  vec2 texelSize = 1.0 / vec2(textureSize(tex0, 0));
   // ghost vector to image centre:
float distortionGain = 5.0;

      vec3 distortion = vec3(-texelSize.x * distortionGain, 0.0, texelSize.x * distortionGain);

  vec2 ghostVec = (vec2(0.5) - texcoord) * 0.15;

   // sample ghosts:
  vec4 result = vec4(0.0);

 for (int i = 0; i < 5; ++i) {
      vec2 offset = fract(texcoord + ghostVec * float(i));

      float weight = length(vec2(0.5) - offset) / length(vec2(0.5));
      weight = smoothstep(2.0, 0.0, weight) / (i+1);
      vec2 direction = normalize(ghostVec);

      result.rgb += textureDistorted(tex0, offset, direction, distortion) * weight;
   }


  o_output =result;
  o_output.a = 1.0;
  //o_output = vec4(weight);
}
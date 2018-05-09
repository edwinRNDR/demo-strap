#version 330

in vec2 v_texCoord0;
uniform sampler2D tex0;
uniform sampler2D velocity;
uniform sampler2D lap0;
uniform sampler2D lap1;
uniform sampler2D real;
uniform float threshold;
uniform float time;

out vec4 o_color;
#define HASHSCALE 443.8975
vec2 hash22(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * HASHSCALE);
    p3 += dot(p3, p3.yzx+19.19);
    return fract(vec2((p3.x + p3.y)*p3.z, (p3.x+p3.z)*p3.y));
}

vec3 rgbToYuv(vec3 rgb) {
    vec3 yuv;
    yuv.x =  0.299   *  rgb.r  + 0.587   * rgb.g + 0.114 * rgb.b;
    yuv.y = -0.14713 *  rgb.r   -0.28886 * rgb.g + 0.436 * rgb.b;
    yuv.z =  0.615   *  rgb.r   -0.51499 * rgb.g -0.10001 * rgb.b;
    return yuv;
}

vec3 yuvToRgb(vec3 yuv) {
    vec3 rgb;
    rgb.x = 1.0 * yuv.x + 1.13983 * yuv.z;
    rgb.y = 1.0 * yuv.x  -0.39465 * yuv.y -0.58060 * yuv.z;
    rgb.z = 1.0 * yuv.x + 2.0321 * yuv.y;
    return rgb;
}

void main() {

    vec2 s = textureSize(tex0, 0).xy;
    s = vec2(1.0/s.x, 1.0/s.y);
    vec2 q = v_texCoord0; //floor(v_texCoord0*32.0)/32.0;
    q.x = floor(q.x*80)/80;
    q.y = floor(q.y*45)/45;

    vec2 co = hash22(q*1.0+time)-0.5;
    vec2 realNoise = hash22(q*1.0+time*0.454);

    float addFactor0 = (hash22(q*1.0+time*0.1).x-0.5)*2.0;
    float addFactor1 = (hash22(q*1.0+time*0.1).y-0.5)*2.0;

    //vec3 cm1 =    vec3(hash22(q*0.656+time*0.1)*0.05+0.95,hash22(q*0.643-time*0.1).x*0.05+0.95);


    vec3 add0 = texture(lap0, v_texCoord0).xyz;
    vec3 add1 = texture(lap1, v_texCoord0).xyz;

    vec2 blurDirection = (   texture(velocity, q).xy)*10.0; //vec2(2.0,cos(v_texCoord0.x*10.0));//texture(tex1, v_texCoord0).xy * 4.0;


    blurDirection = floor(blurDirection*4.0)/4.0;



    vec3 c0 = rgbToYuv(texture(tex0, v_texCoord0 - blurDirection * s).rgb);
//   vec3 c1 = rgbToYuv(texture(tex0, v_texCoord0 - blurDirection * s + vec2(co.x,0.0) * s).rgb);
 //  vec3 c2 = rgbToYuv(texture(tex0, v_texCoord0 - blurDirection * s + vec2(0.0, co.y)*s).rgb);

    vec4 c = vec4(0.0, 0.0, 0.0, 1.0);
    c.r = c0.r; //floor(c.r*64.0)/64.0;
    c.g = c0.g + co.x*0.001;//floor(c0.g*co.x)/co.x;
    c.b = c0.b + co.y*0.001;//floor(c0.b*co.y)/co.y;

    c.rgb = yuvToRgb(c.rgb);
    c.rgb += add0 * addFactor0 + add1 * addFactor1;

    vec4 r = texture(real, v_texCoord0);
    float ri = max(0.0, min(1.0, dot(vec3(0.33), r.rgb)));

    //vec3 blurDirection2 = (   texture(velocity, v_texCoord0).xyz); //vec2(2.0,cos(v_texCoord0.x*10.0));//texture(tex1, v_texCoord0).xy * 4.0;

    float f =  min(1.0, max(0.0, 1.0-threshold)); // smoothstep(0.0, 0.4,  v_texCoord0.y -  threshold);

    c.rgb = (1.0-f) * c.rgb + f * r.rgb;
    o_color = c;
    o_color.rgb -= (add0-add1)*10.0 * (1.0-f);
     //o_color.rgb += vec3(realNoise.x)*0.1;

}
precision highp float;
precision highp int;
layout(rgba16f, binding = 0) uniform highp readonly image2D inTexture;
layout(rgba16f, binding = 1) uniform highp writeonly image2D outTexture;
#define LAYOUT //
#define OFFSET 0,0
#define OUTSET 0,0
#define FIRST 0
#define RESCALING 1
#define TILE 3
#define NOISEO 0.0
#define NOISES 0.0
#import rescale
#import thresholding
#import gaussian
#import mix4
float wavepdf(ivec2 xy){
    xy-=ivec2((TILE-1)/2,(TILE-1)/2);
    return pdf(vec2(xy));
}

LAYOUT
void main() {
    ivec2 xyIn = ivec2(gl_GlobalInvocationID.xy);
    ivec2 xyOut = xyIn*RESCALING*TILE + ivec2(OFFSET);//= rescaleUpi(xyIn,ivec4(OFFSET,OUTSET),RESCALING*TILE);
    xyIn = xyIn*RESCALING*TILE + ivec2(OFFSET);//= rescaleUpi(xyIn,ivec4(OFFSET,OUTSET),RESCALING*TILE);
    if(xyOut.x+TILE*RESCALING >= ivec2(OUTSET).x) return;
    if(xyOut.y+TILE*RESCALING >= ivec2(OUTSET).y) return;

    vec4 texColor[TILE*TILE];
    for(int i =0; i<TILE*TILE;i++){
        texColor[i] = imageLoad(inTexture,xyIn+ivec2(i%TILE,i/TILE)*RESCALING);
    }


    vec4 brs[4];
    brs[0] = texColor[0];
    brs[1] = imageLoad(inTexture,xyIn+ivec2(1,0)*RESCALING*TILE);
    brs[2] = imageLoad(inTexture,xyIn+ivec2(0,1)*RESCALING*TILE);
    brs[3] = imageLoad(inTexture,xyIn+ivec2(1,1)*RESCALING*TILE);


    vec4 br = brs[0];
    vec4 sum = vec4(0.0);
    for(int i =1; i<TILE*TILE;i++){
        sum+=texColor[i];
    }
    vec4 in0 = br*float(TILE*TILE) - (br*float(TILE*TILE - 1) - sum);
    texColor[0] = br-in0;

    for(int i =0; i<TILE*TILE;i++){
        float distr = wavepdf(ivec2(i%TILE,i/TILE));
        vec2 interp = vec2(i%TILE,i/TILE)/float(TILE);
        br = mix4(brs,interp);
        vec4 brdiff = br-brs[0];
        texColor[i] = softThresholding2(texColor[i]+brdiff,sqrt(1.0*NOISES + NOISEO));

        imageStore(outTexture, xyOut+ivec2(i%TILE,i/TILE)*RESCALING, (br-texColor[i]));
    }
}
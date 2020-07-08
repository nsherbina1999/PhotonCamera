package com.eszdman.photoncamera.OpenGL.Nodes;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.Render.Parameters;

import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class Initial extends Node {
    public Initial(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Run(BasePipeline pipeline) {
        startT();
        Node Previous = super.previousNode;
        GLProg glProg = GLInterface.i.glprogram;
        Parameters params = GLInterface.i.parameters;
        GLTexture GainMapTex = new GLTexture(params.mapsize, new GLFormat(GLFormat.DataType.FLOAT_16,4),FloatBuffer.wrap(params.gainmap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.setTexture("Fullbuffer",super.previousNode.WorkingTexture);
        glProg.setTexture("GainMap",GainMapTex);
        glProg.servar("RawSizeX",params.rawSize.x);
        glProg.servar("RawSizeY",params.rawSize.y);
        for(int i =0; i<4;i++){
            params.blacklevel[i]/=params.whitelevel;
        }
        glProg.servar("blackLevel",params.blacklevel);
        super.WorkingTexture = new GLTexture(params.rawSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),null);
        endT();
    }
}
package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.pro.SpecificSettingSensor;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;

public class CaptureSharpening extends Node {
    public CaptureSharpening() {
        super(0, "CaptureSharpening");
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        Log.d(Name,"CaptureSharpening specific:"+basePipeline.mParameters.sensorSpecifics);
        if(basePipeline.mParameters.sensorSpecifics == null){
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }
        float size = basePipeline.mParameters.sensorSpecifics.captureSharpeningS;
        float strength = basePipeline.mParameters.sensorSpecifics.captureSharpeningIntense;
        glProg.setDefine("SHARPSTR",strength);
        glProg.setDefine("SHARPSIZEKER",size);
        glProg.useProgram(R.raw.capturesharpening);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);

        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);

        glProg.closed = true;
    }
}

package com.eszdman.photoncamera;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.AlignMTB;
import org.opencv.photo.MergeMertens;
import org.opencv.photo.Photo;
import org.opencv.tracking.Tracking;
import org.opencv.video.Video;
import org.opencv.ximgproc.Ximgproc;
import org.opencv.xphoto.Xphoto;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.opencv.calib3d.Calib3d.RANSAC;
import static org.opencv.calib3d.Calib3d.findHomography;
import static org.opencv.features2d.Features2d.drawMatches;

public class ImageProcessing {
    ArrayList<Image> curimgs;
    Boolean israw;
    Boolean isyuv;
    String path;
    ImageProcessing(ArrayList<Image> images) {
        curimgs = images;
    }
    public Mat convertYuv420888ToMat(Image image, boolean isGreyOnly) {
        Image.Plane yPlane = image.getPlanes()[0];
        int width = yPlane.getRowStride();
        int ySize = yPlane.getBuffer().remaining();
        int height = image.getHeight();
        int error = yPlane.getRowStride()-image.getWidth(); //BufferFix
        Log.d("ImageProcessing load_rawsensor()","height:"+height);
        if (isGreyOnly) {
            byte[] data = new byte[ySize];
            yPlane.getBuffer().get(data, 0, ySize);
            Mat greyMat = new Mat(height, width, CvType.CV_8UC1);
            greyMat.put(0, 0, data);
            Imgproc.cvtColor(greyMat,greyMat,Imgproc.COLOR_GRAY2BGR);
            return greyMat;
        }
        Image.Plane uPlane = image.getPlanes()[1];
        Image.Plane vPlane = image.getPlanes()[2];
        // be aware that this size does not include the padding at the end, if there is any
        // (e.g. if pixel stride is 2 the size is ySize / 2 - 1)
        int uSize = uPlane.getBuffer().remaining();
        int vSize = vPlane.getBuffer().remaining();
        byte[] data = new byte[ySize + (ySize/2)];
        //ArrayList<Mat> yuv = new ArrayList<>();
        yPlane.getBuffer().get(data, 0, ySize);
        ByteBuffer ub = uPlane.getBuffer();
        ByteBuffer vb = vPlane.getBuffer();
        /*yuv.add(new Mat(yh,width,CvType.CV_8UC1,yPlane.getBuffer()));
        yuv.add(new Mat(yh,width,CvType.CV_8UC1,yPlane.getBuffer()));
        yuv.add(new Mat(yh,width,CvType.CV_8UC1,yPlane.getBuffer()));*/

        int uvPixelStride = uPlane.getPixelStride(); //stride guaranteed to be the same for u and v planes
        if (uvPixelStride == 1) {
            uPlane.getBuffer().get(data, ySize+(error), uSize-error);
            vPlane.getBuffer().get(data, ySize +uSize+(error), vSize-error);
            Mat yuvMat = new Mat(height + (height / 2), image.getWidth(), CvType.CV_8UC1);
            yuvMat.put(0, 0, data);
            Mat rgbMat = new Mat(height, width, CvType.CV_8UC3);
            Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2BGR_NV21, 3);
            yuvMat.release();
            return rgbMat;
        }
        // if pixel stride is 2 there is padding between each pixel
        // converting it to NV21 by filling the gaps of the v plane with the u values
        vb.get(data, ySize+(error), vSize-error);
        for (int i = 0; i < uSize-error; i += 2) {
            data[ySize + i + 1 + error] = ub.get(i);
        }

        Mat yuvMat = new Mat(height + (height / 2), width, CvType.CV_8UC1);
        yuvMat.put(0, 0, data);
        Mat rgbMat = new Mat(height, width, CvType.CV_8UC3);
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2BGR_NV21, 3);
        rgbMat = rgbMat.colRange(0,width-error);
        yuvMat.release();
        return rgbMat;
    }
    Mat load_rawsensor(Image image){
        Image.Plane plane = image.getPlanes()[0];
        Mat mat = new Mat();
        if(israw) mat = new Mat(image.getHeight(),image.getWidth(),CvType.CV_16UC1,plane.getBuffer());
        else {
            if(!isyuv) mat =  Imgcodecs.imdecode(new Mat(1,plane.getBuffer().remaining(), CvType.CV_8U,plane.getBuffer()),Imgcodecs.IMREAD_UNCHANGED);
        }
        if(isyuv){
            Log.d("ImageProcessing load_rawsensor()","image planes:"+image.getPlanes().length);
            Log.d("ImageProcessing load_rawsensor()","image stride:"+image.getPlanes()[1].getPixelStride());
            Log.d("ImageProcessing load_rawsensor()","row stride:"+image.getPlanes()[1].getRowStride());
            mat = convertYuv420888ToMat(image,false);
            //Imgproc.cvtColor(mat,mat,Imgproc.COLOR_YUV2RGB_NV21,3);
        }
        return mat;
    }
    Mat[][] EqualizeImages(){
        Mat[][] out = new Mat[2][curimgs.size()];
        Mat lut = new Mat(1,256, CvType.CV_8U);
        for(int i =0; i<curimgs.size();i++){
            out[0][i] = new Mat();
            out[1][i] = new Mat();
            if(israw){
                out[0][i]=load_rawsensor(curimgs.get(i));
                out[0][i].convertTo(out[1][i], CvType.CV_8UC1);
            }
            else {
                out[0][i] = load_rawsensor(curimgs.get(i));
                Imgproc.cvtColor(out[0][i],out[1][i],Imgproc.COLOR_BGR2GRAY);
            }
            processingstep();
        }
        return out;
    }
    ORB orb = ORB.create();
    DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
    Mat findFrameHomography(Mat need, Mat from){
        Mat descriptors1=new Mat(), descriptors2=new Mat();
        MatOfKeyPoint keyPoints1 = new MatOfKeyPoint();
        MatOfKeyPoint keyPoints2 = new MatOfKeyPoint();
        orb.detectAndCompute(need,new Mat(),keyPoints1,descriptors1);
        orb.detectAndCompute(from,new Mat(),keyPoints2,descriptors2);
        MatOfDMatch matches = new MatOfDMatch();
        //DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        matcher.match(descriptors1, descriptors2, matches, new Mat());
        MatOfPoint2f points1=new MatOfPoint2f(), points2=new MatOfPoint2f();
        DMatch[]arr =matches.toArray();
        List<KeyPoint> keypoints1 = keyPoints1.toList();
        List<KeyPoint> keypoints2 = keyPoints2.toList();
        //Mat imMatches = new Mat();
        //drawMatches(need, keyPoints1, from, keyPoints2, matches, imMatches);
        ArrayList<Point> keypoints1f = new ArrayList<Point>();
        ArrayList<Point> keypoints2f = new ArrayList<Point>();
        for( int i = 0; i < arr.length; i++ )
        {
            Point on1 = keypoints1.get(arr[i].queryIdx).pt;
            Point on2 = keypoints2.get(arr[i].trainIdx).pt;
            /*on1.x/=0.75;
            on1.y/=0.75;
            on2.x/=0.75;
            on2.y/=0.75;*/
            if(arr[i].distance < 50) {
                keypoints1f.add(on1);
                keypoints2f.add(on2);
            }
        }
        points1.fromArray(keypoints1f.toArray(new Point[keypoints1f.size()]));
        points2.fromArray(keypoints2f.toArray(new Point[keypoints2f.size()]));
        Mat h = null;
        if(!points1.empty() && !points2.empty())h = findHomography(points2,points1,RANSAC);
        keyPoints1.release();
        keyPoints2.release();

        return h;
    }
    void processingstep(){
        int progress =(Camera2Api.loadingcycle.getProgress()+1)%(Camera2Api.loadingcycle.getMax()+1);
        progress = Math.max(1,progress);
        Camera2Api.loadingcycle.setProgress(progress);
    }
    Mat[][] getTiles(Mat[] input, int tilesize){
        Mat[][] output = new Mat[input.length][];
        ArrayList<Mat> out = new ArrayList<>();
        int width = input[0].width();
        int height = input[0].height();
        int oversize = 0;
        for(int i =0; i<input.length;i++){
            for(int h =0; h<(height-tilesize+1)/tilesize;h++){//rows
                for(int w =0;w<(width-tilesize+1)/tilesize;w++){//cols
                    out.add(input[i].submat(h*tilesize,(h+1)*tilesize +oversize,w*tilesize,(w+1)*tilesize +oversize));
                }
            }
            output[i] = out.toArray(new Mat[out.size()]);
        }
        return output;
    }
    Mat[] mergeFrames(Mat[][] tiles, Point[][]shifts, int tilesize, int width, int height){
        ArrayList<Mat> out = new ArrayList<>();

        int oversize = 0;
        int cnt = 0;
        for(int i =0; i<tiles.length;i++){
            for(int h =0; h<(height-tilesize+1)/tilesize;h++){//rows
                for(int w =0;w<(width-tilesize+1)/tilesize;w++){//cols
                    //out.add(tiles[][])
                    cnt++;
                    //out.add(input[i].submat(h*tilesize,(h+1)*tilesize +oversize,w*tilesize,(w+1)*tilesize +oversize));
                }
            }
            //output[i] = out.toArray(new Mat[out.size()]);
        }
        return null;
    }

    void ApplyStabilization(){
        Mat[] grey = null;
        Mat[] col = null;
        Mat[][] readed = EqualizeImages();
        col = readed[0];
        grey = readed[1];
        Log.d("ImageProcessing Stab", "Curimgs size "+curimgs.size());
        Mat output = new Mat();
        col[col.length -1].copyTo(output);
        boolean aligning = Settings.instance.align;
        ArrayList<Mat> imgsmat = new ArrayList<>();
        MergeMertens merge = Photo.createMergeMertens();
        AlignMTB align = Photo.createAlignMTB();
        for(int i =0; i<curimgs.size()-1;i++) {
            //Mat cur = load_rawsensor(curimgs.get(i));
            if(aligning){
                Mat h=new Mat();
                Point shift = new Point();
                if(israw) h = findFrameHomography(grey[grey.length - 1], grey[i]);
                else {
                    //Video.findTransformECC(output,cur,h,Video.MOTION_HOMOGRAPHY, new TermCriteria(TermCriteria.COUNT+TermCriteria.EPS,20,1),new Mat(),5);
                    //h = findFrameHomography(grey[grey.length -1], grey[i]);
                    shift = align.calculateShift(grey[grey.length -1], grey[i]);
                }
                align.shiftMat(col[i],col[i],shift);
                //if(h != null) Imgproc.warpPerspective(col[i], col[i], h, col[i].size());
                //else Log.e("ImageProcessing ApplyStabilization","Can't find FrameHomography");
            }
            Log.d("ImageProcessing Stab", "Curimgs iter:"+i);
            processingstep();
            imgsmat.add(col[i]);
            Core.addWeighted(output,0.8,col[i],0.2,0,output);
        }
        Mat merging = new Mat();
        Log.d("ImageProcessing Stab", "imgsmat size:"+imgsmat.size());
        if(curimgs.size() > 4) for(int i =0; i<imgsmat.size()-1; i+=2) {
            Core.addWeighted(imgsmat.get(i),0.7,imgsmat.get(i+1),0.3,0,imgsmat.get(i));
            imgsmat.remove(i+1);
        }
        if(curimgs.size() > 6)for(int i =0; i<imgsmat.size()-1; i+=2) {
            Core.addWeighted(imgsmat.get(i),0.7,imgsmat.get(i+1),0.3,0,imgsmat.get(i));
            imgsmat.remove(i+1);
        }
        if(curimgs.size() > 11)for(int i =0; i<imgsmat.size()-1; i+=2) {
            Core.addWeighted(imgsmat.get(i),0.7,imgsmat.get(i+1),0.3,0,imgsmat.get(i));
            imgsmat.remove(i+1);
        }
        processingstep();
        //merge.process(imgsmat,merging);
        //Core.convertScaleAbs(merging,output,255);
        if(!israw) {
            Mat outb = new Mat();
            double params = Math.sqrt(Math.log(Camera2Api.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY))*22) + 9;
            Log.d("ImageProcessing Denoise2", "params:"+params + " iso:"+Camera2Api.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
            params = Math.min(params,50);
            int ind = imgsmat.size()/2;
            if(ind %2 == 0) ind-=1;
            int wins = imgsmat.size() - ind;
            if(wins%2 ==0 ) wins-=1;
            wins = Math.max(0,wins);
            ind = Math.max(0,ind);
            Log.d("ImageProcessing Denoise", "index:"+ind + " wins:"+wins);
            imgsmat.set(ind,output);
            Mat outbil = new Mat();
            Mat cols = new Mat();
            ArrayList<Mat> cols2 = new ArrayList<>();
            Imgproc.cvtColor(output,cols,Imgproc.COLOR_BGR2YUV);
            Core.split(cols,cols2);
            for(int i =0; i<3; i++) {
                Mat out = new Mat();
                Mat cur = cols2.get(i);
                processingstep();
                if(i==0) {
                    //Core.multiply(cur,new Scalar(1.0),cur);
                    Core.add(cur,new Scalar(-0.1*32),cur);
                    //Xphoto.oilPainting(cur,out,Settings.instance.lumacount,(int)(Settings.instance.lumacount*0.2 + 1));
                    Photo.fastNlMeansDenoising(cur,out,Settings.instance.lumacount,9,15);
                    //Imgproc.bilateralFilter(cur,out,Settings.instance.lumacount,Settings.instance.lumacount*2,Settings.instance.lumacount*2);
                }
                if(i!=0) {
                    Size bef = cur.size();
                    Imgproc.pyrDown(cur,cur);
                    Imgproc.bilateralFilter(cur,out,Settings.instance.chromacount,Settings.instance.chromacount*2,Settings.instance.chromacount*2);//Xphoto.oilPainting(cols2.get(i),cols2.get(i),Settings.instance.chromacount,(int)(Settings.instance.chromacount*0.1));
                    Imgproc.pyrUp(out,out,bef);
                }
                cur.release();
                cols2.set(i,out);
            }
            Core.merge(cols2,cols);
            Imgproc.cvtColor(cols,output,Imgproc.COLOR_YUV2BGR);
            processingstep();
            //Core.merge(cols,output);
            //Imgproc.bilateralFilter(outb,outbil, (int) (params*1.2),params*1.5,params*3.5);
            outb = outbil;
            Imgcodecs.imwrite(path,output);
        }
        Camera2Api.loadingcycle.setProgress(0);

    }
    public void Run(){
        Image.Plane plane = curimgs.get(0).getPlanes()[0];
        byte buffval = plane.getBuffer().get();
        Log.d("ImageProcessing", "Camera bayer:"+Camera2Api.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT));
        CaptureResult res = Camera2Api.mCaptureResult;
        //RggbChannelVector rate = res.get(CaptureResult.COLOR_CORRECTION_GAINS);
        //Core.divide(mat,new Scalar(rate.getBlue(),rate.getGreenOdd()),mat);
        //mat = mat.t();
        //Core.divide(mat,new Scalar(rate.getGreenEven(),rate.getRed()),mat);
        //mat = mat.t();
        //Imgproc.cvtColor(mat,mat,Imgproc.COLOR_BayerBG2BGR);
        //Imgproc.demosaicing(mat,mat,Imgproc.COLOR_BayerBG2BGR);
        ApplyStabilization();
        //Imgcodecs.imwrite(ImageSaver.curDir()+"//"+ImageSaver.curName()+"_CV.jpg",imgs[0]);
        Log.d("ImageProcessing","buffer parameters:");
        Log.d("ImageProcessing","bufferpixelstride"+plane.getPixelStride());
        Log.d("ImageProcessing","bufferrowstride"+plane.getRowStride());
        Log.d("ImageProcessing","buffervalue"+buffval);
    }
}

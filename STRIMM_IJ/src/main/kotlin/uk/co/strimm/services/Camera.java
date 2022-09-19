package uk.co.strimm.services;

import mmcorej.CMMCore;
import uk.co.strimm.STRIMMImage;
import uk.co.strimm.gui.GUIMain;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

//this does not need to be runnable but does need to return a STRIMMImage
public class Camera {
    public String name = "";
    public String library = "";
    public String label = "";
    CMMCore core = new CMMCore();

    int count = 0;

    //These boolean values set how the camera's images will be taken - snapped, triggered, or timelapse
    boolean bSnapped = false;
    boolean bKeyboardSnapEnabled = false;
    int SnapVirtualCode = 0;

    public void SetKeyboardSnapEnabled(Boolean bSnap){
        bKeyboardSnapEnabled = bSnap;
    }
    public void SetSnapVirtualCode(int VK){
        SnapVirtualCode = VK;
    }
    boolean bTriggered = false;
    boolean bTimeLapse = false;



    double intervalMs = 100.0;
    public double exposureMs = 0.5;
    boolean bActive = false;
    int framesInCircularBuffer = 20;

    boolean bGreyscale = false;

    public void Reset(){
        count = 0;
    }
    public void SetGreyScale(boolean bl) {
        bGreyscale = bl;
    }

    public void SetCameraActivation(boolean bl) {
        bActive = bl;
    }

    boolean bConfig = false;

    public CMMCore getCore() {
        return core;
    }

    public void StartAcquisition() {
        System.out.println("Calling StartAcquisition()");
        if (!bSnapped && GUIMain.experimentService.experimentStream.isRunning()) {

            long memSize = core.getBytesPerPixel() * core.getImageHeight() * core.getImageWidth() * framesInCircularBuffer / 1024 / 1024;
            try {
                System.out.println("If error here then problems caused by setCircularBufferMemoryFootprint(), so reduce the number of frames on circular buffer or use the default -1.");
                if (memSize > 0) core.setCircularBufferMemoryFootprint(memSize);

                core.initializeCircularBuffer(); //circular buffer

                core.startContinuousSequenceAcquisition(0); //must be
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

    public void SaveAsTiff(byte[] pix1) {
        try {
            BufferedImage bufImage =
                    new BufferedImage(512, 512,
                            BufferedImage.TYPE_BYTE_GRAY);
            int cnt = 0;
            for (int j = 0; j < 512; j++) {
                for (int i = 0; i < 512; i++) {
                    int val = (int) pix1[cnt];
                    bufImage.setRGB(i, j, val + val * 256 + val * 256 * 256);
                    cnt++;
                }
            }

            ImageIO.write(bufImage, "tiff", new File("Acquisitions/" + name + "/" + count + ".tiff"));
            count++;
        } catch (Exception ex) {
            System.out.println("error saving tiff");
        }
    }

    public static void main(String[] args) {
//        try {
//
//
//            BufferedImage bufImage =
//                    new BufferedImage(512, 512,
//                            BufferedImage.TYPE_BYTE_GRAY);
//            int val = 255;
//            for (int j = 0; j<512; j++){
//                for (int i = 0; i<200; i++){
//                    bufImage.setRGB(i, j, val + val*256 + val*256*256);
//                }
//            }
//
//            ImageIO.write(bufImage, "tiff", new File("testzzz.tiff"));
//        } catch(Exception ex){
//            System.out.println(ex.getMessage());
//        }
    }

    public STRIMMImage run() {
        return new STRIMMImage("Not recognised", null, GUIMain.softwareTimerService.getTime(), 0, 0, 0);
    }

    //


    public void SetExposureMs(double exp) throws Exception {
        exposureMs = exp;
        if (core != null) core.setExposure(exposureMs);
    }

    public void SetSnapped(boolean bSnap) {
        bSnapped = bSnap;
    }

    public void SetTriggered(boolean bTrig) throws Exception {
        bTriggered = bTrig;
    }

    public void SetTimeLapse(boolean bTimeL) {
        bTimeLapse = bTimeL;
    }

    public void SetIntervalMs(double intMs) {
        intervalMs = intMs;
    }

    public void SetFramesInCircularBuffer(int circ) {
        framesInCircularBuffer = circ;
    }

    public void SetROI(int x, int y, int w, int h) {
        if (w > 0 && h > 0) {
            try {
                core.setROI(x, y, w, h);
                GUIMain.strimmUIService.SetROI(label, x, y, w, h);
            } catch (Exception e) {
                System.out.println("Error " + label + " unable to set ROI");
                e.printStackTrace();
            }
        }
    }
}

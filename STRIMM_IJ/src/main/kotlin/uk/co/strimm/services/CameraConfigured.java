package uk.co.strimm.services;

import com.opencsv.CSVReader;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import uk.co.strimm.STRIMMImage;
import uk.co.strimm.gui.GUIMain;

import java.io.FileReader;
import java.util.List;
import java.util.logging.Level;

public class CameraConfigured extends Camera{
    double timeAcquired = 0.0;
    int cnt = 0;
    long timeStart = 0;
    String szCameraFile;

    public CameraConfigured(String szCamera) throws Exception{
        try {
            szCameraFile = szCamera;
            StrVector st = new StrVector();
            st.add("./DeviceAdapters");
            core.setDeviceAdapterSearchPaths(st);
            core.loadSystemConfiguration(".\\DeviceAdapters\\CameraMMConfigs\\" + szCamera);
            label = core.getCameraDevice();
            name = core.getDeviceName(label);
            library = core.getDeviceLibrary(label);
            System.out.println(label + " " + library + " " + name);
            //JOptionPane.showMessageDialog(null, label + " " + library + " " + name);


        } catch (Exception ex){
            System.out.println("failed to load " + szCamera);
            System.out.println(ex.getMessage());
           // JOptionPane.showMessageDialog(null, ex.getMessage());
        }
    }
    @Override
    public void SetTriggered(boolean bTrig) throws Exception {
        System.out.println("TERRY : SetTriggered");
        bTriggered = bTrig;
        if (bTrig) {
            List <String[]> r = null;
            try (CSVReader reader = new CSVReader(new FileReader(".\\DeviceAdapters\\CameraMMConfigsTrigger\\" + szCameraFile))) {
                System.out.println("TERRY Made a CSVReader at " + ".\\DeviceAdapters\\CameraMMConfigsTrigger\\" + szCameraFile);
                r = reader.readAll();
                System.out.println("TERRY  CSVReader ReadAll()");
            } catch (Exception ex){
                System.out.println(ex.getMessage());
            }
            for (String[] triggerCfg : r){
                try {
                    System.out.println("TERRY  core.setProperty " + triggerCfg[0] + "  " + triggerCfg[1]);
                    core.setProperty(label, triggerCfg[0], triggerCfg[1]);
                } catch(Exception ex){
                    System.out.println(ex.getMessage());
                }

            }

        }
    }

    @Override
    public STRIMMImage run() {
        //System.out.println("" + core);
        Object pix = null;

        try {

            //prevent taking images before runStream
            while (!GUIMain.acquisitionMethodService.getBCamerasAcquire()) {
                Thread.sleep(10);
            }

            if (GUIMain.experimentService.experimentStream.getExpConfig().isGlobalStart()){
                //System.out.println("Start trigger = " + GUIMain.protocolService.getBGlobalSourceStartTrigger());
                while (!GUIMain.protocolService.getBGlobalSourceStartTrigger()){
                    //System.out.println(GUIMain.protocolService.getBGlobalSourceStartTrigger());
                    Boolean bKeyPressed = GUIMain.protocolService.getJdaq().GetKeyState(GUIMain.experimentService.experimentStream.getExpConfig().getGlobalStartVirtualCode());
                    //System.out.println(bKeyPressed);
                    if (bKeyPressed){
                        GUIMain.protocolService.setBGlobalSourceStartTrigger(true);
                        GUIMain.softwareTimerService.setFirstTimeMeasurement(); //mix up with time here TIME_CONFUSION
                        //this is used by experiment duration
                    }
                    Thread.sleep(100);
                }
           }

//            while(!GUIMain.experimentService.experimentStream.isRunning()){
//                Thread.sleep(10);
//            }

            if (!bSnapped) // using the core's circular buffer
            {
                int x1 = 0;
                int x2 = 0;
                while (true) {
                    //get the current index into the circular buffer store it in x1 and
                    //then wait until that changes which means that a new image has been
                    //put onto the circular buffer
                    x1 = core.getRemainingImageCount();
                    x2 = x1;
                    //if the buffer is empty or the device is busy then spin
                    while (x2 == x1 || x2 == 0 || core.deviceBusy(label)){
                        x2 = core.getRemainingImageCount();
                    }

                    //collect and remove (pop) the last image (to reduce the chances of overflow
                    // which might happen more quickly if you kept the image on the circular buffer)
                    //
                    //between the end of the above while loop and the beginning of the section
                    //which retieves the image the buffer could have reset so wrap the next section in
                    //try/catch  to alert us to this happening and also to have another go at getting an
                    //image when it does happen.
                    //
                    if (count == 0) {
                        timeStart = 0L;
                    }

                    try {
                        //collect the image
                        TaggedImage im = core.getLastTaggedImage();
                        pix = im.pix;
                        count++;

                        String MMMetadataTagName = "ElapsedTime-ms";
                        JSONObject test = im.tags;
                        String timeStamp = (String)im.tags.get(MMMetadataTagName);
                        timeAcquired = Double.parseDouble(timeStamp);
                        break;
                    } catch (Exception ex) {
                        GUIMain.loggerService.log(Level.SEVERE, "Error getting image from circular buffer. Message " + ex.getMessage());
                        GUIMain.loggerService.log(Level.SEVERE, ex.getStackTrace());
                        //TW 2/8/21 the most likely reason for ending up here is that
                        //we attempted to get an image from an circular buffer (it has reset)
                        //so go around the while loop again and retrieve an image
                        //This will mean that we have LOST an image unless the circular buffer
                        //size is larger than the number of frames needed in the acquisition.
                        System.out.print("*****CIRCULAR BUUFER: Found 0 frames in the buffer *****");
                    }
                }
            }
            else //using the core's image buffer
            {
                //snap the image and also gather some information to
                //estimate the fps
                if (bKeyboardSnapEnabled){
                    Boolean bKeyPressed = GUIMain.protocolService.getJdaq().GetKeyState(SnapVirtualCode);
                   // System.out.println("Key pressed = " + bKeyPressed.toString());
                    while (!bKeyPressed) {
                        Thread.sleep(50);
                        bKeyPressed = GUIMain.protocolService.getJdaq().GetKeyState(SnapVirtualCode);

                    }
                }

                if (count == 0){
                    timeStart = System.nanoTime();
                }

                core.snapImage();
                pix = core.getImage();

                count++;
                timeAcquired = System.nanoTime();
//                String timeStampString = pop.tags[MMMetadataTagName];
//                val timeStampString = pop.tags[MMMetadataTagName] as String
            }
        } catch (Exception ex) {
            System.out.println(label + " exception");
            System.out.println(ex.getMessage());
        }
        //check label
        //check the time getTime()
        if (core.getBytesPerPixel() == 4) {
            //deal with ARGB  for OpenCV - needs to be updated for better support for ARGB, RGB etc

            float[] fpix = new float[(int) (getCore().getImageWidth() * getCore().getImageHeight())];
            byte[] pix1 = (byte[]) pix;
            for (int j = 0; j < getCore().getImageHeight(); j++) {
                for (int i = 0; i < getCore().getImageWidth(); i++) {
                    byte b1 = pix1[4 * i + 4 * j * (int) getCore().getImageWidth()];
                    byte b2 = pix1[1 + 4 * i + 4 * j * (int) getCore().getImageWidth()];
                    byte b3 = pix1[2 + 4 * i + 4 * j * (int) getCore().getImageWidth()];

                    fpix[i + j * (int) getCore().getImageWidth()] = (Byte.toUnsignedInt(b1) +
                            Byte.toUnsignedInt(b2) + Byte.toUnsignedInt(b3)) / 3;
                }
            }

            if (bGreyscale){
                return new STRIMMImage(label, fpix, GUIMain.softwareTimerService.getTime(), count, (int) core.getImageWidth(), (int) core.getImageHeight());
            }
            else{
                return new STRIMMImage(label, pix, GUIMain.softwareTimerService.getTime(), count, (int) core.getImageWidth(), (int) core.getImageHeight());
            }
        }
       // System.out.println("taken image:" + count);
        return new STRIMMImage(label, pix, GUIMain.softwareTimerService.getTime(), count, (int) core.getImageWidth(), (int) core.getImageHeight());
    }
}


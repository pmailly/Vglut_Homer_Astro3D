package VglutHomerTools;


import emblcmci.BleachCorrection_MH;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.WaitForUserDialog;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.RGBStackMerge;
import ij.plugin.filter.RankFilters;
import ij.plugin.frame.ThresholdAdjuster;
import ij.process.ImageProcessor;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import mcib3d.geom.Object3D;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.geom.Voxel3D;
import mcib3d.image3d.ImageFloat;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.ImageLabeller;
import mcib3d.image3d.distanceMap3d.EDT;
import mcib3d.image3d.processing.MaximaFinder;
import mcib3d.utils.ThreadUtil;
        
 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose dots_Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author phm
 */
public class Vglut_Homer_Tools3D {
    
// max distance to find dots to astrocyte
    public static double maxDistAstro = 1.5; 
// max distance to define a synapse    
    public static double maxDistSynap = 0.4;
    public static double VglutTol, HomerTol;
    public static double zStep;
    public static float vglutMaxRadXY = 3;
    public static float vglutMaxRadZ = 3;
    public static float vglutNoise = 500;
    public static float homerMaxRadXY = 3;
    public static float homerMaxRadZ = 3;
    public static float homerNoise = 2000;
// bleac correction Astrocyte
    public static boolean bleachCorrAstro = false;
    public static boolean bleachCorrVglutSted = false;
    public static boolean bleachCorrVglutConf = false;
    public static boolean bleachCorrHomerSted = false;
    public static boolean bleachCorrHomerConf = false;
    
    // min intensity of vglutSted dot in vglut confocal channel
    public static int vglutConfDotsIntRef = 10000;
    // min intensity of homerSted dot in homer confocal channel
    public static int homerConfDotsIntRef = 500;  
    public static Calibration cal = new Calibration();
 // threshold method
    public static String thresholdMethod = "Default"; 
    public static ArrayList<String> channels = new ArrayList();
    
    public static Vglut_Homer_Tools3D instance;
    


    /**
     * Histogram matching bleach correction
     * 
    */
    public static void bleachCorrection (ImagePlus img) {
        BleachCorrection_MH BCMH = new BleachCorrection_MH(img);
        BCMH.doCorrection();
        if(WindowManager.getWindow("Log").isShowing())
            WindowManager.getWindow("Log").dispose();
    }
    
    
    /**
     * Threshold dialog
     */
    public static void dialogThreshod(ImagePlus img) {
        img.getProcessor().resetThreshold();
        img.setSlice(img.getNSlices()/2);
        img.updateAndDraw();
        img.show();
        IJ.run("Threshold...");
        if (!WindowManager.getWindow("Threshold").isVisible());
            WindowManager.getWindow("Threshold").setVisible(true);
        new WaitForUserDialog("Choose threshold method and press OK\n Do not click on Apply!!!").show();
        thresholdMethod = new ThresholdAdjuster().getMethod();
        WindowManager.getWindow("Threshold").setVisible(false);
        img.hide();
    }
    
 /**
  * Find dots sted
  * take only dots sted if intensity in confocal channel >= dotsIntRef 
  */
    public static void findDotsIntPop (ImagePlus imgConf, ArrayList<Voxel3D> dotsPop, int intRef) {
        Iterator itr = dotsPop.iterator(); 
        while (itr.hasNext()) {
            Voxel3D dotVoxel = (Voxel3D)itr.next();
            imgConf.setSlice(dotVoxel.getRoundZ() + 1);
            ImageProcessor ip = imgConf.getProcessor();
            int confInt = ip.getPixel(dotVoxel.getRoundX(), dotVoxel.getRoundY());
            dotVoxel.setValue(confInt);
            //System.out.println("int = "+confInt);
            if (confInt <= intRef) {
                itr.remove();
            }
        }
    }
    
  
    /**
     * return objects population in an binary image
     * @param img
     * @return pop objects population
     */

    public static Objects3DPopulation getPopFromImage(ImagePlus img) {
        // label binary images first
        ImageLabeller labeller = new ImageLabeller();
        ImageInt labels = labeller.getLabels(ImageHandler.wrap(img));
        Objects3DPopulation pop = new Objects3DPopulation(labels);
        return pop;
    }   
     
    // Flush and close images
    public static void flush_close(ImagePlus img) {
        img.flush();
        img.close();
    }

    
     /*Median filter 
     * 
     * @param img
     * @param size
     */ 
    public static void median_filter(ImagePlus img, double size) {
        RankFilters median = new RankFilters();
        for (int s = 1; s <= img.getNSlices(); s++) {
            img.setZ(s);
            median.rank(img.getProcessor(), size, RankFilters.MEDIAN);
            img.updateAndDraw();
        }
    }
    
    private static void saveMaxDotsImage(ImagePlus imgDots, ImagePlus imgMax, String path) {
        ImagePlus[] imgColors = {imgMax, null, null, imgDots.duplicate()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(path+imgDots.getTitle()+"_MaxDots.tif");
        flush_close(imgObjects);
    }
    
     /**
     * Find dots population with 3D local maximum
     * return dots with intensity > confDotsIntRef
     * @param imgDots
     * @return 
     */
    public static ArrayList<Voxel3D> findDotsWithMaxLocal (ImagePlus imgDots, float maxRadXY, float maxRadZ,
            float noise, String path) {
        ImageHandler imh = ImageHandler.wrap(imgDots.duplicate());
        MaximaFinder max = new MaximaFinder(imh, maxRadXY, maxRadZ, noise);
        max.setVerbose(false);
        max.setNbCpus(ThreadUtil.getNbCpus());
        ArrayList<Voxel3D> listPeaks = max.getListPeaks();
        // filter dots on first and last section
        Iterator<Voxel3D> it = listPeaks.iterator();
        while (it.hasNext()) {
            Voxel3D v = it.next();
            int z = v.getRoundZ();
            if (z == 0 || z == imgDots.getNSlices() - 1)
                it.remove();
        }
        saveMaxDotsImage(imgDots, max.getImagePeaks().getImagePlus(), path);
        return(listPeaks);
    }

    
    private static double distance3DUnits (Voxel3D vox1, Voxel3D vox2) {
        double d = (cal.pixelWidth * cal.pixelHeight) * (Math.pow((vox2.x - vox1.x),2) + Math.pow((vox2.y - vox1.y), 2)) +
                (cal.pixelDepth * cal.pixelDepth) * Math.pow((vox2.z - vox1.z), 2); 
        return(Math.sqrt(d));
    }
    
    private static Voxel3D findNearestVoxel(Voxel3D vox, ArrayList<Voxel3D> list) {
        double dist = maxDistSynap;
        Voxel3D voxNearest = null;
        for (int i = 0; i < list.size(); i++) {
            double d = distance3DUnits(vox, list.get(i));
            if (d <= dist) {
                dist = d;
                voxNearest = list.get(i);
            }
        }
        return(voxNearest);    
    }
    
    
     /**
      * 
     * Find synapses point and distance between vglut and homer
     * @param vglut
     * @param homer
     * @return Synapses
     */
    
    public static ArrayList<Synapse_Vglut_Homer> findSynapses (ArrayList<Voxel3D> vglut, ArrayList<Voxel3D> homer) {
        ArrayList<Synapse_Vglut_Homer> synapList = new ArrayList();
        // find synapses 
        IJ.showStatus("Finding synapses ...");
        for (int i = 0; i < vglut.size(); i++) {
            Voxel3D vglutV = vglut.get(i);
            Voxel3D homerNearest = findNearestVoxel(vglutV, homer);
            if (homerNearest != null) {
                double dist = distance3DUnits(vglutV, homerNearest);
                double xc = (vglutV.x + homerNearest.x)/2;
                double yc = (vglutV.y + homerNearest.y)/2;
                double zc = (vglutV.z + homerNearest.z)/2;
                Voxel3D synV = new Voxel3D(xc, yc, zc, 0);
                Synapse_Vglut_Homer synap  = new Synapse_Vglut_Homer(synV, dist, 0, vglutV, homerNearest);
                /*System.out.println("Vglut "+vglutV.x+", "+vglutV.y+", "+vglutV.z+
                        "Homer "+homerNearest.x+", "+homerNearest.y+", "+homerNearest.z+ 
                        "Synapse "+synV.x+", "+synV.y+", "+synV.z);
                */
                synapList.add(synap);
                // remove homer dot it could not participate to another synapse
                homer.remove(homerNearest);
            }
        }
        System.out.println(synapList.size() +" synpases found");
        return(synapList);
    }
    
    
    /** Find min distance from synapses dots to astro border using EDT
     * 
     *  Compute Volumes of synapses dots
     * @param imgAstro
     * @param astroObj
     * @param synapses
     */
    public static void distanceSynToAstroBorder (ImagePlus imgAstro, Object3D astroObj, 
            ArrayList<Synapse_Vglut_Homer> synapses, String outDirResults) {
        double dist;
        // EDT inverse astro info 
        ImageHandler img = ImageInt.wrap(imgAstro).createSameDimensions();
        astroObj.draw(img, 128);
        ImageFloat edtAstro = EDT.run(img, 120, (float)cal.pixelWidth, (float)cal.pixelDepth, true, 0);
        Iterator itr = synapses.iterator(); 
        while (itr.hasNext()) {
            IJ.showStatus("Finding distances ...");
            Synapse_Vglut_Homer syn = (Synapse_Vglut_Homer)itr.next();
            Voxel3D synapse = syn.getSynV();
            // distance to astro
            int x = synapse.getRoundX();
            int y = synapse.getRoundY();
            int z = synapse.getRoundZ() + 1;
            edtAstro.getImagePlus().setSlice(z);
            dist = edtAstro.getImagePlus().getProcessor().getPixelValue(x, y);
            //System.out.println("x= "+x+" y= "+y+" z= "+z+" dist = "+dist);
            // if dist <= distMax add dist to synapse else remove synapse
            if (dist <= maxDistAstro)
                syn.setDistAstro(dist);
            else
                itr.remove();
        }
        
//        FileSaver edtSave = new FileSaver(edtAstro.getImagePlus());
//        edtSave.saveAsTiff(outDirResults + img.getTitle() + "_EDT" + ".tif");
        edtAstro.closeImagePlus();
        img.closeImagePlus();
        System.out.println(synapses.size()+" synapses arround "+maxDistAstro+ " from astrocyte");
    }
    
    
    
    public static void savePopImage(ImagePlus imgSted, ImagePlus imgConf, ArrayList<Voxel3D> dots, String col, String outDirResults) {
        String imageName = imgSted.getTitle();
        //create image objects population
        ImagePlus imgDots = IJ.createImage("synapses", imgSted.getWidth(), imgSted.getHeight(), imgSted.getNSlices(), 16);
        drawDots(dots, imgDots, 65000);
        ImagePlus[] imgColors = {null, null, imgSted, imgConf};
        if (col.equals("green"))
            imgColors[1] =  imgDots;
        else if (col.equals("red"))
            imgColors[0] =  imgDots;
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        FileSaver imgObjectsFile = new FileSaver(imgObjects);
        imgObjectsFile.saveAsTiff(outDirResults +File.separator+ imageName + "_StedinConf.tif");
        flush_close(imgDots);
    }
    
        
   // write object labels
    public static void labelsObject (Voxel3D pt, ImagePlus img, int number) {
        Font tagFont = new Font("SansSerif", Font.PLAIN, 10);
        int x = pt.getRoundX();
        int y = pt.getRoundY();
        int z = pt.getRoundZ() + 1;
        img.setSlice(z);
        ImageProcessor ip = img.getProcessor();
        ip.setFont(tagFont);
        ip.setColor(65000);
        String text = Integer.toString(number);
        ip.drawString(text, x - text.length(), y - 2);
        ip.drawDot(x, y);
        img.updateAndDraw();
    }
    
    /**
     * Draw dots
     */
    private static void drawDots(ArrayList<Voxel3D> dots, ImagePlus img, int col){
        for (int i = 0; i < dots.size(); i++) {
            int r = 6;
            int x = dots.get(i).getRoundX() - r/2;
            int y = dots.get(i).getRoundY() - r/2;
            int z = dots.get(i).getRoundZ() + 1;
            img.setSlice(z);
            ImageProcessor ip = img.getProcessor();
            ip.setColor(col);
            ip.drawOval(x, y, r, r);
            img.updateAndDraw();
        }
    }
   
    
     /**
     * Save objects images
     * @param vglutPop
     * @param homerPop
     * @param synapses
     * @param imgAstro
     * @param path 
     */
    public static void saveSynapses(ArrayList<Synapse_Vglut_Homer> synapses, ImagePlus imgAstro, Object3D astroObj, String path) {
        ImagePlus imgSynap = IJ.createImage("synapses", imgAstro.getWidth(), imgAstro.getHeight(), 
               imgAstro.getNSlices(), 16);
        ImagePlus imgVglutObjects = imgSynap.duplicate();
        ImagePlus imgHomerObjects = imgSynap.duplicate();
        ImageHandler imgAstroObject = ImageHandler.wrap(imgSynap.duplicate());
        ArrayList<Voxel3D> vglutV = new ArrayList();
        ArrayList<Voxel3D> homerV = new ArrayList();
        for (int i = 0; i < synapses.size(); i++) {
            Synapse_Vglut_Homer syn = synapses.get(i);
            vglutV.add(syn.getVglutV());
            homerV.add(syn.getHomerV());
        }
        drawDots(vglutV, imgVglutObjects, 255);
        drawDots(homerV, imgHomerObjects, 255);
        astroObj.draw(imgAstroObject, 64);
        // draw synapses as point
        for (int i = 0; i < synapses.size(); i++) {
            labelsObject(synapses.get(i).getSynV(), imgSynap, (i+1));
        }
        ImagePlus[] imgColors = {imgHomerObjects, imgVglutObjects, null, imgAstro, imgAstroObject.getImagePlus(),
        null, imgSynap};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(path+"_Objects.tif");
        flush_close(imgObjects);
        flush_close(imgVglutObjects);
        flush_close(imgHomerObjects);
        flush_close(imgSynap);   
    }
    
    /** Write results
     * 
     * @param image
     * @param astroPop
     * @param synapses
     * @param outDirResults
     * @param outPutResults
     * @throws java.io.IOException
     */
    public static void writeResults(String image, Object3D astroObj, ArrayList<Synapse_Vglut_Homer> synapses, 
            String outDirResults, BufferedWriter outPutResults) throws IOException {
        
        // Total astro volume
        double astroVol = astroObj.getVolumeUnit();
        // write results
        for (int n = 0; n < synapses.size(); n++) {
            Synapse_Vglut_Homer synap = synapses.get(n);
            if (n == 0)
                outPutResults.write(image+"\t"+astroVol+"\t");
            else
                 outPutResults.write("-\t-\t");
            outPutResults.write((n+1)+"\t"+synap.getDistDots()+"\t"+synap.getDistAstro()+"\t"+synap.getVglutV().getValue()+"\t"+
                    synap.getHomerV().getValue()+"\n");
        }
        outPutResults.flush();
    }    
    
     /**
     * 
     * @param FileResults
     * @param resultsFileName
     * @param header
     * @return 
     */
    public static BufferedWriter writeHeaders(String outDirResults, String resultsFileName, String header) throws IOException {
        FileWriter FileResults = new FileWriter(outDirResults + resultsFileName, false);
        BufferedWriter outPutResults = new BufferedWriter(FileResults); 
        outPutResults.write(header);
        outPutResults.flush();
        return(outPutResults);
    }        
}

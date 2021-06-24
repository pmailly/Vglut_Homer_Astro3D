/*
 * Compute distances from Vglut-Homer synapses to astrocyte border
 * Author Philippe Mailly
 */
import VglutHomerTools.Synapse_Vglut_Homer;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.bleachCorrAstro;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.bleachCorrVglutSted;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.bleachCorrVglutConf;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.bleachCorrHomerSted;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.bleachCorrHomerConf;

import static VglutHomerTools.Vglut_Homer_Tools3DV2.bleachCorrection;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.cal;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.channels;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.vglutConfDotsIntRef;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.homerConfDotsIntRef;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.dialogThreshod;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.distanceSynToAstroBorder;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.findDotsIntPop;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.findDotsWithMaxLocal;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.findSynapses;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.flush_close;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.homerMaxRadXY;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.homerMaxRadZ;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.homerNoise;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.saveSynapses;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.savePopImage;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.thresholdMethod;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.vglutMaxRadXY;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.vglutMaxRadZ;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.vglutNoise;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.writeResults;
import static VglutHomerTools.Vglut_Homer_Tools3DV2.writeHeaders;

import ij.*;
import ij.plugin.PlugIn;
import java.awt.Frame;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom.Object3D;
import static mcib3d.geom.Object3D_IJUtils.createObject3DVoxels;
import mcib3d.geom.Voxel3D;
import org.apache.commons.io.FilenameUtils;



public class Vglut_Homer_Astrocyte3DV2 implements PlugIn {

    private boolean canceled = false;
    private String imageDir = "";
    public String outDirResults = "";
    public BufferedWriter outPutResults;
    
    
    /**
     * 
     * @param arg
     */
    @Override
    public void run(String arg) {
        
        try {
            imageDir = IJ.getDirectory("Choose Directory Containing Image Files...");
            if (imageDir == null) {
                return;
            }
            
            File inDir = new File(imageDir);
            String[] imageFile = inDir.list();
            if (imageFile == null) {
                return;
            }
            
            // create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            Arrays.sort(imageFile);
            int imageNum = 0;
            int series;
            String imageName = "";
            String rootName = "";
            for (String f : imageFile) {
                String fileExt = FilenameUtils.getExtension(f);
                if (fileExt.equals("ics") || fileExt.equals("tif")) {
                    imageName = inDir+ File.separator+f;
                    rootName = f.replace(fileExt, "");
                    imageNum++;
                    boolean showCal = false;
                    reader.setId(imageName);
                    int chNb = reader.getSizeC();

                    for (int c = 0; c < chNb; c++)
                        channels.add(Integer.toString(c));
                    // Check calibration
                    if (imageNum == 1) {
                        
                        series = 0;
                        cal.pixelWidth = meta.getPixelsPhysicalSizeX(series).value().doubleValue();
                        cal.pixelHeight = cal.pixelWidth;
                        if (meta.getPixelsPhysicalSizeZ(series) != null)
                            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(series).value().doubleValue();
                        else
                            showCal= true;
                        
                        cal.setUnit("microns");
                        System.out.println("x cal = " +cal.pixelWidth+", z cal=" + cal.pixelDepth);
                        
                        // return the index for channels Astro, Vglut and Homer dots and ask for calibration if needed
                        JDialogParametersVglut_Homer_Astro dialog = new JDialogParametersVglut_Homer_Astro(new Frame(), true);
                        dialog.show();
                        
                        if (channels == null) {
                            IJ.showStatus("Plugin cancelled !!!");
                            return;
                        }
                        // create output folder
                        outDirResults = inDir + File.separator+ "Results"+ File.separator;
                        File outDir = new File(outDirResults);
                        if (!Files.exists(Paths.get(outDirResults))) {
                            outDir.mkdir();
                        }
                        /**
                         * Write headers results for results file
                         */
                        
                        String resultsName = "Synapses.xls";
                        String header = "Image\tAstrocyte Vol\t#Synapse\tSynapse dots distance Vglut-Homer\t"
                                + "Synapse Dist to Astro border\tVglut conf intensity\tHomer conf intensity\n";
                        outPutResults = writeHeaders(outDirResults, resultsName, header);
                    }
                    
                    
                    // read channels
                    
                    ImporterOptions options = new ImporterOptions();
                    options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                    options.setId(imageName);
                    options.setSplitChannels(true);
                    options.setQuiet(true);
                    options.setSeriesOn(0, true);

                    // Open channels
                    // Astro channel
                    String channelName = rootName + "_Astro";
                    int channelIndex = Integer.parseInt(channels.get(0));
                    options.setCBegin(0, channelIndex);
                    options.setCEnd(0, channelIndex);
                    System.out.println("Opening"+ channelName+" channel "+ channelIndex);
                    ImagePlus imgAstro = BF.openImagePlus(options)[0];
                    if(imgAstro.getBitDepth() == 32)
                        IJ.run(imgAstro, "16-bit","");
                    imgAstro.setTitle(channelName);
                    // Find Astro pop
                    IJ.showStatus("Finding astrocyte cell ...");
                    ImagePlus imgAstroBin = imgAstro.duplicate();
                    if (bleachCorrAstro)
                        bleachCorrection(imgAstroBin);
                    IJ.run(imgAstroBin, "Unsharp Mask...", "radius=2.5 mask=0.7 stack");
                    if (imageNum == 1) 
                        dialogThreshod(imgAstroBin); 
                    IJ.setAutoThreshold(imgAstroBin, thresholdMethod + " dark");
                    System.out.println("Astro threshold = "+thresholdMethod);
                    Prefs.blackBackground = false;
                    IJ.run(imgAstroBin, "Convert to Mask","method="+thresholdMethod+" background=Dark");
                    Object3D astroObj = createObject3DVoxels(imgAstroBin, 255);
                    flush_close(imgAstroBin);
                    
                    // Vglut Sted
                    channelName = rootName + "_VglutSted";
                    channelIndex = Integer.parseInt(channels.get(2));
                    options.setCBegin(0, channelIndex);
                    options.setCEnd(0, channelIndex);
                    System.out.println("Opening"+ channelName+" channel "+ channelIndex);
                    ImagePlus imgVglutSted = BF.openImagePlus(options)[0];
                    if(imgVglutSted.getBitDepth() == 32)
                        IJ.run(imgVglutSted, "16-bit","");
                    imgVglutSted.setTitle(channelName);
                    if (bleachCorrVglutSted)
                        bleachCorrection(imgVglutSted);
                    
                    // Find Vglut dots in sted image
                    IJ.showStatus("Finding vglut sted dots ...");
                    ArrayList<Voxel3D> vglutStedVoxels = findDotsWithMaxLocal(imgVglutSted, vglutMaxRadXY, vglutMaxRadZ, 
                            vglutNoise, outDirResults);
                    System.out.println(vglutStedVoxels.size()+" Vglut dots found");

                    // Open vglut conf image
                    channelName = rootName + "_VglutConf";
                    channelIndex = Integer.parseInt(channels.get(1));
                    options.setCBegin(0, channelIndex);
                    options.setCEnd(0, channelIndex);
                    ImagePlus imgVglutConf = BF.openImagePlus(options)[0];
                    if(imgVglutConf.getBitDepth() == 32)
                        IJ.run(imgVglutConf, "16-bit","");
                    imgVglutConf.setTitle(channelName);
                    if (bleachCorrVglutConf)
                        bleachCorrection(imgVglutConf);
                    
                    // take only vglutSted dots if intensity in vglutConf image > dotsIntRef
                    findDotsIntPop(imgVglutConf, vglutStedVoxels, vglutConfDotsIntRef);
                    System.out.println(vglutStedVoxels.size()+" Vglut sted dots after threshold in confocal image");
                    
                    // save VglutStedPop on VglutConf image
                    savePopImage(imgVglutSted, imgVglutConf, vglutStedVoxels, "green", outDirResults);
                    flush_close(imgVglutConf);
                    flush_close(imgVglutSted);

                    // Homer
                    channelName = rootName + "_HomerSted";
                    channelIndex = Integer.parseInt(channels.get(4));
                    options.setCBegin(0, channelIndex);
                    options.setCEnd(0, channelIndex);
                    System.out.println("Opening"+ channelName+" channel "+ channelIndex);
                    ImagePlus imgHomerSted = BF.openImagePlus(options)[0];
                    if(imgHomerSted.getBitDepth() == 32)
                        IJ.run(imgHomerSted, "16-bit","");
                    imgHomerSted.setTitle(channelName);
                    if (bleachCorrHomerSted)
                        bleachCorrection(imgHomerSted);
                    
                    // Find Homer dots in sted image
                    IJ.showStatus("Finding homer sted dots ...");
                    ArrayList<Voxel3D> homerStedVoxels = findDotsWithMaxLocal(imgHomerSted, homerMaxRadXY, homerMaxRadZ,
                            homerNoise, outDirResults);
                    System.out.println(homerStedVoxels.size()+" Homer dots found");
                    
                    // Open Homer conf image
                    channelName = rootName + "_HomerConf";
                    channelIndex = Integer.parseInt(channels.get(3));
                    options.setCBegin(0, channelIndex);
                    options.setCEnd(0, channelIndex);
                    ImagePlus imgHomerConf = BF.openImagePlus(options)[0];
                    if(imgHomerConf.getBitDepth() == 32)
                        IJ.run(imgHomerConf, "16-bit","");
                    imgHomerConf.setTitle(channelName);
                    if (bleachCorrHomerConf)
                        bleachCorrection(imgHomerConf);

                   // take only homerSted dots if intensity in homerConf image > dotsIntRef
                    findDotsIntPop(imgHomerConf, homerStedVoxels, homerConfDotsIntRef);
                    System.out.println(homerStedVoxels.size() + " Homer sted dots after threshold in confocal image");
                    
                    // save HomerStedPop on HomerConf image
                    savePopImage(imgHomerSted, imgHomerConf, homerStedVoxels, "red", outDirResults);
                    flush_close(imgHomerConf);
                    flush_close(imgHomerSted);

                    // Find synapses

                    // synapse = distance homerSted dots vglutSted dots <= synapDist
                    IJ.showStatus("Finding synapses ....");
                    ArrayList<Synapse_Vglut_Homer> synapses = findSynapses(vglutStedVoxels, homerStedVoxels);
                    // Compute distance synapses to border astro
                    IJ.showStatus("Computing distances to astrocyte ....");
                    // with Distance map
                    distanceSynToAstroBorder(imgAstro, astroObj, synapses, outDirResults);

                    try {
                        // Writing results
                        writeResults(rootName, astroObj, synapses, outDirResults, outPutResults);
                    } catch (IOException ex) {
                        Logger.getLogger(Vglut_Homer_Astrocyte3DV2.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    // Save image objects
                    saveSynapses(synapses, imgAstro, astroObj, outDirResults+rootName);
                    flush_close(imgAstro);
                    
                    IJ.run("Collect Garbage", "");
                }    
            }
            if(!outDirResults.isEmpty())
                outPutResults.close();
            IJ.showStatus("Process done");
        } catch (DependencyException | FormatException | IOException | ServiceException ex) {
            Logger.getLogger(Vglut_Homer_Astrocyte3DV2.class.getName()).log(Level.SEVERE, null, ex);
        }
    } 
    
}
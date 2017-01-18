/*
 * JTrack version 1.0.0.
 */

import ij.IJ;
import ij.plugin.PlugIn;
import ij.*;
import ij.gui.GenericDialog;
import ij.process.*;
import ij.plugin.*;
import ij.measure.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import static java.lang.Math.abs;

/**
 *
 * @author Rowan Knobel
 */
public class JTrack_plugin implements PlugIn {

    @Override
    public void run(String arg) {
        // Initial options window for setting custom parameters.
        GenericDialog gd = new GenericDialog("jTrack Custom Settings");
        // All binary method options not causing errors or exceptions for our testing videos in the current version of ImageJ. 
        String[] binaryOptions = {"Default", "IsoData", "IJ_IsoData", "Li", "MaxEntropy", "Moments", "Otsu", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"};
        gd.setInsets(15, 0, 15);
        gd.addChoice("Binarization Method: ", binaryOptions, "Yen");
        gd.addCheckbox("Override Direction Detection for Animal Motion:", false);
        String[] directionOptions = {"Downwards", "Upwards"};
        gd.setInsets(-27, 300, 30);
        gd.addRadioButtonGroup("", directionOptions, 1, 2, "Downwards");
        gd.setInsets(30, 0, 30);
        gd.addNumericField("Noise Removal Threshold (in pixels): ", 25, 0);
        gd.setInsets(0, 70, 0);
        gd.addCheckbox("Override Target Line Detection -", false);
        gd.setInsets(-23, 0, 30);
        gd.addNumericField(" Y Coordinate (in pixels): ", 0, 0);
        gd.addNumericField("Head Angle Detection Width in Respect to the Nose Tip (in percentage): ", 10, 0);
        gd.setInsets(0, 0, 0);
        gd.addNumericField("ROI Length in Respect to the Nose Tip (in percentage): ", 10, 0);
        gd.setInsets(5, 198, 30);
        gd.addCheckbox("Dilate ROI for Whisker Detection", false);
        gd.addNumericField("Search Window for Whiskers (from the snout) - Min (in pixels): ", 20, 0);
        gd.setInsets(0, 0, 15);
        gd.addNumericField("Search Window for Whiskers (from the snout) - Max (in pixels): ", 30, 0);
        gd.addNumericField("Spatial Filter for Whisker Thickness - Min (in pixels): ", 4, 0);
        gd.setInsets(0, 0, 25);
        gd.addNumericField("Spatial Filter for Whisker Thickness - Max (in pixels): ", 8, 0);
        gd.setInsets(0, 185, 15);
        gd.addCheckbox("Paint Data Lines on Original Video", false);
        gd.addStringField("Body Outline - Color (in hexcode):", "0xFF0000");
        gd.addStringField("Nose Line - Color (in hexcode):", "0xFF8000");
        gd.addStringField("Target Line - Color (in hexcode):", "0xFFFFFF");
        gd.addStringField("Head Angle Detection Lines - Color (in hexcode):", "0x00FFFF");
        gd.addStringField("Whisker Angle Detection Lines - Color (in hexcode):", "0xFFFF33");
//        gd.addCheckbox("Use old whisker detection", false);
//        gd.setInsets(-23, 0, 0);
//        gd.addNumericField("Whisker Angle Width: ", 13, 0);

        gd.showDialog();
        if (gd.wasCanceled()) {
            return;
        }
        String binaryPick = gd.getNextChoice();
        boolean useCustomDirection = gd.getNextBoolean();
        String directionPick = gd.getNextRadioButton();
        int noiseThreshold = (int) gd.getNextNumber();
        boolean useCustomTarget = gd.getNextBoolean();
        int customTargetLine = (int) gd.getNextNumber();
        int headAnglePercentage = (int) gd.getNextNumber();
        int roiLengthPercentage = (int) gd.getNextNumber();
        boolean dilateROI = gd.getNextBoolean();
        int whiskerMinDistance = (int) gd.getNextNumber();
        int whiskerMaxDistance = (int) gd.getNextNumber();
        int whiskerMinThickness = (int) gd.getNextNumber();
        int whiskerMaxThickness = (int) gd.getNextNumber();
        boolean paintOriginal = gd.getNextBoolean();
        int outlineColor;
        int noseLineColor;
        int targetLineColor;
        int headAngleColor;
        int whiskerAngleColor;
        
        try {
            outlineColor = Integer.decode(gd.getNextString());
            noseLineColor = Integer.decode(gd.getNextString());
            targetLineColor = Integer.decode(gd.getNextString());
            headAngleColor = Integer.decode(gd.getNextString());
            whiskerAngleColor = Integer.decode(gd.getNextString());
        } catch (NumberFormatException error) {
            IJ.showMessage("Invalid hexcode.");
            return;
        }
//        boolean oldWhiskerFunction = gd.getNextBoolean();
//        int whiskerAnglePercentage = (int) gd.getNextNumber();

        double roiLength = roiLengthPercentage * 0.01;
        // Loading the complete video that is to be processed.
        IJ.run("Image Sequence...");
        ImagePlus imageSequence = IJ.getImage();

        // Option window to pick whether to use a single frame or an average of multiple frames as the background for the image calculator.
        GenericDialog gd2 = new GenericDialog("jTrack Background Frame Options");
        String[] backgroundOptions = {"Single", "Average"};
        gd2.setInsets(0, 0, 0);
        gd2.addMessage("If an averaged background frame is selected, the entire sequence of images");
        gd2.setInsets(0, 0, 0);
        gd2.addMessage("to be used as background must be imported.");
        gd2.setInsets(0, 0, -55);
        gd2.addMessage("Single or Averaged Background Frame: ");
        gd2.setInsets(0, 230, 0);
        gd2.addRadioButtonGroup(" ", backgroundOptions, 1, 2, "Single");
        gd2.showDialog();
        if (gd2.wasCanceled()) {
            return;
        }
        ImageCalculator imageCalculator = new ImageCalculator();
        String backgroundPick = gd2.getNextRadioButton();
        ImagePlus imageFrame = null;
        if (backgroundPick.equals("Single")) {
            IJ.open();
            imageFrame = IJ.getImage();
        } else {
            IJ.run("Image Sequence...");
            ImagePlus averageSequence = IJ.getImage();
            if (averageSequence.getStackSize() > 1) {
                ImagePlus firstImage = averageSequence.crop();
                ImagePlus newAverageFrame = imageCalculator.run("Average create", firstImage, averageSequence);
                ImagePlus newAverageFrame2 = null;
                firstImage.close();
                int i = 2;
                boolean uneven = false;
                // Gets an average of a new slice within a given image sequence against all previously averaged slices. It has to work one by one, and requires alternating
                // variables to close all the previous results until the final slice is averaged with the rest.
                while (i <= averageSequence.getStackSize()) {
                    averageSequence.setSlice(i);
                    if (uneven == true) {
                        newAverageFrame.close();
                        imageFrame = imageCalculator.run("Average create", newAverageFrame2, averageSequence);
                        imageFrame.show();
                        newAverageFrame = IJ.getImage();
                        uneven = false;
                    } else {
                        if (i != 2) {
                            newAverageFrame2.close();
                        }
                        imageFrame = imageCalculator.run("Average create", newAverageFrame, averageSequence);
                        imageFrame.show();
                        newAverageFrame2 = IJ.getImage();
                        uneven = true;
                    }
                    i = i + 1;
                }
                if (uneven == false) {
                    newAverageFrame2.close();
                } else {
                    newAverageFrame.close();
                }
                averageSequence.close();
            } else {
                imageFrame = averageSequence.crop();
                imageFrame.show();
                averageSequence.close();
            }
        }

        // Calculate difference between the entire image sequence and the chosen background frame, this should only leave the subject.
        ImagePlus differenceImage = imageCalculator.run("Difference create stack", imageSequence, imageFrame);
        if (paintOriginal == true) {
            imageSequence.hide();
        } else {
            imageSequence.close();
        }

        // Turn the difference result into a binary sequence, emphasizing only the subject and removing the background.
        IJ.run(differenceImage, "Make Binary", "method=" + binaryPick + " background=Black calculate"); //Black

        ImagePlus erodedImage = new Duplicator().run(differenceImage);
        // Erode thrice to make sure to remove whiskers completely.
        IJ.run(erodedImage, "Erode", "stack");
        IJ.run(erodedImage, "Erode", "stack");
        IJ.run(erodedImage, "Erode", "stack");
        IJ.run(erodedImage, "Erode", "stack");
        // System

        // Find out subject direction. Flip if subject moves upward.
        IJ.showStatus("Finding subject movement direction...");
        boolean flipImage;
        if (useCustomDirection == true) {
            if (directionPick.equals("Downwards")) {
                flipImage = false;
            } else {
                flipImage = true;
            }
        } else {
            flipImage = checkSubjectDirection(erodedImage);
        }
        if (flipImage == true) {
            IJ.run(erodedImage, "Flip Vertically", "stack");
            IJ.run(differenceImage, "Flip Vertically", "stack");
            IJ.run(imageFrame, "Flip Vertically", "stack");
            if (paintOriginal == true) {
                IJ.run(imageSequence, "Flip Vertically", "stack");
            }
        }

        // Find target line if a custom one has not been set in the options.
        int targetLineY;
        if (useCustomTarget != true) {
            IJ.showStatus("Finding target line...");
            double targetLineSensitivityPercentage = 0.75;
            targetLineY = findTargetLine(imageFrame, targetLineSensitivityPercentage);
            if (targetLineY == 0) {
                IJ.showMessage("Couldn't find target line.");
            }
        } else {
            if (flipImage == true) {
                targetLineY = imageFrame.getHeight() - customTargetLine;
            } else {
                targetLineY = customTargetLine;
            }
        }

        imageFrame.changes = false;
        imageFrame.close();

        // Calculate coordinates of the nose tip in every frame.
        IJ.showStatus("Finding nose data...");
        Map<Integer, ArrayList<Integer>> noseLineCoordinateMap = getNoseLineCoordinates(erodedImage, targetLineY);

        int frameThreshold = findTargetCrossedFrame(noseLineCoordinateMap, erodedImage, targetLineY);

        IJ.showStatus("Finding subject core outline data...");
        Map<Integer, ArrayList<ArrayList<Integer>>> erodedOutlineMap = getErodedOutlineCoordinates(noseLineCoordinateMap, erodedImage, frameThreshold, roiLength);
        // Calculate two points on both sides of the head in every frame to determine head direction.
        IJ.showStatus("Finding head angle data...");
        Map<Integer, ArrayList<String>> headAngleMap = getHeadAngleCoordinates(noseLineCoordinateMap, erodedImage, frameThreshold, headAnglePercentage);
        erodedImage.close();

        // Dilates the area around the head to thicken the whiskers for detection, eroding the rest to easily remove background noise.
        differenceImage.show();
        IJ.showStatus("Enhancing image...");
        differenceImage = enhanceImage(differenceImage, noseLineCoordinateMap, frameThreshold, roiLength, dilateROI);

        // Removes background noise in the dilated head area.
        IJ.showStatus("Removing noise...");
        differenceImage = removeNoise(differenceImage, noseLineCoordinateMap, frameThreshold, noiseThreshold, roiLength);

        // Calculate two points on both whiskers in every frame to determine whisker position.
        IJ.showStatus("Finding whisker angle data...");
        Map<Integer, ArrayList<String>> whiskerAngleMap = new HashMap<Integer, ArrayList<String>>();
//        if (oldWhiskerFunction == false) {
        whiskerAngleMap = getWhiskerAngleCoordinates(noseLineCoordinateMap, targetLineY, differenceImage, frameThreshold, erodedOutlineMap, whiskerMinDistance, whiskerMaxDistance,
                whiskerMinThickness, whiskerMaxThickness, roiLength);
//        } else {
//            whiskerAngleMap = getWhiskerAngleCoordinatesBackUp(noseLineCoordinateMap, targetLineY, differenceImage, frameThreshold, whiskerAnglePercentage);
//        }
        // Calculate head and whisker angles.
        headAngleMap = calculateAngles(headAngleMap, noseLineCoordinateMap, frameThreshold, false);
        whiskerAngleMap = calculateAngles(whiskerAngleMap, noseLineCoordinateMap, frameThreshold, true);

        IJ.showStatus("Finding subject outline data...");
        Map<Integer, ArrayList<ArrayList<Integer>>> outlineMap = new HashMap<Integer, ArrayList<ArrayList<Integer>>>();
        outlineMap = getOutlineCoordinates(differenceImage, frameThreshold);

        String title;
        if (paintOriginal == true) {
            title = imageSequence.getTitle();
            differenceImage.changes = false;
            differenceImage.close();
            imageSequence.show();
        } else {
            title = differenceImage.getTitle();
        }
        String channels = "c1=[";
        channels = channels.concat(title);
        channels = channels.concat("] c2=[");
        channels = channels.concat(title);
        channels = channels.concat("] c3=[");
        channels = channels.concat(title);
        channels = channels.concat("] create");

        // Convert image sequence to RGB.
        IJ.showStatus("Converting stack to RGB...");
        IJ.run("Merge Channels...", channels);
        IJ.run("Stack to RGB", "slices");

        ImagePlus rgbImage = IJ.getImage();
        ImageStack rgbStack = rgbImage.getStack();

        ImageProcessor ip;
        int xmax = rgbImage.getWidth();
        int ymax = rgbImage.getHeight();

        int lineX;

        // Color outline of the body, and indicate target line, nose tip line, and head/whisker contact points.
        IJ.showStatus("Painting image stack...");
        for (int i = 1; i <= rgbStack.getSize(); i++) {
            IJ.showProgress(i, frameThreshold);
            ip = rgbStack.getProcessor(i);
            int noseTipY = noseLineCoordinateMap.get(i).get(0);

            int pixelCount = 0;
            while (pixelCount < outlineMap.get(i).size()) {
                int x = outlineMap.get(i).get(pixelCount).get(0);
                int y = outlineMap.get(i).get(pixelCount).get(1);
                ip.set(x, y, outlineColor);
                pixelCount = pixelCount + 1;
            }
//            while (y > -1) {
//                x = 0;
//                while (x < xmax) {
//                    if (ip.getPixelValue(x, y) != 0) {
//                        blackDensityOutlineCount = findSurroundingPixelIntensity(x, y, ip);
//                        if (blackDensityOutlineCount > 1 && blackDensityOutlineCount < 8) {
//                            ip.set(x, y, outlineIntensity);
//                        }
//                    }
//                    x = x + 1;
//                }
//                y = y - 1;
//            }
            int y = ymax;
            // The indication lines are processed after the outline coloring process so the new colors don't interfere.
            while (y > -1) {
                if (y == targetLineY) {
                    lineX = 0;
                    while (lineX < xmax) {
                        if (ip.getPixel(lineX, y) != outlineColor) {
                            ip.set(lineX, y, targetLineColor);
                        }
                        lineX = lineX + 1;
                    }
                }
                lineX = 0;
                if (noseTipY != 0) {
                    while (lineX < xmax) {
                        if (y == noseTipY) {
                            if (ip.getPixel(lineX, noseTipY) != outlineColor) {
                                ip.set(lineX, noseTipY, noseLineColor);
                            }
                        }

                        //Between nose tip and left cheek point.
                        if (y <= noseTipY && y >= Integer.parseInt(headAngleMap.get(i).get(2))) {
                            if (lineX == Integer.parseInt(headAngleMap.get(i).get(0))) {
                                ip.set(lineX, y, headAngleColor);
                            }
                        }
                        //Between nose tip and right cheek point.
                        if (y <= noseTipY && y >= Integer.parseInt(headAngleMap.get(i).get(3))) {
                            if (lineX == Integer.parseInt(headAngleMap.get(i).get(1))) {
                                ip.set(lineX, y, headAngleColor);
                            }
                        }
                        //Between target line and left whisker point.
                        if (Integer.parseInt(whiskerAngleMap.get(i).get(2)) != 0) {
                            if (y <= targetLineY && y >= Integer.parseInt(whiskerAngleMap.get(i).get(2))) {
                                if (lineX == Integer.parseInt(whiskerAngleMap.get(i).get(0))) {
                                    ip.set(lineX, y, whiskerAngleColor);
                                }
                            }
                        }
                        //Between target line and right whisker point.
                        if (Integer.parseInt(whiskerAngleMap.get(i).get(3)) != 0) {
                            if (y <= targetLineY && y >= Integer.parseInt(whiskerAngleMap.get(i).get(3))) {
                                if (lineX == Integer.parseInt(whiskerAngleMap.get(i).get(1))) {
                                    ip.set(lineX, y, whiskerAngleColor);
                                }
                            }
                        }
                        lineX = lineX + 1;
                    }
                }
                y = y - 1;
            }
            if (i == frameThreshold) {
                break;
            }
        }
        // Show data results in a table.
        ResultsTable data = makeDataMatrix(noseLineCoordinateMap, headAngleMap, whiskerAngleMap, targetLineY, frameThreshold);
        data.show("jTrack Results");
        IJ.showStatus("");
        imageSequence.show();
    }

    public int findTargetLine(ImagePlus imageFrame, double percentage) {
        int xmax = imageFrame.getWidth();
        int ymax = imageFrame.getHeight();
        int y = ymax;
        int x;
        int targetLineThreshold = (int) round((xmax * percentage), 0);
        int edgePixels;

        // Find edges on the given frame, to emphasize the target line.
        IJ.run("Find Edges");
        IJ.run(imageFrame, "Make Binary", "method=Yen background=Dark calculate");

        ImageProcessor ip = imageFrame.getProcessor();
        // Goes up from the bottom to get the first Y coordinate containing a minimum percentage of white pixels. This is likely the target line.
        while (y > -1) {
            x = 0;
            edgePixels = 0;
            while (x < xmax) {
                if (ip.getPixelValue(x, y) == 255) {
                    edgePixels = edgePixels + 1;
                }
                x = x + 1;
            }
            if (edgePixels >= targetLineThreshold) {
                return y;
            }
            y = y - 1;
        }
        return 0;
    }

    public boolean checkSubjectDirection(ImagePlus erodedImage) {
        boolean flipImage = false;
        boolean firstHit = false;
        ImageStack erodedStack = erodedImage.getStack();
        int xmax = erodedImage.getWidth();
        int ymax = erodedImage.getHeight();
        // Go up from the bottom of the frame to check for any black areas. If the first hit is in the bottom half of the frame, the subject is moving up.
        // If the subject moves up, the image sequence should be flipped 180 degrees.
        for (int i = 1; i <= erodedStack.getSize(); i++) {
            ImageProcessor ip = erodedStack.getProcessor(i);
            int y = ymax;
            while (y > 0) {
                int x = 0;
                while (x < xmax) {
                    if (ip.getPixelValue(x, y) != 0) {
                        if (y > (erodedStack.getHeight() * 0.5)) {
                            flipImage = true;
                        }
                        firstHit = true;
                        break;
                    }
                    x = x + 1;
                }
                if (firstHit == true) {
                    break;
                }
                y = y - 1;
            }
            if (firstHit == true) {
                break;
            }
        }
        return flipImage;
    }

    public Map<Integer, ArrayList<Integer>> getNoseLineCoordinates(ImagePlus erodedImage, int targetLineY) {
        Map<Integer, ArrayList<Integer>> noseLineCoordinateMap = new HashMap<Integer, ArrayList<Integer>>();
        ImageStack erodedStack = erodedImage.getStack();
        ImageProcessor ip;
        int xmax = erodedImage.getWidth();
        int ymax = erodedImage.getHeight();
        int x;
        int y;
        boolean gotNoseLineInFrame;
        boolean crossedTarget = false;
        // Get coordinates for the nose tip in every frame.
        for (int i = 1; i <= erodedStack.getSize(); i++) {
            if (crossedTarget == true) {
                break;
            }
            ip = erodedStack.getProcessor(i);
            y = ymax;
            gotNoseLineInFrame = false;
            ArrayList<Integer> noseLineInformation = new ArrayList<Integer>();
            ArrayList<Integer> nosePixels = new ArrayList<Integer>();
            while (y > 0) {
                x = 0;
                while (x < xmax) {
                    if (ip.getPixelValue(x, y) != 0) {
                        nosePixels.add(x);
                        gotNoseLineInFrame = true;
                    }
                    x = x + 1;
                }
                if (gotNoseLineInFrame == true) {
                    int middlePixel = nosePixels.get((int) round(nosePixels.size() / 2, 0));
                    noseLineInformation.add(y);
                    noseLineInformation.add(middlePixel);
                    // Nose Line Information: Nose Tip Y, Middle Nose X
                    noseLineCoordinateMap.put(i, noseLineInformation);
                    if (y > targetLineY) {
                        crossedTarget = true;
                    }
                    break;
                }
                y = y - 1;
            }
            if (gotNoseLineInFrame == false) {
                noseLineInformation.add(0);
                noseLineInformation.add(0);
                noseLineCoordinateMap.put(i, noseLineInformation);
            }
        }
        return noseLineCoordinateMap;
    }

    public Integer findTargetCrossedFrame(Map<Integer, ArrayList<Integer>> noseLineCoordinateMap, ImagePlus erodedImage, int targetLineY) {
        // Find the frame where the nose tip has first hit or crossed the target line. Many processes will be stopped after reaching this frame.
        ImageStack erodedStack = erodedImage.getStack();
        int crossedTargetFrame = erodedStack.getSize();
        for (int i = 1; i <= erodedStack.getSize(); i++) {
            if (noseLineCoordinateMap.get(i).get(0) >= targetLineY) {
                crossedTargetFrame = i;
                break;
            }
        }
        return crossedTargetFrame;
    }

    public Map<Integer, ArrayList<ArrayList<Integer>>> getErodedOutlineCoordinates(Map<Integer, ArrayList<Integer>> noseLineCoordinateMap, ImagePlus erodedImage, int frameThreshold,
            double roiLength) {
        Map<Integer, ArrayList<ArrayList<Integer>>> erodedOutlineMap = new HashMap<Integer, ArrayList<ArrayList<Integer>>>();

        ImageStack erodedStack = erodedImage.getStack();
        int xmax = erodedImage.getWidth();
        int ymax = erodedImage.getHeight();
        // Gather outline coordinates on eroded images. These will be used in combination with a given range of distance to find the whiskers.
        for (int i = 1; i <= erodedStack.getSize(); i++) {
            IJ.showProgress(i, frameThreshold);
            ImageProcessor ip = erodedStack.getProcessor(i);
            int noseLine = noseLineCoordinateMap.get(i).get(0);
            int yTop = (int) (noseLine - round((ymax * roiLength), 0));
            int yBottom = (int) (noseLine + round((ymax * roiLength), 0));
            if (yTop < 0) {
                yTop = 0;
            }
            if (yBottom > ymax) {
                yBottom = ymax;
            }
            int y = yTop;
            ArrayList<ArrayList<Integer>> outlineCoordinates = new ArrayList<ArrayList<Integer>>();
            while (y <= yBottom) {
                int x = 0;
                int leftMost = xmax;
                int rightMost = 0;
                while (x < xmax) {
                    if (ip.getPixelValue(x, y) != 0) {
                        int blackDensityOutlineCount = findSurroundingPixelIntensity(x, y, ip);
                        if (blackDensityOutlineCount > 1 && blackDensityOutlineCount < 8) {
                            if (x < leftMost) {
                                leftMost = x;
                            }
                            if (x > rightMost) {
                                rightMost = x;
                            }
                        }
                    }
                    x = x + 1;
                }
                ArrayList<Integer> lineCoordinates = new ArrayList<Integer>();
                lineCoordinates.add(y);
                lineCoordinates.add(leftMost);
                lineCoordinates.add(rightMost);
                outlineCoordinates.add(lineCoordinates);
                y = y + 1;
            }
            erodedOutlineMap.put(i, outlineCoordinates);
            if (i == frameThreshold) {
                break;
            }
        }
        return erodedOutlineMap;
    }

    public Map<Integer, ArrayList<ArrayList<Integer>>> getOutlineCoordinates(ImagePlus differenceImage, int frameThreshold) {
        Map<Integer, ArrayList<ArrayList<Integer>>> outlineMap = new HashMap<Integer, ArrayList<ArrayList<Integer>>>();

        ImageStack differenceStack = differenceImage.getStack();
        int xmax = differenceImage.getWidth();
        int ymax = differenceImage.getHeight();
        ImageProcessor ip;
        // Get coordinates of detected outline of subject in the processed image sequence. These can be used to paint the outline in the final product.
        for (int i = 1; i <= differenceStack.getSize(); i++) {
            IJ.showProgress(i, frameThreshold);
            ip = differenceStack.getProcessor(i);
            int y = ymax;
            ArrayList<ArrayList<Integer>> outlineCoordinates = new ArrayList<ArrayList<Integer>>();
            while (y > -1) {
                int x = 0;
                while (x < xmax) {
                    if (ip.getPixelValue(x, y) != 0) {
                        int blackDensityOutlineCount = findSurroundingPixelIntensity(x, y, ip);
                        if (blackDensityOutlineCount > 1 && blackDensityOutlineCount < 8) {
                            ArrayList outlinePoint = new ArrayList();
                            outlinePoint.add(x);
                            outlinePoint.add(y);
                            outlineCoordinates.add(outlinePoint);
                        }
                    }
                    x = x + 1;
                }
                y = y - 1;
            }
            outlineMap.put(i, outlineCoordinates);
            if (i == frameThreshold) {
                break;
            }
        }
        return outlineMap;
    }

    public Map<Integer, ArrayList<String>> getHeadAngleCoordinates(Map<Integer, ArrayList<Integer>> noseLineCoordinateMap, ImagePlus erodedImage, int frameThreshold,
            double headAnglePercentage) {
        Map<Integer, ArrayList<String>> headAngleMap = new HashMap<Integer, ArrayList<String>>();
        ImageStack erodedStack = erodedImage.getStack();
        int xmax = erodedImage.getWidth();
        int ymax = erodedImage.getHeight();
        headAnglePercentage = headAnglePercentage * 0.01;
        // Get coordinates on both sides of the head equal distance from the nose tip. These can be used to determine head direction.
        for (int i = 1; i <= erodedStack.getSize(); i++) {
            IJ.showProgress(i, frameThreshold);
            ImageProcessor ip = erodedStack.getProcessor(i);
            int y = ymax;
            boolean checkAnglePixels = false;
            boolean checkLeftAngle = false;
            boolean checkRightAngle = false;
            int leftAngleY = 0;
            int rightAngleY = 0;
            int leftAngleX = 0;
            int rightAngleX = 0;
            if (noseLineCoordinateMap.get(i).get(0) == 0) {
                ArrayList<String> headAngleCoordinates = new ArrayList<String>();
                headAngleCoordinates.add(String.valueOf(leftAngleX));
                headAngleCoordinates.add(String.valueOf(rightAngleX));
                headAngleCoordinates.add(String.valueOf(leftAngleY));
                headAngleCoordinates.add(String.valueOf(rightAngleY));
                // Head Angle Coordinates: Left Cheek Angle X, Right Cheek Angle X, Left Cheek Angle Y, Right Cheek Angle Y.
                headAngleMap.put(i, headAngleCoordinates);
            } else {
                while (y >= 0) {
                    if (y == noseLineCoordinateMap.get(i).get(0)) {
                        checkAnglePixels = true;
                        checkLeftAngle = true;
                        checkRightAngle = true;
                        int middlePixel = noseLineCoordinateMap.get(i).get(1);
                        int middlePixelDeviation = (int) (xmax * headAnglePercentage);
                        leftAngleX = middlePixel - middlePixelDeviation;
                        if (leftAngleX < 0) {
                            leftAngleX = 0;
                        }
                        rightAngleX = middlePixel + middlePixelDeviation;
                        if (rightAngleX > xmax) {
                            rightAngleX = xmax;
                        }
                    }
                    if (checkAnglePixels == true) {
                        int x = 0;
                        while (x < xmax) {
                            if (checkLeftAngle == true) {
                                if (x == leftAngleX) {
                                    if (ip.getPixelValue(x, y) != 0) {
                                        leftAngleY = y;
                                        checkLeftAngle = false;
                                    } else if (y == 0) {
                                        leftAngleY = 0;
                                        checkLeftAngle = false;
                                    }
                                }
                            }
                            if (checkRightAngle == true) {
                                if (x == rightAngleX) {
                                    if (ip.getPixelValue(x, y) != 0) {
                                        rightAngleY = y;
                                        checkRightAngle = false;
                                    } else if (y == 0) {
                                        rightAngleY = 0;
                                        checkRightAngle = false;
                                    }
                                }
                            }
                            if (checkLeftAngle == false && checkRightAngle == false) {
                                checkAnglePixels = false;
                                ArrayList<String> headAngleCoordinates = new ArrayList<String>();
                                headAngleCoordinates.add(String.valueOf(leftAngleX));
                                headAngleCoordinates.add(String.valueOf(rightAngleX));
                                headAngleCoordinates.add(String.valueOf(leftAngleY));
                                headAngleCoordinates.add(String.valueOf(rightAngleY));
                                // Head Angle Coordinates: Left Cheek Angle X, Right Cheek Angle X, Left Cheek Angle Y, Right Cheek Angle Y.
                                headAngleMap.put(i, headAngleCoordinates);
                                break;
                            }
                            x = x + 1;
                        }
                    }
                    y = y - 1;
                }
            }
            if (i == frameThreshold) {
                break;
            }
        }
        return headAngleMap;
    }

    public Map<Integer, ArrayList<String>> getWhiskerAngleCoordinates(Map<Integer, ArrayList<Integer>> noseLineCoordinateMap, int targetLineY, ImagePlus cleanedImage,
            int frameThreshold, Map<Integer, ArrayList<ArrayList<Integer>>> erodedOutlineMap, int whiskerMinDistance, int whiskerMaxDistance, int whiskerMinThickness,
            int whiskerMaxThickness, double roiLength) {
        Map<Integer, ArrayList<String>> whiskerAngleMap = new HashMap<Integer, ArrayList<String>>();
        ImageStack imageStack = cleanedImage.getStack();
        int xmax = imageStack.getWidth();
        int ymax = imageStack.getHeight();
        // Get coordinates on both whiskers equal distance from the nose tip. These can be used to determine whisker position.
        for (int i = 1; i <= imageStack.getSize(); i++) {
            IJ.showProgress(i, frameThreshold);
            ArrayList<String> whiskerPixels = new ArrayList<String>();
            int noseLine = noseLineCoordinateMap.get(i).get(0);
            if (noseLine != 0) {
                ImageProcessor ip = imageStack.getProcessor(i);
                int yTop = (int) (noseLine - round((ymax * roiLength), 0));
                int yBottom = (int) (noseLine + round((ymax * roiLength), 0));
                if (yTop < 0) {
                    yTop = 0;
                }
                if (yBottom > ymax) {
                    yBottom = ymax;
                }
                int y = yTop;
                boolean checkLeftAngle = true;
                int leftCheckCount = 0;
                boolean checkRightAngle = true;
                int rightCheckCount = 0;

                int leftWhiskerX = 0;
                int leftWhiskerY = 0;
                int rightWhiskerX = 0;
                int rightWhiskerY = 0;

                while (y <= yBottom) {
                    // Count pixels vertically between a given range of width. If pixels are of a vertical density within the given parameters, it is a whisker.
                    // Find left whisker.
                    if (checkLeftAngle == true) {
                        int leftPixel = erodedOutlineMap.get(i).get(y - yTop).get(1);
                        if (leftPixel < whiskerMaxDistance) {
                            leftPixel = whiskerMaxDistance;
                        }
                        int distanceCount = whiskerMinDistance;
                        while (distanceCount <= whiskerMaxDistance) {
                            leftCheckCount = 0;
                            if (ip.getPixel((leftPixel - distanceCount), y) != 0) {
                                leftCheckCount = leftCheckCount + 1;
                                boolean checkUp = true;
                                int checkUpY = y - 1;
                                while (checkUp == true) {
                                    if (ip.getPixel((leftPixel - distanceCount), checkUpY) != 0) {
                                        leftCheckCount = leftCheckCount + 1;
                                        if (leftCheckCount > whiskerMaxThickness) {
                                            break;
                                        } else {
                                            checkUpY = checkUpY - 1;
                                        }
                                    } else {
                                        checkUp = false;
                                    }
                                }
                                boolean checkDown = true;
                                boolean firstDown = true;
                                int checkDownY = y + 1;
                                while (checkDown == true) {
                                    if (ip.getPixel((leftPixel - distanceCount), checkDownY) != 0) {
                                        leftCheckCount = leftCheckCount + 1;
                                        if (leftCheckCount > whiskerMaxThickness) {
                                            break;
                                        } else {
                                            checkDownY = checkDownY + 1;
                                        }
                                    } else {
                                        checkDown = false;
                                    }
                                }
                                if (leftCheckCount >= whiskerMinThickness && leftCheckCount <= whiskerMaxThickness) {
                                    checkLeftAngle = false;
                                    leftWhiskerX = leftPixel - distanceCount;
                                    if (firstDown == false) {
                                        leftWhiskerY = checkDownY;
                                    } else {
                                        leftWhiskerY = checkDownY - 1;
                                    }
                                    break;
                                }
                            }
                            distanceCount = distanceCount + 1;
                        }
                    }
                    // Find right whisker.
                    if (checkRightAngle == true) {
                        int rightPixel = erodedOutlineMap.get(i).get(y - yTop).get(2);
                        if (rightPixel > (xmax - whiskerMaxDistance)) {
                            rightPixel = xmax - whiskerMaxDistance;
                        }
                        int distanceCount = whiskerMinDistance;
                        while (distanceCount <= whiskerMaxDistance) {
                            rightCheckCount = 0;
                            if (ip.getPixel((rightPixel - distanceCount), y) != 0) {
                                rightCheckCount = rightCheckCount + 1;
                                boolean checkUp = true;
                                int checkUpY = y - 1;
                                while (checkUp == true) {
                                    if (ip.getPixel((rightPixel + distanceCount), checkUpY) != 0) {
                                        rightCheckCount = rightCheckCount + 1;
                                        if (rightCheckCount > whiskerMaxThickness) {
                                            break;
                                        } else {
                                            checkUpY = checkUpY - 1;
                                        }
                                    } else {
                                        checkUp = false;
                                    }
                                }
                                boolean checkDown = true;
                                boolean firstDown = true;
                                int checkDownY = y + 1;
                                while (checkDown == true) {
                                    if (ip.getPixel((rightPixel + distanceCount), checkDownY) != 0) {
                                        rightCheckCount = rightCheckCount + 1;
                                        if (rightCheckCount > whiskerMaxThickness) {
                                            break;
                                        } else {
                                            checkDownY = checkDownY + 1;
                                        }
                                    } else {
                                        checkDown = false;
                                    }
                                }
                                if (rightCheckCount >= whiskerMinThickness && rightCheckCount <= whiskerMaxThickness) {
                                    checkRightAngle = false;
                                    rightWhiskerX = rightPixel + distanceCount;
                                    if (firstDown == false) {
                                        rightWhiskerY = checkDownY;
                                    } else {
                                        rightWhiskerY = checkDownY - 1;
                                    }
                                    break;
                                }
                            }
                            distanceCount = distanceCount + 1;
                        }
                    }
                    if (checkLeftAngle == false && checkRightAngle == false) {
                        break;
                    }
                    y = y + 1;
                }
                whiskerPixels.add(Integer.toString(leftWhiskerX));
                whiskerPixels.add(Integer.toString(rightWhiskerX));
                whiskerPixels.add(Integer.toString(leftWhiskerY));
                whiskerPixels.add(Integer.toString(rightWhiskerY));
                // Whisker Angle Coordinates: Left Whisker Angle X, Right Whisker Angle X, Left Whisker Angle Y, Right Whisker Angle Y.
                whiskerAngleMap.put(i, whiskerPixels);
            } else {
                whiskerPixels.add("0");
                whiskerPixels.add("0");
                whiskerPixels.add("0");
                whiskerPixels.add("0");
                // Whisker Angle Coordinates: Left Whisker Angle X, Right Whisker Angle X, Left Whisker Angle Y, Right Whisker Angle Y.
                whiskerAngleMap.put(i, whiskerPixels);
            }
            if (i == frameThreshold) {
                break;
            }
        }
        return whiskerAngleMap;
    }

    // Old whisker detection. Still available in case it's desired.
//    public Map<Integer, ArrayList<String>> getWhiskerAngleCoordinatesBackUp(Map<Integer, ArrayList<Integer>> noseLineCoordinateMap, int targetLineY, ImagePlus cleanedImage,
//            int frameThreshold, double whiskerAnglePercentage) {
//        Map<Integer, ArrayList<String>> whiskerAngleMap = new HashMap<Integer, ArrayList<String>>();
//        ImageStack imageStack = cleanedImage.getStack();
//        ImageProcessor ip;
//        int xmax = imageStack.getWidth();
//        int ymax = imageStack.getHeight();
//        int x;
//        int y;
//        whiskerAnglePercentage = whiskerAnglePercentage * 0.01;
//        // Get coordinates on both whiskers equal distance from the nose tip. These can be used to determine whisker position.
//        for (int i = 1; i <= imageStack.getSize(); i++) {
//            IJ.showProgress(i, frameThreshold);
//            ip = imageStack.getProcessor(i);
//            y = ymax;
//            int leftAngleX = 0;
//            int rightAngleX = 0;
//            int leftAngleY = 0;
//            int rightAngleY = 0;
//            boolean leftHit = false;
//            boolean rightHit = false;
//            boolean frameDone = false;
//            boolean measure = false;
//
//            if (noseLineCoordinateMap.get(i).get(0) == 0) {
//                ArrayList<String> whiskerAngleCoordinates = new ArrayList<String>();
//                whiskerAngleCoordinates.add(String.valueOf(leftAngleX));
//                whiskerAngleCoordinates.add(String.valueOf(rightAngleX));
//                whiskerAngleCoordinates.add(String.valueOf(leftAngleY));
//                whiskerAngleCoordinates.add(String.valueOf(rightAngleY));
//                // Whisker Angle Coordinates: Left Whisker Angle X, Right Whisker Angle X, Left Whisker Angle Y, Right Whisker Angle Y.
//                whiskerAngleMap.put(i, whiskerAngleCoordinates);
//            } else {
//                while (y > 0 && frameDone == false) {
//                    x = 0;
//                    if (y == targetLineY) {
//                        measure = true;
//                        int nosePixelX = noseLineCoordinateMap.get(i).get(1);
//                        int middlePixelDeviation = (int) (xmax * whiskerAnglePercentage);
//                        leftAngleX = nosePixelX - middlePixelDeviation;
//                        if (leftAngleX < 0) {
//                            leftAngleX = 0;
//                        }
//                        rightAngleX = nosePixelX + middlePixelDeviation;
//                        if (rightAngleX > xmax) {
//                            rightAngleX = xmax;
//                        }
//                    }
//                    if (measure == true) {
//                        while (x < xmax) {
//                            if (leftHit == false && x == leftAngleX) {
//                                if (ip.getPixelValue(x, y) != 0) {
//                                    leftAngleY = y;
//                                    leftHit = true;
//                                }
//                            } else if (rightHit == false && x == rightAngleX) {
//                                if (ip.getPixelValue(x, y) != 0) {
//                                    rightAngleY = y;
//                                    rightHit = true;
//                                }
//                            } else if (leftHit == true && rightHit == true) {
//                                ArrayList<String> whiskerAngleCoordinates = new ArrayList<String>();
//                                whiskerAngleCoordinates.add(String.valueOf(leftAngleX));
//                                whiskerAngleCoordinates.add(String.valueOf(rightAngleX));
//                                whiskerAngleCoordinates.add(String.valueOf(leftAngleY));
//                                whiskerAngleCoordinates.add(String.valueOf(rightAngleY));
//                                // Whisker Angle Coordinates: Left Whisker Angle X, Right Whisker Angle X, Left Whisker Angle Y, Right Whisker Angle Y.
//                                whiskerAngleMap.put(i, whiskerAngleCoordinates);
//                                frameDone = true;
//                                break;
//                            }
//                            x = x + 1;
//                        }
//                    }
//                    y = y - 1;
//                }
//                if (frameDone == false) {
//                    ArrayList<String> whiskerAngleCoordinates = new ArrayList<String>();
//                    whiskerAngleCoordinates.add(String.valueOf(leftAngleX));
//                    whiskerAngleCoordinates.add(String.valueOf(rightAngleX));
//                    whiskerAngleCoordinates.add(String.valueOf(leftAngleY));
//                    whiskerAngleCoordinates.add(String.valueOf(rightAngleY));
//                    // Whisker Angle Coordinates: Left Whisker Angle X, Right Whisker Angle X, Left Whisker Angle Y, Right Whisker Angle Y.
//                    whiskerAngleMap.put(i, whiskerAngleCoordinates);
//                }
//            }
//            if (i == frameThreshold) {
//                break;
//            }
//        }
//        return whiskerAngleMap;
//    }
    public Map<Integer, ArrayList<String>> calculateAngles(Map<Integer, ArrayList<String>> angleMap, Map<Integer, ArrayList<Integer>> noseLineCoordinateMap,
            int frameThreshold, boolean whisker) {
        int frameCount = noseLineCoordinateMap.size();
        int i = 1;
        // Find the angles between either the cheeks or the whiskers and the tip of the nose.
        while (i <= frameCount) {
            int noseX = noseLineCoordinateMap.get(i).get(1);
            int noseY = noseLineCoordinateMap.get(i).get(0);
            int leftPointX = Integer.parseInt(angleMap.get(i).get(0));
            int rightPointX = Integer.parseInt(angleMap.get(i).get(1));
            int leftPointY = Integer.parseInt(angleMap.get(i).get(2));
            int rightPointY = Integer.parseInt(angleMap.get(i).get(3));
            float leftAngle = getAngle(leftPointX, leftPointY, noseX, noseY);
            float rightAngle = getAngle(rightPointX, rightPointY, noseX, noseY);

            ArrayList<String> angleMapData = angleMap.get(i);
            if (noseX == 0 && noseY == 0) {
                if (whisker == true) {
                    angleMapData.add("-999.0");
                    angleMapData.add("-999.0");
                } else {
                    angleMapData.add("-999.0");
                }
            } else {
                if (whisker == true) {
                    if (leftPointY == 0) {
                        angleMapData.add("-999.0");
                    } else if ((leftAngle + 180) > 180 && (leftAngle + 180) <= 360) {
                        angleMapData.add(String.valueOf(-(360 - (leftAngle + 180))));
                    } else {
                        angleMapData.add(String.valueOf(leftAngle + 180));
                    }
                    if (rightPointY == 0) {
                        angleMapData.add("-999.0");
                    } else if (abs(rightAngle) > 180 && abs(rightAngle) <= 360) {
                        angleMapData.add(String.valueOf((360 - abs(rightAngle)) * -1));
                    } else {
                        angleMapData.add(String.valueOf(abs(rightAngle)));
                    }
                } else {
                    if (leftPointY == 0 || rightPointY == 0) {
                        angleMapData.add("-999.0");
                    } else {
                        float sumAngle = (leftAngle + 180) - abs(rightAngle);
                        angleMapData.add(String.valueOf(sumAngle));
                    }
                }
            }
            angleMap.put(i, angleMapData);

            if (i == frameThreshold) {
                break;
            }

            i = i + 1;
        }
        return angleMap;
    }

    public float getAngle(int pointX, int pointY, int noseX, int noseY) {
        // Calculates the angle.
        return (float) Math.toDegrees(Math.atan2(pointY - noseY, pointX - noseX));
    }

    public ImagePlus enhanceImage(ImagePlus differenceImage, Map<Integer, ArrayList<Integer>> noseLineCoordinateMap, int frameThreshold, double roiLength, boolean dilateROI) {
        ImageStack imageStack = differenceImage.getStack();
        int xmax = differenceImage.getWidth();
        int ymax = differenceImage.getHeight();
        // Edit the image sequence frame by frame through optional dilation of the head area and erosion of the rest of the frame.
        for (int i = 1; i <= imageStack.getSize(); i++) {
            IJ.showProgress(i, frameThreshold);
            differenceImage.setSlice(i);
            int noseLine = noseLineCoordinateMap.get(i).get(0);
            int roiTop = (int) (noseLine - round((ymax * roiLength), 0));
            int roiBottom = (int) (noseLine + round((ymax * roiLength), 0));
            if (roiTop < 0) {
                roiTop = 0;
            }
            if (roiBottom > ymax) {
                roiBottom = ymax;
            }
            //Dilate area around the nose to make whiskers more dense for tracking.
            if (dilateROI == true) {
                differenceImage.setRoi(0, roiTop, xmax, (roiBottom - roiTop));
                IJ.run(differenceImage, "Dilate", "slice");
            }

            //Erode areas above and below the dilated area to remove uninteresting noise.
            differenceImage.setRoi(0, 0, xmax, roiTop);
            IJ.run(differenceImage, "Erode", "slice");
            differenceImage.setRoi(0, roiBottom, xmax, ymax);
            IJ.run(differenceImage, "Erode", "slice");

            if (i == frameThreshold) {
                break;
            }
        }
        differenceImage.deleteRoi();
        return differenceImage;
    }

    public ImagePlus removeNoise(ImagePlus differenceImage, Map<Integer, ArrayList<Integer>> noseLineCoordinateMap, int frameThreshold, int noiseThreshold,
            double roiLength) {
        ImageStack imageStack = differenceImage.getStack();
        int xmax = differenceImage.getWidth();
        int ymax = differenceImage.getHeight();
        // Remove noise in the dilated head area based on a given sensitivity (pixel count). If an area of connected non-background pixels is equal or lower than this amount,
        // these pixels will be converted to background.
        for (int i = 1; i <= imageStack.getSize(); i++) {
            IJ.showProgress(i, frameThreshold);
            ImageProcessor ip = imageStack.getProcessor(i);
            int noseLine = noseLineCoordinateMap.get(i).get(0);
            int roiTop = (int) (noseLine - round((ymax * roiLength), 0));
            int roiBottom = (int) (noseLine + round((ymax * roiLength), 0));
            if (roiTop < 0) {
                roiTop = 0;
            }
            if (roiBottom > ymax) {
                roiBottom = ymax;
            }
            int y = roiTop;
            while (y < roiBottom) {
                int x = 0;
                while (x < xmax) {
                    if (ip.getPixelValue(x, y) != 0) {
                        ArrayList<String> coordinateList = findAreaSize(ip, x, y, noiseThreshold);
                        if (coordinateList.size() <= noiseThreshold) {
                            int coordinateNr = 0;
                            while (coordinateNr < coordinateList.size()) {
                                String coordinates = coordinateList.get(0);
                                int xCoordinate = Integer.parseInt(coordinates.substring(0, (coordinates.indexOf("-") - 1)));
                                int yCoordinate = Integer.parseInt(coordinates.substring((coordinates.indexOf("-") + 2), coordinates.length()));
                                ip.set(xCoordinate, yCoordinate, 0);
                                coordinateNr = coordinateNr + 1;
                            }
                        }
                    }
                    x = x + 1;
                }
                y = y + 1;
            }

            if (i == frameThreshold) {
                break;
            }
        }
        return differenceImage;
    }

    public ArrayList<String> findAreaSize(ImageProcessor ip, int x, int y, int noiseThreshold) {
        ArrayList<String> connectedCoordinateList = new ArrayList<String>();
        ArrayList<String> checkedList = new ArrayList<String>();
        ArrayList<String> newConnectionsList = new ArrayList<String>();
        connectedCoordinateList.add(Integer.toString(x).concat(" - ").concat(Integer.toString(y)));
        checkedList.add(Integer.toString(x).concat(" - ").concat(Integer.toString(y)));
        // Check all the surrounding pixels for their colour. If they are not background pixels, they get added to a list. If this list exceeds a certain size,
        // it is no longer considered noise, and the process gets stopped.
        boolean finishSearch = false;
        while (finishSearch == false) {
            if (checkedList.contains(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y - 1))) == false) {
                if (ip.getPixelValue(x - 1, y - 1) != 0) {
                    newConnectionsList.add(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y - 1)));
                    connectedCoordinateList.add(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y - 1)));
                    checkedList.add(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y - 1)));
                } else {
                    checkedList.add(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y - 1)));
                }
            }
            if (checkedList.contains(Integer.toString(x).concat(" - ").concat(Integer.toString(y - 1))) == false) {
                if (ip.getPixelValue(x, y - 1) != 0) {
                    newConnectionsList.add(Integer.toString(x).concat(" - ").concat(Integer.toString(y - 1)));
                    connectedCoordinateList.add(Integer.toString(x).concat(" - ").concat(Integer.toString(y - 1)));
                    checkedList.add(Integer.toString(x).concat(" - ").concat(Integer.toString(y - 1)));
                } else {
                    checkedList.add(Integer.toString(x).concat(" - ").concat(Integer.toString(y - 1)));
                }
            }
            if (checkedList.contains(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y - 1))) == false) {
                if (ip.getPixelValue(x + 1, y - 1) != 0) {
                    newConnectionsList.add(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y - 1)));
                    connectedCoordinateList.add(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y - 1)));
                    checkedList.add(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y - 1)));
                } else {
                    checkedList.add(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y - 1)));
                }
            }
            if (checkedList.contains(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y))) == false) {
                if (ip.getPixelValue(x - 1, y) != 0) {
                    newConnectionsList.add(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y)));
                    connectedCoordinateList.add(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y)));
                    checkedList.add(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y)));
                } else {
                    checkedList.add(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y)));
                }
            }
            if (checkedList.contains(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y))) == false) {
                if (ip.getPixelValue(x + 1, y) != 0) {
                    newConnectionsList.add(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y)));
                    connectedCoordinateList.add(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y)));
                    checkedList.add(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y)));
                } else {
                    checkedList.add(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y)));
                }
            }
            if (checkedList.contains(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y + 1))) == false) {
                if (ip.getPixelValue(x - 1, y + 1) != 0) {
                    newConnectionsList.add(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y + 1)));
                    connectedCoordinateList.add(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y + 1)));
                    checkedList.add(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y + 1)));
                } else {
                    checkedList.add(Integer.toString(x - 1).concat(" - ").concat(Integer.toString(y + 1)));
                }
            }
            if (checkedList.contains(Integer.toString(x).concat(" - ").concat(Integer.toString(y + 1))) == false) {
                if (ip.getPixelValue(x, y + 1) != 0) {
                    newConnectionsList.add(Integer.toString(x).concat(" - ").concat(Integer.toString(y + 1)));
                    connectedCoordinateList.add(Integer.toString(x).concat(" - ").concat(Integer.toString(y + 1)));
                    checkedList.add(Integer.toString(x).concat(" - ").concat(Integer.toString(y + 1)));
                } else {
                    checkedList.add(Integer.toString(x).concat(" - ").concat(Integer.toString(y + 1)));
                }
            }
            if (checkedList.contains(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y + 1))) == false) {
                if (ip.getPixelValue(x + 1, y + 1) != 0) {
                    newConnectionsList.add(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y + 1)));
                    connectedCoordinateList.add(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y + 1)));
                    checkedList.add(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y + 1)));
                } else {
                    checkedList.add(Integer.toString(x + 1).concat(" - ").concat(Integer.toString(y + 1)));
                }
            }

            if (connectedCoordinateList.size() > noiseThreshold) {
                break;
            }

            if (newConnectionsList.isEmpty() == false) {
                String coordinate = newConnectionsList.get(0);
                x = Integer.parseInt(coordinate.substring(0, (coordinate.indexOf("-") - 1)));
                y = Integer.parseInt(coordinate.substring((coordinate.indexOf("-") + 2), coordinate.length()));
                newConnectionsList.remove(0);
            } else {
                break;
            }
        }
        return connectedCoordinateList;
    }

    public int findSurroundingPixelIntensity(int x, int y, ImageProcessor ip) {
        int density = 0;

        // Find the amount of non-background pixels around a given pixel.
        if (ip.getPixelValue(x - 1, y - 1) != 0) {
            density = density + 1;
        }
        if (ip.getPixelValue(x, y - 1) != 0) {
            density = density + 1;
        }
        if (ip.getPixelValue(x + 1, y - 1) != 0) {
            density = density + 1;
        }
        if (ip.getPixelValue(x - 1, y) != 0) {
            density = density + 1;
        }
        if (ip.getPixelValue(x + 1, y) != 0) {
            density = density + 1;
        }
        if (ip.getPixelValue(x - 1, y + 1) != 0) {
            density = density + 1;
        }
        if (ip.getPixelValue(x, y + 1) != 0) {
            density = density + 1;
        }
        if (ip.getPixelValue(x + 1, y + 1) != 0) {
            density = density + 1;
        }

        return density;
    }

    public ResultsTable makeDataMatrix(Map<Integer, ArrayList<Integer>> noseLineCoordinateMap, Map<Integer, ArrayList<String>> headAngleMap,
            Map<Integer, ArrayList<String>> whiskerAngleMap, int targetLineY, int frameThreshold) {
        int frameCount = noseLineCoordinateMap.size();
        int i = 1;
        ResultsTable data = new ResultsTable();

        data.disableRowLabels();
        // Add data to a table per frame.
        while (i <= frameCount) {
            data.setValue("Frame", i - 1, i);

            int noseTipX = noseLineCoordinateMap.get(i).get(1);
            int noseTipY = noseLineCoordinateMap.get(i).get(0);
            if (noseTipX == 0 && noseTipY == 0) {
                noseTipX = -999;
                noseTipY = -999;
            }

            data.setValue("Nose Tip X", i - 1, noseTipX);
            data.setValue("Nose Tip Y", i - 1, noseTipY);
            data.setValue("Nose Distance", i - 1, (targetLineY - noseLineCoordinateMap.get(i).get(0)));
            data.setValue("Head Angle", i - 1, (headAngleMap.get(i).get(4)).replace(".", ","));

            int leftWhiskerX = Integer.parseInt(whiskerAngleMap.get(i).get(0));
            int leftWhiskerY = Integer.parseInt(whiskerAngleMap.get(i).get(2));
            if (leftWhiskerX == 0 && leftWhiskerY == 0) {
                leftWhiskerX = -999;
                leftWhiskerY = -999;
            }

            data.setValue("Left Whisker X", i - 1, leftWhiskerX);
            data.setValue("Left Whisker Y", i - 1, leftWhiskerY);
            data.setValue("Left Whisker Angle", i - 1, (whiskerAngleMap.get(i).get(4)).replace(".", ","));

            int rightWhiskerX = Integer.parseInt(whiskerAngleMap.get(i).get(1));
            int rightWhiskerY = Integer.parseInt(whiskerAngleMap.get(i).get(3));
            if (rightWhiskerX == 0 && rightWhiskerY == 0) {
                rightWhiskerX = -999;
                rightWhiskerY = -999;
            }

            data.setValue("Right Whisker X", i - 1, rightWhiskerX);
            data.setValue("Right Whisker Y", i - 1, rightWhiskerY);
            data.setValue("Right Whisker Angle", i - 1, (whiskerAngleMap.get(i).get(5)).replace(".", ","));
            data.setValue("Target Line Y", i - 1, targetLineY);

            if (i == frameThreshold) {
                break;
            }
            i = i + 1;
        }
        return data;
    }

    private double round(double value, int places) {
        // Round a given number up to a given number of digits after the period.
        if (places < 0) {
            throw new IllegalArgumentException();
        }
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static void main(final String... args) {
        // Used to run a version of ImageJ for testing purposes. Has thus far not caused any issues when included in the plugin jar file.
        new ij.ImageJ();

        ij.Menus.getMenuBar().getMenu(5).add("jTrack");
        ij.Menus.getMenuBar().getMenu(5).getItem(ij.Menus.getMenuBar().getMenu(5).getItemCount() - 1).addActionListener(new java.awt.event.ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                new JTrack_plugin().run("");
            }
        });
    }
}

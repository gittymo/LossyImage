package com.plus.mevanspn.imgcomp;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;

public class App 
{
    public static void main( String[] args )
    {
        // Crappy command line argument parsing
        if (args.length < 5) {
            System.out.println("Usage: java -jar imgcomp-1.0.jar <ybits> <cbits> <block size> <input file> <output file>");
            return;
        }
        
        // Get the number of bits to use for each colour channel and the block size from the command line arguments.
        int YBitFieldLength = getNumberInRangeFromArg(args[0], 1, 8, 4);
        int CrCbBitFieldLength = getNumberInRangeFromArg(args[1], 1, 8, 4);
        int CrCbBlockSize = getNumberInRangeFromArg(args[2], 2, 8, 8);

        // Get the input and output file names from the command line arguments.
        String inputFile = args[3];
        String outputFile = args[4];

        // Make sure blocksize is a multiple of 2, if not make it so.
        if (CrCbBlockSize % 2 != 0) CrCbBlockSize++;

        // Read the image and save a copy of it complete with compression artifacts.
        try {
            // Read the image from the source file.
            BufferedImage image = ImageIO.read(new File(inputFile));

            // Define a couple of constants to prevent unnecessary function calls in loops.
            final int IMAGE_WIDTH = image.getWidth();
            final int IMAGE_HEIGHT = image.getHeight();

            // Get the RGB values of the image pixels.
            int[] rgbValues = image.getRGB(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, null, 0, IMAGE_WIDTH); 
            
            // Create a new image with the same dimensions as the original.
            BufferedImage compressedImage = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
            
            // Create an array to store the compressed image's RGB values.
            int[] compressedRGBValues = new int[rgbValues.length];
            
            // Loop through the original image's RGB values and stored compressed equivalents in the new image.
            
            // First, work out the maximum and minimum luminance (Y) value and totals of all the Cr and Cb values in each block of 
            // blockSize x blockSize pixels.
            for (int y = 0; y < IMAGE_HEIGHT; y += CrCbBlockSize) {
                for (int x = 0; x < IMAGE_WIDTH; x += CrCbBlockSize) {
                    long totalCr = 0, totalCb = 0;
                    for (int yy = y; yy < IMAGE_HEIGHT && yy < y + CrCbBlockSize; yy++) {
                        for (int xx = x, offset = (IMAGE_WIDTH * yy) + x; xx < IMAGE_WIDTH && xx < x + CrCbBlockSize; xx++, offset++) {
                            // Get the YCrCb values for the pixel.
                            int[] YCrCb = getYCrCb(rgbValues[offset]);
                            // Add the Cr and Cb values to the totals.
                            totalCr += YCrCb[1];
                            totalCb += YCrCb[2];
                        }
                    }

                    // Work out the average Cr and Cb value, which will be used for every pixel in the 
                    // compressed block. We need to make sure we're dividing by the correct number of 
                    // pixels in the block, otherwise we'll end up with weird miscoloured blocks on the
                    // right and bottom of the image.
                    int xBlockSize = Math.min(CrCbBlockSize, IMAGE_WIDTH - x);
                    int yBlockSize = Math.min(CrCbBlockSize, IMAGE_HEIGHT - y);
                    int averageCr = (int) (totalCr / (xBlockSize * yBlockSize));
                    int averageCb = (int) (totalCb / (xBlockSize * yBlockSize));
                    // Now go through the block again and use the averaged Cr and Cb values and average of
                    // every two vertical luminance (Y) values as the compressed image data's YCrCb pixel values.
                    for (int yy = y; yy < IMAGE_HEIGHT && yy < y + CrCbBlockSize; yy += 2) {
                        for (int xx = x; xx < IMAGE_WIDTH && xx < x + CrCbBlockSize; xx++) {
                            // Get the total of every two vertical Y values in the block.
                            int totalY = 0;
                            for (int yyy = yy, offset = (IMAGE_WIDTH * yyy) + xx; yyy < IMAGE_HEIGHT && yyy < yy + 2; yyy++, offset += IMAGE_WIDTH) {
                                int Y = getLuminance(rgbValues[offset]);
                                totalY += Y;
                            }
                            // Work out the average Y value.
                            int averageY = totalY / 2;

                            // Now go through the block again and write the decompressed RGB values to the compressedRGBValues array.
                            for (int yyy = yy, offset = (IMAGE_WIDTH * yyy) + xx; yyy < IMAGE_HEIGHT && yyy < yy + 2; yyy++, offset += IMAGE_WIDTH) {   
                                int Y = getUnpackedValue(getPackedValue(averageY, YBitFieldLength), YBitFieldLength);
                                int Cr = getUnpackedValue(getPackedValue(averageCr, CrCbBitFieldLength), CrCbBitFieldLength);
                                int Cb = getUnpackedValue(getPackedValue(averageCb, CrCbBitFieldLength), CrCbBitFieldLength);
                                compressedRGBValues[offset] = getARGB(Y, Cr, Cb);
                            }
                        }
                    }
                }
            }
            // Write the array of decompressed RGB pixel values to the new image.
            compressedImage.setRGB(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, compressedRGBValues, 0, IMAGE_WIDTH);

            // Save the compressed image to disk.  The demo assumes the output file will have a JPEG file extension as we're 
            // outputting a JPEG image, but this could be changed to any other image format supported by ImageIO.
            ImageIO.write(compressedImage, "jpg", new File(outputFile));

            // Print some stats
            // Compressed size in bytes is equal to:
            // (total_blocks * ((CrBits + CbBits) / 8)) + (((image_width * image_height / 2) * YBits) / 8)
            final int WIDTH_IN_BLOCKS = (int) Math.ceil((double) IMAGE_WIDTH / CrCbBlockSize);
            final int HEIGHT_IN_BLOCKS = (int) Math.ceil((double) IMAGE_HEIGHT / CrCbBlockSize);
            final int CRCB_DATA_SIZE = WIDTH_IN_BLOCKS * HEIGHT_IN_BLOCKS * (CrCbBitFieldLength + CrCbBitFieldLength) / 8;
            final int Y_DATA_SIZE = (IMAGE_WIDTH * IMAGE_HEIGHT / 2) * YBitFieldLength / 8;
            final int TOTAL_COMPRESSED_DATA_SIZE = CRCB_DATA_SIZE + Y_DATA_SIZE;
            final int SOURCE_DATA_SIZE = rgbValues.length * 3;
            System.out.println("Image size is " + image.getWidth() + " x " + image.getHeight() + " pixels.");
            System.out.println("Using a compression block size of " + CrCbBlockSize + " x " + CrCbBlockSize + " pixels.");
            System.out.println("Using " + YBitFieldLength + " bits for Y values and " + CrCbBitFieldLength + " bits for Cr and Cb values.");
            System.out.println("Original size: " + SOURCE_DATA_SIZE + " bytes");
            System.out.println("Compressed size: " + TOTAL_COMPRESSED_DATA_SIZE + " bytes");
            System.out.printf("Compression ratio: %.2f%%\n",(float)TOTAL_COMPRESSED_DATA_SIZE * 100 / SOURCE_DATA_SIZE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getUnpackedValue(int value, int valueBits) {
        return value << (8 - valueBits);
    }

    public static int getPackedValue(int value, int requiredBits) {
        return value >> (8 - requiredBits);
    }

    public static int[] getYCrCb(int argb) {
        int red = (argb & 0x00FF0000) >> 16;
        int green = (argb & 0x0000FF00) >> 8;
        int blue = argb & 0xFF;
        int y = (int) (0.299 * red + 0.587 * green + 0.114 * blue);
        int cr = (int) ((red - y) * 0.713 + 128);
        int cb = (int) ((blue - y) * 0.564 + 128);
        return new int[] { y, cr, cb };
    }

    public static int getARGB(int Y, int Cr, int Cb) {
        int red = (int) (Y + 1.403 * (Cr - 128));
        int green = (int) (Y - 0.344 * (Cb - 128) - 0.714 * (Cr - 128));
        int blue = (int) (Y + 1.770 * (Cb - 128));

        red = red < 0 ? 0 : red > 255 ? 255 : red;
        green = green < 0 ? 0 : green > 255 ? 255 : green;
        blue = blue < 0 ? 0 : blue > 255 ? 255 : blue;
        return red << 16 | green << 8 | blue;
    }

    public static int getLuminance(int argb) {
        int red = (argb & 0x00FF0000) >> 16;
        int green = (argb & 0x0000FF00) >> 8;
        int blue = argb & 0xFF;
        return (int) (0.299 * red + 0.587 * green + 0.114 * blue);
    }

    public static int getNumberInRangeFromArg(String argString, int minimumValue, int maximumValue, int defaultValue) {
        int value = 0;
        try {
            value = Integer.parseInt(argString);
            if (value < minimumValue) value = minimumValue;
            if (value > maximumValue) value = maximumValue;
        } catch (Exception e) {
            value = defaultValue;
        }
        return value;
    }
}

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
        final int Y_BITFIELD_LENGTH = getNumberInRangeFromArg(args[0], 1, 8, 4, 1);
        final int CRCB_BITFIELD_LENGTH = getNumberInRangeFromArg(args[1], 1, 8, 4,1);
        final int CRCB_BLOCK_SIZE = getNumberInRangeFromArg(args[2], 2, 8, 8, 2);

        // Get the input and output file names from the command line arguments.
        final String INPUT_FILE = args[3];
        final String OUTPUT_FILE = args[4];

        // Read the image and save a copy of it complete with compression artifacts.
        try {
            // Read the image from the source file.
            final BufferedImage SOURCE_IMAGE = ImageIO.read(new File(INPUT_FILE));

            // Define a couple of constants to prevent unnecessary function calls in loops.
            final int IMAGE_WIDTH = SOURCE_IMAGE.getWidth();
            final int IMAGE_HEIGHT = SOURCE_IMAGE.getHeight();

            // Get the RGB values of the image pixels.
            int[] rgbValues = SOURCE_IMAGE.getRGB(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, null, 0, IMAGE_WIDTH); 
            
            // Create a new image with the same dimensions as the original.
            BufferedImage compressedImage = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
            
            // Create an array to store the compressed image's RGB values.
            int[] compressedRGBValues = new int[rgbValues.length];
            
            // Loop through the original image's RGB values and stored compressed equivalents in the new image.
            
            // First, work out the maximum and minimum luminance (Y) value and totals of all the Cr and Cb values in each block of 
            // blockSize x blockSize pixels.
            for (int y = 0; y < IMAGE_HEIGHT; y += CRCB_BLOCK_SIZE) {
                for (int x = 0; x < IMAGE_WIDTH; x += CRCB_BLOCK_SIZE) {
                    long totalCr = 0, totalCb = 0;
                    for (int yy = y; yy < IMAGE_HEIGHT && yy < y + CRCB_BLOCK_SIZE; yy++) {
                        for (int xx = x, offset = (IMAGE_WIDTH * yy) + x; xx < IMAGE_WIDTH && xx < x + CRCB_BLOCK_SIZE; xx++, offset++) {
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
                    int xBlockSize = Math.min(CRCB_BLOCK_SIZE, IMAGE_WIDTH - x);
                    int yBlockSize = Math.min(CRCB_BLOCK_SIZE, IMAGE_HEIGHT - y);
                    int averageCr = (int) (totalCr / (xBlockSize * yBlockSize));
                    int averageCb = (int) (totalCb / (xBlockSize * yBlockSize));
                    
                    for (int yy = y; yy < IMAGE_HEIGHT && yy < y + CRCB_BLOCK_SIZE; yy++) {
                        for (int xx = x, offset = (IMAGE_WIDTH * yy) + x; xx < IMAGE_WIDTH && xx < x + CRCB_BLOCK_SIZE; xx++, offset++) {
                            // Get the Y value for the pixel.
                            int Y = getLuminance(rgbValues[offset]);
                            int uY = getUnpackedValue(getPackedValue(Y, Y_BITFIELD_LENGTH), Y_BITFIELD_LENGTH);
                            int uCr = getUnpackedValue(getPackedValue(averageCr, CRCB_BITFIELD_LENGTH), CRCB_BITFIELD_LENGTH);
                            int uCb = getUnpackedValue(getPackedValue(averageCb, CRCB_BITFIELD_LENGTH), CRCB_BITFIELD_LENGTH);
                            compressedRGBValues[offset] = getARGB(uY, uCr, uCb);
                        }
                    }
                }
            }
            // Write the array of decompressed RGB pixel values to the new image.
            compressedImage.setRGB(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, compressedRGBValues, 0, IMAGE_WIDTH);

            // Save the compressed image to disk.  The demo assumes the output file will have a JPEG file extension as we're 
            // outputting a JPEG image, but this could be changed to any other image format supported by ImageIO.
            ImageIO.write(compressedImage, "jpg", new File(OUTPUT_FILE));

            // Print some stats
            // Compressed size in bytes is equal to:
            // (total_blocks * ((CrBits + CbBits) / 8)) + (((image_width * image_height) * YBits) / 8)
            final int WIDTH_IN_BLOCKS = (int) Math.ceil((double) IMAGE_WIDTH / CRCB_BLOCK_SIZE);
            final int HEIGHT_IN_BLOCKS = (int) Math.ceil((double) IMAGE_HEIGHT / CRCB_BLOCK_SIZE);
            final int CRCB_DATA_SIZE = WIDTH_IN_BLOCKS * HEIGHT_IN_BLOCKS * (CRCB_BITFIELD_LENGTH + CRCB_BITFIELD_LENGTH) / 8;
            final int Y_DATA_SIZE = (IMAGE_WIDTH * IMAGE_HEIGHT) * Y_BITFIELD_LENGTH / 8;
            final int TOTAL_COMPRESSED_DATA_SIZE = CRCB_DATA_SIZE + Y_DATA_SIZE;
            final int SOURCE_DATA_SIZE = rgbValues.length * 3;
            System.out.println("Image size is " + SOURCE_IMAGE.getWidth() + " x " + SOURCE_IMAGE.getHeight() + " pixels.");
            System.out.println("Using a compression block size of " + CRCB_BLOCK_SIZE + " x " + CRCB_BLOCK_SIZE + " pixels.");
            System.out.println("Using " + Y_BITFIELD_LENGTH + " bits for Y values and " + CRCB_BITFIELD_LENGTH + " bits for Cr and Cb values.");
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

    public static int getNumberInRangeFromArg(String argString, int minimumValue, int maximumValue, int defaultValue, int divisor) {
        int value = 0;
        try {
            value = Integer.parseInt(argString);
            if (value < minimumValue) value = minimumValue;
            if (value > maximumValue) value = maximumValue;
        } catch (Exception e) {
            value = defaultValue >= minimumValue && defaultValue <= maximumValue ? defaultValue : minimumValue + ((maximumValue - minimumValue) / 2);
        }
        while (value % divisor != 0 && value < maximumValue) {
            value++;
        }
        return value;
    }
}

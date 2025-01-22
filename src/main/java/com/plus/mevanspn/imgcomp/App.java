package com.plus.mevanspn.imgcomp;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;

/**
 * Hello world!
 *
 */
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
            // Read the image
            BufferedImage image = ImageIO.read(new File(inputFile));
            // Get the RGB values of the image pixels.
            int[] rgbValues = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()); 
            // Create a new image with the same dimensions as the original.
            BufferedImage compressedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            // Loop through the original image's RGB values and stored compressed equivalents in the new image.

            // First, work out the maximum and minimum luminance (Y) value and totals of all the Cr and Cb values in each block of 
            // blockSize x blockSize pixels.
            for (int y = 0; y < image.getHeight(); y += CrCbBlockSize) {
                for (int x = 0; x < image.getWidth(); x += CrCbBlockSize) {
                    long totalCr = 0, totalCb = 0;
                    for (int yy = y; yy < image.getHeight() && yy < y + CrCbBlockSize; yy++) {
                        for (int xx = x; xx < image.getWidth() && xx < x + CrCbBlockSize; xx++) {
                            // Get the pixel offset in the image's RGB values array.
                            int offset = yy * image.getWidth() + xx;
                            // Get the YCrCb values for the pixel.
                            int[] YCrCb = getYCrCb(rgbValues[offset]);
                            // Add the Cr and Cb values to the totals.
                            totalCr += YCrCb[1];
                            totalCb += YCrCb[2];
                        }
                    }

                    // Work out the average Cr and Cb value, which will be used for every pixel in the 
                    // compressed block.
                    int averageCr = (int) (totalCr / (CrCbBlockSize * CrCbBlockSize));
                    int averageCb = (int) (totalCb / (CrCbBlockSize * CrCbBlockSize));
                    // Now go through the block again and use the averaged Cr and Cb values and average of
                    // every two vertical luminance (Y) values as the compressed image data's YCrCb pixel values.
                    for (int yy = y; yy < image.getHeight() && yy < y + CrCbBlockSize; yy += 2) {
                        for (int xx = x; xx < image.getWidth() && xx < x + CrCbBlockSize; xx++) {
                            int totalY = 0;
                            for (int yyy = yy; yyy < image.getHeight() && yyy < yy + 2; yyy++) {
                                int offset = yyy * image.getWidth() + xx;
                                int Y = getLuminance(rgbValues[offset]);
                                totalY += Y;
                            }
                            int averageY = totalY / 2;
                            for (int yyy = yy; yyy < image.getHeight() && yyy < yy + 2; yyy++) {
                                // For the purposes of this demo, we're compressing (getPackedValue) and decompressing 
                                // (getUnpackedValue) the Y, Cr and Cb values and then writing the decompressed values to the
                                // output image.  In a real-world application, the packed values would be written to a file or
                                // transmitted over a network and then unpacked and decompressed at the other end.
                                int Y = getUnpackedValue(getPackedValue(averageY, YBitFieldLength), YBitFieldLength);
                                int Cr = getUnpackedValue(getPackedValue(averageCr, CrCbBitFieldLength), CrCbBitFieldLength);
                                int Cb = getUnpackedValue(getPackedValue(averageCb, CrCbBitFieldLength), CrCbBitFieldLength);
                                compressedImage.setRGB(xx, yyy, getARGB(Y, Cr, Cb));
                            }
                        }
                    }
                }
            }
            // Apologies for the all nested loops.  I wanted to inline the YCrCb calculations for performance reasons,
            // but it obviously makes the code less readable and harder to debug. There are better ways to do this (including)
            // improving performance by decompressing all values to an ARGB array and then writing the ARGB values to the
            // output image in one go, but I wanted to keep the code simple and easy to understand for this demo.

            // Save the compressed image to disk.  The demo assumes the output file will have a JPEG file extension as we're 
            // outputting a JPEG image, but this could be changed to any other image format supported by ImageIO.
            ImageIO.write(compressedImage, "jpg", new File(outputFile));

            // Print some stats
            // Compressed size in bytes is equal to:
            // (total_blocks * ((CrBits + CbBits) / 8)) + (((image_width * image_height / 2) * YBits) / 8)
            int widthInBlocks = (int) Math.ceil((double) image.getWidth() / CrCbBlockSize);
            int heightInBlocks = (int) Math.ceil((double) image.getHeight() / CrCbBlockSize);
            int crcbDataSize = widthInBlocks * heightInBlocks * (CrCbBitFieldLength + CrCbBitFieldLength) / 8;
            int ydataSize = (image.getWidth() * image.getHeight() / 2) * YBitFieldLength / 8;
            int compressedSize = crcbDataSize + ydataSize;
            int originalSize = rgbValues.length * 3;
            System.out.println("Image size is " + image.getWidth() + " x " + image.getHeight() + " pixels.");
            System.out.println("Using a compression block size of " + CrCbBlockSize + " x " + CrCbBlockSize + " pixels.");
            System.out.println("Using " + YBitFieldLength + " bits for Y values and" + CrCbBitFieldLength + " bits for Cr and Cb values.");
            System.out.println("Original size: " + originalSize + " bytes");
            System.out.println("Compressed size: " + compressedSize + " bytes");
            System.out.printf("Compression ratio: %.2f%%\n",(float)compressedSize * 100 / originalSize);
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

    public static int getNumberInRangeFromArg(String arg, int min, int max, int defaultValue) {
        int value = 0;
        try {
            value = Integer.parseInt(arg);
            if (value < min) value = min;
            if (value > max) value = max;
        } catch (Exception e) {
            value = defaultValue;
        }
        return value;
    }
}

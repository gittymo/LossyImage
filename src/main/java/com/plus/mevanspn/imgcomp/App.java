package com.plus.mevanspn.imgcomp;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;

public class App {
    public static void main(String[] args) {
        // Crappy command line argument parsing
        if (args.length < 5) {
            System.out.println(
                    "Usage: java -jar imgcomp-1.0.jar <ybits> <cbits> <block size> <input file> <output file>");
            return;
        }

        // Get the number of bits to use for each colour channel and the block size from
        // the command line arguments.
        final int Y_BITFIELD_LENGTH = getNumberInRangeFromArg(args[0], 1, 8, 4, 1);
        final int CRCB_BITFIELD_LENGTH = getNumberInRangeFromArg(args[1], 1, 8, 4, 1);
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

            // Create a new image with the same dimensions as the original, which will hold
            // a copy of the decompressed image.
            BufferedImage compressedImage = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);

            int totalCompressedSize = 0;
            for (int y = 0; y < IMAGE_HEIGHT; y += CRCB_BLOCK_SIZE) {
                for (int x = 0; x < IMAGE_WIDTH; x += CRCB_BLOCK_SIZE) {
                    EncodedBlock encodedBlock = new EncodedBlock(SOURCE_IMAGE, x, y, CRCB_BLOCK_SIZE, Y_BITFIELD_LENGTH,
                            CRCB_BITFIELD_LENGTH);
                    totalCompressedSize += encodedBlock.getCompressedSize();
                    encodedBlock.toImage(compressedImage);
                }
            }

            // Save the compressed image to disk. The demo assumes the output file will have
            // a JPEG file extension as we're
            // outputting a JPEG image, but this could be changed to any other image format
            // supported by ImageIO.
            ImageIO.write(compressedImage, "jpg", new File(OUTPUT_FILE));

            // Print some stats
            final int TOTAL_COMPRESSED_DATA_SIZE = totalCompressedSize;
            final int SOURCE_DATA_SIZE = IMAGE_WIDTH * IMAGE_HEIGHT * 3;
            System.out.println(
                    "Image size is " + SOURCE_IMAGE.getWidth() + " x " + SOURCE_IMAGE.getHeight() + " pixels.");
            System.out.println(
                    "Using a compression block size of " + CRCB_BLOCK_SIZE + " x " + CRCB_BLOCK_SIZE + " pixels.");
            System.out.println("Using " + Y_BITFIELD_LENGTH + " bits for Y values and " + CRCB_BITFIELD_LENGTH
                    + " bits for Cr and Cb values.");
            System.out.println("Original size: " + SOURCE_DATA_SIZE + " bytes");
            System.out.println("Compressed size: " + TOTAL_COMPRESSED_DATA_SIZE + " bytes");
            System.out.printf("Compression ratio: %.2f%%\n",
                    (float) TOTAL_COMPRESSED_DATA_SIZE * 100 / SOURCE_DATA_SIZE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getNumberInRangeFromArg(String argString, int minimumValue, int maximumValue, int defaultValue,
            int divisor) {
        int value = 0;
        try {
            value = Integer.parseInt(argString);
            if (value < minimumValue)
                value = minimumValue;
            if (value > maximumValue)
                value = maximumValue;
        } catch (Exception e) {
            value = defaultValue >= minimumValue && defaultValue <= maximumValue ? defaultValue
                    : minimumValue + ((maximumValue - minimumValue) / 2);
        }
        while (value % divisor != 0 && value < maximumValue) {
            value++;
        }
        return value;
    }
}

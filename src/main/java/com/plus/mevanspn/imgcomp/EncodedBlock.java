package com.plus.mevanspn.imgcomp;

import java.awt.image.BufferedImage;

final public class EncodedBlock {
	public EncodedBlock(BufferedImage image, int x, int y, int blockSize, int compressedYBitFieldLength,
			int CrCbBitFieldLength) {
		// Get the image width and height.
		final int IMAGE_WIDTH = image.getWidth();
		final int IMAGE_HEIGHT = image.getHeight();

		// Set the x and y co-ordinates for the block.
		this.xpos = x;
		this.ypos = y;

		// Set the bitfield lengths for the Y and CrCb values.
		this.compressedYBitFieldLength = compressedYBitFieldLength;
		this.CrCbBitFieldLength = CrCbBitFieldLength;

		// Determine the actual block dimensions depending on the starting x and y
		// co-ordinates in the image.
		this.blockWidth = Math.min(blockSize, IMAGE_WIDTH - x);
		this.blockHeight = Math.min(blockSize, IMAGE_HEIGHT - y);

		// Get the ARGB values from the chosen block of the image.
		int[] argbValues = image.getRGB(x, y, this.blockWidth, this.blockHeight, null, 0, this.blockWidth);

		// Create an array of ints to hold the Y values for each pixel in the block.
		this.YValues = new int[this.blockWidth * this.blockHeight];

		// Get the minimum and maximum Y values for the block and the total Cr and Cb
		// values.
		long totalCr = 0, totalCb = 0;
		int minY = 255, maxY = 0;
		int offset = 0;
		for (int py = 0; py < this.blockHeight; py++) {
			for (int px = 0; px < this.blockWidth; px++, offset++) {
				int red = (argbValues[offset] >> 16) & 0xff;
				int green = (argbValues[offset] >> 8) & 0xff;
				int blue = argbValues[offset] & 0xff;

				int Y = this.YValues[offset] = (int) (0.299 * red + 0.587 * green + 0.114 * blue);
				if (this.YValues[offset] < minY)
					minY = this.YValues[offset];
				if (this.YValues[offset] > maxY)
					maxY = this.YValues[offset];

				totalCr += (int) ((red - Y) * 0.713 + 128);
				totalCb += (int) ((blue - Y) * 0.564 + 128);
			}
		}

		// Calculate the maximum delta Y value for the block.
		this.maxDeltaValue = maxY - minY;

		// Calculate the base Y value for the block.
		this.baseYValue = minY;

		// Take the minimum Y value from all the Y values in the block.
		for (int i = 0; i < this.YValues.length; i++)
			this.YValues[i] -= this.baseYValue;

		// Determine the bitfield length required to store the Y delta values.
		this.YDeltaBitfieldLength = 0;
		while ((1 << this.YDeltaBitfieldLength) < this.maxDeltaValue)
			this.YDeltaBitfieldLength++;

		// If the Y delta bitfield length is greater than the compressed Y bitfield
		// length, we need to
		// shift the stored Y values to the right to make them the right size.
		if (this.YDeltaBitfieldLength > this.compressedYBitFieldLength) {
			int shift = this.YDeltaBitfieldLength - this.compressedYBitFieldLength;
			for (int i = 0; i < this.YValues.length; i++)
				this.YValues[i] >>= shift;
		}

		// Calculate the average Cr and Cb values for the block.
		this.averageCr = (int) (totalCr / (this.blockWidth * this.blockHeight)) >> (8 - CrCbBitFieldLength);
		this.averageCb = (int) (totalCb / (this.blockWidth * this.blockHeight)) >> (8 - CrCbBitFieldLength);
	}

	public void toImage(BufferedImage image) {
		// Create an array of ARGB values from the average Cr and Cb values and the
		// stored Y values.
		int[] argbValues = new int[this.blockWidth * this.blockHeight];
		int offset = 0;
		for (int py = 0; py < this.blockHeight; py++) {
			for (int px = 0; px < this.blockWidth; px++, offset++) {
				int Y = this.YValues[offset];
				if (this.YDeltaBitfieldLength > this.compressedYBitFieldLength)
					Y <<= this.YDeltaBitfieldLength - this.compressedYBitFieldLength;
				Y += this.baseYValue;
				int Cr = this.averageCr << (8 - this.CrCbBitFieldLength);
				int Cb = this.averageCb << (8 - this.CrCbBitFieldLength);
				int red = (int) (Y + 1.403 * (Cr - 128));
				int green = (int) (Y - 0.344 * (Cb - 128) - 0.714 * (Cr - 128));
				int blue = (int) (Y + 1.770 * (Cb - 128));
				red = red < 0 ? 0 : red > 255 ? 255 : red;
				green = green < 0 ? 0 : green > 255 ? 255 : green;
				blue = blue < 0 ? 0 : blue > 255 ? 255 : blue;
				argbValues[offset] = red << 16 | green << 8 | blue;
			}
		}

		// Write the ARGB values to the image.
		image.setRGB(this.xpos, this.ypos, this.blockWidth, this.blockHeight, argbValues, 0, this.blockWidth);
	}

	public int getCompressedSize() {
		// Base length of four bytes is derived from:
		// 1. Two bytes for the average Cr and Cb values.
		// 2. One byte to hold the base Y value.
		// 3. One byte to hold the Y delta values bit shift.
		int length = 4;
		if (this.maxDeltaValue > 0)
			length += Math.ceil((this.YValues.length * this.compressedYBitFieldLength) / 8.0);
		return length;
	}

	private int averageCr, averageCb;
	private int baseYValue = 0, maxDeltaValue = 0;
	private int YDeltaBitfieldLength;
	private int compressedYBitFieldLength;
	private int CrCbBitFieldLength;
	private int[] YValues;
	private int xpos, ypos;
	private int blockWidth, blockHeight;
}

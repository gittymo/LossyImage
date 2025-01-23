package com.plus.mevanspn;

import java.util.ArrayList;

final public class BitStream {
	public BitStream() {

	}

	public void write(byte value, byte bitCount) {
		for (int i = 0; i < bitCount; i++) {
			if (bitIndex == 0)
				data.add((byte) 0);
			data.set(data.size() - 1, (byte) (data.get(data.size() - 1) | ((value >> i) & 1) << bitIndex));
			bitIndex = (bitIndex + 1) % 8;
		}
	}

	public void write(byte value) {
		write(value, (byte) 8);
	}

	public Byte[] toArray() {
		return data.toArray(new Byte[data.size()]);
	}

	public Byte[] RLEEncode() {
		// Return a byte array containing the RLE encoded block data.
		Byte[] rawEncodedData = toArray();
		ArrayList<Byte> encodedData = new ArrayList<Byte>();
		int runLength = 1, notRunLength = 0;
		byte lastByte = rawEncodedData[0];
		for (int i = 1; i < rawEncodedData.length; i++) {
			if (rawEncodedData[i] == lastByte) {
				if (notRunLength > 0) {
					encodedData.add((byte) 0); // Indicates non-RLE block starting.
					encodedData.add((byte) notRunLength); // Add the length of the non-RLE block.
					// Add the non-RLE block data.
					notRunLength--;
					for (int j = i - notRunLength - 1; j < i; j++)
						encodedData.add(rawEncodedData[j]);
					notRunLength = 0;
					runLength = 1;
				}
				runLength++;
				if (runLength == 255) {
					encodedData.add((byte) 1); // Indicates RLE block starting.
					encodedData.add((byte) runLength);
					encodedData.add(lastByte);
					runLength = 0;
				}
			} else {
				if (runLength > 1) {
					encodedData.add((byte) 1); // Indicates RLE block starting.
					encodedData.add((byte) runLength);
					encodedData.add(lastByte);
					notRunLength = 0;
				}
				notRunLength++;
				if (notRunLength == 255) {
					encodedData.add((byte) 0); // Indicates non-RLE block starting.
					encodedData.add((byte) notRunLength); // Add the length of the non-RLE block.
					// Add the non-RLE block data.
					for (int j = i - notRunLength; j < i; j++)
						encodedData.add(rawEncodedData[j]);
					notRunLength = 0;
				}
			}

			lastByte = rawEncodedData[i];
		}
		if (runLength > 1) {
			encodedData.add((byte) 1); // Indicates RLE block starting.
			encodedData.add((byte) runLength);
			encodedData.add(lastByte);
		} else if (notRunLength > 0) {
			encodedData.add((byte) 0); // Indicates non-RLE block starting.
			encodedData.add((byte) notRunLength); // Add the length of the non-RLE block.
			// Add the non-RLE block data.
			for (int j = rawEncodedData.length - notRunLength; j < rawEncodedData.length; j++)
				encodedData.add(rawEncodedData[j]);
		}
		return encodedData.toArray(new Byte[encodedData.size()]);
	}

	private ArrayList<Byte> data = new ArrayList<Byte>();
	private int bitIndex = 0;
}

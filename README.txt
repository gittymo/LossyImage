Quick And Dirty Demo Of Lossy Image Compression
-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

Just a small executable JAR that shows off some very rudimentary lossy image Compression
by converting RGB values to YCrCb (luminance and chrominance) values which are more 
readily able to be used in lossy applications such a photo and video storage/transmission.

Not a great algorithm by all means, both in terms of performance or image quality.  There 
are various things that can be done to improve both (such as recording luminance delta 
values instead of absolute values, which reduces banding even at lower Y bit field sizes).

To build just run:
mvn package

To execute, you should find the JAR file in the /target directory, so executing:
java -jar target/imgcomp-1.0.jar 6 5 6 images/test.jpg images/output656.jpg

would run the application, compressing the image test.jpg in the images folder and
would put the output in the image output656.jpg in the images folder.

The three values before the input and output filepaths are:
<Luminance bit field length> - This must be a value between 1 and 8 (in the example this is the first 6)
<Chrominance bit field length> - This must be a value between 1 and 8 (in the example this is 5)
<Chrominance block size> - The dimensions of the square block of pixels used for obtain the average 
chrominance value for the block (this is the last 6 in the example) and must have be a value between 2 and 8.

Have a play around with the luminance and chrominance bit field lengths and the block size values and 
see how different values affect compression ratio and the image quality.

NOTE:  Usually, after lossy compression, lossless compression algorithms are applied to the further
shrink the file size.  I haven't applied any of these techniques.  Also, video compression uses 
many other tricks such as moving window and rotoscoping which aren't in this demo (and obviously no 
sound compression either!)

I will eventually add lossless compression to this demo, along with other refinements (such as the 
aforementioned luminance delta encoding).
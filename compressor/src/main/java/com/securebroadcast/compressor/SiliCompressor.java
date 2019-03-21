package com.securebroadcast.compressor;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.securebroadcast.compressor.videocompression.MediaController;

import java.io.File;
import java.net.URISyntaxException;
public class SiliCompressor {

    private static final String LOG_TAG = SiliCompressor.class.getSimpleName();
    public static String videoCompressionPath;

    static volatile SiliCompressor singleton = null;
    private static Context mContext;
    public static final String FILE_PROVIDER_AUTHORITY = "com.iceteck.silicompressor.provider";

    public SiliCompressor(Context context) {
        mContext = context;
    }

    // initialise the class and set the context
    public static SiliCompressor with(Context context) {
        if (singleton == null) {
            synchronized (SiliCompressor.class) {
                if (singleton == null) {
                    singleton = new Builder(context).build();
                }
            }
        }
        return singleton;

    }

    public String compressVideo(String videoFilePath, String destinationDir) throws URISyntaxException {
        return compressVideo(videoFilePath, destinationDir, 0, 0, 0);
    }

    /**
     * Perform background video compression. Make sure the videofileUri and destinationUri are valid
     * resources because this method does not account for missing directories hence your converted file
     * could be in an unknown location
     *
     * @param videoFilePath  source path for the video file
     * @param destinationDir destination directory where converted file should be saved
     * @param outWidth       the target width of the compressed video or 0 to use default width
     * @param outHeight      the target height of the compressed video or 0 to use default height
     * @param bitrate        the target bitrate of the compressed video or 0 to user default bitrate
     * @return The Path of the compressed video file
     */
    public String compressVideo(String videoFilePath, String destinationDir, int outWidth, int outHeight, int bitrate) throws URISyntaxException {
        boolean isconverted = MediaController.Companion.getInstance().convertVideo(videoFilePath, new File(destinationDir), 0, 0, 0);
        if (isconverted) {
            Log.v(LOG_TAG, "Video Conversion Complete");
        } else {
            Log.v(LOG_TAG, "Video conversion in progress");
        }

        return MediaController.Companion.getCachedFile().getPath();

    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        final float totalPixels = width * height;
        final float totalReqPixelsCap = reqWidth * reqHeight * 2;
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++;
        }

        return inSampleSize;
    }

    public static class Builder {

        private final Context context;
        public Builder(Context context) {
            if (context == null) {
                throw new IllegalArgumentException("Context must not be null.");
            }
            this.context = context.getApplicationContext();
        }
        public SiliCompressor build() {
            Context context = this.context;

            return new SiliCompressor(context);
        }
    }
}

package com.securebroadcast.compressor

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log

import com.securebroadcast.compressor.videocompression.MediaController

import java.io.File
import java.net.URISyntaxException

class SiliCompressor(context: Context) {

    init {
        mContext = context
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
    @Throws(URISyntaxException::class)
    @JvmOverloads
    fun compressVideo(videoFilePath: String, destinationDir: String, outWidth: Int = 0, outHeight: Int = 0, bitrate: Int = 0): String {
        val isconverted = MediaController.instance.convertVideo(videoFilePath, File(destinationDir), 0, 0, 0)
        if (isconverted) {
            Log.v(LOG_TAG, "Video Conversion Complete")
        } else {
            Log.v(LOG_TAG, "Video conversion in progress")
        }

        return MediaController.cachedFile.path

    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
        }
        val totalPixels = (width * height).toFloat()
        val totalReqPixelsCap = (reqWidth * reqHeight * 2).toFloat()
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++
        }

        return inSampleSize
    }

    class Builder(context: Context?) {

        private val context: Context

        init {
            if (context == null) {
                throw IllegalArgumentException("Context must not be null.")
            }
            this.context = context.applicationContext
        }

        fun build(): SiliCompressor {
            val context = this.context

            return SiliCompressor(context)
        }
    }

    companion object {

        private val LOG_TAG = SiliCompressor::class.java.simpleName
        var videoCompressionPath: String? = null

        @Volatile
        internal var singleton: SiliCompressor? = null
        private lateinit var mContext: Context
        val FILE_PROVIDER_AUTHORITY = "com.iceteck.silicompressor.provider"

        // initialise the class and set the context
        fun with(context: Context): SiliCompressor? {
            if (singleton == null) {
                synchronized(SiliCompressor::class.java) {
                    if (singleton == null) {
                        singleton = Builder(context).build()
                    }
                }
            }
            return singleton

        }
    }
}

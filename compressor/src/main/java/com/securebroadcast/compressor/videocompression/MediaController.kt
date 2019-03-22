package com.securebroadcast.compressor.videocompression

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("NewApi")
class MediaController {
    lateinit var path: String
    private var videoConvertFirstWrite = true

    private fun didWriteData(last: Boolean, error: Boolean) {
        val firstWrite = videoConvertFirstWrite
        if (firstWrite) {
            videoConvertFirstWrite = false
        }
    }

    class VideoConvertRunnable private constructor(private val videoPath: String, private val destDirectory: File) : Runnable {

        override fun run() {
            MediaController.instance.convertVideo(videoPath, destDirectory)
        }

        companion object {

            fun runConversion(videoPath: String, dest: File) {
                Thread(Runnable {
                    try {
                        val wrapper = VideoConvertRunnable(videoPath, dest)
                        val th = Thread(wrapper, "VideoConvertRunnable")
                        th.start()
                        th.join()
                    } catch (e: Exception) {
                        Log.e("tmessages", e.message)
                    }
                }).start()
            }
        }
    }

    /**
     * Background conversion for queueing tasks
     * @param path source file to compress
     * @param dest destination directory to put result
     */

    fun scheduleVideoConvert(path: String, dest: File) {
        startVideoConvertFromQueue(path, dest)
    }

    private fun startVideoConvertFromQueue(path: String, dest: File) {
        VideoConvertRunnable.runConversion(path, dest)
    }

    @TargetApi(16)
    @Throws(Exception::class)
    private fun readAndWriteTrack(extractor: MediaExtractor?, mediaMuxer: MP4Builder?, info: MediaCodec.BufferInfo, start: Long, end: Long, file: File, isAudio: Boolean): Long {
        val trackIndex = selectTrack(extractor!!, isAudio)
        if (trackIndex >= 0) {
            extractor.selectTrack(trackIndex)
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val muxerTrackIndex = mediaMuxer!!.addTrack(trackFormat, isAudio)
            val maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            var inputDone = false
            if (start > 0) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }
            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            var startTime: Long = -1

            while (!inputDone) {

                var eof = false
                val index = extractor.sampleTrackIndex
                if (index == trackIndex) {
                    info.size = extractor.readSampleData(buffer, 0)

                    if (info.size < 0) {
                        info.size = 0
                        eof = true
                    } else {
                        info.presentationTimeUs = extractor.sampleTime
                        if (start > 0 && startTime == -1L) {
                            startTime = info.presentationTimeUs
                        }
                        if (end < 0 || info.presentationTimeUs < end) {
                            info.offset = 0
                            info.flags = extractor.sampleFlags
                            if (mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info, isAudio)) {
                                // didWriteData(messageObject, file, false, false);
                            }
                            extractor.advance()
                        } else {
                            eof = true
                        }
                    }
                } else if (index == -1) {
                    eof = true
                }
                if (eof) {
                    inputDone = true
                }
            }

            extractor.unselectTrack(trackIndex)
            return startTime
        }
        return -1
    }

    @TargetApi(16)
    private fun selectTrack(extractor: MediaExtractor, audio: Boolean): Int {
        val numTracks = extractor.trackCount
        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i
                }
            }
        }
        return -5
    }

    /**
     * Perform the actual video compression. Processes the frames and does the magic
     * Width, height and bitrate are now default
     * @param sourcePath the source uri for the file as per
     * @param destDir the destination directory where compressed video is eventually saved
     * @return
     */
    fun convertVideo(sourcePath: String, destDir: File): Boolean {
        return convertVideo(sourcePath, destDir, 0, 0, 0)
    }

    /**
     * Perform the actual video compression. Processes the frames and does the magic
     * @param sourcePath the source uri for the file as per
     * @param destDir the destination directory where compressed video is eventually saved
     * @param outWidth the target width of the converted video, 0 is default
     * @param outHeight the target height of the converted video, 0 is default
     * @param outBitrate the target bitrate of the converted video, 0 is default
     * @return
     */
    @TargetApi(16)
    fun convertVideo(sourcePath: String, destDir: File, outWidth: Int, outHeight: Int, outBitrate: Int): Boolean {
        this.path = sourcePath

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

        val startTime: Long = -1
        val endTime: Long = -1

        var resultWidth = if (outWidth > 0) outWidth else DEFAULT_VIDEO_WIDTH
        var resultHeight = if (outHeight > 0) outHeight else DEFAULT_VIDEO_HEIGHT

        var rotationValue = Integer.valueOf(rotation)
        val originalWidth = Integer.valueOf(width)
        val originalHeight = Integer.valueOf(height)

        val bitrate = if (outBitrate > 0) outBitrate else DEFAULT_VIDEO_BITRATE
        var rotateRender = 0

        val cacheFile = File(destDir,
                "VIDEO_" + SimpleDateFormat("ddMMyyyy_HHmmss", Locale.UK).format(Date()) + ".mp4"
        )

        if (rotationValue == 90) {
            val temp = resultHeight
            resultHeight = resultWidth
            resultWidth = temp
            rotationValue = 0
            rotateRender = 270
        } else if (rotationValue == 180) {
            rotateRender = 180
            rotationValue = 0
        } else if (rotationValue == 270) {
            val temp = resultHeight
            resultHeight = resultWidth
            resultWidth = temp
            rotationValue = 0
            rotateRender = 90
        }

        val inputFile = File(path)
        if (!inputFile.canRead()) {
            didWriteData(true, true)
            return false
        }

        videoConvertFirstWrite = true
        var error = false
        var videoStartTime = startTime

        val time = System.currentTimeMillis()

        if (resultWidth != 0 && resultHeight != 0) {
            var mediaMuxer: MP4Builder? = null
            var extractor: MediaExtractor? = null

            try {
                val info = MediaCodec.BufferInfo()
                val movie = Mp4Movie()
                movie.cacheFile = cacheFile
                movie.setRotation(rotationValue)
                movie.setSize(resultWidth, resultHeight)
                mediaMuxer = MP4Builder().createMovie(movie)
                extractor = MediaExtractor()
                extractor.setDataSource(inputFile.toString())

                if (resultWidth != originalWidth || resultHeight != originalHeight) {
                    val videoIndex: Int
                    videoIndex = selectTrack(extractor, false)

                    if (videoIndex >= 0) {
                        var decoder: MediaCodec? = null
                        var encoder: MediaCodec? = null
                        var inputSurface: InputSurface? = null
                        var outputSurface: OutputSurface? = null

                        try {
                            var videoTime: Long = -1
                            var outputDone = false
                            var inputDone = false
                            var decoderDone = false
                            var swapUV = 0
                            var videoTrackIndex = -5

                            val colorFormat: Int
                            var processorType = PROCESSOR_TYPE_OTHER
                            val manufacturer = Build.MANUFACTURER.toLowerCase()
                            if (Build.VERSION.SDK_INT < 18) {
                                val codecInfo = selectCodec(MIME_TYPE)
                                colorFormat = selectColorFormat(codecInfo!!, MIME_TYPE)
                                if (colorFormat == 0) {
                                    throw RuntimeException("no supported color format")
                                }
                                val codecName = codecInfo.name
                                if (codecName.contains("OMX.qcom.")) {
                                    processorType = PROCESSOR_TYPE_QCOM
                                    if (Build.VERSION.SDK_INT == 16) {
                                        if (manufacturer == "lge" || manufacturer == "nokia") {
                                            swapUV = 1
                                        }
                                    }
                                } else if (codecName.contains("OMX.Intel.")) {
                                    processorType = PROCESSOR_TYPE_INTEL
                                } else if (codecName == "OMX.MTK.VIDEO.ENCODER.AVC") {
                                    processorType = PROCESSOR_TYPE_MTK
                                } else if (codecName == "OMX.SEC.AVC.Encoder") {
                                    processorType = PROCESSOR_TYPE_SEC
                                    swapUV = 1
                                } else if (codecName == "OMX.TI.DUCATI1.VIDEO.H264E") {
                                    processorType = PROCESSOR_TYPE_TI
                                }
                                Log.e("tmessages", "codec = " + codecInfo.name + " manufacturer = " + manufacturer + "device = " + Build.MODEL)
                            } else {
                                colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                            }
                            Log.e("tmessages", "colorFormat = $colorFormat")

                            var resultHeightAligned = resultHeight
                            var padding = 0
                            var bufferSize = resultWidth * resultHeight * 3 / 2
                            if (processorType == PROCESSOR_TYPE_OTHER) {
                                if (resultHeight % 16 != 0) {
                                    resultHeightAligned += 16 - resultHeight % 16
                                    padding = resultWidth * (resultHeightAligned - resultHeight)
                                    bufferSize += padding * 5 / 4
                                }
                            } else if (processorType == PROCESSOR_TYPE_QCOM) {
                                if (manufacturer.toLowerCase() != "lge") {
                                    val uvoffset = resultWidth * resultHeight + 2047 and 2047.inv()
                                    padding = uvoffset - resultWidth * resultHeight
                                    bufferSize += padding
                                }
                            } else if (processorType == PROCESSOR_TYPE_TI) {
                                //resultHeightAligned = 368;
                                //bufferSize = resultWidth * resultHeightAligned * 3 / 2;
                                //resultHeightAligned += (16 - (resultHeight % 16));
                                //padding = resultWidth * (resultHeightAligned - resultHeight);
                                //bufferSize += padding * 5 / 4;
                            } else if (processorType == PROCESSOR_TYPE_MTK) {
                                if (manufacturer == "baidu") {
                                    resultHeightAligned += 16 - resultHeight % 16
                                    padding = resultWidth * (resultHeightAligned - resultHeight)
                                    bufferSize += padding * 5 / 4
                                }
                            }

                            extractor.selectTrack(videoIndex)
                            if (startTime > 0) {
                                extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                            } else {
                                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                            }
                            val inputFormat = extractor.getTrackFormat(videoIndex)

                            val outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight)
                            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, if (bitrate != 0) bitrate else 5000000)
                            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25)
                            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)
                            if (Build.VERSION.SDK_INT < 18) {
                                outputFormat.setInteger("stride", resultWidth + 32)
                                outputFormat.setInteger("slice-height", resultHeight)
                            }

                            encoder = MediaCodec.createEncoderByType(MIME_TYPE)
                            encoder!!.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                            if (Build.VERSION.SDK_INT >= 18) {
                                inputSurface = InputSurface(encoder.createInputSurface())
                                inputSurface.makeCurrent()
                            }
                            encoder.start()

                            decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME))
                            if (Build.VERSION.SDK_INT >= 18) {
                                outputSurface = OutputSurface()
                            } else {
                                outputSurface = OutputSurface(resultWidth, resultHeight, rotateRender)
                            }
                            decoder!!.configure(inputFormat, outputSurface.surface, null, 0)
                            decoder.start()

                            val TIMEOUT_USEC = 2500
                            var decoderInputBuffers: Array<ByteBuffer>? = null
                            var encoderOutputBuffers: Array<ByteBuffer>? = null
                            var encoderInputBuffers: Array<ByteBuffer>? = null
                            if (Build.VERSION.SDK_INT < 21) {
                                decoderInputBuffers = decoder.inputBuffers
                                encoderOutputBuffers = encoder.outputBuffers
                                if (Build.VERSION.SDK_INT < 18) {
                                    encoderInputBuffers = encoder.inputBuffers
                                }
                            }

                            while (!outputDone) {
                                if (!inputDone) {
                                    var eof = false
                                    val index = extractor.sampleTrackIndex
                                    if (index == videoIndex) {
                                        val inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                                        if (inputBufIndex >= 0) {
                                            val inputBuf: ByteBuffer?
                                            if (Build.VERSION.SDK_INT < 21) {
                                                inputBuf = decoderInputBuffers!![inputBufIndex]
                                            } else {
                                                inputBuf = decoder.getInputBuffer(inputBufIndex)
                                            }
                                            val chunkSize = extractor.readSampleData(inputBuf!!, 0)
                                            if (chunkSize < 0) {
                                                decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                                inputDone = true
                                            } else {
                                                decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.sampleTime, 0)
                                                extractor.advance()
                                            }
                                        }
                                    } else if (index == -1) {
                                        eof = true
                                    }
                                    if (eof) {
                                        val inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                                        if (inputBufIndex >= 0) {
                                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                            inputDone = true
                                        }
                                    }
                                }

                                var decoderOutputAvailable = !decoderDone
                                var encoderOutputAvailable = true
                                while (decoderOutputAvailable || encoderOutputAvailable) {
                                    val encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC.toLong())
                                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                        encoderOutputAvailable = false
                                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                        if (Build.VERSION.SDK_INT < 21) {
                                            encoderOutputBuffers = encoder.outputBuffers
                                        }
                                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                        val newFormat = encoder.outputFormat
                                        if (videoTrackIndex == -5) {
                                            videoTrackIndex = mediaMuxer!!.addTrack(newFormat, false)
                                        }
                                    } else if (encoderStatus < 0) {
                                        throw RuntimeException("unexpected result from encoder.dequeueOutputBuffer: $encoderStatus")
                                    } else {
                                        val encodedData: ByteBuffer?
                                        if (Build.VERSION.SDK_INT < 21) {
                                            encodedData = encoderOutputBuffers!![encoderStatus]
                                        } else {
                                            encodedData = encoder.getOutputBuffer(encoderStatus)
                                        }
                                        if (encodedData == null) {
                                            throw RuntimeException("encoderOutputBuffer $encoderStatus was null")
                                        }
                                        if (info.size > 1) {
                                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                                if (mediaMuxer!!.writeSampleData(videoTrackIndex, encodedData, info, false)) {
                                                    didWriteData(false, false)
                                                }
                                            } else if (videoTrackIndex == -5) {
                                                val csd = ByteArray(info.size)
                                                encodedData.limit(info.offset + info.size)
                                                encodedData.position(info.offset)
                                                encodedData.get(csd)
                                                var sps: ByteBuffer? = null
                                                var pps: ByteBuffer? = null
                                                for (a in info.size - 1 downTo 0) {
                                                    if (a > 3) {
                                                        if (csd[a].toInt() == 1 && csd[a - 1].toInt() == 0 && csd[a - 2].toInt() == 0 && csd[a - 3].toInt() == 0) {
                                                            sps = ByteBuffer.allocate(a - 3)
                                                            pps = ByteBuffer.allocate(info.size - (a - 3))
                                                            sps!!.put(csd, 0, a - 3).position(0)
                                                            pps!!.put(csd, a - 3, info.size - (a - 3)).position(0)
                                                            break
                                                        }
                                                    } else {
                                                        break
                                                    }
                                                }

                                                val newFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight)
                                                if (sps != null && pps != null) {
                                                    newFormat.setByteBuffer("csd-0", sps)
                                                    newFormat.setByteBuffer("csd-1", pps)
                                                }
                                                videoTrackIndex = mediaMuxer!!.addTrack(newFormat, false)
                                            }
                                        }
                                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                        encoder.releaseOutputBuffer(encoderStatus, false)
                                    }
                                    if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                                        continue
                                    }

                                    if (!decoderDone) {
                                        val decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC.toLong())
                                        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                            decoderOutputAvailable = false
                                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                            val newFormat = decoder.outputFormat
                                            Log.e("tmessages", "newFormat = $newFormat")
                                        } else if (decoderStatus < 0) {
                                            throw RuntimeException("unexpected result from decoder.dequeueOutputBuffer: $decoderStatus")
                                        } else {
                                            var doRender: Boolean
                                            if (Build.VERSION.SDK_INT >= 18) {
                                                doRender = info.size != 0
                                            } else {
                                                doRender = info.size != 0 || info.presentationTimeUs != 0L
                                            }
                                            if (endTime > 0 && info.presentationTimeUs >= endTime) {
                                                inputDone = true
                                                decoderDone = true
                                                doRender = false
                                                info.flags = info.flags or MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                            }
                                            if (startTime > 0 && videoTime == -1L) {
                                                if (info.presentationTimeUs < startTime) {
                                                    doRender = false
                                                    Log.e("tmessages", "drop frame startTime = " + startTime + " present time = " + info.presentationTimeUs)
                                                } else {
                                                    videoTime = info.presentationTimeUs
                                                }
                                            }
                                            decoder.releaseOutputBuffer(decoderStatus, doRender)
                                            if (doRender) {
                                                var errorWait = false
                                                try {
                                                    outputSurface.awaitNewImage()
                                                } catch (e: Exception) {
                                                    errorWait = true
                                                    Log.e("tmessages", e.message)
                                                }

                                                if (!errorWait) {
                                                    if (Build.VERSION.SDK_INT >= 18) {
                                                        outputSurface.drawImage(false)
                                                        inputSurface!!.setPresentationTime(info.presentationTimeUs * 1000)
                                                        inputSurface.swapBuffers()
                                                    } else {
                                                        val inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                                                        if (inputBufIndex >= 0) {
                                                            outputSurface.drawImage(true)
                                                            val rgbBuf = outputSurface.frame
                                                            val yuvBuf = encoderInputBuffers!![inputBufIndex]
                                                            yuvBuf.clear()
                                                            convertVideoFrame(rgbBuf, yuvBuf, colorFormat, resultWidth, resultHeight, padding, swapUV)
                                                            encoder.queueInputBuffer(inputBufIndex, 0, bufferSize, info.presentationTimeUs, 0)
                                                        } else {
                                                            Log.e("tmessages", "input buffer not available")
                                                        }
                                                    }
                                                }
                                            }
                                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                                decoderOutputAvailable = false
                                                Log.e("tmessages", "decoder stream end")
                                                if (Build.VERSION.SDK_INT >= 18) {
                                                    encoder.signalEndOfInputStream()
                                                } else {
                                                    val inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                                                    if (inputBufIndex >= 0) {
                                                        encoder.queueInputBuffer(inputBufIndex, 0, 1, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (videoTime != -1L) {
                                videoStartTime = videoTime
                            }
                        } catch (e: Exception) {
                            Log.e("tmessages", e.message)
                            error = true
                        }

                        extractor.unselectTrack(videoIndex)

                        outputSurface?.release()
                        inputSurface?.release()
                        if (decoder != null) {
                            decoder.stop()
                            decoder.release()
                        }
                        if (encoder != null) {
                            encoder.stop()
                            encoder.release()
                        }
                    }
                } else {
                    val videoTime = readAndWriteTrack(extractor, mediaMuxer, info, startTime, endTime, cacheFile, false)
                    if (videoTime != -1L) {
                        videoStartTime = videoTime
                    }
                }
                if (!error) {
                    readAndWriteTrack(extractor, mediaMuxer, info, videoStartTime, endTime, cacheFile, true)
                }
            } catch (e: Exception) {
                error = true
                Log.e("tmessages", e.message)
            } finally {
                extractor?.release()
                if (mediaMuxer != null) {
                    try {
                        mediaMuxer.finishMovie(false)
                    } catch (e: Exception) {
                        Log.e("tmessages", e.message)
                    }

                }
                Log.e("tmessages", "time = " + (System.currentTimeMillis() - time))
            }
        } else {
            didWriteData(true, true)
            return false
        }
        didWriteData(true, error)

        cachedFile = cacheFile

        /* File fdelete = inputFile;
        if (fdelete.exists()) {
            if (fdelete.delete()) {
               Log.e("file Deleted :" ,inputFile.getPath());
            } else {
                Log.e("file not Deleted :" , inputFile.getPath());
            }
        }*/

        //inputFile.delete();
        Log.i("ViratPath", path + "")
        Log.i("ViratPath", cacheFile.path + "")
        Log.i("ViratPath", inputFile.path + "")


        /* Log.e("ViratPath",path+"");
        File replacedFile = new File(path);

        FileOutputStream fos = null;
        InputStream inputStream = null;
        try {
            fos = new FileOutputStream(replacedFile);
             inputStream = new FileInputStream(cacheFile);
            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
            inputStream.close();
            fos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
*/

        //    cacheFile.delete();

        /* try {
           // copyFile(cacheFile,inputFile);
            //inputFile.delete();
            FileUtils.copyFile(cacheFile,inputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        /*cacheFile.delete();
        inputFile.delete();*/
        return true
    }

    companion object {

        lateinit var cachedFile: File

        val MIME_TYPE = "video/avc"
        private val PROCESSOR_TYPE_OTHER = 0
        private val PROCESSOR_TYPE_QCOM = 1
        private val PROCESSOR_TYPE_INTEL = 2
        private val PROCESSOR_TYPE_MTK = 3
        private val PROCESSOR_TYPE_SEC = 4
        private val PROCESSOR_TYPE_TI = 5
        @Volatile
        private var Instance: MediaController? = null

        //Default values
        private val DEFAULT_VIDEO_WIDTH = 1920
        private val DEFAULT_VIDEO_HEIGHT = 1080
        private val DEFAULT_VIDEO_BITRATE = 5000000

        val instance: MediaController
            get() {
                var localInstance = Instance
                if (localInstance == null) {
                    synchronized(MediaController::class.java) {
                        localInstance = Instance
                        if (localInstance == null) {
                            localInstance = MediaController()
                            Instance = localInstance
                        }
                    }
                }
                return localInstance!!
            }

        @SuppressLint("NewApi")
        fun selectColorFormat(codecInfo: MediaCodecInfo, mimeType: String): Int {
            val capabilities = codecInfo.getCapabilitiesForType(mimeType)
            var lastColorFormat = 0
            for (i in capabilities.colorFormats.indices) {
                val colorFormat = capabilities.colorFormats[i]
                if (isRecognizedFormat(colorFormat)) {
                    lastColorFormat = colorFormat
                    if (!(codecInfo.name == "OMX.SEC.AVC.Encoder" && colorFormat == 19)) {
                        return colorFormat
                    }
                }
            }
            return lastColorFormat
        }

        private fun isRecognizedFormat(colorFormat: Int): Boolean {
            when (colorFormat) {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar, MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar -> return true
                else -> return false
            }
        }

        external fun convertVideoFrame(src: ByteBuffer, dest: ByteBuffer, destFormat: Int, width: Int, height: Int, padding: Int, swap: Int): Int

        fun selectCodec(mimeType: String): MediaCodecInfo? {
            val numCodecs = MediaCodecList.getCodecCount()
            var lastCodecInfo: MediaCodecInfo? = null
            for (i in 0 until numCodecs) {
                val codecInfo = MediaCodecList.getCodecInfoAt(i)
                if (!codecInfo.isEncoder) {
                    continue
                }
                val types = codecInfo.supportedTypes
                for (type in types) {
                    if (type.equals(mimeType, ignoreCase = true)) {
                        lastCodecInfo = codecInfo
                        if (lastCodecInfo!!.name != "OMX.SEC.avc.enc") {
                            return lastCodecInfo
                        } else if (lastCodecInfo.name == "OMX.SEC.AVC.Encoder") {
                            return lastCodecInfo
                        }
                    }
                }
            }
            return lastCodecInfo
        }

        @Throws(IOException::class)
        fun copyFile(src: File, dst: File) {
            val inChannel = FileInputStream(src).channel
            val outChannel = FileOutputStream(dst).channel
            try {
                inChannel!!.transferTo(1, inChannel.size(), outChannel)
            } finally {
                inChannel?.close()
                outChannel?.close()
            }
        }
    }
}

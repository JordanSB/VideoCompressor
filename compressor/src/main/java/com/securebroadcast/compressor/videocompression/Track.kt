/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package com.securebroadcast.compressor.videocompression

import android.annotation.TargetApi
import android.media.MediaCodec
import android.media.MediaFormat

import com.coremedia.iso.boxes.AbstractMediaHeaderBox
import com.coremedia.iso.boxes.SampleDescriptionBox
import com.coremedia.iso.boxes.SoundMediaHeaderBox
import com.coremedia.iso.boxes.VideoMediaHeaderBox
import com.coremedia.iso.boxes.sampleentry.AudioSampleEntry
import com.coremedia.iso.boxes.sampleentry.VisualSampleEntry
import com.googlecode.mp4parser.boxes.mp4.ESDescriptorBox
import com.googlecode.mp4parser.boxes.mp4.objectdescriptors.AudioSpecificConfig
import com.googlecode.mp4parser.boxes.mp4.objectdescriptors.DecoderConfigDescriptor
import com.googlecode.mp4parser.boxes.mp4.objectdescriptors.ESDescriptor
import com.googlecode.mp4parser.boxes.mp4.objectdescriptors.SLConfigDescriptor
import com.mp4parser.iso14496.part15.AvcConfigurationBox

import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import java.util.LinkedList

@TargetApi(16)
class Track @Throws(Exception::class)
constructor(id: Int, format: MediaFormat, isAudio: Boolean) {
    var trackId: Long = 0
    val samples = ArrayList<Sample>()
    var duration: Long = 0
        private set
    var handler: String? = null
        private set
    var mediaHeaderBox: AbstractMediaHeaderBox? = null
        private set
    var sampleDescriptionBox: SampleDescriptionBox? = null
        private set
    private var syncSamples: LinkedList<Int>? = null
    var timeScale: Int = 0
        private set
    val creationTime = Date()
    var height: Int = 0
        private set
    var width: Int = 0
        private set
    var volume = 0f
        private set
    val sampleDurations = ArrayList<Long>()
    val isAudio = false
    private var lastPresentationTimeUs: Long = 0
    private var first = true

    init {
        var isAudio = isAudio
        trackId = id.toLong()
        if (!isAudio) {
            sampleDurations.add(3015.toLong())
            duration = 3015
            width = format.getInteger(MediaFormat.KEY_WIDTH)
            height = format.getInteger(MediaFormat.KEY_HEIGHT)
            timeScale = 90000
            syncSamples = LinkedList()
            handler = "vide"
            mediaHeaderBox = VideoMediaHeaderBox()
            sampleDescriptionBox = SampleDescriptionBox()
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime == "video/avc") {
                val visualSampleEntry = VisualSampleEntry("avc1")
                visualSampleEntry.dataReferenceIndex = 1
                visualSampleEntry.depth = 24
                visualSampleEntry.frameCount = 1
                visualSampleEntry.horizresolution = 72.0
                visualSampleEntry.vertresolution = 72.0
                visualSampleEntry.width = width
                visualSampleEntry.height = height

                val avcConfigurationBox = AvcConfigurationBox()

                if (format.getByteBuffer("csd-0") != null) {
                    val spsArray = ArrayList<ByteArray>()
                    val spsBuff = format.getByteBuffer("csd-0")
                    spsBuff.position(4)
                    val spsBytes = ByteArray(spsBuff.remaining())
                    spsBuff.get(spsBytes)
                    spsArray.add(spsBytes)

                    val ppsArray = ArrayList<ByteArray>()
                    val ppsBuff = format.getByteBuffer("csd-1")
                    ppsBuff.position(4)
                    val ppsBytes = ByteArray(ppsBuff.remaining())
                    ppsBuff.get(ppsBytes)
                    ppsArray.add(ppsBytes)
                    avcConfigurationBox.sequenceParameterSets = spsArray
                    avcConfigurationBox.pictureParameterSets = ppsArray
                }
                //ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(spsBytes);
                //SeqParameterSet seqParameterSet = SeqParameterSet.read(byteArrayInputStream);

                avcConfigurationBox.avcLevelIndication = 13
                avcConfigurationBox.avcProfileIndication = 100
                avcConfigurationBox.bitDepthLumaMinus8 = -1
                avcConfigurationBox.bitDepthChromaMinus8 = -1
                avcConfigurationBox.chromaFormat = -1
                avcConfigurationBox.configurationVersion = 1
                avcConfigurationBox.lengthSizeMinusOne = 3
                avcConfigurationBox.profileCompatibility = 0

                visualSampleEntry.addBox(avcConfigurationBox)
                sampleDescriptionBox!!.addBox(visualSampleEntry)
            } else if (mime == "video/mp4v") {
                val visualSampleEntry = VisualSampleEntry("mp4v")
                visualSampleEntry.dataReferenceIndex = 1
                visualSampleEntry.depth = 24
                visualSampleEntry.frameCount = 1
                visualSampleEntry.horizresolution = 72.0
                visualSampleEntry.vertresolution = 72.0
                visualSampleEntry.width = width
                visualSampleEntry.height = height

                sampleDescriptionBox!!.addBox(visualSampleEntry)
            }
        } else {
            sampleDurations.add(1024.toLong())
            duration = 1024
            isAudio = true
            volume = 1f
            timeScale = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            handler = "soun"
            mediaHeaderBox = SoundMediaHeaderBox()
            sampleDescriptionBox = SampleDescriptionBox()
            val audioSampleEntry = AudioSampleEntry("mp4a")
            audioSampleEntry.channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            audioSampleEntry.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).toLong()
            audioSampleEntry.dataReferenceIndex = 1
            audioSampleEntry.sampleSize = 16

            val esds = ESDescriptorBox()
            val descriptor = ESDescriptor()
            descriptor.esId = 0

            val slConfigDescriptor = SLConfigDescriptor()
            slConfigDescriptor.predefined = 2
            descriptor.slConfigDescriptor = slConfigDescriptor

            val decoderConfigDescriptor = DecoderConfigDescriptor()
            decoderConfigDescriptor.objectTypeIndication = 0x40
            decoderConfigDescriptor.streamType = 5
            decoderConfigDescriptor.bufferSizeDB = 1536
            decoderConfigDescriptor.maxBitRate = 96000
            decoderConfigDescriptor.avgBitRate = 96000

            val audioSpecificConfig = AudioSpecificConfig()
            audioSpecificConfig.setAudioObjectType(2)
            audioSpecificConfig.setSamplingFrequencyIndex(samplingFrequencyIndexMap[audioSampleEntry.sampleRate.toInt()]!!)
            audioSpecificConfig.setChannelConfiguration(audioSampleEntry.channelCount)
            decoderConfigDescriptor.audioSpecificInfo = audioSpecificConfig

            descriptor.decoderConfigDescriptor = decoderConfigDescriptor

            val data = descriptor.serialize()
            esds.esDescriptor = descriptor
            esds.data = data
            audioSampleEntry.addBox(esds)
            sampleDescriptionBox!!.addBox(audioSampleEntry)
        }
    }

    fun addSample(offset: Long, bufferInfo: MediaCodec.BufferInfo) {
        val isSyncFrame = !isAudio && bufferInfo.flags and MediaCodec.BUFFER_FLAG_SYNC_FRAME != 0
        samples.add(Sample(offset, bufferInfo.size.toLong()))
        if (syncSamples != null && isSyncFrame) {
            syncSamples!!.add(samples.size)
        }

        var delta = bufferInfo.presentationTimeUs - lastPresentationTimeUs
        lastPresentationTimeUs = bufferInfo.presentationTimeUs
        delta = (delta * timeScale + 500000L) / 1000000L
        if (!first) {
            sampleDurations.add(sampleDurations.size - 1, delta)
            duration += delta
        }
        first = false
    }

    fun getSyncSamples(): LongArray? {
        if (syncSamples == null || syncSamples!!.isEmpty()) {
            return null
        }
        val returns = LongArray(syncSamples!!.size)
        for (i in syncSamples!!.indices) {
            returns[i] = syncSamples!![i].toLong()
        }
        return returns
    }

    companion object {
        private val samplingFrequencyIndexMap = HashMap<Int, Int>()

        init {
            samplingFrequencyIndexMap[96000] = 0x0
            samplingFrequencyIndexMap[88200] = 0x1
            samplingFrequencyIndexMap[64000] = 0x2
            samplingFrequencyIndexMap[48000] = 0x3
            samplingFrequencyIndexMap[44100] = 0x4
            samplingFrequencyIndexMap[32000] = 0x5
            samplingFrequencyIndexMap[24000] = 0x6
            samplingFrequencyIndexMap[22050] = 0x7
            samplingFrequencyIndexMap[16000] = 0x8
            samplingFrequencyIndexMap[12000] = 0x9
            samplingFrequencyIndexMap[11025] = 0xa
            samplingFrequencyIndexMap[8000] = 0xb
        }
    }
}

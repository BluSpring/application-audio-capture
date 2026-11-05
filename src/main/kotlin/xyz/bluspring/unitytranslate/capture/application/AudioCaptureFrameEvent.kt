package xyz.bluspring.unitytranslate.capture.application

import java.nio.ByteBuffer

fun interface AudioCaptureFrameEvent {
    fun onAudioFrame(buffer: ByteBuffer)
}

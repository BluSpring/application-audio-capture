package xyz.bluspring.unitytranslate.capture.application

class RunningAudioSession(private val sessionData: AudioCaptureData, private val sessionId: Long) {
    private var wasStopped = false

    fun stop() {
        if (this.wasStopped)
            throw IllegalStateException("Session was already stopped!")

        ApplicationAudioCapture.stopCapture(this.sessionId)
        this.wasStopped = true
    }
}

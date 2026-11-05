package xyz.bluspring.unitytranslate.capture.application

class AudioCaptureData(val sampleRate: Int, val channels: Int, private val callback: AudioCaptureFrameEvent) {
    private var activeSession: RunningAudioSession? = null
        set(value) {
            field = value

            if (value != null) {
                this.createBlockingThread()
            }
        }

    private lateinit var blockingThread: Thread

    fun hasActiveSession(): Boolean {
        return this.activeSession != null
    }

    fun capture(processId: Int) {
        if (this.activeSession != null)
            throw IllegalStateException("There is already an active audio capture session!")

        val ptr = ApplicationAudioCapture.startCaptureId(this.sampleRate, this.channels.toByte(), processId, this.callback)
        val session = RunningAudioSession(this, ptr)
        this.activeSession = session
    }

    fun capture(processName: String) {
        if (this.activeSession != null)
            throw IllegalStateException("There is already an active audio capture session!")

        val ptr = ApplicationAudioCapture.startCaptureName(this.sampleRate, this.channels.toByte(), processName, this.callback)
        val session = RunningAudioSession(this, ptr)
        this.activeSession = session
    }

    fun captureSystem() {
        if (this.activeSession != null)
            throw IllegalStateException("There is already an active audio capture session!")

        val ptr = ApplicationAudioCapture.startCaptureSystemAudio(this.sampleRate, this.channels.toByte(), this.callback)
        val session = RunningAudioSession(this, ptr)
        this.activeSession = session
    }

    private fun createBlockingThread() {
//        this.blockingThread = thread {
//            while (this.activeSession != null) {
//                Thread.sleep(1)
//            }
//        }
    }

    fun stop() {
        this.activeSession?.stop()
        this.activeSession = null
    }
}

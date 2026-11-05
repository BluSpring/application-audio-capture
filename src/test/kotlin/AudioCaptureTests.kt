import xyz.bluspring.unitytranslate.capture.application.ApplicationAudioCapture

fun main() {
    ApplicationAudioCapture.autoLoadOrThrow()

    val sources = ApplicationAudioCapture.listAllSources()

    for (info in sources) {
        println(info)
    }

    val session = ApplicationAudioCapture.createCapture(48_000, 2) { data ->
        println(data)
    }

    session.captureSystem()
}

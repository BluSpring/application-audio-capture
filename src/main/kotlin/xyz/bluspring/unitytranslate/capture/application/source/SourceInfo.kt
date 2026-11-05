package xyz.bluspring.unitytranslate.capture.application.source

sealed interface SourceInfo {
    val sampleRate: Int
    val channels: Short
    val type: SourceType
    val state: SourceState
    val name: String
    val stableKey: String

    @JvmRecord
    data class Application(
        override val sampleRate: Int,
        override val channels: Short,
        override val state: SourceState,
        override val name: String,
        override val stableKey: String,

        val processId: Int?,
        val appId: String?,
    ) : SourceInfo {
        constructor(
            sampleRate: Int,
            channels: Short,
            state: Int,
            name: String,
            stableKey: String,

            processId: Int?,
            appId: String?
        ) : this(sampleRate, channels, SourceState.entries[state], name, stableKey, processId, appId)

        override val type: SourceType
            get() = SourceType.APPLICATION
    }

    @JvmRecord
    data class Generic(
        override val sampleRate: Int,
        override val channels: Short,
        override val type: SourceType,
        override val state: SourceState,
        override val name: String,
        override val stableKey: String,
    ) : SourceInfo {
        constructor(
            sampleRate: Int,
            channels: Short,
            type: Int,
            state: Int,
            name: String,
            stableKey: String,
        ) : this(sampleRate, channels, SourceType.entries[type], SourceState.entries[state], name, stableKey)
    }
}

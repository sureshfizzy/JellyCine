package `is`.xyz.mpv

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface

@Suppress("unused")
object MPVLib {
    init {
        val libs = arrayOf("mpv", "player")
        for (lib in libs) {
            System.loadLibrary(lib)
        }
    }

    fun preload() {
        // object init already loaded libmpv / libplayer
    }

    external fun create(appctx: Context)
    external fun init()
    external fun destroy()
    external fun attachSurface(surface: Surface)
    external fun detachSurface()

    external fun command(cmd: Array<out String>)

    external fun setOptionString(name: String, value: String): Int

    external fun grabThumbnail(dimension: Int): Bitmap?

    external fun getPropertyInt(property: String): Int?
    external fun setPropertyInt(property: String, value: Int)
    external fun getPropertyDouble(property: String): Double?
    external fun setPropertyDouble(property: String, value: Double)
    external fun getPropertyBoolean(property: String): Boolean?
    external fun setPropertyBoolean(property: String, value: Boolean)
    external fun getPropertyString(property: String): String?
    external fun setPropertyString(property: String, value: String)

    external fun observeProperty(property: String, format: Int)

    @JvmStatic
    @androidx.annotation.Keep
    fun logMessage(prefix: String, level: Int, text: String) {
        Unit
    }

    private val observers = mutableListOf<EventObserver>()

    @JvmStatic
    fun addObserver(o: EventObserver) {
        synchronized(observers) {
            observers.add(o)
        }
    }

    @JvmStatic
    fun removeObserver(o: EventObserver) {
        synchronized(observers) {
            observers.remove(o)
        }
    }

    @JvmStatic
    fun eventProperty(property: String, value: Long) {
        synchronized(observers) {
            for (observer in observers) observer.eventProperty(property, value)
        }
    }

    @JvmStatic
    fun eventProperty(property: String, value: Boolean) {
        synchronized(observers) {
            for (observer in observers) observer.eventProperty(property, value)
        }
    }

    @JvmStatic
    fun eventProperty(property: String, value: Double) {
        synchronized(observers) {
            for (observer in observers) observer.eventProperty(property, value)
        }
    }

    @JvmStatic
    fun eventProperty(property: String, value: String) {
        synchronized(observers) {
            for (observer in observers) observer.eventProperty(property, value)
        }
    }

    @JvmStatic
    fun eventProperty(property: String) {
        synchronized(observers) {
            for (observer in observers) observer.eventProperty(property)
        }
    }

    @JvmStatic
    fun event(eventId: Int) {
        synchronized(observers) {
            for (observer in observers) observer.event(eventId)
        }
    }

    interface EventObserver {
        fun eventProperty(property: String)
        fun eventProperty(property: String, value: Long)
        fun eventProperty(property: String, value: Boolean)
        fun eventProperty(property: String, value: String)
        fun eventProperty(property: String, value: Double)
        fun event(eventId: Int)
    }

    object MpvFormat {
        const val MPV_FORMAT_NONE: Int = 0
        const val MPV_FORMAT_STRING: Int = 1
        const val MPV_FORMAT_OSD_STRING: Int = 2
        const val MPV_FORMAT_FLAG: Int = 3
        const val MPV_FORMAT_INT64: Int = 4
        const val MPV_FORMAT_DOUBLE: Int = 5
        const val MPV_FORMAT_NODE: Int = 6
        const val MPV_FORMAT_NODE_ARRAY: Int = 7
        const val MPV_FORMAT_NODE_MAP: Int = 8
        const val MPV_FORMAT_BYTE_ARRAY: Int = 9
    }

    object MpvEvent {
        const val MPV_EVENT_NONE: Int = 0
        const val MPV_EVENT_SHUTDOWN: Int = 1
        const val MPV_EVENT_LOG_MESSAGE: Int = 2
        const val MPV_EVENT_GET_PROPERTY_REPLY: Int = 3
        const val MPV_EVENT_SET_PROPERTY_REPLY: Int = 4
        const val MPV_EVENT_COMMAND_REPLY: Int = 5
        const val MPV_EVENT_START_FILE: Int = 6
        const val MPV_EVENT_END_FILE: Int = 7
        const val MPV_EVENT_FILE_LOADED: Int = 8
        const val MPV_EVENT_VIDEO_RECONFIG: Int = 17
        const val MPV_EVENT_AUDIO_RECONFIG: Int = 18
        const val MPV_EVENT_SEEK: Int = 20
        const val MPV_EVENT_PLAYBACK_RESTART: Int = 21
        const val MPV_EVENT_PROPERTY_CHANGE: Int = 22
        const val MPV_EVENT_QUEUE_OVERFLOW: Int = 24
        const val MPV_EVENT_HOOK: Int = 25
    }
}

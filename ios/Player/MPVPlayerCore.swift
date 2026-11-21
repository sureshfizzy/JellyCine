import UIKit
import QuartzCore
import Mpv

protocol MPVPlayerCoreDelegate: AnyObject {
    func mpvBufferingChanged(isBuffering: Bool)
    func mpvDidFail(message: String)
    func mpvDidFinish()
    func mpvDidLoad()
}

/// mpv draws into a standalone CAMetalLayer added as a sublayer, not the UIView's own backing
/// layer: MoltenVK changes layer properties from mpv's video thread, which UIKit forbids for
/// layers owned by a UIView.
final class MPVVideoView: UIView {
    let metalLayer = CAMetalLayer()
    var onLayout: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        metalLayer.isOpaque = true
        layer.addSublayer(metalLayer)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override func layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        metalLayer.frame = bounds
        CATransaction.commit()
        onLayout?()
    }
}

nonisolated private func mpvWakeup(_ ctx: UnsafeMutableRawPointer?) {
    guard let ctx else { return }
    Unmanaged<MPVPlayerCore>.fromOpaque(ctx).takeUnretainedValue().drainEvents()
}

nonisolated final class MPVPlayerCore: @unchecked Sendable {
    weak var delegate: MPVPlayerCoreDelegate?

    private var mpv: OpaquePointer?
    private let eventQueue = DispatchQueue(label: "com.jellycine.mpv.events")
    private weak var view: MPVVideoView?
    private var isShutDown = false
    private var hasStarted = false
    private(set) var fileLoaded = false
    private var isLoaded = false
    private var lastSize: CGSize = .zero

    private(set) var duration: Double = 0
    private(set) var position: Double = 0

    private var pendingSubtitleUrls: [String] = []
    private var selectedSubtitleUrl: String?
    private var volumeLevel: Double = 1
    private var zoomEnabled = false
    var userOptions: [String: String] = [:]
    private(set) var isPaused = false

    /// Starts mpv on first use; later calls replace the current file on the same instance.
    func load(
        in view: MPVVideoView,
        url: String,
        headers: [String: String],
        startPosition: Double,
        startPaused: Bool,
        audioTrackId: String?,
        subtitleTrackId: String?,
        subtitleUrls: [String],
        selectedSubtitleUrl: String?
    ) {
        self.view = view
        pendingSubtitleUrls = subtitleUrls
        self.selectedSubtitleUrl = selectedSubtitleUrl
        isLoaded = false
        fileLoaded = false
        position = 0
        duration = 0

        if !hasStarted {
            hasStarted = true
            syncLayerSize()
            guard createPlayer(in: view) else { return }
        }
        guard mpv != nil else { return }
        applyVolume()
        applyZoom()

        if !headers.isEmpty {
            setProperty("http-header-fields", headers.map { "\($0.key): \($0.value)" }.joined(separator: ","))
        }
        var options: [String] = []
        if startPosition > 1 { options.append(String(format: "start=%.3f", startPosition)) }
        if let audioTrackId { options.append("aid=\(audioTrackId)") }
        if selectedSubtitleUrl == nil, let subtitleTrackId { options.append("sid=\(subtitleTrackId)") }
        if startPaused { options.append("pause=yes") }
        if options.isEmpty {
            command(["loadfile", url, "replace"])
        } else {
            command(["loadfile", url, "replace", "-1", options.joined(separator: ",")])
        }
    }

    private func createPlayer(in view: MPVVideoView) -> Bool {
        guard let handle = mpv_create() else {
            delegate?.mpvDidFail(message: "mpv_create failed")
            return false
        }
        mpv = handle

        var wid = Int64(Int(bitPattern: Unmanaged.passUnretained(view.metalLayer).toOpaque()))
        mpv_set_option(handle, "wid", MPV_FORMAT_INT64, &wid)
        setOption("vo", "gpu-next")
        setOption("gpu-api", "vulkan")
        setOption("gpu-context", "iosvk")
        setOption("hwdec", "videotoolbox")
        setOption("target-colorspace-hint", "yes")
        setOption("ao", "audiounit")
        setOption("keep-open", "yes")
        setOption("idle", "yes")
        setOption("framedrop", "vo")
        setOption("vd-lavc-threads", "0")
        setOption("cache", "yes")
        setOption("sub-font-provider", "none")
        setOption("sub-ass-override", "strip")
        for (name, value) in userOptions { setOption(name, value) }

        let status = mpv_initialize(handle)
        guard status >= 0 else {
            delegate?.mpvDidFail(message: "mpv_initialize: \(String(cString: mpv_error_string(status)))")
            return false
        }

        mpv_request_log_messages(handle, "warn")
        observe("time-pos", MPV_FORMAT_DOUBLE)
        observe("duration", MPV_FORMAT_DOUBLE)
        observe("pause", MPV_FORMAT_FLAG)
        observe("paused-for-cache", MPV_FORMAT_FLAG)
        mpv_set_wakeup_callback(handle, mpvWakeup, Unmanaged.passUnretained(self).toOpaque())
        return true
    }

    private func syncLayerSize() {
        guard let layer = view?.metalLayer, let view else { return }
        let scale = view.window?.screen.scale ?? UIScreen.main.scale
        layer.frame = view.bounds
        layer.contentsScale = scale
        layer.drawableSize = CGSize(width: view.bounds.width * scale, height: view.bounds.height * scale)
    }

    /// mpv reads the layer size when the video output is (re)configured, so rotation needs a reload.
    func viewSizeChanged() {
        guard hasStarted, isLoaded, let view, view.bounds.size != lastSize else {
            lastSize = view?.bounds.size ?? lastSize
            return
        }
        lastSize = view.bounds.size
        syncLayerSize()
        command(["video-reload"])
    }

    func drainEvents() {
        eventQueue.async { [weak self] in
            guard let self, let mpv = self.mpv, !self.isShutDown else { return }
            while let event = mpv_wait_event(mpv, 0), event.pointee.event_id != MPV_EVENT_NONE {
                self.handle(event.pointee)
            }
        }
    }

    private func handle(_ event: mpv_event) {
        switch event.event_id {
        case MPV_EVENT_PROPERTY_CHANGE:
            guard let raw = event.data else { return }
            let prop = raw.assumingMemoryBound(to: mpv_event_property.self).pointee
            let name = String(cString: prop.name)
            switch (name, prop.format) {
            case ("time-pos", MPV_FORMAT_DOUBLE):
                if let data = prop.data { position = data.assumingMemoryBound(to: Double.self).pointee }
            case ("duration", MPV_FORMAT_DOUBLE):
                if let data = prop.data { duration = data.assumingMemoryBound(to: Double.self).pointee; isLoaded = duration > 0 }
            case ("pause", MPV_FORMAT_FLAG):
                if let data = prop.data {
                    let paused = data.assumingMemoryBound(to: Int32.self).pointee != 0
                    isPaused = paused
                }
            case ("paused-for-cache", MPV_FORMAT_FLAG):
                if let data = prop.data {
                    let buffering = data.assumingMemoryBound(to: Int32.self).pointee != 0
                    DispatchQueue.main.async { self.delegate?.mpvBufferingChanged(isBuffering: buffering) }
                }
            default: break
            }
        case MPV_EVENT_LOG_MESSAGE:
            guard let raw = event.data else { return }
            let log = raw.assumingMemoryBound(to: mpv_event_log_message.self).pointee
            if let prefix = log.prefix, let text = log.text {
                NSLog("mpv[%@] %@", String(cString: prefix), String(cString: text).trimmingCharacters(in: .whitespacesAndNewlines))
            }
        case MPV_EVENT_FILE_LOADED:
            fileLoaded = true
            for url in pendingSubtitleUrls {
                command(["sub-add", url, url == selectedSubtitleUrl ? "select" : "auto"])
            }
            pendingSubtitleUrls = []
            DispatchQueue.main.async { self.delegate?.mpvDidLoad() }
        case MPV_EVENT_END_FILE:
            guard let raw = event.data else { return }
            let end = raw.assumingMemoryBound(to: mpv_event_end_file.self).pointee
            if end.reason == MPV_END_FILE_REASON_ERROR {
                let message = String(cString: mpv_error_string(end.error))
                DispatchQueue.main.async { self.delegate?.mpvDidFail(message: message) }
            } else if end.reason == MPV_END_FILE_REASON_EOF {
                DispatchQueue.main.async { self.delegate?.mpvDidFinish() }
            }
        default: break
        }
    }

    func setPaused(_ paused: Bool) { setProperty("pause", paused ? "yes" : "no") }
    func seek(to seconds: Double) { command(["seek", String(format: "%.3f", seconds), "absolute"]) }
    /// Volume and zoom can be set before the first load, so they are remembered and applied once mpv exists.
    func setVolume(_ volume: Double) {
        volumeLevel = max(0, min(1, volume))
        applyVolume()
    }
    func setZoom(_ enabled: Bool) {
        zoomEnabled = enabled
        applyZoom()
    }
    private func applyVolume() { setProperty("volume", String(format: "%.1f", volumeLevel * 100)) }
    private func applyZoom() { setProperty("panscan", zoomEnabled ? "1" : "0") }
    func selectAudio(_ trackId: String) { setProperty("aid", trackId) }
    func selectSubtitle(_ trackId: String, externalUrl: String?) {
        if trackId == "no" {
            setProperty("sid", "no")
        } else if let externalUrl {
            command(["sub-add", externalUrl, "select"])
        } else {
            setProperty("sid", trackId)
        }
    }

    /// Seconds of media buffered ahead in the demuxer cache, measured from the file start.
    func bufferedPosition() -> Double {
        guard let mpv, !isShutDown else { return 0 }
        var value: Double = 0
        guard mpv_get_property(mpv, "demuxer-cache-time", MPV_FORMAT_DOUBLE, &value) >= 0 else { return position }
        return max(value, position)
    }

    private func setOption(_ name: String, _ value: String) { mpv_set_option_string(mpv, name, value) }
    private func setProperty(_ name: String, _ value: String) {
        guard let mpv, !isShutDown else { return }
        mpv_set_property_string(mpv, name, value)
    }
    private func observe(_ name: String, _ format: mpv_format) {
        guard let mpv else { return }
        mpv_observe_property(mpv, 0, name, format)
    }

    private func command(_ args: [String]) {
        guard let mpv else { return }
        var cStrings: [UnsafeMutablePointer<CChar>?] = args.map { strdup($0) }
        cStrings.append(nil)
        defer { cStrings.forEach { free($0) } }
        var constPointers = cStrings.map { UnsafePointer<CChar>($0) }
        mpv_command(mpv, &constPointers)
    }

    func shutdown() {
        isShutDown = true
        guard let handle = mpv else { return }
        mpv_set_wakeup_callback(handle, nil, nil)
        mpv = nil
        DispatchQueue.global(qos: .userInitiated).async { mpv_terminate_destroy(handle) }
    }

    deinit { if mpv != nil { shutdown() } }
}

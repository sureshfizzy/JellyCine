import UIKit
import QuartzCore
import Mpv

protocol MPVPlayerCoreDelegate: AnyObject {
    func mpvTimeChanged(position: Double, duration: Double)
    func mpvPauseChanged(isPaused: Bool)
    func mpvBufferingChanged(isBuffering: Bool)
    func mpvDidFail(message: String)
    func mpvDidFinish()
}

/// mpv draws into a standalone CAMetalLayer added as a sublayer, not the UIView's own backing
/// layer: MoltenVK changes layer properties from mpv's video thread, which UIKit forbids for
/// layers owned by a UIView.
final class MPVVideoView: UIView {
    let metalLayer = CAMetalLayer()

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
    private var isLoaded = false
    private var lastSize: CGSize = .zero

    private(set) var duration: Double = 0
    private(set) var position: Double = 0

    func start(in view: MPVVideoView, url: String, headers: [String: String], startPosition: Double) {
        guard !hasStarted else { return }
        hasStarted = true
        self.view = view
        syncLayerSize()

        guard let handle = mpv_create() else {
            delegate?.mpvDidFail(message: "mpv_create failed")
            return
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
        setOption("demuxer-max-bytes", "64MiB")
        setOption("sub-font-provider", "none")
        if !headers.isEmpty {
            setOption("http-header-fields", headers.map { "\($0.key): \($0.value)" }.joined(separator: ","))
        }
        if startPosition > 1 {
            setOption("start", String(format: "%.3f", startPosition))
        }

        let status = mpv_initialize(handle)
        guard status >= 0 else {
            delegate?.mpvDidFail(message: "mpv_initialize: \(String(cString: mpv_error_string(status)))")
            return
        }

        observe("time-pos", MPV_FORMAT_DOUBLE)
        observe("duration", MPV_FORMAT_DOUBLE)
        observe("pause", MPV_FORMAT_FLAG)
        observe("paused-for-cache", MPV_FORMAT_FLAG)
        mpv_set_wakeup_callback(handle, mpvWakeup, Unmanaged.passUnretained(self).toOpaque())
        command(["loadfile", url])
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
                report()
            case ("duration", MPV_FORMAT_DOUBLE):
                if let data = prop.data { duration = data.assumingMemoryBound(to: Double.self).pointee; isLoaded = duration > 0 }
                report()
            case ("pause", MPV_FORMAT_FLAG):
                if let data = prop.data {
                    let paused = data.assumingMemoryBound(to: Int32.self).pointee != 0
                    DispatchQueue.main.async { self.delegate?.mpvPauseChanged(isPaused: paused) }
                }
            case ("paused-for-cache", MPV_FORMAT_FLAG):
                if let data = prop.data {
                    let buffering = data.assumingMemoryBound(to: Int32.self).pointee != 0
                    DispatchQueue.main.async { self.delegate?.mpvBufferingChanged(isBuffering: buffering) }
                }
            default: break
            }
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

    private var lastReport: CFTimeInterval = 0
    private func report() {
        let now = CACurrentMediaTime()
        guard now - lastReport > 0.25 else { return }
        lastReport = now
        let (p, d) = (position, duration)
        DispatchQueue.main.async { self.delegate?.mpvTimeChanged(position: p, duration: d) }
    }

    func setPaused(_ paused: Bool) { setProperty("pause", paused ? "yes" : "no") }
    func seek(to seconds: Double) { command(["seek", String(format: "%.3f", seconds), "absolute"]) }
    func seek(by seconds: Double) { command(["seek", String(format: "%.3f", seconds), "relative"]) }

    private func setOption(_ name: String, _ value: String) { mpv_set_option_string(mpv, name, value) }
    private func setProperty(_ name: String, _ value: String) { mpv_set_property_string(mpv, name, value) }
    private func observe(_ name: String, _ format: mpv_format) { mpv_observe_property(mpv, 0, name, format) }

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

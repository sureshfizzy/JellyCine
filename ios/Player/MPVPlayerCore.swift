import AVFoundation
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
    let appleLayer = AVPlayerLayer()
    var onLayout: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        metalLayer.isOpaque = true
        metalLayer.pixelFormat = .rgba16Float
        metalLayer.wantsExtendedDynamicRangeContent = true
        metalLayer.colorspace = CGColorSpace(name: CGColorSpace.extendedLinearITUR_2020)
        appleLayer.videoGravity = .resizeAspect
        appleLayer.backgroundColor = UIColor.black.cgColor
        appleLayer.isHidden = true
        layer.addSublayer(metalLayer)
        layer.addSublayer(appleLayer)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override func layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        metalLayer.frame = bounds
        appleLayer.frame = bounds
        CATransaction.commit()
        onLayout?()
    }

    func showAppleDisplay(_ show: Bool) {
        appleLayer.isHidden = !show
        metalLayer.isHidden = show
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
    private(set) var isPaused = false
    private var apple: AppleDisplayPlayer?
    private var appleActive = false
    private var appleFailedOver = false
    private var lastRequest: LoadRequest?

    func readoutPosition() -> Double { appleActive ? (apple?.position ?? position) : position }
    func readoutDuration() -> Double { appleActive ? (apple?.duration ?? duration) : duration }
    func readoutPlaying() -> Bool {
        appleActive ? (apple?.isPlaying ?? false) : (fileLoaded && !isPaused)
    }

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
        selectedSubtitleUrl: String?,
        useAppleDisplay: Bool
    ) {
        self.view = view
        pendingSubtitleUrls = subtitleUrls
        self.selectedSubtitleUrl = selectedSubtitleUrl
        isLoaded = false
        fileLoaded = false
        position = 0
        duration = 0
        lastRequest = LoadRequest(
            url: url,
            headers: headers,
            startPosition: startPosition,
            startPaused: startPaused,
            audioTrackId: audioTrackId,
            subtitleTrackId: subtitleTrackId,
            subtitleUrls: subtitleUrls,
            selectedSubtitleUrl: selectedSubtitleUrl
        )
        appleFailedOver = false
        stopApple()
        view.showAppleDisplay(false)

        if useAppleDisplay, startApple(
            url: url,
            headers: headers,
            startPosition: startPosition,
            startPaused: startPaused,
            audioTrackId: audioTrackId,
            subtitleTrackId: subtitleTrackId
        ) {
            return
        }

        beginMpv(
            url: url,
            headers: headers,
            startPosition: startPosition,
            startPaused: startPaused,
            audioTrackId: audioTrackId,
            subtitleTrackId: subtitleTrackId
        )
    }

    private func beginMpv(
        url: String,
        headers: [String: String],
        startPosition: Double,
        startPaused: Bool,
        audioTrackId: String?,
        subtitleTrackId: String?
    ) {
        guard let view else { return }
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

    private func startApple(
        url: String,
        headers: [String: String],
        startPosition: Double,
        startPaused: Bool,
        audioTrackId: String?,
        subtitleTrackId: String?
    ) -> Bool {
        guard let view, let mediaURL = Self.mediaURL(url) else { return false }
        if mpv != nil { command(["stop"]) }
        let player = AppleDisplayPlayer(layer: view.appleLayer)
        player.onReady = { [weak self] in self?.delegate?.mpvDidLoad() }
        player.onBuffering = { [weak self] buffering in self?.delegate?.mpvBufferingChanged(isBuffering: buffering) }
        player.onEnded = { [weak self] in self?.delegate?.mpvDidFinish() }
        player.onFailed = { [weak self] message in
            guard let self else { return }
            if self.appleFailedOver {
                self.delegate?.mpvDidFail(message: message)
            } else {
                self.failOverToMpv()
            }
        }
        apple = player
        appleActive = true
        view.showAppleDisplay(true)
        applyZoom()
        player.load(
            url: mediaURL,
            headers: headers,
            startPosition: startPosition,
            startPaused: startPaused,
            volume: volumeLevel,
            audioTrackId: audioTrackId,
            subtitleTrackId: subtitleTrackId
        )
        return true
    }

    private func failOverToMpv() {
        guard !appleFailedOver, let lastRequest else { return }
        appleFailedOver = true
        stopApple()
        view?.showAppleDisplay(false)
        beginMpv(
            url: lastRequest.url,
            headers: lastRequest.headers,
            startPosition: lastRequest.startPosition,
            startPaused: lastRequest.startPaused,
            audioTrackId: lastRequest.audioTrackId,
            subtitleTrackId: lastRequest.subtitleTrackId
        )
    }

    private func stopApple() {
        apple?.stop()
        apple = nil
        appleActive = false
        view?.appleLayer.player = nil
    }

    private static func mediaURL(_ raw: String) -> URL? {
        if let url = URL(string: raw), url.scheme != nil { return url }
        if raw.hasPrefix("/") { return URL(fileURLWithPath: raw) }
        return nil
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
        setOption("demuxer-max-bytes", "64MiB")
        setOption("sub-font-provider", "none")
        setOption("sub-ass-override", "strip")

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
        view.appleLayer.contentsScale = scale
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
        if !appleActive { command(["video-reload"]) }
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

    func setPaused(_ paused: Bool) {
        if appleActive { apple?.setPaused(paused) } else { setProperty("pause", paused ? "yes" : "no") }
    }
    func seek(to seconds: Double) {
        if appleActive { apple?.seek(to: seconds) } else { command(["seek", String(format: "%.3f", seconds), "absolute"]) }
    }
    /// Volume and zoom can be set before the first load, so they are remembered and applied once mpv exists.
    func setVolume(_ volume: Double) {
        volumeLevel = max(0, min(1, volume))
        applyVolume()
    }
    func setZoom(_ enabled: Bool) {
        zoomEnabled = enabled
        applyZoom()
    }
    private func applyVolume() {
        if appleActive { apple?.setVolume(volumeLevel) }
        setProperty("volume", String(format: "%.1f", volumeLevel * 100))
    }
    private func applyZoom() {
        view?.appleLayer.videoGravity = zoomEnabled ? .resizeAspectFill : .resizeAspect
        setProperty("panscan", zoomEnabled ? "1" : "0")
    }
    func selectAudio(_ trackId: String) {
        if appleActive { apple?.selectAudio(trackId) } else { setProperty("aid", trackId) }
    }
    func selectSubtitle(_ trackId: String, externalUrl: String?) {
        if appleActive {
            apple?.selectSubtitle(trackId)
            return
        }
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
        if appleActive { return apple?.buffered() ?? position }
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

    private struct LoadRequest {
        let url: String
        let headers: [String: String]
        let startPosition: Double
        let startPaused: Bool
        let audioTrackId: String?
        let subtitleTrackId: String?
        let subtitleUrls: [String]
        let selectedSubtitleUrl: String?
    }

    func shutdown() {
        isShutDown = true
        stopApple()
        guard let handle = mpv else { return }
        mpv_set_wakeup_callback(handle, nil, nil)
        mpv = nil
        DispatchQueue.global(qos: .userInitiated).async { mpv_terminate_destroy(handle) }
    }

    deinit { if mpv != nil || apple != nil { shutdown() } }
}

private final class AppleDisplayPlayer {
    var onReady: (() -> Void)?
    var onBuffering: ((Bool) -> Void)?
    var onEnded: (() -> Void)?
    var onFailed: ((String) -> Void)?

    private let player = AVPlayer()
    private let layer: AVPlayerLayer
    private var item: AVPlayerItem?
    private var statusObs: NSKeyValueObservation?
    private var controlObs: NSKeyValueObservation?
    private var endObserver: NSObjectProtocol?
    private var wantsPlay = false
    private var startAt: Double = 0
    private var didReportReady = false
    private var stopped = false
    private var audioTrackId: String?
    private var subtitleTrackId: String?

    init(layer: AVPlayerLayer) {
        self.layer = layer
        layer.player = player
    }

    var position: Double { finite(player.currentTime().seconds) }
    var duration: Double { finite(item?.duration.seconds ?? 0) }
    var isPlaying: Bool { didReportReady && wantsPlay }

    func buffered() -> Double {
        let end = item?.loadedTimeRanges.reduce(0.0) { partial, value in
            let rangeEnd = CMTimeGetSeconds(CMTimeRangeGetEnd(value.timeRangeValue))
            return max(partial, finite(rangeEnd))
        } ?? 0
        return max(end, position)
    }

    func load(
        url: URL,
        headers: [String: String],
        startPosition: Double,
        startPaused: Bool,
        volume: Double,
        audioTrackId: String?,
        subtitleTrackId: String?
    ) {
        wantsPlay = !startPaused
        startAt = startPosition
        self.audioTrackId = audioTrackId
        self.subtitleTrackId = subtitleTrackId
        player.volume = Float(volume)
        let asset = AVURLAsset(
            url: url,
            options: headers.isEmpty ? nil : [AVURLAssetHTTPHeaderFieldsKey: headers]
        )
        let item = AVPlayerItem(asset: asset)
        self.item = item
        statusObs = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            DispatchQueue.main.async { self?.handleStatus(item) }
        }
        controlObs = player.observe(\.timeControlStatus, options: [.new]) { [weak self] player, _ in
            DispatchQueue.main.async {
                guard let self, self.didReportReady, !self.stopped else { return }
                self.onBuffering?(player.timeControlStatus == .waitingToPlayAtSpecifiedRate)
            }
        }
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            guard let self, !self.stopped else { return }
            self.wantsPlay = false
            self.onEnded?()
        }
        player.replaceCurrentItem(with: item)
    }

    func setPaused(_ paused: Bool) {
        wantsPlay = !paused
        if paused { player.pause() } else { player.play() }
    }

    func seek(to seconds: Double) {
        player.seek(to: CMTime(seconds: seconds, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
    }

    func setVolume(_ volume: Double) { player.volume = Float(volume) }

    func selectAudio(_ trackId: String) {
        audioTrackId = trackId
        select(trackId, characteristic: .audible)
    }

    func selectSubtitle(_ trackId: String) {
        subtitleTrackId = trackId
        select(trackId, characteristic: .legible)
    }

    func stop() {
        stopped = true
        statusObs = nil
        controlObs = nil
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = nil
        player.pause()
        player.replaceCurrentItem(with: nil)
        layer.player = nil
        item = nil
    }

    private func handleStatus(_ item: AVPlayerItem) {
        guard !stopped else { return }
        switch item.status {
        case .readyToPlay:
            guard !didReportReady else { return }
            didReportReady = true
            select(audioTrackId, characteristic: .audible)
            select(subtitleTrackId, characteristic: .legible)
            if startAt > 1 {
                player.seek(to: CMTime(seconds: startAt, preferredTimescale: 600)) { [weak self] _ in
                    guard let self, self.wantsPlay, !self.stopped else { return }
                    self.player.play()
                }
            } else if wantsPlay {
                player.play()
            }
            onReady?()
        case .failed:
            onFailed?(item.error?.localizedDescription ?? "AVPlayer failed")
        default:
            break
        }
    }

    private func select(_ trackId: String?, characteristic: AVMediaCharacteristic) {
        guard let item, let group = item.asset.mediaSelectionGroup(forMediaCharacteristic: characteristic) else { return }
        if trackId == nil || trackId == "no" {
            if characteristic == .legible { item.select(nil, in: group) }
            return
        }
        guard let index = Int(trackId ?? ""), group.options.indices.contains(index - 1) else { return }
        item.select(group.options[index - 1], in: group)
    }

    private func finite(_ value: Double) -> Double { value.isFinite ? value : 0 }
}

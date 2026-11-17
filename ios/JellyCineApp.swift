import AVFoundation
import SwiftUI
import UIKit
import JellyCineShared

@main
struct JellyCineApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
        }
    }
}

/// Connects the shared Kotlin player to libmpv. Kotlin owns the UI; this only plays video.
final class MPVBridge: NSObject, IosMpvBridge, MPVPlayerCoreDelegate {
    private let core = MPVPlayerCore()
    private var videoView: MPVVideoView?
    private var listener: IosMpvBridgeListener?

    override init() {
        super.init()
        core.delegate = self
    }

    private func ensureView() -> MPVVideoView {
        if let videoView { return videoView }
        let view = MPVVideoView()
        view.onLayout = { [weak self] in self?.core.viewSizeChanged() }
        videoView = view
        return view
    }

    func createVideoView() -> UIView { ensureView() }

    func load(
        url: String,
        headers: [String: String],
        startPositionMs: Int64,
        startPlayback: Bool,
        audioTrackId: String?,
        subtitleTrackId: String?,
        subtitleUrls: [String],
        selectedSubtitleUrl: String?
    ) {
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try? AVAudioSession.sharedInstance().setActive(true)
        core.load(
            in: ensureView(),
            url: url,
            headers: headers,
            startPosition: Double(startPositionMs) / 1000,
            startPaused: !startPlayback,
            audioTrackId: audioTrackId,
            subtitleTrackId: subtitleTrackId,
            subtitleUrls: subtitleUrls,
            selectedSubtitleUrl: selectedSubtitleUrl
        )
    }

    func setPaused(paused: Bool) { core.setPaused(paused) }
    func seekTo(positionMs: Int64) { core.seek(to: Double(positionMs) / 1000) }
    func setVolume(volume: Float) { core.setVolume(Double(volume)) }
    func selectAudioTrack(trackId: String) { core.selectAudio(trackId) }
    func selectSubtitleTrack(trackId: String, externalUrl: String?) { core.selectSubtitle(trackId, externalUrl: externalUrl) }
    func setZoom(enabled: Bool) { core.setZoom(enabled) }
    func viewSizeChanged() { core.viewSizeChanged() }
    func attachListener(listener: IosMpvBridgeListener) { self.listener = listener }

    func position() -> Int64 { Int64(core.position * 1000) }
    func duration() -> Int64 { Int64(core.duration * 1000) }
    func bufferedPosition() -> Int64 { Int64(core.bufferedPosition() * 1000) }
    func isPlaying() -> Bool { core.fileLoaded && !core.isPaused }

    func shutdown() {
        core.shutdown()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    // MARK: MPVPlayerCoreDelegate

    func mpvBufferingChanged(isBuffering: Bool) { listener?.onBuffering(isBuffering: isBuffering) }
    func mpvDidFail(message: String) { listener?.onError(message: message) }
    func mpvDidFinish() { listener?.onEnded() }
    func mpvDidLoad() { listener?.onReady() }
}

final class MPVBridgeFactory: NSObject, IosMpvBridgeFactory {
    func create() -> IosMpvBridge { MPVBridge() }
}

struct ComposeView: UIViewControllerRepresentable {
    private let bridgeFactory = MPVBridgeFactory()

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(bridgeFactory: bridgeFactory)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

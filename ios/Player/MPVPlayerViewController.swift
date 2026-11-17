import UIKit
import AVFoundation

final class MPVPlayerViewController: UIViewController, MPVPlayerCoreDelegate {
    private let url: String
    private let headers: [String: String]
    private let startPosition: Double
    private let core = MPVPlayerCore()

    private let videoView = MPVVideoView()
    private let controls = UIView()
    private let titleLabel = UILabel()
    private let closeButton = UIButton(type: .system)
    private let playButton = UIButton(type: .system)
    private let backButton = UIButton(type: .system)
    private let forwardButton = UIButton(type: .system)
    private let slider = UISlider()
    private let elapsedLabel = UILabel()
    private let remainingLabel = UILabel()
    private let spinner = UIActivityIndicatorView(style: .large)
    private let errorLabel = UILabel()

    private var didStart = false
    private var isScrubbing = false
    private var isPaused = false
    private var hideTask: DispatchWorkItem?

    init(url: String, title: String?, headers: [String: String], startPosition: Double) {
        self.url = url
        self.headers = headers
        self.startPosition = startPosition
        super.init(nibName: nil, bundle: nil)
        titleLabel.text = title
        modalPresentationStyle = .fullScreen
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override var prefersStatusBarHidden: Bool { true }
    override var prefersHomeIndicatorAutoHidden: Bool { true }
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .allButUpsideDown }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try? AVAudioSession.sharedInstance().setActive(true)

        videoView.frame = view.bounds
        videoView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(videoView)
        buildControls()
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(toggleControls)))

        core.delegate = self
        spinner.startAnimating()
        scheduleHide()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard view.bounds.width > 0 else { return }
        if !didStart {
            didStart = true
            core.start(in: videoView, url: url, headers: headers, startPosition: startPosition)
        } else {
            core.viewSizeChanged()
        }
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isBeingDismissed || presentingViewController == nil {
            core.shutdown()
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        }
    }

    private func buildControls() {
        controls.frame = view.bounds
        controls.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        controls.backgroundColor = UIColor.black.withAlphaComponent(0.35)
        view.addSubview(controls)

        func icon(_ button: UIButton, _ name: String, size: CGFloat, action: Selector) {
            let config = UIImage.SymbolConfiguration(pointSize: size, weight: .semibold)
            button.setImage(UIImage(systemName: name, withConfiguration: config), for: .normal)
            button.tintColor = .white
            button.addTarget(self, action: action, for: .touchUpInside)
            button.translatesAutoresizingMaskIntoConstraints = false
            controls.addSubview(button)
        }
        icon(closeButton, "xmark", size: 20, action: #selector(closeTapped))
        icon(backButton, "gobackward.10", size: 30, action: #selector(skipBack))
        icon(playButton, "pause.fill", size: 44, action: #selector(togglePlay))
        icon(forwardButton, "goforward.10", size: 30, action: #selector(skipForward))

        titleLabel.textColor = .white
        titleLabel.font = .systemFont(ofSize: 17, weight: .semibold)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        controls.addSubview(titleLabel)

        for label in [elapsedLabel, remainingLabel] {
            label.textColor = .white
            label.font = .monospacedDigitSystemFont(ofSize: 13, weight: .medium)
            label.text = "0:00"
            label.translatesAutoresizingMaskIntoConstraints = false
            controls.addSubview(label)
        }
        slider.minimumTrackTintColor = .white
        slider.addTarget(self, action: #selector(scrubBegan), for: .touchDown)
        slider.addTarget(self, action: #selector(scrubChanged), for: .valueChanged)
        slider.addTarget(self, action: #selector(scrubEnded), for: [.touchUpInside, .touchUpOutside, .touchCancel])
        slider.translatesAutoresizingMaskIntoConstraints = false
        controls.addSubview(slider)

        spinner.color = .white
        spinner.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(spinner)

        errorLabel.textColor = .white
        errorLabel.numberOfLines = 0
        errorLabel.textAlignment = .center
        errorLabel.isHidden = true
        errorLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(errorLabel)

        let guide = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            closeButton.topAnchor.constraint(equalTo: guide.topAnchor, constant: 12),
            closeButton.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: 16),
            titleLabel.centerYAnchor.constraint(equalTo: closeButton.centerYAnchor),
            titleLabel.leadingAnchor.constraint(equalTo: closeButton.trailingAnchor, constant: 16),
            titleLabel.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -16),

            playButton.centerXAnchor.constraint(equalTo: controls.centerXAnchor),
            playButton.centerYAnchor.constraint(equalTo: controls.centerYAnchor),
            backButton.centerYAnchor.constraint(equalTo: playButton.centerYAnchor),
            backButton.trailingAnchor.constraint(equalTo: playButton.leadingAnchor, constant: -48),
            forwardButton.centerYAnchor.constraint(equalTo: playButton.centerYAnchor),
            forwardButton.leadingAnchor.constraint(equalTo: playButton.trailingAnchor, constant: 48),

            elapsedLabel.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: 16),
            elapsedLabel.bottomAnchor.constraint(equalTo: guide.bottomAnchor, constant: -16),
            remainingLabel.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -16),
            remainingLabel.bottomAnchor.constraint(equalTo: elapsedLabel.bottomAnchor),
            slider.leadingAnchor.constraint(equalTo: elapsedLabel.trailingAnchor, constant: 12),
            slider.trailingAnchor.constraint(equalTo: remainingLabel.leadingAnchor, constant: -12),
            slider.centerYAnchor.constraint(equalTo: elapsedLabel.centerYAnchor),

            spinner.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            errorLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            errorLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            errorLabel.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: 32),
            errorLabel.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -32)
        ])
    }

    private func setPlayIcon() {
        let config = UIImage.SymbolConfiguration(pointSize: 44, weight: .semibold)
        playButton.setImage(UIImage(systemName: isPaused ? "play.fill" : "pause.fill", withConfiguration: config), for: .normal)
    }

    private func format(_ seconds: Double) -> String {
        let total = max(0, Int(seconds.rounded()))
        let (h, m, s) = (total / 3600, total % 3600 / 60, total % 60)
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
    }

    @objc private func closeTapped() { dismiss(animated: true) }
    @objc private func togglePlay() { core.setPaused(!isPaused); scheduleHide() }
    @objc private func skipBack() { core.seek(by: -10); scheduleHide() }
    @objc private func skipForward() { core.seek(by: 10); scheduleHide() }
    @objc private func scrubBegan() { isScrubbing = true; hideTask?.cancel() }
    @objc private func scrubChanged() {
        elapsedLabel.text = format(Double(slider.value))
        remainingLabel.text = "-" + format(core.duration - Double(slider.value))
    }
    @objc private func scrubEnded() {
        core.seek(to: Double(slider.value))
        isScrubbing = false
        scheduleHide()
    }

    @objc private func toggleControls() {
        let show = controls.alpha < 0.5
        UIView.animate(withDuration: 0.2) { self.controls.alpha = show ? 1 : 0 }
        if show { scheduleHide() }
    }

    private func scheduleHide() {
        hideTask?.cancel()
        let task = DispatchWorkItem { [weak self] in
            guard let self, !self.isPaused, !self.isScrubbing else { return }
            UIView.animate(withDuration: 0.25) { self.controls.alpha = 0 }
        }
        hideTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 4, execute: task)
    }

    func mpvTimeChanged(position: Double, duration: Double) {
        spinner.stopAnimating()
        guard !isScrubbing, duration > 0 else { return }
        slider.maximumValue = Float(duration)
        slider.value = Float(position)
        elapsedLabel.text = format(position)
        remainingLabel.text = "-" + format(duration - position)
    }

    func mpvPauseChanged(isPaused: Bool) {
        self.isPaused = isPaused
        setPlayIcon()
        if isPaused { controls.alpha = 1 } else { scheduleHide() }
    }

    func mpvBufferingChanged(isBuffering: Bool) {
        isBuffering ? spinner.startAnimating() : spinner.stopAnimating()
    }

    func mpvDidFail(message: String) {
        spinner.stopAnimating()
        errorLabel.text = "Playback failed\n\(message)"
        errorLabel.isHidden = false
        controls.alpha = 1
    }

    func mpvDidFinish() { dismiss(animated: true) }
}

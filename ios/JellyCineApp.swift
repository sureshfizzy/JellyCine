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

final class MPVPlayerLauncher: NSObject, IosPlayerLauncher {
    func play(url: String, title: String?, headers: [String: String], startPositionMs: Int64) {
        DispatchQueue.main.async {
            guard let presenter = Self.topViewController() else { return }
            let player = MPVPlayerViewController(
                url: url,
                title: title,
                headers: headers,
                startPosition: Double(startPositionMs) / 1000
            )
            presenter.present(player, animated: true)
        }
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.windows.first(where: \.isKeyWindow)?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

struct ComposeView: UIViewControllerRepresentable {
    private let launcher = MPVPlayerLauncher()

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(playerLauncher: launcher)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

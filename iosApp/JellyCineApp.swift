import SwiftUI
import JellyCineData

@main
struct JellyCineApp: App {
    init() {
        AuthFacade.shared.setStringResolver { key, argument in
            AndroidStrings.get(key, argument, module: .data)
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

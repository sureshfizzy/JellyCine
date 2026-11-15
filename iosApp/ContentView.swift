import SwiftUI
import JellyCineData

struct ContentView: View {
    private enum SessionState {
        case loading
        case signedOut
        case signedIn
    }

    @State private var state: SessionState = .loading
    private let repository = AuthFacade.shared

    var body: some View {
        ZStack {
            AuthBackground()

            switch state {
            case .loading:
                ProgressView().tint(.white)
            case .signedOut:
                AuthScreen(onAuthSuccess: { state = .signedIn })
                    .transition(.opacity)
            case .signedIn:
                EmptyView()
            }
        }
        .animation(.easeOut(duration: 0.24), value: state)
        .task {
            let authenticated = (try? await repository.isAuthenticated())?.boolValue ?? false
            state = authenticated ? .signedIn : .signedOut
        }
    }
}

#Preview {
    ContentView()
}

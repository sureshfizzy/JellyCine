import Foundation
import Observation
import JellyCineData

enum AuthStep {
    case serverConnection
    case login
}

@Observable
final class AuthScreenViewModel {
    var step: AuthStep
    var selectedServerUrl: String
    var selectedServerName: String?

    var serverUrl = ""
    var isServerLoading = false
    var serverErrorMessage: String?

    var username = ""
    var password = ""
    var isLoginLoading = false
    var loginErrorMessage: String?
    var showQuickConnect = true
    var isQuickConnectLoading = false
    var quickConnectCode: String?

    @ObservationIgnored private let startedAtLogin: Bool
    @ObservationIgnored private let repository = AuthFacade.shared
    @ObservationIgnored private var quickConnectTask: Task<Void, Never>?

    private static let quickConnectPollInterval: Duration = .seconds(3)
    private static let quickConnectMaxPolls = 40

    init(serverUrl: String? = nil, serverName: String? = nil, startAtLogin: Bool = false) {
        let hasServer = !(serverUrl ?? "").trimmingCharacters(in: .whitespaces).isEmpty
        let startsAtLogin = startAtLogin && hasServer
        self.startedAtLogin = startsAtLogin
        self.step = startsAtLogin ? .login : .serverConnection
        self.selectedServerUrl = serverUrl ?? ""
        self.selectedServerName = serverName
    }

    var canNavigateBackToServerStep: Bool {
        step == .login && !startedAtLogin
    }

    func updateServerUrl(_ url: String) {
        serverUrl = url
        serverErrorMessage = nil
    }

    func updateUsername(_ value: String) {
        username = value
        loginErrorMessage = nil
    }

    func updatePassword(_ value: String) {
        password = value
        loginErrorMessage = nil
    }

    func goBackToServerStep() {
        guard !isLoginLoading else { return }
        cancelQuickConnect()
        loginErrorMessage = nil
        step = .serverConnection
    }

    func connectToServer() async {
        let url = serverUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !url.isEmpty else {
            serverErrorMessage = AndroidStrings.get("auth_error_enter_server_url")
            return
        }

        isServerLoading = true
        serverErrorMessage = nil
        defer { isServerLoading = false }

        do {
            let result = try await repository.testServerConnection(serverUrl: url)
            if let failure = result.failure {
                serverErrorMessage = Self.mapServerError(failure)
                return
            }
            selectedServerUrl = url
            selectedServerName = result.serverName
            step = .login
        } catch {
            if error is CancellationError { return }
            serverErrorMessage = AndroidStrings.get("auth_error_server_generic")
        }
    }

    func login() async -> Bool {
        guard !username.trimmingCharacters(in: .whitespaces).isEmpty else {
            loginErrorMessage = AndroidStrings.get("auth_error_enter_username")
            return false
        }

        isLoginLoading = true
        loginErrorMessage = nil
        defer { isLoginLoading = false }

        do {
            let result = try await repository.authenticateUser(
                serverUrl: selectedServerUrl,
                username: username,
                password: password
            )
            if let failure = result.failure {
                loginErrorMessage = Self.mapLoginError(failure)
                return false
            }
            return true
        } catch {
            if error is CancellationError { return false }
            loginErrorMessage = AndroidStrings.get("auth_error_login_generic")
            return false
        }
    }

    func refreshQuickConnectVisibility() async {
        let url = selectedServerUrl.trimmingCharacters(in: .whitespaces)
        guard !url.isEmpty else {
            showQuickConnect = false
            return
        }

        let supported = (try? await repository.isQuickConnectSupported(serverUrl: url))?.boolValue ?? true
        showQuickConnect = supported
        if !supported {
            quickConnectCode = nil
            isQuickConnectLoading = false
        }
    }

    func loginWithQuickConnect(onSuccess: @escaping @MainActor () -> Void) {
        guard showQuickConnect, !isQuickConnectLoading else { return }

        let url = selectedServerUrl.trimmingCharacters(in: .whitespaces)
        guard !url.isEmpty else {
            loginErrorMessage = AndroidStrings.get("auth_error_missing_server_url_quick_connect")
            return
        }

        quickConnectTask?.cancel()
        quickConnectTask = Task { [weak self] in
            guard let self else { return }
            self.isQuickConnectLoading = true
            self.loginErrorMessage = nil
            self.quickConnectCode = nil

            let initiated: QuickConnectStartResult
            do {
                initiated = try await self.repository.initiateQuickConnect(serverUrl: url)
            } catch {
                self.finishQuickConnect(error: AndroidStrings.get("auth_error_quick_connect_start_failed"))
                return
            }

            if let failure = initiated.failure {
                self.finishQuickConnect(error: Self.mapLoginError(failure))
                return
            }

            let secret = initiated.secret ?? ""
            let code = initiated.code ?? ""
            guard !secret.isEmpty, !code.isEmpty else {
                self.finishQuickConnect(error: AndroidStrings.get("auth_error_quick_connect_start_failed"))
                return
            }

            self.quickConnectCode = code

            for _ in 0..<Self.quickConnectMaxPolls {
                try? await Task.sleep(for: Self.quickConnectPollInterval)
                if Task.isCancelled { return }

                let poll = try? await self.repository.authenticateWithQuickConnect(
                    serverUrl: url,
                    secret: secret
                )
                if Task.isCancelled { return }
                if poll?.isSuccess == true {
                    self.finishQuickConnect(error: nil)
                    onSuccess()
                    return
                }
            }

            self.finishQuickConnect(error: AndroidStrings.get("auth_error_quick_connect_timed_out"))
        }
    }

    func cancelQuickConnect() {
        quickConnectTask?.cancel()
        quickConnectTask = nil
        isQuickConnectLoading = false
        quickConnectCode = nil
    }

    private func finishQuickConnect(error: String?) {
        isQuickConnectLoading = false
        quickConnectCode = nil
        if let error { loginErrorMessage = error }
    }

    private static func mapLoginError(_ failure: AuthFailure) -> String {
        let message = failure.message.trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = message.lowercased()

        switch failure.httpCode?.intValue {
        case 400: return AndroidStrings.get("auth_error_login_invalid_request")
        case 401: return AndroidStrings.get("auth_error_login_invalid_credentials")
        case 403: return AndroidStrings.get("auth_error_login_access_denied")
        case 404: return AndroidStrings.get("auth_error_login_service_not_found")
        case 429: return AndroidStrings.get("auth_error_login_too_many_attempts")
        case 500, 502, 503, 504: return AndroidStrings.get("auth_error_login_server_error")
        default:
            if message == "401" || lower.contains("authentication failed") {
                return AndroidStrings.get("auth_error_login_invalid_credentials")
            }
            if lower.contains("timeout") {
                return AndroidStrings.get("auth_error_login_timeout")
            }
            if lower.contains("cannot reach server") || lower.contains("unable to resolve host") {
                return AndroidStrings.get("auth_error_login_cannot_reach_server")
            }
            if lower.contains("connection refused") || lower.contains("failed to connect") {
                return AndroidStrings.get("auth_error_login_cannot_connect")
            }
            return AndroidStrings.get("auth_error_login_generic")
        }
    }

    private static func mapServerError(_ failure: AuthFailure) -> String {
        let message = failure.message.trimmingCharacters(in: .whitespacesAndNewlines)

        switch failure.httpCode?.intValue {
        case 400: return AndroidStrings.get("auth_error_server_invalid_request")
        case 401, 403: return AndroidStrings.get("auth_error_server_access_rejected")
        case 404: return AndroidStrings.get("auth_error_server_endpoint_not_found")
        case 500, 502, 503, 504: return AndroidStrings.get("auth_error_server_unavailable")
        default:
            if message == "401" {
                return AndroidStrings.get("auth_error_server_access_rejected")
            }
            if message.isEmpty {
                return AndroidStrings.get("auth_error_server_generic")
            }
            return message
        }
    }
}

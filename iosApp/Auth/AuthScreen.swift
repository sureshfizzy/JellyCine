import SwiftUI

struct AuthScreen: View {
    @State private var viewModel: AuthScreenViewModel
    let onAuthSuccess: () -> Void

    init(
        serverUrl: String? = nil,
        serverName: String? = nil,
        startAtLogin: Bool = false,
        onAuthSuccess: @escaping () -> Void
    ) {
        _viewModel = State(
            initialValue: AuthScreenViewModel(
                serverUrl: serverUrl,
                serverName: serverName,
                startAtLogin: startAtLogin
            )
        )
        self.onAuthSuccess = onAuthSuccess
    }

    var body: some View {
        ZStack {
            AuthBackground()

            GeometryReader { proxy in
                ScrollView {
                    Group {
                        switch viewModel.step {
                        case .serverConnection:
                            ServerConnectionContent(viewModel: viewModel)
                        case .login:
                            LoginContent(viewModel: viewModel, onAuthSuccess: onAuthSuccess)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 16)
                    .frame(maxWidth: 520)
                    .frame(maxWidth: .infinity, minHeight: proxy.size.height)
                }
                .scrollDismissesKeyboard(.interactively)
                .scrollBounceBehavior(.basedOnSize)
            }

            topBar
        }
        .preferredColorScheme(.dark)
        .animation(.easeOut(duration: 0.24), value: viewModel.step)
        .task(id: viewModel.step) {
            if viewModel.step == .login {
                await viewModel.refreshQuickConnectVisibility()
            }
        }
        .onDisappear { viewModel.cancelQuickConnect() }
    }

    private var topBar: some View {
        VStack {
            HStack {
                if viewModel.canNavigateBackToServerStep {
                    Button {
                        viewModel.goBackToServerStep()
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 48, height: 48)
                    }
                    .disabled(viewModel.isLoginLoading)
                    .accessibilityLabel(Text(verbatim: AndroidStrings.get("cd_back_button")))
                }

                Spacer()

                Button(action: openLanguageSettings) {
                    Image(systemName: "translate")
                        .font(.system(size: 22))
                        .foregroundStyle(.white)
                        .frame(width: 48, height: 48)
                }
                .accessibilityLabel(Text(verbatim: AndroidStrings.get("settings_language")))
            }
            .padding(.horizontal, 8)
            Spacer()
        }
    }

    private func openLanguageSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

private struct AnimatedBrandHero: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(spacing: 12) {
            TimelineView(.animation) { context in
                let t = context.date.timeIntervalSinceReferenceDate
                Image("jellycine_logo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 132, height: 132)
                    .scaleEffect(1 + 0.015 * sin(2 * .pi * t / 4.0))
                    .rotationEffect(.degrees(1.5 * sin(2 * .pi * t / 7.2)))
                    .offset(
                        x: 4 * sin(2 * .pi * t / 6.0),
                        y: 6 * sin(2 * .pi * t / 4.8)
                    )
            }
            .accessibilityHidden(true)

            Text(title)
                .font(.title.weight(.bold))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)

            Text(subtitle)
                .font(.subheadline)
                .foregroundStyle(Color.white.opacity(0.78))
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 24)
        }
    }
}

private struct ServerConnectionContent: View {
    @Bindable var viewModel: AuthScreenViewModel
    @FocusState private var urlFocused: Bool

    var body: some View {
        VStack(spacing: 24) {
            AnimatedBrandHero(
                title: AndroidStrings.get("auth_connect_title"),
                subtitle: AndroidStrings.get("auth_connect_subtitle")
            )

            AuthCard {
                Text(verbatim: AndroidStrings.get("auth_connection_settings"))
                    .font(.headline)
                    .foregroundStyle(.white)

                AuthTextField(
                    title: AndroidStrings.get("server_url"),
                    placeholder: AndroidStrings.get("auth_server_url_placeholder"),
                    isEmpty: viewModel.serverUrl.isEmpty,
                    isFocused: urlFocused
                ) {
                    TextField("", text: Binding(
                        get: { viewModel.serverUrl },
                        set: { viewModel.updateServerUrl($0) }
                    ))
                    .focused($urlFocused)
                    .keyboardType(.URL)
                    .textContentType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.go)
                    .onSubmit(connect)
                }
                .disabled(viewModel.isServerLoading)

                AuthErrorText(message: viewModel.serverErrorMessage)

                Button(action: connect) {
                    Text(verbatim: AndroidStrings.get("auth_connect_to_server"))
                }
                .buttonStyle(AuthPrimaryButtonStyle(isLoading: viewModel.isServerLoading))
                .disabled(viewModel.isServerLoading || viewModel.serverUrl.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .animation(.easeOut(duration: 0.24), value: viewModel.serverErrorMessage)
        }
    }

    private func connect() {
        guard !viewModel.isServerLoading else { return }
        urlFocused = false
        Task { await viewModel.connectToServer() }
    }
}

private struct LoginContent: View {
    @Bindable var viewModel: AuthScreenViewModel
    let onAuthSuccess: () -> Void

    private enum Field { case username, password }

    @FocusState private var focus: Field?
    @State private var isPasswordVisible = false

    private var isBusy: Bool { viewModel.isLoginLoading || viewModel.isQuickConnectLoading }

    var body: some View {
        VStack(spacing: 20) {
            AnimatedBrandHero(
                title: viewModel.selectedServerName ?? AndroidStrings.get("auth_welcome_back"),
                subtitle: viewModel.selectedServerUrl.isEmpty
                    ? AndroidStrings.get("auth_sign_in_subtitle")
                    : viewModel.selectedServerUrl
            )

            AuthCard {
                AuthTextField(
                    title: AndroidStrings.get("username"),
                    icon: "person.fill",
                    isEmpty: viewModel.username.isEmpty,
                    isFocused: focus == .username
                ) {
                    TextField("", text: Binding(
                        get: { viewModel.username },
                        set: { viewModel.updateUsername($0) }
                    ))
                    .focused($focus, equals: .username)
                    .textContentType(.username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.next)
                    .onSubmit { focus = .password }
                }
                .disabled(isBusy)

                AuthTextField(
                    title: AndroidStrings.get("password"),
                    icon: "lock.fill",
                    isEmpty: viewModel.password.isEmpty,
                    isFocused: focus == .password,
                    trailing: AnyView(
                        Button {
                            isPasswordVisible.toggle()
                            focus = .password
                        } label: {
                            Image(systemName: isPasswordVisible ? "eye.slash.fill" : "eye.fill")
                                .foregroundStyle(Color.white.opacity(0.65))
                                .frame(width: 32, height: 32)
                        }
                        .accessibilityLabel(Text(verbatim: AndroidStrings.get(isPasswordVisible ? "auth_hide_password" : "auth_show_password")))
                    )
                ) {
                    passwordInput
                }
                .disabled(isBusy)

                AuthErrorText(message: viewModel.loginErrorMessage)

                Button(action: signIn) {
                    Text(verbatim: AndroidStrings.get("auth_sign_in"))
                }
                .buttonStyle(AuthPrimaryButtonStyle(isLoading: viewModel.isLoginLoading))
                .disabled(isBusy || viewModel.username.trimmingCharacters(in: .whitespaces).isEmpty)

                if viewModel.showQuickConnect {
                    quickConnect
                }
            }
            .animation(.easeOut(duration: 0.24), value: viewModel.loginErrorMessage)
        }
    }

    @ViewBuilder
    private var passwordInput: some View {
        Group {
            if isPasswordVisible {
                TextField("", text: passwordBinding)
            } else {
                SecureField("", text: passwordBinding)
            }
        }
        .focused($focus, equals: .password)
        .textContentType(.password)
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .submitLabel(.go)
        .onSubmit(signIn)
    }

    private var passwordBinding: Binding<String> {
        Binding(
            get: { viewModel.password },
            set: { viewModel.updatePassword($0) }
        )
    }

    private var quickConnect: some View {
        VStack(spacing: 8) {
            Button {
                focus = nil
                viewModel.loginWithQuickConnect(onSuccess: onAuthSuccess)
            } label: {
                if viewModel.isQuickConnectLoading && viewModel.quickConnectCode == nil {
                    ProgressView().tint(.white)
                } else if let code = viewModel.quickConnectCode {
                    Text(verbatim: AndroidStrings.get("auth_quick_connect_code", code))
                } else {
                    Text(verbatim: AndroidStrings.get("auth_quick_connect"))
                }
            }
            .buttonStyle(AuthOutlinedButtonStyle())
            .disabled(isBusy)

            if viewModel.quickConnectCode != nil {
                Text(verbatim: AndroidStrings.get("auth_quick_connect_approval_hint"))
                    .font(.footnote)
                    .foregroundStyle(Color.white.opacity(0.75))
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func signIn() {
        guard !isBusy else { return }
        focus = nil
        Task {
            if await viewModel.login() { onAuthSuccess() }
        }
    }
}

#Preview("Server") {
    AuthScreen(onAuthSuccess: {})
}

#Preview("Login") {
    AuthScreen(
        serverUrl: "http://192.168.1.100:8096",
        serverName: "Home Media Server",
        startAtLogin: true,
        onAuthSuccess: {}
    )
}

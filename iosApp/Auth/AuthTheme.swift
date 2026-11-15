import SwiftUI

enum AuthTheme {
    static let jellyBlue = Color(red: 0x00 / 255, green: 0x7A / 255, blue: 0xFF / 255)
    static let jellyRed = Color(red: 0xD3 / 255, green: 0x2F / 255, blue: 0x2F / 255)
    static let cardBackground = Color(red: 0x0A / 255, green: 0x0A / 255, blue: 0x0A / 255)
    static let disabledButton = Color(red: 0x1E / 255, green: 0x1E / 255, blue: 0x1E / 255)
    static let backgroundTint = Color(red: 0x03 / 255, green: 0x04 / 255, blue: 0x06 / 255)

    static let cardRadius: CGFloat = 24
    static let controlRadius: CGFloat = 14
    static let buttonHeight: CGFloat = 50
}

struct AuthBackground: View {
    var body: some View {
        LinearGradient(
            colors: [.black, .black, AuthTheme.backgroundTint, .black],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }
}

struct AuthCard<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 14, content: content)
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: AuthTheme.cardRadius, style: .continuous)
                    .fill(AuthTheme.cardBackground)
                    .shadow(color: .black.opacity(0.5), radius: 8, y: 4)
            )
    }
}

struct AuthPrimaryButtonStyle: ButtonStyle {
    var isLoading = false
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        ZStack {
            if isLoading {
                ProgressView().tint(.white)
            } else {
                configuration.label
                    .font(.body.weight(.medium))
            }
        }
        .foregroundStyle(isEnabled || isLoading ? Color.white : Color.white.opacity(0.4))
        .frame(maxWidth: .infinity)
        .frame(height: AuthTheme.buttonHeight)
        .background(
            RoundedRectangle(cornerRadius: AuthTheme.controlRadius, style: .continuous)
                .fill(isEnabled || isLoading ? AuthTheme.jellyBlue : AuthTheme.disabledButton)
        )
        .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

struct AuthOutlinedButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.medium))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: AuthTheme.buttonHeight)
            .overlay(
                RoundedRectangle(cornerRadius: AuthTheme.controlRadius, style: .continuous)
                    .stroke(Color.white.opacity(0.3), lineWidth: 1)
            )
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

struct AuthTextField<Field: View>: View {
    let title: String
    var placeholder: String?
    var icon: String?
    var isEmpty: Bool
    var isFocused: Bool
    var trailing: AnyView?
    @ViewBuilder var field: () -> Field

    private var isFloating: Bool { isFocused || !isEmpty }
    private var accent: Color { isFocused ? AuthTheme.jellyBlue : Color.white.opacity(0.6) }

    var body: some View {
        HStack(spacing: 12) {
            if let icon {
                Image(systemName: icon)
                    .font(.system(size: 18))
                    .foregroundStyle(isFocused ? AuthTheme.jellyBlue : Color.white.opacity(0.65))
                    .frame(width: 22)
            }

            ZStack(alignment: .leading) {
                if let placeholder, isFocused, isEmpty {
                    Text(verbatim: placeholder)
                        .foregroundStyle(Color.white.opacity(0.6))
                        .lineLimit(1)
                        .allowsHitTesting(false)
                }
                field()
                    .foregroundStyle(.white)
                    .tint(AuthTheme.jellyBlue)
            }

            if let trailing { trailing }
        }
        .padding(.horizontal, 14)
        .frame(height: 56)
        .overlay(
            RoundedRectangle(cornerRadius: AuthTheme.controlRadius, style: .continuous)
                .stroke(
                    isFocused ? AuthTheme.jellyBlue : Color.white.opacity(0.26),
                    lineWidth: isFocused ? 2 : 1
                )
        )
        .overlay(alignment: .topLeading) {
            if isFloating {
                Text(verbatim: title)
                    .font(.caption)
                    .foregroundStyle(accent)
                    .padding(.horizontal, 4)
                    .background(AuthTheme.cardBackground)
                    .offset(x: 10, y: -8)
                    .allowsHitTesting(false)
                    .transition(.opacity)
            }
        }
        .overlay(alignment: .leading) {
            if !isFloating {
                Text(verbatim: title)
                    .foregroundStyle(Color.white.opacity(0.6))
                    .padding(.leading, icon == nil ? 14 : 14 + 22 + 12)
                    .allowsHitTesting(false)
                    .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.15), value: isFloating)
        .animation(.easeOut(duration: 0.15), value: isFocused)
    }
}

struct AuthErrorText: View {
    let message: String?

    var body: some View {
        if let message {
            Text(message)
                .font(.footnote)
                .foregroundStyle(AuthTheme.jellyRed)
                .frame(maxWidth: .infinity, alignment: .leading)
                .transition(.opacity)
        }
    }
}

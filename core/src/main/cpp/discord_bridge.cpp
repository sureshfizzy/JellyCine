#include <jni.h>
#include <string>
#include <memory>
#include <thread>
#include <atomic>
#include <mutex>
#include <android/log.h>
#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"

#define LOG_TAG "DiscordBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::unique_ptr<discordpp::Client> g_client = nullptr;
static std::atomic<bool> g_running{false};
static std::thread g_callback_thread;
static std::atomic<bool> g_ready{false};
static uint64_t g_appId = 0;

struct PendingPresence {
    std::string details;
    std::string state;
    std::string largeImageKey;
    std::string largeImageText;
    int64_t startTimestamp;
    int activityType;
    bool valid = false;
};

static std::mutex g_pendingMutex;
static PendingPresence g_pending;

static void sendPresence(const PendingPresence& p) {
    discordpp::Activity activity;
    activity.SetType(static_cast<discordpp::ActivityTypes>(p.activityType));
    if (!p.details.empty()) {
        activity.SetDetails(std::optional<std::string>(p.details));
    }
    if (p.state.size() >= 2) {
        activity.SetState(std::optional<std::string>(p.state));
    }

    discordpp::ActivityAssets assets;
    bool hasAssets = false;
    if (!p.largeImageKey.empty()) {
        assets.SetLargeImage(std::optional<std::string>(p.largeImageKey));
        hasAssets = true;
    }
    if (!p.largeImageText.empty()) {
        assets.SetLargeText(std::optional<std::string>(p.largeImageText));
        hasAssets = true;
    }
    if (hasAssets) {
        activity.SetAssets(std::optional<discordpp::ActivityAssets>(std::move(assets)));
    }

    if (p.startTimestamp > 0) {
        discordpp::ActivityTimestamps timestamps;
        timestamps.SetStart(static_cast<uint64_t>(p.startTimestamp));
        activity.SetTimestamps(std::optional<discordpp::ActivityTimestamps>(std::move(timestamps)));
    }

    g_client->UpdateRichPresence(std::move(activity), [](discordpp::ClientResult result) {
        if (!result.Successful()) {
            LOGE("Failed to update Rich Presence: %s", result.Error().c_str());
        }
    });
}

static void callbackLoop() {
    while (g_running.load()) {
        try {
            discordpp::RunCallbacks();
        } catch (...) {}
        std::this_thread::sleep_for(std::chrono::milliseconds(16));
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_jellycine_player_discord_DiscordRpcManager_nativeInitialize(
        JNIEnv *env, jobject thiz, jstring application_id, jstring access_token) {

    if (g_client != nullptr) {
        return JNI_TRUE;
    }

    const char *appIdStr = env->GetStringUTFChars(application_id, nullptr);
    const char *tokenStr = env->GetStringUTFChars(access_token, nullptr);
    g_appId = strtoull(appIdStr, nullptr, 10);
    std::string token(tokenStr);
    env->ReleaseStringUTFChars(application_id, appIdStr);
    env->ReleaseStringUTFChars(access_token, tokenStr);

    try {
        g_client = std::make_unique<discordpp::Client>();
        g_client->SetApplicationId(g_appId);

        g_client->SetStatusChangedCallback([](discordpp::Client::Status status,
                                              discordpp::Client::Error error,
                                              int32_t errorDetail) {
            if (status == discordpp::Client::Status::Ready) {
                g_ready.store(true);
                std::lock_guard<std::mutex> lock(g_pendingMutex);
                if (g_pending.valid) {
                    sendPresence(g_pending);
                    g_pending.valid = false;
                }
            } else if (status == discordpp::Client::Status::Disconnected) {
                g_ready.store(false);
            }
        });

        g_running.store(true);
        g_callback_thread = std::thread(callbackLoop);

        g_client->UpdateToken(
            discordpp::AuthorizationTokenType::Bearer,
            token,
            [](discordpp::ClientResult result) {
                if (result.Successful()) {
                    g_client->Connect();
                } else {
                    LOGE("Failed to set token: %d", static_cast<int>(result.Type()));
                }
            }
        );
    } catch (...) {
        LOGE("Exception during Discord SDK initialization");
        g_client.reset();
        g_running.store(false);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_jellycine_player_discord_DiscordRpcManager_nativeUpdatePresence(
        JNIEnv *env, jobject thiz,
        jstring details, jstring state,
        jstring large_image_key, jstring large_image_text,
        jlong start_timestamp, jint activity_type) {

    if (g_client == nullptr) return;

    const char *detailsStr = env->GetStringUTFChars(details, nullptr);
    const char *stateStr = env->GetStringUTFChars(state, nullptr);
    const char *largeImageKeyStr = env->GetStringUTFChars(large_image_key, nullptr);
    const char *largeImageTextStr = env->GetStringUTFChars(large_image_text, nullptr);

    PendingPresence p;
    p.details = detailsStr;
    p.state = stateStr;
    p.largeImageKey = largeImageKeyStr;
    p.largeImageText = largeImageTextStr;
    p.startTimestamp = start_timestamp;
    p.activityType = activity_type;
    p.valid = true;

    env->ReleaseStringUTFChars(details, detailsStr);
    env->ReleaseStringUTFChars(state, stateStr);
    env->ReleaseStringUTFChars(large_image_key, largeImageKeyStr);
    env->ReleaseStringUTFChars(large_image_text, largeImageTextStr);

    if (g_ready.load()) {
        try {
            sendPresence(p);
        } catch (...) {
            LOGE("Exception in UpdateRichPresence");
        }
    } else {
        std::lock_guard<std::mutex> lock(g_pendingMutex);
        g_pending = p;
    }
}

JNIEXPORT void JNICALL
Java_com_jellycine_player_discord_DiscordRpcManager_nativeClearPresence(
        JNIEnv *env, jobject thiz) {

    {
        std::lock_guard<std::mutex> lock(g_pendingMutex);
        g_pending.valid = false;
    }
    if (g_client == nullptr || !g_ready.load()) return;
    try {
        g_client->ClearRichPresence();
    } catch (...) {}
}

JNIEXPORT void JNICALL
Java_com_jellycine_player_discord_DiscordRpcManager_nativeShutdown(
        JNIEnv *env, jobject thiz) {

    if (g_client == nullptr) return;

    g_running.store(false);
    if (g_callback_thread.joinable()) {
        g_callback_thread.join();
    }

    try {
        g_client->Disconnect();
    } catch (...) {}
    g_ready.store(false);
    g_client.reset();
}

} // extern "C"

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <chrono>
#include <cstdint>
#include <inttypes.h>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <thread>
#include <utility>

#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"

namespace {
constexpr char kLogTag[] = "DiscordSocialSdk";

std::mutex clientMutex;
std::shared_ptr<discordpp::Client> client;
std::atomic<bool> callbacksRunning{false};
std::atomic<bool> debugLogging{false};
std::thread callbackThread;
uint64_t configuredApplicationId = 0;

void LogDebug(const char* message) {
    if (debugLogging.load()) {
        __android_log_print(ANDROID_LOG_DEBUG, kLogTag, "%s", message);
    }
}

int AndroidLogPriority(discordpp::LoggingSeverity severity) {
    switch (severity) {
        case discordpp::LoggingSeverity::Verbose:
            return ANDROID_LOG_VERBOSE;
        case discordpp::LoggingSeverity::Info:
            return ANDROID_LOG_INFO;
        case discordpp::LoggingSeverity::Warning:
            return ANDROID_LOG_WARN;
        case discordpp::LoggingSeverity::Error:
            return ANDROID_LOG_ERROR;
        case discordpp::LoggingSeverity::None:
        default:
            return ANDROID_LOG_DEBUG;
    }
}

void LogUpdateResult(const char* kind, discordpp::ClientResult result) {
    if (!debugLogging.load()) return;
    if (result.Successful()) {
        __android_log_print(
            ANDROID_LOG_DEBUG,
            kLogTag,
            "DiscordPresence update success kind=%s",
            kind);
        return;
    }
    const std::string error = result.Error();
    const std::string description = result.ToString();
    __android_log_print(
        ANDROID_LOG_ERROR,
        kLogTag,
        "DiscordPresence update failed kind=%s type=%s code=%" PRId32
        " status=%s retryable=%s error=%s result=%s",
        kind,
        discordpp::EnumToString(result.Type()),
        result.ErrorCode(),
        discordpp::EnumToString(result.Status()),
        result.Retryable() ? "true" : "false",
        error.c_str(),
        description.c_str());
}

void SubmitActivity(discordpp::Activity activity, const char* kind) {
    if (debugLogging.load()) {
        __android_log_print(
            ANDROID_LOG_DEBUG,
            kLogTag,
            "DiscordSocialSdkBridge -> JNI -> Client::UpdateRichPresence kind=%s",
            kind);
    }
    client->UpdateRichPresence(
        std::move(activity),
        [kind](discordpp::ClientResult result) {
            LogUpdateResult(kind, std::move(result));
        });
}

std::optional<std::string> JStringToOptional(JNIEnv* env, jstring value) {
    if (value == nullptr) return std::nullopt;
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return std::nullopt;
    const jsize length = env->GetStringLength(value);
    std::string result;
    result.reserve(static_cast<size_t>(length));
    for (jsize index = 0; index < length; ++index) {
        uint32_t codePoint = chars[index];
        if (codePoint >= 0xD800 && codePoint <= 0xDBFF && index + 1 < length) {
            const uint32_t low = chars[index + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                codePoint = 0x10000 + ((codePoint - 0xD800) << 10) + (low - 0xDC00);
                ++index;
            }
        }
        if (codePoint <= 0x7F) {
            result.push_back(static_cast<char>(codePoint));
        } else if (codePoint <= 0x7FF) {
            result.push_back(static_cast<char>(0xC0 | (codePoint >> 6)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else if (codePoint <= 0xFFFF) {
            result.push_back(static_cast<char>(0xE0 | (codePoint >> 12)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else {
            result.push_back(static_cast<char>(0xF0 | (codePoint >> 18)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 12) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        }
    }
    env->ReleaseStringChars(value, chars);
    if (result.empty()) return std::nullopt;
    return result;
}

void StartCallbackPump() {
    if (callbacksRunning.exchange(true)) return;
    callbackThread = std::thread([] {
        while (callbacksRunning.load()) {
            {
                std::lock_guard<std::mutex> lock(clientMutex);
                if (client) discordpp::RunCallbacks();
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
    });
}

void StopCallbackPump() {
    callbacksRunning.store(false);
    if (callbackThread.joinable()) callbackThread.join();
}
}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jagr_fridamusic_discord_DiscordSocialSdkBridge_nativeInitialize(
    JNIEnv*, jobject, jlong applicationId, jboolean debug) {
    debugLogging.store(debug == JNI_TRUE);
    if (applicationId <= 0) {
        LogDebug("initialization failed: invalid Application ID");
        return JNI_FALSE;
    }
    const auto requestedApplicationId = static_cast<uint64_t>(applicationId);
    std::lock_guard<std::mutex> lock(clientMutex);
    if (!client) {
        client = std::make_shared<discordpp::Client>();
        if (debugLogging.load()) {
            client->AddLogCallback(
                [](std::string message, discordpp::LoggingSeverity severity) {
                    __android_log_print(
                        AndroidLogPriority(severity),
                        kLogTag,
                        "SDK[%s] %s",
                        discordpp::EnumToString(severity),
                        message.c_str());
                },
                discordpp::LoggingSeverity::Verbose);
        }
        configuredApplicationId = requestedApplicationId;
        client->SetApplicationId(configuredApplicationId);
        if (debugLogging.load()) {
            __android_log_print(
                ANDROID_LOG_DEBUG,
                kLogTag,
                "Discord Social SDK version: %" PRId32 ".%" PRId32 ".%" PRId32,
                discordpp::Client::GetVersionMajor(),
                discordpp::Client::GetVersionMinor(),
                discordpp::Client::GetVersionPatch());
            __android_log_print(
                ANDROID_LOG_DEBUG,
                kLogTag,
                "Discord Social SDK Application ID: %" PRIu64,
                configuredApplicationId);
        }
        LogDebug("connect: Direct RPC client configured");
    } else if (configuredApplicationId != requestedApplicationId) {
        __android_log_print(
            ANDROID_LOG_ERROR,
            kLogTag,
            "initialization failed: Application ID mismatch configured=%" PRIu64
            " requested=%" PRIu64,
            configuredApplicationId,
            requestedApplicationId);
        return JNI_FALSE;
    }
    StartCallbackPump();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jagr_fridamusic_discord_DiscordSocialSdkBridge_nativeUpdateTestPresence(
    JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(clientMutex);
    if (!client) {
        LogDebug("minimal update failed: client unavailable");
        return JNI_FALSE;
    }

    discordpp::Activity activity;
    activity.SetName("FridaMusic");
    activity.SetType(discordpp::ActivityTypes::Playing);
    activity.SetDetails("Discord Social SDK Test");
    activity.SetState("Android Direct RPC");
    SubmitActivity(std::move(activity), "minimal-playing");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jagr_fridamusic_discord_DiscordSocialSdkBridge_nativeUpdatePresence(
    JNIEnv* env,
    jobject,
    jstring title,
    jstring state,
    jstring album,
    jstring artworkUrl,
    jstring detailsUrl,
    jlong startTimeSeconds,
    jlong endTimeSeconds,
    jboolean paused,
    jstring buttonLabel,
    jstring buttonUrl) {
    std::lock_guard<std::mutex> lock(clientMutex);
    if (!client) {
        LogDebug("update failed: client unavailable");
        return JNI_FALSE;
    }

    discordpp::Activity activity;
    activity.SetName("FridaMusic");
    activity.SetType(discordpp::ActivityTypes::Listening);
    activity.SetStatusDisplayType(discordpp::StatusDisplayTypes::Name);
    activity.SetSupportedPlatforms(discordpp::ActivityGamePlatforms::Android);
    activity.SetDetails(JStringToOptional(env, title));
    activity.SetState(JStringToOptional(env, state));

    const auto detailsUrlValue = JStringToOptional(env, detailsUrl);
    if (detailsUrlValue) activity.SetDetailsUrl(detailsUrlValue);

    const auto artwork = JStringToOptional(env, artworkUrl);
    const auto albumValue = JStringToOptional(env, album);
    if (artwork || albumValue) {
        discordpp::ActivityAssets assets;
        if (artwork) {
            assets.SetLargeImage(artwork);
            assets.SetLargeUrl(artwork);
        }
        if (albumValue) assets.SetLargeText(albumValue);
        activity.SetAssets(std::move(assets));
    }

    if (paused != JNI_TRUE && startTimeSeconds > 0) {
        discordpp::ActivityTimestamps timestamps;
        timestamps.SetStart(static_cast<uint64_t>(startTimeSeconds));
        if (endTimeSeconds > startTimeSeconds) {
            timestamps.SetEnd(static_cast<uint64_t>(endTimeSeconds));
        }
        activity.SetTimestamps(std::move(timestamps));
    }

    const auto label = JStringToOptional(env, buttonLabel);
    const auto url = JStringToOptional(env, buttonUrl);
    if (label && url) {
        discordpp::ActivityButton button;
        button.SetLabel(*label);
        button.SetUrl(*url);
        activity.AddButton(std::move(button));
    }

    SubmitActivity(std::move(activity), "dynamic-listening");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_jagr_fridamusic_discord_DiscordSocialSdkBridge_nativeClearPresence(
    JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(clientMutex);
    if (client) {
        client->ClearRichPresence();
        LogDebug("clear");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_jagr_fridamusic_discord_DiscordSocialSdkBridge_nativeShutdown(
    JNIEnv*, jobject) {
    {
        std::lock_guard<std::mutex> lock(clientMutex);
        if (client) client->ClearRichPresence();
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(150));
    StopCallbackPump();
    {
        std::lock_guard<std::mutex> lock(clientMutex);
        client.reset();
        configuredApplicationId = 0;
    }
    LogDebug("shutdown");
}

# Discord Rich Presence on Android

FridaMusic publishes Rich Presence through the official Discord Social SDK Direct RPC path:

`MusicService -> DiscordPresenceManager -> DiscordSocialSdkBridge -> JNI -> discordpp::Client`

This integration targets Discord Social SDK `1.10.18687` (minimum `1.10`, when unauthenticated Android RPC became available). It calls `Client::SetApplicationId`, builds a `discordpp::Activity`, and calls `Client::UpdateRichPresence`. It does not call `Client::Connect`, open the Discord Gateway, send `IDENTIFY`, or use an OAuth access or refresh token.

## SDK installation

The Android package is distributed by Discord from the application page in the authenticated Developer Portal. Download the official package, copy its Android AAR to:

`app/libs/discord_partner_sdk.aar`

The app module detects that exact file. When present it enables Prefab, adds the AAR dependency, and builds `app/src/main/cpp/discord/DiscordPresenceBridge.cpp` through CMake. Without it, Kotlin builds remain usable and Rich Presence fails closed without affecting playback.

The bridge contains the single `DISCORDPP_IMPLEMENTATION` definition required by the generated C++ header. `MainActivity` provides the current Activity to `DiscordSocialSdkInit.setEngineActivity`; no Activity is retained by FridaMusic. A small native callback pump runs while Presence is enabled because the SDK requires `discordpp::RunCallbacks()`.

## Application and lifecycle

The existing Discord Application ID is `1543937368900370554`. It is public configuration, not a secret.

OAuth2 with PKCE remains only for optional account linking and profile display. Direct Rich Presence does not depend on linking.

`MusicService` requests event-driven updates for track/metadata changes, play, pause/resume, significant seek, playback-speed changes, and screen return. Updates are debounced and there is no playback polling. Pause keeps the track visible without advancing timestamps; resume and seek recalculate Unix-second start/end timestamps. Stop, end, disabling the setting, and service destruction clear Presence.

The activity uses the real title, artists, album, and HTTP(S) artwork resolved by `DiscordImageResolver`, plus one existing public FridaMusic URL. Missing artwork, Discord not installed, Discord not signed in, unavailable RPC, or an SDK rejection cannot fail music playback.

## Manual verification

1. Install and sign in to Discord on an Android 7.0+ device.
2. Install a build that contains the official AAR and enable Rich Presence.
3. Play, pause, resume, seek, change tracks, stop playback, and disable the setting.
4. Confirm the Discord profile updates and clears as expected.

Official references:

- https://docs.discord.com/developers/discord-social-sdk/development-guides/setting-rich-presence
- https://docs.discord.com/developers/discord-social-sdk/core-concepts/platform-compatibility
- https://docs.discord.com/developers/discord-social-sdk/development-guides/account-linking-on-mobile
- https://discord.com/developers/docs/social-sdk/release_notes.html

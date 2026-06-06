# Consumer ProGuard rules for the core library module.

# NewPipeExtractor is used to turn supported remote trailer pages into playable media URLs.
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn org.mozilla.javascript.**
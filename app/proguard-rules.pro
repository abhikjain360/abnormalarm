# Abnormalarm ProGuard/R8 rules.
# Compose + AndroidX ship their own consumer rules; we keep this minimal.

# Keep BroadcastReceivers/Services referenced only from the manifest (R8 can't always
# see manifest-only entry points across all configs; be explicit for the alarm core).
-keep class com.abhikjain360.abnormalarm.scheduling.** { *; }
-keep class com.abhikjain360.abnormalarm.ring.** { *; }

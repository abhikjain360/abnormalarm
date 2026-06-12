{
  # Abnormalarm dev shell.
  #
  # Philosophy (see DESIGN.md §11): Nix provides the *reproducible build toolchain*
  # (JDK + Gradle to bootstrap the wrapper). It deliberately does NOT vendor the
  # multi-GB Android SDK or an emulator — we reuse the host Android Studio install
  # at ~/Library/Android/sdk and the host's hardware-accelerated AVD. "Best of both
  # worlds": reproducible builds, platform-native emulator.
  description = "Abnormalarm — Android alarm clock dev shell (build toolchain only; host SDK + host emulator)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.05";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        jdk = pkgs.jdk17;
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            jdk
            pkgs.gradle # only to bootstrap ./gradlew; actual builds use the pinned wrapper (Gradle 9.1)
          ];

          # Point the build at the HOST Android SDK rather than a Nix-managed one.
          shellHook = ''
            export JAVA_HOME="${jdk}"
            export ANDROID_HOME="''${ANDROID_HOME:-$HOME/Library/Android/sdk}"
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
            # Gradle reads sdk.dir from local.properties or ANDROID_HOME; we rely on ANDROID_HOME.

            if [ ! -d "$ANDROID_HOME" ]; then
              echo "⚠️  ANDROID_HOME ($ANDROID_HOME) not found."
              echo "    Install Android Studio and its SDK, or set ANDROID_HOME to your SDK path."
            elif [ ! -d "$ANDROID_HOME/platforms/android-36" ]; then
              echo "⚠️  Android platform 36 not found under $ANDROID_HOME/platforms."
              echo "    Install it via Android Studio > SDK Manager (compileSdk = 36)."
            else
              echo "✅ Abnormalarm dev shell — JDK 17, host SDK at $ANDROID_HOME"
              echo "   Build:  ./gradlew installDebug"
              echo "   Test:   ./gradlew :app:testDebugUnitTest"
              echo "   Emu:    emulator -avd draftbros_-_Pixel_9 &   (or launch from Android Studio)"
            fi
          '';
        };
      });
}

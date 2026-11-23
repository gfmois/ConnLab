#!/usr/bin/env bash
# Builds the Windows installer target/dist/ConnLab-<version>.msi on Linux, so the release runs
# on the self-hosted Linux runner. It does by hand what jpackage does on Windows:
#
#   1. Maven builds the jar and copies the dependencies, with the Windows JavaFX jars.
#   2. jlink, from a Linux JDK, links a Windows runtime from the jmods of the Windows JDK of
#      the same release (jlink needs both to be the same version).
#   3. The application image is the one jpackage makes: ConnLab.exe (the jpackage launcher of
#      the Windows JDK, with the ConnLab icon and version), app/ (jars + ConnLab.cfg) and
#      runtime/.
#   4. wixl (msitools) packs it into the MSI, with the same UpgradeCode as the MSIs jpackage
#      made, so installing a new version replaces the old one.
#
# Needs curl, jq, tar, unzip, wixl, wixl-heat and node/npm on the PATH. The JDKs are downloaded
# once into the runner's tool cache. Prints the path of the MSI on the last line.
set -euo pipefail

NAME=ConnLab
VENDOR="Moises Guerola"
UPGRADE_CODE=654E4008-CAC7-345F-9D50-C1D20F16AF4D   # from the jpackage MSIs; never change it
JAVA_FEATURE=21
MODULES=java.base,java.sql,java.desktop,java.prefs,javafx.controls,javafx.fxml,javafx.graphics
ADOPTIUM=https://api.adoptium.net/v3

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
cache="${RUNNER_TOOL_CACHE:-$HOME/.cache}/connlab-release"
work="$root/target/msi"
mkdir -p "$cache"

log() { echo "==> $*" >&2; }

# Downloads (once) the Temurin JDK <release> for <os> into the cache and prints its home.
jdk() {
  local release=$1 os=$2 dir="$cache/$1-$2"
  if [ ! -f "$dir/release" ]; then
    local asset link sum file tmp
    asset="$(curl -fsSL "$ADOPTIUM/assets/release_name/eclipse/$(jq -rn --arg r "$release" '$r|@uri')?os=$os&architecture=x64&image_type=jdk&project=jdk")"
    link="$(jq -r '.binaries[0].package.link' <<< "$asset")"
    sum="$(jq -r '.binaries[0].package.checksum' <<< "$asset")"
    [ -n "$link" ] && [ "$link" != null ] || { echo "No $os JDK for $release" >&2; exit 1; }
    log "Downloading the $os JDK $release"
    tmp="$(mktemp -d "$cache/tmp.XXXXXX")"
    file="$tmp/${link##*/}"
    curl -fsSL -o "$file" "$link"
    echo "$sum  $file" | sha256sum -c --quiet -
    case "$file" in
      *.zip) unzip -q "$file" -d "$tmp/x" ;;
      *) mkdir "$tmp/x" && tar -xzf "$file" -C "$tmp/x" ;;
    esac
    rm -rf "$dir" && mv "$tmp/x/"* "$dir" && rm -rf "$tmp"
  fi
  echo "$dir"
}

# The latest Java 21 release, as long as there is a Windows build of it.
release="$(curl -fsSL "$ADOPTIUM/assets/latest/$JAVA_FEATURE/hotspot?os=windows&architecture=x64&image_type=jdk" | jq -r '.[0].release_name')"
linux_jdk="$(jdk "$release" linux)"
windows_jdk="$(jdk "$release" windows)"
export JAVA_HOME="$linux_jdk"

cd "$root"
version="$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)"
log "Building $NAME $version with $release"

# The tests already ran in CI, and with the Windows JavaFX jars they cannot run here.
./mvnw -B -q -Djavafx.platform=win -DskipTests clean package
libs="$root/target/libs"
main_jar="connlab-$version.jar"
[ -f "$libs/$main_jar" ] || { echo "No $main_jar in target/libs" >&2; exit 1; }

image="$work/image/$NAME"
rm -rf "$work" && mkdir -p "$image/app"

log "Linking the Windows runtime"
"$JAVA_HOME/bin/jlink" --module-path "$windows_jdk/jmods:$libs" --add-modules "$MODULES" \
  --strip-native-commands --strip-debug --no-man-pages --no-header-files \
  --output "$image/runtime"

cp "$libs"/*.jar "$image/app/"
{
  echo "[Application]"
  echo "app.classpath=\$APPDIR\\$main_jar"
  echo "app.mainclass=io.github.gfmois.connlab.Launcher"
  for jar in "$image/app"/*.jar; do
    jar="${jar##*/}"
    [ "$jar" = "$main_jar" ] || echo "app.classpath=\$APPDIR\\$jar"
  done
  echo
  echo "[JavaOptions]"
  echo "java-options=-Djpackage.app-version=$version"
} | sed 's/$/\r/' > "$image/app/$NAME.cfg"
printf '%s' "$NAME" > "$image/app/.package"

log "Preparing the launcher"
"$JAVA_HOME/bin/jmod" extract --dir "$work/jpackage" "$windows_jdk/jmods/jdk.jpackage.jmod"
launcher="$(find "$work/jpackage" -name jpackageapplauncherw.exe | head -1)"
[ -n "$launcher" ] || { echo "No jpackage launcher in the Windows JDK" >&2; exit 1; }
cp "$launcher" "$image/$NAME.exe"
npm install --prefix "$cache/node" --no-save --no-audit --no-fund --silent resedit@3.1.1
NODE_PATH="$cache/node/node_modules" node "$here/exe-resources.cjs" "$image/$NAME.exe" \
  "$root/src/main/resources/favicon.ico" "$version" "$NAME" "$VENDOR"

log "Packing the MSI"
(cd "$work/image" && find "$NAME" -type f ! -path "$NAME/$NAME.exe" | sort) \
  | wixl-heat -p "$NAME/" --win64 --var var.ImageDir --directory-ref INSTALLDIR \
      --component-group AppFiles > "$work/files.wxs"
mkdir -p "$root/target/dist"
msi="$root/target/dist/$NAME-$version.msi"
wixl -a x64 -o "$msi" \
  -D ImageDir="$work/image/$NAME" -D Version="$version" -D Name="$NAME" -D Vendor="$VENDOR" \
  -D Win64=yes -D UpgradeCode="$UPGRADE_CODE" -D Icon="$root/src/main/resources/favicon.ico" \
  "$here/installer.wxs" "$work/files.wxs"
log "Built $msi ($(du -h "$msi" | cut -f1))"
echo "$msi"

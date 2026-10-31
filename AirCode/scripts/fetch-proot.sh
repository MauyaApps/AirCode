#!/usr/bin/env bash
set -uo pipefail

REPO_URL="${PROOT_REPO_URL:-https://github.com/green-green-avk/build-proot-android}"
OUT="${PROOT_OUT_DIR:-terminal/src/main/jniLibs}"
REQUIRED="${PROOT_REQUIRED:-0}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if [ -n "${PROOT_REPO_DIR:-}" ]; then
  REPO="$PROOT_REPO_DIR"
else
  REPO="$WORK/repo"
  if ! git clone --depth 1 "$REPO_URL" "$REPO"; then
    echo "proot: не удалось клонировать $REPO_URL"
    [ "$REQUIRED" = "1" ] && exit 1
    exit 0
  fi
fi

SEARCH="$WORK/search"
mkdir -p "$SEARCH"
ln -s "$REPO" "$SEARCH/repo"

n=0
while IFS= read -r archive; do
  n=$((n + 1))
  target="$SEARCH/extracted/$(basename "$archive")-$n"
  mkdir -p "$target"
  case "$archive" in
    *.tar.gz | *.tgz) tar -xzf "$archive" -C "$target" 2>/dev/null ;;
    *.tar.xz) tar -xJf "$archive" -C "$target" 2>/dev/null ;;
    *.tar) tar -xf "$archive" -C "$target" 2>/dev/null ;;
    *.zip) unzip -q -o "$archive" -d "$target" 2>/dev/null ;;
  esac
done < <(find -L "$REPO" -type f \( -name '*.tar.gz' -o -name '*.tgz' -o -name '*.tar.xz' -o -name '*.tar' -o -name '*.zip' \) -not -path '*/.git/*')

declare -A ARCH_PATTERN=(
  [arm64-v8a]='(^|[^a-z0-9_])(aarch64|arm64)([^a-z0-9_]|$)'
  [armeabi-v7a]='(^|[^a-z0-9_])(arm|armv7|armv7a|armeabi-v7a)([^a-z0-9_]|$)'
  [x86_64]='(^|[^a-z0-9_])(x86_64|amd64)([^a-z0-9_]|$)'
  [x86]='(^|[^a-z0-9_])(x86|i686|i386)([^a-z0-9_]|$)'
)

found=0
for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  pattern="${ARCH_PATTERN[$abi]}"
  proot=""
  loader=""
  while IFS= read -r candidate; do
    rel="${candidate#"$SEARCH"/}"
    if echo "$rel" | grep -Eq "$pattern"; then
      proot="$candidate"
      break
    fi
  done < <(find -L "$SEARCH" -type f -name proot -not -path '*/.git/*' 2>/dev/null | sort)
  if [ -z "$proot" ]; then
    echo "proot: для $abi бинарник не найден"
    continue
  fi
  base="$(dirname "$(dirname "$proot")")"
  for guess in "$base/libexec/proot/loader" "$(dirname "$proot")/loader" "$base/libexec/loader"; do
    if [ -f "$guess" ]; then
      loader="$guess"
      break
    fi
  done
  if [ -z "$loader" ]; then
    loader="$(find -L "$base" -type f -name loader 2>/dev/null | head -n 1)"
  fi
  if [ -z "$loader" ]; then
    echo "proot: для $abi найден proot, но нет loader"
    continue
  fi
  mkdir -p "$OUT/$abi"
  cp "$proot" "$OUT/$abi/libproot.so"
  cp "$loader" "$OUT/$abi/libproot-loader.so"
  chmod 755 "$OUT/$abi/libproot.so" "$OUT/$abi/libproot-loader.so"
  echo "proot: $abi готов ($proot, $loader)"
  found=$((found + 1))
done

if [ "$found" -eq 0 ]; then
  echo "proot: не найдено ни одного бинарника, содержимое репозитория:"
  find -L "$REPO" -maxdepth 3 -not -path '*/.git/*' | head -n 80
  [ "$REQUIRED" = "1" ] && exit 1
fi
exit 0

#!/usr/bin/env bash
# Prints the "-t <repository>:<tag>" arguments buildx pushes for one image of a release.
#
#   docker-release-tags.sh <image> <version> <flavor>
#
#   image    am-gateway, am-management-api or am-management-ui (the Docker Hub repository is graviteeio/<image>)
#   version  the released version, x.y.z
#   flavor   the base image of this build: alpine, noble or debian; empty for an image with a single flavor (the UI)
#
# Environment: TAG_LATEST_SUPPORT=true also tags x.y; TAG_LATEST=true also tags x and latest.
#
# Every flavor gets its tags with a "-<flavor>" suffix. The default flavor, the one a user gets with a tag without
# suffix, also gets the tags without suffix, from the same build so both point to the same digest.
set -Eeuo pipefail

DEFAULT_FLAVOR="alpine"

image="${1:-}"
version="${2:-}"
flavor="${3:-}"

if [[ -z "${image}" || -z "${version}" ]]; then
  echo "usage: $0 <image> <version> <flavor>" >&2
  exit 2
fi
if [[ ! "${version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$ ]]; then
  echo "version must be x.y.z, got '${version}'" >&2
  exit 2
fi
case "${flavor}" in
  ''|alpine|noble|debian) ;;
  *) echo "unknown flavor '${flavor}': expected alpine, noble, debian or nothing" >&2; exit 2 ;;
esac

major="${version%%.*}"
minor="${version#*.}"; minor="${minor%%.*}"

tags=("${version}")
if [[ "${TAG_LATEST_SUPPORT:-false}" == "true" ]]; then
  tags+=("${major}.${minor}")
fi
if [[ "${TAG_LATEST:-false}" == "true" ]]; then
  tags+=("${major}" "latest")
fi

suffixes=()
if [[ -z "${flavor}" || "${flavor}" == "${DEFAULT_FLAVOR}" ]]; then
  suffixes+=("")
fi
if [[ -n "${flavor}" ]]; then
  suffixes+=("-${flavor}")
fi

args=()
for suffix in "${suffixes[@]}"; do
  for tag in "${tags[@]}"; do
    args+=("-t" "graviteeio/${image}:${tag}${suffix}")
  done
done
echo "${args[*]}"

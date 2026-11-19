#!/usr/bin/env bash
# Tests of .circleci/scripts/docker-release-tags.sh: the Docker Hub tags pushed by a release.
#
# Run from anywhere: .circleci/scripts/tests/docker-release-tags.test.sh
set -Eeuo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="${HERE}/../docker-release-tags.sh"
WORKFLOWS="${HERE}/../../workflows.yml"

failures=0
pass() { echo "ok   - $1"; }
fail() { echo "FAIL - $1"; failures=$((failures + 1)); }

# assert_tags <description> <expected tags, space separated> <actual output>
# Compares the tags as sets: order does not matter, duplicates and extras do.
assert_tags() {
  local description="$1" expected="$2" actual="$3"
  local expected_sorted actual_sorted
  expected_sorted=$(tr ' ' '\n' <<<"${expected}" | sed '/^$/d' | sort)
  actual_sorted=$(tr ' ' '\n' <<<"${actual}" | sed '/^$/d' | sort)
  if [[ "${expected_sorted}" == "${actual_sorted}" ]]; then
    pass "${description}"
  else
    fail "${description}"
    echo "       expected: ${expected}"
    echo "       actual:   ${actual}"
  fi
}

# tags <image> <version> <flavor> <TAG_LATEST_SUPPORT> <TAG_LATEST>
# Prints the tags (without the -t and the repository) the script asks buildx to push.
tags() {
  TAG_LATEST_SUPPORT="$4" TAG_LATEST="$5" "${SCRIPT}" "$1" "$2" "$3" \
    | tr ' ' '\n' | grep -v '^-t$' | sed "s#^graviteeio/$1:##" | tr '\n' ' '
}

if [[ ! -x "${SCRIPT}" ]]; then
  fail "${SCRIPT} exists and is executable"
  echo "${failures} failure(s)"
  exit 1
fi

# --- Alpine build of the backend images: tags without suffix and -alpine tags, same build -------------------

assert_tags "gateway alpine, latest support and latest: plain and -alpine tags" \
  "4.14.0 4.14 4 latest 4.14.0-alpine 4.14-alpine 4-alpine latest-alpine" \
  "$(tags am-gateway 4.14.0 alpine true true)"

assert_tags "management-api alpine, latest support only: no major nor latest tag" \
  "4.13.6 4.13 4.13.6-alpine 4.13-alpine" \
  "$(tags am-management-api 4.13.6 alpine true false)"

assert_tags "gateway alpine, hotfix (no latest flags): version tags only" \
  "4.12.14 4.12.14-alpine" \
  "$(tags am-gateway 4.12.14 alpine false false)"

# --- Other flavors keep their suffixed tags only -------------------------------------------------------------

assert_tags "gateway debian, latest support and latest: -debian tags only" \
  "4.14.0-debian 4.14-debian 4-debian latest-debian" \
  "$(tags am-gateway 4.14.0 debian true true)"

assert_tags "management-api noble, latest support only: -noble tags only" \
  "4.14.0-noble 4.14-noble" \
  "$(tags am-management-api 4.14.0 noble true false)"

# --- The UI has a single flavor and no -alpine tag -----------------------------------------------------------

assert_tags "management-ui, latest support and latest: plain tags only" \
  "4.14.0 4.14 4 latest" \
  "$(tags am-management-ui 4.14.0 '' true true)"

# --- Errors ----------------------------------------------------------------------------------------------------

if TAG_LATEST_SUPPORT=true TAG_LATEST=true "${SCRIPT}" am-gateway 4.14.0 windows >/dev/null 2>&1; then
  fail "unknown flavor is rejected"
else
  pass "unknown flavor is rejected"
fi

if TAG_LATEST_SUPPORT=true TAG_LATEST=true "${SCRIPT}" am-gateway 4.14 alpine >/dev/null 2>&1; then
  fail "version without a patch number is rejected"
else
  pass "version without a patch number is rejected"
fi

# --- The release job uses the script for every image and flavor --------------------------------------------

for image_flavor in "am-gateway alpine" "am-gateway noble" "am-gateway debian" \
                    "am-management-api alpine" "am-management-api noble" "am-management-api debian" \
                    "am-management-ui ''"; do
  image="${image_flavor% *}"
  flavor="${image_flavor#* }"
  if grep -q -F "docker-release-tags.sh ${image} \"\${RELEASE_VERSION}\" ${flavor})" "${WORKFLOWS}"; then
    pass "workflows.yml computes the tags of ${image_flavor} with the script"
  else
    fail "workflows.yml computes the tags of ${image_flavor} with the script"
  fi
done

echo
if [[ ${failures} -gt 0 ]]; then
  echo "${failures} failure(s)"
  exit 1
fi
echo "all tests passed"

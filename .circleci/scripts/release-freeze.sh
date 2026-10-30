#!/bin/bash
set -euo pipefail

ACTION="${1:?usage: release-freeze.sh <freeze|unfreeze> <branch>}"
BRANCH="${2:?usage: release-freeze.sh <freeze|unfreeze> <branch>}"
REPOSITORY="${RELEASE_FREEZE_REPOSITORY:-gravitee-io/gravitee-access-management}"
DEFAULT_BYPASS_ACTORS='[{"actor_id":5,"actor_type":"RepositoryRole","bypass_mode":"always"}]'
BYPASS_ACTORS="${RELEASE_FREEZE_BYPASS_ACTORS:-${DEFAULT_BYPASS_ACTORS}}"
RULESET_NAME="release-freeze-${BRANCH}"
API="https://api.github.com/repos/${REPOSITORY}/rulesets"

if [ "${DRY_RUN:-true}" == "true" ]; then
  echo "Dry run: skipping ${ACTION} of [${BRANCH}]"
  exit 0
fi

if ! [[ "${BRANCH}" =~ ^[0-9]+\.[0-9]+\.x$ ]]; then
  echo "[${BRANCH}] is not a maintenance branch: skipping ${ACTION}"
  exit 0
fi

github() {
  curl --fail-with-body -sS \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "$@"
}

ruleset_id() {
  github "${API}?includes_parents=false&per_page=100" \
    | jq -r --arg name "${RULESET_NAME}" '.[] | select(.name == $name) | .id'
}

RULESET_ID="$(ruleset_id)"

case "${ACTION}" in
  freeze)
    if [ -n "${RULESET_ID}" ]; then
      echo "[${BRANCH}] already frozen by ruleset ${RULESET_ID}"
      exit 0
    fi
    jq -n \
      --arg name "${RULESET_NAME}" \
      --arg ref "refs/heads/${BRANCH}" \
      --argjson bypass "${BYPASS_ACTORS}" \
      '{
        name: $name,
        target: "branch",
        enforcement: "active",
        bypass_actors: $bypass,
        conditions: { ref_name: { include: [$ref], exclude: [] } },
        rules: [ { type: "update" }, { type: "deletion" } ]
      }' \
      | github -X POST "${API}" --data @- > /dev/null
    echo "[${BRANCH}] frozen"
    ;;
  unfreeze)
    if [ -z "${RULESET_ID}" ]; then
      echo "[${BRANCH}] is not frozen"
      exit 0
    fi
    github -X DELETE "${API}/${RULESET_ID}"
    echo "[${BRANCH}] unfrozen"
    ;;
  *)
    echo "Unknown action [${ACTION}], expected freeze or unfreeze"
    exit 1
    ;;
esac

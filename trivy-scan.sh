#!/usr/bin/env bash
set -euo pipefail

MAINTAINED_VERSION=(3.18 3.19 3.20 3.21 latest)
COMPONENTS=(am-gateway am-management-api am-management-ui)
REGISTRY="graviteeio"

for version in "${MAINTAINED_VERSION[@]}"
do
    for component in "${COMPONENTS[@]}"
    do
        docker_image="${REGISTRY}/${component}:${version}"
        echo "##### Scan ${docker_image}:"
        docker pull --quiet "${docker_image}"
        trivy image --severity HIGH,CRITICAL --quiet --format table "${docker_image}"
    done
done

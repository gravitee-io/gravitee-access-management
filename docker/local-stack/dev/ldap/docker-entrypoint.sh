#!/bin/sh
#
# Copyright (C) 2015 The Gravitee team (http://gravitee.io)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e

if [ "$#" -gt 0 ]; then
    exec "$@"
fi

CONFIG_DIR=/etc/openldap/slapd.d
SEED_LDIF=/ldifs/gravitee-io.ldif
DATA_DIR=/var/lib/openldap/openldap-data
# Written last, so a start that failed part-way is seeded again from scratch.
SEEDED_MARKER=/var/lib/openldap/.seeded

if [ ! -f "$SEEDED_MARKER" ]; then
    rm -rf "${CONFIG_DIR:?}"/* "${DATA_DIR:?}"/*
    echo "==> Loading slapd config"
    slapadd -n0 -F "$CONFIG_DIR" -l /etc/openldap/slapd-config.ldif
    echo "==> Loading seed tree from $SEED_LDIF"
    slapadd -n1 -F "$CONFIG_DIR" -l "$SEED_LDIF"
    touch "$SEEDED_MARKER"
else
    echo "==> slapd already initialized, skipping seed"
fi

# slapd runs unprivileged, so it cannot bind 389.
exec slapd -d 0 -F "$CONFIG_DIR" -h "ldap://0.0.0.0:1389/"

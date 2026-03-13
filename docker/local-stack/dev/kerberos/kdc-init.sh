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

REALM="GRAVITEE.LOCAL"
KDC_PASSWORD="admin123"
SERVICE_PRINCIPAL="HTTP/gateway@${REALM}"
TEST_USER="testuser"
TEST_USER_PASSWORD="Password1!"
KEYTAB_PATH="/var/lib/krb5kdc/am.keytab"

if [ -f /var/lib/krb5kdc/principal ]; then
    echo "==> Kerberos realm already exists, skipping creation"
else
    echo "==> Initializing Kerberos realm: ${REALM}"
    kdb5_util create -s -r ${REALM} -P ${KDC_PASSWORD}
fi

echo "==> Starting KDC and kadmind"
krb5kdc
kadmind

echo "==> Creating service principal: ${SERVICE_PRINCIPAL}"
kadmin.local -q "addprinc -randkey ${SERVICE_PRINCIPAL}"

echo "==> Exporting keytab to ${KEYTAB_PATH}"
kadmin.local -q "ktadd -k ${KEYTAB_PATH} ${SERVICE_PRINCIPAL}"
chmod 644 ${KEYTAB_PATH}

echo "==> Creating test user: ${TEST_USER}@${REALM}"
kadmin.local -q "addprinc -pw ${TEST_USER_PASSWORD} ${TEST_USER}@${REALM}"

echo "==> Verifying keytab"
klist -kt ${KEYTAB_PATH}

echo "==> KDC ready. Principals:"
kadmin.local -q "listprincs"

echo "==> Keeping container alive..."
tail -f /var/log/krb5kdc.log 2>/dev/null || tail -f /dev/null

#!/bin/bash
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


setup() {
    echo "Configure management API and management UI"
    cat /var/www/html/constants.json.template | \
    sed "s#http://localhost:8093/#${MGMT_API_URL}#g;s#http://localhost:4200/#${MGMT_UI_URL}#g" > /var/www/html/constants.json

    [[ -n $BASEPATH ]] && sed -i "s%<base href=\"/\">%<base href=\"$BASEPATH\">%g" /var/www/html/index.html
}

setup
exec "$@"
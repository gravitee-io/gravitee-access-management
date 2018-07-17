#!/bin/bash

setup() {
    echo "Configure management API and management UI"
    cat /var/www/html/constants.json.template | \
    sed "s#http://localhost:8093/#${MGMT_API_URL}#g;s#http://localhost:4200/#${MGMT_UI_URL}#g" > /var/www/html/constants.json
}

setup
exec "$@"
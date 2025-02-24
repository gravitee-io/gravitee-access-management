#!/bin/bash

SERVICE_HOME_DIR=$1

PID_FILE="/tmp/workspace/graviteeio.pid"
SERVICE_PORT=8093

if [ ${SERVICE_HOME_DIR} == "gravitee-am-gateway-standalone*" ]; then
  SERVICE_PORT=8092
fi

${SERVICE_HOME_DIR}/bin/gravitee -d -p={$PID_FILE}

timeout 240s bash -c "until nc -z localhost ${SERVICE_PORT}; do sleep 2; done"

retVal=$?
if [ $retVal -ne 0 ]; then
  kill `cat $PID_FILE`
  sleep 5
  ${SERVICE_HOME_DIR}/bin/gravitee -d -p={$PID_FILE}
fi

timeout 240s bash -c "until nc -z localhost ${SERVICE_PORT}; do sleep 2; done"
retVal=$?
if [ $retVal -ne 0 ]; then
  kill `cat $PID_FILE`
  echo "STOP CI: AM Service doesn't start from ${SERVICE_HOME_DIR}"
  exit 1
fi

# wait 15 sec so the service can be fully started
sleep 15

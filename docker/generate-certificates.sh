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

if [[ $# != 4 ]]; then
  echo 'usage   : generate-certificates <dest_dir> <RootCA subj> <Server subj> <Client subj>'
  echo 'Example : generate-certificates /tmp "/CN=127.0.0.1" "/CN=127.0.0.1" "/CN=127.0.0.1"'
  echo 'Example : generate-certificates /tmp "/CN=domain.com" "/CN=server.domain.com" "/CN=client.domain.com"'
  exit 1
fi

generate_rootCA () {

  CERT_DIR=$1
  OPENSSL_ROOT_CA=$2

  echo "### Generate RootCA certificate: ###"

  # RSA
  openssl genrsa 2048 > ${CERT_DIR}/ca.key.pem
  openssl req -new -x509 -nodes -days 3600 -subj "${OPENSSL_ROOT_CA}" -key ${CERT_DIR}/ca.key.pem -out ${CERT_DIR}/ca.crt.pem

  # EC
  #openssl ecparam -name prime256v1 -genkey -noout -out ${CERT_DIR}/ca.key.pem
  #openssl req -new -x509 -sha256 -days 365 -key ${CERT_DIR}/ca.key.pem -out ${CERT_DIR}/ca.crt.pem -subj ${OPENSSL_ROOT_CA}
}

generate_server_cert () {
  CERT_DIR=$1
  OPENSSL_SERVER=$2

  echo "### Generate Server certificate: ###"

  # RSA

  openssl req -newkey rsa:2048 -days 365 -nodes -subj "${OPENSSL_SERVER}" -keyout ${CERT_DIR}/server.key.pem -out ${CERT_DIR}/server.req.pem
  openssl rsa -in ${CERT_DIR}/server.key.pem -out ${CERT_DIR}/server.key.pem
  openssl x509 -req -in ${CERT_DIR}/server.req.pem -days 365 -CA ${CERT_DIR}/ca.crt.pem -CAkey ${CERT_DIR}/ca.key.pem -set_serial 01 -out ${CERT_DIR}/server.crt.pem

  # EC
  #openssl ecparam -name prime256v1 -genkey -noout -out ${CERT_DIR}/server.key.pem
  #openssl req -new -sha256 -key ${CERT_DIR}/server.key.pem -out ${CERT_DIR}/server.csr -subj ${OPENSSL_SERVER}
  #openssl x509 -req -in ${CERT_DIR}/server.csr -CA ${CERT_DIR}/ca.crt.pem -CAkey ${CERT_DIR}/ca.key.pem -CAcreateserial -out ${CERT_DIR}/server.crt.pem -days 365 -sha256

}

generate_client_cert () {
  CERT_DIR=$1
  OPENSSL_CLIENT=$2

  echo "### Generate Client certificate: ###"

  # RSA

  openssl req -newkey rsa:2048 -days 365 -nodes -subj "${OPENSSL_CLIENT}" -keyout ${CERT_DIR}/client.key.pem -out ${CERT_DIR}/client.req.pem
  openssl rsa -in ${CERT_DIR}/client.key.pem -out ${CERT_DIR}/client.key.pem
  openssl x509 -req -in ${CERT_DIR}/client.req.pem -days 365 -CA ${CERT_DIR}/ca.crt.pem -CAkey ${CERT_DIR}/ca.key.pem -set_serial 01 -out ${CERT_DIR}/client.crt.pem

  # EC

  #openssl ecparam -name prime256v1 -genkey -noout -out ${CERT_DIR}/client.key.pem
  #openssl req -new -sha256 -key ${CERT_DIR}/client.key.pem -out ${CERT_DIR}/client.csr -subj ${OPENSSL_SERVER}
  #openssl x509 -req -in ${CERT_DIR}/client.csr -CA ${CERT_DIR}/ca.crt.pem -CAkey ${CERT_DIR}/ca.key.pem -CAcreateserial -out ${CERT_DIR}/client.crt.pem -days 365 -sha256

}

create_dir () {

  mkdir -p $1

}

create_dir $1
generate_rootCA $1 $2
generate_server_cert $1 $3
generate_client_cert $1 $4

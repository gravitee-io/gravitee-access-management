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

FROM alpine:3.22

# bash: for the openldap-init script in docker-compose-ldap.yml.
RUN apk add --no-cache \
      bash \
      openldap \
      openldap-back-mdb \
      openldap-clients \
      openldap-overlay-memberof && \
    mkdir -p /var/lib/openldap/openldap-data /etc/openldap/slapd.d /run/openldap && \
    chown -R ldap:ldap /var/lib/openldap /etc/openldap/slapd.d /run/openldap

COPY slapd-config.ldif /etc/openldap/slapd-config.ldif
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

USER ldap

EXPOSE 1389

ENTRYPOINT ["/docker-entrypoint.sh"]

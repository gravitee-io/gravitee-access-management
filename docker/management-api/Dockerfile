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

FROM alpine:3.22 AS build-env
LABEL maintainer="contact@graviteesource.com"

ARG GRAVITEEAM_VERSION=0

RUN apk update \
	&& apk add --update --no-cache unzip netcat-openbsd wget \
	&& wget --no-check-certificate  -O /tmp/gravitee-am-management-api-standalone-${GRAVITEEAM_VERSION}.zip https://download.gravitee.io/graviteeio-am/components/gravitee-am-management-api/gravitee-am-management-api-standalone-${GRAVITEEAM_VERSION}.zip \
    && unzip /tmp/gravitee-am-management-api-standalone-${GRAVITEEAM_VERSION}.zip -d /gravitee-tmp/ \
    && apk del unzip netcat-openbsd wget \
    && rm -rf /var/cache/apk/* /tmp/*

FROM graviteeio/java:21
LABEL maintainer="contact@graviteesource.com"

ENV GRAVITEEIO_HOME=/opt/graviteeio-am-management-api

RUN mkdir -p ${GRAVITEEIO_HOME} \
    && addgroup -g 1000 graviteeio \
    && adduser -D -H -u 1001 graviteeio --ingroup graviteeio \
    && chmod -R g=u ${GRAVITEEIO_HOME}

COPY --from=build-env --chown=1001:1000 /gravitee-tmp/gravitee-am-management-api-standalone-* ${GRAVITEEIO_HOME}

WORKDIR ${GRAVITEEIO_HOME}
EXPOSE 8093
ENTRYPOINT ["./bin/gravitee"]

USER graviteeio

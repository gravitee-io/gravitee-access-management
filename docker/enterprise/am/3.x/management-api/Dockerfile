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


FROM graviteeio/java:21
LABEL maintainer="contact@graviteesource.com"

ARG GRAVITEEIO_AM_VERSION=0
ARG GRAVITEEIO_DOWNLOAD_URL=https://download.gravitee.io/graviteeio-ee/am/distributions
ARG GRAVITEEIO_HOME="/opt/graviteeio-am-management-api"
ENV GRAVITEEIO_HOME="/opt/graviteeio-am-management-api"

RUN addgroup -g 1000 graviteeio \
    && adduser -D -H -u 1001 graviteeio --ingroup graviteeio

RUN apk update \
    && apk add --update --no-cache wget unzip \
	&& wget ${GRAVITEEIO_DOWNLOAD_URL}/graviteeio-ee-am-full-${GRAVITEEIO_AM_VERSION}.zip --no-check-certificate -P /tmp \
	&& unzip /tmp/graviteeio-ee-am-full-${GRAVITEEIO_AM_VERSION}.zip -d /tmp/ \
    && apk del unzip \
	&& mkdir -p ${GRAVITEEIO_HOME} \
	&& cp -fR /tmp/graviteeio-ee-am-full-${GRAVITEEIO_AM_VERSION}/graviteeio-am-management-api-${GRAVITEEIO_AM_VERSION}/* ${GRAVITEEIO_HOME} \
	&& rm -rf /tmp/* \
    && rm -rf /var/lib/apt/lists/* \
	&& chgrp -R graviteeio ${GRAVITEEIO_HOME} \
    && chmod -R g=u ${GRAVITEEIO_HOME}

WORKDIR /opt/graviteeio-am-management-api

EXPOSE 8093
CMD ["./bin/gravitee"]

USER graviteeio

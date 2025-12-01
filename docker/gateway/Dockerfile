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
<<<<<<< HEAD
ARG JATTACH_VERSION=2.2
ENV GRAVITEEIO_HOME /opt/graviteeio-am-gateway
=======
>>>>>>> dede27f4e (chore(docker): refactor Dockerfile for GW and MAPI to reduce number of layer)

RUN apk add --update --no-cache zip unzip

RUN apk update \
<<<<<<< HEAD
    && apk add --no-cache ca-certificates jemalloc jemalloc-dev perl \
	&& apk add --no-cache --virtual .build-deps zip unzip netcat-openbsd wget build-base \
	&& wget -O /tmp/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip https://download.gravitee.io/graviteeio-am/components/gravitee-am-gateway/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip \
    && unzip /tmp/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip -d /tmp/ \
    && wget -O /tmp/jattach.tar.gz https://github.com/apangin/jattach/archive/refs/tags/v${JATTACH_VERSION}.tar.gz \
    && tar -xf /tmp/jattach.tar.gz -C /tmp/ \
    && make -C /tmp/jattach-${JATTACH_VERSION} \
    && JATTACH_SRC="/tmp/jattach-${JATTACH_VERSION}/jattach" \
    && if [ ! -f "${JATTACH_SRC}" ]; then \
            JATTACH_SRC="/tmp/jattach-${JATTACH_VERSION}/build/jattach"; \
       fi \
    && install -m 0755 "${JATTACH_SRC}" /usr/local/bin/jattach \
    && ln -s /usr/local/bin/jattach /usr/local/bin/jeattach \
    && apk del .build-deps \
    && mv /tmp/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION} ${GRAVITEEIO_HOME} \
    && rm -rf /tmp/* \
	&& chgrp -R graviteeio ${GRAVITEEIO_HOME} \
=======
	&& apk add --update --no-cache unzip netcat-openbsd wget \
	&& wget --no-check-certificate -O /tmp/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip https://download.gravitee.io/graviteeio-am/components/gravitee-am-gateway/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip \
    && unzip /tmp/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip -d /tmp/ \
	&& apk del unzip netcat-openbsd wget

FROM graviteeio/java:21
LABEL maintainer="contact@graviteesource.com"

ENV GRAVITEEIO_HOME /opt/graviteeio-am-gateway

COPY --from=build-env /tmp/gravitee-am-gateway-standalone-* ${GRAVITEEIO_HOME}

RUN addgroup -g 1000 graviteeio \
    && adduser -D -H -u 1001 graviteeio --ingroup graviteeio \
    && chgrp -R graviteeio ${GRAVITEEIO_HOME} \
>>>>>>> dede27f4e (chore(docker): refactor Dockerfile for GW and MAPI to reduce number of layer)
    && chmod -R g=u ${GRAVITEEIO_HOME}

RUN if [ -x "${JAVA_HOME}/bin/jcmd" ]; then \
        ln -sf "${JAVA_HOME}/bin/jcmd" /usr/local/bin/jcmd; \
    else \
        echo "jcmd not found in ${JAVA_HOME}/bin; skipping symlink"; \
    fi

WORKDIR ${GRAVITEEIO_HOME}
EXPOSE 8092
ENTRYPOINT ["./bin/gravitee"]

USER graviteeio

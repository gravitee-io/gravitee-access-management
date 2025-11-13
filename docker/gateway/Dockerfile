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

ARG GRAVITEEAM_VERSION=0
ARG JATTACH_VERSION=1.6
ENV GRAVITEEIO_HOME /opt/graviteeio-am-gateway
ENV LD_PRELOAD /usr/lib/libjemalloc.so.2

RUN addgroup -g 1000 graviteeio \
    && adduser -D -H -u 1001 graviteeio --ingroup graviteeio

RUN apk update \
    && apk add --no-cache ca-certificates jemalloc \
	&& apk add --no-cache --virtual .build-deps zip unzip netcat-openbsd wget build-base \
	&& wget -O /tmp/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip https://download.gravitee.io/graviteeio-am/components/gravitee-am-gateway/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip \
    && unzip /tmp/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip -d /tmp/ \
    && wget -O /tmp/jattach.tar.gz https://github.com/apangin/jattach/archive/refs/tags/v${JATTACH_VERSION}.tar.gz \
    && tar -xf /tmp/jattach.tar.gz -C /tmp/ \
    && make -C /tmp/jattach-${JATTACH_VERSION} \
    && install -m 0755 /tmp/jattach-${JATTACH_VERSION}/jattach /usr/local/bin/jattach \
    && ln -s /usr/local/bin/jattach /usr/local/bin/jeattach \
    && apk del .build-deps \
    && mv /tmp/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION} ${GRAVITEEIO_HOME} \
    && rm -rf /tmp/* \
	&& chgrp -R graviteeio ${GRAVITEEIO_HOME} \
    && chmod -R g=u ${GRAVITEEIO_HOME}

RUN if [ -x "${JAVA_HOME}/bin/jcmd" ]; then \
        ln -sf "${JAVA_HOME}/bin/jcmd" /usr/local/bin/jcmd; \
    else \
        echo "jcmd not found in ${JAVA_HOME}/bin" >&2; \
        exit 1; \
    fi

WORKDIR ${GRAVITEEIO_HOME}
EXPOSE 8092
ENTRYPOINT ["./bin/gravitee"]

USER graviteeio

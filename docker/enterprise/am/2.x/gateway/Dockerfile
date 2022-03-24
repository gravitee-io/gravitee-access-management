#-------------------------------------------------------------------------------
# Copyright (C) 2015 The Gravitee team (http://gravitee.io)
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#            http://www.apache.org/licenses/LICENSE-2.0
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#-------------------------------------------------------------------------------
FROM alpine as builder
LABEL maintainer="contact@graviteesource.com"

ARG GRAVITEEIO_AM_VERSION=0
ARG GRAVITEEIO_EE_VERSION=1.1.0
ENV GRAVITEEIO_HOME /opt/graviteeio-am-gateway

RUN apk --no-cache add zip unzip wget

RUN wget --no-check-certificate -O /tmp/gravitee-am-gateway-standalone-${GRAVITEEIO_AM_VERSION}.zip https://download.gravitee.io/graviteeio-am/components/gravitee-am-gateway/gravitee-am-gateway-standalone-${GRAVITEEIO_AM_VERSION}.zip \
    && unzip /tmp/gravitee-am-gateway-standalone-${GRAVITEEIO_AM_VERSION}.zip -d /tmp/ \
    && mv /tmp/gravitee-am-gateway-standalone-${GRAVITEEIO_AM_VERSION} /opt/graviteeio-am-gateway \
    && rm -rf /tmp/*

# Prepare license directory
RUN mkdir ${GRAVITEEIO_HOME}/license

# Add specific enterprise libraries and plugins
RUN wget https://download.gravitee.io/graviteeio-ee/license/gravitee-license-node-enterprise-${GRAVITEEIO_EE_VERSION}.jar --no-check-certificate -P ${GRAVITEEIO_HOME}/lib

FROM graviteeio/java:8
MAINTAINER GraviteeSource Team <https://gravitee.io>

ENV GRAVITEEIO_HOME /opt/graviteeio-am-gateway

COPY --from=builder ${GRAVITEEIO_HOME} ${GRAVITEEIO_HOME}

RUN chgrp -R 0 ${GRAVITEEIO_HOME} && \
    chmod -R g=u ${GRAVITEEIO_HOME}

WORKDIR ${GRAVITEEIO_HOME}

EXPOSE 8092
CMD ["./bin/gravitee"]
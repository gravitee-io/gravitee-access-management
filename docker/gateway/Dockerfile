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

FROM graviteeio/java:8
MAINTAINER Gravitee Team <http://gravitee.io>

ARG GRAVITEEAM_VERSION=0

# Update to get support for Zip/Unzip, nc and wget
RUN apk add --update zip unzip netcat-openbsd wget

RUN wget https://download.gravitee.io/graviteeio-am/components/gravitee-am-gateway/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip --no-check-certificate -P /tmp/ \
    && unzip /tmp/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip -d /tmp/ \
    && mv /tmp/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION} /opt/gravitee-am-gateway \
    && rm -rf /tmp/*

ENV GRAVITEEAM_HOME /opt/gravitee-am-gateway
WORKDIR ${GRAVITEEAM_HOME}

ENV GRAVITEEIO_MONGODB_HOST localhost
ENV GRAVITEEIO_MONGODB_PORT 27017

EXPOSE 8092

VOLUME ["/opt/gravitee-am-gateway/logs"]

CMD ["./bin/gravitee"]
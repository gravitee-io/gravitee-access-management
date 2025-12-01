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

FROM graviteeio/java:17-alpine-3.20
LABEL maintainer="contact@graviteesource.com"

ARG GRAVITEEAM_VERSION=0

RUN apk add --update --no-cache zip unzip

RUN apk update \
	&& apk add --update --no-cache unzip netcat-openbsd wget \
	&& wget --no-check-certificate -O /tmp/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip https://download.gravitee.io/graviteeio-am/components/gravitee-am-gateway/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip \
    && unzip /tmp/gravitee-am-gateway-standalone-${GRAVITEEAM_VERSION}.zip -d /tmp/ \
	&& apk del unzip netcat-openbsd wget

FROM graviteeio/java:17-alpine-3.20
LABEL maintainer="contact@graviteesource.com"

ENV GRAVITEEIO_HOME /opt/graviteeio-am-gateway

COPY --from=build-env /tmp/gravitee-am-gateway-standalone-* ${GRAVITEEIO_HOME}

RUN addgroup -g 1000 graviteeio \
    && adduser -D -H -u 1001 graviteeio --ingroup graviteeio \
    && chgrp -R graviteeio ${GRAVITEEIO_HOME} \
    && chmod -R g=u ${GRAVITEEIO_HOME}

WORKDIR ${GRAVITEEIO_HOME}
EXPOSE 8092
ENTRYPOINT ["./bin/gravitee"]

USER graviteeio

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

FROM graviteeio/nginx:1.27.5
LABEL maintainer="contact@graviteesource.com"

ARG GRAVITEEAM_VERSION=0

# Update to get support Bash
RUN apk --update add bash

ENV WWW_TARGET /usr/share/nginx/html
ENV MGMT_BASE_HREF "/"
ENV MGMT_API_URL "http://localhost:8093"

RUN apk update \
  && apk add --update --no-cache zip unzip netcat-openbsd wget \
  && apk add --upgrade --no-cache libgcrypt \
  && wget https://download.gravitee.io/graviteeio-am/components/gravitee-am-webui/gravitee-am-webui-${GRAVITEEAM_VERSION}.zip -P /tmp \
  && unzip /tmp/gravitee-am-webui-${GRAVITEEAM_VERSION}.zip -d /tmp/ \
  && apk del zip unzip netcat-openbsd wget \
  && cp -fr /tmp/gravitee-am-webui-${GRAVITEEAM_VERSION}/* ${WWW_TARGET} \
  && rm -rf /tmp/*

ADD config/constants.json /usr/share/nginx/html/constants.json
ADD config/default.conf /etc/nginx/conf.d/default.conf

RUN chown -R 101:0 /usr/share/nginx/ /etc/nginx/

CMD ["sh", "/run.sh"]

USER nginx


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
FROM nginx:1.12.1-alpine
MAINTAINER Gravitee Team <http://gravitee.io>

ARG GRAVITEEAM_VERSION=0

# Update to get support for Zip/Unzip, Bash
RUN apk --update add zip unzip bash wget

RUN rm /etc/nginx/nginx.conf

COPY nginx.conf /etc/nginx/nginx.conf

ENV WWW_TARGET /var/www/html/

RUN wget https://download.gravitee.io/graviteeio-am/components/gravitee-am-webui/gravitee-am-webui-${GRAVITEEAM_VERSION}.zip --no-check-certificate -P /tmp/ \
    && unzip /tmp/gravitee-am-webui-${GRAVITEEAM_VERSION}.zip -d /tmp/ \
    && mkdir -p ${WWW_TARGET} \
    && mv /tmp/gravitee-am-webui-${GRAVITEEAM_VERSION}/* ${WWW_TARGET} \
    && rm -rf /tmp/*

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

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

FROM openjdk:21-slim

ENV GATLING_VERSION 3.9.5

WORKDIR /opt

RUN mkdir -p gatling

WORKDIR  /opt/gatling

# install gatling
RUN apt-get update && apt-get install -y wget unzip
RUN wget -q -O gatling-$GATLING_VERSION.zip \
  https://repo1.maven.org/maven2/io/gatling/highcharts/gatling-charts-highcharts-bundle/$GATLING_VERSION/gatling-charts-highcharts-bundle-$GATLING_VERSION-bundle.zip
RUN unzip gatling-$GATLING_VERSION.zip
RUN cp -R gatling-charts-highcharts-bundle-$GATLING_VERSION/* .
RUN rm -rf gatling-$GATLING_VERSION.zip gatling-charts-highcharts-bundle-$GATLING_VERSION

# set directories below to be mountable from host
VOLUME ["/opt/gatling/conf", "/opt/gatling/results", "/opt/gatling/user-files"]

# set environment variables
ENV PATH /opt/gatling/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ENV GATLING_HOME /opt/gatling

COPY scripts/entrypoint.sh /opt/gatling/bin
RUN chmod +x /opt/gatling/bin/entrypoint.sh

COPY  src/test/scala/ user-files/simulations

ENTRYPOINT ["entrypoint.sh"]

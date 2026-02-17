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

FROM eclipse-temurin:21-jdk-jammy

ENV GATLING_VERSION=3.14.3

WORKDIR /opt

# install gatling
RUN apt-get update && apt-get install --no-install-recommends -y wget unzip lsof vim sysstat && \
    wget -q -O gatling-$GATLING_VERSION.zip https://repo1.maven.org/maven2/io/gatling/highcharts/gatling-charts-highcharts-bundle/$GATLING_VERSION/gatling-charts-highcharts-bundle-$GATLING_VERSION-bundle.zip && \
    unzip gatling-$GATLING_VERSION.zip && \
    mv gatling-charts-highcharts-bundle-$GATLING_VERSION/ gatling && \
    rm -rf gatling-$GATLING_VERSION.zip

WORKDIR  /opt/gatling

# set directories below to be mountable from host
VOLUME ["/opt/gatling/src/test/resources/", "/opt/gatling/target", "/opt/gatling/src/test/scala"]

# set environment variables
ENV PATH=/opt/gatling:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ENV GATLING_HOME=/opt/gatling

COPY scripts/entrypoint.sh /opt/gatling/
RUN chmod +x /opt/gatling/entrypoint.sh

COPY  src/test/scala/ src/test/scala

COPY pom.xml pom.xml

RUN mvnw test-compile

ENTRYPOINT ["entrypoint.sh"]

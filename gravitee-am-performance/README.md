# Overview

This module contains gatling scripts to run performance tests on AccessManagement.
In this README, you will find documentation to execute the tests locally using maven or using Docker.  

# Prerequisites

The AM installation targeted by those simulation needs to have a valid EE license and the following plugins installed:
* [SMS factor](https://download.gravitee.io/#graviteeio-ee/am/plugins/factors/gravitee-am-factor-sms/) (present in the default bundle)
* [Mock MFA Resource](https://download.gravitee.io/graviteeio-am/plugins/resources/gravitee-am-resource-mfa-mock/gravitee-am-resource-mfa-mock-1.0.0.zip)

For authorization simulations, an instance of an **OpenFGA** server is required. See [Authorization Simulations](./src/test/scala/io/gravitee/am/performance/authorization/README.md).

# Maven execution

## Create Dataset

See [Provisioning](./src/test/scala/io/gravitee/am/performance/management/provisioning/README.md).

## Management API Workload Simulations

See [Search](./src/test/scala/io/gravitee/am/performance/management/search/README.md).

## Gateway Workload Simulations

See [Gateway](./src/test/scala/io/gravitee/am/performance/gateway/README.md).

## Authorization

See [Authorization](./src/test/scala/io/gravitee/am/performance/authorization/README.md).

# Docker

## Build Docker Image

A docker image that embeds all the simulations can be created.
```bash
$ docker build --platform linux/amd64,linux/arm64 -t am-gatling-runner .
```
or build with auto push (ACR Docker authentication required)
```bash
$ ./build-publish.sh [version name, default its datestamp]
```

Once the image is available, you can run a simulation by providing the simulation class as parameter and extra parameter to adapt the simulation behaviour 
```bash
docker run --rm am-gatling-runner "io.gravitee.am.performance.gateway.MultiDomainBasicLoginFlow" "-Dnumber_of_domains=10 -Dagents=600 -Dgw_url=https://am-perf.gateway.4-2-x.team-am.gravitee.dev"
```

You can also run it in interaction mode to execute gatling manually.
```bash
docker run -it --rm am-gatling-runner 
$> root@77f37ff72533:/opt/gatling# mvnw gatling:test 
GATLING_HOME is set to /opt/gatling
Do you want to run the simulation locally, on Gatling Enterprise, or just package it?
Type the number corresponding to your choice and press enter
[0] <Quit>
[1] Run the Simulation locally
[2] Package and upload the Simulation to Gatling Enterprise Cloud, and run it there
[3] Package the Simulation for Gatling Enterprise
[4] Show help and exit
```

```bash
docker run -it --rm am-gatling-runner 
$> root@77f37ff72533:/opt/gatling# mvnw gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.gateway.MultiDomainBasicLoginFlow -Dnumber_of_domains=10 -Dagents=600 -Dgw_url=https://am-perf.gateway.4-2-x.team-am.gravitee.dev
```

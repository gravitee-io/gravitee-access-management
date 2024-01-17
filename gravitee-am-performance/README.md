# Overview

This module contains gatling scripts to run performance tests on AccessManagement.
In this README, you will find documentation to execute the tests locally using maven or using Docker.  

# Maven execution

## Create Dataset

Here are simulations to create a dataset.

### Create Single Domain

The CreateDomain simulation will create a domain with two applications and a single default IDP.

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.CreateDomain
```

#### Parameters

The CreateDomain simulation accept some JavaOpts as parameters:

* `mng_url`: base URL of the Management REST API (default: http://localhost:8093)
* `mng_user`: username to request an access token to the Management REST API (default: admin)
* `mng_password`: password to request an access token to the Management REST API (default: adminadmin)
* `domain`: the domain name targeted by the simulation (default: gatling-domain)


### Load users linked to a domain

The CreateUsers simulation will populate the domain IDP with the given number of users

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.CreateUsers -Dnumber_of_users=100000
```

#### Parameters

The CreateUsers simulation accept some JavaOpts as parameters:

* `mng_url`: base URL of the Management REST API (default: http://localhost:8093)
* `mng_user`: username to request an access token to the Management REST API (default: admin)
* `mng_password`: password to request an access token to the Management REST API (default: adminadmin)
* `domain`: the domain name targeted by the simulation (default: gatling-domain)
* `idp`: the IDP name targeted by the simulation (default: "Default Identity Provider")
* `min_user_index`: first value of the index used to create users (default: 1)
* `number_of_users`: how many users the simulation will create (default: 2000)
* `agents`: number of agents used to create users (default: 10)


### Create Multiple Domain

The CreateMultipleDomains simulation will create multiple domains using the same prefix with :
* 3 applications (Web, SPA, Service) and a single default IDP
* users attached to the default IDP

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.CreateMultipleDomains
```

#### Parameters

The CreateMultipleDomains simulation accept some JavaOpts as parameters:

* mng_url: base URL of the Management REST API (default: http://localhost:8093)
* mng_user: username to request an access token to the Management REST API (default: admin)
* mng_password: password to request an access token to the Management REST API (default: adminadmin)
* domain: the prefix for domain name targeted by the simulation on which a index will be added (default: gatling-domain)
* min_domain_index: first value of the index used to create domains (default: 1)
* number_of_domains: how many domains the simulation will create (default: 10)
* number_of_users: how many users the simulation will create (default: 2000)
* agents: number of agents for the simulation (default: 10)


## Runtime simulation based on Dataset

### Search Audit Logs

This simulation searches audit logs under domain context.

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.search.SearchAuditLog -Devent=USER_UPDATED,USER_DELETED -Dstart="22/10/1998 13:12:11" -Dend="30/09/2022 0:0:0"
```

or with default parameter the following command searches USER_LOGIN and USER_LOGOUT in 24 hours range:

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.search.SearchAuditLog
```

#### Parameters

* `mng_url`: base URL of the Management REST API (default: http://localhost:8093)
* `mng_user`: username to request an access token to the Management REST API (default: admin)
* `mng_password`: password to request an access token to the Management REST API (default: adminadmin)
* `domain`: the domain name targeted by the simulation (default: gatling-domain)
* `agents`: number of agents used to create users (default: 10)
* `repeat`: number of searches each agent perform (default: 10)
* `start`: beginning of the search range in "dd/MM/yyyy HH:mm:ss" format such as "22/10/1998 13:12:11" (default: last 24 hours)
* `end`: ending of the search range in dd/MM/yyyy HH:mm:ss format such as "22/10/2022 09:12:13" (default: current date time)
* `event`: comma seperated list of supported events such as (USER_UPDATED,USER_DELETED)


### Search Users

This simulation perform search in domain user context.

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.search.SearchUser -Dfield=email,username,firstName  -Doperator=co -Dcondition=or
```

#### Parameters

* `mng_url`: base URL of the Management REST API (default: http://localhost:8093)
* `mng_user`: username to request an access token to the Management REST API (default: admin)
* `mng_password`: password to request an access token to the Management REST API (default: adminadmin)
* `domain`: the domain name targeted by the simulation (default: gatling-domain)
* `agents`: number of agents used to create users (default: 10)
* `field`: comma seperated fields such as (email,username,firstName)
* `operator`: SCIM 2.0 supported operator such as "eq", "ne", "co" etc.
* `condition`: logical condition such as "and" and "or"


## Workload Simulations

### BasicLoginFlow

Basic simulation to authenticate users using code flow and request token

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.BasicLoginFlow -Dagents=100
```

#### Parameters

* `gw_url`: base URL of the Management REST API (default: http://localhost:8093)
* `domain`: the domain name targeted by the simulation (default: gatling-domain)
* `min_user_index`: minimal value of the user index
* `number_of_users`: size of the users range used to randomly select a user between min_user_index and (min_user_index + number_of_users) (default: 2000)
* `agents`: number of agent loaded per seconds (default: 10)
* `inject-during`: duration (in sec) of the agents load (default: 300 => 5 minutes)
* `requests`: number of requests per seconds to reach (default: 100)
* `req-ramp-during`: ramp duration (in sec)  (default: 10)
* `req-hold-during`: duration (in sec) of the simulation at the given rate of requests (default: 1800 => 30 minutes)


### MultiDomainServiceIntrospect

Basic simulation to generate token using client_credentials flow and call 10 introspects on domain which is randomly selected

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.MultiDomainServiceIntrospect  -Ddomain=perf -Dnumber_of_domains=3 -Dagents=5 -Dapp=appservice
```

#### Parameters
* `gw_url`: base URL of the Management REST API (default: http://localhost:8093)
* `domain`: the domain name prefix targeted by the simulation (default: gatling-domain)
* `min_domain_index`: minimal value of the domain index
* `number_of_domains`: size of the users range used to randomly select a domain between min_domain_index and (min_domain_index + number_of_domains) (default: 10)
* `app`: the application/client_id to use (clientSecret should be equals to clientId)
* `inject-during`: duration (in sec) of the agents load (default: 300 => 5 minutes)
* `introspect`: do we have to request token introspection (default: false)
* `number_of_introspections`: number of token introspection (default: 10)

### MultiDomainBasicLoginFlow

Basic simulation to authenticate users using code flow and generate access_token on domain which is randomly selected

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.MultiDomainBasicLoginFlow  -Ddomain=perf -Dnumber_of_domains=3 -Dagents=5 
```

#### Parameters
* `gw_url`: base URL of the Management REST API (default: http://localhost:8093)
* `domain`: the domain name prefix targeted by the simulation (default: gatling-domain)
* `min_domain_index`: minimal value of the domain index
* `number_of_domains`: size of the users range used to randomly select a domain between min_domain_index and (min_domain_index + number_of_domains) (default: 10)
* `min_user_index`: minimal value of the user index
* `number_of_users`: size of the users range used to randomly select a user between min_user_index and (min_user_index + number_of_users) (default: 2000)
* `agents`: number of agent loaded per seconds (default: 10)
* `inject-during`: duration (in sec) of the agents load (default: 300 => 5 minutes)

### MultiDomainLoginPasswordFlow

Basic simulation to authenticate users using password flow and generate access_token on domain which is randomly selected. Introspection on token is optional

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.MultiDomainLoginPasswordFlow  -Ddomain=perf -Dnumber_of_domains=3 -Dagents=5 
```

#### Parameters
* `gw_url`: base URL of the Management REST API (default: http://localhost:8093)
* `domain`: the domain name prefix targeted by the simulation (default: gatling-domain)
* `min_domain_index`: minimal value of the domain index
* `number_of_domains`: size of the users range used to randomly select a domain between min_domain_index and (min_domain_index + number_of_domains) (default: 10)
* `min_user_index`: minimal value of the user index
* `number_of_users`: size of the users range used to randomly select a user between min_user_index and (min_user_index + number_of_users) (default: 2000)
* `agents`: number of agent loaded per seconds (default: 10)
* `inject-during`: duration (in sec) of the agents load (default: 300 => 5 minutes)
* `introspect`: do we have to request token introspection (default: false)
* `number_of_introspections`: number of token introspection (default: 10)

# Docker

## Build Docker Image

A docker image that embeds all the simulations can be created.
```bash
$ docker build -t am-gatling-runner .
```

Once the image is available, you can run a simulation by providing the simulation class as parameter and extra parameter to adapt the simulation behaviour 
```bash
docker run --rm am-gatling-runner "io.gravitee.am.performance.MultiDomainBasicLoginFlow" "-Dnumber_of_domains=10 -Dagents=600 -Dgw_url=https://am-perf.gateway.4-2-x.team-am.gravitee.dev"
```

You can also run it in interaction mode to execute gatling manually.
```bash
docker run -it --rm am-gatling-runner 
$> root@77f37ff72533:/opt/gatling# gatling.sh 
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
$> root@77f37ff72533:/opt/gatling# gatling.sh --run-mode local -s io.gravitee.am.performance.MultiDomainBasicLoginFlow -erjo "-Dnumber_of_domains=10 -Dagents=600 -Dgw_url=https://am-perf.gateway.4-2-x.team-am.gravitee.dev" 
```

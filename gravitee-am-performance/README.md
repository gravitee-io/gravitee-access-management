# Create Dataset

There are two simulations to create a dataset.

## Create Domain

The CreateDomain simulation will create a domain with two applications and a single default IDP.

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.CreateDomain
```

### Parameters

The CreateDomain simulation accept some JavaOpts as parameters:

* `mng_url`: base URL of the Management REST API (default: http://localhost:8093)
* `mng_user`: username to request an access token to the Management REST API (default: admin)
* `mng_password`: password to request an access token to the Management REST API (default: adminadmin)
* `domain`: the domain name targeted by the simulation (default: gatling-domain)

## Load users

The CreateUsers simulation will populate the domain IDP with the given number of users

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.CreateUsers -Dnumber_of_users=100000
```

### Parameters

The CreateUsers simulation accept some JavaOpts as parameters:

* `mng_url`: base URL of the Management REST API (default: http://localhost:8093)
* `mng_user`: username to request an access token to the Management REST API (default: admin)
* `mng_password`: password to request an access token to the Management REST API (default: adminadmin)
* `domain`: the domain name targeted by the simulation (default: gatling-domain)
* `idp`: the IDP name targeted by the simulation (default: "Default Identity Provider")
* `min_user_index`: first value of the index used to create users (default: 1)
* `number_of_users`: how many users the simulation will create (default: 2000)
* `agents`: number of agents used to create users (default: 10)

# Workload Simulations

We currently only have a single simulation to test a simple login flow using the authorization code flow.

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.BasicLoginFlow -Dagents=100
```

### Parameters

* `gw_url`: base URL of the Management REST API (default: http://localhost:8093)
* `domain`: the domain name targeted by the simulation (default: gatling-domain)
* `min_user_index`: minimal value of the user index
* `number_of_users`: size of the users range used to randomly select a user between min_user_index and (min_user_index + number_of_users) (default: 2000)
* `agents`: number of agent loaded per seconds (default: 10)
* `inject-during`: duration (in sec) of the agents load (default: 300 => 5 minutes)
* `requests`: number of requests per seconds to reach (default: 100)
* `req-ramp-during`: ramp duration (in sec)  (default: 10)
* `req-hold-during`: duration (in sec) of the simulation at the given rate of requests (default: 1800 => 30 minutes)
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


## Search Audit Logs

This simulation searches audit logs under domain context.

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.search.SearchAuditLog -Devent=USER_UPDATED,USER_DELETED -Dstart="22/10/1998 13:12:11" -Dend="30/09/2022 0:0:0"
```

or with default parameter the following command searches USER_LOGIN and USER_LOGOUT in 24 hours range:

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.search.SearchAuditLog
```

### Parameters

* `mng_url`: base URL of the Management REST API (default: http://localhost:8093)
* `mng_user`: username to request an access token to the Management REST API (default: admin)
* `mng_password`: password to request an access token to the Management REST API (default: adminadmin)
* `domain`: the domain name targeted by the simulation (default: gatling-domain)
* `agents`: number of agents used to create users (default: 10)
* `repeat`: number of searches each agent perform (default: 10)
* `start`: beginning of the search range in "dd/MM/yyyy HH:mm:ss" format such as "22/10/1998 13:12:11" (default: last 24 hours)
* `end`: ending of the search range in dd/MM/yyyy HH:mm:ss format such as "22/10/2022 09:12:13" (default: current date time)
* `event`: comma seperated list of supported events such as (USER_UPDATED,USER_DELETED)


## Search Users

This simulation perform search in domain user context.

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.search.SearchUser -Dfield=email,username,firstName  -Doperator=co -Dcondition=or
```

### Parameters

* `mng_url`: base URL of the Management REST API (default: http://localhost:8093)
* `mng_user`: username to request an access token to the Management REST API (default: admin)
* `mng_password`: password to request an access token to the Management REST API (default: adminadmin)
* `domain`: the domain name targeted by the simulation (default: gatling-domain)
* `agents`: number of agents used to create users (default: 10)
* `field`: comma seperated fields such as (email,username,firstName)
* `operator`: SCIM 2.0 supported operator such as "eq", "ne", "co" etc.
* `condition`: logical condition such as "and" and "or"


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
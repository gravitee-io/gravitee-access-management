## Management API Workload Simulations

### Search Audit Logs

This simulation searches audit logs under domain context.

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.management.search.SearchAuditLog -Devent=USER_UPDATED,USER_DELETED -Dstart="22/10/1998 13:12:11" -Dend="30/09/2022 0:0:0"
```

or with default parameter the following command searches USER_LOGIN and USER_LOGOUT in 24 hours range:

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.management.search.SearchAuditLog
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
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.management.search.SearchUser -Dfield=email,username,firstName  -Doperator=co -Dcondition=or
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
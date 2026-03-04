## Gateway Workload Simulations

### MultiDomainServiceIntrospect

Basic simulation to generate tokens using client_credentials flow and call 10 introspects on domain which is randomly selected

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.gateway.MultiDomainServiceIntrospect  -Ddomain=perf -Dnumber_of_domains=3 -Dagents=5 -Dapp=appservice
```

#### Parameters
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
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.gateway.MultiDomainBasicLoginFlow  -Ddomain=perf -Dnumber_of_domains=3 -Dagents=5 
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
* `app`: the app name targeted by the simulation (default: appweb)

### MultiDomainLoginPasswordFlow

Basic simulation to authenticate users using password flow and generate access_token on domain which is randomly selected. Introspection on token is optional

```
    mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.gateway.MultiDomainLoginPasswordFlow  -Ddomain=perf -Dnumber_of_domains=3 -Dagents=5 
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
* `app`: the app name targeted by the simulation (default: appweb)

### MultiDomainMFALoginFlow

Simulation to authenticate users using code flow and multifactor authentication.
Once authenticated, a token is generated, it is checked using Introspection endpoint then the user is logout.

**NOTE**: Remember that application on which MFA is enabled are suffixed by `mfa` (ex: `appwebmfa`)

```
    mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.gateway.MultiDomainMFALoginFlow  -Ddomain=perf -Dnumber_of_domains=3 -Dagents=5 
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
* `app`: the app name targeted by the simulation (default: appweb)

### MultiDomainConsentLoginFlow

Simulation to authenticate users using code flow and consent.
Once authenticated, a token is generated, and UserInfo endpoint is requested before signing out the user.

**NOTE**: Remember that application on which MFA is enabled are suffixed by `mfa` (ex: `appwebmfa`)

```
    mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.gateway.MultiDomainConsentLoginFlow  -Ddomain=perf -Dnumber_of_domains=3 -Dagents=5 
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
* `app`: the app name targeted by the simulation (default: appweb)
* `scopes`: comma separated list of scopes (openid is set by default)

### TokenExchange

Simulation that creates token trees using token exchange.

Each tree is built as follows:
* Get one shared parent token using `client_credentials` (once per agent)
* Create two root tokens according to `use_delegation_mode`:
    * `false` (default): impersonation
    * `true`: delegation (`subject_token=parentToken`, `actor_token=parentToken`)
* Expand descendants according to `use_delegation_mode`:
    * `false` (default): impersonation (`subject_token` only)
    * `true`: delegation (`subject_token` + `actor_token`)
* For delegation mode, `actor_token` is selected from the same level using round-robin and differs from `subject_token` when possible

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.gateway.TokenExchange -Dgw_url=http://localhost:8092 -Ddomain=gatling-domain -Dclient_id=my-client -Dclient_secret=my-secret -Ddepth=25 -Dnumber_of_trees=100 -Dbranch_factor=1 -Dlevel_width=2 -Duse_delegation_mode=false -Dagents=10
```

#### Parameters
* `gw_url`: base URL of the Gateway REST API (default: http://localhost:8092)
* `domain`: target domain name (default: gatling-domain)
* `client_id`: client id configured for client_credentials (required)
* `client_secret`: client secret configured for client_credentials (required)
* `depth`: tree depth (default: 5). `depth=1` means only the two root tokens
* `number_of_trees`: number of trees created by each agent (default: 100)
* `branch_factor`: number of children per token when `level_width=0` (default: 2, minimum: 1)
* `level_width`: exact number of tokens generated on each non-root level when greater than 0 (default: 0)
* `use_delegation_mode`: `true` for delegation roots/descendants, `false` for impersonation roots/descendants (default: false)
* `agents`: number of concurrent virtual users executing the simulation (default: 10)

### TokenExchangeRevoke

Self-contained simulation that prepares token trees using token exchange and then revokes both root tokens of each tree.
This simulation is dedicated to measure revoke endpoint performance separately from token exchange simulations.
Token generation runs during `before` and only revoke requests are executed during the Gatling scenario.
The simulation prints expected/generated/revoked progress in the console.

#### Branching mode (explosive)
```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.gateway.TokenExchangeRevoke -Dgw_url=http://localhost:8092 -Ddomain=gatling-domain -Dclient_id=my-client -Dclient_secret=my-secret -Ddepth=5 -Dnumber_of_trees=100 -Dbranch_factor=2 -Dlevel_width=0 -Duse_delegation_mode=true -Dagents=10
```
#### Fixed-width mode
```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.gateway.TokenExchangeRevoke -Dgw_url=http://localhost:8092 -Ddomain=gatling-domain -Dclient_id=my-client -Dclient_secret=my-secret -Ddepth=5 -Dnumber_of_trees=100 -Dlevel_width=100 -Duse_delegation_mode=true -Dagents=10
```

#### Parameters
* `gw_url`: base URL of the Gateway REST API (default: http://localhost:8092)
* `domain`: target domain name (default: gatling-domain)
* `client_id`: client id configured for client_credentials (required)
* `client_secret`: client secret configured for client_credentials (required)
* `depth`: tree depth (default: 5). `depth=1` means only the two root tokens
* `number_of_trees`: number of trees created by each agent (default: 100)
* `branch_factor`: number of children per token when `level_width=0` (default: 2, minimum: 1)
* `level_width`: exact number of tokens generated on each non-root level when greater than 0 (default: 0)
* `use_delegation_mode`: `true` for delegation roots/descendants, `false` for impersonation roots/descendants (default: false)
* `agents`: number of concurrent virtual users executing the simulation (default: 10)

### TokenRevoke

Self-contained simulation that creates `client_credentials` tokens (no token exchange) and revokes every generated token.
Token generation runs during `before` and only revoke requests are executed during the Gatling scenario.
The simulation prints expected/generated/revoked progress in the console.

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.gateway.TokenRevoke -Dgw_url=http://localhost:8092 -Ddomain=gatling-domain -Dclient_id=my-client -Dclient_secret=my-secret -Dnumber_of_tokens=100000 -Dagents=10
```
#### Parameters
* `gw_url`: base URL of the Gateway REST API (default: http://localhost:8092)
* `domain`: target domain name (default: gatling-domain)
* `client_id`: client id configured with token exchange (required)
* `client_secret`: client secret configured with token exchange (required)
* `number_of_tokens`: total number of access tokens generated and revoked across all agents (default: 10000)
* `agents`: number of concurrent virtual users executing the simulation (default: 10)

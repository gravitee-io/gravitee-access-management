## Create Dataset

Here are simulations to create a dataset.

### Create Domains

The CreateMultipleDomains simulation will create multiple domains using the same prefix.
SCIM is enabled on each domain and 5 applications (2 Web, 2 SPA, Service) are created:
* For type WEB, application names are
    * appweb
    * appwebmfa : this app has MFA enabled
* For type SPA, application names are
    * appspa
    * appspamfa : this app has MFA enabled
* For type Service, application name is
    * appservice

For WEB & SPA applications:
* a single default IDP is enabled
* following scopes are defined `openid`, `email`, `profile`, `roles`, `groups`, `full_profile`
* for the application `appwebmfa` & `appspamfa`, SMS factor using a Mock resource is enabled and MFA requires Enrollment & Challenge

For Service application, the `scim`scope is defined

The simulation creates users for each domain and they are attached to the default IDP

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.management.provisioning.CreateMultipleDomains
```

#### Parameters

The CreateMultipleDomains simulation accepts some JavaOpts as parameters:

* mng_url: base URL of the Management REST API (default: http://localhost:8093)
* mng_user: username to request an access token to the Management REST API (default: admin)
* mng_password: password to request an access token to the Management REST API (default: adminadmin)
* domain: the prefix for domain name targeted by the simulation on which a index will be added (default: gatling-domain)
* min_domain_index: first value of the index used to create domains (default: 1)
* number_of_domains: how many domains the simulation will create (default: 1)
* number_of_users: how many users the simulation will create (default: 2000)
* agents: number of agents for the simulation (default: 10)

### Create Users

Once domains are created it can be useful to add new users. For doing this the CreateUsers simulation has been created.
This simulation needs to be executed one domain at a time and takes the number of users to add and the initial index.

```
mvn gatling:test -Dgatling.simulationClass=io.gravitee.am.performance.management.provisioning.CreateUsers
```

#### Parameters

The CreateUsers simulation accepts some JavaOpts as parameters:

* mng_url: base URL of the Management REST API (default: http://localhost:8093)
* mng_user: username to request an access token to the Management REST API (default: admin)
* mng_password: password to request an access token to the Management REST API (default: adminadmin)
* domain: the domain name targeted by the simulation (default: gatling-domain)
* idp: the IDP name targeted by the simulation (default: "Default Identity Provider")
* min_user_index: first value of the index used to create users (default: 1)
* number_of_users: how many users the simulation will create (default: 2000)
* agents: number of agents for the simulation (default: 10)
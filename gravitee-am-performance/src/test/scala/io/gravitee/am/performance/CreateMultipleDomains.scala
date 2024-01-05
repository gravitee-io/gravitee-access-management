/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.performance

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gravitee.am.performance.utils.ManagementAPICalls._
import io.gravitee.am.performance.utils.ScopeSettings
import io.gravitee.am.performance.utils.SimulationSettings._

/**
 * Purpose of this simulation is to create a bunch of domains with only the DefaultIdp and 3 applications (WEB, BROWSER/SPA, SERVICE)
 * For each domains, the simulation will create the same number of users.
 *
 * Possible arguments:
 * - mng_url: base URL of the Management REST API (default: http://localhost:8093)
 * - mng_user: username to request an access token to the Management REST API (default: admin)
 * - mng_password: password to request an access token to the Management REST API (default: adminadmin)
 * - domain: the prefix for domain name targeted by the simulation on which a index will be added (default: gatling-domain)
 * - min_domain_index: first value of the index used to create domains (default: 1)
 * - number_of_domains: how many domains the simulation will create (default: 10)
 * - number_of_users: how many users the simulation will create (default: 2000)
 * - agents: number of agents for the simulation (default: 10)
 */
class CreateMultipleDomains extends Simulation {

  val httpProtocol = http
    .userAgentHeader("Gatling - Create Multiple Domains")
    .disableFollowRedirect

  val domainGenerator = multiDomainsFeeder(DATALOAD)
  val userGenerator = userFeeder(DATALOAD)

  val scn = scenario("Create Multiple Domains")
    .exec(login)
    .feed(domainGenerator)
    .doWhile("#{continueDomainCreation}") (
        exec(createDomain("#{domainName}"))
        .exec(enableCurrentDomain)
        .exec(activateScimOnDomain)
        .exec(retrieveIdentityProviderId("Default Identity Provider"))
        .exec(createApplication("appweb", "WEB"))
        .exec(enableCurrentIDPToApp("appweb"))
        .exec(addScopesToApp("appweb", Array(ScopeSettings("openid"))))
        .exec(createApplication("appspa", "BROWSER"))
        .exec(enableCurrentIDPToApp("appspa"))
        .exec(addScopesToApp("appspa", Array(ScopeSettings("openid"))))
        .exec(createApplication("appservice", "SERVICE"))
        .exec(addScopesToApp("appservice", Array(ScopeSettings("scim"))))
        .feed(domainGenerator)
        .doWhile("#{continueUserCreation}", "userIndex") (
          exec { session =>
            val newSession = session.setAll(singleUser(session("userIndex").as[Int]))
            newSession
          }
          .exec(createUser)
        )
    )
  setUp(scn.inject(atOnceUsers(Math.min(AGENTS, NUMBER_OF_DOMAINS))).protocols(httpProtocol))
}

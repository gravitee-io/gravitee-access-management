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
package io.gravitee.am.performance.gateway

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gravitee.am.performance.commands.GatewayCalls._
import io.gravitee.am.performance.utils.SimulationSettings._

/**
 * Purpose of this simulation is to sign in users only with login/password
 *
 * Possible arguments:
 * - gw_url: base URL of the Management REST API (default: http://localhost:8093)
 * - domain: the domain name prefix targeted by the simulation (default: gatling-domain)
 * - min_domain_index: minimal value of the domain index
 * - number_of_domains: size of the users range used to randomly select a domain between min_domain_index and (min_domain_index + number_of_domains) (default: 10)
 * - min_user_index: minimal value of the user index
 * - number_of_users: size of the users range used to randomly select a user between min_user_index and (min_user_index + number_of_users) (default: 2000)
 * - agents: number of agent loaded per seconds (default: 10)
 * - inject-during: duration (in sec) of the agents load (default: 300 => 5 minutes)
 * - introspect: do we have to request token introspection (default: false)
 * - number_of_introspections: number of token introspection (default: 10)
 * - app: the app name targeted by the simulation (default: appweb)
 */
class MultiDomainLoginPasswordFlow extends Simulation {

  val httpProtocol = http
    .userAgentHeader("Gatling - Multiple Domain Pwd Login Flow")
    .disableFollowRedirect

  val userGenerator = userFeeder(WORKLOAD)
  val domainGenerator = multiDomainsFeeder(WORKLOAD)
  val introspect = introspectFeeder()

  val scn = scenario("Multi Domain Password Login Flow")
    .feed(introspect)
    .feed(userGenerator)
    .feed(domainGenerator)
    .exec(requestAccessTokenWithUserCredentials())
    .doIf("#{introspect_enabled}")(
      pause(1)
      .exec(introspectToken())
      .pause(10)
      .repeat("#{introspections}") {
        exec(introspectToken())
      }
    )

  setUp(
    scn.inject(
      rampConcurrentUsers(1).to(AGENTS.intValue()).during(60),
      constantConcurrentUsers(AGENTS.intValue()).during(INJECTION_DURATION.seconds),
      rampConcurrentUsers(AGENTS.intValue()).to(1).during(60)
    )
  ).protocols(httpProtocol)
}

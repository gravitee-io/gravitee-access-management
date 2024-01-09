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
import io.gravitee.am.performance.utils.SimulationSettings._

/**
 * Purpose of this simulation is to generate a token for service app and call introspect endpoint (to simulate an API Gateway usage)
 * Possible arguments:
 * - gw_url: base URL of the Management REST API (default: http://localhost:8093)
 * - domain: the domain name prefix targeted by the simulation (default: gatling-domain)
 * - min_domain_index: minimal value of the domain index
 * - number_of_domains: size of the users range used to randomly select a domain between min_domain_index and (min_domain_index + number_of_domains) (default: 10)
 * - app: the application/client_id to use (clientSecret should be equals to clientId)
 * - inject-during: duration (in sec) of the agents load (default: 300 => 5 minutes)
 * - introspect: do we have to request token introspection (default: false)
 * - number_of_introspections: number of token introspection (default: 10)
 */
class MultiDomainServiceIntrospect extends Simulation {

  val httpProtocol = http
    .userAgentHeader("Gatling - Multi Domain Introspect")
    .disableFollowRedirect

  val domainGenerator = multiDomainsFeeder(WORKLOAD)
  val introspect = introspectFeeder()

  val scn = scenario("Multi Domain Service Simulation")
    .feed(introspect)
    .feed(domainGenerator)
    .exec(http("Ask Token")
      .post(GATEWAY_BASE_URL + "/#{domainName}/oauth/token")
      .basicAuth(APP_NAME, APP_NAME)
      .formParam("grant_type", "client_credentials")
      .check(status.is(200))
      .check(jsonPath("$.access_token").saveAs("access_token")))
    .doIf("#{introspect_enabled}")(
      pause(10)
        .repeat("#{introspections}") {
        exec(http("Introspect Access Token")
          .post(GATEWAY_BASE_URL + "/#{domainName}/oauth/introspect")
          .basicAuth(APP_NAME, APP_NAME)
          .formParam("token", "${access_token}")
          .check(status.is(200))
          .check(jsonPath("$.active").is("true")))
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

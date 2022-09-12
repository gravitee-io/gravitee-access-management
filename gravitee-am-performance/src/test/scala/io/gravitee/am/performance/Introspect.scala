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
import io.gravitee.am.performance.utils.GatewayCalls._

/**
 * Purpose of this simulation is to generate a roken and call introspect endpoint (to simulate an API Gateway usage)
 * Possible arguments:
 * - gw_url: base URL of the Management REST API (default: http://localhost:8093)
 * - domain: the domain name targeted by the simulation (default: gatling-domain)
 * - app: the application/client_id to use (clientSecret should be equals to clientsecret)
 */
class Introspect extends Simulation {

  val httpProtocol = http
    .userAgentHeader("Gatling - Introspect")
    .disableFollowRedirect

  val scn = scenario("Service Simulation")
    .exec(http("Ask Token")
      .post(GATEWAY_BASE_URL + s"/${DOMAIN_NAME}/oauth/token")
      .basicAuth(APP_NAME, APP_NAME)
      .formParam("grant_type", "client_credentials")
      .check(status.is(200))
      .check(jsonPath("$.access_token").saveAs("access_token")))
    .pause(10)
      .repeat(10) {
        exec(http("Introspect Access Token")
          .post(GATEWAY_BASE_URL + s"/${DOMAIN_NAME}/oauth/introspect")
          .basicAuth(APP_NAME, APP_NAME)
          .formParam("token", "${access_token}")
          .check(status.is(200))
          .check(jsonPath("$.active").is("true")))
    }


  setUp(scn.inject(constantUsersPerSec(50).during(240)))
    .protocols(httpProtocol)

}
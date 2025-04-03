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
 * Purpose of this simulation is to create a amount of user in a IdentityProvider
 * Possible arguments:
 * - gw_url: base URL of the Management REST API (default: http://localhost:8093)
 * - domain: the domain name targeted by the simulation (default: gatling-domain)
 * - min_user_index: minimal value of the user index
 * - number_of_users: size of the users range used to randomly select a user between min_user_index and (min_user_index + number_of_users) (default: 2000)
 * - agents: number of agent loaded per seconds (default: 10)
 * - inject-during: duration (in sec) of the agents load (default: 300 => 5 minutes)
 * - requests: number of requests per seconds to reach (default: 100)
 * - req-ramp-during: ramp duration (in sec)  (default: 10)
 * - req-hold-during: duration (in sec) of the simulation at the given rate of requests (default: 1800 => 30 minutes)
 * - factorId: factor internal Id to enroll & challenge
 */
class BasicLoginFlowWithMFA extends Simulation {

  val httpProtocol = http
    .userAgentHeader("Gatling - Basic Login Flow MFA")
    .disableFollowRedirect

  val userGenerator = userFeeder(WORKLOAD)

  val scn = scenario("Basic Login Flow with MFA")
    .feed(userGenerator)
    .exec(initCodeFlow())
    .exec(renderLoginForm())
    .pause(1)
    .exec(submitLoginForm())
    .exec(initCodeFlow()) // call authorization endpoint
    .doIfOrElse(session => session("postAuthorize").as[String].contains("enroll"))(
          exec(renderMfaEnrollForm())
          .pause(1)
          .exec(submitMfaEnrollForm())
          .exec(initCodeFlow())
          .exec(renderMfaChallengeForm())
          .pause(1)
          .exec(submitMfaChallengeForm())
    )(
          exec(renderMfaChallengeForm())
          .pause(1)
          .exec(submitMfaChallengeForm())
    )
    .exec(callPostLoginRedirect)
    .exec(requestAccessToken())
    .pause(1)
    .exec(introspectToken())
    .exec(logout())

  setUp(
    scn.inject(
    rampConcurrentUsers(1).to(AGENTS.intValue()).during(60),
    constantConcurrentUsers(AGENTS.intValue()).during(INJECTION_DURATION.seconds),
    rampConcurrentUsers(AGENTS.intValue()).to(1).during(60)
  ))
    .protocols(httpProtocol)

}

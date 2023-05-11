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
import io.gravitee.am.performance.utils.SimulationSettings.{AGENTS, DATALOAD, MAX_USER_INDEX, userFeeder}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class ScimUserUpdate extends Simulation {

  val httpProtocol = http
    .userAgentHeader("Gatling - Update SCIM Users")
    .disableFollowRedirect

  val userGenerator = userFeeder(DATALOAD)

  val scn = scenario("Create Scim Users")
    .exec(generateAccessToken("scim-app", "scim-app"))
    .feed(userGenerator)
    .doWhile(session => session("index").as[Int] < 2)(
      exec(createScimUser)
        .exec(patchScimUser)
        .feed(userGenerator)
    )

  setUp(scn.inject(atOnceUsers(1)).protocols(httpProtocol))

  /*setUp(scn.inject(atOnceUsers(50))).throttle(
    reachRps(200) in (10 seconds),
    holdFor(2 minutes),
    reachRps(300) in (10 seconds),
    holdFor(2 minutes)
  ).protocols(httpProtocol)*/
}

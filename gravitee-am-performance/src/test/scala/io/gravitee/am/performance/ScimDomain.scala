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

class ScimDomain extends Simulation {

  val httpProtocol = http
    .userAgentHeader("Gatling - SCIM Domain")
    .disableFollowRedirect

  val scn = scenario("Create SCIM Domain")
    .exec(login)
    .exec(createDomain("gatling-scim-test"))
    .exec(enableCurrentDomain)
    .exec(configureScim)
    .exec(createApplication("scim-app", "SERVICE"))
    .exec(addScopesToApp("scim-app", Array(ScopeSettings("scim", defaultScope = true))))

  setUp(scn.inject(atOnceUsers(1)).protocols(httpProtocol))

}

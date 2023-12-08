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
 * Purpose of this simulation is to create a simple domain with one application (Service) with scim scope
 * Possible arguments:
 * - mng_url: base URL of the Management REST API (default: http://localhost:8093)
 * - mng_user: username to request an access token to the Management REST API (default: admin)
 * - mng_password: password to request an access token to the Management REST API (default: adminadmin)
 * - domain: the domain name targeted by the simulation (default: gatling-domain)
 */
class CreateSCIMDomain extends Simulation {

  val httpProtocol = http
    .userAgentHeader("Gatling - Create SCIM Domain")
    .disableFollowRedirect

  val scn = scenario("Create SCIM Domain")
    .exec(login)
    .exec(createDomain(DOMAIN_NAME))
    .exec(enableCurrentDomain)
    .exec(createApplication("appservice", "SERVICE"))
    .exec(addScopesToApp("appservice", Array(ScopeSettings("scim"))))

  setUp(scn.inject(atOnceUsers(1)).protocols(httpProtocol))
}

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
package io.gravitee.am.performance.management.provisioning

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gravitee.am.performance.commands.ManagementAPICalls._
import io.gravitee.am.performance.utils.SimulationSettings._

/**
 * Purpose of this simulation is to create a amount of user in a IdentityProvider
 * Possible arguments:
 * - mng_url: base URL of the Management REST API (default: http://localhost:8093)
 * - mng_user: username to request an access token to the Management REST API (default: admin)
 * - mng_password: password to request an access token to the Management REST API (default: adminadmin)
 * - domain: the domain name targeted by the simulation (default: gatling-domain)
 * - idp: the IDP name targeted by the simulation (default: "Default Identity Provider")
 * - min_user_index: first value of the index used to create users (default: 1)
 * - number_of_users: how many users the simulation will create (default: 2000)
 * - agents: number of agents for the simulation (default: 10)
 */
class CreateUsers extends Simulation {

  val httpProtocol = http
    .userAgentHeader("Gatling - Create Users")
    .disableFollowRedirect

  val userGenerator = userFeeder(DATALOAD)

  val scn = scenario("Create Users")
    .exec(login)
    .exec(retrieveDomainId(DOMAIN_NAME))
    .exec(retrieveIdentityProviderId(IDENTITY_PROVIDER_NAME))
    .feed(userGenerator)
    .doWhile(session => session("index").as[Int] < MAX_USER_INDEX) (
        exec(createUser)
          .feed(userGenerator)
    )

  setUp(scn.inject(atOnceUsers(AGENTS)).protocols(httpProtocol))

}
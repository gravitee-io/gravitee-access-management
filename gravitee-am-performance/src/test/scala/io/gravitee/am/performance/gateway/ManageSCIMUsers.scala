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
import io.gravitee.am.performance.utils.SimulationSettings._

import scala.util.Random

/**
 * Purpose of this simulation is to manager users via the SCIM protocol
 *
 * Possible arguments:
 * - mng_url: base URL of the Management REST API (default: http://localhost:8093)
 * - mng_user: username to request an access token to the Management REST API (default: admin)
 * - mng_password: password to request an access token to the Management REST API (default: adminadmin)
 * - domain: the domain name targeted by the simulation (default: gatling-domain)
 * - app: the app name targeted by the simulation (default: appweb)
 * - idp: the IDP idp targeted by the simulation (default: "Default Identity Provider")
 * - min_user_index: first value of the index used to create users (default: 1)
 * - number_of_users: how many users the simulation will create (default: 2000)
 * - agents: number of agents for the simulation (default: 10)
 */
class ManageSCIMUsers extends Simulation {

  var scimAccessToken = ""

  val httpProtocol = http
    .userAgentHeader("Gatling - Manage SCIM Users")
    .disableFollowRedirect

  val idFeeder = Iterator.continually(Map("id" -> Random.nextInt(9999999)))

  val createAccessToken = scenario("Create SCIM access token")
    .exec(http("Ask Token")
      .post(GATEWAY_BASE_URL + s"/${DOMAIN_NAME}/oauth/token")
      .basicAuth(APP_NAME, APP_NAME)
      .formParam("grant_type", "client_credentials")
      .check(status.is(200))
      .check(jsonPath("$.access_token").saveAs("scimAccessToken")))
    .exec(session => {
        scimAccessToken = session("scimAccessToken").as[String].trim
        session
      }
    );

  val scn = scenario("Manage SCIM Users")
    .exec(_.set("scimAccessToken", scimAccessToken))
    .exec(_.set("idp", s"${IDENTITY_PROVIDER_NAME}"))
    .feed(idFeeder)
    .exec(http("Create SCIM USER")
      .post(GATEWAY_BASE_URL + s"/${DOMAIN_NAME}/scim/Users")
      .header("Authorization", "Bearer ${scimAccessToken}")
      .body(StringBody(
        """{
          |    "schemas": [
          |        "urn:ietf:params:scim:schemas:core:2.0:User"
          |    ],
          |    "externalId": "externalId_${id}",
          |    "userName": "username_${id}",
          |    "password": "Test@1234",
          |    "source": "${idp}",
          |    "emails": [
          |        {
          |            "primary": true,
          |            "value": "user@acme.fr"
          |        }
          |    ]
          |}""".stripMargin)).asJson
      .check(status.is(201))
      .check(jsonPath("$.id").saveAs("scim_user_id")))
    .exec(http("Patch SCIM USER")
      .patch(GATEWAY_BASE_URL + s"/${DOMAIN_NAME}/scim/Users/" + "#{scim_user_id}")
      .header("Authorization", "Bearer #{scimAccessToken}")
      .body(StringBody(
        """{
          |    "schemas": [
          |        "urn:ietf:params:scim:api:messages:2.0:PatchOp"
          |    ],
          |    "Operations": [
          |        {
          |            "op": "replace",
          |            "value" : {
          |               "password": "Test@12345"
          |            }
          |        }
          |    ]
          |}""".stripMargin)).asJson
      .check(status.is(200)));

  setUp(
    createAccessToken.inject(atOnceUsers(1))
      .andThen(
        scn.inject(constantUsersPerSec(AGENTS.floatValue()).during(INJECTION_DURATION.seconds))
      )
  )
    .protocols(httpProtocol)
    .throttle(reachRps(REQUEST_PER_SEC).in(REQUEST_RAMP_DURATION.seconds), holdFor(REQUEST_HOLD_DURING.seconds))

  setUp(
    scn.inject(
      rampConcurrentUsers(1).to(AGENTS.intValue()).during(60),
      constantConcurrentUsers(AGENTS.intValue()).during(INJECTION_DURATION.seconds),
      rampConcurrentUsers(AGENTS.intValue()).to(1).during(60)
    )
  )
}

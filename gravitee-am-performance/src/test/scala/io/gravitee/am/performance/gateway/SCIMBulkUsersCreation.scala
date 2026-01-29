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
import io.gatling.core.session.Session
import io.gravitee.am.performance.commands.ManagementAPICalls

/**
 * Purpose of this simulation is to create users with SCIM bulk command
 *
 * Possible arguments:
 * - gw_url: base URL of the Gateway (default: http://localhost:8092)
 * - domain: the domain name targeted by the simulation (default: gatling-domain)
 * - app: the app name targeted by the simulation (default: appweb)
 * - idp: the IDP idp targeted by the simulation (default: "Default Identity Provider")
 * - min_user_index: first value of the index used to create users (default: 1)
 * - number_of_users: how many users the simulation will create (default: 2000)
 * - batch_size: how many users are part of a single Bulk request (default: 500)
 * - agents: number of agents for the simulation (default: 10)
 */
class SCIMBulkUsersCreation extends Simulation {

  var scimAccessToken = ""
  var identityProviderId = ""

  val MIN_USER_INDEX: Int = Integer.getInteger("min_user_index", 1).intValue()
  val NUMBER_OF_USERS: Int = Integer.getInteger("number_of_users", 2000).intValue()
  val BATCH_SIZE: Int = Integer.getInteger("batch_size", 500).intValue()

  val httpProtocol = http
    .userAgentHeader("Gatling - Manage SCIM Users")
    .disableFollowRedirect
          .shareConnections

  // Feeder that generates bulk operation batches
  val bulkOperationsFeeder = Iterator.from(0).map { batchIndex =>
    val startIndex = MIN_USER_INDEX + (batchIndex * BATCH_SIZE)
    val endIndex = Math.min(startIndex + BATCH_SIZE, MIN_USER_INDEX + NUMBER_OF_USERS)

    Map("startIndex" -> startIndex, "endIndex" -> endIndex, "batchIndex" -> batchIndex)
  }

  val initializeIdentityProvider = scenario("Initialize Identity Provider")
    .exec(ManagementAPICalls.login)
    .exec(ManagementAPICalls.retrieveDomainId(DOMAIN_NAME))
    .exec(ManagementAPICalls.retrieveIdentityProviderId(IDENTITY_PROVIDER_NAME))
    .exec(session => {
      identityProviderId = session("identityId").as[String].trim
      session
    })

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

  // Function to generate bulk operations JSON dynamically
  def generateBulkRequestBody(session: Session): String = {
    val startIndex = session("startIndex").as[Int]
    val endIndex = session("endIndex").as[Int]
    val idpId = session("idpId").as[String]

    val operations = (startIndex until endIndex).map { userIndex =>
      s"""{
         |      "method": "POST",
         |      "path": "/Users",
         |      "bulkId": "user_$userIndex",
         |      "data": {
         |        "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
         |        "externalId": "externalId_$userIndex",
         |        "userName": "username_$userIndex",
         |        "password": "Gr@v1t33B3nchUs3rs!",
         |        "source": "$idpId",
         |        "emails": [
         |          {
         |            "primary": true,
         |            "value": "user_$userIndex@acme.fr"
         |          }
         |        ]
         |      }
         |    }""".stripMargin
    }.mkString(",\n")

    s"""{
       |  "schemas": ["urn:ietf:params:scim:api:messages:2.0:BulkRequest"],
       |  "failOnErrors": 1,
       |  "Operations": [
       |$operations
       |  ]
       |}""".stripMargin
  }

  val scn = scenario("Bulk Create SCIM Users")
    .exec(_.set("scimAccessToken", scimAccessToken))
    .exec(_.set("idpId", identityProviderId))
    .repeat(session => {
      // Calculate how many batches this agent should process
      val agentId = session.userId
      val totalBatches = Math.ceil(NUMBER_OF_USERS.toDouble / BATCH_SIZE).toInt
      val batchesPerAgent = Math.ceil(totalBatches.toDouble / AGENTS.intValue()).toInt
      val startBatch = (agentId - 1) * batchesPerAgent
      val endBatch = Math.min(startBatch + batchesPerAgent, totalBatches)
      val batchesToProcess = Math.max(0, endBatch - startBatch).toInt
      batchesToProcess
    }, "batchCounter") {
      feed(bulkOperationsFeeder)
        .exec(http("Create SCIM Users via Bulk")
          .post(GATEWAY_BASE_URL + s"/${DOMAIN_NAME}/scim/Bulk")
          .header("Authorization", "Bearer #{scimAccessToken}")
          .body(StringBody(generateBulkRequestBody _)).asJson
          .check(status.is(200))
          .check(jsonPath("$.Operations[*].status").findAll.saveAs("operationStatuses")))
    }

  setUp(
    initializeIdentityProvider.inject(atOnceUsers(1))
      .andThen(
        createAccessToken.inject(atOnceUsers(1))
      )
      .andThen(
        scn.inject(atOnceUsers(AGENTS.intValue()))
      )
  ).protocols(httpProtocol)
}

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
package io.gravitee.am.performance.authorization

import io.gatling.core.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder
import io.gravitee.am.performance.commands.GatewayCalls
import io.gravitee.am.performance.utils.JsonUtils.mapToJson
import io.gravitee.am.performance.utils.SimulationSettings._
import ujson._

/**
 * The purpose of this simulation is to benchmark authorization evaluation calls
 * against the AuthZen API via AM Gateway, based on data seeded by OpenFGAProvision.
 *
 * Possible arguments:
 * - gw_url: base URL of the Gateway REST API (default: http://localhost:8092)
 * - domain: domain name targeted by the simulation (default: gatling-domain)
 * - client_id: client id used to request OAuth2 access token
 * - client_secret: client secret used to request OAuth2 access token
 * - number_of_users: how many users the provisioning data created
 * - number_of_teams: how many teams the provisioning data created
 * - depth_of_teams: maximum depth of teams in tree hierarchy
 * - agents: number of concurrent agents (default: 10)
 * - inject-during: duration (in sec) of the steady-state load (default: 300)
 * - repeat: number of evaluation checks each virtual agent performs per iteration (default: 10)
 */
class AuthZenEvaluation
    extends AuthorizationEvaluation(AuthZenAuthorizationBackend, Set(AuthorizationEvaluation.ComparableTag)) {
  require(CLIENT_ID.nonEmpty, "client_id system property must be set")
  require(CLIENT_SECRET.nonEmpty, "client_secret system property must be set")
}

object AuthZenAuthorizationBackend extends AuthorizationBackend {
  override val name: String = "authzen"

  override def authenticate = exec(GatewayCalls.requestAuthzenAccessToken())

  private def parseEntity(value: String): (String, String) = {
    val parts = value.split(":", 2)
    val entityType = parts.headOption.getOrElse("")
    val entityId = if (parts.length > 1) parts(1) else ""
    (entityType, entityId)
  }

  override def buildRequestBody(testCase: EvaluationCase): String = {
    val (subjectType, subjectId) = parseEntity(testCase.tupleKey.user)
    val (resourceType, resourceId) = parseEntity(testCase.tupleKey.objectKey)
    val requestBody = Obj(
      "subject" -> Obj(
        "type" -> subjectType,
        "id" -> subjectId
      ),
      "resource" -> Obj(
        "type" -> resourceType,
        "id" -> resourceId
      ),
      "action" -> Obj(
        "name" -> testCase.tupleKey.relation
      )
    )

    testCase.context.foreach(ctx => requestBody("context") = mapToJson(ctx))
    ujson.write(requestBody)
  }

  override def checkAuthorization(description: String): HttpRequestBuilder =
    GatewayCalls.authzenEvaluateAccess(description)
}

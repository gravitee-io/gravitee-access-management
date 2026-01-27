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

import io.gatling.http.request.builder.HttpRequestBuilder
import io.gravitee.am.performance.commands.OpenFGACalls
import io.gravitee.am.performance.commands.OpenFGACalls._
import io.gravitee.am.performance.utils.SimulationSettings._

/**
 * The purpose of this simulation is to benchmark authorization evaluation calls
 * against OpenFGA, based on data seeded by OpenFGAProvision.
 *
 * Possible arguments:
 * - fga_api_url: base URL of the OpenFGA REST API (default: http://localhost:8080)
 * - fga_store_id: OpenFGA Store identifier
 * - fga_authorization_model_id: OpenFGA authorization model identifier
 * - number_of_users: how many users have been provisioned
 * - number_of_teams: how many teams have been provisioned
 * - depth_of_teams: maximum depth of teams in tree hierarchy
 * - evaluation_tags: optional comma-separated tags to filter evaluation cases
 * - agents: number of concurrent agent (default: 10)
 * - inject-during: duration (in sec) of the steady-state load (default: 300)
 * - repeat: number of evaluation checks each virtual agent performs per iteration (default: 10)
  */
class OpenFGAEvaluation
    extends AuthorizationEvaluation(OpenFGAAuthorizationBackend, OpenFGAEvaluation.tagFilter) {
  require(FGA_STORE_ID.nonEmpty, "fga_store_id system property must be set")
  require(FGA_AUTHORIZATION_MODEL_ID.nonEmpty, "fga_authorization_model_id system property must be set")
}

object OpenFGAEvaluation {
  val tagFilter: Set[String] = System
    .getProperty("evaluation_tags", "")
    .split(",")
    .map(_.trim)
    .filter(_.nonEmpty)
    .toSet
}

object OpenFGAAuthorizationBackend extends AuthorizationBackend {
  override val name: String = "openfga"

  override def buildRequestBody(testCase: EvaluationCase): String = {
    val tuples = if (testCase.contextualTuples.nonEmpty) Some(testCase.contextualTuples) else None
    checkToJsonRequestBody(
      user = testCase.tupleKey.user,
      relation = testCase.tupleKey.relation,
      objectKey = testCase.tupleKey.objectKey,
      context = testCase.context,
      contextualTuples = tuples
    )
  }

  override def checkAuthorization(description: String): HttpRequestBuilder =
    OpenFGACalls.checkAuthorization(description)
}

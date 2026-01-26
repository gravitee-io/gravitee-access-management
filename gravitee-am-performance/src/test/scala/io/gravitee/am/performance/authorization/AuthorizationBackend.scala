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

trait AuthorizationBackend {
  def name: String
  def buildRequestBody(testCase: EvaluationCase): String
  def checkAuthorization(description: String): HttpRequestBuilder
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

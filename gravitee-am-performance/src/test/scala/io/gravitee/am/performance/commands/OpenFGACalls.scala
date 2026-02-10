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
package io.gravitee.am.performance.commands

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gravitee.am.performance.utils.JsonUtils.mapToJson
import io.gravitee.am.performance.utils.SimulationSettings._
import ujson._

object OpenFGACalls {

  case class Condition(name: String, context: Map[String, Any])

  case class Tuple(user: String, relation: String, objectKey: String, condition: Option[Condition] = None)

  case class TupleKey(user: String, relation: String, objectKey: String)

  /**
   * Convert a Condition to a uJSON object
   */
  private def conditionToJson(condition: Condition): Obj = {
    Obj(
      "name" -> condition.name,
      "context" -> mapToJson(condition.context)
    )
  }

  /**
   * Convert a Tuple to a uJSON object
   */
  private def tupleToJson(tuple: Tuple): Obj = {
    val baseObj = Obj(
      "user" -> tuple.user,
      "relation" -> tuple.relation,
      "object" -> tuple.objectKey
    )
    
    tuple.condition match {
      case Some(cond) => baseObj("condition") = conditionToJson(cond); baseObj
      case None => baseObj
    }
  }

  /**
   * Convert a TupleKey to a uJSON object
   */
  private def tupleKeyToJson(tuple: TupleKey): Obj = {
    Obj(
      "user" -> tuple.user,
      "relation" -> tuple.relation,
      "object" -> tuple.objectKey
    )
  }

  /**
   * Convert a list of tuples to a JSON request body string for OpenFGA write API
   */
  def tuplesToJsonRequestBody(tuples: List[Tuple]): String = {
    val requestBody = Obj(
      "writes" -> Obj(
        "tuple_keys" -> Arr.from(tuples.map(tupleToJson)),
        "on_duplicate" -> "error"
      ),
      "authorization_model_id" -> FGA_AUTHORIZATION_MODEL_ID
    )
    
    ujson.write(requestBody)
  }

  /**
   * Convert check inputs to a JSON request body string for OpenFGA check API
   */
  def checkToJsonRequestBody(
    user: String,
    relation: String,
    objectKey: String,
    context: Option[Map[String, Any]] = None,
    contextualTuples: Option[List[TupleKey]] = None
  ): String = {
    val requestBody = Obj(
      "tuple_key" -> tupleKeyToJson(TupleKey(user, relation, objectKey)),
      "authorization_model_id" -> FGA_AUTHORIZATION_MODEL_ID,
      "consistency" -> "HIGHER_CONSISTENCY"
    )

    context.foreach(ctx => requestBody("context") = mapToJson(ctx))
    contextualTuples.foreach { tuples =>
      requestBody("contextual_tuples") = Obj(
        "tuple_keys" -> Arr.from(tuples.map(tupleKeyToJson))
      )
    }

    ujson.write(requestBody)
  }

  def getStore = {
    http("Get Store")
      .get(FGA_API_URL + s"/stores/${FGA_STORE_ID}")
      .header("Authorization", s"Bearer ${FGA_API_TOKEN}")
      .check(status.is(200))
  }

  def listAuthorizationModels = {
    http("List Authorization Models")
      .get(FGA_API_URL + s"/stores/${FGA_STORE_ID}/authorization-models")
      .header("Authorization", s"Bearer ${FGA_API_TOKEN}")
      .check(status.is(200))
  }

  def getAuthorizationModel = {
    http("Get Authorization Model")
      .get(FGA_API_URL + s"/stores/${FGA_STORE_ID}/authorization-models/${FGA_AUTHORIZATION_MODEL_ID}")
      .header("Authorization", s"Bearer ${FGA_API_TOKEN}")
      .check(status.is(200))
  }

  def writeTuplesFrom(description: String, tuples: List[Tuple]) = {
    http("Write Tuples: " + description)
      .post(FGA_API_URL + s"/stores/${FGA_STORE_ID}/write")
      .header("Authorization", s"Bearer ${FGA_API_TOKEN}")
      .body(StringBody(tuplesToJsonRequestBody(tuples))).asJson
      .check(status.is(200))
  }

  def writeTuples(description: String) = {
    http("Write Tuples: " + description)
      .post(FGA_API_URL + s"/stores/${FGA_STORE_ID}/write")
      .header("Authorization", s"Bearer ${FGA_API_TOKEN}")
      .body(StringBody("#{tupleRequestBody}")).asJson
      .check(status.is(200))
  }

  def checkAuthorization(description: String) = {
    http("Check Authorization: " + description)
      .post(FGA_API_URL + s"/stores/${FGA_STORE_ID}/check")
      .header("Authorization", s"Bearer ${FGA_API_TOKEN}")
      .body(StringBody("#{checkRequestBody}")).asJson
      .check(status.is(200))
      .check(jsonPath("$.allowed").ofType[Boolean].saveAs("allowed"))
  }
}

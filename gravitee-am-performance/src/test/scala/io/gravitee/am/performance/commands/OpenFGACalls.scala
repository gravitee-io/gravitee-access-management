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
import io.gravitee.am.performance.utils.SimulationSettings._

object OpenFGACalls {

  case class Condition(name: String, context: Map[String, Any])

  case class Tuple(user: String, relation: String, objectKey: String, condition: Option[Condition] = None)

  /**
   * Escape JSON string values by replacing quotes and backslashes
   */
  private def escapeJsonString(value: String): String = {
    value.replace("\\", "\\\\").replace("\"", "\\\"")
  }

  /**
   * Serialize a context map value to JSON string representation
   */
  private def serializeContextValue(value: Any): String = {
    value match {
      case s: String => s""""${escapeJsonString(s)}""""
      case i: Int => i.toString
      case l: Long => l.toString
      case d: Double => d.toString
      case f: Float => f.toString
      case b: Boolean => b.toString
      case null => "null"
      case _ => s""""${escapeJsonString(value.toString)}""""
    }
  }

  /**
   * Serialize a condition context map to JSON string
   */
  private def serializeContext(context: Map[String, Any]): String = {
    if (context.isEmpty) {
      ""
    } else {
      context.map { case (key, value) =>
        val escapedKey = escapeJsonString(key)
        val serializedValue = serializeContextValue(value)
        s""""$escapedKey":$serializedValue"""
      }.mkString(",")
    }
  }

  /**
   * Serialize a condition to JSON string
   */
  private def serializeCondition(condition: Condition): String = {
    val name = escapeJsonString(condition.name)
    val context = serializeContext(condition.context)
    s"""{"name":"$name","context":{$context}}"""
  }

  /**
   * Convert a list of tuples to a JSON request body string for OpenFGA write API
   */
  def tuplesToJsonRequestBody(tuples: List[Tuple]): String = {
    val tupleKeysJson = tuples.map { tuple =>
      val user = escapeJsonString(tuple.user)
      val relation = escapeJsonString(tuple.relation)
      val objectKey = escapeJsonString(tuple.objectKey)
      val conditionJson = tuple.condition.map(c => s""","condition":${serializeCondition(c)}""").getOrElse("")
      s"""{"user":"$user","relation":"$relation","object":"$objectKey"$conditionJson}"""
    }.mkString(",")
    s"""{"writes":{"tuple_keys":[$tupleKeysJson],"on_duplicate":"error"},"authorization_model_id":"$FGA_AUTHORIZATION_MODEL_ID"}"""
  }

  def getStore = {
    http("Get Store")
      .get(FGA_API_URL + s"/stores/${FGA_STORE_ID}")
      .check(status.is(200))
  }

  def listAuthorizationModels = {
    http("List Authorization Models")
      .get(FGA_API_URL + s"/stores/${FGA_STORE_ID}/authorization-models")
      .check(status.is(200))
  }

  def getAuthorizationModel = {
    http("Get Authorization Model")
      .get(FGA_API_URL + s"/stores/${FGA_STORE_ID}/authorization-models/${FGA_AUTHORIZATION_MODEL_ID}")
      .check(status.is(200))
  }

  def writeTuplesFrom(description: String, tuples: List[Tuple]) = {
    http("Write Tuples: " + description)
      .post(FGA_API_URL + s"/stores/${FGA_STORE_ID}/write")
      .body(StringBody(tuplesToJsonRequestBody(tuples))).asJson
      .check(status.is(200))
  }

  def writeTuples(description: String) = {
    http("Write Tuples: " + description)
      .post(FGA_API_URL + s"/stores/${FGA_STORE_ID}/write")
      .body(StringBody("#{tupleRequestBody}")).asJson
      .check(status.is(200))
  }
}

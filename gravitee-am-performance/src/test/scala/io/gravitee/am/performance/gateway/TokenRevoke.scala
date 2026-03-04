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
import io.gravitee.am.performance.commands.GatewayCalls._
import io.gravitee.am.performance.utils.SimulationSettings._

import java.net.URI
import java.net.URLEncoder
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

class TokenRevoke extends Simulation {

  private val ProgressLogEvery = 1000L

  require(CLIENT_ID.nonEmpty, "client_id parameter is required")
  require(CLIENT_SECRET.nonEmpty, "client_secret parameter is required")
  require(NUMBER_OF_TOKENS >= 1, "number_of_tokens must be >= 1")
  require(AGENTS >= 1, "agents must be >= 1")

  private val httpProtocol = http
    .userAgentHeader("Gatling - Token Revoke")
    .disableFollowRedirect

  private val baseOauthUrl = s"${GATEWAY_BASE_URL}/${DOMAIN_NAME}/oauth"
  private val authHeader = {
    val value = s"${CLIENT_ID}:${CLIENT_SECRET}"
    "Basic " + Base64.getEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8))
  }

  private val expectedTotalGenerated: BigInt = BigInt(NUMBER_OF_TOKENS.intValue())
  private val expectedTotalRevokes: Long = NUMBER_OF_TOKENS.longValue()

  private val generatedCounter = new AtomicLong(0)

  @volatile private var tokensByAgent: Vector[Vector[String]] = Vector.empty

  private def logProgress(prefix: String, current: Long, total: BigInt): Unit = {
    val pct = if (total == 0) 100.0 else (BigDecimal(current) * 100 / BigDecimal(total)).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
    println(s"[TokenRevoke] ${prefix}: ${current}/${total} (${pct}%)")
  }

  private def maybeLogEvery(counterValue: Long, total: BigInt, prefix: String): Unit = {
    if (counterValue % ProgressLogEvery == 0 || BigInt(counterValue) == total) {
      logProgress(prefix, counterValue, total)
    }
  }

  private def formEncode(params: Seq[(String, String)]): String = {
    params.map { case (k, v) =>
      s"${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
    }.mkString("&")
  }

  private def extractAccessToken(responseBody: String): String = {
    "\"access_token\"\\s*:\\s*\"([^\"]+)\"".r
      .findFirstMatchIn(responseBody)
      .map(_.group(1))
      .getOrElse(throw new IllegalStateException(s"Cannot extract access_token from response: ${responseBody.take(500)}"))
  }

  private def callTokenEndpoint(httpClient: HttpClient, params: Seq[(String, String)]): String = {
    val request = HttpRequest.newBuilder()
      .uri(URI.create(baseOauthUrl + "/token"))
      .header("Authorization", authHeader)
      .header("Content-Type", "application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString(formEncode(params)))
      .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() / 100 != 2) {
      throw new IllegalStateException(s"Token endpoint returned ${response.statusCode()} with body: ${response.body().take(500)}")
    }
    extractAccessToken(response.body())
  }

  private def requestClientCredentialsToken(httpClient: HttpClient): String = {
    callTokenEndpoint(httpClient, Seq("grant_type" -> "client_credentials"))
  }

  private def incGeneratedCounter(): Unit = {
    val current = generatedCounter.incrementAndGet()
    maybeLogEvery(current, expectedTotalGenerated, "Generated tokens")
  }

  private def splitTokens(tokens: Vector[String]): Vector[Vector[String]] = {
    val total = tokens.length
    val agents = AGENTS.intValue()
    val base = total / agents
    val remainder = total % agents
    var offset = 0

    (0 until agents).map { index =>
      val size = base + (if (index < remainder) 1 else 0)
      val slice = if (size > 0) tokens.slice(offset, offset + size) else Vector.empty
      offset += size
      slice
    }.toVector
  }

  before {
    println(s"[TokenRevoke] Preparation started")
    println(s"[TokenRevoke] Expected generated tokens: ${expectedTotalGenerated}")
    println(s"[TokenRevoke] Expected revoke operations: ${expectedTotalRevokes}")

    val httpClient = HttpClient.newHttpClient()
    val tokensBuilder = Vector.newBuilder[String]

    (0 until NUMBER_OF_TOKENS.intValue()).foreach { _ =>
      tokensBuilder += requestClientCredentialsToken(httpClient)
      incGeneratedCounter()
    }

    val tokens = tokensBuilder.result()
    tokensByAgent = splitTokens(tokens)
    println(s"[TokenRevoke] Preparation completed. Tokens ready for revoke: ${tokens.length}")
  }

  private val scn = scenario("Token Revoke")
    .exec { session =>
      val agentIndex = (session.userId % AGENTS.intValue()).toInt
      session
        .set("domainName", DOMAIN_NAME)
        .set("tokensToRevoke", tokensByAgent(agentIndex))
    }
    .foreach("#{tokensToRevoke}", "token") {
      group("Revoke Token") {
        exec(revokeToken("#{token}", CLIENT_ID, CLIENT_SECRET))
      }
    }

  setUp(
    scn.inject(atOnceUsers(AGENTS.intValue()))
  ).protocols(httpProtocol)
}

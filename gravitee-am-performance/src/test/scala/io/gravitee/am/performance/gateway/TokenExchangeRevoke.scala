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
import io.gravitee.am.performance.utils.TokenTreeTraversal.{CursorState, nextBoundedWidthRequest, nextBranchFactorRequest}

import java.net.URI
import java.net.URLEncoder
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

class TokenExchangeRevoke extends Simulation {

  private val ProgressLogEvery = 1000L

  require(CLIENT_ID.nonEmpty, "client_id parameter is required")
  require(CLIENT_SECRET.nonEmpty, "client_secret parameter is required")
  require(DEPTH >= 1, "depth must be >= 1")
  require(NUMBER_OF_TREES >= 1, "number_of_trees must be >= 1")
  require(BRANCH_FACTOR >= 1, "branch_factor must be >= 1")
  require(LEVEL_WIDTH >= 0, "level_width must be >= 0")
  require(AGENTS >= 1, "agents must be >= 1")

  private val httpProtocol = http
    .userAgentHeader("Gatling - Token Revoke")
    .disableFollowRedirect

  private val baseOauthUrl = s"${GATEWAY_BASE_URL}/${DOMAIN_NAME}/oauth"
  private val authHeader = {
    val value = s"${CLIENT_ID}:${CLIENT_SECRET}"
    "Basic " + Base64.getEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8))
  }

  private val expectedGeneratedPerTree: BigInt = {
    if (LEVEL_WIDTH.intValue() > 0) {
      BigInt(2) + BigInt(DEPTH.intValue() - 1) * BigInt(LEVEL_WIDTH.intValue())
    } else if (BRANCH_FACTOR.intValue() == 1) {
      BigInt(2) * BigInt(DEPTH.intValue())
    } else {
      val b = BigInt(BRANCH_FACTOR.intValue())
      BigInt(2) * ((b.pow(DEPTH.intValue()) - 1) / (b - 1))
    }
  }

  private val expectedTotalGenerated: BigInt =
    BigInt(AGENTS.intValue()) * (BigInt(1) + BigInt(NUMBER_OF_TREES.intValue()) * expectedGeneratedPerTree)

  private val expectedTotalRevokes: Long = AGENTS.longValue() * NUMBER_OF_TREES.longValue() * 2L

  private val generatedCounter = new AtomicLong(0)
  private val revokeCounter = new AtomicLong(0)

  @volatile private var rootTokensByAgent: Vector[Vector[String]] = Vector.empty

  private def logProgress(prefix: String, current: Long, total: BigInt): Unit = {
    val pct = if (total == 0) 100.0 else (BigDecimal(current) * 100 / BigDecimal(total)).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
    println(s"[TokenExchangeRevoke] ${prefix}: ${current}/${total} (${pct}%)")
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

  private def exchangeImpersonationToken(httpClient: HttpClient, subjectToken: String): String = {
    callTokenEndpoint(httpClient, Seq(
      "grant_type" -> "urn:ietf:params:oauth:grant-type:token-exchange",
      "subject_token" -> subjectToken,
      "subject_token_type" -> "urn:ietf:params:oauth:token-type:access_token"
    ))
  }

  private def exchangeDelegationToken(httpClient: HttpClient, subjectToken: String, actorToken: String): String = {
    callTokenEndpoint(httpClient, Seq(
      "grant_type" -> "urn:ietf:params:oauth:grant-type:token-exchange",
      "subject_token" -> subjectToken,
      "subject_token_type" -> "urn:ietf:params:oauth:token-type:access_token",
      "actor_token" -> actorToken,
      "actor_token_type" -> "urn:ietf:params:oauth:token-type:access_token"
    ))
  }

  private def createRoot(httpClient: HttpClient, parentToken: String): String = {
    if (USE_DELEGATION_MODE) {
      exchangeDelegationToken(httpClient, parentToken, parentToken)
    } else {
      exchangeImpersonationToken(httpClient, parentToken)
    }
  }

  private def createDescendant(httpClient: HttpClient, subjectToken: String, actorToken: Option[String]): String = {
    if (USE_DELEGATION_MODE) {
      exchangeDelegationToken(httpClient, subjectToken, actorToken.get)
    } else {
      exchangeImpersonationToken(httpClient, subjectToken)
    }
  }

  private def incGeneratedCounter(): Unit = {
    val current = generatedCounter.incrementAndGet()
    maybeLogEvery(current, expectedTotalGenerated, "Generated tokens")
  }

  before {
    println(s"[TokenExchangeRevoke] Preparation started (use_delegation_mode=${USE_DELEGATION_MODE})")
    println(s"[TokenExchangeRevoke] Expected generated tokens: ${expectedTotalGenerated}")
    println(s"[TokenExchangeRevoke] Expected revoke operations: ${expectedTotalRevokes}")

    val httpClient = HttpClient.newHttpClient()
    val rootsPerAgent = scala.collection.mutable.ArrayBuffer.empty[Vector[String]]

    (0 until AGENTS.intValue()).foreach { _ =>
      val parentToken = requestClientCredentialsToken(httpClient)
      incGeneratedCounter()

      val revokeRoots = scala.collection.mutable.ArrayBuffer.empty[String]

      (0 until NUMBER_OF_TREES.intValue()).foreach { _ =>
        val rootA = createRoot(httpClient, parentToken)
        incGeneratedCounter()
        val rootB = createRoot(httpClient, parentToken)
        incGeneratedCounter()

        revokeRoots += rootA
        revokeRoots += rootB

        var currentLevel = Vector(rootA, rootB)
        var currentDepth = 1
        var actorCursor = 0
        var subjectCursor = 0

        while (currentDepth < DEPTH.intValue()) {
          val nextLevel = scala.collection.mutable.ArrayBuffer.empty[String]

          if (LEVEL_WIDTH.intValue() > 0) {
            (0 until LEVEL_WIDTH.intValue()).foreach { _ =>
              val state = CursorState(subjectCursor = subjectCursor, actorCursor = actorCursor)
              val (exchangeRequest, nextState) = nextBoundedWidthRequest(
                currentLevel,
                state,
                useDelegationMode = USE_DELEGATION_MODE
              )
              subjectCursor = nextState.subjectCursor
              actorCursor = nextState.actorCursor

              val child = createDescendant(httpClient, exchangeRequest.subjectToken, exchangeRequest.actorToken)
              nextLevel += child
              incGeneratedCounter()
            }
          } else {
            currentLevel.foreach { subject =>
              (0 until BRANCH_FACTOR.intValue()).foreach { _ =>
                val (exchangeRequest, nextActorCursor) = nextBranchFactorRequest(
                  currentLevel,
                  subject,
                  actorCursor,
                  useDelegationMode = USE_DELEGATION_MODE
                )
                actorCursor = nextActorCursor

                val child = createDescendant(httpClient, exchangeRequest.subjectToken, exchangeRequest.actorToken)
                nextLevel += child
                incGeneratedCounter()
              }
            }
          }

          currentLevel = nextLevel.toVector
          currentDepth += 1
        }
      }

      rootsPerAgent += revokeRoots.toVector
    }

    rootTokensByAgent = rootsPerAgent.toVector
    println(s"[TokenExchangeRevoke] Preparation completed. Root tokens ready for revoke: ${rootTokensByAgent.map(_.size).sum}")
  }

  private val scn = scenario("Token Exchange Revoke")
    .exec { session =>
      val agentIndex = (session.userId % AGENTS.intValue()).toInt
      session
        .set("domainName", DOMAIN_NAME)
        .set("rootTokensToRevoke", rootTokensByAgent(agentIndex))
    }
    .foreach("#{rootTokensToRevoke}", "rootToken") {
      group("Revoke Root Token") {
        exec(revokeToken("#{rootToken}", CLIENT_ID, CLIENT_SECRET))
          .exec { session =>
            val current = revokeCounter.incrementAndGet()
            maybeLogEvery(current, BigInt(expectedTotalRevokes), "Revoked tokens")
            session
          }
      }
    }

  setUp(
    scn.inject(atOnceUsers(AGENTS.intValue()))
  ).protocols(httpProtocol)
}

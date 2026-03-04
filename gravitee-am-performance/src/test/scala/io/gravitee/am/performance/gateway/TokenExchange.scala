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
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gravitee.am.performance.commands.GatewayCalls._
import io.gravitee.am.performance.utils.SimulationSettings._
import io.gravitee.am.performance.utils.TokenTreeTraversal.{CursorState, nextBoundedWidthRequest, nextBranchFactorRequest}

class TokenExchange extends Simulation {

  require(CLIENT_ID.nonEmpty, "client_id parameter is required")
  require(CLIENT_SECRET.nonEmpty, "client_secret parameter is required")
  require(DEPTH >= 1, "depth must be >= 1")
  require(NUMBER_OF_TREES >= 1, "number_of_trees must be >= 1")
  require(BRANCH_FACTOR >= 1, "branch_factor must be >= 1")
  require(LEVEL_WIDTH >= 0, "level_width must be >= 0")
  require(AGENTS >= 1, "agents must be >= 1")

  private val httpProtocol = http
    .userAgentHeader("Gatling - Token Exchange")
    .disableFollowRedirect

  private val domainFeeder = Iterator.continually(Map("domainName" -> DOMAIN_NAME))

  private val scn = scenario("Token Exchange")
    .feed(domainFeeder)
    .exec(requestClientCredentialsToken(CLIENT_ID, CLIENT_SECRET))
    .exec(session => session.set("parentToken", session(TOKEN_EXCHANGE_ACCESS_TOKEN_KEY).as[String]))
    .repeat(NUMBER_OF_TREES.intValue()) {
      TokenExchange.buildTokenTree(
        groupName = "Insert Token Tree",
        clientId = CLIENT_ID,
        clientSecret = CLIENT_SECRET,
        depth = DEPTH.intValue(),
        branchFactor = BRANCH_FACTOR.intValue(),
        levelWidth = LEVEL_WIDTH.intValue(),
        useDelegationMode = USE_DELEGATION_MODE,
        collectRootTokensForRevoke = false
      )
    }

  setUp(
    scn.inject(atOnceUsers(AGENTS.intValue()))
  ).protocols(httpProtocol)
}

object TokenExchange {

  private def addGeneratedTokenToNextLevel: ChainBuilder = {
    exec { session =>
      val childToken = session(TOKEN_EXCHANGE_ACCESS_TOKEN_KEY).as[String]
      val updatedNextLevel = session("nextLevelTokens").as[Vector[String]] :+ childToken
      session.set("nextLevelTokens", updatedNextLevel)
    }
  }

  private def buildBoundedWidthLevel(
      clientId: String,
      clientSecret: String,
      levelWidth: Int,
      useDelegationMode: Boolean
  ): ChainBuilder = {
    repeat(levelWidth) {
      if (useDelegationMode) {
        exec { session =>
          val currentLevel = session("currentLevelTokens").as[Vector[String]]
          val state = CursorState(
            subjectCursor = session("subjectCursor").as[Int],
            actorCursor = session("actorCursor").as[Int]
          )
          val (exchangeRequest, nextState) = nextBoundedWidthRequest(currentLevel, state, useDelegationMode = true)
          session
            .set("subjectToken", exchangeRequest.subjectToken)
            .set("actorToken", exchangeRequest.actorToken.get)
            .set("subjectCursor", nextState.subjectCursor)
            .set("actorCursor", nextState.actorCursor)
        }
          .exec(exchangeDelegationToken("#{subjectToken}", "#{actorToken}", clientId, clientSecret))
          .exec(addGeneratedTokenToNextLevel)
      } else {
        exec { session =>
          val currentLevel = session("currentLevelTokens").as[Vector[String]]
          val state = CursorState(
            subjectCursor = session("subjectCursor").as[Int],
            actorCursor = session("actorCursor").as[Int]
          )
          val (exchangeRequest, nextState) = nextBoundedWidthRequest(currentLevel, state, useDelegationMode = false)
          session
            .set("subjectToken", exchangeRequest.subjectToken)
            .set("subjectCursor", nextState.subjectCursor)
        }
          .exec(exchangeImpersonateToken("#{subjectToken}", clientId, clientSecret))
          .exec(addGeneratedTokenToNextLevel)
      }
    }
  }

  private def buildBranchFactorLevel(
      clientId: String,
      clientSecret: String,
      branchFactor: Int,
      useDelegationMode: Boolean
  ): ChainBuilder = {
    foreach("#{currentLevelTokens}", "subjectToken") {
      repeat(branchFactor) {
        if (useDelegationMode) {
          exec { session =>
            val currentLevel = session("currentLevelTokens").as[Vector[String]]
            val subjectToken = session("subjectToken").as[String]
            val actorCursor = session("actorCursor").as[Int]
            val (exchangeRequest, nextActorCursor) = nextBranchFactorRequest(
              currentLevel,
              subjectToken,
              actorCursor,
              useDelegationMode = true
            )
            session
              .set("actorToken", exchangeRequest.actorToken.get)
              .set("actorCursor", nextActorCursor)
          }
            .exec(exchangeDelegationToken("#{subjectToken}", "#{actorToken}", clientId, clientSecret))
            .exec(addGeneratedTokenToNextLevel)
        } else {
          exec(exchangeImpersonateToken("#{subjectToken}", clientId, clientSecret))
            .exec(addGeneratedTokenToNextLevel)
        }
      }
    }
  }

  def buildTokenTree(
      groupName: String,
      clientId: String,
      clientSecret: String,
      depth: Int,
      branchFactor: Int,
      levelWidth: Int,
      useDelegationMode: Boolean,
      collectRootTokensForRevoke: Boolean
  ): ChainBuilder = {
    val generationChain =
      exec(
        if (useDelegationMode)
          exchangeDelegationToken("#{parentToken}", "#{parentToken}", clientId, clientSecret)
        else
          exchangeImpersonateToken("#{parentToken}", clientId, clientSecret)
      )
        .exec(session => session.set("rootTokenA", session(TOKEN_EXCHANGE_ACCESS_TOKEN_KEY).as[String]))
        .exec(
          if (useDelegationMode)
            exchangeDelegationToken("#{parentToken}", "#{parentToken}", clientId, clientSecret)
          else
            exchangeImpersonateToken("#{parentToken}", clientId, clientSecret)
        )
        .exec(session => session.set("rootTokenB", session(TOKEN_EXCHANGE_ACCESS_TOKEN_KEY).as[String]))
        .exec { session =>
          val roots = Vector(session("rootTokenA").as[String], session("rootTokenB").as[String])
          val withRevokeList =
            if (collectRootTokensForRevoke) {
              val rootTokensToRevoke = session("rootTokensToRevoke").as[Vector[String]] :+
                session("rootTokenA").as[String] :+
                session("rootTokenB").as[String]
              session.set("rootTokensToRevoke", rootTokensToRevoke)
            } else {
              session
            }

          withRevokeList
            .set("currentLevelTokens", roots)
            .set("subjectCursor", 0)
            .set("actorCursor", 0)
            .set("currentDepth", 1)
        }
        .asLongAs(session => session("currentDepth").as[Int] < depth) {
          exec(session => session.set("nextLevelTokens", Vector.empty[String]))
            .exec(
              if (levelWidth > 0)
                buildBoundedWidthLevel(clientId, clientSecret, levelWidth, useDelegationMode)
              else
                buildBranchFactorLevel(clientId, clientSecret, branchFactor, useDelegationMode)
            )
            .exec { session =>
              val nextLevel = session("nextLevelTokens").as[Vector[String]]
              val nextDepth = session("currentDepth").as[Int] + 1
              session
                .set("currentLevelTokens", nextLevel)
                .set("currentDepth", nextDepth)
            }
        }

    if (groupName.nonEmpty) {
      group(groupName) {
        generationChain
      }
    } else {
      generationChain
    }
  }
}

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
import io.gatling.http.Predef._
import io.gravitee.am.performance.commands.OpenFGACalls.TupleKey
import io.gravitee.am.performance.utils.SimulationSettings._

case class EvaluationCase(
  name: String,
  tupleKey: TupleKey,
  expectedAllowed: Boolean,
  context: Option[Map[String, Any]] = None,
  contextualTuples: List[TupleKey] = Nil,
  tags: Set[String] = Set.empty
)

object AuthorizationEvaluation {
  val ComparableTag = "comparable"
  val OpenFgaOnlyTag = "openfga_only"
}

abstract class AuthorizationEvaluation(backend: AuthorizationBackend, tagFilter: Set[String] = Set.empty) extends Simulation {

  private val numberOfTeams = NUMBER_OF_TEAMS.intValue()
  private val numberOfUsers = NUMBER_OF_USERS.intValue()
  private val depthOfTeams = DEPTH_OF_TEAMS.intValue()

  require(numberOfTeams >= 3, "number_of_teams must be >= 3")
  require(numberOfUsers >= numberOfTeams, "number_of_users must be >= number_of_teams")
  require(depthOfTeams >= 2, "depth_of_teams must be >= 2")

  private val httpProtocol = http
    .userAgentHeader(s"Gatling - ${backend.name} Evaluation")
    .disableFollowRedirect

  private val normalizedTagFilter = tagFilter.map(_.trim).filter(_.nonEmpty)

  private def buildEvaluationCases(): List[EvaluationCase] = {
    val parentMap = AuthorizationTopology.teamParentMap()
    val teamIds = parentMap.keys.toSeq
    val teamIdsWithUsers = teamIds.filter(id => AuthorizationTopology.getUserIdInTeam(id).isDefined)
    val deepestTeamId = teamIdsWithUsers.maxBy(id => AuthorizationTopology.teamAncestry(id, parentMap).size)
    val deepAncestors = AuthorizationTopology.teamAncestry(deepestTeamId, parentMap)
    val ancestorTeamId = deepAncestors.lastOption.getOrElse(deepestTeamId)
    val deepTeamUserId = AuthorizationTopology
      .getUserIdInTeam(deepestTeamId)
      .getOrElse(throw new IllegalStateException(s"No user found in team $deepestTeamId"))
    val nonMemberTeamId = teamIdsWithUsers
      .find(teamId => teamId != ancestorTeamId && !AuthorizationTopology.teamAncestry(teamId, parentMap).contains(ancestorTeamId))
      .orElse(teamIdsWithUsers.find(_ != ancestorTeamId))
      .getOrElse(deepestTeamId)
    val nonMemberUserId = AuthorizationTopology
      .getUserIdInTeam(nonMemberTeamId)
      .getOrElse(throw new IllegalStateException(s"No user found in team $nonMemberTeamId"))
    val unrestrictedReaderUserId = AuthorizationTopology.getUserIdWithRole(AuthorizationTopology.RoleAssignmentSharedResourcesUnrestrictedReader)
    val localReaderUserId = AuthorizationTopology.getUserIdWithRole(AuthorizationTopology.RoleAssignmentSharedResourcesLocalReader)
    val workingHoursReaderUserId = AuthorizationTopology.getUserIdWithRole(AuthorizationTopology.RoleAssignmentPersonalResourcesWorkingHoursReader)
    val managerTeamId = parentMap(deepestTeamId)
    val managerChainTeamId = ancestorTeamId
    val ownerUserId = deepTeamUserId
    val managerUserId = AuthorizationTopology
      .getUserIdInTeam(managerTeamId)
      .getOrElse(throw new IllegalStateException(s"No user found in team $managerTeamId"))
    val managerChainUserId = AuthorizationTopology
      .getUserIdInTeam(managerChainTeamId)
      .getOrElse(throw new IllegalStateException(s"No user found in team $managerChainTeamId"))
    val blockedTeamId = 1
    val blockedUserId = AuthorizationTopology
      .getUserIdInTeam(blockedTeamId)
      .getOrElse(throw new IllegalStateException(s"No user found in team $blockedTeamId"))
    val emergencyUserId = AuthorizationTopology.getUserIdNotInTeam(blockedTeamId)
    val sharedResourceId = 1
    val resourceId = 1
    
    // Output important values for manual verification
    println("IDs selected for simulation:")
    println(s"deepestTeamId: $deepestTeamId")
    println(s"deepAncestors: ${deepAncestors.mkString(", ")}")
    println(s"ancestorTeamId: $ancestorTeamId")
    println(s"deepTeamUserId: $deepTeamUserId")
    println(s"nonMemberTeamId: $nonMemberTeamId")
    println(s"nonMemberUserId: $nonMemberUserId")
    println(s"unrestrictedReaderUserId: $unrestrictedReaderUserId")
    println(s"localReaderUserId: $localReaderUserId")
    println(s"workingHoursReaderUserId: $workingHoursReaderUserId")
    println(s"managerTeamId: $managerTeamId")
    println(s"managerChainTeamId: $managerChainTeamId")
    println(s"ownerUserId: $ownerUserId")
    println(s"managerUserId: $managerUserId")
    println(s"managerChainUserId: $managerChainUserId")
    println(s"blockedTeamId: $blockedTeamId")
    println(s"blockedUserId: $blockedUserId")
    println(s"emergencyUserId: $emergencyUserId")
    println(s"sharedResourceId: $sharedResourceId")
    println(s"resourceId: $resourceId")

    List(
      // 1. Inherited permissions via nested teams (deep traversal)
      EvaluationCase(
        name = "inherited-team-access-deep",
        tupleKey = TupleKey(
          s"user:user_${deepTeamUserId}",
          "can_access",
          s"resource:team_${ancestorTeamId}_resource_${resourceId}"
        ),
        expectedAllowed = true,
        tags = Set(AuthorizationEvaluation.ComparableTag)
      ),
      EvaluationCase(
        name = "inherited-team-access-negative",
        tupleKey = TupleKey(
          s"user:user_${nonMemberUserId}",
          "can_access",
          s"resource:team_${ancestorTeamId}_resource_${resourceId}"
        ),
        expectedAllowed = false,
        tags = Set(AuthorizationEvaluation.ComparableTag)
      ),

      // 2. Scoped role binding with context
      EvaluationCase(
        name = "shared-resource-local-reader-in-network",
        tupleKey = TupleKey(
          s"user:user_${localReaderUserId}",
          "can_access",
          s"resource:shared_resource_${sharedResourceId}"
        ),
        expectedAllowed = true,
        context = Some(Map("user_ip" -> "192.168.0.10")),
        tags = Set(AuthorizationEvaluation.OpenFgaOnlyTag) // AM plugins do not handle context
      ),
      EvaluationCase(
        name = "shared-resource-local-reader-outside-network",
        tupleKey = TupleKey(
          s"user:user_${localReaderUserId}",
          "can_access",
          s"resource:shared_resource_${sharedResourceId}"
        ),
        expectedAllowed = false,
        context = Some(Map("user_ip" -> "10.0.0.10")),
        tags = Set(AuthorizationEvaluation.OpenFgaOnlyTag) // AM plugins do not handle context
      ),
      EvaluationCase(
        name = "personal-resource-working-hours-allowed",
        tupleKey = TupleKey(
          s"user:user_${workingHoursReaderUserId}",
          "can_access",
          s"resource:user_${localReaderUserId}_resource_${resourceId}"
        ),
        expectedAllowed = true,
        context = Some(Map("current_time" -> "2026-01-01T10:00:00Z")),
        tags = Set(AuthorizationEvaluation.OpenFgaOnlyTag) // AM plugins do not handle context
      ),
      EvaluationCase(
        name = "personal-resource-working-hours-denied",
        tupleKey = TupleKey(
          s"user:user_${workingHoursReaderUserId}",
          "can_access",
          s"resource:user_${localReaderUserId}_resource_${resourceId}"
        ),
        expectedAllowed = false,
        context = Some(Map("current_time" -> "2026-01-01T07:00:00Z")),
        tags = Set(AuthorizationEvaluation.OpenFgaOnlyTag) // AM plugins do not handle context
      ),

      // 3. Access across resource groups and resources
      EvaluationCase(
        name = "shared-resource-unrestricted-reader",
        tupleKey = TupleKey(
          s"user:user_${unrestrictedReaderUserId}",
          "can_access",
          s"resource:shared_resource_${sharedResourceId}"
        ),
        expectedAllowed = true,
        tags = Set(AuthorizationEvaluation.ComparableTag)
      ),
      EvaluationCase(
        name = "personal-resource-unrelated-user",
        tupleKey = TupleKey(
          s"user:user_${unrestrictedReaderUserId}",
          "can_access",
          s"resource:user_${localReaderUserId}_resource_${resourceId}"
        ),
        expectedAllowed = false,
        tags = Set(AuthorizationEvaluation.ComparableTag)
      ),

      // 4. Delegation via manager relationships
      EvaluationCase(
        name = "manager-access-direct",
        tupleKey = TupleKey(
          s"user:user_${managerUserId}",
          "can_access",
          s"resource:user_${ownerUserId}_resource_${resourceId}"
        ),
        expectedAllowed = true,
        tags = Set(AuthorizationEvaluation.ComparableTag)
      ),
      EvaluationCase(
        name = "manager-access-deep",
        tupleKey = TupleKey(
          s"user:user_${managerChainUserId}",
          "can_access",
          s"resource:user_${ownerUserId}_resource_${resourceId}"
        ),
        expectedAllowed = true,
        tags = Set(AuthorizationEvaluation.ComparableTag)
      ),
      EvaluationCase(
        name = "manager-access-non-manager",
        tupleKey = TupleKey(
          s"user:user_${nonMemberUserId}",
          "can_access",
          s"resource:user_${ownerUserId}_resource_${resourceId}"
        ),
        expectedAllowed = false,
        tags = Set(AuthorizationEvaluation.ComparableTag)
      ),

      // 5. Emergency/blocked short-circuiting
      EvaluationCase(
        name = "emergency-access",
        tupleKey = TupleKey(
          s"user:user_${emergencyUserId}",
          "can_access",
          s"resource:team_${blockedTeamId}_resource_${resourceId}"
        ),
        expectedAllowed = true,
        tags = Set(AuthorizationEvaluation.ComparableTag)
      ),
      EvaluationCase(
        name = "blocked-access",
        tupleKey = TupleKey(
          s"user:user_${blockedUserId}",
          "can_access",
          s"resource:team_${blockedTeamId}_resource_${resourceId}"
        ),
        expectedAllowed = false,
        tags = Set(AuthorizationEvaluation.ComparableTag)
      )
    )
  }

  private val evaluationCases = buildEvaluationCases()

  private val filteredCases = evaluationCases.filter { testCase =>
    normalizedTagFilter.isEmpty || testCase.tags.intersect(normalizedTagFilter).nonEmpty
  }

  require(filteredCases.nonEmpty, "No evaluation cases match evaluation_tags filter")

  private val evaluationFeeder = Iterator.continually(filteredCases).flatten.map { testCase =>
    Map(
      "caseName" -> testCase.name,
      "expectedAllowed" -> testCase.expectedAllowed,
      "checkRequestBody" -> backend.buildRequestBody(testCase)
    )
  }

  private val scn = scenario(s"${backend.name} Authorization Evaluation")
    .exec(backend.authenticate)
    .repeat(REPEAT.intValue()) {
      feed(evaluationFeeder)
        .exec(backend.checkAuthorization("#{caseName}"))
        .exec { session =>
          session("allowed").asOption[Boolean] match {
            case Some(allowed) =>
              val expected = session("expectedAllowed").as[Boolean]
              if (allowed == expected) session else session.markAsFailed
            case None =>
              session.markAsFailed
          }
        }
    }

  setUp(
    scn.inject(
      rampConcurrentUsers(1).to(AGENTS.intValue()).during(60),
      constantConcurrentUsers(AGENTS.intValue()).during(INJECTION_DURATION.seconds),
      rampConcurrentUsers(AGENTS.intValue()).to(1).during(60)
    )
  ).protocols(httpProtocol)
}

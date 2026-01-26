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

abstract class AuthorizationEvaluation(backend: AuthorizationBackend) extends Simulation {
  private val ComparableTag = "comparable"
  private val OpenFgaOnlyTag = "openfga_only"

  private val httpProtocol = http
    .userAgentHeader(s"Gatling - ${backend.name} Evaluation")
    .disableFollowRedirect

  private val tagFilter = System
    .getProperty("evaluation_tags", "")
    .split(",")
    .map(_.trim)
    .filter(_.nonEmpty)
    .toSet

  private def buildEvaluationCases(): List[EvaluationCase] = {
    val numberOfTeams = NUMBER_OF_TEAMS.intValue()
    val parentMap = AuthorizationTopology.teamParentMap()
    val teamIds = parentMap.keys.toSeq
    val teamIdsWithUsers = teamIds.filter(id => AuthorizationTopology.getUserIdInTeam(id).isDefined)
    val deepestTeamId = if (teamIdsWithUsers.nonEmpty) {
      teamIdsWithUsers.maxBy(id => AuthorizationTopology.teamAncestry(id, parentMap).size)
    } else {
      1
    }
    val deepAncestors = AuthorizationTopology.teamAncestry(deepestTeamId, parentMap)
    val ancestorTeamId = deepAncestors.lastOption.getOrElse(deepestTeamId)
    val deepTeamUserId = AuthorizationTopology.getUserIdInTeam(deepestTeamId).getOrElse(1)
    val nonMemberTeamId = teamIdsWithUsers
      .find(teamId => teamId != ancestorTeamId && !AuthorizationTopology.teamAncestry(teamId, parentMap).contains(ancestorTeamId))
      .orElse(teamIdsWithUsers.find(_ != ancestorTeamId))
      .getOrElse(deepestTeamId)
    val nonMemberUserId = AuthorizationTopology.getUserIdInTeam(nonMemberTeamId).getOrElse(1)
    val unrestrictedReaderUserId = AuthorizationTopology.getUserIdWithRole(AuthorizationTopology.RoleAssignmentSharedResourcesUnrestrictedReader)
    val localReaderUserId = AuthorizationTopology.getUserIdWithRole(AuthorizationTopology.RoleAssignmentSharedResourcesLocalReader)
    val workingHoursReaderUserId = AuthorizationTopology.getUserIdWithRole(AuthorizationTopology.RoleAssignmentPersonalResourcesWorkingHoursReader)
    val managerUserId = unrestrictedReaderUserId
    val ownerUserId = workingHoursReaderUserId
    val blockedTeamId = Math.floorMod(deepTeamUserId, numberOfTeams)
    val blockedUserId = deepTeamUserId
    val emergencyUserId = AuthorizationTopology.getUserIdNotInTeam(blockedTeamId)
    val sharedResourceId = 1
    val resourceId = 1

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
        tags = Set(ComparableTag)
      ),
      EvaluationCase(
        name = "inherited-team-access-negative",
        tupleKey = TupleKey(
          s"user:user_${nonMemberUserId}",
          "can_access",
          s"resource:team_${ancestorTeamId}_resource_${resourceId}"
        ),
        expectedAllowed = false,
        tags = Set(ComparableTag)
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
        tags = Set(OpenFgaOnlyTag) // AM plugins do not handle context
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
        tags = Set(OpenFgaOnlyTag) // AM plugins do not handle context
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
        tags = Set(OpenFgaOnlyTag) // AM plugins do not handle context
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
        tags = Set(OpenFgaOnlyTag) // AM plugins do not handle context
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
        tags = Set(ComparableTag)
      ),
      EvaluationCase(
        name = "personal-resource-unrelated-user",
        tupleKey = TupleKey(
          s"user:user_${unrestrictedReaderUserId}",
          "can_access",
          s"resource:user_${localReaderUserId}_resource_${resourceId}"
        ),
        expectedAllowed = false,
        tags = Set(ComparableTag)
      ),

      // 4. Delegation via manager relationships (contextual tuples)
      EvaluationCase(
        name = "manager-access-with-contextual-tuple",
        tupleKey = TupleKey(
          s"user:user_${managerUserId}",
          "can_access",
          s"resource:user_${ownerUserId}_resource_${resourceId}"
        ),
        expectedAllowed = true,
        contextualTuples = List(
          TupleKey(
            s"user:user_${managerUserId}",
            "manager",
            s"user:user_${ownerUserId}"
          )
        ),
        tags = Set(OpenFgaOnlyTag) // AM plugins do not handle contextual tuples
      ),
      EvaluationCase(
        name = "manager-access-without-contextual-tuple",
        tupleKey = TupleKey(
          s"user:user_${managerUserId}",
          "can_access",
          s"resource:user_${ownerUserId}_resource_${resourceId}"
        ),
        expectedAllowed = false,
        tags = Set(ComparableTag)
      ),

      // 5. Emergency/blocked short-circuiting (contextual tuples)
      EvaluationCase(
        name = "emergency-access-contextual-tuple",
        tupleKey = TupleKey(
          s"user:user_${emergencyUserId}",
          "can_access",
          s"resource:team_${blockedTeamId}_resource_${resourceId}"
        ),
        expectedAllowed = true,
        contextualTuples = List(
          TupleKey(
            s"user:user_${emergencyUserId}",
            "emergency_access",
            s"resource_group:team_${blockedTeamId}_resources"
          )
        ),
        tags = Set(OpenFgaOnlyTag) // AM plugins do not handle contextual tuples
      ),
      EvaluationCase(
        name = "blocked-access-contextual-tuple",
        tupleKey = TupleKey(
          s"user:user_${blockedUserId}",
          "can_access",
          s"resource:team_${blockedTeamId}_resource_${resourceId}"
        ),
        expectedAllowed = false,
        contextualTuples = List(
          TupleKey(
            s"user:user_${blockedUserId}",
            "blocked",
            s"resource_group:team_${blockedTeamId}_resources"
          )
        ),
        tags = Set(OpenFgaOnlyTag) // AM plugins do not handle contextual tuples
      )
    )
  }

  private val evaluationCases = buildEvaluationCases()

  private val filteredCases = evaluationCases.filter { testCase =>
    tagFilter.isEmpty || testCase.tags.intersect(tagFilter).nonEmpty
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

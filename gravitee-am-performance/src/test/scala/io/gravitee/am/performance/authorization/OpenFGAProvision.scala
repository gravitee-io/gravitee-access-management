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

import io.gatling.commons.validation.Success
import io.gatling.core.Predef._
import io.gravitee.am.performance.commands.OpenFGACalls._
import io.gravitee.am.performance.commands.ManagementAPICalls
import io.gravitee.am.performance.utils.SimulationSettings._
import io.gravitee.am.performance.utils.TreeGenerator

/**
 * The purpose of this simulation is to validate an OpenFGA instance
 * and provision it with relationship tuples to describe a large company organization.
 *
 * Provisioning runs in three sequential phases:
 *   Phase 1 – a single virtual user validates the store/model and writes static role tuples.
 *   Phase 2 – `agents` virtual users provision data in parallel. Each user receives a
 *              round-robin partition of shared resources, teams (hierarchy + manager
 *              relations + team resources) and users (memberships + user resources).
 *   Phase 3 – a single virtual user writes emergency/blocked tuples and, optionally,
 *              configures the AM domain authorization engine.
 *
 * Possible arguments:
 * - fga_api_url: base URL of the OpenFGA REST API (default: http://localhost:8080)
 * - fga_store_id: OpenFGA Store identifier
 * - fga_authorization_model_id: OpenFGA authorization model identifier
 * - agents: number of parallel virtual users for the provisioning phase (default: 10)
 * - number_of_users: how many users the simulation will create tuples for
 * - number_of_teams: how many teams the simulation will create tuples for
 * - depth_of_teams: maximum depth of teams in tree hierarchy
 * - number_of_resources_per_user: how many resources per user the simulation will create tuples for
 * - number_of_resources_per_team: how many resources per team the simulation will create tuples for
 * - number_of_shared_resources: how many global resources the simulation will create tuples for
 * - configure_domain: when true, configure the AM domain with the OpenFGA authorization engine (default: false)
 * - mng_url: base URL of the AM Management API (required when configure_domain is true)
 * - mng_user: Management API admin username (required when configure_domain is true)
 * - mng_password: Management API admin password (required when configure_domain is true)
 * - domain: name of the AM domain to configure (required when configure_domain is true)
 */
class OpenFGAProvision extends Simulation {

  require(FGA_STORE_ID.nonEmpty, "fga_store_id system property must be set")
  require(FGA_AUTHORIZATION_MODEL_ID.nonEmpty, "fga_authorization_model_id system property must be set")
  require(NUMBER_OF_TEAMS.intValue() >= 3, "number_of_teams must be >= 3")
  require(NUMBER_OF_USERS.intValue() >= NUMBER_OF_TEAMS.intValue(), "number_of_users must be >= number_of_teams")
  require(DEPTH_OF_TEAMS.intValue() >= 2, "depth_of_teams must be >= 2")

  private val teamParentMap = AuthorizationTopology.teamParentMap()

  private val numAgents    = AGENTS.intValue()
  private val numTeams     = NUMBER_OF_TEAMS.intValue()
  private val numUsers     = NUMBER_OF_USERS.intValue()
  private val numSharedRes = NUMBER_OF_SHARED_RESOURCES.intValue()
  private val resPerUser   = NUMBER_OF_RESOURCES_PER_USER.intValue()
  private val resPerTeam   = NUMBER_OF_RESOURCES_PER_TEAM.intValue()

  // -------------------------------------------------------------------------
  // Tuple-building helpers (read from session, unchanged from original)
  // -------------------------------------------------------------------------

  private def buildRoleTuples(): Session => Session = { session =>
    val tuples = List(
      // Role availability
      Tuple("user:*", "can_access_resource_group", "role:unrestricted_reader"),
      Tuple("user:*", "can_access_resource_group", "role:local_reader",
        Some(Condition("in_company_network", Map("cidr" -> "192.168.0.0/24")))
      ),
      Tuple("user:*", "can_access_resource_group", "role:working_hours_reader",
        Some(Condition("working_hours_access", Map("start_hour" -> 8, "end_hour" -> 17)))
      ),
      // Role assignments
      Tuple("role:unrestricted_reader", "role", AuthorizationTopology.RoleAssignmentSharedResourcesUnrestrictedReader),
      Tuple("role:local_reader", "role", AuthorizationTopology.RoleAssignmentSharedResourcesLocalReader),
      Tuple("role:working_hours_reader", "role", AuthorizationTopology.RoleAssignmentPersonalResourcesWorkingHoursReader),
      // Apply role assignments to resource groups
      Tuple(AuthorizationTopology.RoleAssignmentSharedResourcesUnrestrictedReader, "role_assignment", "resource_group:shared_resources"),
      Tuple(AuthorizationTopology.RoleAssignmentSharedResourcesLocalReader, "role_assignment", "resource_group:shared_resources"),
      Tuple(AuthorizationTopology.RoleAssignmentPersonalResourcesWorkingHoursReader, "role_assignment", "resource_group:personal_resources")
    )

    session.set("tupleRequestBody", tuplesToJsonRequestBody(tuples))
  }

  private def buildSharedResourceTuple(): Session => Session = { session =>
    val resourceId = session("resourceId").as[Int]
    val tuple = Tuple(s"resource_group:shared_resources", "group", s"resource:shared_resource_${resourceId}")
    session.set("tupleRequestBody", tuplesToJsonRequestBody(List(tuple)))
  }

  private def buildTeamTuples(): Session => Session = { session =>
    val teamId   = session("teamId").as[Int]
    val parentId = session("parentId").as[Int]
    val tuples = List(
      Tuple(s"team:team_${teamId}", "child", s"team:team_${parentId}"),
      Tuple(s"team:team_${teamId}#all_members", "owner", s"resource_group:team_${teamId}_resources")
    )
    session.set("tupleRequestBody", tuplesToJsonRequestBody(tuples))
  }

  private def buildTeamResourceTuple(): Session => Session = { session =>
    val teamId     = session("teamId").as[Int]
    val resourceId = session("resourceId").as[Int]
    val tuple = Tuple(s"resource_group:team_${teamId}_resources", "group", s"resource:team_${teamId}_resource_${resourceId}")
    session.set("tupleRequestBody", tuplesToJsonRequestBody(List(tuple)))
  }

  private def buildUserMembershipTuples(): Session => Session = { session =>
    val userId         = session("userId").as[Int]
    val teamId         = session("teamId").as[Int]
    val roleAssignment = session("roleAssignment").as[String]
    val tuples = List(
      Tuple(s"user:user_${userId}", "member", s"team:team_${teamId}"),
      Tuple(s"user:user_${userId}", "assignee", roleAssignment)
    )
    session.set("tupleRequestBody", tuplesToJsonRequestBody(tuples))
  }

  private def buildManagerRelationTuples(): Session => Session = { session =>
    val managerId = session("managerId").as[Int]
    val reportId  = session("reportId").as[Int]
    val tuple = Tuple(s"user:user_${managerId}", "manager", s"user:user_${reportId}")
    session.set("tupleRequestBody", tuplesToJsonRequestBody(List(tuple)))
  }

  private def buildEmergencyBlockedTuples(): Session => Session = { session =>
    val teamId = 1
    val blockedUserId = AuthorizationTopology
      .getUserIdInTeam(teamId)
      .getOrElse(throw new IllegalStateException(s"No user found in team $teamId"))
    val emergencyUserId = AuthorizationTopology.getUserIdNotInTeam(teamId)
    val tuples = List(
      Tuple(s"user:user_${blockedUserId}", "blocked", s"resource_group:team_${teamId}_resources"),
      Tuple(s"user:user_${emergencyUserId}", "emergency_access", s"resource_group:team_${teamId}_resources")
    )
    session.set("tupleRequestBody", tuplesToJsonRequestBody(tuples))
  }

  private def buildUserResourceTuples(): Session => Session = { session =>
    val userId     = session("userId").as[Int]
    val resourceId = session("resourceId").as[Int]
    val tuples = List(
      Tuple(s"user:user_${userId}", "owner", s"resource:user_${userId}_resource_${resourceId}"),
      Tuple("resource_group:personal_resources", "group", s"resource:user_${userId}_resource_${resourceId}")
    )
    session.set("tupleRequestBody", tuplesToJsonRequestBody(tuples))
  }

  // -------------------------------------------------------------------------
  // Phase 1 – Init (single virtual user)
  //   Validate store + authorization model, then write the static role tuples.
  // -------------------------------------------------------------------------

  private val scnInit = scenario("Phase 1: Init")
    .exec(getStore)
    .doIf(_.isFailed) {
      exec(session => {
        println(s"[ERROR] Unable to get OpenFGA store - exiting")
        session
      })
    }
    .exitHereIfFailed
    .exec(getAuthorizationModel)
    .doIf(_.isFailed) {
      exec(session => {
        println(s"[ERROR] Unable to get OpenFGA authorization model - exiting")
        session
      })
    }
    .exitHereIfFailed
    .exec(buildRoleTuples())
    .exec(writeTuples("Add Roles"))

  // -------------------------------------------------------------------------
  // Phase 2 – Parallel provisioning (numAgents virtual users)
  //
  //   Each virtual user (index k, 1-based) handles the round-robin partition:
  //     shared resources : k, k+N, k+2N, ...  (1..numSharedRes)
  //     teams            : k, k+N, k+2N, ...  (1 until numTeams)
  //     users            : k, k+N, k+2N, ...  (1..numUsers)
  //
  //   For each team in its partition the VU also writes the manager relation
  //   and all team resources.  For each user it also writes all user resources.
  // -------------------------------------------------------------------------

  private val scnProvision = scenario("Phase 2: Parallel Provisioning")
    // Compute this VU's partition and store it in the session.
    .exec { session =>
      val vuIndex = session.userId.toInt  // 1-based, 1..numAgents

      val mySharedResourceIds: Seq[Int] = (vuIndex to numSharedRes   by numAgents).toList
      val myTeamIds: Seq[Int]           = (vuIndex until numTeams    by numAgents).toList
      val myUserIds: Seq[Int]           = (vuIndex to numUsers       by numAgents).toList

      session
        .set("mySharedResourceIds", mySharedResourceIds)
        .set("myTeamIds", myTeamIds)
        .set("myUserIds", myUserIds)
    }
    // --- Shared resources ---
    .foreach("#{mySharedResourceIds}", "resourceId") {
      exec(buildSharedResourceTuple())
        .exec(writeTuples("Add Shared Resource"))
    }
    // --- Team hierarchy, manager relations, and team resources ---
    .foreach("#{myTeamIds}", "teamId") {
      exec { session =>
        val teamId    = session("teamId").as[Int]
        val parentId  = teamParentMap(teamId)
        val reportId  = AuthorizationTopology
          .getUserIdInTeam(teamId)
          .getOrElse(throw new IllegalStateException(s"No user found in team $teamId"))
        val managerId = AuthorizationTopology
          .getUserIdInTeam(parentId)
          .getOrElse(throw new IllegalStateException(s"No user found in team $parentId"))
        session
          .set("parentId",  parentId)
          .set("managerId", managerId)
          .set("reportId",  reportId)
      }
        .exec(buildTeamTuples())
        .exec(writeTuples("Add Team"))
        .exec(buildManagerRelationTuples())
        .exec(writeTuples("Add Manager Relation"))
        .repeat(resPerTeam, "resourceIdx") {
          exec { session =>
            session.set("resourceId", session("resourceIdx").as[Int] + 1)
          }
            .exec(buildTeamResourceTuple())
            .exec(writeTuples("Add Team Resource"))
        }
    }
    // --- User memberships and user resources ---
    .foreach("#{myUserIds}", "userId") {
      exec { session =>
        val userId = session("userId").as[Int]
        session
          .set("teamId",         AuthorizationTopology.getTeamIdForUser(userId))
          .set("roleAssignment", AuthorizationTopology.getUserRoleAssignment(userId))
      }
        .exec(buildUserMembershipTuples())
        .exec(writeTuples("Add User Membership"))
        .repeat(resPerUser, "resourceIdx") {
          exec { session =>
            session.set("resourceId", session("resourceIdx").as[Int] + 1)
          }
            .exec(buildUserResourceTuples())
            .exec(writeTuples("Add User Resource"))
        }
    }

  // -------------------------------------------------------------------------
  // Phase 3 – Finalize (single virtual user)
  //   Write emergency/blocked tuples, then optionally configure the AM domain.
  // -------------------------------------------------------------------------

  private val scnFinalize = scenario("Phase 3: Finalize")
    .exec(buildEmergencyBlockedTuples())
    .exec(writeTuples("Add Emergency/Blocked"))
    .doIf(_ => Success(CONFIGURE_DOMAIN_AUTH_ENGINE)) {
      exec(ManagementAPICalls.login)
        .doIf(_.isFailed) {
          exec(session => {
            println(s"[ERROR] Unable to login to Management API - exiting")
            session
          })
        }
        .exitHereIfFailed
        .exec(ManagementAPICalls.retrieveDomainId(DOMAIN_NAME))
        .doIf(_.isFailed) {
          exec(session => {
            println(s"[ERROR] Unable to retrieve domain ID for domain '$DOMAIN_NAME' - exiting")
            session
          })
        }
        .exitHereIfFailed
        .exec(ManagementAPICalls.listAuthorizationEngines)
        .doIf(_.isFailed) {
          exec(session => {
            println(s"[ERROR] Unable to list authorization engines - exiting")
            session
          })
        }
        .exitHereIfFailed
        .exec(ManagementAPICalls.ensureOpenFGAAuthorizationEngine)
        .doIf(_.isFailed) {
          exec(session => {
            val error = session("error").asOption[String].getOrElse("Unknown error")
            println(s"[ERROR] Authorization engine validation failed: $error")
            session
          })
        }
        .exitHereIfFailed
        .doIf(session => Success(session("needsAuthEngineCreation").asOption[Boolean].getOrElse(false))) {
          exec(ManagementAPICalls.createAuthorizationEngine)
            .doIf(_.isFailed) {
              exec(session => {
                println(s"[ERROR] Unable to create authorization engine - exiting")
                session
              })
            }
            .exitHereIfFailed
        }
    }

  // -------------------------------------------------------------------------
  // Injection plan – three sequential phases
  // -------------------------------------------------------------------------

  setUp(
    scnInit.inject(atOnceUsers(1))
      .andThen(scnProvision.inject(atOnceUsers(numAgents)))
      .andThen(scnFinalize.inject(atOnceUsers(1)))
  )
}

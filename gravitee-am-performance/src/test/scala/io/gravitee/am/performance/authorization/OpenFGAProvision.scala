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
 * Possible arguments:
 * - fga_api_url: base URL of the OpenFGA REST API (default: http://localhost:8080)
 * - fga_store_id: OpenFGA Store identifier
 * - fga_authorization_model_id: OpenFGA authorization model identifier
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

  /**
   * Create a feeder for shared resources
   */
  private def sharedResourceFeeder() = {
    val numResources = NUMBER_OF_SHARED_RESOURCES.intValue()

    (1 to numResources).map { resourceId =>
      Map("resourceId" -> resourceId)
    }
  }

  /**
   * Create a feeder for team hierarchy and resource group ownership
   */
  private def teamFeeder() = {
    val numTeams = NUMBER_OF_TEAMS.intValue()

    // Create feeder for teams (excluding root team)
    (1 until numTeams).map { teamId =>
      val parentId = teamParentMap(teamId)
      Map(
        "teamId" -> teamId,
        "parentId" -> parentId
      )
    }
  }

  /**
   * Create a feeder for team resources
   */
  private def teamResourceFeeder() = {
    val numTeams = NUMBER_OF_TEAMS.intValue()
    val numResources = NUMBER_OF_RESOURCES_PER_TEAM.intValue()
    
    // Generate team-resource combinations (excluding root team)
    (1 until numTeams).flatMap { teamId =>
      (1 to numResources).map { resourceId =>
        Map(
          "teamId" -> teamId,
          "resourceId" -> resourceId
        )
      }
    }
  }

  /**
   * Create a feeder for user team membership
   */
  private def userMembershipFeeder() = {
    AuthorizationTopology.userIdsIterator().map { userId =>
      Map(
        "userId" -> userId,
        "teamId" -> AuthorizationTopology.getTeamIdForUser(userId),
        "roleAssignment" -> AuthorizationTopology.getUserRoleAssignment(userId)
      )
    }
  }

  /**
   * Create a feeder for manager relationships using team-parent scheme
   */
  private def managerRelationFeeder() = {
    val numTeams = NUMBER_OF_TEAMS.intValue()

    (1 until numTeams).map { teamId =>
      val parentId = teamParentMap(teamId)
      val reportId = AuthorizationTopology
        .getUserIdInTeam(teamId)
        .getOrElse(throw new IllegalStateException(s"No user found in team $teamId"))
      val managerId = AuthorizationTopology
        .getUserIdInTeam(parentId)
        .getOrElse(throw new IllegalStateException(s"No user found in team $parentId"))

      Map(
        "managerId" -> managerId,
        "reportId" -> reportId
      )
    }
  }

  /**
   * Create a feeder for user resources
   */
  private def userResourceFeeder() = {
    val numResources = NUMBER_OF_RESOURCES_PER_USER.intValue()
    AuthorizationTopology.userIdsIterator().flatMap { userId =>
      (1 to numResources).map { resourceId =>
        Map(
          "userId" -> userId,
          "resourceId" -> resourceId
        )
      }
    }
  }

  // Create feeders (as iterators)
  private val sharedResourceFeederIterator = sharedResourceFeeder().iterator
  private val teamFeederIterator = teamFeeder().iterator
  private val teamResourceFeederIterator = teamResourceFeeder().iterator
  private val userMembershipFeederIterator = userMembershipFeeder()
  private val managerRelationFeederData = managerRelationFeeder()
  private val managerRelationFeederIterator = managerRelationFeederData.iterator
  private val userResourceFeederIterator = userResourceFeeder()
  
  // Calculate counts for repeat loops
  private val numSharedResources = NUMBER_OF_SHARED_RESOURCES.intValue()
  private val numNonRootTeams = NUMBER_OF_TEAMS.intValue() - 1
  private val numTeamResources = numNonRootTeams * NUMBER_OF_RESOURCES_PER_TEAM.intValue()
  private val numUsers = NUMBER_OF_USERS.intValue()
  private val numManagerRelations = managerRelationFeederData.size
  private val numUserResources = numUsers * NUMBER_OF_RESOURCES_PER_USER.intValue()

  // Role assignments
  /**
   * Build role tuples - these are statically defined
   */
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

  /**
   * Build shared resource tuple based on session data
   */
  private def buildSharedResourceTuple(): Session => Session = { session =>
    val resourceId = session("resourceId").as[Int]

    val tuple = Tuple(s"resource_group:shared_resources", "group", s"resource:shared_resource_${resourceId}")

    session.set("tupleRequestBody", tuplesToJsonRequestBody(List(tuple)))
  }

  /**
   * Build team tuples (hierarchy + resource group ownership) based on session data
   */
  private def buildTeamTuples(): Session => Session = { session =>
    val teamId = session("teamId").as[Int]
    val parentId = session("parentId").as[Int]
    
    val tuples = List(
      Tuple(s"team:team_${teamId}", "child", s"team:team_${parentId}"),
      Tuple(s"team:team_${teamId}#all_members", "owner", s"resource_group:team_${teamId}_resources")
    )
    
    session.set("tupleRequestBody", tuplesToJsonRequestBody(tuples))
  }

  /**
   * Build team resource tuple based on session data
   */
  private def buildTeamResourceTuple(): Session => Session = { session =>
    val teamId = session("teamId").as[Int]
    val resourceId = session("resourceId").as[Int]

    val tuple = Tuple(s"resource_group:team_${teamId}_resources", "group", s"resource:team_${teamId}_resource_${resourceId}")

    session.set("tupleRequestBody", tuplesToJsonRequestBody(List(tuple)))
  }

  /**
   * Build user membership tuples (team, role) based on session data
   */
  private def buildUserMembershipTuples(): Session => Session = { session =>
    val userId = session("userId").as[Int]
    val teamId = session("teamId").as[Int]
    val roleAssignment = session("roleAssignment").as[String]
    
    val tuples = List(
      Tuple(s"user:user_${userId}", "member", s"team:team_${teamId}"),
      Tuple(s"user:user_${userId}", "assignee", roleAssignment)
    )
    
    session.set("tupleRequestBody", tuplesToJsonRequestBody(tuples))
  }

  /**
   * Build manager relationship tuples based on session data
   */
  private def buildManagerRelationTuples(): Session => Session = { session =>
    val managerId = session("managerId").as[Int]
    val reportId = session("reportId").as[Int]

    val tuple = Tuple(s"user:user_${managerId}", "manager", s"user:user_${reportId}")

    session.set("tupleRequestBody", tuplesToJsonRequestBody(List(tuple)))
  }

  /**
   * Build emergency and blocked tuples for a deterministic team
   */
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

  /**
   * Build user resource tuples (ownership + resource grouping) based on session data
   */
  private def buildUserResourceTuples(): Session => Session = { session =>
    val userId = session("userId").as[Int]
    val resourceId = session("resourceId").as[Int]
    
    val tuples = List(
      Tuple(s"user:user_${userId}", "owner", s"resource:user_${userId}_resource_${resourceId}"),
      Tuple("resource_group:personal_resources", "group", s"resource:user_${userId}_resource_${resourceId}")
    )
    
    session.set("tupleRequestBody", tuplesToJsonRequestBody(tuples))
  }

  private val scn = scenario("OpenFGA Provisioning")
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
    .repeat(numSharedResources) {
      feed(sharedResourceFeederIterator)
        .exec(buildSharedResourceTuple())
        .exec(writeTuples("Add Shared Resource"))
    }
    .repeat(numNonRootTeams) {
      feed(teamFeederIterator)
        .exec(buildTeamTuples())
        .exec(writeTuples("Add Team"))
    }
    .repeat(numTeamResources) {
      feed(teamResourceFeederIterator)
        .exec(buildTeamResourceTuple())
        .exec(writeTuples("Add Team Resource"))
    }
    .repeat(numUsers) {
      feed(userMembershipFeederIterator)
        .exec(buildUserMembershipTuples())
        .exec(writeTuples("Add User Membership"))
    }
    .repeat(numManagerRelations) {
      feed(managerRelationFeederIterator)
        .exec(buildManagerRelationTuples())
        .exec(writeTuples("Add Manager Relation"))
    }
    .exec(buildEmergencyBlockedTuples())
    .exec(writeTuples("Add Emergency/Blocked"))
    .repeat(numUserResources) {
      feed(userResourceFeederIterator)
        .exec(buildUserResourceTuples())
        .exec(writeTuples("Add User Resource"))
    }
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

  setUp(
    scn.inject(atOnceUsers(1))
  )
}

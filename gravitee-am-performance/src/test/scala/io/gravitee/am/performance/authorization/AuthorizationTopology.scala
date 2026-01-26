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

import io.gravitee.am.performance.utils.SimulationSettings._
import io.gravitee.am.performance.utils.TreeGenerator

object AuthorizationTopology {
  val RoleAssignmentSharedResourcesUnrestrictedReader = "role_assignment:shared_resources_unrestricted_reader"
  val RoleAssignmentSharedResourcesLocalReader = "role_assignment:shared_resources_local_reader"
  val RoleAssignmentPersonalResourcesWorkingHoursReader = "role_assignment:personal_resources_working_hours_reader"

  val roleAssignments: List[String] = List(
    RoleAssignmentSharedResourcesUnrestrictedReader,
    RoleAssignmentSharedResourcesLocalReader,
    RoleAssignmentPersonalResourcesWorkingHoursReader
  )

  private val roleIndexByName: Map[String, Int] =
    roleAssignments.zipWithIndex.toMap

  def userIdsIterator(): Iterator[Int] =
    Iterator.from(1).take(NUMBER_OF_USERS.intValue())

  def getTeamIdForUser(userId: Int): Int = {
    userId % NUMBER_OF_TEAMS.intValue()
  }

  def getUserIdInTeam(teamId: Int): Option[Int] = {
    val numberOfTeams = NUMBER_OF_TEAMS.intValue()
    userIdsIterator().find(userId => Math.floorMod(userId, numberOfTeams) == teamId)
  }

  def getUserIdNotInTeam(teamId: Int): Int = {
    val numberOfTeams = NUMBER_OF_TEAMS.intValue()
    if (numberOfTeams <= 1) 1
    else {
      userIdsIterator()
        .find(userId => Math.floorMod(userId, numberOfTeams) != teamId)
        .getOrElse(1)
    }
  }

  def getUserRoleAssignment(userId: Int): String =
    roleAssignments(userId % roleAssignments.length)

  def getUserIdWithRole(role: String): Int = {
    val index = roleIndexByName.getOrElse(role, 0)
    val candidate = if (index == 0) roleAssignments.length else index // avoid nonexistent user_0
    require(NUMBER_OF_USERS.intValue() >= candidate, s"Not enough users (${NUMBER_OF_USERS.intValue()}) for role assignment index $candidate")
    candidate
  }

  def teamParentMap(): Map[Int, Int] =
    TreeGenerator.generateTreeEdges(NUMBER_OF_TEAMS.intValue(), DEPTH_OF_TEAMS.intValue()).toMap

  def teamAncestry(teamId: Int, parentMap: Map[Int, Int]): List[Int] = {
    val ancestors = scala.collection.mutable.ListBuffer.empty[Int]
    var current = teamId
    while (parentMap.contains(current) && parentMap(current) != 0) {
      val parent = parentMap(current)
      ancestors += parent
      current = parent
    }
    ancestors.toList
  }
}

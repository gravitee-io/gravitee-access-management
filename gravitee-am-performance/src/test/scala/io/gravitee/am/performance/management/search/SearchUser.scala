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
package io.gravitee.am.performance.management.search

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gravitee.am.performance.commands.ManagementAPICalls.{login, retrieveDomainId}
import io.gravitee.am.performance.utils.SimulationSettings._

import java.net.URLEncoder

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */

/**
 * This simulation perform search in domain users
 * Possible arguments:
 * - mng_url: base URL of the Management REST API (default: http://localhost:8093)
 * - mng_user: username to request an access token to the Management REST API (default: admin)
 * - mng_password: password to request an access token to the Management REST API (default: adminadmin)
 * - domain: the domain name targeted by the simulation (default: gatling-domain)
 * - agents: number of agents for the simulation (default: 10)
 * - field: comma seperated fields such as (email,username,firstName)
 * - operator: SCIM 2.0 supported operator such as "eq", "ne", "co" etc.
 * - condition: logical condition such as "and" and "or"
 */
class SearchUser extends Simulation {

  def encode(query: String) = URLEncoder.encode(query, "UTF-8")
    .replace("+", "%20")
    .replace("%22", "\"")

  def getSearchString(index: Int) = {
    FIELD.split(",").map(field => field match {
      case "email" => s"""${field} ${OPERATOR} "${USER_PREFIX}${index}@acme.fr" """
      case "firstname" => s"""${field} ${OPERATOR} "first${index}" """
      case "lastname" => s"""${field} ${OPERATOR} "last${index}" """
      case "username" => s"""${field} ${OPERATOR} "user${index}" """
      case _ => s"""${field} ${OPERATOR} "${USER_PREFIX}" """
    }).mkString(s""" ${CONDITION} """)
  }

  def searchFeeder = {
    val iterator = Iterator.from(MIN_USER_INDEX, 1)
    iterator
      .map(index => {
        val searchString = encode(getSearchString(index))
        Map(
          "searchQuery" -> searchString,
          "index" -> index)
      })
  }

  def search = http("Perform Search")
    .get(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/#{domainId}/users?filter=" + "#{searchQuery}" + "&page=0&size=25")
    .header("Authorization", "Bearer #{auth-token}")
    .check(status.is(200))
    .check(jsonPath("$.totalCount").ofType[Int].gt(0))


  val httpProtocol = http
    .userAgentHeader("Gatling - Search Users")
    .disableFollowRedirect

  val scn = scenario("Search Users")
    .exec(login)
    .exec(retrieveDomainId(DOMAIN_NAME))
    .feed(searchFeeder)
    .doWhile(session => session("index").as[Int] < MAX_USER_INDEX)(
      exec(search)
        .feed(searchFeeder)
    )

  setUp(scn.inject(atOnceUsers(AGENTS)).protocols(httpProtocol))
}

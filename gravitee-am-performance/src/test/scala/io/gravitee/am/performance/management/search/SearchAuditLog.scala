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

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */

/**
 * This simulation searches audit log under domain context
 * Possible arguments:
 * - mng_url: base URL of the Management REST API (default: http://localhost:8093)
 * - mng_user: username to request an access token to the Management REST API (default: admin)
 * - mng_password: password to request an access token to the Management REST API (default: adminadmin)
 * - domain: the domain name targeted by the simulation (default: gatling-domain)
 * - agents: number of agents for the simulation (default: 10)
 * - repeat: number of searches each agent perform (default: 10)
 * - start: beginning of the search range in "dd/MM/yyyy HH:mm:ss" format such as "22/10/1998 13:12:11" (default: last 24 hours)
 * - end: ending of the search range in dd/MM/yyyy HH:mm:ss format such as "22/10/2022 09:12:13" (default: current date time)
 * - event: comma seperated list of supported events such as (USER_UPDATED,USER_DELETED)
 */
class SearchAuditLog extends Simulation {
  val twentyFourHours = 24 * 3600 * 1000
  val currentTime = System.currentTimeMillis()

  def isCustomSearch() = {
    EVENT.nonEmpty
  }

  def dateToMilliSeconds(date: String) = {
    //need to replace first and last double quote
    val dateString = date.replace("\"", "")
    val format = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    format.parse(dateString).getTime().toString
  }

  def createSearchFeeder() = {
    val status: String = "SUCCESS"
    val user: String = USER
    val from: String = if (START.nonEmpty) dateToMilliSeconds(START) else (currentTime - twentyFourHours).toString
    val to: String = if (END.nonEmpty) dateToMilliSeconds(END) else currentTime.toString

    EVENT.split(",")
      .map(event => Map("searchQuery" -> s"type=${event}&status=${status}&user=${user}&from=${from}&to=${to}"))
      .toIndexedSeq
  }

  val fixedFeeder =
    IndexedSeq(
      Map("searchQuery" -> searchString("USER_LOGIN")),
      Map("searchQuery" -> searchString("USER_LOGOUT"))

    ).circular

  def searchString(event: String) = {
    val from: String = (currentTime - twentyFourHours).toString
    val to: String = currentTime.toString

    s"type=${event}&status=SUCCESS&from=${from}&to=${to}"
  }

  def getFeeder = {
    if (isCustomSearch()) createSearchFeeder().circular else fixedFeeder
  }

  val feeder = getFeeder()

  def search = http("Perform Search")
    .get(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/#{domainId}/audits?page=0&size=25&" + "#{searchQuery}")
    .header("Authorization", "Bearer #{auth-token}")
    .check(status.is(200))
    .check(jsonPath("$.totalCount").ofType[Int].gt(0))

  val httpProtocol = http
    .userAgentHeader("Gatling - Search AM Audit Logs")
    .disableFollowRedirect

  val scn = scenario("Search AM Audit Logs")
    .exec(login)
    .exec(retrieveDomainId(DOMAIN_NAME))
    .feed(feeder)
    .repeat(REPEAT.intValue())(
      exec(search)
        .feed(feeder)
    )

  setUp(scn.inject(atOnceUsers(AGENTS)).protocols(httpProtocol))
}

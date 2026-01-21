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
package io.gravitee.am.performance.utils

import scala.util.Random

object SimulationSettings {

  // ========================================
  // Java Options used to customize the simulations
  // ========================================

  val GATEWAY_BASE_URL = System.getProperty("gw_url", "http://localhost:8092")
  val MANAGEMENT_BASE_URL = System.getProperty("mng_url", "http://localhost:8093")
  val MANAGEMENT_USER = System.getProperty("mng_user", "admin")
  val MANAGEMENT_PWD = System.getProperty("mng_password", "adminadmin")

  val FGA_API_URL = System.getProperty("fga_api_url", "http://localhost:8080")
  val FGA_STORE_ID = System.getProperty("fga_store_id", "")
  val FGA_AUTHORIZATION_MODEL_ID = System.getProperty("fga_authorization_model_id", "")

  val DOMAIN_NAME = System.getProperty("domain", "gatling-domain")
  val IDENTITY_PROVIDER_NAME = System.getProperty("idp", "Default Identity Provider")
  val MULTI_DOMAIN_NAMES = System.getProperty("multiDomains", "gatling-domain").split(",")

  val MIN_DOMAIN_INDEX = Integer.getInteger("min_domain_index", 1)
  val NUMBER_OF_DOMAINS = Integer.getInteger("number_of_domains", 1)
  val MAX_DOMAIN_INDEX = MIN_DOMAIN_INDEX + NUMBER_OF_DOMAINS

  val MIN_USER_INDEX = Integer.getInteger("min_user_index", 1)
  val NUMBER_OF_USERS = Integer.getInteger("number_of_users", 2000)

  // useful if in future simulation we want multiple IDP
  // it will be possible to generate username with IDP relationship
  val USER_PREFIX = System.getProperty("user_prefix", "user")
  val MAX_USER_INDEX = MIN_USER_INDEX + NUMBER_OF_USERS

  val NUMBER_OF_TEAMS = Integer.getInteger("number_of_teams", 40)
  val DEPTH_OF_TEAMS = Integer.getInteger("depth_of_teams", 4)
  val NUMBER_OF_RESOURCES_PER_USER = Integer.getInteger("number_of_resources_per_user", 200)
  val NUMBER_OF_RESOURCES_PER_TEAM = Integer.getInteger("number_of_resources_per_team", 200)
  val NUMBER_OF_SHARED_RESOURCES = Integer.getInteger("number_of_shared_resources", 1000)

  val APP_NAME = System.getProperty("app", "appweb")


  val SCOPES = System.getProperty("scopes", "").split(",")

  // ========================================
  // Injection topology
  // ========================================

  val AGENTS = Integer.getInteger("agents", 10)
  val INJECTION_DURATION = Integer.getInteger("inject-during", 300)

  val REQUEST_PER_SEC = Integer.getInteger("requests", 100)
  val REQUEST_RAMP_DURATION = Integer.getInteger("req-ramp-during", 10)
  val REQUEST_HOLD_DURING = Integer.getInteger("req-hold-during", 1800)
  val REPEAT = Integer.getInteger("repeat", 10)
  val QUERY = System.getProperty("query", "")
  val EVENT = System.getProperty("event", "")
  val USER = System.getProperty("user", "admin")
  val START = System.getProperty("start", "")
  val END = System.getProperty("end", "")
  val FIELD = System.getProperty("field", "")
  val OPERATOR = System.getProperty("operator", "")
  val VALUE = System.getProperty("value", "")
  val CONDITION = System.getProperty("condition", "")
  val INTROSPECT_ENABLED = System.getProperty("introspect", "false")
  val INTROSPECTIONS = Integer.getInteger("number_of_introspections", 10)
  val FACTOR_ID = System.getProperty("factorId", "unknown")


  // ========================================
  // User Feeder
  // ========================================

  sealed trait FeederMode
  case object DATALOAD extends FeederMode
  case object WORKLOAD extends FeederMode

  def introspectFeeder() = {
    Iterator.continually(
      Map(
        "introspect_enabled" -> INTROSPECT_ENABLED,
        "introspections" -> INTROSPECTIONS)
    )
  }

  def userFeeder(mode: FeederMode) = {

    val iterator = if (mode == DATALOAD)
      Iterator.from(MIN_USER_INDEX, 1)
    else
      Iterator.continually(Random.between(MIN_USER_INDEX, MAX_USER_INDEX))

    iterator.map(singleUser)
  }

  def singleUser(index: Int) = {
        Map("email" -> s"${USER_PREFIX}${index}@acme.fr",
          "username" -> s"${USER_PREFIX}${index}",
          "firstname" -> s"first${index}",
          "lastname" -> s"last${index}",
          "password" -> "Gr@v1t33B3nchUs3rs!",
          "index" -> index,
          "factorId" -> FACTOR_ID,
          "continueUserCreation" -> (index < MAX_USER_INDEX))
  }

  def multiDomainsFeeder(mode: FeederMode) = {

    val iterator = if (mode == DATALOAD)
      Iterator.from(MIN_DOMAIN_INDEX, 1)
    else
      Iterator.continually(Random.between(MIN_DOMAIN_INDEX, MAX_DOMAIN_INDEX))

    iterator
      .map(index => {
        Map("domainName" -> s"${DOMAIN_NAME}-${index}",
          "domainIndex" -> index,
          "continueUserCreation" -> true,
          "continueDomainCreation" -> (index < MAX_DOMAIN_INDEX))
      })
  }

  def domainFeeder() = {
    val iterator = Iterator.continually(Random.between(0, MULTI_DOMAIN_NAMES.length))
    iterator
      .map(index => {
        Map("currentDomain" -> s"${MULTI_DOMAIN_NAMES(index)}",
          "index" -> index)
      })
  }
}

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

import io.gatling.core.Predef.constantUsersPerSec
import scala.concurrent.duration._

import scala.util.Random

object SimulationSettings {

  // ========================================
  // Java Options used to customize the simulations
  // ========================================

  val GATEWAY_BASE_URL = System.getProperty("gw_url", "http://localhost:8092")
  val MANAGEMENT_BASE_URL = System.getProperty("mng_url", "http://localhost:8093")
  val MANAGEMENT_USER = System.getProperty("mng_user", "admin")
  val MANAGEMENT_PWD = System.getProperty("mng_password", "adminadmin")

  val DOMAIN_NAME = System.getProperty("domain", "gatling-domain")
  val IDENTITY_PROVIDER_NAME = System.getProperty("idp", "Default Identity Provider")

  val MIN_USER_INDEX = Integer.getInteger("min_user_index", 1)
  val NUMBER_OF_USERS = Integer.getInteger("number_of_users", 2000)

  // useful if in future simulation we want multiple IDP
  // it will be possible to generate username with IDP relationship
  val USER_PREFIX = System.getProperty("user_prefix", "user")
  val MAX_USER_INDEX = MIN_USER_INDEX + NUMBER_OF_USERS

  val APP_NAME = System.getProperty("app", "appweb")

  // ========================================
  // Injection topology
  // ========================================

  val AGENTS = Integer.getInteger("agents", 10)
  val INJECTION_DURATION = Integer.getInteger("inject-during", 300)

  val REQUEST_PER_SEC = Integer.getInteger("requests", 100)
  val REQUEST_RAMP_DURATION = Integer.getInteger("req-ramp-during", 10)
  val REQUEST_HOLD_DURING = Integer.getInteger("req-hold-during", 1800)

  // ========================================
  // User Feeder
  // ========================================

  sealed trait UserFeederMode
  case object DATALOAD extends UserFeederMode
  case object WORKLOAD extends UserFeederMode

  def userFeeder(mode: UserFeederMode) = {

    val iterator = if (mode == DATALOAD)
      Iterator.from(MIN_USER_INDEX, 1)
    else
      Iterator.continually(Random.between(MIN_USER_INDEX, MAX_USER_INDEX))

    iterator
      .map(index => {
        Map("email" -> s"${USER_PREFIX}${index}@acme.fr",
          "username" -> s"${USER_PREFIX}${index}",
          "firstname" -> s"first${index}",
          "lastname" -> s"last${index}",
          "password" -> "B3nchUs3rs!",
          "index" -> index)
      })
  }
}

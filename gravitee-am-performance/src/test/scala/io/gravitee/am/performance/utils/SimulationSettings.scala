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

object SimulationSettings {

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

  val userFeeder = Iterator.from(MIN_USER_INDEX, 1)
    .map(index => Map("email" -> s"${USER_PREFIX}${index}@acme.fr",
      "username" -> s"${USER_PREFIX}${index}",
      "firstname" -> s"first${index}",
      "lastname" -> s"last${index}",
      "password" -> "B3nchUs3rs!",
      "index" -> index))
}

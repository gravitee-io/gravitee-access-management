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
package io.gravitee.am.performance.actions

import io.gatling.core.Predef.exec
import io.gatling.core.structure.ChainBuilder
import io.gravitee.am.performance.commands.ManagementAPICalls.{addScopesToApp, createApplication, enableCurrentIDPToApp, enableMfaOnApp}
import io.gravitee.am.performance.utils.ScopeSettings

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
object ProvisioningActions {

  /**
   * Create EndUser application with a list of predefined scopes (openid, profile, email, roles, groups)
   *
   * @param appName the application name used also as clientId and client secret
   * @param appType the application type (WEB or SPA)
   * @param useMfa boolean to specify if MFA need to be enabled
   * @return
   */
  def createEndUserApplication(appName: String, appType: String, useMfa: Boolean): ChainBuilder = {
    val builder = exec(createApplication(appName, appType))
            .exec(enableCurrentIDPToApp(appName))
            .exec(addScopesToApp(appName, Array(ScopeSettings("openid"), ScopeSettings("email"), ScopeSettings("profile"), ScopeSettings("roles"), ScopeSettings("groups"), ScopeSettings("full_profile"))))
    if (useMfa) builder.exec(enableMfaOnApp(appName)) else builder
  }

  /**
   * Create service application with a list of predefined scopes (scim)
   *
   * @param appName the application name used also as clientId and client secret
   * @param appType the application type (WEB or SPA)
   * @return
   */
  def createServiceApplication(appName: String, appType: String): ChainBuilder = {
    exec(createApplication(appName, appType))
      .exec(addScopesToApp(appName, Array(ScopeSettings("scim"))))
  }
}

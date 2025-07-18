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
import io.gravitee.am.performance.commands.GatewayCalls.{evaluateNextStep, renderConsentForm, renderLoginForm, renderMfaChallengeForm, renderMfaEnrollForm, submitConsentForm, submitLoginForm, submitMfaChallengeForm, submitMfaEnrollForm}
import io.gatling.core.Predef._

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
object EndUserActions {

  /**
   * display the user login form and submit the user credentials
   *
   * @return
   */
  def authenticateEndUser(): ChainBuilder = {
    exec(renderLoginForm())
    .pause(1)
    .exec(submitLoginForm())
  }


  def consent(scopes: Set[String]): ChainBuilder = {
    exec(renderConsentForm())
      .pause(1)
      .exec(submitConsentForm(scopes))
  }

  def challengeUser: ChainBuilder = {
    group("MFA Challenge") {
      exec(renderMfaChallengeForm())
        .pause(1)
        .exec(submitMfaChallengeForm())
    }
  }

  def enrollFactor: ChainBuilder = {
    group("MFA Enrollment") {
      exec(renderMfaEnrollForm())
        .pause(1)
        .exec(submitMfaEnrollForm())
    }
    .exec(evaluateNextStep)
    .group("MFA Challenge") {
      exec(renderMfaChallengeForm())
        .pause(1)
        .exec(submitMfaChallengeForm())
    }
  }
}

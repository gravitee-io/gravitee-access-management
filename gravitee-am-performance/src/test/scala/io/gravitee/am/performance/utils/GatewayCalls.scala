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

import io.gatling.core.Predef._
import io.gatling.http.HeaderNames.Location
import io.gatling.http.Predef._
import io.gravitee.am.performance.utils.SimulationSettings._

import java.net.URL

object GatewayCalls {

  def initCodeFlow(domain: String = DOMAIN_NAME) = {
    http("Get Authorization Endpoint")
      .get(GATEWAY_BASE_URL + s"/${domain}/oauth/authorize?client_id=${APP_NAME}&response_type=code&redirect_uri=https%3A%2F%2Fcallback-${APP_NAME}")
      .check(status.is(302))
      .check(header(Location).transform(headerValue => headerValue.contains("error")).is(false))
      .check(header(Location).saveAs("postAuthorize"))
  }

  def renderLoginForm(domain: String = DOMAIN_NAME) = {
    http("Get Login Form")
      .get(GATEWAY_BASE_URL + s"/${domain}/login?client_id=${APP_NAME}&response_type=code&redirect_uri=https://callback-${APP_NAME}")
      .check(status.is(200))
      // should use getCookieValue instead but our cookie doesn't have domain value that seems to be problematic for gatling
      // so extract the value from the form
      .check(css("input[name='X-XSRF-TOKEN']", "value").find.saveAs("XSRF-TOKEN"))
  }

  def submitLoginForm(domain: String = DOMAIN_NAME) = {
    http("Post Login Form")
      .post(GATEWAY_BASE_URL + s"/${domain}/login?client_id=${APP_NAME}&response_type=code&redirect_uri=https://callback-${APP_NAME}")
      .formParam("X-XSRF-TOKEN", "#{XSRF-TOKEN}")
      .formParam("username", "#{username}")
      .formParam("password", "#{password}")
      .formParam("client_id", APP_NAME)
      .check(status.is(302))
      .check(header(Location).transform(headerValue => headerValue.contains("error")).is(false))
      .check(header(Location).saveAs("postLoginRedirect"))
  }

  def callPostLoginRedirect = {
    http("Request Code after Login")
      .get("#{postLoginRedirect}")
      .check(status.is(302))
      .check(header(Location).transform(headerValue => headerValue.contains("error")).is(false))
      .check(header(Location).transform(headerValue => headerValue.contains("code")).is(true))
      .check(header(Location).transform(headerValue => {
        val params = new URL(headerValue).getQuery().split('&')
        val head = params.filter(_.startsWith("code")).map(_.split("=")).head
        head(1)
      }
      ).saveAs("code"))
  }

  def renderMfaEnrollForm(domain: String = DOMAIN_NAME) = {
    http("Get Mfa Enroll Form")
            .get(GATEWAY_BASE_URL + s"/${domain}/mfa/enroll?client_id=${APP_NAME}&response_type=code&redirect_uri=https://callback-${APP_NAME}")
            .check(status.is(200))
            // should use getCookieValue instead but our cookie doesn't have domain value that seems to be problematic for gatling
            // so extract the value from the form
            .check(css("input[name='X-XSRF-TOKEN']", "value").find.saveAs("XSRF-TOKEN"))
  }

  def submitMfaEnrollForm(domain: String = DOMAIN_NAME) = {
    http("Post Mfa Enroll Form")
            .post(GATEWAY_BASE_URL + s"/${domain}/mfa/enroll?client_id=${APP_NAME}&response_type=code&redirect_uri=https://callback-${APP_NAME}")
            .formParam("X-XSRF-TOKEN", "#{XSRF-TOKEN}")
            .formParam("factorId", "#{factorId}")
            .formParam("user_mfa_enrollment", "true")
            .formParam("phone", "+33615492508")
            .formParam("client_id", APP_NAME)
            .check(status.is(302))
            .check(header(Location).transform(headerValue => headerValue.contains("error")).is(false))
            .check(header(Location).saveAs("postMfaEnroll"))
  }

  def renderMfaChallengeForm(domain: String = DOMAIN_NAME) = {
    http("Get Mfa Challenge Form")
            .get(GATEWAY_BASE_URL + s"/${domain}/mfa/challenge?client_id=${APP_NAME}&response_type=code&redirect_uri=https://callback-${APP_NAME}")
            .check(status.is(200))
            // should use getCookieValue instead but our cookie doesn't have domain value that seems to be problematic for gatling
            // so extract the value from the form
            .check(css("input[name='X-XSRF-TOKEN']", "value").find.saveAs("XSRF-TOKEN"))
  }

  def submitMfaChallengeForm(domain: String = DOMAIN_NAME) = {
    http("Post Mfa Challenge Form")
            .post(GATEWAY_BASE_URL + s"/${domain}/mfa/challenge?client_id=${APP_NAME}&response_type=code&redirect_uri=https://callback-${APP_NAME}")
            .formParam("X-XSRF-TOKEN", "#{XSRF-TOKEN}")
            .formParam("code", "123456")
            .formParam("factorId", "#{factorId}")
            .formParam("client_id", APP_NAME)
            .check(status.is(302))
            .check(header(Location).transform(headerValue => headerValue.contains("error")).is(false))
            .check(header(Location).saveAs("postLoginRedirect"))
  }

  def requestAccessToken(domain: String = DOMAIN_NAME) = {
    http("Ask Token")
      .post(GATEWAY_BASE_URL + s"/${domain}/oauth/token")
      .basicAuth(APP_NAME, APP_NAME)
      .formParam("code", "#{code}")
      .formParam("grant_type", "authorization_code")
      .formParam("redirect_uri", s"https://callback-${APP_NAME}")
      .formParam("client_id", APP_NAME)
      .check(status.is(200))
      .check(jsonPath("$.access_token").saveAs("access_token"))
  }

  def requestAccessTokenWithUserCredentials(domain: String = DOMAIN_NAME) = {
    http("Ask Token")
      .post(GATEWAY_BASE_URL + s"/${domain}/oauth/token")
      .basicAuth(APP_NAME, APP_NAME)
      .formParam("grant_type", "password")
      .formParam("redirect_uri", s"https://callback-${APP_NAME}")
      .formParam("client_id", APP_NAME)
      .formParam("username", "#{username}")
      .formParam("password", "#{password}")
      .check(status.is(200))
      .check(jsonPath("$.access_token").saveAs("access_token"))
  }

  def logout(domain: String = DOMAIN_NAME) = {
    http("logout")
      .get(GATEWAY_BASE_URL + s"/${domain}/logout")
      .check(status.is(302))
  }

  def introspectToken(domain: String = DOMAIN_NAME) = {
    http("Introspect Access Token")
      .post(s"${GATEWAY_BASE_URL}/${domain}/oauth/introspect")
      .basicAuth(APP_NAME, APP_NAME)
      .formParam("token", "#{access_token}")
      .check(status.is(200))
      .check(jsonPath("$.active").is("true"))
  }
}

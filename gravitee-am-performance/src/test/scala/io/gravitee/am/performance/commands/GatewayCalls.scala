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
package io.gravitee.am.performance.commands

import io.gatling.core.Predef._
import io.gatling.http.HeaderNames.Location
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder
import io.gravitee.am.performance.utils.SimulationSettings._

import java.net.URL

object GatewayCalls {

  val NEXT_ACTION_KEY = "nextAction"
  val ACCESS_TOKEN_KEY = "accessToken"
  val XSRF_TOKEN_KEY = "XSRF-Token"
  val FACTOR_ID = "factorId"
  val CODE_KEY = "authorizationCode"

  def initAuthFlow(scopes: Set[String] = Set.empty) = {
    http("Get Authorization Endpoint")
      .get(GATEWAY_BASE_URL + s"/#{domainName}/oauth/authorize?client_id=${APP_NAME}&response_type=code&redirect_uri=https%3A%2F%2Fcallback-${APP_NAME}" + (if (scopes.nonEmpty) "&prompt=consent&scope=" + scopes.mkString(" ").trim else ""))
      .check(status.is(302))
      .check(header(Location).transform(headerValue => headerValue.contains("error")).is(false))
      .check(header(Location).saveAs(NEXT_ACTION_KEY))
  }

  def evaluateNextStep   = {
    http("Get Authorization Endpoint")
      .get("#{" + NEXT_ACTION_KEY + "}")
      .check(status.is(302))
      .check(header(Location).transform(headerValue => headerValue.contains("error")).is(false))
      .check(header(Location).saveAs(NEXT_ACTION_KEY))
  }

  def renderLoginForm() = {
    http("Get Login Form")
      .get("#{" + NEXT_ACTION_KEY + "}")
      .check(status.is(200))
      // should use getCookieValue instead but our cookie doesn't have domain value that seems to be problematic for gatling
      // so extract the value from the form
      .check(css("input[name='X-XSRF-TOKEN']", "value").find.saveAs(XSRF_TOKEN_KEY))
  }

  def submitLoginForm() = {
    http("Post Login Form")
      .post("#{" + NEXT_ACTION_KEY + "}")
      .formParam("X-XSRF-TOKEN", "#{"+XSRF_TOKEN_KEY+"}")
      .formParam("username", "#{username}")
      .formParam("password", "#{password}")
      .formParam("client_id", APP_NAME)
      .check(status.is(302))
      .check(header(Location).transform(headerValue => headerValue.contains("error")).is(false))
      .check(header(Location).saveAs(NEXT_ACTION_KEY))
  }

  def requestForAuthorizationCode = {
    http("Request Authorization Code")
      .get("#{" + NEXT_ACTION_KEY + "}")
      .check(status.is(302))
      .check(header(Location).transform(headerValue => headerValue.contains("error")).is(false))
      .check(header(Location).transform(headerValue => headerValue.contains("code")).is(true))
      .check(header(Location).transform(headerValue => {
        val params = new URL(headerValue).getQuery().split('&')
        val head = params.filter(_.startsWith("code")).map(_.split("=")).head
        head(1)
      }
      ).saveAs(CODE_KEY))
  }

  def renderMfaEnrollForm() = {
    http("Get Mfa Enroll Form")
      .get("#{" + NEXT_ACTION_KEY + "}")
      .check(status.is(200))
      // should use getCookieValue instead but our cookie doesn't have domain value that seems to be problematic for gatling
      // so extract the value from the form
      .check(css("input[name='X-XSRF-TOKEN']", "value").find.saveAs(XSRF_TOKEN_KEY))
      .check(css("input[name='factorId']", "value").find.saveAs(FACTOR_ID))
  }

  def submitMfaEnrollForm() = {
    http("Post Mfa Enroll Form")
      .post("#{" + NEXT_ACTION_KEY + "}")
      .formParam("X-XSRF-TOKEN", "#{"+XSRF_TOKEN_KEY+"}")
      .formParam("factorId", "#{"+FACTOR_ID+"}")
      .formParam("user_mfa_enrollment", "true")
      .formParam("phone", "+33615492508")
      .formParam("client_id", APP_NAME)
      .check(status.is(302))
      .check(header(Location).transform(headerValue => headerValue.contains("error")).is(false))
      .check(header(Location).saveAs(NEXT_ACTION_KEY))
  }

  def renderMfaChallengeForm() = {
    http("Get Mfa Challenge Form")
      .get("#{" + NEXT_ACTION_KEY + "}")
      .check(status.is(200))
      // should use getCookieValue instead but our cookie doesn't have domain value that seems to be problematic for gatling
      // so extract the value from the form
      .check(css("input[name='X-XSRF-TOKEN']", "value").find.saveAs(XSRF_TOKEN_KEY))
      .check(css("input[name='factorId']", "value").find.saveAs(FACTOR_ID))
  }

  def submitMfaChallengeForm() = {
    http("Post Mfa Challenge Form")
      .post("#{" + NEXT_ACTION_KEY + "}")
      .formParam("X-XSRF-TOKEN", "#{"+XSRF_TOKEN_KEY+"}")
      .formParam("code", "123456")
      .formParam("factorId", "#{"+FACTOR_ID+"}")
      .formParam("client_id", APP_NAME)
      .check(status.is(302))
      .check(header(Location).transform(headerValue => headerValue.contains("error")).is(false))
      .check(header(Location).saveAs(NEXT_ACTION_KEY))
  }

  def renderConsentForm() = {
    http("Get Consent Form")
      .get("#{" + NEXT_ACTION_KEY + "}")
      .check(status.is(200))
      // should use getCookieValue instead but our cookie doesn't have domain value that seems to be problematic for gatling
      // so extract the value from the form
      .check(css("input[name='X-XSRF-TOKEN']", "value").find.saveAs(XSRF_TOKEN_KEY))
  }

  def submitConsentForm(scopes: Set[String]) = {
    val builder = http("Post Mfa Challenge Form")
            .post("#{" + NEXT_ACTION_KEY + "}")
            .formParam("X-XSRF-TOKEN", "#{" + XSRF_TOKEN_KEY + "}")
            .formParam("client_id", APP_NAME)
            .formParam("user_oauth_approval", true)

    scopes.foldRight(builder)((scope, b) => b.formParam("scope." + scope, true))
      .check(status.is(302))
      .check(header(Location).transform(headerValue => headerValue.contains("error")).is(false))
      .check(header(Location).saveAs(NEXT_ACTION_KEY))
  }

  def requestAccessToken(): HttpRequestBuilder = {
    http("Ask Token")
            .post(GATEWAY_BASE_URL + "/#{domainName}/oauth/token")
            .basicAuth(APP_NAME, APP_NAME)
            .formParam("code", "#{" + CODE_KEY + "}")
            .formParam("grant_type", "authorization_code")
            .formParam("redirect_uri", s"https://callback-${APP_NAME}")
            .formParam("client_id", APP_NAME)
            .check(status.is(200))
            .check(jsonPath("$.access_token").saveAs(ACCESS_TOKEN_KEY))
  }

  def requestAccessTokenWithUserCredentials(): HttpRequestBuilder = {
    http("Ask Token With User Password")
            .post(GATEWAY_BASE_URL + "/#{domainName}/oauth/token")
            .basicAuth(APP_NAME, APP_NAME)
            .formParam("grant_type", "password")
            .formParam("redirect_uri", s"https://callback-${APP_NAME}")
            .formParam("client_id", APP_NAME)
            .formParam("username", "#{username}")
            .formParam("password", "#{password}")
            .check(status.is(200))
            .check(jsonPath("$.access_token").saveAs(ACCESS_TOKEN_KEY))
  }

  def requestTokenForServiceApp(): HttpRequestBuilder = {
    http("Ask Token")
            .post(GATEWAY_BASE_URL + "/#{domainName}/oauth/token")
            .basicAuth(APP_NAME, APP_NAME)
            .formParam("grant_type", "client_credentials")
            .check(status.is(200))
            .check(jsonPath("$.access_token").saveAs(GatewayCalls.ACCESS_TOKEN_KEY))
  }

  def logout() = {
    http("logout")
      .get(GATEWAY_BASE_URL + s"/#{domainName}/logout")
      .check(status.is(302))
  }

  def introspectToken() = {
    http("Introspect Access Token")
      .post(s"${GATEWAY_BASE_URL}/#{domainName}/oauth/introspect")
      .basicAuth(APP_NAME, APP_NAME)
      .formParam("token", "#{" + ACCESS_TOKEN_KEY + "}")
      .check(status.is(200))
      .check(jsonPath("$.active").is("true"))
  }

  def getUserInfo() = {
    http("Get User Information")
      .get(s"${GATEWAY_BASE_URL}/#{domainName}/oidc/userinfo")
      .header("Authorization", "Bearer #{"+ACCESS_TOKEN_KEY+"}")
      .check(status.is(200))
  }
}

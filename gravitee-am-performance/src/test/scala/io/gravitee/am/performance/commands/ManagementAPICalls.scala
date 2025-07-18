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
import io.gatling.http.Predef._
import io.gravitee.am.performance.utils.ScopeSettings
import io.gravitee.am.performance.utils.SimulationSettings._

object ManagementAPICalls {

  def login = {
    http("Login")
      .post(MANAGEMENT_BASE_URL + "/management/auth/token")
      .basicAuth(MANAGEMENT_USER, MANAGEMENT_PWD)
      .check(jsonPath("$.access_token").saveAs("auth-token"))
      .check(status.is(200))
  }

  def retrieveDomainId(domainName: String) = {
    http("Get Domain")
      .get(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/")
      .queryParam("q", s"*${domainName}*")
      .header("Authorization", "Bearer #{auth-token}")
      .check(status.is(200))
      .check(jsonPath("$.data[?(@.name==\"" + domainName + "\")].id").saveAs("domainId"))
  }

  def retrieveIdentityProviderId(idpName: String) = {
    http("Get Identity Providers")
      .get(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/#{domainId}/identities")
      .header("Authorization", "Bearer #{auth-token}")
      .check(status.is(200))
      .check(jsonPath("$[?(@.name==\"" + idpName + "\")].id").saveAs("identityId"))

  }

  def createDomain(domain: String) = {
    http("Create Domain")
      .post(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/")
      .header("Authorization", "Bearer #{auth-token}")
      .body(StringBody(s"""{"name":"${domain}", "dataPlaneId":"default"}""")).asJson
      .check(status.is(201))
      .check(jsonPath("$.id").saveAs("domainId"))
  }

  def enableCurrentIDPToApp(appName: String) = {
    http("Enable IDP on " + appName + " Application")
      .patch(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/#{domainId}/applications/#{"+appName+"Id}")
      .header("Authorization", "Bearer #{auth-token}")
      .body(StringBody("""{"identityProviders":[{"identity":"#{identityId}","selectionRule":"","priority":0}]}""")).asJson
      .check(status.is(200))
  }

  def addScopesToApp(appName: String, scopes: Array[ScopeSettings]) = {
    // convert settings in json object string concat with coma.
    val scopeSettings = scopes.map(s => s"""{"scope":"${s.scope}", "defaultScope":${s.defaultScope}}""").mkString(",")

    http("Add Scopes to " + appName + " Application")
      .patch(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/#{domainId}/applications/#{"+appName+"Id}")
      .header("Authorization", "Bearer #{auth-token}")
      .body(StringBody(s"""{"settings":{"oauth":{"enhanceScopesWithUserPermissions":false,"scopeSettings":[${scopeSettings}]}}}""")).asJson
      .check(status.is(200))
  }

  def createApplication(appName: String, appType: String) = {
    http("Create " + appType + " Application ")
      .post(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/#{domainId}/applications")
      .header("Authorization", "Bearer #{auth-token}")
      .body(StringBody(s"""{"name":"${appName}","type":"${appType}","clientId":"${appName}","clientSecret":"${appName}","redirectUris":["https://callback-${appName}"]}""")).asJson
      .check(status.is(201))
      .check(jsonPath("$.id").saveAs(s"${appName}Id"))
  }

  def enableCurrentDomain = {
    http("Enable Domain")
      .patch(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/#{domainId}")
      .header("Authorization", "Bearer #{auth-token}")
      .body(StringBody(s"""{"enabled":"true"}""")).asJson
      .check(status.is(200))
  }

  def activateScimOnDomain = {
    http("Enable SCIM on Domain")
      .patch(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/#{domainId}")
      .header("Authorization", "Bearer #{auth-token}")
      .body(StringBody(s"""{"scim":{"enabled":true,"idpSelectionEnabled":false}}""")).asJson
      .check(status.is(200))
  }

  def createUser = {
    http("Create User")
      .post(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/#{domainId}/users")
      .header("Authorization", "Bearer #{auth-token}")
      .body(StringBody(
        """{"firstName":"#{firstname}",
          |"lastName":"#{lastname}",
          |"email":"#{email}",
          |"username":"#{username}",
          |"password":"#{password}",
          |"source":"#{identityId}",
          |"preRegistration":false}""".stripMargin)).asJson
      .check(status.is(201))
  }

  def createMockResource = {
    http("Create Mock resource ")
            .post(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/#{domainId}/resources")
            .header("Authorization", "Bearer #{auth-token}")
            .body(StringBody(s"""{"type":"mock-mfa-am-resource","configuration":"{\\"code\\" : \\"123456\\"}","name":"Mock Resource MFA"}""")).asJson
            .check(status.is(201))
            .check(jsonPath("$.id").saveAs("mockResourceMFAId"))
  }

  def createMFAWithMockResource = {
    http("Create SMS Factor ")
            .post(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/#{domainId}/factors")
            .header("Authorization", "Bearer #{auth-token}")
            .body(StringBody("""{"type":"sms-am-factor","factorType":"SMS","configuration":"{\"countryCodes\":\"fr\",\"graviteeResource\":\"#{mockResourceMFAId}\",\"returnDigits\":6,\"expiresAfter\":300}","name":"SMS"}""")).asJson
            .check(status.is(201))
            .check(jsonPath("$.id").saveAs("smsMFAId"))
  }

  def enableMfaOnApp(appName: String) = {
    http("Add MFA to " + appName + " Application")
            .patch(MANAGEMENT_BASE_URL + "/management/organizations/DEFAULT/environments/DEFAULT/domains/#{domainId}/applications/#{"+appName+"Id}")
            .header("Authorization", "Bearer #{auth-token}")
            .body(StringBody("""{"factors":["#{smsMFAId}"],"settings":{"riskAssessment":{"enabled":false,"deviceAssessment":{"enabled":false,"thresholds":{}},"ipReputationAssessment":{"enabled":false,"thresholds":{}},"geoVelocityAssessment":{"enabled":false,"thresholds":{}}},"mfa":{"factor":{"defaultFactorId":"#{smsMFAId}","applicationFactors":[{"id":"#{smsMFAId}"}]},"stepUpAuthenticationRule":"","stepUpAuthentication":{},"adaptiveAuthenticationRule":"","rememberDevice":{},"enrollment":{"forceEnrollment":true,"skipTimeSeconds":null},"enroll":{"active":true,"enrollmentRule":"","enrollmentSkipActive":false,"enrollmentSkipRule":"","forceEnrollment":true,"skipTimeSeconds":null,"type":"REQUIRED"},"challenge":{"active":true,"challengeRule":"","type":"REQUIRED"}}}}""")).asJson
            .check(status.is(200))
  }
}

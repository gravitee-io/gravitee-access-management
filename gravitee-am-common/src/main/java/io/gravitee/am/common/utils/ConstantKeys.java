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
package io.gravitee.am.common.utils;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ConstantKeys {

    // Common key.
    String CLIENT_CONTEXT_KEY = "client";
    String USER_CONTEXT_KEY = "user";
    String DOMAIN_CONTEXT_KEY = "domain";
    String PROVIDER_CONTEXT_KEY = "provider";
    String PARAM_CONTEXT_KEY = "param";
    String SCOPES_CONTEXT_KEY = "scopes";
    String AUTHORIZATION_REQUEST_CONTEXT_KEY = "authorization_request";
    String ID_TOKEN_CONTEXT_KEY = "idToken";
    String PROVIDER_METADATA_CONTEXT_KEY = "openIDProviderMetadata";
    String RAW_TOKEN_CONTEXT_KEY = "raw_token";
    String TOKEN_CONTEXT_KEY = "token";
    String RETURN_URL_KEY = "return_url";
    String ID_TOKEN_KEY = "id_token";
    String ACCESS_TOKEN_KEY = "access_token";
    String ID_TOKEN_HINT_KEY = "id_token_hint";
    String EMAIL_PARAM_KEY = "email";
    String ERROR_PARAM_KEY = "error";
    String ERROR_CODE_PARAM_KEY = "error_code";
    String ERROR_DESCRIPTION_PARAM_KEY = "error_description";
    String SUCCESS_PARAM_KEY = "success";
    String WARNING_PARAM_KEY = "warning";
    String PASSWORD_SETTINGS_PARAM_KEY = "passwordSettings";
    String TOKEN_PARAM_KEY = "token";
    String TOKEN_TYPE_HINT_PARAM_KEY = "token_type_hint";
    String USERNAME_PARAM_KEY = "username";
    String PASSWORD_PARAM_KEY = "password";
    String PROVIDER_ID_PARAM_KEY = "providerId";
    String ACTION_KEY = "action";
    String LOGIN_ACTION_KEY = "loginAction";
    String SKIP_ACTION_KEY = "skipAction";
    String TRANSACTION_ID_KEY = "tid";
    String OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY = "op_access_token";
    String OIDC_PROVIDER_ID_TOKEN_KEY = "op_id_token";
    String PEER_CERTIFICATE_THUMBPRINT = "x509_thumbprint_s256";
    String DEVICE_TYPE = "deviceType";
    String DEVICE_ID = "deviceId";

    // enrich authentication flow keys
    String AUTH_FLOW_CONTEXT_KEY = "authFlowContext";
    String AUTH_FLOW_CONTEXT_VERSION_KEY = "authFlowVer";
    String AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY = "authFlow";

    // MFA keys.
    String MFA_SKIPPED_KEY = "mfaEnrollmentSkipped";
    String MFA_CHALLENGE_COMPLETED_KEY = "mfaChallengeCompleted";
    String STRONG_AUTH_COMPLETED_KEY = "strongAuthCompleted";
    String ENROLLED_FACTOR_ID_KEY = "enrolledFactorId";
    String ENROLLED_FACTOR_SECURITY_VALUE_KEY = "enrolledFactorSecurityValue";
    String ENROLLED_FACTOR_PHONE_NUMBER = "enrolledFactorPhoneNumber";
    String ENROLLED_FACTOR_EMAIL_ADDRESS = "enrolledFactorEmailAddress";
    String ALTERNATIVE_FACTOR_ID_KEY = "alternativeFactorId";
    String FACTORS_KEY = "factors";
    String FACTOR_KEY = "factor";
    String MFA_FACTOR_ID_CONTEXT_KEY = "mfaFactorId";

    // Passwordless keys.
    String WEBAUTHN_SKIPPED_KEY = "webAuthnRegistrationSkipped";
    String WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY = "webAuthnCredentialId";
    String PARAM_AUTHENTICATOR_ATTACHMENT_KEY = "authenticatorAttachment";
    String PASSWORDLESS_AUTH_COMPLETED_KEY = "passwordlessAuthCompleted";
    String PASSWORDLESS_CHALLENGE_KEY = "challenge";
    String PASSWORDLESS_CHALLENGE_USER_ID = "passwordlessUserId";
    String PASSWORDLESS_CHALLENGE_USERNAME_KEY = "passwordlessUsername";

    // Consent keys.
    String USER_CONSENT_COMPLETED_KEY = "userConsentCompleted";
    String USER_CONSENT_APPROVED_KEY = "userConsentApproved";

    // Register keys.
    String REGISTRATION_RESPONSE_KEY = "registrationResponse";

    String AUTH_NEGOTIATE_KEY = "Negotiate";
    String ASK_FOR_NEGOTIATE_KEY = "ask-negotiate";
    String NEGOTIATE_CONTINUE_TOKEN_KEY = "negotiate-continue-token";

    // Forgot Password
    String FORGOT_PASSWORD_FIELDS_KEY = "forgotPwdFormFields";
    String FORGOT_PASSWORD_CONFIRM = "forgot_password_confirm";

    // Login keys.
    String USER_LOGIN_COMPLETED_KEY = "userLoginCompleted";

    // key used to store & retrieve the request object from the routing context
    String REQUEST_OBJECT_KEY = "requestObject";
    // identifier of the Pushed Authorization Parameters
    String REQUEST_URI_ID_KEY = "requestUriId";
    String REQUEST_OBJECT_FROM_URI = "request-object-from-uri";

    //geoip
    String GEOIP_KEY = "geoip";

    //login attempts
    String LOGIN_ATTEMPT_KEY = "login_attempts";

    //remember device
    String DEVICE_ALREADY_EXISTS_KEY = "deviceAlreadyExists";
    String REMEMBER_DEVICE_CONSENT_TIME_SECONDS = "rememberDeviceConsentTimeSeconds";

    // ------
    // Values used to find key into gravitee.yaml
    // ------

    // Header name that will contain the Peer Certificate
    String HTTP_SSL_CERTIFICATE_HEADER = "http.ssl.certificateHeader";
    // Base Url for mtls_endpoint_aliases on which the domain HRID and the oauth endpoints will be appended
    String HTTP_SSL_ALIASES_BASE_URL = "http.ssl.mtls_aliases.base_url";
    String HTTP_SSL_ALIASES_ENDPOINTS = "http.ssl.mtls_aliases.endpoints";
    String HTTP_SSL_ALIASES_ENDPOINTS_TOKEN = "token_endpoint";
    String HTTP_SSL_ALIASES_ENDPOINTS_AUTHORIZATION = "authorization_endpoint";
    String HTTP_SSL_ALIASES_ENDPOINTS_REGISTRATION = "registration_endpoint";
    String HTTP_SSL_ALIASES_ENDPOINTS_USERINFO = "userinfo_endpoint";
    String HTTP_SSL_ALIASES_ENDPOINTS_PAR = "pushed_authorization_request_endpoint";
    String HTTP_SSL_ALIASES_ENDPOINTS_END_SESSION = "end_session_endpoint";
    String HTTP_SSL_ALIASES_ENDPOINTS_REVOCATION = "revocation_endpoint";
    String HTTP_SSL_ALIASES_ENDPOINTS_INTROSPECTION = "introspection_endpoint";
}

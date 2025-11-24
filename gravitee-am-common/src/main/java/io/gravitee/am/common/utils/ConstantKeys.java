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

import io.gravitee.am.common.jwt.Claims;

import java.util.Set;

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
    String IDENTITY_PROVIDER_CONTEXT_KEY = "identityProvider";
    String PARAM_CONTEXT_KEY = "param";
    String SCOPES_CONTEXT_KEY = "scopes";
    String AUTHORIZATION_REQUEST_CONTEXT_KEY = "authorization_request";
    String ID_TOKEN_CONTEXT_KEY = "idToken";
    String PROVIDER_METADATA_CONTEXT_KEY = "openIDProviderMetadata";
    String RAW_TOKEN_CONTEXT_KEY = "raw_token";
    String TOKEN_CONTEXT_KEY = "token";
    String SILENT_AUTH_CONTEXT_KEY = "silentAuth";
    String RETURN_URL_KEY = "return_url";
    String ID_TOKEN_KEY = "id_token";
    String ACCESS_TOKEN_KEY = "access_token";
    String ID_TOKEN_HINT_KEY = "id_token_hint";
    String EMAIL_PARAM_KEY = "email";
    String ERROR_PARAM_KEY = "error";
    String SERVER_ERROR = "server_error";
    String INVALID_TOKEN = "invalid_token";
    String MFA_CHALLENGE_FAILED = "mfa_challenge_failed";
    String MFA_ENROLL_VALIDATION_FAILED = "mfa_enroll_failed";
    String LOGIN_FAILED = "login_failed";
    String USER_CONSENT_FAILED = "user_consent_failed";
    String RATE_LIMIT_ERROR_PARAM_KEY = "request_limit_error";
    String VERIFY_ATTEMPT_ERROR_PARAM_KEY = "verify_attempt_error";
    String ERROR_CODE_PARAM_KEY = "error_code";
    String ERROR_DESCRIPTION_PARAM_KEY = "error_description";
    String SUCCESS_PARAM_KEY = "success";
    String WARNING_PARAM_KEY = "warning";
    String PASSWORD_SETTINGS_PARAM_KEY = "passwordSettings";
    String TOKEN_PARAM_KEY = "token";
    String TOKEN_TYPE_HINT_PARAM_KEY = "token_type_hint";
    String USERNAME_PARAM_KEY = "username";
    String PASSWORD_PARAM_KEY = "password";
    String REMEMBER_ME_PARAM_KEY = "rememberMe";
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
    String X_XSRF_TOKEN = "X-XSRF-TOKEN";
    String _CSRF = "_csrf";
    String SOCIAL_PROVIDER_CONTEXT_KEY = "socialProviders";
    String INTERNAL_PROVIDER_CONTEXT_KEY = "identityProviders";
    String URL_HASH_PARAMETER = "urlHash";
    String ERROR_HASH = "errorHash";
    String STORE_ORIGINAL_TOKEN_KEY = "store_original_token";


    // enrich authentication flow keys
    String AUTH_FLOW_CONTEXT_KEY = "authFlowContext";
    String AUTH_FLOW_CONTEXT_VERSION_KEY = "authFlowVer";
    String AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY = "authFlow";

    // MFA keys.
    String MFA_STOP = "mfaStop";
    String MFA_ENROLL_CONDITIONAL_SKIPPED_KEY = "mfaEnrollmentCanBeSkippedConditionally";
    String MFA_ENROLLMENT_COMPLETED_KEY = "mfaEnrollmentCompleted";
    String MFA_CHALLENGE_COMPLETED_KEY = "mfaChallengeCompleted";
    String STRONG_AUTH_COMPLETED_KEY = "strongAuthCompleted";
    String ENROLLED_FACTOR_ID_KEY = "enrolledFactorId";
    String ENROLLED_FACTOR_SECURITY_VALUE_KEY = "enrolledFactorSecurityValue";
    String ENROLLED_FACTOR_PHONE_NUMBER = "enrolledFactorPhoneNumber";
    String ENROLLED_FACTOR_EXTENSION_PHONE_NUMBER = "enrolledFactorExtensionPhoneNumber";
    String ENROLLED_FACTOR_EMAIL_ADDRESS = "enrolledFactorEmailAddress";
    String ENROLLED_FACTOR_INIT_REGISTRATION = "enrolledFactorInit";
    String ALTERNATIVE_FACTOR_ID_KEY = "alternativeFactorId";
    String FACTORS_KEY = "factors";
    String FACTOR_KEY = "factor";
    String MFA_FACTOR_ID_CONTEXT_KEY = "mfaFactorId";
    String MFA_ENROLLING_FIDO2_FACTOR = "enrollingFido2Factor";

    String ENROLLED_FACTOR_KEY = "enrolledFactor";

    String MFA_ALTERNATIVES_ACTION_KEY = "mfaAlternativesAction";

    String MFA_ALTERNATIVES_ENABLE_KEY = "mfaAlternativesEnabled";

    String USER_MFA_ENROLLMENT = "user_mfa_enrollment";
    String MFA_FORCE_ENROLLMENT = "mfa_force_enrollment";
    String MFA_ENROLLMENT_FACTOR_ID = "factorId";
    String MFA_ENROLLMENT_SHARED_SECRET = "sharedSecret";
    String MFA_ENROLLMENT_PHONE = "phone";

    String MFA_ENROLLMENT_EXTENSION_PHONE_NUMBER = "extensionPhoneNumber";
    String MFA_ENROLLMENT_EMAIL = "email";
    long DEFAULT_ENROLLMENT_SKIP_TIME_SECONDS = 10L * 60L * 60L; // ten hours

    // Passwordless keys.
    String WEBAUTHN_SKIPPED_KEY = "webAuthnRegistrationSkipped";
    String WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY = "webAuthnCredentialId";
    String WEBAUTHN_CREDENTIAL_INTERNAL_ID_CONTEXT_KEY = "webAuthnCredentialInternalId";
    String PARAM_AUTHENTICATOR_ATTACHMENT_KEY = "authenticatorAttachment";
    String PASSWORDLESS_AUTH_COMPLETED_KEY = "passwordlessAuthCompleted";
    String PASSWORDLESS_AUTH_ACTION_KEY = "passwordlessAuthAction";
    String PASSWORDLESS_AUTH_ACTION_VALUE_LOGIN = "login";
    String PASSWORDLESS_AUTH_ACTION_VALUE_REGISTER = "register";
    String PASSWORDLESS_CHALLENGE_KEY = "challenge";
    String PASSWORDLESS_CHALLENGE_USERNAME_KEY = "passwordlessUsername";
    String PASSWORDLESS_ORIGIN = "passwordlessOrigin";
    String PASSWORDLESS_ASSERTION = "passwordlessAssertion";
    String PASSWORDLESS_ENFORCE_PASSWORD = "passwordlessEnforcePassword";
    String PASSWORDLESS_DEVICE_NAME = "deviceName";

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
    String PASSWORD_HISTORY = "passwordHistory";
    String PASSWORD_VALIDATION = "passwordValidation";

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
    String REMEMBER_DEVICE_OR_SKIP_UNTIL = "deviceDeviceOrSkipUntil";
    String REMEMBER_DEVICE_CONSENT_TIME_SECONDS = "rememberDeviceConsentTimeSeconds";
    long DEFAULT_REMEMBER_DEVICE_CONSENT_TIME = 36000; // 10 hours
    String REMEMBER_DEVICE_IS_ACTIVE = "rememberDeviceIsActive";
    String DEVICE_IDENTIFIER_PROVIDER_KEY = "deviceIdentifierProvider";

    // ------
    // Default secret value used in gravitee.yaml
    // ------
    String DEFAULT_JWT_OR_CSRF_SECRET = "s3cR3t4grAv1t3310AMS1g1ingDftK3y";

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
    String HTTP_SSL_ALIASES_ENDPOINTS_CIBA = "backchannel_authentication_endpoint";

    String CIBA_AUTH_REQUEST_KEY = "ciba_authentication_request";

    String CSP_SCRIPT_INLINE_NONCE = "script_inline_nonce";

    //User Activity
    String USER_CONSENT_IP_LOCATION = "uc_geoip";
    String USER_CONSENT_USER_AGENT = "uc_ua";
    String USER_ACTIVITY_RETENTION_TIME = "user_activity_retention_time";
    String RISK_ASSESSMENT_KEY = "risk_assessment";
    String USER_ACTIVITY_ENABLED = "userActivityEnabled";

    // Template Variables
    // -------------------
    String TEMPLATE_KEY_BOT_DETECTION_PLUGIN = "bot_detection_plugin";
    String TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION = "bot_detection_configuration";
    // LOGIN Template
    String TEMPLATE_KEY_ALLOW_FORGOT_PASSWORD_CONTEXT_KEY = "allowForgotPassword";
    String TEMPLATE_KEY_ALLOW_REGISTER_CONTEXT_KEY = "allowRegister";
    String TEMPLATE_KEY_ALLOW_PASSWORDLESS_CONTEXT_KEY = "allowPasswordless";
    String TEMPLATE_KEY_ALLOW_CBA_CONTEXT_KEY = "allowCba";
    String TEMPLATE_KEY_HIDE_FORM_CONTEXT_KEY = "hideLoginForm";
    String TEMPLATE_KEY_IDENTIFIER_FIRST_LOGIN_CONTEXT_KEY = "identifierFirstLoginEnabled";
    String TEMPLATE_KEY_FORGOT_ACTION_KEY = "forgotPasswordAction";
    String TEMPLATE_KEY_REGISTER_ACTION_KEY = "registerAction";
    String TEMPLATE_KEY_WEBAUTHN_ACTION_KEY = "passwordlessAction";
    String TEMPLATE_KEY_CBA_ACTION_KEY = "cbaAction";
    String TEMPLATE_KEY_BACK_LOGIN_IDENTIFIER_ACTION_KEY = "backToLoginIdentifierAction";
    String TEMPLATE_KEY_REMEMBER_ME_KEY = "rememberMeEnabled";
    // MFA templates
    String TEMPLATE_KEY_RECOVERY_CODES_KEY = "recoveryCodes";
    String TEMPLATE_KEY_RECOVERY_CODES_URL_KEY = "recoveryCodeURL";

    String TEMPLATE_VERIFY_REGISTRATION_ACCOUNT_KEY = "verifyRegistrationAccount";


    // entry into the io.gravitee.am.model.AuthenticationFlowContext to get access to the
    // content of the OAuth2 parameters retrieved using PAR
    String REQUEST_PARAMETERS_KEY = "requestParameters";


    String ALLOW_REGISTER_CONTEXT_KEY = "allowRegister";
    String ALLOW_PASSWORDLESS_CONTEXT_KEY = "allowPasswordless";
    String ALLOW_FORGOT_PASSWORD_CONTEXT_KEY = "allowForgotPassword";
    String REGISTER_ACTION_KEY = "registerAction";
    String WEBAUTHN_ACTION_KEY = "passwordlessAction";
    String FORGOT_ACTION_KEY = "forgotPasswordAction";
    String REQUEST_CONTEXT_KEY = "request";
    Set<String> ID_TOKEN_EXCLUDED_CLAIMS = Set.of(
            ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY,
            ConstantKeys.OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY,
            Claims.IAT,
            Claims.EXP,
            Claims.NBF,
            Claims.AUTH_TIME,
            Claims.UPDATED_AT
    );

    String REGISTRATION_VERIFY_SUCCESS = "registration_verify_success";

    String POLICY_CHAIN_ERROR_KEY_MFA_CHALLENGE_ERROR = "GATEWAY_POLICY_MFA_CHALLENGE_ERROR";
    String LINKED_ACCOUNT_ID_CONTEXT_KEY = "linkedAccountId";

    String DEFAULT_REMEMBER_DEVICE_COOKIE_NAME = "GRAVITEE_IO_REMEMBER_DEVICE";
    String DEFAULT_REMEMBER_ME_COOKIE_NAME = "GRAVITEE_IO_REMEMBER_ME";
    String USER_ID_KEY = "userId";

    String CLAIM_QUERY_PARAM = "q";
    String CLAIM_PROVIDER_ID = "p";
    String CLAIM_REMEMBER_ME = "r";
    String CLAIM_TARGET = "t";
    String CLAIM_STATUS = "s";
    String CLAIM_TOKEN_PURPOSE = "tp";
    String STATUS_SIGNED_IN = "signed-in";
    String STATUS_FAILURE = "failure";
    String CLAIM_ISSUING_REASON = "ir";
    String ISSUING_REASON_CLOSE_IDP_SESSION = "close_idp_session";

    String CONTINUE_CALLBACK_PROCESSING = "continueCallbackProcess";

    String PROTOCOL_KEY = "protocol";
    String PROTOCOL_VALUE_SAML_REDIRECT = "SAML/HTTP-Redirect";
    String PROTOCOL_VALUE_SAML_POST = "SAML/HTTP-POST";
    String IDP_CODE_VERIFIER = "idp_code_verifier";
}

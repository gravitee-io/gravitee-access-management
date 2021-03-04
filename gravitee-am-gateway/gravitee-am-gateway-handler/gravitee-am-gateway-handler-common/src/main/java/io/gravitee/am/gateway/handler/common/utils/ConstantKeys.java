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
package io.gravitee.am.gateway.handler.common.utils;

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
    String EMAIL_PARAM_KEY = "email";
    String ERROR_PARAM_KEY = "error";
    String ERROR_CODE_PARAM_KEY = "error_code";
    String ERROR_DESCRIPTION_PARAM_KEY = "error_description";
    String SUCCESS_PARAM_KEY = "success";
    String WARNING_PARAM_KEY = "warning";
    String TOKEN_PARAM_KEY = "token";
    String TOKEN_TYPE_HINT_PARAM_KEY = "token_type_hint";
    String USERNAME_PARAM_KEY = "username";
    String PASSWORD_PARAM_KEY = "password";
    String PROVIDER_ID_PARAM_KEY = "providerId";
    String ACTION_KEY = "action";
    String LOGIN_ACTION_KEY = "loginAction";
    String SKIP_ACTION_KEY = "skipAction";
    String TRANSACTION_ID_KEY = "tid";

    // enrich authentication flow keys
    String AUTH_FLOW_CONTEXT_KEY = "authFlowContext";
    String AUTH_FLOW_CONTEXT_VERSION_KEY = "authFlowVer";
    String AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY = "authFlow";

    // MFA keys.
    String MFA_SKIPPED_KEY = "mfaEnrollmentSkipped";
    String STRONG_AUTH_COMPLETED_KEY = "strongAuthCompleted";
    String ENROLLED_FACTOR_ID_KEY = "enrolledFactorId";
    String ENROLLED_FACTOR_SECURITY_VALUE_KEY = "enrolledFactorSecurityValue";
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
}

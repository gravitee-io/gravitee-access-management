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
package io.gravitee.am.common.audit;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Audit event types
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface EventType {

    /**
     * ----------
     * User audit log actions
     * ----------
     */
    String USER_LOGIN = "USER_LOGIN";
    String USER_WEBAUTHN_LOGIN = "USER_WEBAUTHN_LOGIN";
    String USER_LOGOUT = "USER_LOGOUT";
    String USER_CREATED = "USER_CREATED";
    String USER_UPDATED = "USER_UPDATED";
    String USERNAME_UPDATED = "USERNAME_UPDATED";
    String USER_DELETED = "USER_DELETED";
    String USER_LOCKED = "USER_LOCKED";
    String USER_UNLOCKED = "USER_UNLOCKED";
    String USER_ENABLED = "USER_ENABLED";
    String USER_DISABLED = "USER_DISABLED";
    String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    String USER_REGISTERED = "USER_REGISTERED";
    String USER_CONSENT_CONSENTED = "USER_CONSENT_CONSENTED";
    String USER_CONSENT_REVOKED = "USER_CONSENT_REVOKED";
    String USER_ROLES_ASSIGNED = "USER_ROLES_ASSIGNED";
    String REGISTRATION_CONFIRMATION = "REGISTRATION_CONFIRMATION";
    String REGISTRATION_VERIFY_ACCOUNT = "REGISTRATION_VERIFY_ACCOUNT";
    String REGISTRATION_CONFIRMATION_REQUESTED = "REGISTRATION_CONFIRMATION_REQUESTED";
    String REGISTRATION_CONFIRMATION_EMAIL_SENT = "REGISTRATION_CONFIRMATION_EMAIL_SENT";
    String REGISTRATION_VERIFY_REQUESTED = "REGISTRATION_VERIFY_REQUESTED";
    String REGISTRATION_VERIFY_EMAIL_SENT = "REGISTRATION_VERIFY_EMAIL_SENT";
    String FORGOT_PASSWORD_REQUESTED = "FORGOT_PASSWORD_REQUESTED";
    String FORGOT_PASSWORD_EMAIL_SENT = "FORGOT_PASSWORD_EMAIL_SENT";
    String RESET_PASSWORD_EMAIL_SENT = "RESET_PASSWORD_EMAIL_SENT";
    String BLOCKED_ACCOUNT_EMAIL_SENT = "BLOCKED_ACCOUNT_EMAIL_SENT";
    String PASSWORD_HISTORY_CREATED = "PASSWORD_HISTORY_CREATED";
    String ACCOUNT_ACCESS_TOKEN_CREATED = "ACCOUNT_ACCESS_TOKEN_CREATED";
    String ACCOUNT_ACCESS_TOKEN_REVOKED = "ACCOUNT_ACCESS_TOKEN_REVOKED";

    /**
     * ----------
     * Domain audit log actions
     * ----------
     */
    String DOMAIN_CREATED = "DOMAIN_CREATED";
    String DOMAIN_UPDATED = "DOMAIN_UPDATED";
    String DOMAIN_DELETED = "DOMAIN_DELETED";

    /**
     * ----------
     * Client audit log actions
     * ----------
     */
    String CLIENT_CREATED = "CLIENT_CREATED";
    String CLIENT_UPDATED = "CLIENT_UPDATED";
    String CLIENT_DELETED = "CLIENT_DELETED";
    String CLIENT_SECRET_RENEWED = "CLIENT_SECRET_RENEWED";

    /**
     * ----------
     * Application audit log actions
     * ----------
     */
    String APPLICATION_CREATED = "APPLICATION_CREATED";
    String APPLICATION_UPDATED = "APPLICATION_UPDATED";
    String APPLICATION_DELETED = "APPLICATION_DELETED";
    String APPLICATION_CLIENT_SECRET_RENEWED = "APPLICATION_CLIENT_SECRET_RENEWED";
    String APPLICATION_CLIENT_SECRET_CREATED = "APPLICATION_CLIENT_SECRET_CREATED";
    String APPLICATION_CLIENT_SECRET_DELETED = "APPLICATION_CLIENT_SECRET_DELETED";

    /**
     * ----------
     * Certificate audit log actions
     * ----------
     */
    String CERTIFICATE_CREATED = "CERTIFICATE_CREATED";
    String CERTIFICATE_UPDATED = "CERTIFICATE_UPDATED";
    String CERTIFICATE_DELETED = "CERTIFICATE_DELETED";

    /**
     * ----------
     * Email template audit log actions
     * ----------
     */
    String EMAIL_TEMPLATE_CREATED = "EMAIL_TEMPLATE_CREATED";
    String EMAIL_TEMPLATE_UPDATED = "EMAIL_TEMPLATE_UPDATED";
    String EMAIL_TEMPLATE_DELETED = "EMAIL_TEMPLATE_DELETED";

    /**
     * ----------
     * Form template audit log actions
     * ----------
     */
    String FORM_TEMPLATE_CREATED = "FORM_TEMPLATE_CREATED";
    String FORM_TEMPLATE_UPDATED = "FORM_TEMPLATE_UPDATED";
    String FORM_TEMPLATE_DELETED = "FORM_TEMPLATE_DELETED";

    /**
     * ----------
     * Extension grant audit log actions
     * ----------
     */
    String EXTENSION_GRANT_CREATED = "EXTENSION_GRANT_CREATED";
    String EXTENSION_GRANT_UPDATED = "EXTENSION_GRANT_UPDATED";
    String EXTENSION_GRANT_DELETED = "EXTENSION_GRANT_DELETED";

    /**
     * ----------
     * Group audit log actions
     * ----------
     */
    String GROUP_CREATED = "GROUP_CREATED";
    String GROUP_UPDATED = "GROUP_UPDATED";
    String GROUP_DELETED = "GROUP_DELETED";
    String GROUP_ROLES_ASSIGNED = "GROUP_ROLES_ASSIGNED";

    /**
     * ----------
     * Identity provider audit log actions
     * ----------
     */
    String IDENTITY_PROVIDER_CREATED = "IDENTITY_PROVIDER_CREATED";
    String IDENTITY_PROVIDER_UPDATED = "IDENTITY_PROVIDER_UPDATED";
    String IDENTITY_PROVIDER_DELETED = "IDENTITY_PROVIDER_DELETED";

    /**
     * ----------
     * Reporter audit log actions
     * ----------
     */
    String REPORTER_CREATED = "REPORTER_CREATED";
    String REPORTER_UPDATED = "REPORTER_UPDATED";
    String REPORTER_DELETED = "REPORTER_DELETED";

    /**
     * ----------
     * Role audit log actions
     * ----------
     */
    String ROLE_CREATED = "ROLE_CREATED";
    String ROLE_UPDATED = "ROLE_UPDATED";
    String ROLE_DELETED = "ROLE_DELETED";

    /**
     * ----------
     * Scope audit log actions
     * ----------
     */
    String SCOPE_CREATED = "SCOPE_CREATED";
    String SCOPE_UPDATED = "SCOPE_UPDATED";
    String SCOPE_DELETED = "SCOPE_DELETED";

    /**
     * ----------
     * Tag audit log actions
     * ----------
     */
    String TAG_CREATED = "TAG_CREATED";
    String TAG_UPDATED = "TAG_UPDATED";
    String TAG_DELETED = "TAG_DELETED";

    /**
     * ----------
     * Policy audit log actions
     * ----------
     */
    String POLICY_CREATED = "POLICY_CREATED";
    String POLICY_UPDATED = "POLICY_UPDATED";
    String POLICY_DELETED = "POLICY_DELETED";

    /**
     * ----------
     * Membership audit log actions
     * ----------
     */
    String MEMBERSHIP_CREATED = "MEMBERSHIP_CREATED";
    String MEMBERSHIP_UPDATED = "MEMBERSHIP_UPDATED";
    String MEMBERSHIP_DELETED = "MEMBERSHIP_DELETED";

    /**
     * ----------
     * Factor audit log actions
     * ----------
     */
    String FACTOR_CREATED = "FACTOR_CREATED";
    String FACTOR_UPDATED = "FACTOR_UPDATED";
    String FACTOR_DELETED = "FACTOR_DELETED";

    /**
     * ----------
     * Bot Detection audit log actions
     * ----------
     */
    String BOT_DETECTION_CREATED = "BOT_DETECTION_CREATED";
    String BOT_DETECTION_UPDATED = "BOT_DETECTION_UPDATED";
    String BOT_DETECTION_DELETED = "BOT_DETECTION_DELETED";

    /**
     * ----------
     * Device Identifier audit log actions
     * ----------
     */
    String DEVICE_IDENTIFIER_CREATED = "DEVICE_IDENTIFIER_CREATED";
    String DEVICE_IDENTIFIER_UPDATED = "DEVICE_IDENTIFIER_UPDATED";
    String DEVICE_IDENTIFIER_DELETED = "DEVICE_IDENTIFIER_DELETED";
    /**
     * ----------
     *  Device audit log actions
     * ----------
     */
    String DEVICE_DELETED = "DEVICE_DELETED";

    /**
     * ----------
     * Resource audit log actions
     * ----------
     */
    String RESOURCE_CREATED = "RESOURCE_CREATED";
    String RESOURCE_UPDATED = "RESOURCE_UPDATED";
    String RESOURCE_DELETED = "RESOURCE_DELETED";

    /**
     * ----------
     * Organization audit log actions
     * ----------
     */
    String ORGANIZATION_CREATED = "ORGANIZATION_CREATED";
    String ORGANIZATION_UPDATED = "ORGANIZATION_UPDATED";

    /**
     * ----------
     * Environment audit log actions
     * ----------
     */
    String ENVIRONMENT_CREATED = "ENVIRONMENT_CREATED";
    String ENVIRONMENT_UPDATED = "ENVIRONMENT_UPDATED";

    /**
     * ----------
     * Entrypoint audit log actions
     * ----------
     */
    String ENTRYPOINT_CREATED = "ENTRYPOINT_CREATED";
    String ENTRYPOINT_UPDATED = "ENTRYPOINT_UPDATED";
    String ENTRYPOINT_DELETED = "ENTRYPOINT_DELETED";


    /**
     * ----------
     * Flow audit log actions
     * ----------
     */
    String FLOW_CREATED = "FLOW_CREATED";
    String FLOW_UPDATED = "FLOW_UPDATED";
    String FLOW_DELETED = "FLOW_DELETED";

    /**
     * ----------
     * Alert trigger audit log actions
     * ----------
     */
    String ALERT_TRIGGER_CREATED = "ALERT_TRIGGER_CREATED";
    String ALERT_TRIGGER_UPDATED = "ALERT_TRIGGER_UPDATED";
    String ALERT_TRIGGER_DELETED = "ALERT_TRIGGER_DELETED";

    /**
     * ----------
     * Alert notifier audit log actions
     * ----------
     */
    String ALERT_NOTIFIER_CREATED = "ALERT_NOTIFIER_CREATED";
    String ALERT_NOTIFIER_UPDATED = "ALERT_NOTIFIER_UPDATED";
    String ALERT_NOTIFIER_DELETED = "ALERT_NOTIFIER_DELETED";

    /**
     * ----------
     * AuthDevice Notifier audit log actions
     * ----------
     */
    String AUTH_DEVICE_NOTIFIER_CREATED = "AUTH_DEVICE_NOTIFIER_CREATED";
    String AUTH_DEVICE_NOTIFIER_UPDATED = "AUTH_DEVICE_NOTIFIER_UPDATED";
    String AUTH_DEVICE_NOTIFIER_DELETED = "AUTH_DEVICE_NOTIFIER_DELETED";

    /**
     * ----------
     * WebAuthn Credential audit log actions
     * ----------
     */
    String CREDENTIAL_CREATED = "CREDENTIAL_CREATED";
    String CREDENTIAL_UPDATED = "CREDENTIAL_UPDATED";
    String CREDENTIAL_DELETED = "CREDENTIAL_DELETED";

    /**
     * ----------
     * i18n dictionary audit log actions
     * ----------
     */
    String I18N_DICTIONARY_CREATED = "I18N_DICTIONARY_CREATED";
    String I18N_DICTIONARY_UPDATED = "I18N_DICTIONARY_UPDATED";
    String I18N_DICTIONARY_DELETED = "I18N_DICTIONARY_DELETED";
    /**
     * ----------
     * password policy audit log actions
     * ----------
     */
    String PASSWORD_POLICY_CREATED = "PASSWORD_POLICY_CREATED";
    String PASSWORD_POLICY_UPDATED = "PASSWORD_POLICY_UPDATED";
    String PASSWORD_POLICY_DELETED = "PASSWORD_POLICY_DELETED";

    String THEME_CREATED = "THEME_CREATED";
    String THEME_UPDATED = "THEME_UPDATED";
    String THEME_DELETED = "THEME_DELETED";

    String MFA_VERIFICATION_LIMIT_EXCEED = "MFA_VERIFY_LIMIT_EXCEED";
    String TOKEN_CREATED = "TOKEN_CREATED";
    String ACTION_DELEGATED = "ACTION_DELEGATED";
    String TOKEN_REVOKED = "TOKEN_REVOKED";
    String CLIENT_AUTHENTICATION = "CLIENT_AUTHENTICATION";
    String MFA_CHALLENGE_SENT = "MFA_CHALLENGE_SENT";
    String MFA_CHALLENGE = "MFA_CHALLENGE";
    String MFA_ENROLLMENT = "MFA_ENROLLMENT";
    String MFA_MAX_ATTEMPT_REACHED = "MFA_MAX_ATTEMPT_REACHED";
    String MFA_RATE_LIMIT_REACHED = "MFA_RATE_LIMIT_REACHED";
    String MFA_REMEMBER_DEVICE =  "MFA_REMEMBER_DEVICE";

    static Collection<String> types() {
        return new TreeSet<>(Arrays.asList(
                APPLICATION_CREATED, APPLICATION_UPDATED, APPLICATION_DELETED, APPLICATION_CLIENT_SECRET_RENEWED, APPLICATION_CLIENT_SECRET_CREATED, APPLICATION_CLIENT_SECRET_DELETED,
                USER_LOGIN, USER_WEBAUTHN_LOGIN, USER_LOGOUT, USER_CREATED, USER_UPDATED, USER_DELETED, USER_LOCKED, USER_UNLOCKED, USER_ENABLED, USER_DISABLED, USER_PASSWORD_RESET, USER_REGISTERED, USER_CONSENT_CONSENTED, USER_CONSENT_REVOKED, USER_ROLES_ASSIGNED,
                REGISTRATION_CONFIRMATION, REGISTRATION_VERIFY_ACCOUNT, REGISTRATION_CONFIRMATION_REQUESTED, REGISTRATION_CONFIRMATION_EMAIL_SENT, REGISTRATION_VERIFY_REQUESTED, REGISTRATION_VERIFY_EMAIL_SENT, FORGOT_PASSWORD_REQUESTED,
                FORGOT_PASSWORD_EMAIL_SENT, RESET_PASSWORD_EMAIL_SENT, BLOCKED_ACCOUNT_EMAIL_SENT,
                DOMAIN_CREATED, DOMAIN_UPDATED, DOMAIN_DELETED,
                CLIENT_CREATED, CLIENT_UPDATED, CLIENT_DELETED, CLIENT_SECRET_RENEWED,
                CERTIFICATE_CREATED, CERTIFICATE_UPDATED, CERTIFICATE_DELETED,
                EMAIL_TEMPLATE_CREATED, EMAIL_TEMPLATE_UPDATED, EMAIL_TEMPLATE_DELETED,
                FORM_TEMPLATE_CREATED, FORM_TEMPLATE_UPDATED, FORM_TEMPLATE_DELETED,
                EXTENSION_GRANT_CREATED, EXTENSION_GRANT_UPDATED, EXTENSION_GRANT_DELETED,
                GROUP_CREATED, GROUP_UPDATED, GROUP_DELETED, GROUP_ROLES_ASSIGNED,
                IDENTITY_PROVIDER_CREATED, IDENTITY_PROVIDER_UPDATED, IDENTITY_PROVIDER_DELETED,
                REPORTER_CREATED, REPORTER_UPDATED, REPORTER_DELETED,
                ROLE_CREATED, ROLE_UPDATED, ROLE_DELETED,
                SCOPE_CREATED, SCOPE_UPDATED, SCOPE_DELETED,
                TAG_CREATED, TAG_UPDATED, TAG_DELETED,
                POLICY_CREATED, POLICY_UPDATED, POLICY_DELETED,
                MEMBERSHIP_CREATED, MEMBERSHIP_UPDATED, MEMBERSHIP_DELETED,
                FACTOR_CREATED, FACTOR_UPDATED, FACTOR_DELETED,
                ORGANIZATION_CREATED, ORGANIZATION_UPDATED,
                ENVIRONMENT_CREATED, ENVIRONMENT_UPDATED,
                ENTRYPOINT_CREATED, ENTRYPOINT_UPDATED, ENTRYPOINT_DELETED,
                FLOW_CREATED, FLOW_UPDATED, FLOW_DELETED,
                ALERT_TRIGGER_CREATED, ALERT_TRIGGER_UPDATED, ALERT_TRIGGER_DELETED,
                AUTH_DEVICE_NOTIFIER_UPDATED, AUTH_DEVICE_NOTIFIER_CREATED, AUTH_DEVICE_NOTIFIER_DELETED,
                CREDENTIAL_CREATED, CREDENTIAL_UPDATED, CREDENTIAL_DELETED,
                I18N_DICTIONARY_CREATED, I18N_DICTIONARY_UPDATED, I18N_DICTIONARY_DELETED,
                THEME_UPDATED, THEME_DELETED, THEME_CREATED, MFA_VERIFICATION_LIMIT_EXCEED,
                TOKEN_CREATED, TOKEN_REVOKED, CLIENT_AUTHENTICATION,
                MFA_CHALLENGE, MFA_ENROLLMENT, MFA_CHALLENGE_SENT, MFA_MAX_ATTEMPT_REACHED,
                MFA_RATE_LIMIT_REACHED, MFA_REMEMBER_DEVICE,
                PASSWORD_POLICY_CREATED, PASSWORD_POLICY_UPDATED, PASSWORD_POLICY_DELETED
        ));
    }
    static List<String> loginTypes(){
        return Arrays.asList(USER_LOGIN, USER_WEBAUTHN_LOGIN);
    }
}

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
    String USER_LOGOUT = "USER_LOGOUT";
    String USER_CREATED = "USER_CREATED";
    String USER_UPDATED = "USER_UPDATED";
    String USER_DELETED = "USER_DELETED";
    String USER_LOCKED = "USER_LOCKED";
    String USER_UNLOCKED = "USER_UNLOCKED";
    String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    String USER_REGISTERED = "USER_REGISTERED";
    String USER_CONSENT_CONSENTED = "USER_CONSENT_CONSENTED";
    String USER_CONSENT_REVOKED = "USER_CONSENT_REVOKED";
    String REGISTRATION_CONFIRMATION = "REGISTRATION_CONFIRMATION";
    String REGISTRATION_CONFIRMATION_REQUESTED = "REGISTRATION_CONFIRMATION_REQUESTED";
    String FORGOT_PASSWORD_REQUESTED = "FORGOT_PASSWORD_REQUESTED";

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

    static Collection<String> types() {
        return new TreeSet(Arrays.asList(USER_LOGIN, USER_LOGOUT, USER_CREATED, USER_UPDATED, USER_DELETED, USER_LOCKED, USER_UNLOCKED, USER_PASSWORD_RESET,
                USER_REGISTERED, USER_CONSENT_CONSENTED, USER_CONSENT_REVOKED, REGISTRATION_CONFIRMATION, REGISTRATION_CONFIRMATION_REQUESTED, FORGOT_PASSWORD_REQUESTED, DOMAIN_CREATED, DOMAIN_UPDATED,
                DOMAIN_DELETED, CLIENT_CREATED, CLIENT_UPDATED, CLIENT_DELETED, CLIENT_SECRET_RENEWED, CERTIFICATE_CREATED, CERTIFICATE_UPDATED, CERTIFICATE_DELETED,
                EMAIL_TEMPLATE_CREATED, EMAIL_TEMPLATE_UPDATED, EMAIL_TEMPLATE_DELETED, FORM_TEMPLATE_CREATED, FORM_TEMPLATE_UPDATED, FORM_TEMPLATE_DELETED,
                EXTENSION_GRANT_CREATED, EXTENSION_GRANT_UPDATED, EXTENSION_GRANT_DELETED, GROUP_CREATED, GROUP_UPDATED, GROUP_DELETED,
                IDENTITY_PROVIDER_CREATED, IDENTITY_PROVIDER_UPDATED, IDENTITY_PROVIDER_DELETED, REPORTER_CREATED, REPORTER_UPDATED, REPORTER_DELETED, ROLE_CREATED, ROLE_UPDATED,
                ROLE_DELETED, SCOPE_CREATED, SCOPE_UPDATED, SCOPE_DELETED, TAG_CREATED, TAG_UPDATED, TAG_DELETED, POLICY_CREATED, POLICY_UPDATED, POLICY_DELETED
        ));
    }
}

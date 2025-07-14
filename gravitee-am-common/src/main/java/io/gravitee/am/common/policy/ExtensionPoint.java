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
package io.gravitee.am.common.policy;

/**
 * Policy extension point, stage when the policy code will be executed.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum ExtensionPoint {

    ROOT,
    PRE_LOGIN_IDENTIFIER,
    POST_LOGIN_IDENTIFIER,
    PRE_LOGIN,
    POST_LOGIN,
    PRE_CONSENT,
    POST_CONSENT,
    PRE_REGISTER,
    POST_REGISTER,
    PRE_RESET_PASSWORD,
    POST_RESET_PASSWORD,
    PRE_REGISTRATION_CONFIRMATION,
    POST_REGISTRATION_CONFIRMATION,
    PRE_TOKEN,
    POST_TOKEN,
    PRE_CONNECT,
    POST_CONNECT,
    PRE_WEBAUTHN_REGISTER,
    POST_WEBAUTHN_REGISTER,
    PRE_MFA_ENROLLMENT,
    POST_MFA_ENROLLMENT
}

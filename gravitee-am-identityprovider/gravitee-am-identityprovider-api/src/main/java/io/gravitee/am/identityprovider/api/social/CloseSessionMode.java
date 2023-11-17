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

package io.gravitee.am.identityprovider.api.social;

/**
 * Define the way on which the IDP session need to be closed when it has to be closed after
 * the user sign in on AM.
 */
public enum CloseSessionMode {
    /**
     * Do not close the session, lets it active or
     * close it when the AM session is closed and SingleSignOut is enabled.
     */
    KEEP_ACTIVE,
    /**
     * Use a back channel request to close the session.
     */
    BACK_CHANNEL,
    /**
     * Use redirect/front channel request to close the session.
     */
    REDIRECT;
}

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
package io.gravitee.am.gateway.handler.oauth2.exception;

/**
 * The Authorization Server requires End-User interaction of some form to proceed.
 * This error MAY be returned when the prompt parameter value in the Authentication Request is none,
 * but the Authentication Request cannot be completed without displaying a user interface for End-User interaction.
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthError">3.1.2.6. Authentication Error Response</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InteractionRequiredException extends AuthenticationErrorException {

    public InteractionRequiredException() {
        super();
    }

    public InteractionRequiredException(String message) {
        super(message);
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "interaction_required";
    }
}

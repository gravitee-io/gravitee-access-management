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
package io.gravitee.am.common.exception.mfa;

import io.gravitee.am.common.exception.authentication.AuthenticationException;
import io.gravitee.common.http.HttpStatusCode;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SendChallengeException extends AuthenticationException {

    public SendChallengeException(String msg) {
        super(msg);
    }

    @Override
    public String getErrorCode() {
        return "mfa_challenge_send_error";
    }

    @Override
    public int getHttpStatusCode() {
        return HttpStatusCode.INTERNAL_SERVER_ERROR_500;
    }
}

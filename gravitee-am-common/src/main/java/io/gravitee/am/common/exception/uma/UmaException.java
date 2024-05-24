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
package io.gravitee.am.common.exception.uma;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * See <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#authorization-failure">Uma Authorization Failures</a>
 * Using Builder pattern.
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Getter
@AllArgsConstructor
public class UmaException extends RuntimeException{

    private final String error;
    private final String ticket;
    private final String redirectUser;
    private final List<RequiredClaims> requiredClaims;
    private final Integer interval;
    private final Integer status;

    public static UmaExceptionBuilder builder() {
        return new UmaExceptionBuilder();
    }

    /**
     * need_info error must include ticket and either requiredClaims or redirectUser
     * @param ticket String
     * @return UmaExceptionBuilder
     */
    public static UmaExceptionBuilder needInfoBuilder(String ticket) {
        return new UmaExceptionBuilder().error("need_info").ticket(ticket).status(403);
    }

    public static UmaExceptionBuilder requestDeniedBuilder() {
        return new UmaExceptionBuilder().error("request_denied").status(403);
    }
}

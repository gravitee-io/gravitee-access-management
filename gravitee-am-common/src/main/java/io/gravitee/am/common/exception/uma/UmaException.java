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

import io.gravitee.common.util.MultiValueMap;

import java.util.List;

/**
 * See <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#authorization-failure">Uma Authorization Failures</a>
 * Using Builder pattern.
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class UmaException extends RuntimeException{

    private String error;
    private String ticket;
    private String redirectUser;
    private List<RequiredClaims> requiredClaims;
    private Integer interval;
    private Integer status;

    public UmaException(String error, String ticket, String redirectUser, List<RequiredClaims> requiredClaims, Integer interval, Integer status) {
        this.error = error;
        this.ticket = ticket;
        this.redirectUser = redirectUser;
        this.requiredClaims = requiredClaims;
        this.interval = interval;
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public String getTicket() {
        return ticket;
    }

    public String getRedirectUser() {
        return redirectUser;
    }

    public List<RequiredClaims> getRequiredClaims() {
        return requiredClaims;
    }

    public Integer getInterval() {
        return interval;
    }

    public Integer getStatus() {
        return status;
    }

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

    public static UmaExceptionBuilder requestSubmittedBuilder() {
        return new UmaExceptionBuilder().error("request_submitted").status(403);
    }
}

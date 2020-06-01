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
 * UmaException Builder pattern.
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class UmaExceptionBuilder {

    private String error;
    private String ticket;
    private String redirectUser;
    private List<RequiredClaims> requiredClaims;
    private Integer interval;
    private Integer status;

    public UmaExceptionBuilder error(String error) {
        this.error = error;
        return this;
    }

    public UmaExceptionBuilder ticket(String ticket) {
        this.ticket = ticket;
        return this;
    }

    public UmaExceptionBuilder redirectUser(String redirectUser) {
        this.redirectUser = redirectUser;
        return this;
    }

    public UmaExceptionBuilder requiredClaims(List<RequiredClaims> requiredClaims) {
        this.requiredClaims = requiredClaims;
        return this;
    }

    public UmaExceptionBuilder interval(Integer interval) {
        this.interval = interval;
        return this;
    }

    public UmaExceptionBuilder status(Integer status) {
        this.status = status;
        return this;
    }

    public UmaException build() {
        return new UmaException(error, ticket, redirectUser, requiredClaims, interval, status);
    }
}

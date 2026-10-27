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
package io.gravitee.am.service.exception;

import io.gravitee.common.http.HttpStatusCode;
import lombok.Getter;

/**
 * The application changed between the read and the conditional write: nothing was written.
 *
 * @author GraviteeSource Team
 */
@Getter
public class ApplicationConcurrentModificationException extends AbstractManagementException {

    private final String applicationId;

    public ApplicationConcurrentModificationException(String applicationId) {
        this.applicationId = applicationId;
    }

    @Override
    public int getHttpStatusCode() {
        return HttpStatusCode.CONFLICT_409;
    }

    @Override
    public String getMessage() {
        return "Concurrent modification of the application [" + applicationId + "]; send the same request again";
    }
}

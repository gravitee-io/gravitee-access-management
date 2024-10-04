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

package io.gravitee.am.gateway.handler.scim.exception;


import io.gravitee.am.gateway.handler.scim.model.ScimType;
import io.gravitee.common.http.HttpStatusCode;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TooManyOperationException extends SCIMException {
    public TooManyOperationException(String message) {
        super(message);
    }

    @Override
    public int getHttpStatusCode() {
        return HttpStatusCode.REQUEST_ENTITY_TOO_LARGE_413;
    }

    @Override
    public ScimType getScimType() {
        return null;
    }

    public static TooManyOperationException tooManyOperation(int limit) {
        return new TooManyOperationException(String.format("The bulk operation exceeds the maximum number of operations (%d).", limit));
    }

    public static TooManyOperationException payloadLimitReached(int limit) {
        return new TooManyOperationException(String.format("The size of the bulk operation exceeds the maxPayloadSize (%d).", limit));
    }
}

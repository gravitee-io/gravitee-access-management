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

public class TrustDomainNotFoundException extends AbstractNotFoundException {
    private final String reference;

    public TrustDomainNotFoundException(String reference) {
        this.reference = reference;
    }

    @Override
    public String getMessage() {
        return "Trust domain [" + reference + "] can not be found.";
    }
}

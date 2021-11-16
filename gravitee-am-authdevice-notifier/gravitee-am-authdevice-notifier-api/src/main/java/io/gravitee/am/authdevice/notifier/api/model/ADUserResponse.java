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
package io.gravitee.am.authdevice.notifier.api.model;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ADUserResponse {
    private final String tid;
    private final String state;
    private final boolean validated;

    public ADUserResponse(String tid, String state, boolean validated) {
        this.tid = tid;
        this.state = state;
        this.validated = validated;
    }

    public boolean isValidated() {
        return validated;
    }

    public String getState() {
        return state;
    }

    public String getTid() {
        return tid;
    }
}

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
package io.gravitee.am.repository.common;

import io.reactivex.rxjava3.core.Completable;

public interface ExpiredDataSweeper {

    default Completable purgeExpiredData() {
        return Completable.complete();
    }

    enum Target {
        access_tokens,
        authorization_codes,
        refresh_tokens,
        scope_approvals,
        request_objects,
        login_attempts,
        uma_permission_ticket,
        auth_flow_ctx,
        pushed_authorization_requests,
        ciba_auth_requests,
        user_activities,
        devices,
        events,
        audits,
        tokens
    }
}

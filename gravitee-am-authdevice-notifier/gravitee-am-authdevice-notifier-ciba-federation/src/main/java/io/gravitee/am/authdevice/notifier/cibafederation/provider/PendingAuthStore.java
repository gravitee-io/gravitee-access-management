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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PendingAuthStore {

    public record Pending(String tid, String state, String authReqId, int intervalSeconds,
                          long expiresAtEpochSeconds, String adHashPreSend, String callbackUrl) {
        public boolean isExpired(long nowEpochSeconds) { return nowEpochSeconds >= expiresAtEpochSeconds; }
    }

    private final Map<String, Pending> byTid = new ConcurrentHashMap<>();

    public void put(Pending p) { byTid.put(p.tid(), p); }
    public Pending get(String tid) { return byTid.get(tid); }
    public void remove(String tid) { byTid.remove(tid); }
}

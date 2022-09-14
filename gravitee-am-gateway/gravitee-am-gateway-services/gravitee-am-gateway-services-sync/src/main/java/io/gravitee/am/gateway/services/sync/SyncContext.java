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
package io.gravitee.am.gateway.services.sync;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SyncContext {

    private final long lastRefreshAt;

    private final long lastDelay;

    private long nextLastRefreshAt;

    public SyncContext() {
        this(-1, 0);
    }

    public SyncContext(long lastRefreshAt, long lastDelay) {
        this.lastRefreshAt = lastRefreshAt;
        this.lastDelay = lastDelay;
    }

    public long getLastRefreshAt() {
        return lastRefreshAt;
    }

    public long getLastDelay() {
        return lastDelay;
    }

    public long getNextLastRefreshAt() {
        return nextLastRefreshAt;
    }

    public void setNextLastRefreshAt(long nextLastRefreshAt) {
        this.nextLastRefreshAt = nextLastRefreshAt;
    }

    public long computeStartOffset() {
        return lastRefreshAt - lastDelay;
    }

    public SyncContext toNextOffset() {
        var lastRefreshAt = this.getNextLastRefreshAt();
        var lastDelay = System.currentTimeMillis() - this.getNextLastRefreshAt();
        return new SyncContext(lastRefreshAt, lastDelay);
    }
}

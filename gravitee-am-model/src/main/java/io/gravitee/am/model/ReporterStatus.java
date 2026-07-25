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
package io.gravitee.am.model;

/**
 * Whether a deployed reporter can be trusted to answer audit queries.
 * <p>
 * A reporter that cannot search at all is a separate question, answered by the plugin rather than the
 * instance. This is about the instance: an Elasticsearch reporter pointed at an index name its cluster
 * refuses is as deployed as a working one, and is the reporter every read goes to unless something
 * says otherwise.
 *
 * @author GraviteeSource Team
 */
public enum ReporterStatus {

    /**
     * Not usable yet, but nothing says it will not be. An Elasticsearch reporter whose cluster is
     * unreachable stays here and retries, which is deliberate: the migration window is meant to look
     * empty rather than fall back to the store the operator is migrating away from.
     */
    STARTING,

    /** Started, and answering queries. */
    READY,

    /**
     * Started and will not recover without a configuration change — a misconfiguration retrying cannot
     * fix. Reads must skip it, because the next candidate is the reporter that still has the history.
     */
    FAILED
}

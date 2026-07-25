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
package io.gravitee.am.reporter.elasticsearch.audit;

/**
 * The composable index template applied before any audit is written.
 * <p>
 * The template is not optional. {@code actor.attributes} and {@code target.attributes} are free-form
 * maps whose value types vary across audit events, so under dynamic mapping the first event whose
 * attribute value type differs from a previously seen one is rejected outright. Mapping both as
 * {@code enabled:false} stores them in {@code _source} without indexing them, so no value type can
 * ever conflict.
 *
 * @author GraviteeSource Team
 */
public final class AuditIndexTemplate {

    private AuditIndexTemplate() {
    }

    public static String name(String baseIndex) {
        return baseIndex + "-template";
    }

    /**
     * Elasticsearch refuses two composable templates whose patterns overlap at the same priority, and
     * AM runs one reporter per domain and organization where APIM runs one per deployment. Using the
     * index name's length as the priority makes overlapping names resolve deterministically: patterns
     * can only overlap when one index name is a prefix of the other, and the longer, more specific
     * name then always outranks the shorter one. It also lifts us clear of a pre-existing customer
     * template left at the default priority of zero.
     */
    public static int priority(String baseIndex) {
        return baseIndex.length();
    }

    /**
     * Extension point for per-version behaviour. Every supported distribution and major currently
     * takes the same body: it was run unchanged against Elasticsearch 7.17, Elasticsearch 9.3 and
     * OpenSearch 2.19, and composable templates predate the oldest supported version. Add a branch
     * here if a future variant ever needs a different one.
     */
    public static String bodyFor(ElasticsearchServerVersion version, String baseIndex) {
        return body(baseIndex);
    }

    public static String body(String baseIndex) {
        return """
                {
                  "index_patterns": ["%s"],
                  "priority": %d,
                  "template": {
                    "mappings": {
                      "dynamic_templates": [
                        {
                          "strings_as_keyword": {
                            "match_mapping_type": "string",
                            "mapping": { "type": "keyword", "ignore_above": 2048 }
                          }
                        }
                      ],
                      "properties": {
                        "id": { "type": "keyword" },
                        "transactionId": { "type": "keyword" },
                        "referenceType": { "type": "keyword" },
                        "referenceId": { "type": "keyword" },
                        "type": { "type": "keyword" },
                        "timestamp": { "type": "date", "format": "epoch_millis" },
                        "actor": {
                          "properties": {
                            "id": { "type": "keyword" },
                            "alternativeId": { "type": "keyword" },
                            "type": { "type": "keyword" },
                            "displayName": { "type": "keyword", "ignore_above": 2048 },
                            "referenceType": { "type": "keyword" },
                            "referenceId": { "type": "keyword" },
                            "attributes": { "type": "object", "enabled": false }
                          }
                        },
                        "target": {
                          "properties": {
                            "id": { "type": "keyword" },
                            "alternativeId": { "type": "keyword" },
                            "type": { "type": "keyword" },
                            "displayName": { "type": "keyword", "ignore_above": 2048 },
                            "referenceType": { "type": "keyword" },
                            "referenceId": { "type": "keyword" },
                            "attributes": { "type": "object", "enabled": false }
                          }
                        },
                        "accessPoint": {
                          "properties": {
                            "id": { "type": "keyword" },
                            "alternativeId": { "type": "keyword" },
                            "displayName": { "type": "keyword", "ignore_above": 2048 },
                            "ipAddress": { "type": "keyword" },
                            "userAgent": { "type": "keyword", "ignore_above": 2048 }
                          }
                        },
                        "outcome": {
                          "properties": {
                            "status": { "type": "keyword" },
                            "message": { "type": "text" }
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(AuditIndexNames.readPattern(baseIndex), priority(baseIndex));
    }
}

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
package io.gravitee.am.performance.management.provisioning

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gravitee.am.performance.actions.ProvisioningActions._
import io.gravitee.am.performance.commands.ManagementAPICalls._
import io.gravitee.am.performance.utils.SimulationSettings._

/**
 * Adds an Elasticsearch audit reporter to domains that already exist, so the same workload
 * simulation can be run against the database reporter and against Elasticsearch and the two
 * compared.
 *
 * Deliberately separate from CreateMultipleDomains: the standard provisioning has to keep producing
 * the dataset historical perf runs were measured on, so this is opt in and never changes the
 * baseline.
 *
 * Run it after CreateMultipleDomains and before the workload simulation. Audit reads move onto
 * Elasticsearch as soon as the reporter is enabled; writes go to both reporters, which is what makes
 * this a fair comparison of the write path's cost.
 *
 * Possible arguments:
 * - mng_url: base URL of the Management REST API (default: http://localhost:8093)
 * - mng_user / mng_password: management API credentials
 * - domain: the prefix of the domains to add the reporter to (default: gatling-domain)
 * - min_domain_index / number_of_domains: which domains to touch
 * - es_url: Elasticsearch endpoint the reporter writes to (default: http://localhost:9200)
 * - es_index: base index name (default: gatling-audit)
 * - es_bulk_actions: documents buffered before a bulk flush (default: 1000)
 * - es_flush_interval: flush interval in seconds (default: 5)
 */
class AddElasticsearchReporter extends Simulation {

  val httpProtocol = http
    .userAgentHeader("Gatling - Add Elasticsearch Reporter")
    .disableFollowRedirect

  val domainGenerator = multiDomainsFeeder(DATALOAD)

  val scn = scenario("Add Elasticsearch Reporter")
    .exec(login)
    .feed(domainGenerator)
    .doWhile("#{continueDomainCreation}")(
      exec(retrieveDomainId("#{domainName}"))
        .exec(createElasticsearchReporter)
        .feed(domainGenerator)
    )

  setUp(scn.inject(atOnceUsers(1)).protocols(httpProtocol))
}

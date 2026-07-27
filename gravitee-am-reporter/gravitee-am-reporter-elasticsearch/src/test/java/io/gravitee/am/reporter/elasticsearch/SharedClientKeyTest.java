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
package io.gravitee.am.reporter.elasticsearch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The key decides which reporters share a client, so it has to cover everything the client is built
 * from and nothing that is applied per reporter on top of it.
 *
 * @author GraviteeSource Team
 */
class SharedClientKeyTest {

    @Test
    void reporters_on_the_same_cluster_share_a_client() {
        assertThat(configuration().sharedClientKey())
                .isEqualTo(configuration().sharedClientKey());
    }

    @Test
    void reporters_writing_different_indices_to_one_cluster_still_share_a_client() {
        ElasticsearchReporterConfiguration first = configuration();
        first.setIndex("audits-one");
        first.setRolloverPeriod("weekly");
        first.setBulkActions(10);

        ElasticsearchReporterConfiguration second = configuration();
        second.setIndex("audits-two");
        second.setRolloverPeriod("monthly");
        second.setBulkActions(5000);

        assertThat(first.sharedClientKey()).isEqualTo(second.sharedClientKey());
    }

    @Test
    void a_different_endpoint_gets_its_own_client() {
        ElasticsearchReporterConfiguration other = configuration();
        other.setEndpoints(List.of("http://elsewhere:9200"));

        assertThat(other.sharedClientKey()).isNotEqualTo(configuration().sharedClientKey());
    }

    @Test
    void different_credentials_against_one_endpoint_get_their_own_client() {
        ElasticsearchReporterConfiguration other = configuration();
        other.setPassword("something-else");

        assertThat(other.sharedClientKey()).isNotEqualTo(configuration().sharedClientKey());
    }

    @Test
    void different_tls_material_gets_its_own_client() {
        ElasticsearchReporterConfiguration other = configuration();
        other.setSslKeystorePath("/etc/other.p12");

        assertThat(other.sharedClientKey()).isNotEqualTo(configuration().sharedClientKey());
    }

    @Test
    void a_different_request_timeout_gets_its_own_client() {
        ElasticsearchReporterConfiguration other = configuration();
        other.setRequestTimeout(60000L);

        assertThat(other.sharedClientKey()).isNotEqualTo(configuration().sharedClientKey());
    }

    @Test
    void a_different_proxy_gets_its_own_client() {
        ElasticsearchReporterConfiguration direct = configuration();

        ElasticsearchReporterConfiguration proxied = configuration();
        proxied.setProxyHost("proxy.internal");
        proxied.setProxyPort(3128);

        ElasticsearchReporterConfiguration elsewhere = configuration();
        elsewhere.setProxyHost("other-proxy.internal");
        elsewhere.setProxyPort(3128);

        assertThat(proxied.sharedClientKey()).isNotEqualTo(direct.sharedClientKey());
        assertThat(proxied.sharedClientKey()).isNotEqualTo(elsewhere.sharedClientKey());
    }

    @Test
    void different_proxy_credentials_get_their_own_client() {
        ElasticsearchReporterConfiguration proxied = configuration();
        proxied.setProxyHost("proxy.internal");
        proxied.setProxyPort(3128);

        ElasticsearchReporterConfiguration authenticated = configuration();
        authenticated.setProxyHost("proxy.internal");
        authenticated.setProxyPort(3128);
        authenticated.setProxyUsername("someone");
        authenticated.setProxyPassword("secret");

        assertThat(authenticated.sharedClientKey()).isNotEqualTo(proxied.sharedClientKey());
    }

    @Test
    void no_proxy_host_means_no_proxy() {
        assertThat(configuration().isProxyConfigured()).isFalse();

        ElasticsearchReporterConfiguration blank = configuration();
        blank.setProxyHost("   ");
        assertThat(blank.isProxyConfigured()).isFalse();

        ElasticsearchReporterConfiguration set = configuration();
        set.setProxyHost("proxy.internal");
        assertThat(set.isProxyConfigured()).isTrue();
    }

    private ElasticsearchReporterConfiguration configuration() {
        ElasticsearchReporterConfiguration configuration = new ElasticsearchReporterConfiguration();
        configuration.setEndpoints(List.of("http://localhost:9200"));
        configuration.setUsername("elastic");
        configuration.setPassword("changeme");
        configuration.setSslKeystoreType("pkcs12");
        configuration.setSslKeystorePath("/etc/audit.p12");
        return configuration;
    }
}

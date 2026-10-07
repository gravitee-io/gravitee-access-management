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
package io.gravitee.am.repository.encryption;

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.encryption.FieldEncryptor.EncryptionKey;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptingRepositoryProxyTest {

    private static final String CONFIGURATION = "{\"password\":\"s3cr3t\"}";
    private static final EncryptionKey KEY = new EncryptionKey("2025", "a-secret");
    private static final EncryptionKey NEXT_KEY = new EncryptionKey("2026", "another-secret");
    private static final FieldEncryptor ENCRYPTOR = FieldEncryptor.withKeys(List.of(KEY));

    private final InMemoryReporterRepository store = new InMemoryReporterRepository();
    private final ReporterRepository repository = wrap(store, KEY);

    @Test
    void should_store_encrypted_and_return_clear_text_on_create() {
        Reporter reporter = reporter("r1", CONFIGURATION);

        Reporter created = repository.create(reporter).blockingGet();

        assertThat(created.getConfiguration()).isEqualTo(CONFIGURATION);
        assertThat(store.stored("r1").getConfiguration()).startsWith("enc:2025:");
        assertThat(ENCRYPTOR.decrypt(store.stored("r1").getConfiguration())).isEqualTo(CONFIGURATION);
    }

    @Test
    void should_not_modify_the_caller_instance() {
        Reporter reporter = reporter("r1", CONFIGURATION);

        repository.create(reporter).blockingGet();
        repository.update(reporter).blockingGet();

        assertThat(reporter.getConfiguration()).isEqualTo(CONFIGURATION);
    }

    @Test
    void should_decrypt_maybe() {
        repository.create(reporter("r1", CONFIGURATION)).blockingGet();

        assertThat(repository.findById("r1").blockingGet().getConfiguration()).isEqualTo(CONFIGURATION);
    }

    @Test
    void should_decrypt_flowable() {
        repository.create(reporter("r1", CONFIGURATION)).blockingGet();
        repository.create(reporter("r2", CONFIGURATION)).blockingGet();

        assertThat(repository.findAll().toList().blockingGet())
                .extracting(Reporter::getConfiguration)
                .containsOnly(CONFIGURATION);
    }

    @Test
    void should_read_clear_text_stored_before_encryption() {
        store.create(reporter("legacy", CONFIGURATION)).blockingGet();

        assertThat(repository.findById("legacy").blockingGet().getConfiguration()).isEqualTo(CONFIGURATION);
    }

    @Test
    void should_pass_completable_through() {
        repository.create(reporter("r1", CONFIGURATION)).blockingGet();

        repository.delete("r1").blockingAwait();

        assertThat(store.stored("r1")).isNull();
    }

    @Test
    void should_propagate_repository_exceptions_unwrapped() {
        assertThatThrownBy(() -> repository.findInheritedFrom(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_decrypt_page_and_list_results() {
        IdentityProvider provider = new IdentityProvider();
        provider.setConfiguration(ENCRYPTOR.encrypt(CONFIGURATION));
        IdentityProvider other = new IdentityProvider();
        other.setConfiguration(ENCRYPTOR.encrypt(CONFIGURATION));
        EncryptedField<?> field = EncryptedFieldRegistry.forRepository(IdentityProviderRepository.class).orElseThrow();
        PagedRepository paged = (PagedRepository) EncryptingRepositoryProxy.wrap(
                new PagedRepository() {
                    @Override
                    public Single<Page<IdentityProvider>> page() {
                        return Single.just(new Page<>(List.of(provider), 0, 1));
                    }

                    @Override
                    public List<IdentityProvider> list() {
                        return List.of(other);
                    }
                }, field, ENCRYPTOR);

        assertThat(paged.page().blockingGet().getData()).extracting(IdentityProvider::getConfiguration).containsOnly(CONFIGURATION);
        assertThat(paged.list()).extracting(IdentityProvider::getConfiguration).containsOnly(CONFIGURATION);
    }

    @Test
    void should_not_wrap_without_secret() {
        Object bean = new EncryptingRepositoryBeanPostProcessor(environment(Map.of()))
                .postProcessAfterInitialization(store, "reporterRepository");

        assertThat(bean).isSameAs(store);
    }

    @Test
    void should_not_wrap_other_beans() {
        Object bean = new Object();

        Object processed = new EncryptingRepositoryBeanPostProcessor(
                keysEnvironment(KEY))
                .postProcessAfterInitialization(bean, "other");

        assertThat(processed).isSameAs(bean);
    }

    @Test
    void should_read_with_the_old_key_and_write_with_the_new_one() {
        repository.create(reporter("r1", CONFIGURATION)).blockingGet();
        ReporterRepository rotated = wrap(store, KEY, NEXT_KEY);

        Reporter read = rotated.findById("r1").blockingGet();
        assertThat(read.getConfiguration()).isEqualTo(CONFIGURATION);
        assertThat(store.stored("r1").getConfiguration()).startsWith("enc:2025:");

        rotated.update(read).blockingGet();
        assertThat(store.stored("r1").getConfiguration()).startsWith("enc:2026:");
        assertThat(rotated.findById("r1").blockingGet().getConfiguration()).isEqualTo(CONFIGURATION);
    }

    @Test
    void should_fail_to_read_a_value_encrypted_with_a_removed_key() {
        repository.create(reporter("r1", CONFIGURATION)).blockingGet();
        ReporterRepository withoutOldKey = wrap(store, NEXT_KEY);

        assertThatThrownBy(() -> withoutOldKey.findById("r1").blockingGet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[2025]");
    }

    @Test
    void should_delegate_equals_to_the_target() {
        assertThat(repository).isEqualTo(repository);
        assertThat(repository.equals(store)).isTrue();
    }

    private static ReporterRepository wrap(ReporterRepository target, EncryptionKey... keys) {
        return (ReporterRepository) new EncryptingRepositoryBeanPostProcessor(keysEnvironment(keys))
                .postProcessAfterInitialization(target, "reporterRepository");
    }

    private static StandardEnvironment keysEnvironment(EncryptionKey... keys) {
        Map<String, Object> properties = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            properties.put(EncryptionKeys.KEYS_PROPERTY + "[" + i + "].id", keys[i].id());
            properties.put(EncryptionKeys.KEYS_PROPERTY + "[" + i + "].secret", keys[i].secret());
        }
        return environment(properties);
    }

    private static StandardEnvironment environment(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }

    private static Reporter reporter(String id, String configuration) {
        Reporter reporter = new Reporter();
        reporter.setId(id);
        reporter.setConfiguration(configuration);
        return reporter;
    }

    public interface PagedRepository {
        Single<Page<IdentityProvider>> page();

        List<IdentityProvider> list();
    }

    /**
     * Mimics MongoReporterRepository: create/update return the received instance, while the
     * database keeps its own copy.
     */
    private static class InMemoryReporterRepository implements ReporterRepository {

        private final Map<String, Reporter> reporters = new HashMap<>();

        Reporter stored(String id) {
            return reporters.get(id);
        }

        @Override
        public Flowable<Reporter> findAll() {
            return Flowable.fromIterable(reporters.values()).map(Reporter::new);
        }

        @Override
        public Flowable<Reporter> findByReference(Reference reference) {
            return findAll();
        }

        @Override
        public Flowable<Reporter> findInheritedFrom(Reference parentReference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Maybe<Reporter> findById(String id) {
            return Maybe.fromOptional(Optional.ofNullable(reporters.get(id))).map(Reporter::new);
        }

        @Override
        public Single<Reporter> create(Reporter item) {
            reporters.put(item.getId(), new Reporter(item));
            return Single.just(item);
        }

        @Override
        public Single<Reporter> update(Reporter item) {
            return create(item);
        }

        @Override
        public Completable delete(String id) {
            return Completable.fromAction(() -> reporters.remove(id));
        }
    }
}

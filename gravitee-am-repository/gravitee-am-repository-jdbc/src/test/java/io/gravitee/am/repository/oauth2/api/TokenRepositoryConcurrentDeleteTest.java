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
package io.gravitee.am.repository.oauth2.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class TokenRepositoryConcurrentDeleteTest extends AbstractOAuthTest {

    private static final int ROUNDS = 5;
    private static final int TREES = 50;
    private static final int DEPTH = 5;
    private static final int BRANCH_FACTOR = 2;
    private static final int CROSS_LINK_EVERY = 3;
    private static final int INSERT_BATCH_SIZE = 500;
    private static final long RACE_TIMEOUT_SECONDS = 120;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private R2dbcEntityTemplate template;

    @Test
    public void shouldNotFailOnConcurrentRecursiveDeletes() {
        for (int round = 0; round < ROUNDS; round++) {
            String suffix = RandomString.generate().substring(0, 8);
            String domain = "domain-" + suffix;
            String client = "client-" + suffix;
            String subject = "user-" + suffix;

            List<String> roots = seedForest(suffix, domain, client, subject);
            List<Throwable> errors = raceDeletes(domain, subject, roots);

            List<Throwable> concurrencyFailures = errors.stream().filter(this::isConcurrencyFailure).toList();
            assertTrue("Round " + round + ": concurrent token revocation hit " + concurrencyFailures, concurrencyFailures.isEmpty());
            assertEquals("Round " + round + ": tokens left behind after revocation", 0L, countTokens(domain));
        }
    }

    private List<Throwable> raceDeletes(String domain, String subject, List<String> roots) {
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        List<Completable> deletes = new ArrayList<>();
        deletes.add(collectErrors(tokenRepository.deleteByDomainIdAndUserId(domain, UserId.internal(subject)), errors));
        roots.forEach(root -> deletes.add(collectErrors(tokenRepository.deleteByJti(root), errors)));
        boolean completed = Completable.merge(deletes).blockingAwait(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("Concurrent deletes did not complete within " + RACE_TIMEOUT_SECONDS + "s", completed);
        return errors;
    }

    private Completable collectErrors(Completable delete, List<Throwable> errors) {
        return delete.subscribeOn(Schedulers.io())
                .doOnError(errors::add)
                .onErrorComplete();
    }

    private boolean isConcurrencyFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause() == current ? null : current.getCause()) {
            if (current instanceof ConcurrencyFailureException) {
                return true;
            }
        }
        return false;
    }

    private List<String> seedForest(String suffix, String domain, String client, String subject) {
        List<String> roots = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        List<List<String>> levelByTree = new ArrayList<>();

        for (int tree = 0; tree < TREES; tree++) {
            String root = jti(suffix, tree, 0, 0);
            roots.add(root);
            rows.add(new String[]{root, null, null});
            levelByTree.add(List.of(root));
        }

        for (int depth = 1; depth < DEPTH; depth++) {
            List<List<String>> nextLevelByTree = new ArrayList<>();
            for (int tree = 0; tree < TREES; tree++) {
                List<String> parents = levelByTree.get(tree);
                List<String> crossTreeParents = levelByTree.get((tree + 1) % TREES);
                List<String> nextLevel = new ArrayList<>();
                int nodeIndex = 0;
                for (String parent : parents) {
                    for (int branch = 0; branch < BRANCH_FACTOR; branch++) {
                        String child = jti(suffix, tree, depth, nodeIndex);
                        String actor = nodeIndex % CROSS_LINK_EVERY == 0
                                ? crossTreeParents.get(nodeIndex % crossTreeParents.size())
                                : null;
                        rows.add(new String[]{child, parent, actor});
                        nextLevel.add(child);
                        nodeIndex++;
                    }
                }
                nextLevelByTree.add(nextLevel);
            }
            levelByTree = nextLevelByTree;
        }

        insertRows(rows, domain, client, subject);
        return roots;
    }

    private String jti(String suffix, int tree, int depth, int nodeIndex) {
        return suffix + "-" + tree + "-" + depth + "-" + nodeIndex;
    }

    private void insertRows(List<String[]> rows, String domain, String client, String subject) {
        for (int start = 0; start < rows.size(); start += INSERT_BATCH_SIZE) {
            List<String[]> batch = rows.subList(start, Math.min(start + INSERT_BATCH_SIZE, rows.size()));
            StringBuilder sql = new StringBuilder("INSERT INTO tokens (id, token, type, domain, client, subject, created_at, parent_jti_1, parent_jti_2) VALUES ");
            for (int i = 0; i < batch.size(); i++) {
                String[] row = batch.get(i);
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("('").append(row[0]).append("', '").append(row[0]).append("', 'ACCESS_TOKEN', '")
                        .append(domain).append("', '").append(client).append("', '").append(subject)
                        .append("', CURRENT_TIMESTAMP, ").append(literal(row[1])).append(", ").append(literal(row[2])).append(")");
            }
            template.getDatabaseClient().sql(sql.toString()).fetch().rowsUpdated().block();
        }
    }

    private String literal(String value) {
        return value == null ? "NULL" : "'" + value + "'";
    }

    private long countTokens(String domain) {
        Long count = template.getDatabaseClient()
                .sql("SELECT COUNT(*) FROM tokens WHERE domain = '" + domain + "'")
                .map(row -> ((Number) row.get(0)).longValue())
                .one()
                .block();
        return count == null ? 0L : count;
    }
}

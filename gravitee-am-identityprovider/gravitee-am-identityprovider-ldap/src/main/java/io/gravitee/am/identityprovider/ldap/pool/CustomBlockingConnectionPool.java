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
package io.gravitee.am.identityprovider.ldap.pool;

import org.ldaptive.DefaultConnectionFactory;
import org.ldaptive.pool.BlockingConnectionPool;
import org.ldaptive.pool.PoolConfig;
import org.ldaptive.pool.PooledConnectionProxy;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomBlockingConnectionPool extends BlockingConnectionPool {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private int maxRetries = DEFAULT_MAX_RETRIES;

    public CustomBlockingConnectionPool() {}

    public CustomBlockingConnectionPool(DefaultConnectionFactory cf) {
        super(cf);
    }

    public CustomBlockingConnectionPool(DefaultConnectionFactory cf, int maxRetries) {
        this(cf);
        this.maxRetries = maxRetries;
    }

    public CustomBlockingConnectionPool(PoolConfig pc, DefaultConnectionFactory cf) {
        super(pc, cf);
    }

    public CustomBlockingConnectionPool(PoolConfig pc, DefaultConnectionFactory cf, int maxRetryOnFailure) {
        this(pc, cf);
        this.maxRetries = maxRetries;
    }

    /**
     * Attempts to grow the pool to the supplied size. If the pool size is greater than or equal to the supplied size,
     * this method is a no-op.
     *
     * @param  size  to grow the pool to
     * @param  throwOnFailure  whether to throw illegal state exception
     *
     * @throws  IllegalStateException  if the pool cannot grow to the supplied size and {@link
     *                                 #createAvailableConnection(boolean)} throws
     */
    protected void grow(final int size, final boolean throwOnFailure) {
        logger.trace("waiting for pool lock to initialize pool {}", poolLock.getQueueLength());

        int count = 0;
        int failures = 0;
        poolLock.lock();
        try {
            IllegalStateException lastThrown = null;
            int currentPoolSize = active.size() + available.size();
            logger.debug("checking connection pool size >= {} for {}", size, this);
            while (currentPoolSize < size && count < size * 2 && failures < maxRetries) {
                try {
                    final PooledConnectionProxy pc = createAvailableConnection(throwOnFailure);
                    if (pc != null && getPoolConfig().isValidateOnCheckIn()) {
                        if (validate(pc.getConnection())) {
                            logger.trace("connection passed initialize validation: {}", pc);
                        } else {
                            logger.warn("connection failed initialize validation: {}", pc);
                            removeAvailableConnection(pc);
                        }
                    }
                } catch (IllegalStateException e) {
                    lastThrown = e;
                    failures++;
                }
                currentPoolSize = active.size() + available.size();
                count++;
            }
            if (lastThrown != null && currentPoolSize < size) {
                throw lastThrown;
            }
        } finally {
            poolLock.unlock();
        }
    }
}

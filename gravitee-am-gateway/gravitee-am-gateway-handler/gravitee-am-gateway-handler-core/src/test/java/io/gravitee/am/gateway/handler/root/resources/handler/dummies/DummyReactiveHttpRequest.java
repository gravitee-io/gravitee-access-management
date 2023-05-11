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

package io.gravitee.am.gateway.handler.root.resources.handler.dummies;

import io.netty.handler.codec.DecoderResult;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.WriteStream;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DummyReactiveHttpRequest extends HttpServerResponse {

    public DummyReactiveHttpRequest(io.vertx.core.http.HttpServerResponse delegate) {
        super(delegate);
    }

    @Override
    public Completable end() {
        final io.vertx.core.http.HttpServerResponse delegate = getDelegate();
        delegate.end();
        return super.end();
    }
}
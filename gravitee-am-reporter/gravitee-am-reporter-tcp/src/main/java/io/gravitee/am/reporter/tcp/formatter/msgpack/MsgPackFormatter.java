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
package io.gravitee.am.reporter.tcp.formatter.msgpack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.gravitee.am.reporter.tcp.formatter.AbstractFormatter;
import io.gravitee.am.reporter.tcp.formatter.ReportEntry;
import io.vertx.core.buffer.Buffer;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class MsgPackFormatter<T extends ReportEntry> extends AbstractFormatter<T> {

    private final ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());

    {
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
    }

    @Override
    public Buffer format0(T data) {
        try {
            return Buffer.buffer(mapper.writeValueAsBytes(data));
        } catch (JsonProcessingException e) {
            log.error("Unable to serialize audit entry to MessagePack", e);
            return null;
        }
    }
}

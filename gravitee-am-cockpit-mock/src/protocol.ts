/*
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

/**
 * gravitee-exchange V1 wire protocol.
 *
 * Each WebSocket frame is a `;;`-delimited envelope, NOT raw JSON:
 *
 *   t:<COMMAND|REPLY|UNKNOWN>;;et:<exchangeType>;;e:<json-serialized exchange>
 *
 * Mirrors io.gravitee.exchange.api.websocket.protocol.v1.V1ProtocolAdapter.
 * The `e:` payload may itself contain the `;;` separator, so on decode the
 * remainder after the `e:` marker is re-joined exactly like the Java adapter.
 */

const SEPARATOR = ';;';
const TYPE_PREFIX = 't:';
const EXCHANGE_TYPE_PREFIX = 'et:';
const EXCHANGE_PREFIX = 'e:';

export type ProtocolType = 'COMMAND' | 'REPLY' | 'UNKNOWN';

export interface DecodedFrame {
  protocolType: ProtocolType;
  /** The `et:` marker — e.g. HELLO, ORGANIZATION, USER. */
  exchangeType?: string;
  /** The parsed `e:` JSON exchange body (id/commandId/type/payload/...). */
  exchange?: any;
}

export interface EncodableFrame {
  protocolType: ProtocolType;
  exchangeType?: string;
  exchange?: unknown;
}

/** Build a V1 frame string from a protocol type, exchange type and exchange object. */
export function encodeFrame(frame: EncodableFrame): string {
  const parts: string[] = [];
  if (frame.protocolType) {
    parts.push(`${TYPE_PREFIX}${frame.protocolType}`);
  }
  if (frame.exchangeType) {
    parts.push(`${EXCHANGE_TYPE_PREFIX}${frame.exchangeType}`);
  }
  if (frame.exchange !== undefined && frame.exchange !== null) {
    parts.push(`${EXCHANGE_PREFIX}${JSON.stringify(frame.exchange)}`);
  }
  return parts.join(SEPARATOR);
}

/** Parse a V1 frame string into its protocol type, exchange type and exchange object. */
export function decodeFrame(raw: string): DecodedFrame {
  const parts = raw.split(SEPARATOR);
  const result: DecodedFrame = { protocolType: 'UNKNOWN' };

  for (let i = 0; i < parts.length; i++) {
    const part = parts[i];
    if (part.startsWith(TYPE_PREFIX)) {
      const value = part.substring(TYPE_PREFIX.length);
      result.protocolType = value === 'COMMAND' || value === 'REPLY' ? value : 'UNKNOWN';
    } else if (part.startsWith(EXCHANGE_TYPE_PREFIX)) {
      result.exchangeType = part.substring(EXCHANGE_TYPE_PREFIX.length);
    } else if (part.startsWith(EXCHANGE_PREFIX)) {
      // The exchange JSON may contain `;;`; re-join the remaining parts.
      const remainder = parts.slice(i).join(SEPARATOR);
      const json = remainder.substring(EXCHANGE_PREFIX.length);
      try {
        result.exchange = JSON.parse(json);
      } catch {
        result.exchange = json;
      }
      break;
    }
  }
  return result;
}

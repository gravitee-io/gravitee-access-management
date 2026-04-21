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

import * as net from 'net';

const DEFAULT_TCP_WAIT_TIMEOUT_MS = 20000;
const DEFAULT_TCP_ASSERT_NO_WINDOW_MS = 5000;

export interface TcpAuditPayload {
  event_type?: string;
  referenceType?: string;
  referenceId?: string;
  [key: string]: unknown;
}

export interface TcpWaitOptions {
  timeoutMs?: number;
  predicate: (msg: TcpAuditPayload) => boolean;
}

export interface TcpAssertNoOptions {
  windowMs?: number;
  predicate: (msg: TcpAuditPayload) => boolean;
}

export interface TcpServer {
  /** OS-assigned port the server is listening on. Pass this to the reporter config. */
  readonly port: number;
  /**
   * Calls trigger(), then resolves with the first audit message matching predicate.
   * Rejects if no match arrives within timeoutMs.
   */
  waitForMessage(options: TcpWaitOptions, trigger: () => Promise<void>): Promise<TcpAuditPayload>;
  /**
   * Calls trigger(), waits windowMs, then asserts no audit message matched predicate.
   */
  assertNoMessage(options: TcpAssertNoOptions, trigger: () => Promise<void>): Promise<void>;
  close(): Promise<void>;
}

/**
 * Starts a TCP server on a random OS-assigned port.
 *
 * The TCP reporter sends each event as a single-line compressed JSON blob
 * followed by CRLF. This server buffers incoming bytes, splits on line
 * endings, and JSON-parses each non-empty line.
 *
 * Obtain the port first, then pass it to the reporter configuration so the
 * reporter connects back to this server.
 */
export async function startTcpServer(): Promise<TcpServer> {
  const handlers: Array<(payload: TcpAuditPayload) => void> = [];
  const activeSockets = new Set<net.Socket>();

  const server = net.createServer((socket) => {
    activeSockets.add(socket);
    socket.on('close', () => activeSockets.delete(socket));

    let buf = '';
    socket.on('data', (chunk) => {
      buf += chunk.toString('utf8');
      const lines = buf.split(/\r?\n/);
      buf = lines.pop() ?? '';
      for (const line of lines) {
        if (!line.trim()) continue;
        try {
          const payload: TcpAuditPayload = JSON.parse(line);
          for (const handler of handlers) {
            handler(payload);
          }
        } catch {
          // ignore malformed lines
        }
      }
    });
  });

  await new Promise<void>((resolve, reject) => {
    server.listen(0, '0.0.0.0', () => resolve());
    server.on('error', reject);
  });

  const port = (server.address() as net.AddressInfo).port;

  const close = (): Promise<void> =>
    new Promise((resolve) => {
      for (const socket of activeSockets) {
        socket.destroy();
      }
      server.close(() => resolve());
    });

  return {
    port,

    async waitForMessage(options: TcpWaitOptions, trigger: () => Promise<void>): Promise<TcpAuditPayload> {
      const { timeoutMs = DEFAULT_TCP_WAIT_TIMEOUT_MS, predicate } = options;

      let resolveResult: (v: TcpAuditPayload) => void;
      let rejectResult: (e: unknown) => void;
      const result = new Promise<TcpAuditPayload>((res, rej) => {
        resolveResult = res;
        rejectResult = rej;
      });

      const removeHandler = () => {
        const idx = handlers.indexOf(handler);
        if (idx !== -1) handlers.splice(idx, 1);
      };

      const handler = (payload: TcpAuditPayload) => {
        if (predicate(payload)) {
          clearTimeout(timer);
          removeHandler();
          resolveResult(payload);
        }
      };
      handlers.push(handler);

      const timer = setTimeout(() => {
        removeHandler();
        rejectResult(new Error(`Timeout: no TCP message matching predicate within ${timeoutMs}ms`));
      }, timeoutMs);

      try {
        await trigger();
      } catch (err) {
        clearTimeout(timer);
        removeHandler();
        rejectResult(err);
      }

      return result;
    },

    async assertNoMessage(options: TcpAssertNoOptions, trigger: () => Promise<void>): Promise<void> {
      const { windowMs = DEFAULT_TCP_ASSERT_NO_WINDOW_MS, predicate } = options;

      let matched: TcpAuditPayload | null = null;
      const handler = (payload: TcpAuditPayload) => {
        if (predicate(payload)) {
          matched = payload;
        }
      };
      handlers.push(handler);

      try {
        await trigger();
        await new Promise<void>((r) => setTimeout(r, windowMs));
      } finally {
        const idx = handlers.indexOf(handler);
        if (idx !== -1) handlers.splice(idx, 1);
      }

      if (matched !== null) {
        throw new Error(`Expected no TCP message matching predicate, but received one: ${JSON.stringify(matched)}`);
      }
    },

    close,
  };
}

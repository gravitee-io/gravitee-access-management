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
 * Mock cockpit WebSocket server for testing Gravitee AM's cockpit integration.
 *
 * AM is the WebSocket *client*: it dials into `ws://<host>:<port>/exchange/controller`.
 * This tool impersonates cockpit — it auto-answers AM's HELLO handshake, then lets
 * you inject commands/replies toward AM over HTTP and read back everything AM emits.
 *
 *   POST /_control/send        send a COMMAND (default) or REPLY toward AM
 *   GET  /_control/queue       pop the FIFO head of AM's messages (204 when empty)
 *   GET  /_control/queue/peek  non-destructive snapshot of the queue
 *   GET  /_control/status      connection + installation + queue summary
 */

import { createServer, IncomingMessage, ServerResponse } from 'node:http';
import { randomUUID } from 'node:crypto';
import { WebSocketServer, WebSocket } from 'ws';
import { decodeFrame, encodeFrame, ProtocolType } from './protocol';
import { loadInstallationState, InstallationState } from './state';
import { ReplyQueue, QueueEntry } from './queue';

interface Config {
  port: number;
  wsPath: string;
  controlPrefix: string;
  stateFile?: string;
  installationId?: string;
  installationStatus?: string;
  installationType?: string;
}

function parseArgs(argv: string[]): Config {
  const cfg: Config = {
    port: 8085,
    wsPath: '/exchange/controller',
    controlPrefix: '/_control',
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    const next = (): string => {
      const value = argv[++i];
      if (value === undefined) {
        console.error(`[args] missing value for ${arg}`);
        process.exit(1);
      }
      return value;
    };
    switch (arg) {
      case '--port': {
        const raw = next();
        const port = Number(raw);
        if (!Number.isInteger(port) || port <= 0) {
          console.error(`[args] invalid --port value: ${raw}`);
          process.exit(1);
        }
        cfg.port = port;
        break;
      }
      case '--ws-path':
        cfg.wsPath = next();
        break;
      case '--control-prefix':
        cfg.controlPrefix = next();
        break;
      case '--state-file':
        cfg.stateFile = next();
        break;
      case '--installation-id':
        cfg.installationId = next();
        break;
      case '--installation-status':
        cfg.installationStatus = next();
        break;
      case '--installation-type':
        cfg.installationType = next();
        break;
      case '--help':
      case '-h':
        printUsage();
        process.exit(0);
        break;
      default:
        console.warn(`[args] ignoring unknown argument: ${arg}`);
    }
  }
  return cfg;
}

function printUsage(): void {
  console.log(`gravitee-am-cockpit-mock

Usage: tsx src/server.ts [options]

  --port <n>                   HTTP + WebSocket port (default 8085)
  --ws-path <path>             WebSocket upgrade path (default /exchange/controller)
  --control-prefix <path>      REST control-plane prefix (default /_control)
  --state-file <path>          persist installation identity to this JSON file
  --installation-id <id>       override installationId (else generated)
  --installation-status <s>    override installationStatus (default ACCEPTED)
  --installation-type <t>      override installationType (echoed only; inert on AM)
`);
}

function log(scope: string, message: string): void {
  console.log(`${new Date().toISOString()} [${scope}] ${message}`);
}

function main(): void {
  const cfg = parseArgs(process.argv.slice(2));
  const installation = loadInstallationState(cfg);
  const queue = new ReplyQueue();

  /** The single active AM connection (last-writer-wins on reconnect). */
  let activeSocket: WebSocket | null = null;

  const httpServer = createServer((req, res) => handleHttp(req, res));
  const wss = new WebSocketServer({ noServer: true });

  httpServer.on('upgrade', (req, socket, head) => {
    const path = (req.url || '').split('?')[0];
    if (path !== cfg.wsPath) {
      socket.destroy();
      return;
    }
    wss.handleUpgrade(req, socket, head, (ws) => wss.emit('connection', ws, req));
  });

  wss.on('connection', (ws: WebSocket, req: IncomingMessage) => {
    if (activeSocket && activeSocket.readyState === WebSocket.OPEN) {
      log('ws', 'new AM connection; closing the previous one');
      activeSocket.close(1000, 'replaced by a new connection');
    }
    activeSocket = ws;
    log('ws', `AM connected from ${req.socket.remoteAddress}`);

    ws.on('message', (data) => handleAmFrame(data.toString()));
    ws.on('close', (code) => {
      if (activeSocket === ws) activeSocket = null;
      log('ws', `AM disconnected (code ${code})`);
    });
    ws.on('error', (err) => log('ws', `socket error: ${err.message}`));
  });

  /** Handle one inbound V1 frame from AM. */
  function handleAmFrame(raw: string): void {
    const frame = decodeFrame(raw);
    const exchangeType = frame.exchangeType ?? frame.exchange?.type;

    // Auto-handle the HELLO handshake so the link comes up; keep it off the queue.
    if (frame.protocolType === 'COMMAND' && exchangeType === 'HELLO') {
      replyToHello(frame.exchange?.id);
      return;
    }

    const entry: QueueEntry = {
      protocolType: frame.protocolType,
      type: exchangeType,
      commandId: frame.exchange?.commandId ?? frame.exchange?.id,
      commandStatus: frame.exchange?.commandStatus,
      payload: frame.exchange?.payload,
      errorDetails: frame.exchange?.errorDetails,
      receivedAt: new Date().toISOString(),
    };
    queue.push(entry);
    log('ws', `queued ${entry.protocolType} ${entry.type ?? '?'} (queue size ${queue.size})`);
  }

  function replyToHello(commandId: string | undefined): void {
    const payload: Record<string, unknown> = {
      // targetId lives on the base exchange HelloReplyPayload (distinct from
      // installationId below, which is cockpit's own field). AM's channel reads
      // it via handleHelloCommand() and registers its connector under this id
      // as a non-null map key — omitting it causes a NullPointerException in
      // DefaultExchangeConnectorManager.register and the connector never starts.
      targetId: installation.installationId,
      installationId: installation.installationId,
      installationStatus: installation.installationStatus,
    };
    if (installation.installationType) {
      payload.installationType = installation.installationType;
    }
    const frame = encodeFrame({
      protocolType: 'REPLY',
      exchangeType: 'HELLO',
      exchange: {
        type: 'HELLO',
        commandId,
        commandStatus: 'SUCCEEDED',
        payload,
      },
    });
    sendFrame(frame);
    log('ws', `auto-replied HELLO (installationId=${installation.installationId}, status=${installation.installationStatus})`);
  }

  /**
   * Send a V1 frame to AM as a BINARY WebSocket message. The gravitee-exchange
   * channel writes/reads binary frames (Vert.x Buffer); a text frame trips AM's
   * textMessageHandler and the connection is closed with code 1003.
   *
   * `send` can throw synchronously if the socket is closing/closed; since this
   * runs inside the WS message handler and HTTP request handlers, an uncaught
   * throw here would crash the process.
   */
  function sendFrame(frame: string): void {
    try {
      activeSocket?.send(Buffer.from(frame, 'utf-8'), { binary: true });
    } catch (err) {
      log('ws', `failed to send frame: ${(err as Error).message}`);
    }
  }

  function handleHttp(req: IncomingMessage, res: ServerResponse): void {
    const path = (req.url || '').split('?')[0];

    if (req.method === 'POST' && path === `${cfg.controlPrefix}/send`) {
      return handleSend(req, res);
    }
    if (req.method === 'GET' && path === `${cfg.controlPrefix}/queue`) {
      return handlePop(res);
    }
    if (req.method === 'GET' && path === `${cfg.controlPrefix}/queue/peek`) {
      return sendJson(res, 200, queue.peek());
    }
    if (req.method === 'GET' && path === `${cfg.controlPrefix}/status`) {
      return sendJson(res, 200, {
        connected: !!activeSocket && activeSocket.readyState === WebSocket.OPEN,
        installation,
        queueSize: queue.size,
      });
    }
    sendJson(res, 404, { error: 'not found' });
  }

  function handlePop(res: ServerResponse): void {
    const head = queue.pop();
    if (!head) {
      res.writeHead(204);
      res.end();
      return;
    }
    sendJson(res, 200, head);
  }

  function handleSend(req: IncomingMessage, res: ServerResponse): void {
    readBody(req)
      .then((body) => {
        if (!activeSocket || activeSocket.readyState !== WebSocket.OPEN) {
          return sendJson(res, 409, { error: 'no AM connection' });
        }
        const type: string | undefined = body.type;
        if (!type) {
          return sendJson(res, 400, { error: 'missing "type"' });
        }
        const protocolType: ProtocolType = body.protocolType === 'REPLY' ? 'REPLY' : 'COMMAND';

        if (protocolType === 'REPLY') {
          if (!body.commandId) {
            return sendJson(res, 400, { error: 'REPLY requires "commandId"' });
          }
          const exchange: Record<string, unknown> = {
            type,
            commandId: body.commandId,
            commandStatus: body.commandStatus ?? 'SUCCEEDED',
          };
          if (body.payload !== undefined) exchange.payload = body.payload;
          if (body.errorDetails !== undefined) exchange.errorDetails = body.errorDetails;
          sendFrame(encodeFrame({ protocolType, exchangeType: type, exchange }));
          log('http', `sent REPLY ${type} for commandId=${body.commandId}`);
          return sendJson(res, 200, { ok: true });
        }

        const id: string = body.id || randomUUID();
        const exchange: Record<string, unknown> = { id, type };
        if (body.payload !== undefined) exchange.payload = body.payload;
        if (body.replyTimeoutMs !== undefined) exchange.replyTimeoutMs = body.replyTimeoutMs;
        sendFrame(encodeFrame({ protocolType, exchangeType: type, exchange }));
        log('http', `sent COMMAND ${type} (id=${id})`);
        return sendJson(res, 200, { id });
      })
      .catch((err) => sendJson(res, 400, { error: `invalid request: ${err.message}` }));
  }

  httpServer.listen(cfg.port, () => {
    log('boot', `listening on http://localhost:${cfg.port}`);
    log('boot', `  WebSocket : ws://localhost:${cfg.port}${cfg.wsPath}`);
    log('boot', `  control   : ${cfg.controlPrefix}/send | /queue | /queue/peek | /status`);
    log('boot', `  installation: id=${installation.installationId} status=${installation.installationStatus}${installation.installationType ? ` type=${installation.installationType}` : ''}`);
    if (cfg.stateFile) log('boot', `  state file: ${cfg.stateFile}`);
    log('boot', `Point AM at it: cloud.connector.ws.endpoints=http://localhost:${cfg.port} + cloud.enabled=true`);
  });
}

function readBody(req: IncomingMessage): Promise<any> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on('data', (chunk) => chunks.push(chunk as Buffer));
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf-8').trim();
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch (err) {
        reject(err as Error);
      }
    });
    req.on('error', reject);
  });
}

function sendJson(res: ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body);
  res.writeHead(status, { 'content-type': 'application/json' });
  res.end(payload);
}

main();

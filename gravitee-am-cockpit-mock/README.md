# gravitee-am-cockpit-mock

A small standalone tool that **impersonates cockpit** so you can test Gravitee AM's
WebSocket command integration end to end, without a real cockpit.

AM is the WebSocket **client**: it dials *out* to cockpit at
`<host>:<port>/exchange/controller`, upgrading an HTTP(S) connection to WebSocket.
This tool is the server AM connects into. It auto-answers AM's `HELLO` handshake,
lets you **inject** commands/replies toward AM over plain HTTP, and **queues**
everything AM sends back so you can pop it with a GET.

> **Endpoint scheme:** AM's endpoint configuration takes an `http://`/`https://` URL
> (it builds a `java.net.URL`, which has no handler for a bare `ws`/`wss` scheme) and
> upgrades it to a WebSocket connection internally. Configure AM with `http://`, not `ws://`.

## How it works

- Speaks the gravitee-exchange **V1** wire protocol:
  `t:<COMMAND|REPLY>;;et:<type>;;e:<json>` — you only ever provide `type` + `payload`
  as JSON; the framing is hidden.
- On connect, AM sends a `HELLO` command and blocks on a `HELLO` reply. The tool
  answers it automatically with a `SUCCEEDED` reply carrying the configured
  installation identity, so the link comes up. `HELLO` is **not** placed on the queue.
- Every other frame AM emits — replies to your commands **and** commands AM initiates
  on its own — goes onto a single FIFO queue, tagged with `protocolType`.

## Install & run

Requires Node.js >= 20.

```bash
cd gravitee-am-cockpit-mock
npm install
npm start                       # listens on http://localhost:8085
```

Options:

| Flag | Default | Description |
|------|---------|-------------|
| `--port <n>` | `8085` | HTTP + WebSocket port |
| `--ws-path <path>` | `/exchange/controller` | WebSocket upgrade path |
| `--control-prefix <path>` | `/_control` | REST control-plane prefix |
| `--state-file <path>` | *(none)* | persist installation identity to JSON and reload on restart |
| `--installation-id <id>` | *(generated UUID)* | override the installation id |
| `--installation-status <s>` | `ACCEPTED` | override the installation status |
| `--installation-type <t>` | *(none)* | echoed as an extra field only — **inert on AM** (AM does not read it from the HELLO reply) |

Example with stable, persisted identity:

```bash
npm start -- --port 8085 --state-file ./cockpit-state.json --installation-status ACCEPTED
```

## Run as a container

```bash
docker build -t gravitee-am-cockpit-mock:local .

# ephemeral identity
docker run --rm -p 8085:8085 gravitee-am-cockpit-mock:local

# stable identity persisted to a mounted volume
docker run --rm -p 8085:8085 -v "$PWD/data:/data" \
  gravitee-am-cockpit-mock:local --state-file /data/state.json
```

Flags go after the image name (they are appended to the entrypoint).

## Use in the local-stack

Add the mock as a service in `docker/local-stack/dev/docker-compose.yml` (same compose
network as `management`), then tell AM's management API to connect to it:

```yaml
  cockpit-mock:
    build:
      context: ../../../gravitee-am-cockpit-mock
    command: ["--state-file", "/data/state.json"]
    volumes:
      - cockpit-mock-state:/data
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8085/_control/status"]
      interval: 5s
      timeout: 2s
      retries: 10
    ports:
      - 8085:8085

volumes:
  cockpit-mock-state:
```

Point AM at it by adding these to the `management` service `environment:` (and `gateway`
if you want the gateway's connector too):

```yaml
      - GRAVITEE_CLOUD_ENABLED=true
      - GRAVITEE_CLOUD_CONNECTOR_WS_ENDPOINTS_0=http://cockpit-mock:8085
```

Inside the compose network AM reaches the mock at `http://cockpit-mock:8085` (upgraded to
WebSocket internally); from your host the control API stays on `http://localhost:8085`.

> **Requires the cockpit/cloud connector plugin** to be present in the AM image — the
> connection is only attempted when that plugin is loaded and `cloud.enabled=true`.
> Legacy config keys `GRAVITEE_COCKPIT_ENABLED` / `GRAVITEE_COCKPIT_WS_ENDPOINTS_0` also work.

## Point AM at it

In AM's `gravitee.yml` (or via env), plain `http://`, no TLS — see the endpoint-scheme
note above:

```yaml
cloud:
  enabled: true
  connector:
    ws:
      endpoints:
        - http://localhost:8085
```

(Legacy keys `cockpit.enabled` / `cockpit.ws.endpoints` also work.)

## Control API

### Send a command toward AM — `POST /_control/send`

Fire-and-forget. Returns the generated command `id`; AM's reply arrives later on the queue.

```bash
curl -sX POST localhost:8085/_control/send \
  -H 'content-type: application/json' \
  -d '{ "type": "ORGANIZATION", "payload": { "id": "org-1", "name": "Acme" } }'
# -> { "id": "3f1c...-generated-uuid" }
```

### Reply to an AM-initiated command — `POST /_control/send`

When AM initiates a command toward cockpit, it appears on the queue as a `COMMAND`
entry with a `commandId`. Answer it by POSTing a `REPLY` that references that id:

```bash
curl -sX POST localhost:8085/_control/send \
  -H 'content-type: application/json' \
  -d '{ "protocolType": "REPLY", "type": "V4_API", "commandId": "<id-from-queue>", "commandStatus": "SUCCEEDED", "payload": {} }'
# -> { "ok": true }
```

`commandStatus` defaults to `SUCCEEDED`; use `ERROR` (with optional `errorDetails`) to
exercise AM's failure paths.

### Pop AM's next message — `GET /_control/queue`

FIFO. Removes and returns the head; **204** when empty.

```bash
curl -i localhost:8085/_control/queue
# 200 { "protocolType": "REPLY", "type": "ORGANIZATION", "commandId": "3f1c...",
#       "commandStatus": "SUCCEEDED", "payload": { ... }, "receivedAt": "2026-07-13T..." }
```

### Inspect without consuming — `GET /_control/queue/peek`

Returns the full queue as an array, non-destructively.

### Connection status — `GET /_control/status`

```bash
curl -s localhost:8085/_control/status
# { "connected": true, "installation": { ... }, "queueSize": 0 }
```

## Notes

- **Single active connection.** One AM at a time; a reconnect (AM restart) takes over
  the slot. `POST /_control/send` returns `409` when no AM is connected.
- **`installationType` is inert.** AM's `HelloReplyAdapter` reads only `installationId`
  and `installationStatus` from the reply. `installationType` is accepted and persisted
  for convenience and sent as an extra field, but AM ignores it.
- **HELLO reply also carries `targetId`.** Distinct from `installationId` — it's a field
  on the base exchange `HelloReplyPayload` that AM's channel uses to key its connector
  registry. The mock sets it to the same value as `installationId`; omitting it causes a
  `NullPointerException` in AM's `DefaultExchangeConnectorManager` and the connector fails
  to start.
- The `type` you send must be a cockpit command type AM understands
  (e.g. `ORGANIZATION`, `ENVIRONMENT`, `MEMBERSHIP`, `USER`, `INSTALLATION`, `V4_API`, …);
  unknown types are handled by AM as an unknown command.

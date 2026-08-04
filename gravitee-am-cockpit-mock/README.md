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
| `--sso-private-key <path>` | *(none)* | PEM private key that signs `/_control/sso-token`; without it that route returns 501 |

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

### Read AM's HELLO — `GET /_control/hello`

The handshake is answered automatically and never reaches the queue, so this is the only way to see
what AM announced itself with. **204** until AM connects; overwritten on each reconnect.

```bash
curl -s localhost:8085/_control/hello
# { "commandId": "...", "receivedAt": "2026-07-30T...",
#   "payload": { "installationType": "managed", "node": { ... },
#                "accessPointsTemplate": { "ENVIRONMENT": [ { "host": "{environment}.{organization}...",
#                                                             "target": "GATEWAY", "secured": true } ] },
#                "additionalInformation": { "API_URL": "...", "UI_URL": "..." } } }
```

`accessPointsTemplate` is only populated by a managed installation; a standalone one still sends the
field, as an empty object. Note this `installationType` is AM's own (command direction) — not the same
field as the `--installation-type` flag, which rides on the reply and is inert.

### Mint an SSO token — `POST /_control/sso-token`

Cockpit signs users into AM out of band: it mints a short-lived RS512 JWT and redirects the browser to
`/management/auth/cockpit?token=<jwt>`. This reproduces that token — same claims and signing parameters
as cockpit's own `JWTService` (`kid=cockpit`, `iss=https://gravitee.cockpit`, 10 second TTL).

```bash
curl -sX POST localhost:8085/_control/sso-token \
  -H 'content-type: application/json' \
  -d '{ "sub": "cockpit-user-1", "org": "org-1", "env": "env-1" }'
# -> { "token": "eyJhbGciOiJSUzUxMiIsImtpZCI6ImNvY2twaXQi..." }
```

`sub` and `org` are required; `env` and `ttlSeconds` are optional. No AM connection is needed — this is
the redirect path, not a command.

AM verifies the signature with the public key of the certificate under alias `cockpit-client` in the
keystore at `cloud.connector.ws.ssl.keystore.path`, so `--sso-private-key` must be that certificate's
private key. The local stack wires both from `docker/local-stack/dev/cockpit/`.

> Cockpit does **not** set `preferred_username`, and neither does this endpoint. AM resolves the user by
> `sub` (as external id) plus source `cockpit`, so the `USER` command must be acknowledged first —
> otherwise AM creates a null-username account with no role.

### Connection status — `GET /_control/status`

```bash
curl -s localhost:8085/_control/status
# { "connected": true, "installation": { ... }, "queueSize": 0 }
```

## Logging in when there is no inline admin

A managed cloud installation registers no `security.providers[*]` identity provider, so `admin/adminadmin`
against `DEFAULT` does not exist. Two ways in, both after provisioning an organization, a user, an
organization membership and an environment (the SSO endpoint rejects a sign-in whose `env` is missing):

**Cockpit SSO — the real path.** Mint a token, then open it in a browser to land on the console, or
read the cookie for an API token:

```bash
TOKEN=$(curl -sX POST localhost:8085/_control/sso-token -H 'content-type: application/json' \
  -d '{"sub":"<cockpit-user-id>","org":"<org>","env":"<env>","redirectUri":"http://localhost:4200"}' | jq -r .token)
open "http://localhost:8093/management/auth/cockpit?token=$TOKEN"
```

(`redirectUri` is a local-stack concession, not something real Cockpit sends.)

**A password user for dev.** The Cockpit-provisioned owner itself can never do this: it is stored with
`source=cockpit` and no password, and no `cockpit` identity provider is registered to authenticate it.
The signed token above is its only credential.

Create a separate organization user instead. `OrganizationUserServiceImpl` forces `source=gravitee`, and
the `gravitee` provider is registered for every installation and offered to every organization, so a
password login works with no identity provider setup at all. Using a bearer token obtained above:

```bash
# create the user; the response's "id" is the member id below
curl -X POST "$AM/management/organizations/$ORG/users" -H "Authorization: Bearer $BEARER" \
  -H 'content-type: application/json' \
  -d '{"username":"admin","password":"AdminAdmin1!","firstName":"Dev","lastName":"Admin","email":"dev.admin@example.com"}'

# it lands on ORGANIZATION_USER by default; promote it (role id from GET .../roles)
curl -X POST "$AM/management/organizations/$ORG/members" -H "Authorization: Bearer $BEARER" \
  -H 'content-type: application/json' \
  -d '{"memberId":"<user id>","memberType":"USER","role":"<ORGANIZATION_OWNER role id>"}'
```

`admin` then works against `POST /management/auth/token?org=<org>` and on the console login page at
`?org=<org>`. Use `ORGANIZATION_OWNER`, not `ORGANIZATION_PRIMARY_OWNER` — the latter is already held by
the Cockpit-provisioned owner, and `MembershipServiceImpl` allows only one per organization.

Unlike the old `gravitee.yml` inline provider this user is persisted and belongs to a single
organization, so it is not a cross-tenant back door.

## Running the mock outside the local stack

`/_control/sso-token` needs `--sso-private-key`, and AM needs the matching certificate. The local stack
wires both from `docker/local-stack/dev/cockpit/`; reuse the same files when running by hand:

```bash
npm start -- --sso-private-key ../docker/local-stack/dev/cockpit/cockpit-key.pem
```

and start AM with:

```
cloud.connector.ws.ssl.keystore.type=PKCS12
cloud.connector.ws.ssl.keystore.path=<repo>/docker/local-stack/dev/cockpit/cockpit-truststore.p12
cloud.connector.ws.ssl.keystore.password=cockpit
```

Everything else — commands, queue, HELLO — works without either.

## Postman collection

Import `postman/gravitee-am-cockpit-mock.postman_collection.json` for ready-made requests:
status/queue inspection, every supported command type with valid example payloads, REPLY
templates for AM-initiated commands, and management API calls to verify the results.
Command ids are chained between requests automatically via collection variables.

`Commands → AM > License changes (AM-7237)` walks an organization license through
create / no-op / update / delete — the only write path, since the management API exposes
the license read-only. Set `{{licenseB64}}` to `base64 -i <your>.key | tr -d '\n'` first,
then read the result with `List organization license audits`, which asserts the audits
exist and that the raw license appears nowhere in them.

Run the `Commands → AM` requests first — ORGANIZATION, USER, both MEMBERSHIPs, ENVIRONMENT — then
`Sign in (cockpit SSO)` to populate `{{token}}` for everything under `Verify in AM`. Every request
targets `{{orgId}}`, an organization the collection provisions itself; there is no DEFAULT to fall back
on in managed cloud. A Cockpit-created organization does get its own audit reporter, so the license
audits do land in `reporter_audits_{{orgId}}`.

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

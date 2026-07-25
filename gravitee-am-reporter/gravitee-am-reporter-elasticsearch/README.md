# Elasticsearch audit reporter

Writes AM audit records to an Elasticsearch or OpenSearch cluster and reads them back for the
console's audit and analytics screens. Unlike APIM's reporter, this one is read *and* write: it must
be installed in **both** the gateway (which writes audits) and the management API (which reads them
back), or the feature is only half there.

## Supported servers

Elasticsearch and OpenSearch, at the minors listed in the Gravitee platform compatibility matrix.
The reporter detects the distribution and major version at startup and refuses to run against
anything older than Elasticsearch 7 or OpenSearch 1.

Be aware of what has actually been exercised: the reporter has been run against real
**Elasticsearch 7.17, Elasticsearch 9.3 and OpenSearch 2.19** servers, and against Elasticsearch 8.x
in the local development stack and CI. The remaining variants are covered at version-dispatch level
only — a single composable index template and read path serve every variant, and all three tested
servers accepted them unchanged.

**No ingest node required.** Unlike APIM's reporter, this one ships no geoip or user_agent ingest
pipeline, so the cluster does not need a node with the ingest role.

## Setup

Reporters are configured per domain or per organization, from the console under
**Settings → Reporters**, or through the management API. An organization-level reporter marked as
inherited also reports for every domain in that organization.

| Option | Default | What it does |
|---|---|---|
| `endpoints` | — | Elasticsearch/OpenSearch node URLs |
| `index` | `gravitee-audit` | Base index name; see [Index layout](#index-layout) |
| `username` / `password` | — | Basic authentication |
| `requestTimeout` | `30000` | Request timeout, milliseconds |
| `sslKeystoreType` | — | Client certificate type: `jks`, `pkcs12` or `pem` |
| `sslKeystorePath` / `sslKeystorePassword` | — | JKS or PKCS12 client keystore |
| `sslPemCerts` / `sslPemKeys` | — | PEM client certificate and key paths |
| `bulkActions` | `1000` | Documents buffered before a bulk flush |
| `flushInterval` | `5` | Flush interval, seconds |
| `maxPendingBatches` | `50` | Backlog bound; beyond it the oldest pending batch is dropped |
| `maxConcurrentRequests` | `5` | Bulk requests in flight at once |
| `retryAttempts` | `6` | Attempts before a batch is dropped |
| `retryInitialInterval` / `retryMaxInterval` | `3` / `30` | Exponential backoff bounds, seconds |
| `shutdownFlushTimeout` | `10` | How long a stopping reporter waits for its buffer to drain |

### Moving audit reads onto Elasticsearch

Audit reads resolve to a **single** reporter. When a reference has more than one searchable
reporter, the one it has had longest wins, so adding Elasticsearch alongside the built-in database
reporter does not move the audit screen on its own.

The offload path is therefore:

1. Add and enable the Elasticsearch reporter. Audits now go to both stores.
2. Once you are satisfied the data is arriving, **disable** the database reporter. Reads move to
   Elasticsearch immediately, with no restart.

Disabling is allowed on system reporters — the delete protection covers deletion, not update.

## Index layout

Audits are written to one index per day, named `<index>-yyyy.MM.dd`, and read back across
`<index>-*`. The daily suffix comes from the **audit's own timestamp**, resolved in **UTC**, so an
audit retried across midnight lands in the same index it would have the first time, and nodes in
different timezones agree.

Each document's id is the audit id, so retries and concurrent writers overwrite rather than
duplicate.

An index template is applied at startup, before anything is written. It maps the fields the console
filters and aggregates on as keywords, and maps `actor.attributes` / `target.attributes` as
`object` with `enabled: false` — stored but not indexed. That last part is load-bearing: those maps
are free-form and their value types vary between events, so under dynamic mapping the first event
whose attribute type differs from a previously seen one is rejected outright.

**The reporter will not start if it cannot apply its template.** Elasticsearch refuses a composable
index template whose patterns overlap an existing one at the same priority, so a pre-existing
template matching `<index>-*` will block it. The reporter sets its priority to the length of the
index name, which means two AM reporters whose index names overlap by prefix resolve
deterministically (the longer, more specific name wins) and both are accepted.

## Retention

The platform's audit purge retention setting **does not apply** to this reporter. Retention is
delegated to the cluster's own lifecycle management, which differs by vendor.

### Elasticsearch — Index Lifecycle Management

```json
PUT _ilm/policy/gravitee-audit-retention
{
  "policy": {
    "phases": {
      "hot":    { "actions": {} },
      "delete": { "min_age": "90d", "actions": { "delete": {} } }
    }
  }
}
```

Attach it to the audit indices with a template of your own at a priority above the reporter's
(the reporter's priority is the length of its index name, so 14 for the default `gravitee-audit`):

```json
PUT _index_template/gravitee-audit-retention
{
  "index_patterns": ["gravitee-audit-*"],
  "priority": 500,
  "template": { "settings": { "index.lifecycle.name": "gravitee-audit-retention" } }
}
```

### OpenSearch — Index State Management

OpenSearch replaced ILM with ISM, which uses explicitly named states and transitions and a different
API and policy document. The two are not interchangeable.

```json
PUT _plugins/_ism/policies/gravitee-audit-retention
{
  "policy": {
    "default_state": "hot",
    "states": [
      { "name": "hot",    "actions": [], "transitions": [ { "state_name": "delete", "conditions": { "min_index_age": "90d" } } ] },
      { "name": "delete", "actions": [ { "delete": {} } ], "transitions": [] }
    ],
    "ism_template": [ { "index_patterns": ["gravitee-audit-*"], "priority": 100 } ]
  }
}
```

## Cutover

The switch is **forward only**. Because reads resolve to a single reporter, a domain that moves to
Elasticsearch will not show its pre-existing database audit history in the console — that history is
still in the database, but the console reads from one store at a time. There is no backfill.

Plan the switch accordingly: keep the database reporter enabled until you no longer need the old
history on screen, or export it before you switch.

## Limitations

- **Paging ceiling.** Elasticsearch refuses `from + size` beyond 10 000 by default, so paging past
  the 10 000th audit fails with a clear error rather than returning wrong data. Narrow the search
  with a date range, or raise `index.max_result_window` on the audit indices.
- **Server certificates are not verified.** On an `https://` endpoint the shared Gravitee
  Elasticsearch client enables TLS and trusts whatever certificate is presented; it has no
  truststore, CA or hostname-verification option. The client certificate options above are for
  authenticating *to* Elasticsearch, and do not change this. This is inherited platform behaviour,
  shared with every APIM deployment reporting to a secured Elasticsearch, not something this
  reporter introduces — but audit records are sensitive, so a warning is logged at startup whenever
  an https endpoint is configured. Treat the link as encrypted but unauthenticated, and keep it on a
  trusted network.
- **Path-prefixed endpoints.** The shared client holds its request paths in static state, so two
  reporters in one JVM pointing at endpoints with *different path prefixes* will misroute. Endpoints
  of the form `http://host:9200` are unaffected.
- **At-most-once delivery.** Under a sustained outage the reporter drops audits rather than
  exhausting the node. Every drop is logged with its reason and per-type counts, and counted on the
  `gio_dropped_audits` metric, tagged `reason` — `buffer_overflow`, `retries_exhausted`, `rejected`
  or `reporter_stopping`. Alert on it.

## Tests

The module's tests run against a real Elasticsearch container. They are skipped by default and run
under the `cicd` profile, like the MongoDB and JDBC reporters:

```bash
mvn test -pl gravitee-am-reporter/gravitee-am-reporter-elasticsearch -Pcicd
```

Point them at another server version without editing test code:

```bash
mvn test -pl gravitee-am-reporter/gravitee-am-reporter-elasticsearch -Pcicd \
  -Delasticsearch.image=docker.elastic.co/elasticsearch/elasticsearch:7.17.28
```

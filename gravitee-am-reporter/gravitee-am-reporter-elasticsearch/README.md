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
| `index` | `gravitee-audit` | Base index name; see [Index layout](#index-layout) and [Sharing an index between domains](#sharing-an-index-between-domains) |
| `rolloverPeriod` | `daily` | How often a new index is started: `daily`, `weekly` or `monthly`; see [Rollover period](#rollover-period) |
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

Audit reads resolve to a **single** reporter, while writes go to every enabled one. Adding and
enabling this reporter is enough to move the audit screen onto Elasticsearch: a reporter an
administrator added outranks the database reporter AM created with the domain. No restart, and no
need to disable anything. The reporters screen marks the winner with an **audit reads** badge, and
the management API returns `readSource: true` on the same reporter.

Between several reporters you added yourself, the oldest wins — so adding a second searchable
reporter later never silently moves the screen onto a store with no history.

Two consequences worth planning for:

- **Reads move immediately, before any history exists in Elasticsearch.** The audit screen will look
  empty until audits accumulate. The database history is not deleted, just no longer displayed; see
  [Cutover](#cutover).
- **Audits are written to both stores for as long as both reporters are enabled.** That is the
  migration window, and it is deliberate — it is what lets you verify Elasticsearch before
  committing. It is not a steady state: once you are satisfied, **disable the database reporter** to
  stop paying for both.

Disabling is allowed on system reporters — the delete protection covers deletion, not update.

One wrinkle if you are scripting this rather than using the console: the management API nulls a
system reporter's `configuration` when you read it, but the update endpoint rejects a request
without one. There is therefore nothing to round-trip, and a disable call has to supply a
placeholder configuration. It is never used, because a disabled reporter is not started.

## Index layout

Audits are written to one index per rollover period and read back across `<index>-*`. The suffix
comes from the **audit's own timestamp**, resolved in **UTC**, so an audit retried across a period
boundary lands in the same index it would have the first time, and nodes in different timezones agree.

Each document's id is the audit id, so retries and concurrent writers overwrite rather than
duplicate.

### Rollover period

| `rolloverPeriod` | Index name | Example |
|---|---|---|
| `daily` (default) | `<index>-yyyy.MM.dd` | `gravitee-audit-2026.07.25` |
| `weekly` | `<index>-yyyy.wWW` | `gravitee-audit-2026.w30` |
| `monthly` | `<index>-yyyy.MM` | `gravitee-audit-2026.07` |

`weekly` uses the ISO-8601 week and its **week-based year**, so 1 January 2027 is written to
`2026.w53` — the week it actually belongs to.

**Why this is a setting.** Shard count follows your *retention window*, not your audit volume. A
domain retaining two years at daily rollover has roughly 730 indices whether it wrote 600 million
audits or 600, and every shard costs heap in cluster state and segment metadata:

| Retention | `daily` | `weekly` | `monthly` |
|---|---|---|---|
| 90 days | 90 indices | 13 | 3 |
| 1 year | 365 | 53 | 12 |
| 2 years | 730 | 105 | 24 |

Multiply by one primary shard each (the Elasticsearch default), and again by your replica count.

**The cost of a longer period** is coarser date pruning. AM's audit console always sends a bounded
date range and defaults to the last 24 hours, so Elasticsearch can rule out whole shards from the
index name before doing any real work — measured at 731 daily indices, a 24-hour query skipped 729 of
them and ran in roughly a sixth of the time of the same query without a date range. Monthly indices
are fewer but wider in time, so fewer can be skipped. That is the trade: fewer shards and less heap,
against less effective pruning.

Pick the shortest period whose shard count your cluster can carry. Changing it later is safe: the read
wildcard does not mention the period, so indices written under the old setting keep answering reads.

### Sizing heap against retention

**Heap has to be sized against the retention window, not the audit volume**, and running out of it is
a cliff rather than a slope. Elasticsearch's own guidance is to stay under roughly 20 shards per GB of
JVM heap, and `cluster.max_shards_per_node` defaults to 1000.

Measured during development on a single node: 651 shards exhausted a 512 MB heap with 1.8 million
documents in the cluster. Document count was not the driver — shard count was.

The symptom, if you get it wrong: the node starts rejecting **every** request with a
`circuit_breaking_exception` from the parent breaker, including the `_cat/indices` and `_cluster/health`
calls you would reach for to diagnose it, and it does not recover on its own. Watch shard count as
retention grows, and lengthen the rollover period before you get there.

## Sharing an index between domains

Reporters are configured per domain, but the index name is **not** derived from the domain — every
reporter left on the default `gravitee-audit` writes into the same indices. This is a deliberate
default, and it is the opposite of what the built-in database reporters do: those get a per-reference
collection or table when AM provisions them.

**Audit isolation is not affected.** Every query the reporter issues — search, single-record lookup,
count, group-by and date histogram — filters on `referenceType` and `referenceId`, so no domain can
read another's audits through AM, shared index or not. That isolation is enforced in the query layer,
though, not by a storage boundary: anyone with direct cluster access sees every domain's audits in the
shared index.

What a shared index does affect is everything that operates on an *index* rather than a document:

- **Retention.** An ILM or ISM policy applies to indices. Domains sharing indices cannot have
  different retention, so the strictest requirement governs everyone or the loosest violates someone.
- **Deletion.** Removing one domain's audit history means deleting documents by query rather than
  dropping an index, which is the cheap operation dated indices exist to enable.
- **Compliance.** A tenant whose audit records must be physically separable cannot be served by a
  shared index.
- **Blast radius.** A mapping conflict or a corrupt index affects every domain sharing it.

**To isolate a domain, give its reporter its own index name** — for example `gravitee-audit-payments`.
Then decide the layout deliberately, because per-domain indices multiply the shard arithmetic above by
the number of domains. Staying under 1000 primary shards on a node means roughly:

| Retention | `daily` | `weekly` | `monthly` |
|---|---|---|---|
| 90 days | ~11 domains | ~77 | ~333 |
| 2 years | ~1 domain | ~9 | ~41 |

So per-domain indices suit a handful of domains with a real isolation requirement, and do not scale to
hundreds. At that scale the configuration that works is a **single inherited organization reporter**,
which reports for every domain in the organization through one index — the shared layout, chosen on
purpose.

Two constraints on a name you pick:

- Elasticsearch caps an index name at **255 bytes**, and the reporter appends a suffix of up to 11
  characters (`-2026.07.25`), so keep the base name under 244 bytes.
- The base name must be lowercase and use only letters, digits and `_ . + -`, starting with a letter or
  digit. The reporter checks this at startup and refuses to run with a clear message rather than
  failing later on a template it could not apply.

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

The policies below match `<index>-*`, so they work for every [rollover period](#rollover-period). One
thing to adjust when you lengthen the period: lifecycle age is measured from **index creation**, and an
index holds a whole period of audits. A `min_age` of `90d` on monthly indices deletes the July index
around 29 September, taking the 31 July audits with it when they are only 60 days old. To guarantee a
minimum retention, add the period length to `min_age` — `90d` becomes `97d` for weekly and `120d` for
monthly.

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

Export the old history before you switch if you still need it on screen. Keeping the database
reporter enabled does *not* keep it visible: an added reporter wins reads either way, so enabling
both buys you duplicated writes, not a merged view. To read the database history again, disable the
Elasticsearch reporter — reads fall back to the database, and the audits written to Elasticsearch
meanwhile stay in Elasticsearch.

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
- **Path-prefixed endpoints.** The shared client derives its request paths from the endpoint URL and
  stores them in *static* fields, rewriting all of them every time a client is constructed. AM builds
  one client per reporter, and reporters are per domain and per organization, so a node running
  several of them has them all overwriting the same paths — last one wins. Endpoints of the form
  `http://host:9200` all produce identical paths and are unaffected; two reporters whose endpoints
  carry *different path prefixes* (say `http://host:9200` and `http://host:9200/opensearch`) will
  misroute each other's requests.

  This is inherited from the shared client library rather than introduced here, and APIM has shipped
  on it for years — but APIM configures one reporter per gateway node, where AM is the first product
  to construct several clients in one JVM, so it is the first place the behaviour can actually bite.
  Until the library gives each client its own paths, keep every audit reporter on a node pointing at
  endpoints with the same path prefix.
- **At-most-once delivery.** Under a sustained outage the reporter drops audits rather than
  exhausting the node. Every drop is logged at ERROR with its reason and per-type counts, and
  counted on the `gio_dropped_audits` metric, tagged `reason` — `buffer_overflow`,
  `retries_exhausted`, `rejected`, `not_writable`, `unserializable` or `reporter_stopping`. Alert on
  it.

  Note the metric requires `services.metrics.enabled: true` in `gravitee.yml`. Without it the node's
  registry is a no-op, so the counters silently record nothing — the ERROR logs are still emitted,
  and are the only signal you get. Enable metrics if you intend to alert on dropped audits.

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

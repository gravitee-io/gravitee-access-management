# Gravitee AM Management API — Java SDK

Java client for the [Gravitee Access Management](https://github.com/gravitee-io/gravitee-access-management) Management API.

Generated from `docs/mapi/openapi.yaml` at build time via [openapi-generator](https://openapi-generator.tech/) (`java` / `okhttp-gson`). Sources are **not committed** — the reactor build regenerates them from the spec on every build.

---

## Coordinates

```xml
<dependency>
    <groupId>io.gravitee.am</groupId>
    <artifactId>gravitee-am-management-api-sdk-java</artifactId>
    <version>4.12.0-alpha.3-SNAPSHOT</version>
</dependency>
```

Runtime dependencies pulled in transitively: `okhttp3`, `gson`, `gson-fire`, `jakarta.annotation-api`.

JDK 17+.

---

## Building locally

```bash
mvn -pl gravitee-am-management-api-sdk-java -am clean install
```

Regenerates sources from `docs/mapi/openapi.yaml`, compiles, and installs the jar to `~/.m2`.

---

## Configuration

All APIs share a single `ApiClient`. Configure it once; inject it (or `Configuration.getDefaultApiClient()`) into each `*Api` class.

### Base path

```java
import io.gravitee.am.sdk.management.invoker.ApiClient;
import io.gravitee.am.sdk.management.invoker.Configuration;

ApiClient client = Configuration.getDefaultApiClient();
client.setBasePath("https://am.example.com/management");
```

### Authentication — bearer token

The Management API accepts an OAuth2 bearer token issued by AM itself. Obtain a token out-of-band (user login, `/management/auth/token`, etc.) and hand it to the SDK:

```java
client.setBearerToken("eyJhbGciOiJIUzI1NiJ9...");
```

For refreshable credentials, pass a `Supplier<String>` so each request picks up the latest value:

```java
client.setBearerToken(() -> tokenCache.current());
```

### Authentication — basic / API key

The same `ApiClient` exposes `setUsername` / `setPassword` and `setApiKey` — pick whichever matches the security scheme enabled on your AM instance.

### Timeouts & HTTP client tuning

```java
client.setConnectTimeout(10_000);
client.setReadTimeout(30_000);
client.setWriteTimeout(30_000);
```

Or supply a fully-configured `OkHttpClient`: `new ApiClient(okHttpClient)`.

---

## Usage

Every mutating endpoint is scoped by **organization** and **environment**. Use the reserved identifier `DEFAULT` if you're running against a single-tenant install.

```java
String orgId = "DEFAULT";
String envId = "DEFAULT";
```

All SDK methods throw `io.gravitee.am.sdk.management.invoker.ApiException` on non-2xx responses. `ApiException#getCode()` is the HTTP status; `getResponseBody()` is the raw body.

### Domains

```java
import io.gravitee.am.sdk.management.api.DomainApi;
import io.gravitee.am.sdk.management.model.Domain;
import io.gravitee.am.sdk.management.model.NewDomain;

DomainApi domains = new DomainApi(client);

// Create
NewDomain newDomain = new NewDomain()
        .name("customers")
        .description("Customer-facing tenant");
Domain created = domains.createDomain(orgId, envId, newDomain);
String domainId = created.getId();

// Get one
Domain fetched = domains.findDomain(orgId, envId, domainId);

// Get by HRID (human-readable id, usually the slug)
Domain byHrid = domains.findDomainByHrid(orgId, envId, "customers");
```

> **Listing domains.** The Management API exposes domain listings under organization/environment-scoped endpoints (e.g. via `OrganizationApi` / `EnvironmentApi`). Browse the generated `target/generated-sources/openapi/src/main/java/io/gravitee/am/sdk/management/api/` folder to discover the exact method for your installation.

### Applications

```java
import io.gravitee.am.sdk.management.api.ApplicationApi;
import io.gravitee.am.sdk.management.model.Application;
import io.gravitee.am.sdk.management.model.ApplicationPage;
import io.gravitee.am.sdk.management.model.NewApplication;

ApplicationApi applications = new ApplicationApi(client);

// Create — type drives the OAuth2 client flow (WEB, NATIVE, SERVICE, BROWSER, ...)
NewApplication newApp = new NewApplication()
        .name("web-portal")
        .type(NewApplication.TypeEnum.WEB)
        .description("Public-facing portal");
Application app = applications.createApplication(orgId, envId, domainId, newApp);
String applicationId = app.getId();

// Get one
Application found = applications.findApplication(orgId, envId, domainId, applicationId);

// List (paginated) — page index, size, free-text query
ApplicationPage page = applications.listApplications(orgId, envId, domainId, 0, 25, null);
page.getData().forEach(a -> System.out.println(a.getName() + " → " + a.getId()));
```

### Identity providers

```java
import io.gravitee.am.sdk.management.api.IdentityProviderApi;
import io.gravitee.am.sdk.management.model.IdentityProvider;
import io.gravitee.am.sdk.management.model.NewIdentityProvider;
import io.gravitee.am.sdk.management.model.FilteredIdentityProviderInfo;

import java.util.List;

IdentityProviderApi idps = new IdentityProviderApi(client);

// Create — `configuration` is a provider-specific JSON string
String inlineConfig = """
    {
      "users": [
        {"username": "alice", "password": "s3cret", "firstname": "Alice"}
      ]
    }
    """;

NewIdentityProvider newIdp = new NewIdentityProvider()
        .name("inline-users")
        .type("inline-am-idp")
        .configuration(inlineConfig);
IdentityProvider idp = idps.createIdentityProvider(orgId, envId, domainId, newIdp);
String idpId = idp.getId();

// List (with an optional `userProvider` filter — true/false/null)
List<FilteredIdentityProviderInfo> all = idps.listIdentityProviders(orgId, envId, domainId, null);
```

---

## Async calls

Every sync method has an `Async` sibling that takes an `ApiCallback<T>`:

```java
applications.listApplicationsAsync(orgId, envId, domainId, 0, 25, null,
        new ApiCallback<>() {
            @Override public void onSuccess(ApplicationPage result, int statusCode, Map<String, List<String>> headers) {
                result.getData().forEach(System.out::println);
            }
            @Override public void onFailure(ApiException e, int statusCode, Map<String, List<String>> headers) {
                e.printStackTrace();
            }
            @Override public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {}
            @Override public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {}
        });
```

Returns an `okhttp3.Call` you can `cancel()`.

---

## Error handling

```java
try {
    domains.findDomain(orgId, envId, "missing-id");
} catch (ApiException e) {
    switch (e.getCode()) {
        case 401 -> refreshToken();
        case 404 -> logger.info("domain not found");
        default  -> logger.error("AM call failed: {} — {}", e.getCode(), e.getResponseBody(), e);
    }
}
```

`ApiException#getResponseHeaders()` exposes the response headers map for correlation-id style debugging.

---

## Discovering the rest of the surface

- Generated API classes: `target/generated-sources/openapi/src/main/java/io/gravitee/am/sdk/management/api/`
- Generated models:     `target/generated-sources/openapi/src/main/java/io/gravitee/am/sdk/management/model/`

IDEs need `mvn generate-sources` to run once before autocomplete works (the `target/` tree is gitignored).

The canonical reference is `docs/mapi/openapi.yaml` — every method, model, and field on this SDK corresponds 1:1 to a schema there.

# Local Kerberos SPNEGO Lab for Gravitee AM

Set up a local KDC (Key Distribution Center) to test Kerberos SPNEGO authentication with Gravitee AM.

## Prerequisites

Complete the common setup steps in the [local-stack README](../../README.md) (build AM, initialize Docker images).

Additionally:
- Kerberos plugin ZIP at `docker/local-stack/dev/plugins/enterprise/gravitee-am-identityprovider-kerberos-<version>.zip`
  - Download from: https://download.gravitee.io/#graviteeio-ee/am/plugins/idps/gravitee-am-identityprovider-kerberos/
  - The compose file uses `KERBEROS_PLUGIN_VERSION` (default: `3.0.0`). Set it if your version differs.

## Step 1: Start the Stack

```bash
npm --prefix docker/local-stack run stack:down
npm --prefix docker/local-stack run stack:dev:setup:kerberos
```

> `stack:down` first ensures a clean state — prevents port conflicts if a previous stack is still running.

This starts MongoDB, Management API, Gateway, and a KDC container with:
- Realm: `GRAVITEE.LOCAL`
- Service principal: `HTTP/gateway@GRAVITEE.LOCAL`
- Keytab: `/var/lib/krb5kdc/am.keytab` (shared with gateway via Docker volume, mounted at `/etc/krb5kdc/am.keytab`)
- Test user: `testuser@GRAVITEE.LOCAL` (password: `Password1!`)

## Step 2: Configure the Domain

This step creates a domain, Kerberos identity provider, and OAuth application via the Management API.

#### 2a. Wait for Management API to be ready

```bash
until curl -s http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains > /dev/null 2>&1; do sleep 2; done
echo "Management API ready"
```

#### 2b. Authenticate as admin

```bash
TOKEN=$(curl -s -X POST http://localhost:8093/management/auth/token \
  -H 'Authorization: Basic YWRtaW46YWRtaW5hZG1pbg==' | jq -r .access_token)
```

#### 2c. Create a security domain

```bash
DOMAIN=$(curl -s -X POST http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"kerberos-lab","description":"Kerberos SPNEGO lab","dataPlaneId":"default"}' | jq -r '.id')
echo "Domain ID: $DOMAIN"
```

#### 2d. Create the Kerberos identity provider

Points at the KDC's keytab and service principal. The dummy LDAP config is required — the plugin initializes LDAP beans even when `useLDAP=false`, causing an NPE without these placeholder values.

```bash
IDP=$(curl -s -X POST "http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/$DOMAIN/identities" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "kerberos-am-idp",
    "name": "Kerberos SPNEGO",
    "configuration": "{\"realm\":\"GRAVITEE.LOCAL\",\"keytab\":\"/etc/krb5kdc/am.keytab\",\"principal\":\"HTTP/gateway@GRAVITEE.LOCAL\",\"useLDAP\":false,\"ldapConfig\":{\"contextSourceUrl\":\"ldap://localhost:389\",\"contextSourceBase\":\"dc=gravitee,dc=local\",\"contextSourceUsername\":\"admin\",\"contextSourcePassword\":\"admin\",\"userSearchFilter\":\"(uid={0})\"}}"
  }' | jq -r '.id')
echo "Kerberos IDP ID: $IDP"
```

#### 2e. Get the default MongoDB identity provider

Needed as a fallback for the login form when SPNEGO negotiation fails.

```bash
MONGO_IDP=$(curl -s "http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/$DOMAIN/identities" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[] | select(.type == "mongo-am-idp") | .id')
echo "Mongo IDP ID: $MONGO_IDP"
```

#### 2f. Create an OAuth application

```bash
APP=$(curl -s -X POST "http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/$DOMAIN/applications" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"kerberos-test-app","type":"WEB","redirectUris":["https://gravitee.io/callback"]}' | jq -r '.id')

CLIENT_ID=$(curl -s "http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/$DOMAIN/applications/$APP" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.settings.oauth.clientId')

CLIENT_SECRET=$(curl -s "http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/$DOMAIN/applications/$APP" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.settings.oauth.clientSecret')

echo "App ID: $APP"
echo "Client ID: $CLIENT_ID"
echo "Client Secret: $CLIENT_SECRET"
```

#### 2g. Configure OAuth settings (grant types, scopes, skip consent)

```bash
curl -s -X PATCH "http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/$DOMAIN/applications/$APP" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "settings": {
      "oauth": {
        "redirectUris": ["https://gravitee.io/callback"],
        "grantTypes": ["authorization_code", "password"],
        "scopeSettings": [
          {"scope": "openid", "defaultScope": true},
          {"scope": "profile", "defaultScope": false}
        ]
      },
      "advanced": {"skipConsent": true}
    }
  }' | jq '.name'
```

#### 2h. Attach both identity providers to the application

Kerberos as primary (triggers `SPNEGOStep`), Mongo as fallback (provides login form authentication when SPNEGO fails).

```bash
curl -s -X PATCH "http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/$DOMAIN/applications/$APP" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"identityProviders\": [
      {\"identity\": \"$IDP\", \"priority\": 0},
      {\"identity\": \"$MONGO_IDP\", \"priority\": 1}
    ]
  }" | jq '.name'
```

#### 2i. Enable the domain and wait for readiness

```bash
curl -s -X PATCH "http://localhost:8093/management/organizations/DEFAULT/environments/DEFAULT/domains/$DOMAIN" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}' | jq '.enabled'

until curl -s http://localhost:8092/kerberos-lab/oidc/.well-known/openid-configuration | jq -r '.issuer' 2>/dev/null | grep -q kerberos; do sleep 2; echo "waiting..."; done
echo "Domain ready"
```

## Step 3: Configure the Client Machine

### macOS

Add to `/etc/hosts` (requires sudo):
```
127.0.0.1 gateway
```

Create `/etc/krb5.conf` (requires sudo). Use `localhost:88` — not `kdc:88` which is the Docker-internal hostname:
```ini
[libdefaults]
    default_realm = GRAVITEE.LOCAL
    dns_lookup_realm = false
    dns_lookup_kdc = false

[realms]
    GRAVITEE.LOCAL = {
        kdc = localhost:88
        admin_server = localhost:749
    }

[domain_realm]
    .gravitee.local = GRAVITEE.LOCAL
    gravitee.local = GRAVITEE.LOCAL
```

Get a Kerberos ticket:
```bash
kinit testuser@GRAVITEE.LOCAL
# Password: Password1!
klist  # verify: should show krbtgt/GRAVITEE.LOCAL@GRAVITEE.LOCAL
```

Configure Chrome to trust the gateway for SPNEGO:
```bash
defaults write com.google.Chrome AuthServerAllowlist "gateway"
defaults write com.google.Chrome AuthNegotiateDelegateAllowlist "gateway"
```
Restart Chrome completely (Cmd+Q, then reopen).

Firefox: navigate to `about:config` and set:
- `network.negotiate-auth.trusted-uris` → `gateway`
- `network.negotiate-auth.delegation-uris` → `gateway`

Safari uses the macOS Kerberos framework directly — no additional configuration needed.

### Windows (Remote Machine)

Find the Mac's IP (on Mac):
```bash
ipconfig getifaddr en0
```

Verify Windows can reach the KDC (PowerShell):
```powershell
Test-NetConnection -ComputerName <mac-ip> -Port 88
# TcpTestSucceeded must be True
```
If it fails, disable the macOS firewall temporarily (System Settings → Network → Firewall).

Add to `C:\Windows\System32\drivers\etc\hosts` (edit as Administrator):
```
<mac-ip> gateway
```

Configure Windows Kerberos and store credentials (Admin Command Prompt):
```cmd
ksetup /AddKdc GRAVITEE.LOCAL <mac-ip>
cmdkey /add:gateway /user:testuser@GRAVITEE.LOCAL /pass:Password1!
```
Note: `ksetup` may require a Windows restart to take effect.

Configure Chrome (Admin Command Prompt):
```cmd
reg add "HKLM\SOFTWARE\Policies\Google\Chrome" /v AuthServerAllowlist /t REG_SZ /d "gateway" /f
reg add "HKLM\SOFTWARE\Policies\Google\Chrome" /v AuthNegotiateDelegateAllowlist /t REG_SZ /d "gateway" /f
```

Configure Firefox: navigate to `about:config` and set:
- `network.negotiate-auth.trusted-uris` → `gateway`
- `network.negotiate-auth.delegation-uris` → `gateway`

Configure Edge (Admin Command Prompt):
```cmd
reg add "HKLM\SOFTWARE\Policies\Microsoft\Edge" /v AuthServerAllowlist /t REG_SZ /d "gateway" /f
reg add "HKLM\SOFTWARE\Policies\Microsoft\Edge" /v AuthNegotiateDelegateAllowlist /t REG_SZ /d "gateway" /f
```

Restart all browsers after configuration changes.

## Step 4: Test

Clear cookies for `gateway:8092` and navigate to:
```
http://gateway:8092/kerberos-lab/oauth/authorize?response_type=code&client_id=<CLIENT_ID>&redirect_uri=https://gravitee.io/callback&scope=openid
```

Expected: silent redirect to `https://gravitee.io/callback?code=...` (no login page).

## Cleanup

macOS:
```bash
kdestroy
sudo rm /etc/krb5.conf
# Remove "127.0.0.1 gateway" from /etc/hosts
defaults delete com.google.Chrome AuthServerAllowlist
defaults delete com.google.Chrome AuthNegotiateDelegateAllowlist
```

Windows (Admin Command Prompt):
```cmd
cmdkey /delete:gateway
klist purge
ksetup /DelKdc GRAVITEE.LOCAL <mac-ip>
reg delete "HKLM\SOFTWARE\Policies\Google\Chrome" /v AuthServerAllowlist /f
reg delete "HKLM\SOFTWARE\Policies\Google\Chrome" /v AuthNegotiateDelegateAllowlist /f
reg delete "HKLM\SOFTWARE\Policies\Microsoft\Edge" /v AuthServerAllowlist /f
reg delete "HKLM\SOFTWARE\Policies\Microsoft\Edge" /v AuthNegotiateDelegateAllowlist /f
```

Docker:
```bash
npm --prefix docker/local-stack run stack:down
```

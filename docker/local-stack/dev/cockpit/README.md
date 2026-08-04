# Cockpit SSO test keys

Throwaway, self-signed key material for the managed-cloud local stack. **Not a credential** — it is
committed on purpose so the stack is reproducible, and it is only ever loaded by containers on the
local docker network.

| File | Mounted on | Purpose |
| --- | --- | --- |
| `cockpit-truststore.p12` | `management` | Certificate under alias `cockpit-client`; `CockpitAuthenticationFilter` verifies SSO tokens with its public key |
| `cockpit-key.pem` | `cockpit-mock` | Matching private key; signs the tokens `POST /_control/sso-token` returns |
| `cockpit-cert.pem` | — | The certificate in PEM form, kept so the keystore can be rebuilt |

Keystore password: `cockpit`. Alias `cockpit-client` is `CockpitAuthenticationFilter`'s default for
`cloud.connector.ws.ssl.keystore.key.alias`, so the stack does not set it explicitly.

This mirrors production: Cockpit issues a per-installation client keypair, ships the PKCS12 to the
installation, and signs SSO tokens with the private key it kept.

## Regenerating

```bash
openssl req -x509 -newkey rsa:2048 -keyout cockpit-key.pem -out cockpit-cert.pem -days 36500 -nodes -subj "/CN=cockpit-client/O=GraviteeSource/C=FR"
keytool -importcert -alias cockpit-client -file cockpit-cert.pem -keystore cockpit-truststore.p12 -storetype PKCS12 -storepass cockpit -noprompt
```

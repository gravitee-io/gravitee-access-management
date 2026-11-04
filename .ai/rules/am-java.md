---
name: AM Java
layer: local
description: Lombok and RxJava type split
dirs: [gravitee-am-authdevice-notifier, gravitee-am-authenticator, gravitee-am-authorizationengine, gravitee-am-botdetection, gravitee-am-certificate, gravitee-am-common, gravitee-am-dataplane, gravitee-am-deviceidentifier, gravitee-am-extensiongrant, gravitee-am-factor, gravitee-am-gateway, gravitee-am-identityprovider, gravitee-am-jwt, gravitee-am-management-api, gravitee-am-model, gravitee-am-monitoring, gravitee-am-password-dictionary, gravitee-am-plugins-handlers, gravitee-am-policy, gravitee-am-reporter, gravitee-am-repository, gravitee-am-resource, gravitee-am-secrets, gravitee-am-service]
---

# AM Java

## Lombok

The shared Java rule advises against broad Lombok annotations such as `@Data`. Lombok is already conventional in this repository (see `lombok.config`), so for new models this line applies:

- For **new** Java models/POJOs: use Lombok for mutable types — prefer `@Data` when it covers all needs, otherwise compose `@Getter`/`@Setter`/`@ToString`/`@AllArgsConstructor` — and use `record`s for immutable/simple ones. Do not retrofit existing models.

## RxJava

- Use `Single`/`Maybe` for I/O operations and `Completable` for side-effect operations.

---
name: AM Java
layer: local
description: Lombok and RxJava type split
dirs: [gravitee-am-authdevice-notifier, gravitee-am-authenticator, gravitee-am-authorizationengine, gravitee-am-botdetection, gravitee-am-certificate, gravitee-am-common, gravitee-am-dataplane, gravitee-am-deviceidentifier, gravitee-am-extensiongrant, gravitee-am-factor, gravitee-am-gateway, gravitee-am-identityprovider, gravitee-am-jwt, gravitee-am-management-api, gravitee-am-management-api-sdk-java, gravitee-am-model, gravitee-am-monitoring, gravitee-am-password-dictionary, gravitee-am-performance, gravitee-am-plugins-handlers, gravitee-am-policy, gravitee-am-reporter, gravitee-am-repository, gravitee-am-resource, gravitee-am-secrets, gravitee-am-service]
---

# AM Java

## Lombok

This repository has a `lombok.config` and Lombok is conventional here, so for new models the lines below apply instead of the shared default to avoid broad annotations.

- For **new** Java models/POJOs: use Lombok for mutable types — prefer `@Data` when it covers all needs, otherwise compose `@Getter`/`@Setter`/`@ToString`/`@AllArgsConstructor` — and use `record`s for immutable/simple ones. Do not retrofit existing models.

## RxJava

- Use `Single`/`Maybe` for I/O operations and `Completable` for side-effect operations.

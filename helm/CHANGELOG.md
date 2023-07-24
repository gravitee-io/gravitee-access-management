# Changelog

This file documents all notable changes to [Gravitee.io Access Management 3.x](https://github.com/gravitee-io/helm-charts/tree/master/am/) Helm Chart. The release numbering uses [semantic versioning](http://semver.org).

### 3.21.5

To ensure the compatibility between the AM product and the AM Helm Chart, the versioning of the latter is changed.
As of 3.21.5, the AM Helm Chart will follow AM release cycle.
It means Helm Chart and AM should always be aligned.

- [X] Add hook-delete-policy
- [X] Allow users to use `logging.debug: true` when they define their own configuration file by defining a volume
- [X] Remove ingress nginx annotation when `ingress.class` is not nginx

### 4.0.0

To ensure the compatibility between the AM product and the AM Helm Chart, the versioning of the latter is changed.
As of 4.0.0, the AM Helm Chart will follow AM release cycle.
It means Helm Chart and AM should always be aligned.

- [X] Add hook-delete-policy
- [X] Allow users to use `logging.debug: true` when they define their own configuration file by defining a volume
- [X] Remove ingress nginx annotation when `ingress.class` is not nginx

### 1.0.56

- [X] Add `externalTrafficPolicy` in service configuration

### 1.0.55

- [X] Update AM version to 3.20.1

### 1.0.54

- [X] Fix AE alerts configuration without options

### 1.0.53

- [X] Allow users to define their own configuration file by defining a volume

### 1.0.52

- [X] Disable upgrader framework job by default

### 1.0.51

- [X] Add upgrader framework job
- [X] Add alert engine configuration
- [X] Add support for User Activity settings
- [X] Add support for Alert triggers
- [X] Add documentation for alert engine connector ssl
- [X] Add AE engine list support

### 1.0.50

- [X] Replace ClusterRole with Role
- [X] Manage mfa_rate limit on gateway
- [X] Manage default certificates settings on management side
- [X] Add settings for JSON logs format definition

### 1.0.49

- [X] Manage the proxy settings for HTTP clients
- [X] RollingUpdate maxUnavailable set to 25% to avoid stop when only 1 pod
- [X] Add support for Openshift Routes by removing ingress annotation

### 1.0.48

- [X] Add optional value to use gravitee licence key

- Update gravitee.io AM v3.18.6

### 1.0.47

- [X] Trunc port name with k8s limit (63)

### 1.0.46

- [X] Upgrade Mongodb and Elasticsearch dependencies
- [X] Add settings for Backend SSL connection

### 1.0.45

- [X] Add support for autoscaling/v2
- [X] Add support for appProtocol to the services

- Update gravitee.io AM v3.15.9

### 1.0.44

- [X] Add support for ingressClassName

### 1.0.43

- [X] Add version labels on pods

### 1.0.42

- [X] Add support for managed ServiceAccounts name provided by user

### 1.0.41

- [X] Add support for jetty configuration

### 1.0.40

- [X] Add support for PodDisruptionBudget

### 1.0.39

- [X] Reorder HPA resources to avoid outofsync state with ArgoCD

### 1.0.38

- [X] Fix ignoring the managedServiceAccount in the deployment files

### 1.0.37

- [X] Set default array for topologySpreadConstraints

### 1.0.36

- [X] Ease the integration of Gravitee.io Cockpit
- [X] Allow configuration of cookie properties
- [X] Introduce values to enable and configure notification about domain certificates expiry 
- [X] Allow to configure ssl.trust and ssl.protocols through the values.yaml
- [X] Allow to configure user management settings (resetPassword token TTL, username pattern...) in the values.yaml
- [X] Allow to define gateway http section through values instead of environment variables (csp, xframe, cors, csrf)
- [X] Add support for topologySpreadConstraints

### 1.0.35

- [X] Ensure additional jar doesn't exist before downloading it
- [X] Improve AM Gateway readinessProbe configuration

### 1.0.34

- [X] Make app.kubernetes.io/version label consistent
- [X] Add quotes to version to fix #6450
- [X] Add ServiceAccount to all deployments
- Update gravitee.io AM v3.15.0

### 1.0.33

- [X] Add support for revisionHistoryLimit

### 1.0.32

- [X] Allow to override init containers for downloading jdbc drivers

### 1.0.31

- [X] Allow to override init containers for additional plugins
- Update gravitee.io AM v3.14.0

### 1.0.30

- [X] Allow to configure metrics endpoint in the configmap template for AM Gateway & API 

### 1.0.29

- [X] Support Ingress kubernetes >= 1.22.x

### 1.0.28

- [X] Configure deployment strategy
- Update gravitee.io AM v3.11.0

### 1.0.27

- [X] Make Management API entrypoint configurable using `api.http.api.entrypoint` value

### 1.0.26

- [X] Add logger for com.graviteesource packages

### 1.0.25

- [X] Fix additional plugins integration

### 1.0.24

- [X] Fix additional plugins integration

### 1.0.23

- [X] Fix additional plugins integration

### 1.0.22

- [X] Make Management API entrypoint configurable
- [X] Allow to enable or disable liquibase execution

### 1.0.21

- [X] Improve Deployment configurability

### 1.0.20

- [X] Remove Ingress beta API and add support for networking.k8s.io
- Update gravitee.io AM v3.9.2

### 1.0.19

- [X] Add validation on baseURL value 
- [X] Add support for JDBC Connection pool
- Update gravitee.io AM v3.9.0

### 1.0.18

- [X] Correctly evaluate ingress properties to customize constants.json file
- [X] Remove duplicate gateway.url value in AM 

### 1.0.17

- [X] Correctly mount constants.json file
- [X] Handle gateway.url value

### 1.0.16

- [X] Fix wrong constants.json file
- Update gravitee.io AM v3.8.1

### 1.0.15

- [X] Add TLS Protocols support version for the Gateway

### 1.0.14

- [X] Manage JDBC deployment
- [X] Map handlers for Auth Gateway to override X-Transaction and X-Request
- [X] Manage baseURL for the UI constants.json file
- Update gravitee.io AM v3.7.1

### 1.0.13

- [X] Externalize securityContext from deployments

### 1.0.12

- [X] Add condition for replica settings

### 1.0.11

- Update gravitee.io AM v3.4.0

### 1.0.10

- Bump AM Chart version

### 1.0.9

- Bump AM Chart version

### 1.0.8

- [X] Handle valueFrom for environment variables
- Update gravitee.io AM v3.1.2

### 1.0.7

- Update gravitee.io AM v3.1.1

### 1.0.6

- Update gravitee.io AM v3.0.4

### 1.0.5

- [X] Fix security context

### 1.0.4

- Update gravitee.io AM v2.10.15

### 1.0.3

- Bump AM Chart version

### 1.0.2

- Update gravitee.io AM v2.10.14

### 1.0.1

- Update gravitee.io AM v2.10.19

### 1.0.0

- [X] Initial support for AM charts
- Update gravitee.io AM v2.10.8

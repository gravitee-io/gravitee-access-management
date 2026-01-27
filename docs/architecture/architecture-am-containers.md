# AM - C4 Container Diagram

This document provides a Container diagram focusing on the Gravitee Access Management with some external services highlighting the relation they have with the AM containers.

## Container Diagram

```mermaid
---
config:
  theme: neutral
---
C4Container
    title Container diagram for Access Management System
   
    System_Boundary(c1, "AccessManagement") {
        ContainerDb(dp_db, "Data Plane DB", "MongoDB", "Sessions, Tokens, Runtime data")
        Container(gateway, "Gateway", "OAuth2 / OIDC", "Authentication Engine")
        Container(mgmt_api, "Management API", "REST API", "Security Configuration Logic")
        Container(ui, "UI", "Angular", "Admin interface")
        ContainerDb(cp_db, "Control Plane DB", "MongoDB", "Configs & Policies")
    }
    Person(domainUser, "EndUser", "External User")
    System_Ext(app, "Application", "External")
    System_Ext(apim, "APIM Gateway", "External", "Third-party App")
    Person(orgUser, "Admin")
    System_Ext(idp, "Identity Providers", "Google, Azure AD, OIDC Providers")
    System_Ext(apimPortal, "APIM Portal", "External", "Third-party App")
    
    Rel(orgUser, ui, "Manages", "HTTPS")
    Rel(ui, mgmt_api, "API Calls", "JSON/HTTPS")
    Rel(mgmt_api, cp_db, "R/W Config")
    Rel(gateway, cp_db, "Reads Config")
    Rel(gateway, dp_db, "R/W Sessions")
   
    Rel(gateway, idp, "Federates", "SAML/OIDC")
    Rel(app, gateway, "Authenticate User With", "HTTPS")
    Rel(domainUser, app, "Uses", "Browser / Mobile")
    Rel(apim, gateway, "Introspect Token", "HTTPS")
    Rel(domainUser, apimPortal, "Uses")
    Rel(apimPortal, gateway, "Dynamic Client registration", "HTTPS")
```

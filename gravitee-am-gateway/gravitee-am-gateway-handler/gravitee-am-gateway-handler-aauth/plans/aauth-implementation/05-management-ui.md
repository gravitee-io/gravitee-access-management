# Phase 5: Management Console UI for AAUTH Agents

## Goal

Give administrators a way to browse, create, edit, enable/disable, and delete `Application(type=AAUTH_AGENT)` entries in the Gravitee AM management console. AAUTH agents are primarily auto-registered by Phase 4's `AAuthAgentRegistry`, but admins must also be able to pre-register agents (for closed-mode deployments where `autoRegisterAgents=false`) and to manage agents that have already appeared. The UI reuses the existing Application infrastructure in `gravitee-am-ui` and conditionally shows/hides sections based on the `AAUTH_AGENT` type.

## Discovery

**Specification references:**
- No AAUTH spec section prescribes management UI -- this is an AM implementation concern.

**Files to study:**
- `gravitee-am-ui/src/app/domain/applications/creation/steps/step1/step1.component.ts` -- type selector cards
- `gravitee-am-ui/src/app/domain/applications/application/` -- all tabs (overview, endpoints, idp, design, analytics, advanced/)
- `gravitee-am-ui/src/app/app-routing.module.ts` -- routing with `data.types.only` guards
- `gravitee-am-ui/src/app/domain/applications/application/advanced/general/general.component.html` -- General settings template with `*ngIf` conditionals on `application.type`

## Design

### Application creation wizard

Add a 5th card to the type selector (alongside Web, Single-Page App, Native, Backend to Backend):

| Card | Type enum | Title | Subtitle | Examples |
|------|-----------|-------|----------|----------|
| New | `AAUTH_AGENT` | AAUTH Agent | AI agents, autonomous services | e.g. LLM assistants, MCP clients |

Step 2 (Settings) for `AAUTH_AGENT` is minimal:
- **Name** (required)
- **Description** (optional)
- **Agent Metadata URL** (required -- becomes the `clientId`)

No grant types, no redirect URIs, no client secret generation.

### Application detail page — tab visibility

| Tab | AAUTH_AGENT | Reason |
|-----|-------------|--------|
| Overview | **Adapt** | AAUTH-specific summary (see below) |
| Endpoints | **Adapt** | AAUTH endpoint URLs only |
| Identity Providers | **Keep** | Controls which IdPs appear on the consent/interaction login screen when a user authenticates to approve agent access |
| Design | **Keep** | Admin may want to customize the consent page, emails, or flows per agent |
| Analytics | **Keep** | Token issuance metrics are relevant |
| Settings | **Adapt** | A few subsections hidden (see below) |

### Overview tab — AAUTH-specific content

Replace the OAuth grant-type summary with:

| Field | Source | Editable |
|-------|--------|----------|
| Agent Metadata URL | `Application.settings.oauth.clientId` | Read-only |
| Agent Name | `Application.name` (from metadata `client_name`) | Read-only on overview, editable in Settings > General |
| Status | `Application.enabled` | Toggle |
| Registration | "Auto-registered" or "Pre-registered" badge | Read-only (derived from `Application.metadata.aauth.firstSeenAt` presence) |
| First seen | `Application.metadata.aauth.firstSeenAt` | Read-only |
| Last seen | `Application.metadata.aauth.lastSeenAt` | Read-only |

### Endpoints tab — AAUTH endpoints

| Endpoint | URL pattern |
|----------|-------------|
| Token endpoint | `https://{domain}/aauth/token` |
| JWKS | `https://{domain}/aauth/jwks.json` |
| Issuer metadata | `https://{domain}/.well-known/aauth-person.json` |

### Settings subsections — visibility for AAUTH_AGENT

| Subsection | Action | Details |
|------------|--------|---------|
| **General** | **Adapt** | Keep: Name, Description, Type (read-only), Agent Metadata URL (relabeled Client ID). Hide: Client Secret, Redirect URIs, Post-Logout Redirect URIs, Single Sign Out, Silent Re-authentication. |
| **Secrets & Certificates** | **Adapt** | Hide: Client Secrets list. Keep: Certificate selector (admins can pick which cert signs auth_tokens for this agent). |
| **Application Metadata** | **Keep** | Shows `aauth.metadataUrl`, `aauth.firstSeenAt`, `aauth.lastSeenAt` plus any custom admin-added metadata. |
| **OAuth 2.0 / OIDC > Grant Flows** | **Hide** | No OAuth grant types for AAUTH agents. |
| **OAuth 2.0 / OIDC > Scopes** | **Keep** | Per-application scope settings (`ApplicationScopeSettings`). Controls which scopes the agent is allowed to request. This is the key admin lever for AAUTH scope validation (Phase 6). |
| **OAuth 2.0 / OIDC > Tokens** | **Adapt** | Show only auth_token lifetime. Hide refresh token / ID token lifetime settings. |
| **SAML 2.0** | **Hide** | Not applicable to AAUTH. |
| **Login** | **Keep** | Login form settings for the user authenticating at the interaction endpoint. |
| **Multifactor Auth** | **Keep** | Admin may require MFA before users approve access for high-sensitivity agents. |
| **User Accounts** | **Keep** | Account lockout, profile editing for users authenticating at the interaction endpoint. |
| **Password Policy** | **Keep** | Applies to user authentication at AM during consent. |
| **Session Management** | **Keep** | Session settings for user sessions during the interaction flow. |
| **Resources** | **Hide** | UMA-specific, not applicable to AAUTH. |
| **Administrative Roles** | **Keep** | Who can manage this agent Application. |

### Application list page

No structural changes needed. The existing list already shows all Applications. AAUTH agents appear with `type=AAUTH_AGENT` in the type column. A type filter (if present) should include `AAUTH_AGENT` as a filterable value.

## Implementation

### Files to Create

```
gravitee-am-ui/src/app/domain/applications/application/advanced/aauth/
  aauth-overview.component.ts        -- AAUTH-specific overview tab content
  aauth-overview.component.html
  aauth-endpoints.component.ts       -- AAUTH endpoint URLs
  aauth-endpoints.component.html
```

### Files to Modify

```
gravitee-am-ui/src/app/domain/applications/creation/steps/step1/
  step1.component.ts                 -- Add AAUTH_AGENT card to type selector
  step1.component.html               -- Card template

gravitee-am-ui/src/app/domain/applications/creation/steps/step2/
  step2.component.ts                 -- Minimal fields for AAUTH_AGENT
  step2.component.html               -- Conditional template

gravitee-am-ui/src/app/app-routing.module.ts
                                      -- Add AAUTH_AGENT to route guards
                                         (data.types.only arrays)

gravitee-am-ui/src/app/domain/applications/application/
  overview/                           -- Conditionally render AAUTH overview
  endpoints/                          -- Conditionally render AAUTH endpoints
  advanced/general/general.component.html
                                      -- Hide Client Secret, Redirect URIs,
                                         etc. for AAUTH_AGENT type
  advanced/oauth2/                    -- Hide Grant Flows subsection for
                                         AAUTH_AGENT; keep Scopes; adapt Tokens
```

### Key Implementation Details

**Conditional rendering pattern** (matches existing AM conventions):

The existing UI uses two mechanisms for type-based visibility:
1. **Route guards** via `data.types.only` in `app-routing.module.ts` -- controls which tabs appear in the navigation.
2. **`*ngIf` directives** in component templates -- controls which fields/sections appear within a tab.

For AAUTH_AGENT:

```typescript
// In app-routing.module.ts -- add AAUTH_AGENT to existing route guards
// Most tabs already list [WEB, NATIVE, BROWSER, RESOURCE_SERVER]; add AAUTH_AGENT:
{
  path: 'idp',
  component: ApplicationIdPComponent,
  data: { types: { only: ['WEB', 'NATIVE', 'BROWSER', 'RESOURCE_SERVER', 'AAUTH_AGENT'] } }
}
// Only SAML 2.0 and Resources routes exclude AAUTH_AGENT.
// Grant Flows is hidden via *ngIf in the template, not via route guard.
```

```html
<!-- In general.component.html -- hide redirect URIs for AAUTH_AGENT -->
<div *ngIf="application.type !== 'SERVICE' && application.type !== 'AAUTH_AGENT'">
  <!-- Redirect URIs section -->
</div>

<!-- Show Agent Metadata URL for AAUTH_AGENT -->
<div *ngIf="application.type === 'AAUTH_AGENT'">
  <mat-form-field>
    <mat-label>Agent Metadata URL</mat-label>
    <input matInput [value]="application.settings?.oauth?.clientId" readonly>
  </mat-form-field>
</div>
```

**Creation wizard step 1** -- new card:

```typescript
// In step1.component.ts
{
  type: 'AAUTH_AGENT',
  title: 'AAUTH Agent',
  subtitle: 'AI agents, autonomous services',
  examples: 'e.g. LLM assistants, MCP clients',
  icon: 'smart_toy'  // Material icon for AI/robot
}
```

**Creation wizard step 2** -- minimal form for AAUTH_AGENT:

```html
<div *ngIf="selectedType === 'AAUTH_AGENT'">
  <mat-form-field>
    <mat-label>Application Name</mat-label>
    <input matInput [(ngModel)]="application.name" required>
  </mat-form-field>
  <mat-form-field>
    <mat-label>Description</mat-label>
    <textarea matInput [(ngModel)]="application.description"></textarea>
  </mat-form-field>
  <mat-form-field>
    <mat-label>Agent Metadata URL</mat-label>
    <input matInput [(ngModel)]="application.settings.oauth.clientId" required
           placeholder="https://agent.example/.well-known/aauth-agent.json">
    <mat-hint>The agent's well-known metadata URL. Becomes the application's Client ID.</mat-hint>
  </mat-form-field>
</div>
```

## Validation

### Manual Testing Checklist

- [ ] "New application" wizard shows the AAUTH Agent card alongside the existing four types
- [ ] Selecting AAUTH Agent and clicking Next shows the minimal form (Name, Description, Agent Metadata URL)
- [ ] Creating an AAUTH Agent application persists it with `type=AAUTH_AGENT` and `clientId = metadata URL`
- [ ] The application list shows AAUTH agents alongside OIDC apps, with the correct type label
- [ ] Clicking an AAUTH agent opens the detail page with all standard tabs (Overview, Endpoints, Identity Providers, Design, Analytics, Settings)
- [ ] Overview tab shows AAUTH-specific content (metadata URL, status, registration badge, first/last seen)
- [ ] Endpoints tab shows AAUTH endpoint URLs
- [ ] Settings > General shows Name, Description, Type (read-only), Agent Metadata URL. Does NOT show Client Secret, Redirect URIs, SSO/SLO toggles.
- [ ] Settings > Secrets & Certificates shows the certificate selector but NOT the client secrets list
- [ ] Settings > OAuth 2.0 > Grant Flows is NOT visible
- [ ] Settings > OAuth 2.0 > Scopes IS visible and functional (admin can add/remove allowed scopes)
- [ ] Settings > OAuth 2.0 > Tokens shows auth_token lifetime only
- [ ] Settings > Login, MFA, User Accounts, Password Policy, Session Management ARE visible and functional (they configure the user's experience at the interaction endpoint)
- [ ] Settings > SAML 2.0 and Resources are NOT visible
- [ ] Settings > Administrative Roles IS visible and functional
- [ ] Application Metadata IS visible and shows the `aauth.*` entries
- [ ] Disabling an AAUTH agent (enabled=false) is reflected in the UI and prevents further token issuance
- [ ] Deleting an AAUTH agent removes it from the list

### E2E Tests (Phase 14 addition)

Add one test file to `gravitee-am-test/specs/gateway/aauth/`:

- `agent-management.jest.spec.ts` -- creates an AAUTH_AGENT application via the Management API, verifies it appears in the list endpoint, verifies the type is `AAUTH_AGENT`, updates its scope settings, disables it and confirms token requests are refused, re-enables and confirms they succeed, deletes it.

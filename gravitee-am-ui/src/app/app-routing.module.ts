/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { LoginComponent } from './login/login.component';
import { LoginCallbackComponent } from './login/callback/callback.component';
import { DomainsComponent } from './settings/domains/domains.component';
import { DomainComponent } from './domain/domain.component';
import { DomainDashboardComponent } from './domain/dashboard/dashboard.component';
import { DomainSettingsComponent } from './domain/settings/settings.component';
import { DomainSettingsGeneralComponent } from './domain/settings/general/general.component';
import { DomainSettingsOpenidClientRegistrationComponent } from './domain/settings/openid/client-registration/client-registration.component';
import { ClientRegistrationSettingsComponent } from './domain/settings/openid/client-registration/settings/settings.component';
import { ClientRegistrationDefaultScopeComponent } from './domain/settings/openid/client-registration/default-scope/default-scope.component';
import { ClientRegistrationAllowedScopeComponent } from './domain/settings/openid/client-registration/allowed-scope/allowed-scope.component';
import { ClientRegistrationTemplatesComponent } from './domain/settings/openid/client-registration/templates/templates.component';
import { DomainSettingsCertificatesComponent } from './domain/settings/certificates/certificates.component';
import { DomainSettingsProvidersComponent } from './domain/settings/providers/providers.component';
import { DomainSettingsRolesComponent } from './domain/settings/roles/roles.component';
import { DomainSettingsScopesComponent } from './domain/settings/scopes/scopes.component';
import { DomainSettingsFormsComponent } from './domain/settings/forms/forms.component';
import { DomainSettingsFormComponent } from './domain/settings/forms/form/form.component';
import { DomainSettingsLoginComponent } from './domain/settings/login/login.component';
import { DomainSettingsEmailsComponent } from './domain/settings/emails/emails.component';
import { DomainSettingsEmailComponent } from './domain/settings/emails/email/email.component';
import { DomainSettingsExtensionGrantsComponent } from './domain/settings/extension-grants/extension-grants.component';
import { DomainSettingsAccountComponent } from './domain/settings/account/account.component';
import { DomainSettingsSelfServiceAccountComponent } from './domain/settings/self-service-account/self-service-account.component';
import { DomainSettingsMembershipsComponent } from './domain/settings/memberships/memberships.component';
import { DomainSettingsFlowsComponent } from './domain/settings/flows/flows.component';
import { DomainCreationComponent } from './domain/creation/domain-creation.component';
import { ProviderCreationComponent } from './domain/settings/providers/creation/provider-creation.component';
import { ProviderComponent } from './domain/settings/providers/provider/provider.component';
import { LogoutCallbackComponent } from './logout/callback/callback.component';
import { LogoutComponent } from './logout/logout.component';
import { DomainsResolver } from './resolvers/domains.resolver';
import { DomainResolver } from './resolvers/domain.resolver';
import { DomainEntrypointResolver } from './resolvers/domain-entrypoint.resolver';
import { DomainFlowsResolver } from './resolvers/domain-flows.resolver';
import { ProvidersResolver } from './resolvers/providers.resolver';
import { ProviderResolver } from './resolvers/provider.resolver';
import { DataSourcesResolver } from './resolvers/datasources.resolver';
import { ProviderRolesComponent } from './domain/settings/providers/provider/roles/roles.component';
import { ProviderSettingsComponent } from './domain/settings/providers/provider/settings/settings.component';
import { ProviderMappersComponent } from './domain/settings/providers/provider/mappers/mappers.component';
import { CertificatesResolver } from './resolvers/certificates.resolver';
import { SignCertificatesResolver } from './resolvers/sign-certificates.resolver';
import { CertificateCreationComponent } from './domain/settings/certificates/creation/certificate-creation.component';
import { CertificateComponent } from './domain/settings/certificates/certificate/certificate.component';
import { CertificateResolver } from './resolvers/certificate.resolver';
import { RolesResolver } from './resolvers/roles.resolver';
import { PageRolesResolver } from './resolvers/page-roles.resolver';
import { RoleCreationComponent } from './domain/settings/roles/creation/role-creation.component';
import { RoleComponent } from './domain/settings/roles/role/role.component';
import { RoleResolver } from './resolvers/role.resolver';
import { ScopeResolver } from './resolvers/scope.resolver';
import { ScopesResolver } from './resolvers/scopes.resolver';
import { ScopeCreationComponent } from './domain/settings/scopes/creation/scope-creation.component';
import { ScopeComponent } from './domain/settings/scopes/scope/scope.component';
import { SettingsComponent } from './settings/settings.component';
import { SettingsMembershipsComponent } from './settings/memberships/memberships.component';
import { UsersComponent } from './domain/settings/users/users.component';
import { UserComponent } from './domain/settings/users/user/user.component';
import { UserResolver } from './resolvers/user.resolver';
import { UserCreationComponent } from './domain/settings/users/creation/user-creation.component';
import { UserProfileComponent } from './domain/settings/users/user/profile/profile.component';
import { UserApplicationsComponent } from './domain/settings/users/user/applications/applications.component';
import { UserApplicationComponent } from './domain/settings/users/user/applications/application/application.component';
import { UserRolesComponent } from './domain/settings/users/user/roles/roles.component';
import { UserRolesResolver } from './resolvers/user-roles.resolver';
import { DynamicUserRolesResolver } from './resolvers/dynamic-user-roles.resolver';
import { UserFactorsComponent } from './domain/settings/users/user/factors/factors.component';
import { UserCredentialsResolver } from './resolvers/user-credentials.resolver';
import { UserCredentialResolver } from './resolvers/user-credential.resolver';
import { UserCredentialsComponent } from './domain/settings/users/user/credentials/credentials.component';
import { UserCredentialComponent } from './domain/settings/users/user/credentials/credential/credential.component';
import { ExtensionGrantCreationComponent } from './domain/settings/extension-grants/creation/extension-grant-creation.component';
import { ExtensionGrantComponent } from './domain/settings/extension-grants/extension-grant/extension-grant.component';
import { ExtensionGrantsResolver } from './resolvers/extension-grants.resolver';
import { ExtensionGrantResolver } from './resolvers/extension-grant.resolver';
import { ManagementComponent } from './settings/management/management.component';
import { ManagementGeneralComponent } from './settings/management/general/general.component';
import { FormResolver } from './resolvers/form.resolver';
import { GroupsResolver } from './resolvers/groups.resolver';
import { GroupsComponent } from './domain/settings/groups/groups.component';
import { GroupCreationComponent } from './domain/settings/groups/creation/group-creation.component';
import { GroupResolver } from './resolvers/group.resolver';
import { GroupComponent } from './domain/settings/groups/group/group.component';
import { GroupSettingsComponent } from './domain/settings/groups/group/settings/settings.component';
import { GroupMembersComponent } from './domain/settings/groups/group/members/members.component';
import { GroupRolesComponent } from './domain/settings/groups/group/roles/roles.component';
import { GroupRolesResolver } from './resolvers/group-roles.resolver';
import { ScimComponent } from './domain/settings/scim/scim.component';
import { EmailResolver } from './resolvers/email.resolver';
import { ConsentsResolver } from './resolvers/consents.resolver';
import { AuditResolver } from './resolvers/audit.resolver';
import { AuditsComponent } from './domain/settings/audits/audits.component';
import { AuditComponent } from './domain/settings/audits/audit/audit.component';
import { AuditsSettingsComponent } from './domain/settings/audits/settings/settings.component';
import { ReportersResolver } from './resolvers/reporters.resolver';
import { ReporterResolver } from './resolvers/reporter.resolver';
import { ReporterComponent } from './domain/settings/audits/settings/reporter/reporter.component';
import { TagsResolver } from './resolvers/tags.resolver';
import { TagsComponent } from './settings/management/tags/tags.component';
import { TagCreationComponent } from './settings/management/tags/creation/tag-creation.component';
import { TagComponent } from './settings/management/tags/tag/tag.component';
import { TagResolver } from './resolvers/tag.resolver';
import { GroupMembersResolver } from './resolvers/group-members.resolver';
import { ApplicationsComponent } from './domain/applications/applications.component';
import { ApplicationsResolver } from './resolvers/applications.resolver';
import { ApplicationCreationComponent } from './domain/applications/creation/application-creation.component';
import { ApplicationComponent } from './domain/applications/application/application.component';
import { ApplicationOverviewComponent } from './domain/applications/application/overview/overview.component';
import { ApplicationEndpointsComponent } from './domain/applications/application/endpoints/endpoints.component';
import { ApplicationResolver } from './resolvers/application.resolver';
import { ApplicationPermissionsResolver } from './resolvers/application-permissions.resolver';
import { ApplicationIdPComponent } from './domain/applications/application/idp/idp.component';
import { ApplicationDesignComponent } from './domain/applications/application/design/design.component';
import { ApplicationFormsComponent } from './domain/applications/application/design/forms/forms.component';
import { ApplicationFormComponent } from './domain/applications/application/design/forms/form/form.component';
import { ApplicationEmailsComponent } from './domain/applications/application/design/emails/emails.component';
import { ApplicationEmailComponent } from './domain/applications/application/design/emails/email/email.component';
import { ApplicationAdvancedComponent } from './domain/applications/application/advanced/advanced.component';
import { ApplicationGeneralComponent } from './domain/applications/application/advanced/general/general.component';
import { PasswordPolicyComponent } from './domain/applications/application/advanced/password-policy/password-policy.component';
import { ApplicationAccountSettingsComponent } from './domain/applications/application/advanced/account/account.component';
import { OAuth2SettingsComponent } from './domain/components/oauth2-settings/component/oauth2-settings.component';
import { ApplicationSaml2Component } from './domain/applications/application/advanced/saml2/saml2.component';
import { ApplicationSecretsCertificatesComponent } from './domain/applications/application/advanced/secrets-certificates/secrets-certificates.component';
import { DomainMcpServerClientSecretsComponent } from './domain/mcp-servers/mcp-server/advanced/client-secrets/domain-mcp-server-client-secrets.component';
import { DomainMcpServerMembershipsComponent } from './domain/mcp-servers/mcp-server/advanced/memberships/memberships.component';
import { ApplicationMetadataComponent } from './domain/applications/application/advanced/metadata/metadata.component';
import { ApplicationMembershipsComponent } from './domain/applications/application/advanced/memberships/memberships.component';
import { ApplicationFactorsComponent } from './domain/applications/application/advanced/factors/factors.component';
import { ApplicationFlowsComponent } from './domain/applications/application/design/flows/flows.component';
import { ManagementRolesComponent } from './settings/management/roles/roles.component';
import { ManagementRoleComponent } from './settings/management/roles/role/role.component';
import { MembershipsResolver } from './resolvers/memberships.resolver';
import { McpServerMembershipsResolver } from './resolvers/mcp-server-memberships.resolver';
import { SettingsResolver } from './resolvers/settings.resolver';
import { AuthGuard } from './guards/auth-guard.service';
import { HomeComponent } from './home/home.component';
import { DomainSettingsFactorsComponent } from './domain/settings/factors/factors.component';
import { FactorsResolver } from './resolvers/factors.resolver';
import { FactorCreationComponent } from './domain/settings/factors/creation/factor-creation.component';
import { FactorComponent } from './domain/settings/factors/factor/factor.component';
import { FactorResolver } from './resolvers/factor.resolver';
import { EnrolledFactorsResolver } from './resolvers/enrolled-factors.resolver';
import { DomainSettingsResourcesComponent } from './domain/settings/resources/resources.component';
import { ResourceCreationComponent } from './domain/settings/resources/creation/resource-creation.component';
import { ResourceComponent } from './domain/settings/resources/resource/resource.component';
import { ResourceResolver } from './resolvers/resource.resolver';
import { ResourcesResolver } from './resolvers/resources.resolver';
import { NotFoundComponent } from './not-found/not-found.component';
import { EntrypointsComponent } from './settings/management/entrypoints/entrypoints.component';
import { EntrypointCreationComponent } from './settings/management/entrypoints/creation/entrypoint-creation.component';
import { EntrypointComponent } from './settings/management/entrypoints/entrypoint/entrypoint.component';
import { EntrypointResolver } from './resolvers/entrypoint.resolver';
import { EntrypointsResolver } from './resolvers/entrypoints.resolver';
import { UmaComponent } from './domain/settings/uma/uma.component';
import { ApplicationResourcesComponent } from './domain/applications/application/advanced/resources/resources.component';
import { ApplicationResourcesResolver } from './resolvers/application-resources.resolver';
import { ApplicationResourceComponent } from './domain/applications/application/advanced/resources/resource/resource.component';
import { ApplicationResourceResolver } from './resolvers/application-resource.resolver';
import { ApplicationResourcePolicyComponent } from './domain/applications/application/advanced/resources/resource/policies/policy/policy.component';
import { ApplicationResourcePolicyResolver } from './resolvers/application-resource-policy.resolver';
import { ApplicationFlowsResolver } from './resolvers/application-flows.resolver';
import { DomainSettingsEntrypointsComponent } from './domain/settings/entrypoints/entrypoints.component';
import { DomainSettingsWebAuthnComponent } from './domain/settings/webauthn/webauthn.component';
import { ApplicationLoginSettingsComponent } from './domain/applications/application/advanced/login/login.component';
import { IdentitiesResolver } from './resolvers/identities.resolver';
import { IdentitiesOrganizationResolver } from './resolvers/identities-organization.resolver';
import { PluginPoliciesResolver } from './resolvers/plugin-policies.resolver';
import { PlatformFlowSchemaResolver } from './resolvers/platform-flow-schema.resolver';
import { NewsletterComponent } from './newsletter/newsletter.component';
import { NewsletterResolver } from './resolvers/newsletter.resolver';
import { ApplicationAnalyticsComponent } from './domain/applications/application/analytics/analytics.component';
import { UserHistoryComponent } from './domain/settings/users/user/history/history.component';
import { EnvironmentResolver } from './resolvers/environment-resolver.service';
import { DummyComponent } from './components/dummy/dummy.component';
import { CockpitComponent } from './settings/cockpit/cockpit.component';
import { InstallationResolver } from './resolvers/installation.resolver';
import { EnvironmentComponent } from './environment/environment.component';
import { PluginReportersResolver } from './resolvers/plugin-reporters.resolver';
import { DomainAlertGeneralComponent } from './domain/alerts/general/general.component';
import { DomainAlertsComponent } from './domain/alerts/alerts.component';
import { NotifiersResolver } from './resolvers/notifiers.resolver';
import { AlertNotifiersResolver } from './resolvers/alert-notifiers.resolver';
import { DomainAlertNotifiersComponent } from './domain/alerts/notifiers/notifiers.component';
import { AlertNotifierResolver } from './resolvers/alert-notifier.resolver';
import { DomainAlertNotifierCreationComponent } from './domain/alerts/notifiers/creation/notifier-creation.component';
import { DomainAlertNotifierComponent } from './domain/alerts/notifiers/notifier/notifier.component';
import { PlatformAlertStatusResolver } from './resolvers/platform-alert-status.resolver';
import { FactorPluginsResolver } from './resolvers/factor-plugins.resolver';
import { ResourcePluginsResolver } from './resolvers/resource-plugins.resolver';
import { DomainSettingsBotDetectionsComponent } from './domain/settings/botdetections/bot-detections.component';
import { BotDetectionsResolver } from './resolvers/bot-detections.resolver';
import { BotDetectionCreationComponent } from './domain/settings/botdetections/creation/bot-detection-creation.component';
import { BotDetectionPluginsResolver } from './resolvers/bot-detection-plugins.resolver';
import { BotDetectionComponent } from './domain/settings/botdetections/bot-detection/bot-detection.component';
import { BotDetectionResolver } from './resolvers/bot-detection.resolver';
import { ScopesAllResolver } from './resolvers/scopes-all.resolver';
import { OIDCProfileComponent } from './domain/settings/openid/oidc-profile/oidc-profile.component';
import { DomainSettingsDeviceIdentifiersComponent } from './domain/settings/deviceidentifiers/device-identifiers.component';
import { DeviceIdentifierPluginsResolver } from './resolvers/device-identifier-plugins.resolver';
import { DeviceIdentifierCreationComponent } from './domain/settings/deviceidentifiers/creation/device-identifier-creation.component';
import { DeviceIdentifiersResolver } from './resolvers/device-identifiers.resolver';
import { DeviceIdentifierResolver } from './resolvers/device-identifier.resolver';
import { DeviceIdentifierComponent } from './domain/settings/deviceidentifiers/device-identifier/device-identifier.component';
import { UserDevicesComponent } from './domain/settings/users/user/devices/devices.component';
import { UserDevicesResolver } from './resolvers/user-devices.resolver';
import { DomainSettingsAuthorizationEnginesComponent } from './domain/authorization-engines/authorization-engines.component';
import { AuthorizationEngineCreationComponent } from './domain/authorization-engines/creation/authorization-engine-creation.component';
import { OpenFGAComponent } from './domain/authorization-engines/openfga/openfga.component';
import { AuthorizationEnginesResolver } from './resolvers/authorization-engines.resolver';
import { AuthorizationEnginePluginsResolver } from './resolvers/authorization-engine-plugins.resolver';
import { AuthorizationEngineResolver } from './resolvers/authorization-engine.resolver';
import { CibaComponent } from './domain/settings/openid/ciba/ciba.component';
import { CibaSettingsComponent } from './domain/settings/openid/ciba/settings/ciba-settings.component';
import { Saml2Component } from './domain/settings/saml2/saml2.component';
import { DeviceNotifiersComponent } from './domain/settings/openid/ciba/device-notifiers/device-notifiers.component';
import { DeviceNotifiersCreationComponent } from './domain/settings/openid/ciba/device-notifiers/create/device-notifiers-creation.component';
import { DeviceNotifiersResolver } from './resolvers/device-notifiers.resolver';
import { DeviceNotifierPluginsResolver } from './resolvers/device-notifier-plugins.resolver';
import { DeviceNotifierResolver } from './resolvers/device-notifier.resolver';
import { DeviceNotifierComponent } from './domain/settings/openid/ciba/device-notifiers/device-notifier/device-notifier.component';
import { ApplicationCookieSettingsComponent } from './domain/applications/application/advanced/cookie/cookie.component';
import { DomainSettingsDictionariesComponent } from './domain/settings/texts/dictionaries.component';
import { DictionariesResolver } from './resolvers/dictionaries.resolver';
import { DomainSettingsThemeComponent } from './domain/settings/theme/theme.component';
import { ThemesResolver } from './resolvers/themes.resolver';
import { LicenseGuard } from './guards/license-guard.service';
import { AmFeature } from './components/gio-license/gio-license-data';
import { UserIdentitiesComponent } from './domain/settings/users/user/identities/identities.component';
import { UserIdentitiesResolver } from './resolvers/user-identities.resolver';
import { PasswordPoliciesComponent } from './domain/settings/password-policies/domain-password-policies.component';
import { DomainPasswordPolicyComponent } from './domain/settings/password-policy/domain-password-policy.component';
import { PasswordPolicyResolver } from './resolvers/password-policy-resolver';
import { PasswordPoliciesResolver } from './resolvers/password-policies-resolver.service';
import { ProviderGroupsComponent } from './domain/settings/providers/provider/groups/groups/groups.component';
import { DataPlanesResolver } from './resolvers/data-planes.resolver';
import { DomainSettingsSecretsComponent } from './domain/settings/secrets/secrets.component';
import { DomainMcpServersComponent } from './domain/mcp-servers/mcp-servers.component';
import { DomainNewMcpServerComponent } from './domain/mcp-servers/mcp-server-new/new-mcp-server.component';
import { DomainMcpServerComponent } from './domain/mcp-servers/mcp-server/domain-mcp-server.component';
import { McpServerResolver } from './resolvers/mcp-server.resolver';
import { DomainMcpServerOverviewComponent } from './domain/mcp-servers/mcp-server/overview/overview.component';
import { DomainMcpServerToolsComponent } from './domain/mcp-servers/mcp-server/tools/tools.component';
import { DomainMcpServerAdvancedComponent } from './domain/mcp-servers/mcp-server/advanced/advanced.component';
import { DomainMcpServerGeneralComponent } from './domain/mcp-servers/mcp-server/advanced/general/general.component';
import { TokenExchangeComponent } from './domain/settings/oauth/token-exchange/token-exchange.component';
import { DomainGrantTypesResolver } from './resolvers/domain-grant-types.resolver';
import { ApplicationOAuth2Service, McpServerOAuth2Service, OAUTH2_SETTINGS_SERVICE } from './services/oauth2-settings.service';

const applyOnLabel = (label) => label.toLowerCase().replace(/_/g, ' ');

export const routes: Routes = [
  {
    path: 'settings',
    component: SettingsComponent,
    data: {
      perms: {
        only: ['organization_settings_read'],
      },
    },
    children: [
      {
        path: '',
        component: ManagementComponent,
        data: {
          menu: {
            level: 'top',
            label: 'Settings',
            icon: 'gio:settings',
          },
          perms: {
            only: ['organization_settings_read'],
          },
        },
        children: [
          { path: '', redirectTo: 'general', pathMatch: 'full' },
          {
            path: 'general',
            component: ManagementGeneralComponent,
            canActivate: [AuthGuard],
            resolve: {
              settings: SettingsResolver,
            },
            data: {
              menu: {
                label: 'Authentication',
                section: 'Console',
                level: 'level2',
              },
              perms: {
                only: ['organization_settings_read'],
              },
            },
          },
          {
            path: 'members',
            component: SettingsMembershipsComponent,
            canActivate: [AuthGuard],
            resolve: {
              members: MembershipsResolver,
            },
            data: {
              menu: {
                label: 'Administrative roles',
                section: 'Console',
                level: 'level2',
              },
              perms: {
                only: ['organization_member_list'],
              },
            },
          },
          {
            path: 'forms',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Forms',
                section: 'Console',
                level: 'level2',
              },
              perms: {
                only: ['organization_form_list'],
              },
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: DomainSettingsFormsComponent,
              },
              {
                path: 'form',
                component: DomainSettingsFormComponent,
                resolve: {
                  form: FormResolver,
                },
                data: {
                  breadcrumb: {
                    label: 'form.template',
                    applyOnLabel: applyOnLabel,
                  },
                  perms: {
                    only: ['organization_form_read'],
                  },
                },
              },
            ],
          },
          {
            path: 'providers',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Identity providers',
                section: 'Console',
                level: 'level2',
              },
              perms: {
                only: ['organization_identity_provider_list'],
              },
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: DomainSettingsProvidersComponent,
                resolve: {
                  providers: ProvidersResolver,
                  identities: IdentitiesOrganizationResolver,
                },
              },
              {
                path: 'new',
                component: ProviderCreationComponent,
                canActivate: [AuthGuard],
                resolve: {
                  identities: IdentitiesOrganizationResolver,
                  datasources: DataSourcesResolver,
                },
                data: {
                  perms: {
                    only: ['organization_identity_provider_create'],
                  },
                },
              },
              {
                path: ':providerId',
                component: ProviderComponent,
                canActivate: [AuthGuard],
                resolve: {
                  provider: ProviderResolver,
                  datasources: DataSourcesResolver,
                },
                data: {
                  breadcrumb: {
                    label: 'provider.name',
                  },
                  perms: {
                    only: ['organization_identity_provider_read'],
                  },
                },
                children: [
                  { path: '', redirectTo: 'settings', pathMatch: 'full' },
                  { path: 'settings', component: ProviderSettingsComponent },
                  { path: 'mappers', component: ProviderMappersComponent },
                  { path: 'roles', component: ProviderRolesComponent, resolve: { roles: RolesResolver } },
                  {
                    path: 'groups',
                    component: ProviderGroupsComponent,
                    resolve: { groups: GroupsResolver },
                    data: { organizationContext: true },
                  },
                ],
              },
            ],
          },
          {
            path: 'users',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Users',
                section: 'User Management',
                level: 'level2',
              },
              perms: {
                only: ['organization_user_list'],
              },
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: UsersComponent,
              },
              {
                path: 'new',
                component: UserCreationComponent,
                canActivate: [AuthGuard],
                data: {
                  perms: {
                    only: ['organization_user_create'],
                  },
                },
              },
              {
                path: ':userId',
                component: UserComponent,
                canActivate: [AuthGuard],
                resolve: {
                  user: UserResolver,
                },
                data: {
                  breadcrumb: {
                    label: 'user.username',
                  },
                  perms: {
                    only: ['organization_user_read'],
                  },
                },
                children: [
                  {
                    path: '',
                    redirectTo: 'profile',
                    pathMatch: 'full',
                  },
                  {
                    path: 'profile',
                    component: UserProfileComponent,
                  },
                ],
              },
            ],
          },
          {
            path: 'groups',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Groups',
                section: 'User Management',
                level: 'level2',
              },
              perms: {
                only: ['organization_group_list'],
              },
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: GroupsComponent,
                resolve: {
                  groups: GroupsResolver,
                },
              },
              {
                path: 'new',
                component: GroupCreationComponent,
                canActivate: [AuthGuard],
                data: {
                  perms: {
                    only: ['organization_group_create'],
                  },
                },
              },
              {
                path: ':groupId',
                component: GroupComponent,
                canActivate: [AuthGuard],
                resolve: {
                  group: GroupResolver,
                },
                data: {
                  breadcrumb: {
                    label: 'group.name',
                  },
                  perms: {
                    only: ['organization_group_read'],
                  },
                },
                children: [
                  { path: '', redirectTo: 'settings', pathMatch: 'full' },
                  { path: 'settings', component: GroupSettingsComponent },
                  { path: 'members', component: GroupMembersComponent, resolve: { members: GroupMembersResolver } },
                ],
              },
            ],
          },
          {
            path: 'roles',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Roles',
                section: 'User Management',
                level: 'level2',
              },
              perms: {
                only: ['organization_role_list'],
              },
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: ManagementRolesComponent,
                resolve: {
                  roles: RolesResolver,
                },
              },
              {
                path: 'new',
                component: RoleCreationComponent,
                canActivate: [AuthGuard],
                data: {
                  perms: {
                    only: ['organization_role_create'],
                  },
                },
              },
              {
                path: ':roleId',
                component: ManagementRoleComponent,
                canActivate: [AuthGuard],
                resolve: {
                  role: RoleResolver,
                },
                data: {
                  breadcrumb: {
                    label: 'role.name',
                  },
                  perms: {
                    only: ['organization_role_read'],
                  },
                },
              },
            ],
          },
          {
            path: 'tags',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Sharding tags',
                section: 'Gateway',
                level: 'level2',
              },
              perms: {
                only: ['organization_tag_list'],
              },
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: TagsComponent,
                resolve: {
                  tags: TagsResolver,
                },
              },
              {
                path: 'new',
                component: TagCreationComponent,
                canActivate: [AuthGuard],
                data: {
                  perms: {
                    only: ['organization_tag_create'],
                  },
                },
              },
              {
                path: ':tagId',
                component: TagComponent,
                canActivate: [AuthGuard],
                resolve: {
                  tag: TagResolver,
                },
                data: {
                  breadcrumb: {
                    label: 'tag.name',
                  },
                  perms: {
                    only: ['organization_tag_read'],
                  },
                },
              },
            ],
          },
          {
            path: 'entrypoints',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Entrypoints',
                section: 'Gateway',
                level: 'level2',
              },
              perms: {
                only: ['organization_entrypoint_list'],
              },
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: EntrypointsComponent,
                resolve: {
                  entrypoints: EntrypointsResolver,
                },
              },
              {
                path: 'new',
                component: EntrypointCreationComponent,
                canActivate: [AuthGuard],
                data: {
                  perms: {
                    only: ['organization_entrypoint_create'],
                  },
                },
              },
              {
                path: ':entrypointId',
                component: EntrypointComponent,
                canActivate: [AuthGuard],
                resolve: {
                  entrypoint: EntrypointResolver,
                  tags: TagsResolver,
                },
                data: {
                  breadcrumb: {
                    label: 'entrypoint.name',
                  },
                  perms: {
                    only: ['organization_entrypoint_read'],
                  },
                },
              },
            ],
          },
          {
            path: 'audits',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Audit',
                section: 'Audit',
                level: 'level2',
              },
              perms: {
                only: ['organization_audit_list'],
              },
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: AuditsComponent,
              },
              {
                path: 'settings',
                children: [
                  {
                    path: '',
                    pathMatch: 'full',
                    component: AuditsSettingsComponent,
                    canActivate: [AuthGuard],
                    resolve: {
                      reporters: ReportersResolver,
                    },
                    data: {
                      perms: {
                        only: ['organization_reporter_list'],
                      },
                    },
                  },
                  {
                    path: 'new',
                    component: ReporterComponent,
                    canActivate: [AuthGuard],
                    resolve: {
                      reporterPlugins: PluginReportersResolver,
                    },
                    data: {
                      createMode: true,
                      organizationContext: true,
                      perms: {
                        only: ['organization_reporter_create'],
                      },
                    },
                  },
                  {
                    path: ':reporterId',
                    component: ReporterComponent,
                    resolve: {
                      reporter: ReporterResolver,
                      reporterPlugins: PluginReportersResolver,
                    },
                    data: {
                      createMode: false,
                      organizationContext: true,
                      breadcrumb: {
                        label: 'reporter.name',
                      },
                      perms: {
                        only: ['organization_reporter_read'],
                      },
                    },
                  },
                ],
              },
              {
                path: ':auditId',
                component: AuditComponent,
                canActivate: [AuthGuard],
                resolve: {
                  audit: AuditResolver,
                },
                data: {
                  breadcrumb: {
                    label: 'audit.id',
                  },
                  perms: {
                    only: ['organization_audit_read'],
                  },
                },
              },
            ],
          },
          {
            path: 'cockpit',
            component: CockpitComponent,
            canActivate: [AuthGuard],
            resolve: {
              installation: InstallationResolver,
            },
            data: {
              menu: {
                label: 'Discover cockpit',
                section: 'Cockpit',
                level: 'level2',
              },
              perms: {
                only: ['installation_read'],
              },
            },
          },
        ],
      },
    ],
  },
  {
    path: 'environments',
    data: {
      breadcrumb: {
        disabled: true,
      },
    },
    children: [
      {
        path: ':envHrid',
        canActivate: [AuthGuard],
        data: {
          breadcrumb: {
            disabled: true,
          },
        },
        resolve: {
          environment: EnvironmentResolver,
        },
        children: [
          {
            path: '',
            pathMatch: 'full',
            component: EnvironmentComponent,
          },
          {
            path: 'domains',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Domains',
                level: 'top',
                icon: 'gio:report-columns',
                routerLinkActiveOptions: { exact: true },
                displayOptions: { exact: true },
              },
              breadcrumb: {
                label: 'domains',
              },
              perms: {
                only: ['domain_list'],
              },
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: DomainsComponent,
                resolve: {
                  domains: DomainsResolver,
                },
                canActivate: [AuthGuard],
                data: {
                  perms: {
                    only: ['domain_list'],
                  },
                },
              },
              {
                path: 'new',
                component: DomainCreationComponent,
                resolve: {
                  environment: EnvironmentResolver,
                  dataPlanes: DataPlanesResolver,
                },
                canActivate: [AuthGuard],
                data: {
                  perms: {
                    only: ['domain_create'],
                  },
                },
              },
              {
                path: ':domainId',
                component: DomainComponent,
                resolve: {
                  domain: DomainResolver,
                  // permissions: DomainPermissionsResolver
                },
                data: {
                  breadcrumb: {
                    label: 'domain.name',
                  },
                },
                children: [
                  {
                    path: '',
                    component: DomainComponent,
                  },
                  {
                    path: 'dashboard',
                    component: DomainDashboardComponent,
                    canActivate: [AuthGuard],
                    data: {
                      menu: {
                        label: 'Dashboard',
                        icon: 'gio:home',
                        level: 'top',
                      },
                      breadcrumb: {
                        include: true,
                      },
                      perms: {
                        only: ['domain_analytics_read'],
                      },
                    },
                  },
                  {
                    path: 'applications',
                    data: {
                      menu: {
                        label: 'Applications',
                        icon: 'gio:multi-window',
                        level: 'top',
                      },
                      perms: {
                        only: ['application_list'],
                      },
                    },
                    children: [
                      {
                        path: '',
                        pathMatch: 'full',
                        component: ApplicationsComponent,
                        resolve: {
                          applications: ApplicationsResolver,
                        },
                      },
                      {
                        path: 'new',
                        component: ApplicationCreationComponent,
                        canActivate: [AuthGuard],
                        data: {
                          perms: {
                            only: ['application_create'],
                          },
                        },
                      },
                      {
                        path: ':appId',
                        component: ApplicationComponent,
                        resolve: {
                          application: ApplicationResolver,
                          permissions: ApplicationPermissionsResolver,
                        },
                        runGuardsAndResolvers: 'pathParamsOrQueryParamsChange',
                        data: {
                          breadcrumb: {
                            label: 'application.name',
                          },
                        },
                        children: [
                          {
                            path: '',
                            redirectTo: 'overview',
                            pathMatch: 'full',
                          },
                          {
                            path: 'overview',
                            component: ApplicationOverviewComponent,
                            data: {
                              menu: {
                                label: 'Overview',
                                section: 'Overview',
                                level: 'level2',
                              },
                            },
                            resolve: {
                              entrypoint: DomainEntrypointResolver,
                            },
                          },
                          {
                            path: 'endpoints',
                            component: ApplicationEndpointsComponent,
                            data: {
                              menu: {
                                label: 'Endpoints',
                                section: 'Endpoints',
                                level: 'level2',
                              },
                            },
                            resolve: {
                              entrypoint: DomainEntrypointResolver,
                            },
                          },
                          {
                            path: 'idp',
                            component: ApplicationIdPComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              identities: IdentitiesResolver,
                            },
                            data: {
                              menu: {
                                label: 'Identity Providers',
                                section: 'Identity Providers',
                                level: 'level2',
                              },
                              perms: {
                                only: ['application_identity_provider_list'],
                              },
                              types: {
                                only: ['WEB', 'NATIVE', 'BROWSER', 'RESOURCE_SERVER'],
                              },
                            },
                          },
                          {
                            path: 'design',
                            component: ApplicationDesignComponent,
                            data: {
                              menu: {
                                label: 'Design',
                                section: 'Design',
                                level: 'level2',
                              },
                              perms: {
                                only: [
                                  'application_email_template_list',
                                  'application_email_template_read',
                                  'application_form_list',
                                  'application_form_read',
                                ],
                              },
                              types: {
                                only: ['WEB', 'NATIVE', 'BROWSER', 'RESOURCE_SERVER'],
                              },
                            },
                            children: [
                              {
                                path: 'forms',
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Forms',
                                    section: 'Design',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_form_list', 'application_form_read'],
                                  },
                                },
                                children: [
                                  {
                                    path: '',
                                    pathMatch: 'full',
                                    component: ApplicationFormsComponent,
                                  },
                                  {
                                    path: 'form',
                                    component: ApplicationFormComponent,
                                    canActivate: [AuthGuard],
                                    resolve: { form: FormResolver },
                                    data: {
                                      breadcrumb: {
                                        label: 'form.template',
                                        applyOnLabel: applyOnLabel,
                                      },
                                      perms: {
                                        only: ['application_form_read'],
                                      },
                                    },
                                  },
                                ],
                              },
                              {
                                path: 'emails',
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Emails',
                                    section: 'Design',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_email_template_list', 'application_email_template_read'],
                                  },
                                },
                                children: [
                                  {
                                    path: '',
                                    pathMatch: 'full',
                                    component: ApplicationEmailsComponent,
                                    canActivate: [AuthGuard],
                                  },
                                  {
                                    path: 'email',
                                    component: ApplicationEmailComponent,
                                    canActivate: [AuthGuard],
                                    resolve: {
                                      email: EmailResolver,
                                    },
                                    data: {
                                      breadcrumb: {
                                        label: 'email.template',
                                        applyOnLabel: applyOnLabel,
                                      },
                                      perms: {
                                        only: ['application_email_template_read'],
                                      },
                                    },
                                  },
                                ],
                              },
                              {
                                path: 'flows',
                                component: ApplicationFlowsComponent,
                                canActivate: [AuthGuard],
                                resolve: {
                                  flows: ApplicationFlowsResolver,
                                  policies: PluginPoliciesResolver,
                                  flowSettingsForm: PlatformFlowSchemaResolver,
                                  factors: FactorsResolver,
                                },
                                data: {
                                  menu: {
                                    label: 'Flows',
                                    section: 'Design',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_flow_list', 'application_flow_read'],
                                  },
                                },
                              },
                            ],
                          },
                          {
                            path: 'analytics',
                            component: ApplicationAnalyticsComponent,
                            canActivate: [AuthGuard],
                            data: {
                              menu: {
                                label: 'Analytics',
                                section: 'Analytics',
                                level: 'level2',
                              },
                              perms: {
                                only: ['application_analytics_list'],
                              },
                              types: {
                                only: ['WEB', 'NATIVE', 'BROWSER', 'RESOURCE_SERVER'],
                              },
                            },
                          },
                          {
                            path: 'settings',
                            component: ApplicationAdvancedComponent,
                            data: {
                              menu: {
                                label: 'Settings',
                                section: 'Settings',
                                level: 'level2',
                              },
                              perms: {
                                only: ['application_settings_read', 'application_oauth_read', 'application_certificate_list'],
                              },
                            },
                            children: [
                              {
                                path: 'general',
                                component: ApplicationGeneralComponent,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'General',
                                    section: 'Settings',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_settings_read'],
                                  },
                                },
                              },
                              {
                                path: 'secrets-certificates',
                                component: ApplicationSecretsCertificatesComponent,
                                canActivate: [AuthGuard],
                                resolve: { certificates: SignCertificatesResolver },
                                data: {
                                  menu: {
                                    label: 'Secrets & Certificates',
                                    section: 'Security',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_settings_read', 'application_certificate_list'],
                                  },
                                },
                              },

                              {
                                path: 'metadata',
                                component: ApplicationMetadataComponent,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Application metadata',
                                    section: 'Settings',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_settings_read'],
                                  },
                                },
                              },
                              {
                                path: 'oauth2',
                                component: OAuth2SettingsComponent,
                                providers: [
                                  {
                                    provide: OAUTH2_SETTINGS_SERVICE,
                                    useClass: ApplicationOAuth2Service,
                                  },
                                ],
                                canActivate: [AuthGuard],
                                resolve: {
                                  domainGrantTypes: ExtensionGrantsResolver,
                                  scopes: ScopesAllResolver,
                                },
                                data: {
                                  menu: {
                                    label: 'OAuth 2.0 / OIDC',
                                    section: 'Settings',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_openid_read'],
                                  },
                                },
                              },
                              {
                                path: 'saml2',
                                component: ApplicationSaml2Component,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'SAML 2.0',
                                    section: 'Settings',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_saml_read'],
                                  },
                                  types: {
                                    only: ['WEB', 'NATIVE', 'BROWSER'],
                                  },
                                  protocol: 'SAML',
                                },
                                resolve: {
                                  certificates: CertificatesResolver,
                                },
                              },
                              {
                                path: 'login',
                                component: ApplicationLoginSettingsComponent,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Login',
                                    section: 'Settings',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_settings_read'],
                                  },
                                  types: {
                                    only: ['WEB', 'NATIVE', 'BROWSER', 'RESOURCE_SERVER'],
                                  },
                                },
                              },
                              {
                                path: 'members',
                                component: ApplicationMembershipsComponent,
                                canActivate: [AuthGuard],
                                resolve: {
                                  members: MembershipsResolver,
                                },
                                data: {
                                  menu: {
                                    label: 'Administrative roles',
                                    section: 'Settings',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_member_list'],
                                  },
                                },
                              },
                              {
                                path: 'factors',
                                component: ApplicationFactorsComponent,
                                canActivate: [AuthGuard],
                                resolve: {
                                  deviceIdentifiers: DeviceIdentifiersResolver,
                                },
                                data: {
                                  menu: {
                                    label: 'Multifactor Auth',
                                    section: 'Security',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_factor_list'],
                                  },
                                  types: {
                                    only: ['WEB', 'NATIVE', 'BROWSER', 'RESOURCE_SERVER'],
                                  },
                                },
                              },
                              {
                                path: 'account',
                                component: ApplicationAccountSettingsComponent,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'User Accounts',
                                    section: 'Security',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_settings_read'],
                                  },
                                  types: {
                                    only: ['WEB', 'NATIVE', 'BROWSER', 'RESOURCE_SERVER'],
                                  },
                                },
                              },
                              {
                                path: 'password-policy',
                                component: PasswordPolicyComponent,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Password policy',
                                    section: 'Security',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_settings_read'],
                                  },
                                },
                              },
                              {
                                path: 'session',
                                component: ApplicationCookieSettingsComponent,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Session management',
                                    section: 'Security',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_settings_read'],
                                  },
                                },
                              },
                              {
                                path: 'resources',
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Resources',
                                    section: 'Security',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['application_resource_list'],
                                  },
                                  types: {
                                    only: ['RESOURCE_SERVER'],
                                  },
                                },
                                children: [
                                  {
                                    path: '',
                                    pathMatch: 'full',
                                    component: ApplicationResourcesComponent,
                                    resolve: {
                                      resources: ApplicationResourcesResolver,
                                    },
                                  },
                                  {
                                    path: ':resourceId',
                                    canActivate: [AuthGuard],
                                    resolve: {
                                      resource: ApplicationResourceResolver,
                                    },
                                    data: {
                                      breadcrumb: {
                                        label: 'resource.name',
                                      },
                                      perms: {
                                        only: ['application_resource_read'],
                                      },
                                    },
                                    children: [
                                      {
                                        path: '',
                                        pathMatch: 'full',
                                        component: ApplicationResourceComponent,
                                      },
                                      {
                                        path: 'policies/:policyId',
                                        component: ApplicationResourcePolicyComponent,
                                        canActivate: [AuthGuard],
                                        resolve: {
                                          policy: ApplicationResourcePolicyResolver,
                                        },
                                        data: {
                                          breadcrumb: {
                                            label: 'policy.name',
                                          },
                                          perms: {
                                            only: ['application_resource_read'],
                                          },
                                        },
                                      },
                                    ],
                                  },
                                ],
                              },
                            ],
                          },
                        ],
                      },
                    ],
                  },
                  {
                    path: 'mcp-servers',
                    canActivate: [AuthGuard],
                    data: {
                      menu: {
                        label: 'MCP Servers',
                        icon: 'gio:mcp',
                        level: 'top',
                      },
                      breadcrumb: {
                        include: true,
                      },
                      perms: {
                        only: ['protected_resource_list'],
                      },
                    },
                    children: [
                      {
                        path: '',
                        component: DomainMcpServersComponent,
                        pathMatch: 'full',
                        runGuardsAndResolvers: 'pathParamsOrQueryParamsChange',
                      },
                      {
                        path: 'new',
                        component: DomainNewMcpServerComponent,
                        resolve: {
                          scopes: ScopesAllResolver,
                        },
                        canActivate: [AuthGuard],
                      },
                      {
                        path: ':mcpServerId',
                        component: DomainMcpServerComponent,
                        runGuardsAndResolvers: 'pathParamsOrQueryParamsChange',
                        resolve: {
                          mcpServer: McpServerResolver,
                        },
                        children: [
                          {
                            path: '',
                            redirectTo: 'overview',
                            pathMatch: 'full',
                          },
                          {
                            path: 'overview',
                            component: DomainMcpServerOverviewComponent,
                            data: {
                              menu: {
                                label: 'Overview',
                                section: 'Overview',
                                level: 'level2',
                              },
                            },
                            resolve: {
                              entrypoint: DomainEntrypointResolver,
                            },
                          },
                          {
                            path: 'tools',
                            component: DomainMcpServerToolsComponent,
                            data: {
                              menu: {
                                label: 'Tools',
                                section: 'Tools',
                                level: 'level2',
                              },
                            },
                            resolve: {
                              entrypoint: DomainEntrypointResolver,
                            },
                          },

                          {
                            path: 'settings',
                            component: DomainMcpServerAdvancedComponent,
                            data: {
                              menu: {
                                label: 'Settings',
                                section: 'Settings',
                                level: 'level2',
                              },
                              perms: {
                                only: ['protected_resource_read', 'protected_resource_update'],
                              },
                            },
                            children: [
                              {
                                path: 'general',
                                component: DomainMcpServerGeneralComponent,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'General',
                                    section: 'Settings',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['protected_resource_read'],
                                  },
                                },
                              },
                              {
                                path: 'secrets-certificates',
                                component: DomainMcpServerClientSecretsComponent,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Secrets & Certificates',
                                    section: 'Security',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['protected_resource_read'],
                                  },
                                },
                                resolve: {
                                  certificates: SignCertificatesResolver,
                                  entrypoint: DomainEntrypointResolver,
                                },
                              },
                              {
                                path: 'oauth2',
                                component: OAuth2SettingsComponent,
                                providers: [
                                  {
                                    provide: OAUTH2_SETTINGS_SERVICE,
                                    useClass: McpServerOAuth2Service,
                                  },
                                ],
                                data: {
                                  menu: {
                                    label: 'OAuth 2.0 / OIDC',
                                    section: 'Security',
                                    level: 'level3',
                                  },
                                },
                                resolve: {
                                  domainGrantTypes: DomainGrantTypesResolver,
                                  scopes: ScopesAllResolver,
                                },
                              },
                              {
                                path: 'members',
                                component: DomainMcpServerMembershipsComponent,
                                canActivate: [AuthGuard],
                                resolve: {
                                  members: McpServerMembershipsResolver,
                                },
                                data: {
                                  menu: {
                                    label: 'Administrative roles',
                                    section: 'Settings',
                                    level: 'level3',
                                  },
                                  perms: {
                                    only: ['protected_resource_member_list'],
                                  },
                                },
                              },
                            ],
                          },
                        ],
                      },
                    ],
                  },
                  {
                    path: 'authorization-engines',
                    canActivate: [AuthGuard],
                    data: {
                      menu: {
                        label: 'Authorization',
                        icon: 'gio:shield-check',
                        level: 'top',
                      },
                      perms: {
                        only: ['domain_authorization_engine_list'],
                      },
                    },
                    children: [
                      {
                        path: '',
                        pathMatch: 'full',
                        component: DomainSettingsAuthorizationEnginesComponent,
                        resolve: {
                          authorizationEngines: AuthorizationEnginesResolver,
                          authorizationEnginePlugins: AuthorizationEnginePluginsResolver,
                        },
                      },
                      {
                        path: 'new',
                        component: AuthorizationEngineCreationComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          authorizationEngines: AuthorizationEnginesResolver,
                          authorizationEnginePlugins: AuthorizationEnginePluginsResolver,
                        },
                        data: {
                          perms: {
                            only: ['domain_authorization_engine_create'],
                          },
                        },
                      },
                      {
                        path: ':engineId/openfga',
                        component: OpenFGAComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          engine: AuthorizationEngineResolver,
                          authorizationEnginePlugins: AuthorizationEnginePluginsResolver,
                        },
                        data: {
                          breadcrumb: {
                            label: 'OpenFGA Management',
                          },
                          perms: {
                            only: ['domain_authorization_engine_read'],
                          },
                        },
                      },
                    ],
                  },
                  {
                    path: 'settings',
                    component: DomainSettingsComponent,
                    canActivate: [AuthGuard],
                    data: {
                      menu: {
                        label: 'Settings',
                        icon: 'gio:settings',
                        level: 'top',
                      },
                      perms: {
                        only: ['domain_settings_read'],
                      },
                    },
                    children: [
                      { path: '', redirectTo: 'general', pathMatch: 'full' },
                      {
                        path: 'general',
                        component: DomainSettingsGeneralComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          tags: TagsResolver,
                          dataPlanes: DataPlanesResolver,
                        },
                        data: {
                          menu: {
                            label: 'General',
                            section: 'Settings',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_settings_read'],
                          },
                        },
                      },
                      {
                        path: 'entrypoints',
                        component: DomainSettingsEntrypointsComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          entrypoint: DomainEntrypointResolver,
                          environment: EnvironmentResolver,
                        },
                        data: {
                          menu: {
                            label: 'Entrypoints',
                            section: 'Settings',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_settings_read'],
                          },
                        },
                      },
                      {
                        path: 'login',
                        component: DomainSettingsLoginComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Login',
                            section: 'Settings',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_settings_read'],
                          },
                        },
                      },
                      {
                        path: 'members',
                        component: DomainSettingsMembershipsComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          members: MembershipsResolver,
                        },
                        data: {
                          menu: {
                            label: 'Administrative roles',
                            section: 'Settings',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_member_list'],
                          },
                        },
                      },
                      {
                        path: 'theme',
                        component: DomainSettingsThemeComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          themes: ThemesResolver,
                        },
                        data: {
                          menu: {
                            label: 'Theme',
                            section: 'Design',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_theme_list', 'domain_theme_read', 'domain_form_list', 'domain_form_read'],
                          },
                        },
                      },
                      {
                        path: 'texts',
                        component: DomainSettingsDictionariesComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          dictionaries: DictionariesResolver,
                        },
                        data: {
                          menu: {
                            label: 'Texts',
                            section: 'Design',
                            level: 'level2',
                          },
                          perms: {
                            only: [
                              'domain_i18n_dictionary_list',
                              'domain_i18n_dictionary_create',
                              'domain_i18n_dictionary_update',
                              'domain_i18n_dictionary_delete',
                            ],
                          },
                        },
                      },
                      {
                        path: 'emails',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Emails',
                            section: 'Design',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_email_template_list', 'domain_email_template_read'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsEmailsComponent,
                          },
                          {
                            path: 'email',
                            component: DomainSettingsEmailComponent,
                            resolve: {
                              email: EmailResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'email.template',
                                applyOnLabel: applyOnLabel,
                              },
                            },
                          },
                        ],
                      },
                      {
                        path: 'flows',
                        component: DomainSettingsFlowsComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          flows: DomainFlowsResolver,
                          policies: PluginPoliciesResolver,
                          flowSettingsForm: PlatformFlowSchemaResolver,
                          factors: FactorsResolver,
                        },
                        data: {
                          menu: {
                            label: 'Flows',
                            section: 'Design',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_flow_list'],
                          },
                        },
                      },
                      {
                        path: 'providers',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Providers',
                            section: 'Identities',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_identity_provider_list'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsProvidersComponent,
                            resolve: {
                              providers: ProvidersResolver,
                              identities: IdentitiesResolver,
                            },
                          },
                          {
                            path: 'new',
                            component: ProviderCreationComponent,
                            resolve: {
                              certificates: CertificatesResolver,
                              identities: IdentitiesResolver,
                              datasources: DataSourcesResolver,
                            },
                          },
                          {
                            path: ':providerId',
                            component: ProviderComponent,
                            resolve: {
                              provider: ProviderResolver,
                              datasources: DataSourcesResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'provider.name',
                              },
                            },
                            children: [
                              {
                                path: '',
                                redirectTo: 'settings',
                                pathMatch: 'full',
                              },
                              {
                                path: 'settings',
                                component: ProviderSettingsComponent,
                                data: {
                                  breadcrumb: {
                                    disabled: true,
                                  },
                                },
                                resolve: {
                                  certificates: CertificatesResolver,
                                },
                              },
                              {
                                path: 'mappers',
                                component: ProviderMappersComponent,
                                data: {
                                  breadcrumb: {
                                    label: 'user mappers',
                                  },
                                },
                              },
                              {
                                path: 'roles',
                                component: ProviderRolesComponent,
                                resolve: {
                                  roles: RolesResolver,
                                },
                                data: {
                                  breadcrumb: {
                                    label: 'role mappers',
                                  },
                                },
                              },
                              {
                                path: 'groups',
                                component: ProviderGroupsComponent,
                                resolve: {
                                  groups: GroupsResolver,
                                },
                                data: {
                                  organizationContext: false,
                                  breadcrumb: {
                                    label: 'group mappers',
                                  },
                                },
                              },
                            ],
                          },
                        ],
                      },
                      {
                        path: 'webauthn',
                        component: DomainSettingsWebAuthnComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          entrypoint: DomainEntrypointResolver,
                        },
                        data: {
                          menu: {
                            label: 'WebAuthn',
                            section: 'Security',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_settings_read'],
                          },
                        },
                      },
                      {
                        path: 'secrets',
                        component: DomainSettingsSecretsComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Client Secrets',
                            section: 'Security',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_settings_read'],
                          },
                        },
                      },
                      {
                        path: 'factors',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Multifactor Auth',
                            section: 'Security',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_factor_list'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsFactorsComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              factors: FactorsResolver,
                            },
                            data: {
                              perms: {
                                only: ['domain_factor_list'],
                              },
                            },
                          },
                          {
                            path: 'new',
                            component: FactorCreationComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              factorPlugins: FactorPluginsResolver,
                              resources: ResourcesResolver,
                              resourcePlugins: ResourcePluginsResolver,
                            },
                            data: {
                              perms: {
                                only: ['domain_factor_create'],
                              },
                            },
                          },
                          {
                            path: ':factorId',
                            component: FactorComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              factor: FactorResolver,
                              factorPlugins: FactorPluginsResolver,
                              resources: ResourcesResolver,
                              resourcePlugins: ResourcePluginsResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'factor.name',
                              },
                              perms: {
                                only: ['domain_factor_read'],
                              },
                            },
                          },
                        ],
                      },
                      {
                        path: 'password-policies',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Password policy',
                            section: 'Security',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_settings_read'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: PasswordPoliciesComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              providers: ProvidersResolver,
                              identities: IdentitiesResolver,
                            },
                            data: {
                              perms: {
                                only: ['domain_settings_read'],
                              },
                            },
                          },
                          {
                            path: 'new',
                            component: DomainPasswordPolicyComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              identities: IdentitiesResolver,
                            },
                            data: {
                              perms: {
                                only: ['domain_settings_update'],
                              },
                            },
                          },
                          {
                            path: ':policyId',
                            component: DomainPasswordPolicyComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              policy: PasswordPolicyResolver,
                              policies: PasswordPoliciesResolver,
                              identities: IdentitiesResolver,
                            },
                            data: {
                              perms: {
                                only: ['domain_settings_read'],
                              },
                            },
                          },
                        ],
                      },
                      {
                        path: 'audits',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Audit Log',
                            section: 'Security',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_audit_list'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: AuditsComponent,
                            canActivate: [AuthGuard],
                            data: {
                              perms: {
                                only: ['domain_audit_list'],
                              },
                            },
                          },
                          {
                            path: 'settings',
                            children: [
                              {
                                path: '',
                                pathMatch: 'full',
                                component: AuditsSettingsComponent,
                                canActivate: [AuthGuard],
                                resolve: {
                                  reporters: ReportersResolver,
                                },
                                data: {
                                  perms: {
                                    only: ['domain_reporter_list'],
                                  },
                                },
                              },
                              {
                                path: 'new',
                                component: ReporterComponent,
                                canActivate: [AuthGuard],
                                resolve: {
                                  reporterPlugins: PluginReportersResolver,
                                },
                                data: {
                                  createMode: true,
                                  organizationContext: false,
                                  perms: {
                                    only: ['domain_reporter_create'],
                                  },
                                },
                              },
                              {
                                path: ':reporterId',
                                component: ReporterComponent,
                                resolve: {
                                  reporter: ReporterResolver,
                                  reporterPlugins: PluginReportersResolver,
                                },
                                data: {
                                  createMode: false,
                                  organizationContext: false,
                                  breadcrumb: {
                                    label: 'reporter.name',
                                  },
                                  perms: {
                                    only: ['domain_reporter_read'],
                                  },
                                },
                              },
                            ],
                          },
                          {
                            path: ':auditId',
                            component: AuditComponent,
                            resolve: {
                              audit: AuditResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'audit.id',
                              },
                              perms: {
                                only: ['domain_audit_read'],
                              },
                            },
                          },
                        ],
                      },
                      {
                        path: 'account',
                        component: DomainSettingsAccountComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'User Accounts',
                            section: 'Security',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_settings_read'],
                          },
                        },
                      },
                      {
                        path: 'certificates',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Certificates',
                            section: 'Security',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_certificate_list'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsCertificatesComponent,
                            resolve: {
                              certificates: CertificatesResolver,
                            },
                          },
                          {
                            path: 'new',
                            component: CertificateCreationComponent,
                          },
                          {
                            path: ':certificateId',
                            component: CertificateComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              certificate: CertificateResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'certificate.name',
                              },
                              perms: {
                                only: ['domain_certificate_read'],
                              },
                            },
                          },
                        ],
                      },
                      {
                        path: 'bot-detection',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Bot Detection',
                            section: 'Security',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_bot_detection_list'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsBotDetectionsComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              detections: BotDetectionsResolver,
                            },
                            data: {
                              perms: {
                                only: ['domain_bot_detection_list'],
                              },
                            },
                          },
                          {
                            path: 'new',
                            component: BotDetectionCreationComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              botDetectionPlugins: BotDetectionPluginsResolver,
                            },
                            data: {
                              perms: {
                                only: ['domain_bot_detection_create'],
                              },
                            },
                          },
                          {
                            path: ':botDetectionId',
                            component: BotDetectionComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              botDetection: BotDetectionResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'botDetection.name',
                              },
                              perms: {
                                only: ['domain_bot_detection_read'],
                              },
                            },
                          },
                        ],
                      },
                      {
                        path: 'device-identifier',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Device Identifier',
                            section: 'Security',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_device_identifier_list'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsDeviceIdentifiersComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              deviceIdentifiers: DeviceIdentifiersResolver,
                            },
                            data: {
                              perms: {
                                only: ['domain_device_identifier_list'],
                              },
                            },
                          },
                          {
                            path: 'new',
                            component: DeviceIdentifierCreationComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              deviceIdentifierPlugins: DeviceIdentifierPluginsResolver,
                            },
                            data: {
                              perms: {
                                only: ['domain_device_identifier_create'],
                              },
                            },
                          },
                          {
                            path: ':deviceIdentifierId',
                            component: DeviceIdentifierComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              deviceIdentifier: DeviceIdentifierResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'deviceIdentifier.name',
                              },
                              perms: {
                                only: ['domain_device_identifier_read'],
                              },
                            },
                          },
                        ],
                      },
                      {
                        path: 'resources',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Services',
                            section: 'Resources',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_resource_list'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsResourcesComponent,
                            canActivate: [AuthGuard],
                            data: {
                              perms: {
                                only: ['domain_resource_read'],
                              },
                            },
                            resolve: {
                              resources: ResourcesResolver,
                              resourcePlugins: ResourcePluginsResolver,
                            },
                          },
                          {
                            path: 'new',
                            component: ResourceCreationComponent,
                            canActivate: [AuthGuard],
                            data: {
                              perms: {
                                only: ['domain_resource_create'],
                              },
                            },
                          },
                          {
                            path: ':resourceId',
                            component: ResourceComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              resource: ResourceResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'resource.name',
                              },
                              perms: {
                                only: ['domain_resource_read'],
                              },
                            },
                          },
                        ],
                      },
                      {
                        path: 'users',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Users',
                            section: 'User Management',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_user_list'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: UsersComponent,
                          },
                          {
                            path: 'new',
                            component: UserCreationComponent,
                            canActivate: [AuthGuard],
                            data: {
                              perms: {
                                only: ['domain_user_create'],
                              },
                            },
                          },
                          {
                            path: ':userId',
                            component: UserComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              user: UserResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'user.username',
                              },
                              perms: {
                                only: ['domain_user_read'],
                              },
                            },
                            children: [
                              {
                                path: '',
                                redirectTo: 'profile',
                                pathMatch: 'full',
                              },
                              {
                                path: 'profile',
                                component: UserProfileComponent,
                              },
                              {
                                path: 'history',
                                component: UserHistoryComponent,
                              },
                              {
                                path: 'applications',
                                children: [
                                  {
                                    path: '',
                                    pathMatch: 'full',
                                    component: UserApplicationsComponent,
                                    resolve: {
                                      consents: ConsentsResolver,
                                    },
                                  },
                                  {
                                    path: ':appId',
                                    component: UserApplicationComponent,
                                    resolve: {
                                      application: ApplicationResolver,
                                      consents: ConsentsResolver,
                                    },
                                    data: {
                                      breadcrumb: {
                                        label: 'application.name',
                                      },
                                    },
                                  },
                                ],
                              },
                              {
                                path: 'factors',
                                component: UserFactorsComponent,
                                resolve: {
                                  factors: EnrolledFactorsResolver,
                                },
                              },
                              {
                                path: 'credentials',
                                children: [
                                  {
                                    path: '',
                                    pathMatch: 'full',
                                    component: UserCredentialsComponent,
                                    resolve: { credentials: UserCredentialsResolver },
                                  },
                                  {
                                    path: ':credentialId',
                                    component: UserCredentialComponent,
                                    resolve: { credential: UserCredentialResolver },
                                    data: {
                                      breadcrumb: {
                                        label: 'detail',
                                      },
                                    },
                                  },
                                ],
                              },
                              {
                                path: 'cert-credentials',
                                children: [
                                  {
                                    path: ':credentialId',
                                    component: UserCredentialComponent,
                                    resolve: { credential: UserCredentialResolver },
                                    data: {
                                      breadcrumb: {
                                        label: 'detail',
                                      },
                                    },
                                  },
                                ],
                              },
                              {
                                path: 'roles',
                                component: UserRolesComponent,
                                resolve: { roles: UserRolesResolver, dynamicRoles: DynamicUserRolesResolver },
                              },
                              {
                                path: 'devices',
                                component: UserDevicesComponent,
                                resolve: {
                                  devices: UserDevicesResolver,
                                  deviceIdentifiers: DeviceIdentifiersResolver,
                                  consents: ConsentsResolver,
                                },
                              },
                              {
                                path: 'identities',
                                component: UserIdentitiesComponent,
                                resolve: {
                                  identities: UserIdentitiesResolver,
                                },
                              },
                            ],
                          },
                        ],
                      },
                      {
                        path: 'groups',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Groups',
                            section: 'User Management',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_group_list'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: GroupsComponent,
                            resolve: {
                              groups: GroupsResolver,
                            },
                          },
                          {
                            path: 'new',
                            component: GroupCreationComponent,
                            canActivate: [AuthGuard],
                            data: {
                              perms: {
                                only: ['domain_group_create'],
                              },
                            },
                          },
                          {
                            path: ':groupId',
                            component: GroupComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              group: GroupResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'group.name',
                              },
                              perms: {
                                only: ['domain_group_read'],
                              },
                            },
                            children: [
                              { path: '', redirectTo: 'settings', pathMatch: 'full' },
                              { path: 'settings', component: GroupSettingsComponent },
                              {
                                path: 'members',
                                component: GroupMembersComponent,
                                resolve: { members: GroupMembersResolver },
                              },
                              { path: 'roles', component: GroupRolesComponent, resolve: { roles: GroupRolesResolver } },
                            ],
                          },
                        ],
                      },
                      {
                        path: 'roles',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Roles',
                            section: 'User Management',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_role_list'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsRolesComponent,
                            resolve: {
                              roles: PageRolesResolver,
                            },
                          },
                          {
                            path: 'new',
                            component: RoleCreationComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              scopes: ScopesAllResolver,
                            },
                            data: {
                              perms: {
                                only: ['domain_role_create'],
                              },
                            },
                          },
                          {
                            path: ':roleId',
                            component: RoleComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              role: RoleResolver,
                              scopes: ScopesAllResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'role.name',
                              },
                              perms: {
                                only: ['domain_role_read'],
                              },
                            },
                          },
                        ],
                      },
                      {
                        path: 'scim',
                        component: ScimComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'SCIM',
                            section: 'User Management',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_scim_read'],
                          },
                        },
                      },
                      {
                        path: 'self-service-account',
                        component: DomainSettingsSelfServiceAccountComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Self-service account',
                            section: 'User Management',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_settings_read'],
                          },
                        },
                      },
                      {
                        path: 'scopes',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Scopes',
                            section: 'OAuth 2.0',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_scope_list'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsScopesComponent,
                            resolve: {
                              scopes: ScopesResolver,
                            },
                          },
                          {
                            path: 'new',
                            component: ScopeCreationComponent,
                            canActivate: [AuthGuard],
                            data: {
                              perms: {
                                only: ['domain_scope_create'],
                              },
                            },
                          },
                          {
                            path: ':scopeId',
                            component: ScopeComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              scope: ScopeResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'scope.name',
                              },
                              perms: {
                                only: ['domain_scope_read'],
                              },
                            },
                          },
                        ],
                      },
                      {
                        path: 'extensionGrants',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Extension Grants',
                            section: 'OAuth 2.0',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_extension_grant_list'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsExtensionGrantsComponent,
                            resolve: {
                              extensionGrants: ExtensionGrantsResolver,
                            },
                          },
                          {
                            path: 'new',
                            component: ExtensionGrantCreationComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              identityProviders: ProvidersResolver,
                            },
                            data: {
                              perms: {
                                only: ['domain_extension_grant_create'],
                              },
                            },
                          },
                          {
                            path: ':extensionGrantId',
                            component: ExtensionGrantComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              extensionGrant: ExtensionGrantResolver,
                              identityProviders: ProvidersResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'extensionGrant.name',
                              },
                              perms: {
                                only: ['domain_extension_grant_read'],
                              },
                            },
                          },
                        ],
                      },
                      {
                        path: 'uma',
                        component: UmaComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'UMA',
                            section: 'OAuth 2.0',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_uma_read'],
                          },
                        },
                      },
                      {
                        path: 'token-exchange',
                        component: TokenExchangeComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Token Exchange',
                            section: 'OAuth 2.0',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_uma_read'],
                          },
                        },
                      },
                      {
                        path: 'dcr',
                        component: DomainSettingsOpenidClientRegistrationComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Client Registration',
                            section: 'Openid',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_openid_read'],
                          },
                        },
                        children: [
                          { path: '', redirectTo: 'settings', pathMatch: 'full' },
                          {
                            path: 'settings',
                            component: ClientRegistrationSettingsComponent,
                          },
                          {
                            path: 'default-scope',
                            component: ClientRegistrationDefaultScopeComponent,
                            resolve: { scopes: ScopesAllResolver },
                          },
                          {
                            path: 'allowed-scope',
                            component: ClientRegistrationAllowedScopeComponent,
                            resolve: { scopes: ScopesAllResolver },
                          },
                          {
                            path: 'templates',
                            component: ClientRegistrationTemplatesComponent,
                            resolve: { apps: ApplicationsResolver },
                          },
                        ],
                      },
                      {
                        path: 'oidc-profile',
                        component: OIDCProfileComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Security Profile',
                            section: 'Openid',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_openid_read'],
                          },
                        },
                      },
                      {
                        path: 'ciba',
                        component: CibaComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'CIBA',
                            section: 'Openid',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_openid_read'],
                          },
                        },
                        children: [
                          { path: '', redirectTo: 'settings', pathMatch: 'full' },
                          {
                            path: 'settings',
                            component: CibaSettingsComponent,
                          },
                          {
                            path: 'device-notifiers',
                            component: DeviceNotifiersComponent,
                            resolve: { notifiers: DeviceNotifiersResolver },
                          },
                          {
                            path: 'device-notifiers/new',
                            component: DeviceNotifiersCreationComponent,
                            resolve: { notifierPlugins: DeviceNotifierPluginsResolver },
                          },
                          {
                            path: 'device-notifiers/:notifierId',
                            component: DeviceNotifierComponent,
                            resolve: { deviceNotifier: DeviceNotifierResolver },
                            data: {
                              breadcrumb: {
                                label: 'detail',
                              },
                            },
                          },
                        ],
                      },
                      {
                        path: 'saml2',
                        component: Saml2Component,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'SAML 2.0',
                            section: 'SAML 2.0',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_saml_read'],
                          },
                        },
                        resolve: {
                          certificates: CertificatesResolver,
                        },
                      },
                    ],
                  },
                  {
                    path: 'alerts',
                    component: DomainAlertsComponent,
                    canActivate: [AuthGuard, LicenseGuard],
                    resolve: {
                      alertStatus: PlatformAlertStatusResolver,
                    },
                    data: {
                      licenseOptions: { feature: AmFeature.ALERT_ENGINE },
                      menu: {
                        label: 'Alerts',
                        icon: 'gio:alarm',
                        level: 'top',
                      },
                      perms: {
                        only: ['domain_alert_read'],
                      },
                    },
                    children: [
                      {
                        path: '',
                        redirectTo: 'general',
                        pathMatch: 'full',
                      },
                      {
                        path: 'general',
                        component: DomainAlertGeneralComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          alertNotifiers: AlertNotifiersResolver,
                        },
                        data: {
                          menu: {
                            label: 'General',
                            section: 'Alerts',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_alert_read'],
                          },
                        },
                      },
                      {
                        path: 'notifiers',
                        canActivate: [AuthGuard],
                        runGuardsAndResolvers: 'pathParamsOrQueryParamsChange',
                        resolve: {
                          notifiers: NotifiersResolver,
                          alertNotifiers: AlertNotifiersResolver,
                        },
                        data: {
                          menu: {
                            label: 'Notifiers',
                            section: 'Alerts',
                            level: 'level2',
                          },
                          perms: {
                            only: ['domain_alert_notifier_list'],
                          },
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainAlertNotifiersComponent,
                          },
                          {
                            path: 'new',
                            component: DomainAlertNotifierCreationComponent,
                            canActivate: [AuthGuard],
                            data: {
                              perms: {
                                only: ['domain_alert_notifier_create'],
                              },
                            },
                          },
                          {
                            path: ':alertNotifierId',
                            component: DomainAlertNotifierComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              alertNotifier: AlertNotifierResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: 'alertNotifier.name',
                              },
                              perms: {
                                only: ['domain_alert_notifier_read'],
                              },
                            },
                          },
                        ],
                      },
                    ],
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
  { path: 'login', component: LoginComponent },
  { path: 'login/callback', component: LoginCallbackComponent },
  { path: 'logout', component: LogoutComponent },
  { path: 'logout/callback', component: LogoutCallbackComponent },
  { path: 'newsletter', component: NewsletterComponent, resolve: { taglines: NewsletterResolver } },
  { path: 'dummy', component: DummyComponent },
  { path: '404', component: NotFoundComponent },
  { path: '', component: HomeComponent },
  { path: '**', redirectTo: '404', pathMatch: 'full' },
];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, {
      paramsInheritanceStrategy: 'always',
      scrollPositionRestoration: 'top',
      onSameUrlNavigation: 'reload',
    }),
  ],
  exports: [RouterModule],
})
export class AppRoutingModule {}

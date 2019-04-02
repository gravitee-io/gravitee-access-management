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
import {NgModule} from "@angular/core";
import {RouterModule, Routes} from "@angular/router";
import {LoginComponent} from "./login/login.component";
import {LoginCallbackComponent} from "./login/callback/callback.component";
import {DomainsComponent} from "./settings/domains/domains.component";
import {DomainComponent} from "./domain/domain.component";
import {DomainDashboardComponent} from "./domain/dashboard/dashboard.component";
import {DomainSettingsComponent} from "./domain/settings/settings.component";
import {DomainSettingsGeneralComponent} from "./domain/settings/general/general.component";
import {DomainSettingsOpenidClientRegistrationComponent} from "./domain/settings/openid/client-registration/client-registration.component";
import {DomainSettingsCertificatesComponent} from "./domain/settings/certificates/certificates.component";
import {DomainSettingsProvidersComponent} from "./domain/settings/providers/providers.component";
import {DomainSettingsRolesComponent} from "./domain/settings/roles/roles.component";
import {DomainSettingsScopesComponent} from "./domain/settings/scopes/scopes.component";
import {DomainSettingsFormsComponent} from "./domain/settings/forms/forms.component";
import {DomainSettingsFormComponent} from "./domain/settings/forms/form/form.component";
import {DomainSettingsLoginComponent} from "./domain/settings/login/login.component";
import {DomainSettingsEmailsComponent} from "./domain/settings/emails/emails.component";
import {DomainSettingsEmailComponent} from "./domain/settings/emails/email/email.component";
import {DomainSettingsExtensionGrantsComponent} from "./domain/settings/extension-grants/extension-grants.component";
import {ClientsComponent} from "./clients/clients.component";
import {ClientComponent} from "./domain/clients/client/client.component";
import {ClientCreationComponent} from "./clients/creation/client-creation.component";
import {DomainCreationComponent} from "./settings/domains/creation/domain-creation.component";
import {ProviderCreationComponent} from "./domain/settings/providers/creation/provider-creation.component";
import {ProviderComponent} from "./domain/settings/providers/provider/provider.component";
import {LogoutCallbackComponent} from "./logout/callback/callback.component";
import {LogoutComponent} from "./logout/logout.component";
import {DomainsResolver} from "./resolvers/domains.resolver";
import {DomainResolver} from "./resolvers/domain.resolver";
import {ClientsResolver} from "./resolvers/clients.resolver";
import {ClientResolver} from "./resolvers/client.resolver";
import {ProvidersResolver} from "./resolvers/providers.resolver";
import {ProviderResolver} from "./resolvers/provider.resolver";
import {ProviderRolesComponent} from "./domain/settings/providers/provider/roles/roles.component";
import {ProviderSettingsComponent} from "./domain/settings/providers/provider/settings/settings.component";
import {ProviderMappersComponent} from "./domain/settings/providers/provider/mappers/mappers.component";
import {ClientOIDCComponent} from "./domain/clients/client/oidc/oidc.component";
import {ClientSettingsComponent} from "./domain/clients/client/settings/settings.component";
import {ClientIdPComponent} from "./domain/clients/client/idp/idp.component";
import {ClientEmailsComponent} from "./domain/clients/client/emails/emails.component";
import {ClientEmailComponent} from "./domain/clients/client/emails/email/email.component";
import {ClientFormsComponent} from "./domain/clients/client/forms/forms.component";
import {ClientFormComponent} from "./domain/clients/client/forms/form/form.component";
import {CertificatesResolver} from "./resolvers/certificates.resolver";
import {CertificateCreationComponent} from "./domain/settings/certificates/creation/certificate-creation.component";
import {CertificateComponent} from "./domain/settings/certificates/certificate/certificate.component";
import {CertificateResolver} from "./resolvers/certificate.resolver";
import {RolesResolver} from "./resolvers/roles.resolver";
import {RoleCreationComponent} from "./domain/settings/roles/creation/role-creation.component";
import {RoleComponent} from "./domain/settings/roles/role/role.component";
import {RoleResolver} from "./resolvers/role.resolver";
import {ScopeResolver} from "./resolvers/scope.resolver";
import {ScopesResolver} from "./resolvers/scopes.resolver";
import {ScopeCreationComponent} from "./domain/settings/scopes/creation/scope-creation.component";
import {ScopeComponent} from './domain/settings/scopes/scope/scope.component';
import {DashboardComponent} from "./dashboard/dashboard.component";
import {SettingsComponent} from "./settings/settings.component";
import {DummyComponent} from "./components/dummy/dummy.component";
import {UsersComponent} from "./domain/settings/users/users.component";
import {UsersResolver} from "./resolvers/users.resolver";
import {UserComponent} from "./domain/settings/users/user/user.component";
import {UserResolver} from "./resolvers/user.resolver";
import {UserCreationComponent} from "./domain/settings/users/creation/user-creation.component";
import {UserProfileComponent} from "./domain/settings/users/user/profile/profile.component";
import {UserApplicationsComponent} from "./domain/settings/users/user/applications/applications.component";
import {ExtensionGrantCreationComponent} from "./domain/settings/extension-grants/creation/extension-grant-creation.component";
import {ExtensionGrantComponent} from "./domain/settings/extension-grants/extension-grant/extension-grant.component";
import {ExtensionGrantsResolver} from "./resolvers/extension-grants.resolver";
import {ExtensionGrantResolver} from "./resolvers/extension-grant.resolver";
import {ManagementComponent} from "./settings/management/management.component";
import {ManagementGeneralComponent} from "./settings/management/general/general.component";
import {FormResolver} from "./resolvers/form.resolver";
import {GroupsResolver} from "./resolvers/groups.resolver";
import {GroupsComponent} from "./domain/settings/groups/groups.component";
import {GroupCreationComponent} from "./domain/settings/groups/creation/group-creation.component";
import {GroupResolver} from "./resolvers/group.resolver";
import {GroupComponent} from "./domain/settings/groups/group/group.component";
import {GroupSettingsComponent} from "./domain/settings/groups/group/settings/settings.component";
import {GroupMembersComponent} from "./domain/settings/groups/group/members/members.component";
import {ScimComponent} from "./domain/settings/scim/scim.component";
import {EmailResolver} from "./resolvers/email.resolver";
import {ConsentsResolver} from "./resolvers/consents.resolver";
import {UserApplicationComponent} from "./domain/settings/users/user/applications/application/application.component";
import {AuditResolver} from "./resolvers/audit.resolver";
import {AuditsComponent} from "./domain/settings/audits/audits.component";
import {AuditsResolver} from "./resolvers/audits.resolver";
import {AuditComponent} from "./domain/settings/audits/audit/audit.component";
import {AuditsSettingsComponent} from "./domain/settings/audits/settings/settings.component";
import {ReportersResolver} from "./resolvers/reporters.resolver";
import {ReporterResolver} from "./resolvers/reporter.resolver";
import {ReporterComponent} from "./domain/settings/audits/settings/reporter/reporter.component";

const routes: Routes = [
  { path: 'dashboard',
    component: DashboardComponent,
    data: {
      menu: {
        label: 'Dashboard',
        icon: 'dashboard',
        firstLevel: true
      }
    }
  },
  { path: 'dashboard/clients',
    component: ClientsComponent,
    resolve: {
      clients: ClientsResolver
    },
    data: {
      menu: {
        label: 'Clients',
        icon: 'list',
        firstLevel: true
      }
    },
  },
  { path: 'dashboard/clients/new',
    component: ClientCreationComponent,
    resolve: {
      domains: DomainsResolver
    },
    data: {
      menu: {
        displayFirstLevel: true,
        activeParentPath: 'dashboard/clients'
      }
    }
  },
  {
    path: 'settings', component: SettingsComponent,
    data: {
      menu: {
        displayFirstLevel: false,
        displaySettingsLevel: true
      }
    },
    children: [
      { path: '', redirectTo: 'domains', pathMatch: 'full' },
      { path: 'domains',
        component: DomainsComponent,
        resolve: {
          domains: DomainsResolver
        },
        data: {
          menu: {
            label: 'Domains',
            icon: 'view_module'
          }
        }
      },
      { path: 'domains/new',
        component: DomainCreationComponent
      },
      {
        path: 'management',
        component: ManagementComponent,
        data: {
          menu: {
            label: 'Settings',
            icon: 'settings'
          }
        },
        children: [
          { path: '', redirectTo: 'general', pathMatch: 'full' },
          { path: 'general',
            component: ManagementGeneralComponent,
            resolve: {
              domain: DomainResolver,
            },
            data: {
              menu: {
                label: 'General',
                section: 'Settings'
              }
            }
          },
          {
            path: 'login',
            component: DomainSettingsFormComponent,
            resolve: {
              form: FormResolver
            },
            data: {
              menu: {
                label: 'Login Page',
                section: 'Settings'
              }
            }
          },
          { path: 'providers',
            component: DomainSettingsProvidersComponent,
            resolve: {
              providers: ProvidersResolver
            },
            data: {
              menu: {
                label: 'Providers',
                section: 'Identities'
              }
            }
          },
          { path: 'providers/new',
            component: ProviderCreationComponent
          },
          { path: 'providers/:providerId',
            component: ProviderComponent,
            resolve: {
              provider: ProviderResolver
            },
            children: [
              { path: '', redirectTo: 'settings', pathMatch: 'full' },
              { path: 'settings', component: ProviderSettingsComponent },
              { path: 'mappers', component: ProviderMappersComponent },
              { path: 'roles', component: ProviderRolesComponent, resolve: { roles: RolesResolver} }
            ]
          },
          { path: 'audits',
            component: AuditsComponent,
            resolve: {
              audits: AuditsResolver,
            },
            data: {
              menu: {
                label: 'Audit Log',
                section: 'Security'
              }
            }
          },
          { path: 'audits/settings',
            component: AuditsSettingsComponent,
            resolve: {
              reporters: ReportersResolver
            }
          },
          { path: 'audits/settings/:reporterId',
            component: ReporterComponent,
            resolve: {
              reporter: ReporterResolver
            }
          },
          { path: 'audits/:auditId',
            component: AuditComponent,
            resolve: {
              audit: AuditResolver
            }
          },
          { path: 'scopes',
            component: DomainSettingsScopesComponent,
            resolve: {
              scopes: ScopesResolver
            },
            data: {
              menu: {
                label: 'Scopes',
                section: 'Security'
              }
            }
          },
          { path: 'scopes/new',
            component: ScopeCreationComponent
          },
          { path: 'scopes/:scopeId',
            component: ScopeComponent,
            resolve: {
              scope: ScopeResolver
            }
          },
          { path: 'users', component: UsersComponent,
            resolve: {
              users: UsersResolver
            },
            data: {
              menu: {
                label: 'Users',
                section: 'User Management'
              }
            }
          },
          {
            path: 'users/:userId',
            component: UserComponent,
            resolve: {
              user: UserResolver
            },
            children: [
              { path: '', redirectTo: 'profile', pathMatch: 'full' },
              { path: 'profile', component: UserProfileComponent },
              { path: 'applications', component: UserApplicationsComponent, resolve: {consents: ConsentsResolver}},
              { path: 'applications/:clientId', component: UserApplicationComponent, resolve: {consents: ConsentsResolver}},
            ]
          },
          { path: 'roles', component: DomainSettingsRolesComponent,
            resolve: {
              roles: RolesResolver
            },
            data: {
              menu: {
                label: 'Roles',
                section: 'User Management',
              }
            }
          },
          { path: 'roles/new',
            component: RoleCreationComponent,
            resolve: {
              scopes: ScopesResolver
            }
          },
          {
            path: 'roles/:roleId',
            component: RoleComponent,
            resolve: {
              role: RoleResolver,
              scopes: ScopesResolver
            }
          }
        ]
      }
    ]
  },
  { path: 'domains/:domainId', component: DomainComponent,
    resolve: {
      domain: DomainResolver,
    },
    data: {
      menu: {
        displayFirstLevel: false
      }
    },
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard',
        component: DomainDashboardComponent,
        data: {
          menu: {
            label: 'Dashboard',
            icon: 'blur_on',
          }
        }
      },
      { path: 'clients',
        component: ClientsComponent,
        resolve: {
          clients: ClientsResolver
        },
        data: {
          menu: {
            label: 'Clients',
            icon: 'list',
          }
        }
      },
      { path: 'clients/new',
        component: ClientCreationComponent,
        resolve: {
          domains: DomainsResolver
        }
      },
      { path: 'clients/:clientId',
        component: ClientComponent,
        resolve: {
          client: ClientResolver
        },
        children: [
          { path: '', redirectTo: 'settings', pathMatch: 'full' },
          { path: 'settings',
            component: ClientSettingsComponent,
            resolve: {
              scopes: ScopesResolver,
              domainGrantTypes: ExtensionGrantsResolver
            }
          },
          { path: 'idp', component: ClientIdPComponent },
          { path: 'oidc', component: ClientOIDCComponent },
          { path: 'forms', component: ClientFormsComponent, resolve: { domain: DomainResolver } },
          { path: 'forms/form', component: ClientFormComponent, resolve: { form: FormResolver } },
          { path: 'emails', component: ClientEmailsComponent, resolve: { domain: DomainResolver } },
          { path: 'emails/email', component: ClientEmailComponent, resolve: { email: EmailResolver} }
        ]
      },
      { path: 'settings', component: DomainSettingsComponent,
        resolve: {
          domain: DomainResolver,
        },
        data: {
          menu: {
            label: 'Settings',
            icon: 'settings',
          }
        },
        children: [
          { path: '', redirectTo: 'general', pathMatch: 'full' },
          { path: 'general',
            component: DomainSettingsGeneralComponent,
            data: {
              menu: {
                label: 'General',
                section: 'Settings'
              }
            }
          },
          { path: 'login',
            component: DomainSettingsLoginComponent,
            resolve: {
              domain: DomainResolver
            },
            data: {
              menu: {
                label: 'Login',
                section: 'Settings'
              }
            }
          },
          { path: 'scopes',
            component: DomainSettingsScopesComponent,
            resolve: {
              scopes: ScopesResolver
            },
            data: {
              menu: {
                label: 'Scopes',
                section: 'OAuth 2.0'
              }
            }
          },
          { path: 'scopes/new',
            component: ScopeCreationComponent
          },
          { path: 'scopes/:scopeId',
            component: ScopeComponent,
            resolve: {
              scope: ScopeResolver
            }
          },
          { path: 'openid/clientRegistration',
            component: DomainSettingsOpenidClientRegistrationComponent,
            data: {
              menu: {
                label: 'Client Registration',
                section: 'Openid'
              }
            }
          },
          { path: 'providers',
            component: DomainSettingsProvidersComponent,
            resolve: {
              providers: ProvidersResolver
            },
            data: {
              menu: {
                label: 'Providers',
                section: 'Identities'
              }
            }
          },
          { path: 'providers/new',
            component: ProviderCreationComponent
          },
          { path: 'providers/:providerId',
            component: ProviderComponent,
            resolve: {
              provider: ProviderResolver
            },
            children: [
              { path: '', redirectTo: 'settings', pathMatch: 'full' },
              { path: 'settings', component: ProviderSettingsComponent },
              { path: 'mappers', component: ProviderMappersComponent },
              { path: 'roles', component: ProviderRolesComponent, resolve: { roles: RolesResolver, groups: GroupsResolver } }
            ]
          },
          { path: 'extensionGrants',
            component: DomainSettingsExtensionGrantsComponent,
            resolve: {
              extensionGrants: ExtensionGrantsResolver
            },
            data: {
              menu: {
                label: 'Extension Grants',
                section: 'OAuth 2.0'
              }
            }
          },
          { path: 'extensionGrants/new',
            component: ExtensionGrantCreationComponent,
            resolve: {
              identityProviders: ProvidersResolver
            }
          },
          {
            path: 'extensionGrants/:extensionGrantId',
            component: ExtensionGrantComponent,
            resolve: {
              extensionGrant: ExtensionGrantResolver,
              identityProviders: ProvidersResolver
            }
          },
          { path: 'audits',
            component: AuditsComponent,
            resolve: {
              audits: AuditsResolver,
            },
            data: {
              menu: {
                label: 'Audit Log',
                section: 'Security'
              }
            }
          },
          { path: 'audits/settings',
            component: AuditsSettingsComponent,
            resolve: {
              reporters: ReportersResolver
            }
          },
          { path: 'audits/settings/:reporterId',
            component: ReporterComponent,
            resolve: {
              reporter: ReporterResolver
            }
          },
          { path: 'audits/:auditId',
            component: AuditComponent,
            resolve: {
              audit: AuditResolver
            }
          },
          { path: 'certificates',
            component: DomainSettingsCertificatesComponent,
            resolve: {
              certificates: CertificatesResolver
            },
            data: {
              menu: {
                label: 'Certificates',
                section: 'Security'
              }
            }
          },
          { path: 'certificates/new',
            component: CertificateCreationComponent
          },
          {
            path: 'certificates/:certificateId',
            component: CertificateComponent,
            resolve: {
              certificate: CertificateResolver
            }
          },
          { path: 'scim', component: ScimComponent,
            resolve: {
              domain: DomainResolver
            },
            data: {
              menu: {
                label: 'SCIM',
                section: 'User Management'
              }
            }
          },
          { path: 'forms',
            component: DomainSettingsFormsComponent,
            resolve: { domain: DomainResolver },
            data: {
              menu: {
                label: 'Forms',
                section: 'User Management'
              }
            }
          },
          { path: 'forms/form',
            component: DomainSettingsFormComponent,
            resolve: {
              form: FormResolver
            },
          },
          { path: 'emails',
            component: DomainSettingsEmailsComponent,
            resolve: { domain: DomainResolver },
            data: {
              menu: {
                label: 'Emails',
                section: 'User Management'
              }
            }
          },
          { path: 'emails/email',
            component: DomainSettingsEmailComponent,
            resolve: {
              email: EmailResolver
            },
          },
          { path: 'users', component: UsersComponent,
            resolve: {
              users: UsersResolver
            },
            data: {
              menu: {
                label: 'Users',
                section: 'User Management'
              }
            }
          },
          { path: 'users/new',
            component: UserCreationComponent
          },
          {
            path: 'users/:userId',
            component: UserComponent,
            resolve: {
              user: UserResolver
            },
            children: [
              { path: '', redirectTo: 'profile', pathMatch: 'full' },
              { path: 'profile', component: UserProfileComponent },
              { path: 'applications', component: UserApplicationsComponent, resolve: {consents: ConsentsResolver}},
              { path: 'applications/:clientId', component: UserApplicationComponent, resolve: {consents: ConsentsResolver}},
            ]
          },
          { path: 'groups', component: GroupsComponent,
            resolve: {
              groups: GroupsResolver
            },
            data: {
              menu: {
                label: 'Groups',
                section: 'User Management'
              }
            }
          },
          { path: 'groups/new',
            component: GroupCreationComponent
          },
          {
            path: 'groups/:groupId',
            component: GroupComponent,
            resolve: {
              group: GroupResolver
            },
            children: [
              { path: '', redirectTo: 'settings', pathMatch: 'full' },
              { path: 'settings', component: GroupSettingsComponent },
              { path: 'members', component: GroupMembersComponent }
            ]
          },
          { path: 'roles', component: DomainSettingsRolesComponent,
            resolve: {
              roles: RolesResolver
            },
            data: {
              menu: {
                label: 'Roles',
                section: 'User Management',
              }
            }
          },
          { path: 'roles/new',
            component: RoleCreationComponent,
            resolve: {
              scopes: ScopesResolver
            }
          },
          {
            path: 'roles/:roleId',
            component: RoleComponent,
            resolve: {
              role: RoleResolver,
              scopes: ScopesResolver
            }
          }
        ]
      }
    ]
  },
  { path: 'login', component: LoginComponent },
  { path: 'login/callback', component: LoginCallbackComponent },
  { path: 'logout', component: LogoutComponent },
  { path: 'logout/callback', component: LogoutCallbackComponent },
  { path: 'dummy', component: DummyComponent },
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' }
];

@NgModule({
  imports: [ RouterModule.forRoot(routes) ],
  exports: [ RouterModule ]
})
export class AppRoutingModule {}

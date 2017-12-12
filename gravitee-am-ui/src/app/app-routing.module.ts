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
import { NgModule } from "@angular/core";
import { RouterModule, Routes } from "@angular/router";
import { LoginComponent } from "./login/login.component";
import { DomainsComponent } from "./settings/domains/domains.component";
import { DomainComponent } from "./domain/domain.component";
import { DomainDashboardComponent } from "./domain/dashboard/dashboard.component";
import { DomainSettingsComponent } from "./domain/settings/settings.component";
import { DomainSettingsGeneralComponent } from "./domain/settings/general/general.component";
import { DomainSettingsLoginComponent } from "./domain/settings/login/login.component";
import { DomainSettingsCertificatesComponent } from "./domain/settings/certificates/certificates.component";
import { DomainSettingsProvidersComponent } from "./domain/settings/providers/providers.component";
import { DomainSettingsRolesComponent } from "./domain/settings/roles/roles.component";
import { DomainSettingsScopesComponent } from "./domain/settings/scopes/scopes.component";
import { ClientsComponent } from "./clients/clients.component";
import { ClientComponent } from "./domain/clients/client/client.component";
import { ClientCreationComponent } from "./clients/creation/client-creation.component";
import { DomainCreationComponent } from "./settings/domains/creation/domain-creation.component";
import { ProviderCreationComponent } from "./domain/settings/providers/creation/provider-creation.component";
import { ProviderComponent } from "./domain/settings/providers/provider/provider.component";
import { OAuthCallbackComponent } from "./oauth/callback/callback.component";
import { LogoutCallbackComponent } from "./logout/callback/callback.component";
import { LogoutComponent } from "./logout/logout.component";
import { DomainsResolver } from "./resolvers/domains.resolver";
import { DomainResolver } from "./resolvers/domain.resolver";
import { ClientsResolver } from "./resolvers/clients.resolver";
import { ClientResolver } from "./resolvers/client.resolver";
import { ProvidersResolver } from "./resolvers/providers.resolver";
import { ProviderResolver } from "./resolvers/provider.resolver";
import { ProviderRolesComponent } from "./domain/settings/providers/provider/roles/roles.component";
import { DomainLoginFormResolver } from "./resolvers/domain-login-form.resolver";
import { ProviderSettingsComponent } from "./domain/settings/providers/provider/settings/settings.component";
import { ProviderMappersComponent } from "./domain/settings/providers/provider/mappers/mappers.component";
import { ClientOIDCComponent } from "./domain/clients/client/oidc/oidc.component";
import { ClientSettingsComponent } from "./domain/clients/client/settings/settings.component";
import { ClientIdPComponent } from "./domain/clients/client/idp/idp.component";
import { CertificatesResolver } from "./resolvers/certificates.resolver";
import { CertificateCreationComponent } from "./domain/settings/certificates/creation/certificate-creation.component";
import { CertificateComponent } from "./domain/settings/certificates/certificate/certificate.component";
import { CertificateResolver } from "./resolvers/certificate.resolver";
import { RolesResolver } from "./resolvers/roles.resolver";
import { RoleCreationComponent } from "./domain/settings/roles/creation/role-creation.component";
import { RoleComponent } from "./domain/settings/roles/role/role.component";
import { RoleResolver } from "./resolvers/role.resolver";
import { ScopeResolver } from "./resolvers/scope.resolver";
import { ScopesResolver } from "./resolvers/scopes.resolver";
import { ScopeCreationComponent } from "./domain/settings/scopes/creation/scope-creation.component";
import { ScopeComponent } from './domain/settings/scopes/scope/scope.component';
import { DashboardComponent } from "./dashboard/dashboard.component";
import { SettingsComponent } from "./settings/settings.component";
import { DummyComponent } from "./components/dummy/dummy.component";
import { UsersComponent } from "./domain/users/users.component";
import { UsersResolver } from "./resolvers/users.resolver";
import { UserComponent } from "./domain/users/user/user.component";
import { UserResolver } from "./resolvers/user.resolver";
import { DomainSettingsExtensionGrantsComponent } from "./domain/settings/extension-grants/extension-grants.component";
import { ExtensionGrantCreationComponent } from "./domain/settings/extension-grants/creation/extension-grant-creation.component";
import { ExtensionGrantComponent } from "./domain/settings/extension-grants/extension-grant/extension-grant.component";
import { ExtensionGrantsResolver } from "./resolvers/extension-grants.resolver";
import { ExtensionGrantResolver } from "./resolvers/extension-grant.resolver";

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
    ]
  },
  { path: 'clients/new',
    component: ClientCreationComponent,
    resolve: {
      domains: DomainsResolver
    }
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
          { path: 'oidc', component: ClientOIDCComponent }
        ]
      },
      { path: 'users', component: UsersComponent,
        resolve: {
          users: UsersResolver
        },
        data: {
          menu: {
            label: 'Users',
            icon: 'person',
          }
        }
      },
      {
        path: 'users/:userId',
        component: UserComponent,
        resolve: {
          user: UserResolver
        }
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
              domainLoginForm: DomainLoginFormResolver
            },
            data: {
              menu: {
                label: 'Login Page',
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
          { path: 'roles', component: DomainSettingsRolesComponent,
            resolve: {
              roles: RolesResolver
            },
            data: {
              menu: {
                label: 'Roles',
                section: 'Security',
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
          },
        ]
      }
    ]
  },
  { path: 'oauth/callback', component: OAuthCallbackComponent },
  { path: 'login', component: LoginComponent },
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

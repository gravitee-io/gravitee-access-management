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
import {DomainComponent} from "./domain.component";
import {DomainDashboardComponent} from "./dashboard/dashboard.component";
import {ClientsComponent} from "../clients/clients.component";
import {ClientComponent} from "./clients/client/client.component";
import {ClientSettingsComponent} from "./clients/client/settings/settings.component";
import {ClientIdPComponent} from "./clients/client/idp/idp.component";
import {ClientOIDCComponent} from "./clients/client/oidc/oidc.component";
import {UsersComponent} from "./users/users.component";
import {UserComponent} from "./users/user/user.component";
import {DomainSettingsComponent} from "./settings/settings.component";
import {DomainSettingsGeneralComponent} from "./settings/general/general.component";
import {DomainSettingsLoginComponent} from "./settings/login/login.component";
import {DomainSettingsProvidersComponent} from "./settings/providers/providers.component";
import {ProviderCreationComponent} from "./settings/providers/creation/provider-creation.component";
import {ProviderComponent} from "./settings/providers/provider/provider.component";
import {ProviderSettingsComponent} from "./settings/providers/provider/settings/settings.component";
import {ProviderMappersComponent} from "./settings/providers/provider/mappers/mappers.component";
import {ProviderRolesComponent} from "./settings/providers/provider/roles/roles.component";
import {DomainSettingsExtensionGrantsComponent} from "./settings/extension-grants/extension-grants.component";
import {ExtensionGrantCreationComponent} from "./settings/extension-grants/creation/extension-grant-creation.component";
import {ExtensionGrantComponent} from "./settings/extension-grants/extension-grant/extension-grant.component";
import {DomainSettingsCertificatesComponent} from "./settings/certificates/certificates.component";
import {CertificateCreationComponent} from "./settings/certificates/creation/certificate-creation.component";
import {CertificateComponent} from "./settings/certificates/certificate/certificate.component";
import {DomainSettingsRolesComponent} from "./settings/roles/roles.component";
import {RoleCreationComponent} from "./settings/roles/creation/role-creation.component";
import {RoleComponent} from "./settings/roles/role/role.component";
import {DomainResolver} from "./shared/resolvers/domain.resolver";
import {ClientsResolver} from "../clients/shared/resolvers/clients.resolver";
import {ClientResolver} from "../clients/shared/resolvers/client.resolver";
import {ExtensionGrantsResolver} from "./shared/resolvers/extension-grants.resolver";
import {UsersResolver} from "./shared/resolvers/users.resolver";
import {UserResolver} from "./shared/resolvers/user.resolver";
import {DomainLoginFormResolver} from "./shared/resolvers/domain-login-form.resolver";
import {ProvidersResolver} from "./shared/resolvers/providers.resolver";
import {ProviderResolver} from "./shared/resolvers/provider.resolver";
import {RolesResolver} from "./shared/resolvers/roles.resolver";
import {RoleResolver} from "./shared/resolvers/role.resolver";
import {CertificateResolver} from "./shared/resolvers/certificate.resolver";
import {ExtensionGrantResolver} from "./shared/resolvers/extension-grant.resolver";
import {CertificatesResolver} from "./shared/resolvers/certificates.resolver";

const routes: Routes = [
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
            component: RoleCreationComponent
          },
          {
            path: 'roles/:roleId',
            component: RoleComponent,
            resolve: {
              role: RoleResolver
            }
          },
        ]
      }
    ]
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ]
})
export class DomainRoutingModule {}

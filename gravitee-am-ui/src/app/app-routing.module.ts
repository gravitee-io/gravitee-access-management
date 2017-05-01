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
import { Routes, RouterModule } from '@angular/router';
import { LoginComponent } from "./login/login.component";
import { DomainsComponent } from "./domains/domains.component";
import { DomainComponent } from "./domains/domain/domain.component";
import { GeneralComponent } from "./domains/domain/general/general.component";
import { ClientsComponent } from "./domains/domain/clients/clients.component";
import { ClientComponent } from "./domains/domain/clients/client/client.component";
import { ClientCreationComponent } from "./domains/domain/clients/creation/client-creation.component";
import { ProvidersComponent } from "./domains/domain/providers/providers.component";
import { DomainCreationComponent } from "./domains/creation/domain-creation.component";
import { ProviderCreationComponent } from "./domains/domain/providers/creation/provider-creation.component";
import { ProviderComponent } from "./domains/domain/providers/provider/provider.component";
import { OAuthCallbackComponent } from "./oauth/callback/callback.component";
import { LogoutCallbackComponent}  from "./logout/callback/callback.component";
import { LogoutComponent } from "./logout/logout.component";
import { DomainsResolver } from "./resolvers/domains.resolver";
import { DomainResolver } from "./resolvers/domain.resolver";
import { ClientsResolver } from "./resolvers/clients.resolver";
import { ClientResolver } from "./resolvers/client.resolver";
import { ProvidersResolver } from "./resolvers/providers.resolver";
import { ProviderResolver } from "./resolvers/provider.resolver";
import { DomainLoginComponent } from "./domains/domain/login/login.component";
import { DomainLoginFormResolver } from "./resolvers/domain-login-form.resolver";

const routes: Routes = [
  { path: 'domains',
    component: DomainsComponent,
    resolve: {
      domains: DomainsResolver
    },
    data: {
        menu: {
          label: 'Domains',
          icon: 'dashboard',
          firstLevel: true
        }
      }
  },
  { path: 'domains/new',
    component: DomainCreationComponent
  },
  { path: 'domains/:domainId', component: DomainComponent,
    resolve: {
      domain: DomainResolver
    },
    children: [
      { path: '', redirectTo: 'general', pathMatch: 'full' },
      { path: 'general',
        component: GeneralComponent,
        data: {
          menu: {
            label: 'General',
            icon: 'blur_on',
          }
        }
      },
      { path: 'login',
        component: DomainLoginComponent,
        resolve: {
          domainLoginForm: DomainLoginFormResolver
        },
        data: {
          menu: {
            label: 'Login page',
            icon: 'web',
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
      },
      { path: 'clients/:clientId/edit',
        component: ClientComponent,
        resolve: {
          client: ClientResolver
        },
      },
      { path: 'providers', component: ProvidersComponent,
        resolve: {
          providers: ProvidersResolver
        },
        data: {
          menu: {
            label: 'Providers',
            icon: 'device_hub',
          }
        }
      },
      { path: 'providers/new',
        component: ProviderCreationComponent,
      },
      { path: 'providers/:providerId/edit',
        component: ProviderComponent,
        resolve: {
          provider: ProviderResolver
        }
      }
    ]
  },
  { path: 'oauth/callback', component: OAuthCallbackComponent },
  { path: 'login', component: LoginComponent },
  { path: 'logout', component: LogoutComponent },
  { path: 'logout/callback', component: LogoutCallbackComponent },
  { path: '', redirectTo: '/domains', pathMatch: 'full' }
];

@NgModule({
  imports: [ RouterModule.forRoot(routes) ],
  exports: [ RouterModule ]
})
export class AppRoutingModule {}

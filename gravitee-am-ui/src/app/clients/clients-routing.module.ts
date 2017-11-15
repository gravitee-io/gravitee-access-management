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
import {RouterModule, Routes} from "@angular/router";
import {NgModule} from "@angular/core";
import {ClientsComponent} from "./clients.component";
import {ClientCreationComponent} from "./creation/client-creation.component";
import {DomainsResolver} from "../domain/shared/resolvers/domains.resolver";
import {ClientsResolver} from "./shared/resolvers/clients.resolver";

const routes: Routes = [
  { path: 'clients',
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
  { path: 'clients/new',
    component: ClientCreationComponent,
    resolve: {
      domains: DomainsResolver
    }
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ]
})
export class ClientsRoutingModule {}

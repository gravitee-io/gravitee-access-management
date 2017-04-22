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
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpModule } from '@angular/http';
import { Http } from '@angular/http';
import { MaterialModule } from '@angular/material';
import { FlexLayoutModule } from '@angular/flex-layout';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { JsonSchemaFormModule } from '../libraries/angular2-json-schema-form';
import 'hammerjs';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { SidenavComponent } from './components/sidenav/sidenav.component';
import { LoginComponent } from './login/login.component';
import { DomainsComponent } from './domains/domains.component';
import { DomainService } from './services/domain.service';
import { DomainComponent } from './domains/domain/domain.component';
import { GeneralComponent } from './domains/domain/general/general.component';
import { ClientsComponent } from './domains/domain/clients/clients.component';
import { ProvidersComponent } from './domains/domain/providers/providers.component';
import { SidenavService } from "./components/sidenav/sidenav.service";
import { HeaderComponent } from './domains/domain/header/header.component';
import { ConfirmComponent } from './components/dialog/confirm/confirm.component';
import { DialogService } from "./services/dialog.service";
import { SnackbarService } from "./services/snackbar.service";
import { EmptystateComponent } from './components/emptystate/emptystate.component';
import { DomainCreationComponent } from './domains/creation/domain-creation.component';
import { ProviderCreationComponent } from './domains/domain/providers/creation/provider-creation.component';
import { ClientComponent } from './domains/domain/clients/client/client.component';
import { ClientCreationComponent } from './domains/domain/clients/creation/client-creation.component';
import { ClientFormComponent } from './domains/domain/clients/client/form/client-form.component';
import { ProviderCreationStep1Component } from './domains/domain/providers/creation/steps/step1/step1.component';
import { ProviderCreationStep2Component } from './domains/domain/providers/creation/steps/step2/step2.component';
import { ProviderComponent } from './domains/domain/providers/provider/provider.component';
import { ProviderFormComponent } from './domains/domain/providers/provider/form/form.component';
import { ClientService } from "./services/client.service";
import { ProviderService } from "./services/provider.service";
import { PlatformService } from "./services/platform.service";
import { HttpService } from "./services/http.service";

@NgModule({
  declarations: [
    AppComponent,
    SidenavComponent,
    LoginComponent,
    DomainsComponent,
    DomainComponent,
    GeneralComponent,
    ClientsComponent,
    ProvidersComponent,
    HeaderComponent,
    ConfirmComponent,
    EmptystateComponent,
    DomainCreationComponent,
    ProviderCreationComponent,
    ClientComponent,
    ClientCreationComponent,
    ClientFormComponent,
    ProviderCreationStep1Component,
    ProviderCreationStep2Component,
    ProviderComponent,
    ProviderFormComponent
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    FormsModule,
    ReactiveFormsModule,
    HttpModule,
    AppRoutingModule,
    MaterialModule,
    FlexLayoutModule,
    NgxDatatableModule,
    JsonSchemaFormModule.forRoot()
  ],
  providers: [
    DomainService,
    ClientService,
    ProviderService,
    SidenavService,
    DialogService,
    SnackbarService,
    PlatformService,
    { provide: Http, useClass: HttpService }
  ],
  entryComponents: [ConfirmComponent],
  bootstrap: [AppComponent]
})
export class AppModule { }

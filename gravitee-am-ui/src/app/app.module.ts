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
import { CodemirrorModule } from 'ng2-codemirror';
import 'hammerjs';
import 'codemirror';
import "codemirror/mode/htmlmixed/htmlmixed";
import "codemirror/addon/selection/mark-selection";

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { SidenavComponent } from './components/sidenav/sidenav.component';
import { LoginComponent } from './login/login.component';
import { DomainsComponent } from './domains/domains.component';
import { DomainService } from './services/domain.service';
import { DomainComponent } from './domains/domain/domain.component';
import { DomainLoginComponent, LoginInfoDialog } from './domains/domain/login/login.component';
import { GeneralComponent } from './domains/domain/general/general.component';
import { ClientsComponent } from './domains/domain/clients/clients.component';
import { ProvidersComponent } from './domains/domain/providers/providers.component';
import { SidenavService } from "./components/sidenav/sidenav.service";
import { ConfirmComponent } from './components/dialog/confirm/confirm.component';
import { DialogService } from "./services/dialog.service";
import { SnackbarService } from "./services/snackbar.service";
import { EmptystateComponent } from './components/emptystate/emptystate.component';
import { DomainCreationComponent } from './domains/creation/domain-creation.component';
import { ProviderCreationComponent } from './domains/domain/providers/creation/provider-creation.component';
import { ClientComponent } from './domains/domain/clients/client/client.component';
import { ClientCreationComponent } from './domains/domain/clients/creation/client-creation.component';
import { ClientSettingsComponent } from './domains/domain/clients/client/settings/settings.component';
import { ClientOIDCComponent, CreateClaimComponent } from './domains/domain/clients/client/oidc/oidc.component';
import { ProviderCreationStep1Component } from './domains/domain/providers/creation/steps/step1/step1.component';
import { ProviderCreationStep2Component } from './domains/domain/providers/creation/steps/step2/step2.component';
import { ProviderComponent } from './domains/domain/providers/provider/provider.component';
import { ProviderFormComponent } from './domains/domain/providers/provider/form/form.component';
import { CreateRoleMapperComponent, ProviderRolesComponent } from "app/domains/domain/providers/provider/roles/roles.component";
import { ClientService } from "./services/client.service";
import { ProviderService } from "./services/provider.service";
import { PlatformService } from "./services/platform.service";
import { HttpService } from "./services/http.service";
import { OAuthCallbackComponent } from './oauth/callback/callback.component';
import { AuthService } from "./services/auth.service";
import { AppConfig } from "../config/app.config";
import { LogoutComponent } from './logout/logout.component';
import { LogoutCallbackComponent } from './logout/callback/callback.component';
import { DomainsResolver } from "./resolvers/domains.resolver";
import { DomainResolver } from "./resolvers/domain.resolver";
import { ClientsResolver } from "./resolvers/clients.resolver";
import { ClientResolver } from "./resolvers/client.resolver";
import { ProvidersResolver } from "./resolvers/providers.resolver";
import { ProviderResolver } from "./resolvers/provider.resolver";
import { DomainLoginFormResolver } from "./resolvers/domain-login-form.resolver";
import { ProviderSettingsComponent } from './domains/domain/providers/provider/settings/settings.component';
import { CreateMapperComponent, ProviderMappersComponent } from './domains/domain/providers/provider/mappers/mappers.component';
import { Ng2BreadcrumbModule } from "libraries/ng2-breadcrumb/app.module";
import { BreadcrumbComponent } from './components/breadcrumb/breadcrumb.component';
import { CertificatesComponent, CertitificatePublicKeyDialog } from './domains/domain/certificates/certificates.component';
import { CertificateCreationComponent } from './domains/domain/certificates/creation/certificate-creation.component';
import { CertificateComponent } from './domains/domain/certificates/certificate/certificate.component';
import { CertificatesResolver } from "./resolvers/certificates.resolver";
import { CertificateService } from "./services/certificate.service";
import { CertificateCreationStep1Component } from "./domains/domain/certificates/creation/steps/step1/step1.component";
import { CertificateCreationStep2Component } from "app/domains/domain/certificates/creation/steps/step2/step2.component";
import { CertificateFormComponent } from "./domains/domain/certificates/certificate/form/form.component";
import { CertificateResolver } from "./resolvers/certificate.resolver";
import { ClipboardModule } from "ngx-clipboard/dist";
import { RoleService } from "./services/role.service";
import { RolesResolver } from "./resolvers/roles.resolver";
import { RoleResolver } from "./resolvers/role.resolver";
import { RolesComponent } from './domains/domain/roles/roles.component';
import { RoleCreationComponent } from './domains/domain/roles/creation/role-creation.component';
import { RoleComponent } from './domains/domain/roles/role/role.component';
import { SnackbarComponent } from "./components/snackbar/snackbar.component";
import { ClientIdPComponent } from './domains/domain/clients/client/idp/idp.component';

@NgModule({
  declarations: [
    AppComponent,
    SidenavComponent,
    LoginComponent,
    DomainsComponent,
    DomainComponent,
    DomainLoginComponent,
    LoginInfoDialog,
    GeneralComponent,
    ClientsComponent,
    ProvidersComponent,
    ConfirmComponent,
    EmptystateComponent,
    DomainCreationComponent,
    ProviderCreationComponent,
    ClientComponent,
    ClientCreationComponent,
    ClientSettingsComponent,
    ClientOIDCComponent,
    ClientIdPComponent,
    ProviderCreationStep1Component,
    ProviderCreationStep2Component,
    ProviderComponent,
    ProviderFormComponent,
    ProviderSettingsComponent,
    ProviderMappersComponent,
    ProviderRolesComponent,
    CreateMapperComponent,
    OAuthCallbackComponent,
    LogoutComponent,
    LogoutCallbackComponent,
    BreadcrumbComponent,
    CreateClaimComponent,
    CertificatesComponent,
    CertificateCreationComponent,
    CertificateComponent,
    CertificateCreationStep1Component,
    CertificateCreationStep2Component,
    CertificateFormComponent,
    CertitificatePublicKeyDialog,
    RolesComponent,
    RoleCreationComponent,
    RoleComponent,
    CreateRoleMapperComponent,
    SnackbarComponent,
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
    JsonSchemaFormModule.forRoot(),
    CodemirrorModule,
    Ng2BreadcrumbModule.forRoot(),
    ClipboardModule
  ],
  providers: [
    DomainService,
    ClientService,
    ProviderService,
    SidenavService,
    DialogService,
    SnackbarService,
    PlatformService,
    AuthService,
    CertificateService,
    RoleService,
    AppConfig,
    DomainsResolver,
    DomainResolver,
    ClientsResolver,
    ClientResolver,
    ProvidersResolver,
    ProviderResolver,
    DomainLoginFormResolver,
    CertificatesResolver,
    CertificateResolver,
    RolesResolver,
    RoleResolver,
    { provide: Http, useClass: HttpService }
  ],
  entryComponents: [
    ConfirmComponent,
    LoginInfoDialog,
    CreateMapperComponent,
    CreateClaimComponent,
    CertitificatePublicKeyDialog,
    CreateRoleMapperComponent,
    SnackbarComponent
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }

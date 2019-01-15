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
import 'polyfills';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpModule } from '@angular/http';
import { Http } from '@angular/http';
import {
  MatButtonModule, MatButtonToggleModule, MatCardModule, MatCheckboxModule, MatDialogModule, MatExpansionModule,
  MatGridListModule, MatIconModule, MatListModule, MatNativeDateModule, MatProgressBarModule, MatProgressSpinnerModule,
  MatRadioModule, MatRippleModule, MatSelectModule, MatSliderModule, MatSnackBarModule, MatSortModule, MatTableModule,
  MatTabsModule, MatToolbarModule, MatTooltipModule, MatStepperModule } from '@angular/material';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';

import { FlexLayoutModule } from '@angular/flex-layout';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { CodemirrorModule } from 'ng2-codemirror';
import { JsonSchemaFormModule } from "./components/json-schema-form/json-schema-form.module";
import 'hammerjs';
import 'codemirror';
import "codemirror/mode/htmlmixed/htmlmixed";
import "codemirror/addon/selection/mark-selection";

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { SidenavComponent } from './components/sidenav/sidenav.component';
import { SidenavSettingsComponent } from "./components/sidenav-settings/sidenav-settings.component";
import { LoginComponent } from './login/login.component';
import { LoginCallbackComponent } from './login/callback/callback.component';
import { DomainsComponent } from './settings/domains/domains.component';
import { DomainService } from './services/domain.service';
import { DomainComponent } from './domain/domain.component';
import { ClientsComponent } from './clients/clients.component';
import { SidenavService } from "./components/sidenav/sidenav.service";
import { ConfirmComponent } from './components/dialog/confirm/confirm.component';
import { DialogService } from "./services/dialog.service";
import { SnackbarService } from "./services/snackbar.service";
import { EmptystateComponent } from './components/emptystate/emptystate.component';
import { DomainCreationComponent } from './settings/domains/creation/domain-creation.component';
import { ProviderCreationComponent } from './domain/settings/providers/creation/provider-creation.component';
import { ClientComponent } from './domain/clients/client/client.component';
import { ClientCreationComponent } from './clients/creation/client-creation.component';
import { ClientSettingsComponent } from './domain/clients/client/settings/settings.component';
import { ClientOIDCComponent, CreateClaimComponent } from './domain/clients/client/oidc/oidc.component';
import { ProviderCreationStep1Component } from './domain/settings/providers/creation/steps/step1/step1.component';
import { ProviderCreationStep2Component } from './domain/settings/providers/creation/steps/step2/step2.component';
import { ProviderComponent } from './domain/settings/providers/provider/provider.component';
import { ProviderFormComponent } from './domain/settings/providers/provider/form/form.component';
import { CreateRoleMapperComponent, ProviderRolesComponent } from "app/domain/settings/providers/provider/roles/roles.component";
import { ClientService } from "./services/client.service";
import { ProviderService } from "./services/provider.service";
import { PlatformService } from "./services/platform.service";
import { HttpService } from "./services/http.service";
import { AuthService } from "./services/auth.service";
import { AppConfig } from "../config/app.config";
import { LogoutComponent } from './logout/logout.component';
import { LogoutCallbackComponent } from './logout/callback/callback.component';
import { DomainsResolver } from "./resolvers/domains.resolver";
import { DomainResolver } from "./resolvers/domain.resolver";
import { DomainDashboardComponent } from "./domain/dashboard/dashboard.component";
import { DomainSettingsComponent } from './domain/settings/settings.component';
import { DomainSettingsGeneralComponent } from "./domain/settings/general/general.component";
import { DomainSettingsOpenidClientRegistrationComponent } from "./domain/settings/openid/client-registration/client-registration.component";
import { DomainSettingsLoginComponent, DomainSettingsLoginInfoDialog } from "./domain/settings/login/login.component";
import { DomainSettingsRolesComponent } from "./domain/settings/roles/roles.component";
import { DomainSettingsScopesComponent } from "./domain/settings/scopes/scopes.component";
import { DomainSettingsCertificatesComponent, CertitificatePublicKeyDialog } from './domain/settings/certificates/certificates.component';
import { DomainSettingsProvidersComponent } from "./domain/settings/providers/providers.component";
import { DomainSettingsExtensionGrantsComponent } from "./domain/settings/extension-grants/extension-grants.component";
import { ClientsResolver } from "./resolvers/clients.resolver";
import { ClientResolver } from "./resolvers/client.resolver";
import { ProvidersResolver } from "./resolvers/providers.resolver";
import { ProviderResolver } from "./resolvers/provider.resolver";
import { DomainLoginFormResolver } from "./resolvers/domain-login-form.resolver";
import { ProviderSettingsComponent } from './domain/settings/providers/provider/settings/settings.component';
import { CreateMapperComponent, ProviderMappersComponent } from './domain/settings/providers/provider/mappers/mappers.component';
import { Ng2BreadcrumbModule } from "libraries/ng2-breadcrumb/app.module";
import { BreadcrumbComponent } from './components/breadcrumb/breadcrumb.component';
import { CertificateCreationComponent } from './domain/settings/certificates/creation/certificate-creation.component';
import { CertificateComponent } from './domain/settings/certificates/certificate/certificate.component';
import { CertificatesResolver } from "./resolvers/certificates.resolver";
import { CertificateService } from "./services/certificate.service";
import { CertificateCreationStep1Component } from "./domain/settings/certificates/creation/steps/step1/step1.component";
import { CertificateCreationStep2Component } from "app/domain/settings/certificates/creation/steps/step2/step2.component";
import { CertificateFormComponent } from "./domain/settings/certificates/certificate/form/form.component";
import { CertificateResolver } from "./resolvers/certificate.resolver";
import { ClipboardModule } from "ngx-clipboard/dist";
import { RoleService } from "./services/role.service";
import { RolesResolver } from "./resolvers/roles.resolver";
import { RoleResolver } from "./resolvers/role.resolver";
import { RoleCreationComponent } from './domain/settings/roles/creation/role-creation.component';
import { RoleComponent } from './domain/settings/roles/role/role.component';
import { ScopeService } from "./services/scope.service";
import { ScopesResolver } from "./resolvers/scopes.resolver";
import { ScopeResolver } from "./resolvers/scope.resolver";
import { ScopeCreationComponent } from './domain/settings/scopes/creation/scope-creation.component';
import { ScopeComponent } from './domain/settings/scopes/scope/scope.component';
import { SnackbarComponent } from "./components/snackbar/snackbar.component";
import { ClientIdPComponent } from './domain/clients/client/idp/idp.component';
import { NavbarComponent } from './components/navbar/navbar.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { DashboardService} from "./services/dashboard.service";
import { WidgetClientsComponent } from './components/widget/clients/clients.component';
import { WidgetTopClientsComponent } from './components/widget/top-clients/top-clients.component';
import { WidgetTotalClientsComponent } from './components/widget/total-clients/total-clients.component';
import { WidgetTotalTokensComponent } from './components/widget/total-tokens/total-tokens.component';
import { SettingsComponent } from './settings/settings.component';
import { HumanDatePipe } from './pipes/human-date.pipe';
import { MapToIterablePipe } from './pipes/map-to-iterable.pipe';
import { DummyComponent } from "./components/dummy/dummy.component";
import { UsersComponent } from './domain/users/users.component';
import { UserComponent } from './domain/users/user/user.component';
import { UserService} from "./services/user.service";
import { UsersResolver } from "./resolvers/users.resolver";
import { UserResolver } from "./resolvers/user.resolver";
import { ExtensionGrantService } from "./services/extension-grant.service";
import { ExtensionGrantsResolver } from "./resolvers/extension-grants.resolver";
import { ExtensionGrantResolver } from 'app/resolvers/extension-grant.resolver';
import { ExtensionGrantCreationComponent } from "./domain/settings/extension-grants/creation/extension-grant-creation.component";
import { ExtensionGrantComponent } from 'app/domain/settings/extension-grants/extension-grant/extension-grant.component';
import { ExtensionGrantFormComponent } from "./domain/settings/extension-grants/extension-grant/form/form.component";
import { ExtensionGrantCreationStep1Component } from "./domain/settings/extension-grants/creation/steps/step1/step1.component";
import { ExtensionGrantCreationStep2Component } from "./domain/settings/extension-grants/creation/steps/step2/step2.component";
import { MaterialFileComponent } from "./components/json-schema-form/material-file.component";
import { ManagementComponent } from "./settings/management/management.component";
import { ManagementGeneralComponent } from "./settings/management/general/general.component";

@NgModule({
  declarations: [
    AppComponent,
    SidenavComponent,
    SidenavSettingsComponent,
    LoginComponent,
    LoginCallbackComponent,
    DomainsComponent,
    DomainComponent,
    DomainDashboardComponent,
    DomainSettingsComponent,
    DomainSettingsLoginComponent,
    DomainSettingsGeneralComponent,
    DomainSettingsOpenidClientRegistrationComponent,
    DomainSettingsProvidersComponent,
    DomainSettingsScopesComponent,
    DomainSettingsRolesComponent,
    DomainSettingsCertificatesComponent,
    DomainSettingsLoginInfoDialog,
    DomainSettingsExtensionGrantsComponent,
    ClientsComponent,
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
    LogoutComponent,
    LogoutCallbackComponent,
    BreadcrumbComponent,
    CreateClaimComponent,
    CertificateCreationComponent,
    CertificateComponent,
    CertificateCreationStep1Component,
    CertificateCreationStep2Component,
    CertificateFormComponent,
    CertitificatePublicKeyDialog,
    RoleCreationComponent,
    RoleComponent,
    CreateRoleMapperComponent,
    ExtensionGrantCreationComponent,
    ExtensionGrantComponent,
    ExtensionGrantCreationStep1Component,
    ExtensionGrantCreationStep2Component,
    ExtensionGrantFormComponent,
    SnackbarComponent,
    NavbarComponent,
    DashboardComponent,
    WidgetClientsComponent,
    WidgetTopClientsComponent,
    WidgetTotalClientsComponent,
    WidgetTotalTokensComponent,
    SettingsComponent,
    HumanDatePipe,
    MapToIterablePipe,
    DummyComponent,
    UsersComponent,
    UserComponent,
    ScopeCreationComponent,
    ScopeComponent,
    MaterialFileComponent,
    ManagementComponent,
    ManagementGeneralComponent
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    FormsModule,
    ReactiveFormsModule,
    HttpModule,
    AppRoutingModule,
    MatAutocompleteModule, MatButtonModule, MatButtonToggleModule, MatCardModule, MatCheckboxModule, MatChipsModule, MatDatepickerModule, MatDialogModule, MatExpansionModule, MatGridListModule, MatIconModule, MatInputModule, MatListModule, MatMenuModule, MatNativeDateModule, MatPaginatorModule, MatProgressBarModule, MatProgressSpinnerModule, MatRadioModule, MatRippleModule, MatSelectModule, MatSidenavModule, MatSliderModule, MatSlideToggleModule, MatSnackBarModule, MatSortModule, MatTableModule, MatTabsModule, MatToolbarModule, MatTooltipModule, MatStepperModule,
    FlexLayoutModule,
    NgxDatatableModule,
    JsonSchemaFormModule,
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
    DashboardService,
    UserService,
    ExtensionGrantService,
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
    UsersResolver,
    UserResolver,
    ExtensionGrantsResolver,
    ExtensionGrantResolver,
    ScopesResolver,
    ScopeResolver,
    ScopeService,
    { provide: Http, useClass: HttpService }
  ],
  entryComponents: [
    ConfirmComponent,
    DomainSettingsLoginInfoDialog,
    CreateMapperComponent,
    CreateClaimComponent,
    CertitificatePublicKeyDialog,
    CreateRoleMapperComponent,
    SnackbarComponent,
    MaterialFileComponent
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }

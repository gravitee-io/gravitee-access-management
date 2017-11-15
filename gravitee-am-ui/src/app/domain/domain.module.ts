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
import {CommonModule} from "@angular/common";
import {FormsModule} from "@angular/forms";
import {
  MdButtonModule, MdCheckboxModule, MdChipsModule, MdDialogModule, MdInputModule, MdOptionModule, MdRadioModule,
  MdSelectModule,
  MdSlideToggleModule,
  MdTabsModule,
  MdTooltipModule
} from "@angular/material";
import {CodemirrorModule} from "ng2-codemirror";
import {ClipboardModule} from "ngx-clipboard/dist";
import {JsonSchemaFormModule} from "angular2-json-schema-form";
import {SharedModule} from "../shared/shared.module";
import {DomainRoutingModule} from "./domain-routing.module";
import {DomainComponent} from "./domain.component";
import {DomainDashboardComponent} from "./dashboard/dashboard.component";
import {DomainSettingsComponent} from "./settings/settings.component";
import {DomainSettingsLoginComponent, DomainSettingsLoginInfoDialog} from "./settings/login/login.component";
import {DomainSettingsSidenavComponent} from "./settings/sidenav/sidenav.component";
import {DomainSettingsGeneralComponent} from "./settings/general/general.component";
import {DomainSettingsProvidersComponent} from "./settings/providers/providers.component";
import {DomainSettingsRolesComponent} from "./settings/roles/roles.component";
import {DomainSettingsCertificatesComponent} from "./settings/certificates/certificates.component";
import {DomainSettingsExtensionGrantsComponent} from "./settings/extension-grants/extension-grants.component";
import {NgxDatatableModule} from "@swimlane/ngx-datatable";
import {ProvidersResolver} from "./shared/resolvers/providers.resolver";
import {ProviderResolver} from "./shared/resolvers/provider.resolver";
import {DomainLoginFormResolver} from "./shared/resolvers/domain-login-form.resolver";
import {CertificatesResolver} from "./shared/resolvers/certificates.resolver";
import {CertificateResolver} from "./shared/resolvers/certificate.resolver";
import {RolesResolver} from "./shared/resolvers/roles.resolver";
import {RoleResolver} from "./shared/resolvers/role.resolver";
import {UsersResolver} from "./shared/resolvers/users.resolver";
import {UserResolver} from "./shared/resolvers/user.resolver";
import {ExtensionGrantsResolver} from "./shared/resolvers/extension-grants.resolver";
import {ExtensionGrantResolver} from "./shared/resolvers/extension-grant.resolver";
import {ProviderService} from "./shared/services/provider.service";
import {CertificateService} from "./shared/services/certificate.service";
import {RoleService} from "./shared/services/role.service";
import {UserService} from "./shared/services/user.service";
import {ExtensionGrantService} from "./shared/services/extension-grant.service";
import {ProviderCreationComponent} from "./settings/providers/creation/provider-creation.component";
import {ClientComponent} from "./clients/client/client.component";
import {ClientSettingsComponent} from "./clients/client/settings/settings.component";
import {ClientOIDCComponent, CreateClaimComponent} from "./clients/client/oidc/oidc.component";
import {ClientIdPComponent} from "./clients/client/idp/idp.component";
import {ProviderCreationStep1Component} from "./settings/providers/creation/steps/step1/step1.component";
import {ProviderCreationStep2Component} from "./settings/providers/creation/steps/step2/step2.component";
import {ProviderComponent} from "./settings/providers/provider/provider.component";
import {ProviderFormComponent} from "./settings/providers/provider/form/form.component";
import {ProviderSettingsComponent} from "./settings/providers/provider/settings/settings.component";
import {CreateMapperComponent, ProviderMappersComponent} from "./settings/providers/provider/mappers/mappers.component";
import {CreateRoleMapperComponent, ProviderRolesComponent} from "./settings/providers/provider/roles/roles.component";
import {CertificateCreationComponent} from "./settings/certificates/creation/certificate-creation.component";
import {CertificateComponent} from "./settings/certificates/certificate/certificate.component";
import {CertificateCreationStep1Component} from "./settings/certificates/creation/steps/step1/step1.component";
import {CertificateCreationStep2Component} from "./settings/certificates/creation/steps/step2/step2.component";
import {CertificateFormComponent} from "./settings/certificates/certificate/form/form.component";
import {RoleCreationComponent} from "./settings/roles/creation/role-creation.component";
import {RoleComponent} from "./settings/roles/role/role.component";
import {ExtensionGrantCreationComponent} from "./settings/extension-grants/creation/extension-grant-creation.component";
import {ExtensionGrantComponent} from "./settings/extension-grants/extension-grant/extension-grant.component";
import {ExtensionGrantCreationStep1Component} from "./settings/extension-grants/creation/steps/step1/step1.component";
import {ExtensionGrantCreationStep2Component} from "./settings/extension-grants/creation/steps/step2/step2.component";
import {ExtensionGrantFormComponent} from "./settings/extension-grants/extension-grant/form/form.component";
import {UsersComponent} from "./users/users.component";
import {UserComponent} from "./users/user/user.component";
import {ClientResolver} from "../clients/shared/resolvers/client.resolver";
import {MaterialDesignFrameworkComponent} from "./shared/components/json-schema-form/material-design-framework.component";
import {MaterialInputComponent} from "./shared/components/json-schema-form/material-input.component";
import {MaterialFileComponent} from "./shared/components/json-schema-form/material-file.component";
import {MaterialAddReferenceComponent} from "./shared/components/json-schema-form/material-add-reference.component";
import {DomainService} from "./shared/services/domain.service";
import {DomainsResolver} from "./shared/resolvers/domains.resolver";
import {DomainResolver} from "./shared/resolvers/domain.resolver";
import {PlatformService} from "./shared/services/platform.service";

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    CodemirrorModule,
    MdInputModule,
    MdTooltipModule,
    MdSlideToggleModule,
    MdTabsModule,
    MdButtonModule,
    MdCheckboxModule,
    MdChipsModule,
    MdOptionModule,
    MdRadioModule,
    MdSelectModule,
    MdDialogModule,
    NgxDatatableModule,
    ClipboardModule,
    JsonSchemaFormModule,
    SharedModule,
    DomainRoutingModule
  ],
  declarations: [
    DomainComponent,
    DomainDashboardComponent,
    DomainSettingsComponent,
    DomainSettingsLoginComponent,
    DomainSettingsSidenavComponent,
    DomainSettingsGeneralComponent,
    DomainSettingsProvidersComponent,
    DomainSettingsRolesComponent,
    DomainSettingsCertificatesComponent,
    DomainSettingsLoginInfoDialog,
    DomainSettingsExtensionGrantsComponent,
    ProviderCreationComponent,
    ClientComponent,
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
    CreateClaimComponent,
    CertificateCreationComponent,
    CertificateComponent,
    CertificateCreationStep1Component,
    CertificateCreationStep2Component,
    CertificateFormComponent,
    RoleCreationComponent,
    RoleComponent,
    CreateRoleMapperComponent,
    ExtensionGrantCreationComponent,
    ExtensionGrantComponent,
    ExtensionGrantCreationStep1Component,
    ExtensionGrantCreationStep2Component,
    ExtensionGrantFormComponent,
    UsersComponent,
    UserComponent,
    MaterialDesignFrameworkComponent,
    MaterialInputComponent,
    MaterialFileComponent,
    MaterialAddReferenceComponent
  ],
  providers: [
    DomainService,
    ProviderService,
    CertificateService,
    RoleService,
    UserService,
    PlatformService,
    DomainsResolver,
    DomainResolver,
    ExtensionGrantService,
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
    ClientResolver
  ],
  entryComponents: [
    DomainSettingsLoginInfoDialog,
    MaterialDesignFrameworkComponent,
    MaterialInputComponent,
    MaterialFileComponent,
    MaterialAddReferenceComponent
  ]
})
export class DomainModule { }

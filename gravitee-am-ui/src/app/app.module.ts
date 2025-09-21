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
import '../polyfills';
import 'codemirror';
import 'codemirror/mode/htmlmixed/htmlmixed';
import 'codemirror/addon/selection/mark-selection';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { CUSTOM_ELEMENTS_SCHEMA, NgModule, inject, provideAppInitializer } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatNativeDateModule, MatRippleModule } from '@angular/material/core';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSliderModule } from '@angular/material/slider';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSortModule } from '@angular/material/sort';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { FlexLayoutModule } from '@angular/flex-layout';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { MaterialDesignFrameworkModule } from '@ajsf/material';
import { HighchartsChartModule } from 'highcharts-angular';
import { ClipboardModule } from 'ngx-clipboard';
import { map, mergeMap } from 'rxjs/operators';
import {
  GioLicenseExpirationNotificationModule,
  GioLicenseModule,
  GioMatConfigModule,
  GioMenuModule,
  GioSafePipeModule,
  GioSaveBarModule,
  GioSubmenuModule,
  GioTopBarLinkModule,
  GioTopBarMenuModule,
  GioTopBarModule,
} from '@gravitee/ui-particles-angular';
import { CodemirrorModule } from '@ctrl/ngx-codemirror';
import { NgOptimizedImage } from '@angular/common';

import { AppConfig } from '../config/app.config';

import { CertificateCreationStep2Component } from './domain/settings/certificates/creation/steps/step2/step2.component';
import { ExtensionGrantResolver } from './resolvers/extension-grant.resolver';
import { ExtensionGrantComponent } from './domain/settings/extension-grants/extension-grant/extension-grant.component';
import { CreateRoleMapperComponent, ProviderRolesComponent } from './domain/settings/providers/provider/roles/roles.component';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HomeComponent } from './home/home.component';
import { DisplayableItemPipe, SidenavComponent } from './components/sidenav/sidenav.component';
import { SubmenuComponent, SubmenuItemsComponent } from './components/submenu/submenu.component';
import { LoginComponent } from './login/login.component';
import { LoginCallbackComponent } from './login/callback/callback.component';
import { DomainsComponent } from './settings/domains/domains.component';
import { DomainService } from './services/domain.service';
import { OpenFGAService } from './services/openfga.service';
import { DomainComponent } from './domain/domain.component';
import { SidenavService } from './components/sidenav/sidenav.service';
import { NavbarService } from './components/navbar/navbar.service';
import { ConfirmComponent } from './components/dialog/confirm/confirm.component';
import { DialogService } from './services/dialog.service';
import { SnackbarService } from './services/snackbar.service';
import { EmptystateComponent } from './components/emptystate/emptystate.component';
import { DomainCreationComponent } from './domain/creation/domain-creation.component';
import { ProviderCreationComponent } from './domain/settings/providers/creation/provider-creation.component';
import { ProviderCreationStep1Component } from './domain/settings/providers/creation/steps/step1/step1.component';
import { ProviderCreationStep2Component } from './domain/settings/providers/creation/steps/step2/step2.component';
import { ProviderComponent } from './domain/settings/providers/provider/provider.component';
import { ProviderFormComponent } from './domain/settings/providers/provider/form/form.component';
import { ProviderService } from './services/provider.service';
import { OrganizationService } from './services/organization.service';
import { EnvironmentService } from './services/environment.service';
import { AuthService } from './services/auth.service';
import { LogoutComponent } from './logout/logout.component';
import { LogoutCallbackComponent } from './logout/callback/callback.component';
import { DomainsResolver } from './resolvers/domains.resolver';
import { DomainResolver } from './resolvers/domain.resolver';
import { DomainEntrypointResolver } from './resolvers/domain-entrypoint.resolver';
import { DomainFlowsResolver } from './resolvers/domain-flows.resolver';
import { DomainDashboardComponent } from './domain/dashboard/dashboard.component';
import { DomainSettingsComponent } from './domain/settings/settings.component';
import { DomainSettingsGeneralComponent } from './domain/settings/general/general.component';
import { DomainSettingsEntrypointsComponent } from './domain/settings/entrypoints/entrypoints.component';
import { DomainSettingsOpenidClientRegistrationComponent } from './domain/settings/openid/client-registration/client-registration.component';
import { ClientRegistrationSettingsComponent } from './domain/settings/openid/client-registration/settings/settings.component';
import { ClientRegistrationDefaultScopeComponent } from './domain/settings/openid/client-registration/default-scope/default-scope.component';
import { ClientRegistrationAllowedScopeComponent } from './domain/settings/openid/client-registration/allowed-scope/allowed-scope.component';
import { ClientRegistrationTemplatesComponent } from './domain/settings/openid/client-registration/templates/templates.component';
import { DomainSettingsRolesComponent } from './domain/settings/roles/roles.component';
import { DomainSettingsScopesComponent } from './domain/settings/scopes/scopes.component';
import {
  CertitificatePublicKeyDialogComponent,
  DomainSettingsCertificatesComponent,
} from './domain/settings/certificates/certificates.component';
import { DomainSettingsProvidersComponent } from './domain/settings/providers/providers.component';
import { DomainSettingsExtensionGrantsComponent } from './domain/settings/extension-grants/extension-grants.component';
import { DomainSettingsFormsComponent } from './domain/settings/forms/forms.component';
import { DomainSettingsFormComponent } from './domain/settings/forms/form/form.component';
import { DomainSettingsLoginComponent } from './domain/settings/login/login.component';
import { DomainSettingsEmailsComponent } from './domain/settings/emails/emails.component';
import { DomainSettingsEmailComponent } from './domain/settings/emails/email/email.component';
import { DomainSettingsAccountComponent } from './domain/settings/account/account.component';
import { DomainSettingsSelfServiceAccountComponent } from './domain/settings/self-service-account/self-service-account.component';
import { DomainMembershipsDialogComponent, DomainSettingsMembershipsComponent } from './domain/settings/memberships/memberships.component';
import { DomainSettingsFactorsComponent } from './domain/settings/factors/factors.component';
import { DomainSettingsResourcesComponent } from './domain/settings/resources/resources.component';
import { DomainSettingsWebAuthnComponent } from './domain/settings/webauthn/webauthn.component';
import { DomainSettingsFlowsComponent } from './domain/settings/flows/flows.component';
import { ProvidersResolver } from './resolvers/providers.resolver';
import { ProviderResolver } from './resolvers/provider.resolver';
import { ProviderSettingsComponent } from './domain/settings/providers/provider/settings/settings.component';
import { CreateMapperComponent, ProviderMappersComponent } from './domain/settings/providers/provider/mappers/mappers.component';
import { BreadcrumbComponent } from './components/breadcrumb/breadcrumb.component';
import { CertificateCreationComponent } from './domain/settings/certificates/creation/certificate-creation.component';
import { CertificateComponent } from './domain/settings/certificates/certificate/certificate.component';
import { CertificatesResolver } from './resolvers/certificates.resolver';
import { SignCertificatesResolver } from './resolvers/sign-certificates.resolver';
import { CertificateService } from './services/certificate.service';
import { CertificateCreationStep1Component } from './domain/settings/certificates/creation/steps/step1/step1.component';
import { CertificateFormComponent } from './domain/settings/certificates/certificate/form/form.component';
import { CertificateResolver } from './resolvers/certificate.resolver';
import { RoleService } from './services/role.service';
import { RolesResolver } from './resolvers/roles.resolver';
import { PageRolesResolver } from './resolvers/page-roles.resolver';
import { RoleResolver } from './resolvers/role.resolver';
import { RoleCreationComponent } from './domain/settings/roles/creation/role-creation.component';
import { RoleComponent } from './domain/settings/roles/role/role.component';
import { ScopeService } from './services/scope.service';
import { ScopesResolver } from './resolvers/scopes.resolver';
import { ScopeResolver } from './resolvers/scope.resolver';
import { ScopeCreationComponent } from './domain/settings/scopes/creation/scope-creation.component';
import { ScopeComponent } from './domain/settings/scopes/scope/scope.component';
import { SnackbarComponent } from './components/snackbar/snackbar.component';
import { NavbarComponent } from './components/navbar/navbar.component';
import { TabNavbarComponent } from './components/tab-navbar/tab-navbar.component';
import { DashboardService } from './services/dashboard.service';
import { SettingsComponent } from './settings/settings.component';
import { HumanDatePipe } from './pipes/human-date.pipe';
import { MapToIterablePipe } from './pipes/map-to-iterable.pipe';
import { DummyComponent } from './components/dummy/dummy.component';
import { UsersComponent, UsersSearchInfoDialogComponent } from './domain/settings/users/users.component';
import { UserComponent } from './domain/settings/users/user/user.component';
import { UserCreationComponent } from './domain/settings/users/creation/user-creation.component';
import { UserClaimComponent } from './domain/settings/users/creation/user-claim.component';
import { UserProfileComponent } from './domain/settings/users/user/profile/profile.component';
import { UserApplicationsComponent } from './domain/settings/users/user/applications/applications.component';
import { UserApplicationComponent } from './domain/settings/users/user/applications/application/application.component';
import { AddUserRolesComponent, UserRolesComponent } from './domain/settings/users/user/roles/roles.component';
import { UserFactorsComponent } from './domain/settings/users/user/factors/factors.component';
import { UserIdentitiesComponent } from './domain/settings/users/user/identities/identities.component';
import { UserIdentitiesResolver } from './resolvers/user-identities.resolver';
import { UserCredentialsComponent } from './domain/settings/users/user/credentials/credentials.component';
import { UserCredentialComponent } from './domain/settings/users/user/credentials/credential/credential.component';
import { UserCredentialsResolver } from './resolvers/user-credentials.resolver';
import { UserCredentialResolver } from './resolvers/user-credential.resolver';
import { UserService } from './services/user.service';
import { UserResolver } from './resolvers/user.resolver';
import { UserRolesResolver } from './resolvers/user-roles.resolver';
import { ExtensionGrantService } from './services/extension-grant.service';
import { ExtensionGrantsResolver } from './resolvers/extension-grants.resolver';
import { ExtensionGrantCreationComponent } from './domain/settings/extension-grants/creation/extension-grant-creation.component';
import { ExtensionGrantFormComponent } from './domain/settings/extension-grants/extension-grant/form/form.component';
import { ExtensionGrantCreationStep1Component } from './domain/settings/extension-grants/creation/steps/step1/step1.component';
import { ExtensionGrantCreationStep2Component } from './domain/settings/extension-grants/creation/steps/step2/step2.component';
import { MaterialFileComponent } from './components/json-schema-form/material-file.component';
import { MaterialCertificateComponent } from './components/json-schema-form/material-certificate-component';
import { ManagementComponent } from './settings/management/management.component';
import { ManagementGeneralComponent } from './settings/management/general/general.component';
import { SettingsMembershipsComponent } from './settings/memberships/memberships.component';
import { FormsComponent } from './domain/components/forms/forms.component';
import { FormComponent, FormInfoDialogComponent } from './domain/components/forms/form/form.component';
import { FormService } from './services/form.service';
import { FormResolver } from './resolvers/form.resolver';
import { GroupsComponent } from './domain/settings/groups/groups.component';
import { GroupCreationComponent } from './domain/settings/groups/creation/group-creation.component';
import { GroupComponent } from './domain/settings/groups/group/group.component';
import { GroupSettingsComponent } from './domain/settings/groups/group/settings/settings.component';
import { AddMemberComponent, GroupMembersComponent } from './domain/settings/groups/group/members/members.component';
import { GroupService } from './services/group.service';
import { GroupsResolver } from './resolvers/groups.resolver';
import { GroupResolver } from './resolvers/group.resolver';
import { GroupMembersResolver } from './resolvers/group-members.resolver';
import { AddGroupRolesComponent, GroupRolesComponent } from './domain/settings/groups/group/roles/roles.component';
import { GroupRolesResolver } from './resolvers/group-roles.resolver';
import { IdpSelectionInfoDialogComponent, ScimComponent } from './domain/settings/scim/scim.component';
import { EmailsComponent } from './domain/components/emails/emails.component';
import { EmailComponent, EmailInfoDialogComponent } from './domain/components/emails/email/email.component';
import { EmailService } from './services/email.service';
import { EmailResolver } from './resolvers/email.resolver';
import { SelectApplicationsComponent } from './domain/components/applications/select-applications.component';
import { ConsentsResolver } from './resolvers/consents.resolver';
import { AuditsComponent } from './domain/settings/audits/audits.component';
import { AuditComponent } from './domain/settings/audits/audit/audit.component';
import { AuditsSettingsComponent } from './domain/settings/audits/settings/settings.component';
import { AuditService } from './services/audit.service';
import { AuditsResolver } from './resolvers/audits.resolver';
import { AuditResolver } from './resolvers/audit.resolver';
import { ReporterService } from './services/reporter.service';
import { ReportersResolver } from './resolvers/reporters.resolver';
import { PluginReportersResolver } from './resolvers/plugin-reporters.resolver';
import { ReporterResolver } from './resolvers/reporter.resolver';
import { ReporterComponent } from './domain/settings/audits/settings/reporter/reporter.component';
import { ReporterFormComponent } from './domain/settings/audits/settings/reporter/form/form.component';
import { TagsResolver } from './resolvers/tags.resolver';
import { TagResolver } from './resolvers/tag.resolver';
import { TagService } from './services/tag.service';
import { TagsComponent } from './settings/management/tags/tags.component';
import { TagCreationComponent } from './settings/management/tags/creation/tag-creation.component';
import { TagComponent } from './settings/management/tags/tag/tag.component';
import { EntrypointsResolver } from './resolvers/entrypoints.resolver';
import { EntrypointResolver } from './resolvers/entrypoint.resolver';
import { EntrypointService } from './services/entrypoint.service';
import { EntrypointsComponent } from './settings/management/entrypoints/entrypoints.component';
import { EntrypointCreationComponent } from './settings/management/entrypoints/creation/entrypoint-creation.component';
import { EntrypointComponent } from './settings/management/entrypoints/entrypoint/entrypoint.component';
import { AccountSettingsComponent } from './domain/components/account/account-settings.component';
import { HttpRequestInterceptor } from './interceptors/http-request.interceptor';
import { PolicyService } from './services/policy.service';
import { ScopeSelectionComponent } from './domain/components/scope-selection/scope-selection.component';
import { RoleSelectionComponent } from './domain/components/role-selection/role-selection.component';
import { ApplicationsComponent } from './domain/applications/applications.component';
import { ApplicationService } from './services/application.service';
import { ApplicationsResolver } from './resolvers/applications.resolver';
import { ApplicationResolver } from './resolvers/application.resolver';
import { ApplicationCreationComponent } from './domain/applications/creation/application-creation.component';
import { ApplicationCreationStep1Component } from './domain/applications/creation/steps/step1/step1.component';
import { ApplicationCreationStep2Component } from './domain/applications/creation/steps/step2/step2.component';
import { ApplicationComponent } from './domain/applications/application/application.component';
import { ApplicationAnalyticsComponent } from './domain/applications/application/analytics/analytics.component';
import { ApplicationOverviewComponent } from './domain/applications/application/overview/overview.component';
import { ApplicationEndpointsComponent } from './domain/applications/application/endpoints/endpoints.component';
import { ApplicationToolsComponent } from './domain/applications/application/tools/tools.component';
import { ApplicationGeneralComponent } from './domain/applications/application/advanced/general/general.component';
import { PasswordPolicyComponent } from './domain/applications/application/advanced/password-policy/password-policy.component';
import { DomainPasswordPolicyComponent } from './domain/settings/password-policy/domain-password-policy.component';
import { ApplicationIdPComponent, CreateIdpSelectionRuleComponent } from './domain/applications/application/idp/idp.component';
import { ApplicationDesignComponent } from './domain/applications/application/design/design.component';
import { ApplicationFormsComponent } from './domain/applications/application/design/forms/forms.component';
import { ApplicationFormComponent } from './domain/applications/application/design/forms/form/form.component';
import { ApplicationEmailsComponent } from './domain/applications/application/design/emails/emails.component';
import { ApplicationEmailComponent } from './domain/applications/application/design/emails/email/email.component';
import { ApplicationAdvancedComponent } from './domain/applications/application/advanced/advanced.component';
import { ApplicationAccountSettingsComponent } from './domain/applications/application/advanced/account/account.component';
import { ApplicationOAuth2Component } from './domain/applications/application/advanced/oauth2/oauth2.component';
import { ApplicationSaml2Component } from './domain/applications/application/advanced/saml2/saml2.component';
import {
  AddScopeComponent,
  ApplicationScopesComponent,
} from './domain/applications/application/advanced/oauth2/scopes/application-scopes.component';
import {
  ApplicationTokensComponent,
  ClaimsInfoDialogComponent,
  CreateClaimComponent,
} from './domain/applications/application/advanced/oauth2/tokens/application-tokens.component';
import { ApplicationGrantFlowsComponent } from './domain/applications/application/advanced/oauth2/grantFlows/application-grant-flows.component';
import { ApplicationSecretsCertificatesComponent } from './domain/applications/application/advanced/secrets-certificates/secrets-certificates.component';
import { ApplicationMetadataComponent } from './domain/applications/application/advanced/metadata/metadata.component';
import { ApplicationAgentCardComponent } from './domain/applications/application/advanced/agent-card/agent-card.component';
import {
  ApplicationMembershipsComponent,
  ApplicationMembershipsDialogComponent,
} from './domain/applications/application/advanced/memberships/memberships.component';
import { ApplicationFactorsComponent } from './domain/applications/application/advanced/factors/factors.component';
import { ManagementRolesComponent } from './settings/management/roles/roles.component';
import { ManagementRoleComponent } from './settings/management/roles/role/role.component';
import { MembershipsResolver } from './resolvers/memberships.resolver';
import { SettingsResolver } from './resolvers/settings.resolver';
import { MembershipsComponent } from './domain/components/memberships/memberships.component';
import { ApplicationPermissionsResolver } from './resolvers/application-permissions.resolver';
import { DomainPermissionsResolver } from './resolvers/domain-permissions.resolver';
import { AuthGuard } from './guards/auth-guard.service';
import { HasPermissionDirective } from './directives/has-permission.directive';
import { HasAnyPermissionDirective } from './directives/has-any-permission.directive';
import { AnalyticsService } from './services/analytics.service';
import { DashboardComponent } from './domain/components/dashboard/dashboard.component';
import { WidgetComponent } from './components/widget/widget.component';
import { WidgetChartLineComponent } from './components/widget/chart-line/widget-chart-line.component';
import { WidgetChartPieComponent } from './components/widget/chart-pie/widget-chart-pie.component';
import { WidgetChartGaugeComponent } from './components/widget/chart-gauge/widget-chart-gauge.component';
import { WidgetDataTableComponent } from './components/widget/data-table/widget-data-table.component';
import { WidgetCountComponent } from './components/widget/count/widget-count.component';
import { LoaderComponent } from './components/loader/loader.component';
import { FactorsResolver } from './resolvers/factors.resolver';
import { FactorPluginsResolver } from './resolvers/factor-plugins.resolver';
import { FactorService } from './services/factor.service';
import { FactorComponent } from './domain/settings/factors/factor/factor.component';
import { FactorCreationComponent } from './domain/settings/factors/creation/factor-creation.component';
import { FactorCreationStep1Component } from './domain/settings/factors/creation/steps/step1/step1.component';
import { FactorCreationStep2Component } from './domain/settings/factors/creation/steps/step2/step2.component';
import { FactorFormComponent } from './domain/settings/factors/factor/form/form.component';
import { FactorResolver } from './resolvers/factor.resolver';
import { EnrolledFactorsResolver } from './resolvers/enrolled-factors.resolver';
import { ResourcesResolver } from './resolvers/resources.resolver';
import { ResourcePluginsResolver } from './resolvers/resource-plugins.resolver';
import { ResourceService } from './services/resource.service';
import { ResourceComponent } from './domain/settings/resources/resource/resource.component';
import { ResourceCreationComponent } from './domain/settings/resources/creation/resource-creation.component';
import { ResourceCreationStep1Component } from './domain/settings/resources/creation/steps/step1/step1.component';
import { ResourceCreationStep2Component } from './domain/settings/resources/creation/steps/step2/step2.component';
import { ResourceFormComponent } from './domain/settings/resources/resource/form/form.component';
import { ResourceResolver } from './resolvers/resource.resolver';
import { IdenticonHashDirective } from './directives/identicon-hash.directive';
import { UserAvatarComponent } from './components/user-avatar/user-avatar.component';
import { NotFoundComponent } from './not-found/not-found.component';
import { UmaComponent } from './domain/settings/uma/uma.component';
import { OIDCProfileComponent } from './domain/settings/openid/oidc-profile/oidc-profile.component';
import { CibaComponent } from './domain/settings/openid/ciba/ciba.component';
import { ApplicationResourcesComponent } from './domain/applications/application/advanced/resources/resources.component';
import { ApplicationResourcesResolver } from './resolvers/application-resources.resolver';
import { ApplicationResourceComponent } from './domain/applications/application/advanced/resources/resource/resource.component';
import { ApplicationResourceResolver } from './resolvers/application-resource.resolver';
import { ApplicationResourcePolicyComponent } from './domain/applications/application/advanced/resources/resource/policies/policy/policy.component';
import { ApplicationResourcePolicyResolver } from './resolvers/application-resource-policy.resolver';
import { LoginSettingsComponent } from './domain/components/login/login-settings.component';
import { ApplicationLoginSettingsComponent } from './domain/applications/application/advanced/login/login.component';
import { ApplicationCookieSettingsComponent } from './domain/applications/application/advanced/cookie/cookie.component';
import { ApplicationFlowsComponent } from './domain/applications/application/design/flows/flows.component';
import { IdentitiesResolver } from './resolvers/identities.resolver';
import { PluginPoliciesResolver } from './resolvers/plugin-policies.resolver';
import { PlatformFlowSchemaResolver } from './resolvers/platform-flow-schema.resolver';
import { NewsletterComponent } from './newsletter/newsletter.component';
import { NewsletterResolver } from './resolvers/newsletter.resolver';
import { UserHistoryComponent } from './domain/settings/users/user/history/history.component';
import { ApplicationFlowsResolver } from './resolvers/application-flows.resolver';
import { EnvironmentResolver } from './resolvers/environment-resolver.service';
import { NavigationService } from './services/navigation.service';
import { CockpitComponent } from './settings/cockpit/cockpit.component';
import { InstallationResolver } from './resolvers/installation.resolver';
import { InstallationService } from './services/installation.service';
import { EnvironmentComponent } from './environment/environment.component';
import { DomainAlertsComponent } from './domain/alerts/alerts.component';
import { DomainAlertGeneralComponent } from './domain/alerts/general/general.component';
import { AlertService } from './services/alert.service';
import { DomainAlertNotifiersComponent } from './domain/alerts/notifiers/notifiers.component';
import { NotifiersResolver } from './resolvers/notifiers.resolver';
import { AlertNotifiersResolver } from './resolvers/alert-notifiers.resolver';
import { AlertNotifierResolver } from './resolvers/alert-notifier.resolver';
import { DomainAlertNotifierCreationStep1Component } from './domain/alerts/notifiers/creation/steps/step1/step1.component';
import { DomainAlertNotifierCreationComponent } from './domain/alerts/notifiers/creation/notifier-creation.component';
import { DomainAlertNotifierCreationStep2Component } from './domain/alerts/notifiers/creation/steps/step2/step2.component';
import { AlertNotifierFormComponent } from './domain/alerts/notifiers/notifier/form/form.component';
import { DomainAlertNotifierComponent } from './domain/alerts/notifiers/notifier/notifier.component';
import { PlatformAlertStatusResolver } from './resolvers/platform-alert-status.resolver';
import { DomainSettingsBotDetectionsComponent } from './domain/settings/botdetections/bot-detections.component';
import { BotDetectionService } from './services/bot-detection.service';
import { BotDetectionsResolver } from './resolvers/bot-detections.resolver';
import { BotDetectionCreationComponent } from './domain/settings/botdetections/creation/bot-detection-creation.component';
import { BotDetectionCreationStep1Component } from './domain/settings/botdetections/creation/steps/step1/step1.component';
import { BotDetectionCreationStep2Component } from './domain/settings/botdetections/creation/steps/step2/step2.component';
import { BotDetectionPluginsResolver } from './resolvers/bot-detection-plugins.resolver';
import { BotDetectionComponent } from './domain/settings/botdetections/bot-detection/bot-detection.component';
import { BotDetectionFormComponent } from './domain/settings/botdetections/bot-detection/form/form.component';
import { BotDetectionResolver } from './resolvers/bot-detection.resolver';
import { ScopesAllResolver } from './resolvers/scopes-all.resolver';
import { GvFormControlDirective } from './directives/gv-form-control.directive';
import { DomainSettingsDeviceIdentifiersComponent } from './domain/settings/deviceidentifiers/device-identifiers.component';
import { DeviceIdentifierCreationComponent } from './domain/settings/deviceidentifiers/creation/device-identifier-creation.component';
import { DeviceIdentifierCreationStep1Component } from './domain/settings/deviceidentifiers/creation/steps/step1/step1.component';
import { DeviceIdentifierCreationStep2Component } from './domain/settings/deviceidentifiers/creation/steps/step2/step2.component';
import { DeviceIdentifierPluginsResolver } from './resolvers/device-identifier-plugins.resolver';
import { DeviceIdentifierFormComponent } from './domain/settings/deviceidentifiers/device-identifier/form/form.component';
import { DeviceIdentifiersResolver } from './resolvers/device-identifiers.resolver';
import { DeviceIdentifierResolver } from './resolvers/device-identifier.resolver';
import { DeviceIdentifierService } from './services/device-identifier.service';
import { DeviceIdentifierComponent } from './domain/settings/deviceidentifiers/device-identifier/device-identifier.component';
import { UserDevicesComponent } from './domain/settings/users/user/devices/devices.component';
import { UserDevicesResolver } from './resolvers/user-devices.resolver';
import { DynamicUserRolesResolver } from './resolvers/dynamic-user-roles.resolver';
import { CibaSettingsComponent } from './domain/settings/openid/ciba/settings/ciba-settings.component';
import { DeviceNotifiersCreationComponent } from './domain/settings/openid/ciba/device-notifiers/create/device-notifiers-creation.component';
import { DeviceNotifiersComponent } from './domain/settings/openid/ciba/device-notifiers/device-notifiers.component';
import { DeviceNotifiersService } from './services/device-notifiers.service';
import { DeviceNotifiersResolver } from './resolvers/device-notifiers.resolver';
import { DeviceNotifierPluginsResolver } from './resolvers/device-notifier-plugins.resolver';
import { DeviceNotifierResolver } from './resolvers/device-notifier.resolver';
import { DeviceNotifierCreationStep1Component } from './domain/settings/openid/ciba/device-notifiers/create/steps/step1/step1.component';
import { DeviceNotifierCreationStep2Component } from './domain/settings/openid/ciba/device-notifiers/create/steps/step2/step2.component';
import { DeviceNotifierComponent } from './domain/settings/openid/ciba/device-notifiers/device-notifier/device-notifier.component';
import { DeviceNotifierFormComponent } from './domain/settings/openid/ciba/device-notifiers/device-notifier/form/form.component';
import { CookieSettingsComponent } from './domain/components/cookie/cookie-settings.component';
import { UserNotificationsService } from './services/user-notifications.service';
import { Saml2Component } from './domain/settings/saml2/saml2.component';
import { MfaSelectComponent } from './domain/applications/application/advanced/factors/mfa/mfa-select.component';
import { MfaRememberDeviceComponent } from './domain/applications/application/advanced/factors/remember-device/mfa-remember-device.component';
import { TimeConverterService } from './services/time-converter.service';
import { MfaStepUpComponent } from './domain/applications/application/advanced/factors/step-up-auth/mfa-step-up.component';
import { MfaActivateComponent } from './domain/applications/application/advanced/factors/mfa-activate/mfa-activate.component';
import { TimePeriodPickerComponent } from './domain/applications/application/advanced/factors/time-period-picker/time-period-picker.component';
import { MfaConditionalComponent } from './domain/applications/application/advanced/factors/mfa-activate/conditional/mfa-conditional.component';
import { MfaRiskBasedComponent } from './domain/applications/application/advanced/factors/mfa-challenge/risk-based/mfa-risk-based.component';
import { AssessmentComponent } from './domain/applications/application/advanced/factors/mfa-challenge/risk-based/assessment/assessment.component';
import { DomainSettingsDictionariesComponent } from './domain/settings/texts/dictionaries.component';
import { DictionaryDialogComponent } from './components/dialog/dictionary/dictionary-dialog.component';
import { DictionariesResolver } from './resolvers/dictionaries.resolver';
import { I18nDictionaryService } from './services/dictionary.service';
import { DomainSettingsThemeComponent } from './domain/settings/theme/theme.component';
import { ThemesResolver } from './resolvers/themes.resolver';
import { ThemeService } from './services/theme.service';
import { HelpTipsThemeComponent } from './domain/settings/theme/help-tips/help-tips.component';
import { EmailTemplateFactoryService } from './services/email.template.factory.service';
import { FormTemplateFactoryService } from './services/form.template.factory.service';
import { LicenseGuard } from './guards/license-guard.service';
import { MfaChallengeComponent } from './domain/applications/application/advanced/factors/mfa-challenge/mfa-challenge.component';
import { InfoBannerComponent } from './domain/applications/application/advanced/factors/info-banner/info-banner.component';
import { ExpressionInfoDialogComponent } from './domain/applications/application/advanced/factors/expression-info-dialog/expression-info-dialog.component';
import { FactorsSelectDialogComponent } from './domain/applications/application/advanced/factors/mfa/factors-select-dialog/factors-select-dialog.component';
import { SelectionRuleDialogComponent } from './domain/applications/application/advanced/factors/selection-rule-dialog/selection-rule-dialog.component';
import { PasswordPoliciesComponent } from './domain/settings/password-policies/domain-password-policies.component';
import { PasswordPoliciesIdpSelectDialogFactory } from './domain/settings/password-policies/password-policies-idp-select-dialog/password-policies-idp-select-dialog.factory';
import { AccountTokenDialogModule } from './domain/settings/users/user/profile/token/account-token-dialog.module';
import { PasswordPoliciesResolver } from './resolvers/password-policies-resolver.service';
import { PasswordPolicyResolver } from './resolvers/password-policy-resolver';
import { PasswordPolicyService } from './services/password-policy.service';
import { CreateGroupMapperComponent, ProviderGroupsComponent } from './domain/settings/providers/provider/groups/groups/groups.component';
import { IdentitiesOrganizationResolver } from './resolvers/identities-organization.resolver';
import { PasswordPolicyStatusComponent } from './domain/settings/password-policy/pass-policy-status/password-policy-status.component';
import { DataPlaneService } from './services/data-plane.service';
import { DataPlanesResolver } from './resolvers/data-planes.resolver';
import { SecretsCertificatesModule } from './domain/applications/application/advanced/secrets-certificates/secrets-certificates.module';
import { DomainSettingsSecretsComponent } from './domain/settings/secrets/secrets.component';
import { DomainStoreService } from './stores/domain.store';

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    SidenavComponent,
    SubmenuComponent,
    SubmenuItemsComponent,
    LoginComponent,
    LoginCallbackComponent,
    DomainsComponent,
    CockpitComponent,
    DomainComponent,
    DomainDashboardComponent,
    DomainSettingsComponent,
    DomainSettingsGeneralComponent,
    DomainSettingsEntrypointsComponent,
    DomainSettingsOpenidClientRegistrationComponent,
    ClientRegistrationSettingsComponent,
    ClientRegistrationDefaultScopeComponent,
    ClientRegistrationAllowedScopeComponent,
    ClientRegistrationTemplatesComponent,
    DomainSettingsProvidersComponent,
    DomainSettingsScopesComponent,
    DomainSettingsRolesComponent,
    DomainSettingsCertificatesComponent,
    DomainSettingsExtensionGrantsComponent,
    DomainSettingsDictionariesComponent,
    DomainSettingsFormsComponent,
    DomainSettingsFormComponent,
    DomainSettingsLoginComponent,
    DomainSettingsEmailsComponent,
    DomainSettingsEmailComponent,
    DomainSettingsAccountComponent,
    DomainSettingsSelfServiceAccountComponent,
    DomainSettingsMembershipsComponent,
    DomainSettingsFactorsComponent,
    DomainSettingsResourcesComponent,
    DomainSettingsWebAuthnComponent,
    DomainSettingsFlowsComponent,
    DomainSettingsDeviceIdentifiersComponent,
    DomainSettingsThemeComponent,
    ConfirmComponent,
    DictionaryDialogComponent,
    EmptystateComponent,
    DomainCreationComponent,
    ProviderCreationComponent,
    ProviderCreationStep1Component,
    ProviderCreationStep2Component,
    ProviderComponent,
    ProviderFormComponent,
    ProviderSettingsComponent,
    ProviderMappersComponent,
    ProviderRolesComponent,
    ProviderGroupsComponent,
    CreateMapperComponent,
    CreateIdpSelectionRuleComponent,
    LogoutComponent,
    LogoutCallbackComponent,
    BreadcrumbComponent,
    CreateClaimComponent,
    CertificateCreationComponent,
    CertificateComponent,
    CertificateCreationStep1Component,
    CertificateCreationStep2Component,
    CertificateFormComponent,
    CertitificatePublicKeyDialogComponent,
    RoleCreationComponent,
    RoleComponent,
    CreateRoleMapperComponent,
    CreateGroupMapperComponent,
    ExtensionGrantCreationComponent,
    ExtensionGrantComponent,
    ExtensionGrantCreationStep1Component,
    ExtensionGrantCreationStep2Component,
    ExtensionGrantFormComponent,
    FactorComponent,
    FactorCreationComponent,
    FactorCreationStep1Component,
    FactorCreationStep2Component,
    FactorFormComponent,
    ResourceComponent,
    ResourceCreationComponent,
    ResourceCreationStep1Component,
    ResourceCreationStep2Component,
    DeviceIdentifierComponent,
    DeviceIdentifierCreationComponent,
    DeviceIdentifierCreationStep1Component,
    DeviceIdentifierCreationStep2Component,
    DeviceIdentifierFormComponent,
    ResourceFormComponent,
    SnackbarComponent,
    NavbarComponent,
    TabNavbarComponent,
    SettingsComponent,
    HumanDatePipe,
    MapToIterablePipe,
    DummyComponent,
    UsersComponent,
    UsersComponent,
    UserComponent,
    UserCreationComponent,
    UserClaimComponent,
    UserProfileComponent,
    UserApplicationsComponent,
    UserApplicationComponent,
    UserRolesComponent,
    UserFactorsComponent,
    UserIdentitiesComponent,
    UserCredentialsComponent,
    UserCredentialComponent,
    UserDevicesComponent,
    AddUserRolesComponent,
    ScopeCreationComponent,
    ScopeComponent,
    MaterialFileComponent,
    MaterialCertificateComponent,
    ManagementComponent,
    ManagementGeneralComponent,
    SettingsMembershipsComponent,
    FormsComponent,
    FormComponent,
    FormInfoDialogComponent,
    GroupsComponent,
    GroupCreationComponent,
    GroupComponent,
    GroupSettingsComponent,
    GroupMembersComponent,
    GroupRolesComponent,
    AddMemberComponent,
    AddGroupRolesComponent,
    ScimComponent,
    EmailsComponent,
    EmailComponent,
    EmailInfoDialogComponent,
    SelectApplicationsComponent,
    AuditsComponent,
    AuditComponent,
    AuditsSettingsComponent,
    ReporterComponent,
    ReporterFormComponent,
    TagsComponent,
    TagCreationComponent,
    TagComponent,
    EntrypointsComponent,
    EntrypointCreationComponent,
    EntrypointComponent,
    AccountSettingsComponent,
    ScopeSelectionComponent,
    ClaimsInfoDialogComponent,
    RoleSelectionComponent,
    ApplicationsComponent,
    ApplicationAnalyticsComponent,
    ApplicationCreationComponent,
    ApplicationCreationStep1Component,
    ApplicationCreationStep2Component,
    ApplicationComponent,
    ApplicationOverviewComponent,
    ApplicationEndpointsComponent,
    ApplicationToolsComponent,
    ApplicationGeneralComponent,
    PasswordPolicyComponent,
    PasswordPolicyStatusComponent,
    DomainPasswordPolicyComponent,
    ApplicationIdPComponent,
    ApplicationDesignComponent,
    ApplicationFormsComponent,
    ApplicationFormComponent,
    ApplicationEmailsComponent,
    ApplicationEmailComponent,
    ApplicationAdvancedComponent,
    ApplicationAccountSettingsComponent,
    ApplicationOAuth2Component,
    ApplicationSaml2Component,
    ApplicationScopesComponent,
    AddScopeComponent,
    ApplicationTokensComponent,
    ApplicationGrantFlowsComponent,
    ApplicationSecretsCertificatesComponent,
    ApplicationMetadataComponent,
    ApplicationAgentCardComponent,
    ApplicationMembershipsComponent,
    ApplicationFactorsComponent,
    ApplicationResourcesComponent,
    ApplicationResourceComponent,
    ApplicationResourcePolicyComponent,
    ApplicationLoginSettingsComponent,
    ApplicationCookieSettingsComponent,
    ApplicationFlowsComponent,
    ManagementRolesComponent,
    ManagementRoleComponent,
    MembershipsComponent,
    HasPermissionDirective,
    HasAnyPermissionDirective,
    IdenticonHashDirective,
    InfoBannerComponent,
    DashboardComponent,
    WidgetComponent,
    WidgetChartLineComponent,
    WidgetChartPieComponent,
    WidgetChartGaugeComponent,
    WidgetDataTableComponent,
    WidgetCountComponent,
    LoaderComponent,
    DomainMembershipsDialogComponent,
    ApplicationMembershipsDialogComponent,
    UserAvatarComponent,
    NotFoundComponent,
    UmaComponent,
    OIDCProfileComponent,
    CibaComponent,
    CibaSettingsComponent,
    Saml2Component,
    DeviceNotifiersComponent,
    DeviceNotifierComponent,
    DeviceNotifierFormComponent,
    DeviceNotifiersCreationComponent,
    DeviceNotifierCreationStep1Component,
    DeviceNotifierCreationStep2Component,
    CookieSettingsComponent,
    LoginSettingsComponent,
    UsersSearchInfoDialogComponent,
    NewsletterComponent,
    UserHistoryComponent,
    EnvironmentComponent,
    DomainAlertsComponent,
    DomainAlertGeneralComponent,
    DomainAlertNotifiersComponent,
    DomainAlertNotifierCreationComponent,
    DomainAlertNotifierCreationStep1Component,
    DomainAlertNotifierCreationStep2Component,
    AlertNotifierFormComponent,
    DomainAlertNotifierComponent,
    DomainSettingsBotDetectionsComponent,
    BotDetectionCreationComponent,
    BotDetectionCreationStep1Component,
    BotDetectionCreationStep2Component,
    BotDetectionComponent,
    BotDetectionFormComponent,
    ExpressionInfoDialogComponent,
    SelectionRuleDialogComponent,
    GvFormControlDirective,
    IdpSelectionInfoDialogComponent,
    MfaSelectComponent,
    MfaRememberDeviceComponent,
    MfaStepUpComponent,
    MfaActivateComponent,
    TimePeriodPickerComponent,
    MfaConditionalComponent,
    MfaRiskBasedComponent,
    AssessmentComponent,
    HelpTipsThemeComponent,
    DisplayableItemPipe,
    MfaChallengeComponent,
    FactorsSelectDialogComponent,
    PasswordPoliciesComponent,
    DomainSettingsSecretsComponent,
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  bootstrap: [AppComponent],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    FormsModule,
    ReactiveFormsModule,
    AppRoutingModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatCheckboxModule,
    MatChipsModule,
    MatDatepickerModule,
    MatDialogModule,
    MatDividerModule,
    MatExpansionModule,
    MatGridListModule,
    MatIconModule,
    MatInputModule,
    MatListModule,
    MatMenuModule,
    MatNativeDateModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatRadioModule,
    MatRippleModule,
    MatSelectModule,
    MatSidenavModule,
    MatSliderModule,
    MatSlideToggleModule,
    MatSnackBarModule,
    MatSortModule,
    MatTableModule,
    MatTabsModule,
    MatToolbarModule,
    MatTooltipModule,
    MatStepperModule,
    MatBadgeModule,
    MaterialDesignFrameworkModule,
    GioMatConfigModule,
    GioMenuModule,
    GioTopBarModule,
    GioTopBarLinkModule,
    GioTopBarMenuModule,
    GioSubmenuModule,
    GioLicenseModule,
    GioSafePipeModule,
    GioSaveBarModule,
    GioLicenseExpirationNotificationModule,
    DragDropModule,
    FlexLayoutModule,
    NgxDatatableModule,
    CodemirrorModule,
    ClipboardModule,
    HighchartsChartModule,
    AccountTokenDialogModule,
    NgOptimizedImage,
    SecretsCertificatesModule,
  ],
  providers: [
    DomainService,
    OpenFGAService,
    ProviderService,
    SidenavService,
    NavigationService,
    NavbarService,
    DialogService,
    SnackbarService,
    OrganizationService,
    EnvironmentService,
    AuthService,
    CertificateService,
    RoleService,
    DashboardService,
    UserService,
    ExtensionGrantService,
    InstallationService,
    DeviceIdentifierService,
    AlertService,
    AppConfig,
    DomainsResolver,
    DomainResolver,
    DomainEntrypointResolver,
    DomainFlowsResolver,
    ProvidersResolver,
    ProviderResolver,
    CertificatesResolver,
    SignCertificatesResolver,
    CertificateResolver,
    EnvironmentResolver,
    RolesResolver,
    PageRolesResolver,
    RoleResolver,
    UserResolver,
    UserRolesResolver,
    DynamicUserRolesResolver,
    UserCredentialsResolver,
    UserCredentialResolver,
    UserDevicesResolver,
    UserIdentitiesResolver,
    ExtensionGrantsResolver,
    ExtensionGrantResolver,
    ScopesResolver,
    ScopeResolver,
    ScopeService,
    FormService,
    FormResolver,
    FormTemplateFactoryService,
    GroupService,
    GroupsResolver,
    GroupResolver,
    GroupRolesResolver,
    GroupMembersResolver,
    EmailTemplateFactoryService,
    EmailService,
    EmailResolver,
    DictionariesResolver,
    ConsentsResolver,
    AuditService,
    AuditsResolver,
    AuditResolver,
    ReporterService,
    ReportersResolver,
    PluginReportersResolver,
    ReporterResolver,
    TagService,
    TagsResolver,
    TagResolver,
    EntrypointService,
    EntrypointsResolver,
    EntrypointResolver,
    PolicyService,
    ApplicationService,
    ApplicationsResolver,
    ApplicationResolver,
    MembershipsResolver,
    SettingsResolver,
    ApplicationPermissionsResolver,
    DomainPermissionsResolver,
    FactorService,
    FactorsResolver,
    FactorPluginsResolver,
    FactorResolver,
    EnrolledFactorsResolver,
    ResourceService,
    ResourcesResolver,
    ResourcePluginsResolver,
    ResourceResolver,
    AuthGuard,
    LicenseGuard,
    AnalyticsService,
    ApplicationResourcesResolver,
    ApplicationResourceResolver,
    ApplicationResourcePolicyResolver,
    IdentitiesResolver,
    IdentitiesOrganizationResolver,
    PluginPoliciesResolver,
    PlatformFlowSchemaResolver,
    NewsletterResolver,
    ApplicationFlowsResolver,
    InstallationResolver,
    NotifiersResolver,
    AlertNotifiersResolver,
    AlertNotifierResolver,
    PlatformAlertStatusResolver,
    BotDetectionService,
    BotDetectionsResolver,
    BotDetectionResolver,
    BotDetectionPluginsResolver,
    ScopesAllResolver,
    UserNotificationsService,
    DeviceIdentifierPluginsResolver,
    DeviceIdentifiersResolver,
    DeviceIdentifierResolver,
    DeviceNotifiersService,
    DeviceNotifiersResolver,
    DeviceNotifierResolver,
    DeviceNotifierPluginsResolver,
    TimeConverterService,
    I18nDictionaryService,
    ThemesResolver,
    ThemeService,
    PasswordPoliciesIdpSelectDialogFactory,
    PasswordPoliciesResolver,
    PasswordPolicyResolver,
    PasswordPolicyService,
    DataPlaneService,
    DataPlanesResolver,
    DomainStoreService,
    {
      provide: HTTP_INTERCEPTORS,
      useClass: HttpRequestInterceptor,
      multi: true,
    },
    provideAppInitializer(() => {
      const initializerFn = initCurrentUser(inject(AuthService), inject(EnvironmentService));
      return initializerFn();
    }),
    provideHttpClient(withInterceptorsFromDi()),
  ],
})
export class AppModule {}

export function initCurrentUser(authService: AuthService, environmentService: EnvironmentService): () => Promise<any> {
  return (): Promise<any> => {
    return authService
      .userInfo()
      .pipe(
        mergeMap((user) => {
          return environmentService.getAllEnvironments().pipe(
            map((environments) => {
              // For now the selected environment is the first from the list but could be changed in favor of a 'last env' coming from user's preferences.
              if (environments && environments.length >= 1) {
                environmentService.setCurrentEnvironment(environments[0]);
              } else {
                environmentService.setCurrentEnvironment(EnvironmentService.NO_ENVIRONMENT);
              }
              return user;
            }),
          );
        }),
      )
      .toPromise()
      .catch(() => null);
  };
}

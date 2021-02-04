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
import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';
import {LoginComponent} from './login/login.component';
import {LoginCallbackComponent} from './login/callback/callback.component';
import {DomainsComponent} from './settings/domains/domains.component';
import {DomainComponent} from './domain/domain.component';
import {DomainDashboardComponent} from './domain/dashboard/dashboard.component';
import {DomainSettingsComponent} from './domain/settings/settings.component';
import {DomainSettingsGeneralComponent} from './domain/settings/general/general.component';
import {DomainSettingsOpenidClientRegistrationComponent} from './domain/settings/openid/client-registration/client-registration.component';
import {ClientRegistrationSettingsComponent} from './domain/settings/openid/client-registration/settings/settings.component';
import {ClientRegistrationDefaultScopeComponent} from './domain/settings/openid/client-registration/default-scope/default-scope.component';
import {ClientRegistrationAllowedScopeComponent} from './domain/settings/openid/client-registration/allowed-scope/allowed-scope.component';
import {ClientRegistrationTemplatesComponent} from './domain/settings/openid/client-registration/templates/templates.component';
import {DomainSettingsCertificatesComponent} from './domain/settings/certificates/certificates.component';
import {DomainSettingsProvidersComponent} from './domain/settings/providers/providers.component';
import {DomainSettingsRolesComponent} from './domain/settings/roles/roles.component';
import {DomainSettingsScopesComponent} from './domain/settings/scopes/scopes.component';
import {DomainSettingsFormsComponent} from './domain/settings/forms/forms.component';
import {DomainSettingsFormComponent} from './domain/settings/forms/form/form.component';
import {DomainSettingsLoginComponent} from './domain/settings/login/login.component';
import {DomainSettingsEmailsComponent} from './domain/settings/emails/emails.component';
import {DomainSettingsEmailComponent} from './domain/settings/emails/email/email.component';
import {DomainSettingsExtensionGrantsComponent} from './domain/settings/extension-grants/extension-grants.component';
import {DomainSettingsAccountComponent} from './domain/settings/account/account.component';
import {DomainSettingsMembershipsComponent} from './domain/settings/memberships/memberships.component';
import {DomainSettingsFlowsComponent} from './domain/settings/flows/flows.component';
import {DomainCreationComponent} from './domain/creation/domain-creation.component';
import {ProviderCreationComponent} from './domain/settings/providers/creation/provider-creation.component';
import {ProviderComponent} from './domain/settings/providers/provider/provider.component';
import {LogoutCallbackComponent} from './logout/callback/callback.component';
import {LogoutComponent} from './logout/logout.component';
import {DomainsResolver} from './resolvers/domains.resolver';
import {DomainResolver} from './resolvers/domain.resolver';
import {DomainEntrypointResolver} from './resolvers/domain-entrypoint.resolver';
import {DomainFlowsResolver} from './resolvers/domain-flows.resolver';
import {DomainPermissionsResolver} from './resolvers/domain-permissions.resolver';
import {ProvidersResolver} from './resolvers/providers.resolver';
import {ProviderResolver} from './resolvers/provider.resolver';
import {ProviderRolesComponent} from './domain/settings/providers/provider/roles/roles.component';
import {ProviderSettingsComponent} from './domain/settings/providers/provider/settings/settings.component';
import {ProviderMappersComponent} from './domain/settings/providers/provider/mappers/mappers.component';
import {CertificatesResolver} from './resolvers/certificates.resolver';
import {CertificateCreationComponent} from './domain/settings/certificates/creation/certificate-creation.component';
import {CertificateComponent} from './domain/settings/certificates/certificate/certificate.component';
import {CertificateResolver} from './resolvers/certificate.resolver';
import {RolesResolver} from './resolvers/roles.resolver';
import {RoleCreationComponent} from './domain/settings/roles/creation/role-creation.component';
import {RoleComponent} from './domain/settings/roles/role/role.component';
import {RoleResolver} from './resolvers/role.resolver';
import {ScopeResolver} from './resolvers/scope.resolver';
import {ScopesResolver} from './resolvers/scopes.resolver';
import {ScopeCreationComponent} from './domain/settings/scopes/creation/scope-creation.component';
import {ScopeComponent} from './domain/settings/scopes/scope/scope.component';
import {SettingsComponent} from './settings/settings.component';
import {SettingsMembershipsComponent} from './settings/memberships/memberships.component';
import {UsersComponent} from './domain/settings/users/users.component';
import {UsersResolver} from './resolvers/users.resolver';
import {UserComponent} from './domain/settings/users/user/user.component';
import {UserResolver} from './resolvers/user.resolver';
import {UserCreationComponent} from './domain/settings/users/creation/user-creation.component';
import {UserProfileComponent} from './domain/settings/users/user/profile/profile.component';
import {UserApplicationsComponent} from './domain/settings/users/user/applications/applications.component';
import {UserApplicationComponent} from './domain/settings/users/user/applications/application/application.component';
import {UserRolesComponent} from './domain/settings/users/user/roles/roles.component';
import {UserRolesResolver} from './resolvers/user-roles.resolver';
import {UserFactorsComponent} from './domain/settings/users/user/factors/factors.component';
import {UserCredentialsResolver} from './resolvers/user-credentials.resolver';
import {UserCredentialResolver} from './resolvers/user-credential.resolver';
import {UserCredentialsComponent} from './domain/settings/users/user/credentials/credentials.component';
import {UserCredentialComponent} from'./domain/settings/users/user/credentials/credential/credential.component';
import {ExtensionGrantCreationComponent} from './domain/settings/extension-grants/creation/extension-grant-creation.component';
import {ExtensionGrantComponent} from './domain/settings/extension-grants/extension-grant/extension-grant.component';
import {ExtensionGrantsResolver} from './resolvers/extension-grants.resolver';
import {ExtensionGrantResolver} from './resolvers/extension-grant.resolver';
import {ManagementComponent} from './settings/management/management.component';
import {ManagementGeneralComponent} from './settings/management/general/general.component';
import {FormResolver} from './resolvers/form.resolver';
import {GroupsResolver} from './resolvers/groups.resolver';
import {GroupsComponent} from './domain/settings/groups/groups.component';
import {GroupCreationComponent} from './domain/settings/groups/creation/group-creation.component';
import {GroupResolver} from './resolvers/group.resolver';
import {GroupComponent} from './domain/settings/groups/group/group.component';
import {GroupSettingsComponent} from './domain/settings/groups/group/settings/settings.component';
import {GroupMembersComponent} from './domain/settings/groups/group/members/members.component';
import {GroupRolesComponent} from './domain/settings/groups/group/roles/roles.component';
import {GroupRolesResolver} from './resolvers/group-roles.resolver';
import {ScimComponent} from './domain/settings/scim/scim.component';
import {EmailResolver} from './resolvers/email.resolver';
import {ConsentsResolver} from './resolvers/consents.resolver';
import {AuditResolver} from './resolvers/audit.resolver';
import {AuditsComponent} from './domain/settings/audits/audits.component';
import {AuditComponent} from './domain/settings/audits/audit/audit.component';
import {AuditsSettingsComponent} from './domain/settings/audits/settings/settings.component';
import {ReportersResolver} from './resolvers/reporters.resolver';
import {ReporterResolver} from './resolvers/reporter.resolver';
import {ReporterComponent} from './domain/settings/audits/settings/reporter/reporter.component';
import {TagsResolver} from './resolvers/tags.resolver';
import {TagsComponent} from './settings/management/tags/tags.component';
import {TagCreationComponent} from './settings/management/tags/creation/tag-creation.component';
import {TagComponent} from './settings/management/tags/tag/tag.component';
import {TagResolver} from './resolvers/tag.resolver';
import {GroupMembersResolver} from './resolvers/group-members.resolver';
import {ApplicationsComponent} from './domain/applications/applications.component';
import {ApplicationsResolver} from './resolvers/applications.resolver';
import {ApplicationCreationComponent} from './domain/applications/creation/application-creation.component';
import {ApplicationComponent} from './domain/applications/application/application.component';
import {ApplicationOverviewComponent} from './domain/applications/application/overview/overview.component';
import {ApplicationEndpointsComponent} from './domain/applications/application/endpoints/endpoints.component';
import {ApplicationResolver} from './resolvers/application.resolver';
import {ApplicationPermissionsResolver} from './resolvers/application-permissions.resolver';
import {ApplicationIdPComponent} from './domain/applications/application/idp/idp.component';
import {ApplicationDesignComponent} from './domain/applications/application/design/design.component';
import {ApplicationFormsComponent} from './domain/applications/application/design/forms/forms.component';
import {ApplicationFormComponent} from './domain/applications/application/design/forms/form/form.component';
import {ApplicationEmailsComponent} from './domain/applications/application/design/emails/emails.component';
import {ApplicationEmailComponent} from './domain/applications/application/design/emails/email/email.component';
import {ApplicationAdvancedComponent} from './domain/applications/application/advanced/advanced.component';
import {ApplicationGeneralComponent} from './domain/applications/application/advanced/general/general.component';
import {ApplicationAccountSettingsComponent} from './domain/applications/application/advanced/account/account.component';
import {ApplicationOAuth2Component} from './domain/applications/application/advanced/oauth2/oauth2.component';
import {ApplicationCertificatesComponent} from './domain/applications/application/advanced/certificates/certificates.component';
import {ApplicationMetadataComponent} from './domain/applications/application/advanced/metadata/metadata.component';
import {ApplicationMembershipsComponent} from './domain/applications/application/advanced/memberships/memberships.component';
import {ApplicationFactorsComponent} from './domain/applications/application/advanced/factors/factors.component';
import {ApplicationFlowsComponent} from './domain/applications/application/design/flows/flows.component';
import {ManagementRolesComponent} from './settings/management/roles/roles.component';
import {ManagementRoleComponent} from './settings/management/roles/role/role.component';
import {MembershipsResolver} from './resolvers/memberships.resolver';
import {SettingsResolver} from './resolvers/settings.resolver';
import {AuthGuard} from './guards/auth-guard.service';
import {HomeComponent} from './home/home.component';
import {DomainSettingsFactorsComponent} from './domain/settings/factors/factors.component';
import {FactorsResolver} from './resolvers/factors.resolver';
import {FactorCreationComponent} from './domain/settings/factors/creation/factor-creation.component';
import {FactorComponent} from './domain/settings/factors/factor/factor.component';
import {FactorResolver} from './resolvers/factor.resolver';
import {EnrolledFactorsResolver} from './resolvers/enrolled-factors.resolver';
import {NotFoundComponent} from './not-found/not-found.component';
import {EntrypointsComponent} from './settings/management/entrypoints/entrypoints.component';
import {EntrypointCreationComponent} from './settings/management/entrypoints/creation/entrypoint-creation.component';
import {EntrypointComponent} from './settings/management/entrypoints/entrypoint/entrypoint.component';
import {EntrypointResolver} from './resolvers/entrypoint.resolver';
import {EntrypointsResolver} from './resolvers/entrypoints.resolver';
import {UmaComponent} from './domain/settings/uma/uma.component';
import {ApplicationResourcesComponent} from './domain/applications/application/advanced/resources/resources.component';
import {ApplicationResourcesResolver} from './resolvers/application-resources.resolver';
import {ApplicationResourceComponent} from './domain/applications/application/advanced/resources/resource/resource.component';
import {ApplicationResourceResolver} from './resolvers/application-resource.resolver';
import {ApplicationResourcePolicyComponent} from './domain/applications/application/advanced/resources/resource/policies/policy/policy.component';
import {ApplicationResourcePolicyResolver} from './resolvers/application-resource-policy.resolver';
import {ApplicationFlowsResolver} from './resolvers/application-flows.resolver';
import {DomainSettingsEntrypointsComponent} from './domain/settings/entrypoints/entrypoints.component';
import {DomainSettingsWebAuthnComponent} from './domain/settings/webauthn/webauthn.component';
import {ApplicationLoginSettingsComponent} from "./domain/applications/application/advanced/login/login.component";
import {IdentitiesResolver} from './resolvers/identities.resolver';
import {PluginPoliciesResolver} from './resolvers/plugin-policies.resolver';
import {PlatformFlowSchemaResolver} from './resolvers/platform-flow-schema.resolver';
import {ApplicationAnalyticsComponent} from "./domain/applications/application/analytics/analytics.component";
import { UserHistoryComponent } from './domain/settings/users/user/history/history.component';
import {EnvironmentResolver} from "./resolvers/environment-resolver.service";
import {DummyComponent} from "./components/dummy/dummy.component";
import {CockpitComponent} from "./settings/cockpit/cockpit.component";
import {InstallationResolver} from "./resolvers/installation.resolver";
import {EnvironmentComponent} from "./environment/environment.component";

let applyOnLabel = (label) => label.toLowerCase().replace(/_/g, ' ');

export const routes: Routes = [
  {
    path: 'settings',
    component: SettingsComponent,
    data: {
      perms: {
        only: ['organization_settings_read']
      }
    },
    children: [
      {
        path: '',
        component: ManagementComponent,
        data: {
          menu: {
            level: 'top',
            label: 'Settings',
            icon: 'settings'
          },
          perms: {
            only: ['organization_settings_read']
          }
        },
        children: [
          {path: '', redirectTo: 'general', pathMatch: 'full'},
          {
            path: 'general',
            component: ManagementGeneralComponent,
            canActivate: [AuthGuard],
            resolve: {
              settings: SettingsResolver,
            },
            data: {
              menu: {
                label: 'General',
                section: 'Settings'
              },
              perms: {
                only: ['organization_settings_read']
              }
            }
          },
          {
            path: 'members',
            component: SettingsMembershipsComponent,
            canActivate: [AuthGuard],
            resolve: {
              members: MembershipsResolver
            },
            data: {
              menu: {
                label: 'Administrative roles',
                section: 'Settings'
              },
              perms: {
                only: ['organization_member_list']
              }
            }
          },
          {
            path: 'forms',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Forms',
                section: 'Design'
              },
              perms: {
                only: ['organization_form_list']
              }
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: DomainSettingsFormsComponent,
              },
              {
                path: 'form',
                component: DomainSettingsFormComponent,
                resolve: {
                  form: FormResolver
                },
                data: {
                  breadcrumb: {
                    label: 'form.template',
                    applyOnLabel: applyOnLabel
                  },
                  perms: {
                    only: ['organization_form_read']
                  }
                }
              }
            ]
          },
          {
            path: 'providers',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Providers',
                section: 'Identities'
              },
              perms: {
                only: ['organization_identity_provider_list']
              }
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: DomainSettingsProvidersComponent,
                resolve: {
                  providers: ProvidersResolver,
                  identities: IdentitiesResolver
                }
              },
              {
                path: 'new',
                component: ProviderCreationComponent,
                canActivate: [AuthGuard],
                resolve: {
                  identities: IdentitiesResolver
                },
                data: {
                  perms: {
                    only: ['organization_identity_provider_create']
                  }
                }
              },
              {
                path: ':providerId',
                component: ProviderComponent,
                canActivate: [AuthGuard],
                resolve: {
                  provider: ProviderResolver
                },
                data: {
                  breadcrumb: {
                    label: 'provider.name'
                  },
                  perms: {
                    only: ['organization_identity_provider_read']
                  }
                },
                children: [
                  {path: '', redirectTo: 'settings', pathMatch: 'full'},
                  {path: 'settings', component: ProviderSettingsComponent},
                  {path: 'mappers', component: ProviderMappersComponent},
                  {path: 'roles', component: ProviderRolesComponent, resolve: {roles: RolesResolver}}
                ]
              },
            ]
          },
          {
            path: 'audits',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Audit Log',
                section: 'Security'
              },
              perms: {
                only: ['organization_audit_list']
              }
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: AuditsComponent,
              },
              {
                path: ':auditId',
                component: AuditComponent,
                canActivate: [AuthGuard],
                resolve: {
                  audit: AuditResolver
                },
                data: {
                  breadcrumb: {
                    label: 'audit.id'
                  },
                  perms: {
                    only: ['organization_audit_read']
                  }
                }
              }
            ]
          },
          {
            path: 'users',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Users',
                section: 'User Management'
              },
              perms: {
                only: ['organization_user_list']
              }
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: UsersComponent,
                resolve: {
                  users: UsersResolver
                }
              },
              {
                path: ':userId',
                component: UserComponent,
                canActivate: [AuthGuard],
                resolve: {
                  user: UserResolver
                },
                data: {
                  breadcrumb: {
                    label: 'user.username'
                  },
                  perms: {
                    only: ['organization_user_read']
                  }
                },
                children: [
                  {path: '', redirectTo: 'profile', pathMatch: 'full'},
                  {path: 'profile', component: UserProfileComponent}
                ]
              },
            ]
          },
          {
            path: 'groups',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Groups',
                section: 'User Management'
              },
              perms: {
                only: ['organization_group_list']
              }
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: GroupsComponent,
                resolve: {
                  groups: GroupsResolver
                }
              },
              {
                path: 'new',
                component: GroupCreationComponent,
                canActivate: [AuthGuard],
                data: {
                  perms: {
                    only: ['organization_group_create']
                  }
                }
              },
              {
                path: ':groupId',
                component: GroupComponent,
                canActivate: [AuthGuard],
                resolve: {
                  group: GroupResolver
                },
                data: {
                  breadcrumb: {
                    label: 'group.name'
                  },
                  perms: {
                    only: ['organization_group_read']
                  }
                },
                children: [
                  {path: '', redirectTo: 'settings', pathMatch: 'full'},
                  {path: 'settings', component: GroupSettingsComponent},
                  {path: 'members', component: GroupMembersComponent, resolve: {members: GroupMembersResolver}}
                ]
              }
            ]
          },
          {
            path: 'roles',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Roles',
                section: 'User Management',
              },
              perms: {
                only: ['organization_role_list']
              }
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: ManagementRolesComponent,
                resolve: {
                  roles: RolesResolver
                },
              },
              {
                path: 'new',
                component: RoleCreationComponent,
                canActivate: [AuthGuard],
                data: {
                  perms: {
                    only: ['organization_role_create']
                  }
                }
              },
              {
                path: ':roleId',
                component: ManagementRoleComponent,
                canActivate: [AuthGuard],
                resolve: {
                  role: RoleResolver
                },
                data: {
                  breadcrumb: {
                    label: 'role.name'
                  },
                  perms: {
                    only: ['organization_role_read']
                  }
                }
              }
            ]
          },
          {
            path: 'tags',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Sharding tags',
                section: 'Deployment'
              },
              perms: {
                only: ['organization_tag_list']
              }
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: TagsComponent,
                resolve: {
                  tags: TagsResolver
                },
              },
              {
                path: 'new',
                component: TagCreationComponent,
                canActivate: [AuthGuard],
                data: {
                  perms: {
                    only: ['organization_tag_create']
                  }
                }
              },
              {
                path: ':tagId',
                component: TagComponent,
                canActivate: [AuthGuard],
                resolve: {
                  tag: TagResolver
                },
                data: {
                  breadcrumb: {
                    label: 'tag.name'
                  },
                  perms: {
                    only: ['organization_tag_read']
                  }
                }
              }
            ]
          },
          {
            path: 'entrypoints',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Entrypoints',
                section: 'Deployment'
              },
              perms: {
                only: ['organization_entrypoint_list']
              }
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: EntrypointsComponent,
                resolve: {
                  entrypoints: EntrypointsResolver
                },
              },
              {
                path: 'new',
                component: EntrypointCreationComponent,
                canActivate: [AuthGuard],
                data: {
                  perms: {
                    only: ['organization_entrypoint_create']
                  }
                }
              },
              {
                path: ':entrypointId',
                component: EntrypointComponent,
                canActivate: [AuthGuard],
                resolve: {
                  entrypoint: EntrypointResolver,
                  tags: TagsResolver
                },
                data: {
                  breadcrumb: {
                    label: 'entrypoint.name'
                  },
                  perms: {
                    only: ['organization_entrypoint_read']
                  }
                }
              }
            ]
          }
        ]
      },
      {
        path: 'cockpit',
        component: CockpitComponent,
        canActivate: [AuthGuard],
        resolve: {
          installation: InstallationResolver
        },
        data: {
          menu: {
            level: 'top',
            label: 'Cockpit',
            icon: 'explore',
            beta: true
          },
          perms: {
            only: ['installation_read']
          }
        }
      }
    ]
  },
  {
    path: 'environments',
    data: {
      breadcrumb: {
        disabled: true
      }
    },
    children: [
      {
        path: ':envHrid',
        canActivate: [AuthGuard],
        data: {
          breadcrumb: {
            disabled: true
          }
        },
        resolve: {
          environment: EnvironmentResolver,
        },
        children: [
          {
            path: '',
            pathMatch: 'full',
            component: EnvironmentComponent,
          },
          {
            path: 'domains',
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Domains',
                level: 'top',
                icon: 'developer_board',
                routerLinkActiveOptions: { exact: true },
                displayOptions: { exact: true }
              },
              breadcrumb: {
                label: 'domains'
              },
              perms: {
                only: ['domain_list']
              }
            },
            children: [
              {
                path: '',
                pathMatch: 'full',
                component: DomainsComponent,
                resolve: {
                  domains: DomainsResolver
                },
                canActivate: [AuthGuard],
                data: {
                  perms: {
                    only: ['domain_list']
                  }
                }
              },
              {
                path: 'new',
                component: DomainCreationComponent,
                resolve: {
                  environment: EnvironmentResolver
                },
                canActivate: [AuthGuard],
                data: {
                  perms: {
                    only: ['domain_create']
                  }
                },
              },
              {
                path: ':domainId',
                component: DomainComponent,
                resolve: {
                  domain: DomainResolver,
                  permissions: DomainPermissionsResolver
                },
                data: {
                  breadcrumb: {
                    label: "domain.name"
                  }
                },
                children: [
                  {
                    path: '',
                    pathMatch: 'full',
                    redirectTo: 'dashboard'
                  },
                  {
                    path: 'dashboard',
                    component: DomainDashboardComponent,
                    canActivate: [AuthGuard],
                    data: {
                      menu: {
                        label: 'Dashboard',
                        icon: 'bar_chart',
                        level: 'top'
                      },
                      breadcrumb: {
                        include: true,
                      },
                      perms: {
                        only: ['domain_analytics_read']
                      }
                    }
                  },
                  {
                    path: 'applications',
                    data: {
                      menu: {
                        label: 'Applications',
                        icon: 'devices',
                        level: 'top'
                      },
                      perms: {
                        only: ['application_list']
                      }
                    },
                    children: [
                      {
                        path: '',
                        pathMatch: 'full',
                        component: ApplicationsComponent,
                        resolve: {
                          applications: ApplicationsResolver
                        },
                      },
                      {
                        path: 'new',
                        component: ApplicationCreationComponent,
                        canActivate: [AuthGuard],
                        data: {
                          perms: {
                            only: ['application_create']
                          }
                        }
                      },
                      {
                        path: ':appId',
                        component: ApplicationComponent,
                        resolve: {
                          application: ApplicationResolver,
                          permissions: ApplicationPermissionsResolver
                        },
                        data: {
                          breadcrumb: {
                            label: 'application.name'
                          }
                        },
                        children: [
                          {
                            path: '', redirectTo: 'overview', pathMatch: 'full'
                          },
                          {
                            path: 'overview', component: ApplicationOverviewComponent,
                            resolve: {
                              entrypoint: DomainEntrypointResolver
                            }
                          },
                          {
                            path: 'endpoints', component: ApplicationEndpointsComponent,
                            resolve: {
                              entrypoint: DomainEntrypointResolver
                            }
                          },
                          {
                            path: 'idp',
                            component: ApplicationIdPComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              identities: IdentitiesResolver
                            },
                            data: {
                              perms: {
                                only: ['application_identity_provider_list']
                              }
                            }
                          },
                          {
                            path: 'design',
                            component: ApplicationDesignComponent,
                            children: [
                              {
                                path: 'forms',
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Forms',
                                    section: 'Design'
                                  },
                                  perms: {
                                    only: ['application_form_list', 'application_form_read']
                                  }
                                },
                                children: [
                                  {
                                    path: '',
                                    pathMatch: 'full',
                                    component: ApplicationFormsComponent,
                                  },
                                  {
                                    path: 'form',
                                    component: ApplicationFormComponent,
                                    canActivate: [AuthGuard],
                                    resolve: {form: FormResolver},
                                    data: {
                                      breadcrumb: {
                                        label: "form.template",
                                        applyOnLabel: applyOnLabel
                                      },
                                      perms: {
                                        only: ['application_form_read']
                                      }
                                    }
                                  }
                                ]
                              },
                              {
                                path: 'emails',
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Emails',
                                    section: 'Design'
                                  },
                                  perms: {
                                    only: ['application_email_template_list', 'application_email_template_read']
                                  }
                                },
                                children: [
                                  {
                                    path: '',
                                    pathMatch: 'full',
                                    component: ApplicationEmailsComponent,
                                    canActivate: [AuthGuard],
                                  },
                                  {
                                    path: 'email',
                                    component: ApplicationEmailComponent,
                                    canActivate: [AuthGuard],
                                    resolve: {
                                      email: EmailResolver
                                    },
                                    data: {
                                      breadcrumb: {
                                        label: "email.template",
                                        applyOnLabel: applyOnLabel
                                      },
                                      perms: {
                                        only: ['application_email_template_read']
                                      }
                                    }
                                  }
                                ]
                              },
                              { path: 'flows',
                                component: ApplicationFlowsComponent,
                                canActivate: [AuthGuard],
                                resolve: {
                                  flows: ApplicationFlowsResolver,
                                  policies: PluginPoliciesResolver,
                                  flowSettingsForm: PlatformFlowSchemaResolver
                                },
                                data: {
                                  menu: {
                                    label: 'Flows',
                                    section: 'Design'
                                  },
                                  perms: {
                                    only: ['application_flow_list', 'application_flow_read']
                                  }
                                }
                              },
                            ]
                          },
                          {
                            path: 'analytics',
                            component: ApplicationAnalyticsComponent,
                            canActivate: [AuthGuard],
                          },
                          {
                            path: 'settings',
                            component: ApplicationAdvancedComponent,
                            children: [
                              {
                                path: 'general',
                                component: ApplicationGeneralComponent,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'General',
                                    section: 'Settings'
                                  },
                                  perms: {
                                    only: ['application_settings_read']
                                  }
                                }
                              },
                              {
                                path: 'metadata',
                                component: ApplicationMetadataComponent,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Application metadata',
                                    section: 'Settings'
                                  },
                                  perms: {
                                    only: ['application_settings_read']
                                  }
                                }
                              },
                              {
                                path: 'oauth2',
                                component: ApplicationOAuth2Component,
                                canActivate: [AuthGuard],
                                resolve: {domainGrantTypes: ExtensionGrantsResolver, scopes: ScopesResolver},
                                data: {
                                  menu: {
                                    label: 'OAuth 2.0 / OIDC',
                                    section: 'Settings'
                                  },
                                  perms: {
                                    only: ['application_openid_read']
                                  }
                                }
                              },
                              {
                                path: 'login',
                                component: ApplicationLoginSettingsComponent,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Login',
                                    section: 'Settings'
                                  },
                                  perms: {
                                    only: ['application_settings_read']
                                  },
                                  types: {
                                    only: ['WEB', 'NATIVE', 'BROWSER', 'RESOURCE_SERVER']
                                  }
                                }
                              },
                              {
                                path: 'members',
                                component: ApplicationMembershipsComponent,
                                canActivate: [AuthGuard],
                                resolve: {
                                  members: MembershipsResolver
                                },
                                data: {
                                  menu: {
                                    label: 'Administrative roles',
                                    section: 'Settings'
                                  },
                                  perms: {
                                    only: ['application_member_list']
                                  }
                                }
                              },
                              {
                                path: 'factors',
                                component: ApplicationFactorsComponent,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Multifactor Auth',
                                    section: 'Security'
                                  },
                                  perms: {
                                    only: ['application_factor_list']
                                  },
                                  types: {
                                    only: ['WEB', 'NATIVE', 'BROWSER', 'RESOURCE_SERVER']
                                  }
                                }
                              },
                              {
                                path: 'account',
                                component: ApplicationAccountSettingsComponent,
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'User Accounts',
                                    section: 'Security'
                                  },
                                  perms: {
                                    only: ['application_settings_read']
                                  },
                                  types: {
                                    only: ['WEB', 'NATIVE', 'BROWSER', 'RESOURCE_SERVER']
                                  }
                                }
                              },
                              {
                                path: 'certificates',
                                component: ApplicationCertificatesComponent,
                                canActivate: [AuthGuard],
                                resolve: {certificates: CertificatesResolver},
                                data: {
                                  menu: {
                                    label: 'Certificates',
                                    section: 'Security'
                                  },
                                  perms: {
                                    only: ['application_certificate_list']
                                  }
                                }
                              },
                              {
                                path: 'resources',
                                canActivate: [AuthGuard],
                                data: {
                                  menu: {
                                    label: 'Resources',
                                    section: 'Security'
                                  },
                                  perms: {
                                    only: ['application_resource_list']
                                  },
                                  types: {
                                    only: ['RESOURCE_SERVER']
                                  }
                                },
                                children: [
                                  {
                                    path: '',
                                    pathMatch: 'full',
                                    component: ApplicationResourcesComponent,
                                    resolve: {
                                      resources: ApplicationResourcesResolver
                                    }
                                  },
                                  {
                                    path: ':resourceId',
                                    canActivate: [AuthGuard],
                                    resolve: {
                                      resource: ApplicationResourceResolver
                                    },
                                    data: {
                                      breadcrumb: {
                                        label: 'resource.name'
                                      },
                                      perms: {
                                        only: ['application_resource_read']
                                      }
                                    },
                                    children: [
                                      {
                                        path: '',
                                        pathMatch: 'full',
                                        component: ApplicationResourceComponent,
                                      },
                                      {
                                        path: 'policies/:policyId',
                                        component: ApplicationResourcePolicyComponent,
                                        canActivate: [AuthGuard],
                                        resolve: {
                                          policy: ApplicationResourcePolicyResolver
                                        },
                                        data: {
                                          breadcrumb: {
                                            label: 'policy.name'
                                          },
                                          perms: {
                                            only: ['application_resource_read']
                                          }
                                        }
                                      }

                                    ]
                                  },
                                ]
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  },
                  {
                    path: 'settings',
                    component: DomainSettingsComponent,
                    canActivate: [AuthGuard],
                    data: {
                      menu: {
                        label: 'Settings',
                        icon: 'settings',
                        level: 'top'
                      },
                      perms: {
                        only: ['domain_settings_read']
                      }
                    },
                    children: [
                      {path: '', redirectTo: 'general', pathMatch: 'full'},
                      {
                        path: 'general',
                        component: DomainSettingsGeneralComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          tags: TagsResolver
                        },
                        data: {
                          menu: {
                            label: 'General',
                            section: 'Settings'
                          },
                          perms: {
                            only: ['domain_settings_read']
                          }
                        }
                      },
                      {
                        path: 'entrypoints',
                        component: DomainSettingsEntrypointsComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          entrypoint: DomainEntrypointResolver,
                          environment: EnvironmentResolver
                        },
                        data: {
                          menu: {
                            label: 'Entrypoints',
                            section: 'Settings'
                          },
                          perms: {
                            only: ['domain_settings_read']
                          }
                        }
                      },
                      {
                        path: 'login',
                        component: DomainSettingsLoginComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Login',
                            section: 'Settings'
                          },
                          perms: {
                            only: ['domain_settings_read']
                          }
                        }
                      },
                      {
                        path: 'members',
                        component: DomainSettingsMembershipsComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          members: MembershipsResolver
                        },
                        data: {
                          menu: {
                            label: 'Administrative roles',
                            section: 'Settings'
                          },
                          perms: {
                            only: ['domain_member_list']
                          }
                        }
                      },
                      {
                        path: 'forms',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Forms',
                            section: 'Design'
                          },
                          perms: {
                            only: ['domain_form_list', 'domain_form_read']
                          }
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsFormsComponent,
                          },
                          {
                            path: 'form',
                            component: DomainSettingsFormComponent,
                            resolve: {
                              form: FormResolver
                            },
                            data: {
                              breadcrumb: {
                                label: "form.template",
                                applyOnLabel: applyOnLabel
                              }
                            }
                          },
                        ]
                      },
                      {
                        path: 'emails',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Emails',
                            section: 'Design'
                          },
                          perms: {
                            only: ['domain_email_template_list', 'domain_email_template_read']
                          }
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsEmailsComponent,
                          },
                          {
                            path: 'email',
                            component: DomainSettingsEmailComponent,
                            resolve: {
                              email: EmailResolver
                            },
                            data: {
                              breadcrumb: {
                                label: "email.template",
                                applyOnLabel: applyOnLabel
                              }
                            }
                          }
                        ]
                      },
                      { path: 'flows',
                        component: DomainSettingsFlowsComponent,
                        canActivate: [AuthGuard],
                        resolve: {
                          flows: DomainFlowsResolver,
                          policies: PluginPoliciesResolver,
                          flowSettingsForm: PlatformFlowSchemaResolver
                        },
                        data: {
                          menu: {
                            label: 'Flows',
                            section: 'Design'
                          },
                          perms: {
                            only: ['domain_flow_list']
                          }
                        }
                      },
                      {
                        path: 'providers',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Providers',
                            section: 'Identities'
                          },
                          perms: {
                            only: ['domain_identity_provider_list']
                          }
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsProvidersComponent,
                            resolve: {
                              providers: ProvidersResolver,
                              identities: IdentitiesResolver
                            }
                          },
                          {
                            path: 'new',
                            component: ProviderCreationComponent,
                            resolve: {
                              certificates: CertificatesResolver,
                              identities: IdentitiesResolver
                            }
                          },
                          {
                            path: ':providerId',
                            component: ProviderComponent,
                            resolve: {
                              provider: ProviderResolver
                            },
                            data: {
                              breadcrumb: {
                                label: 'provider.name'
                              }
                            },
                            children: [
                              {
                                path: '', redirectTo: 'settings', pathMatch: 'full'
                              },
                              {
                                path: 'settings',
                                component: ProviderSettingsComponent,
                                data: {
                                  breadcrumb: {
                                    disabled: true
                                  }
                                },
                                resolve: {
                                  certificates: CertificatesResolver
                                }
                              },
                              {
                                path: 'mappers',
                                component: ProviderMappersComponent,
                                data: {
                                  breadcrumb: {
                                    label: 'user mappers'
                                  }
                                }
                              },
                              {
                                path: 'roles', component: ProviderRolesComponent,
                                resolve: {
                                  roles: RolesResolver
                                },
                                data: {
                                  breadcrumb: {
                                    label: 'role mappers'
                                  }
                                }
                              }
                            ]
                          }
                        ]
                      },
                      {
                        path: 'webauthn',
                        component: DomainSettingsWebAuthnComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'WebAuthn',
                            section: 'Security'
                          },
                          perms: {
                            only: ['domain_settings_read']
                          }
                        }
                      },
                      {
                        path: 'factors',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Multifactor Auth',
                            section: 'Security'
                          },
                          perms: {
                            only: ['domain_factor_list']
                          }
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsFactorsComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              factors: FactorsResolver,
                            },
                            data: {
                              perms: {
                                only: ['domain_factor_list']
                              }
                            }
                          },
                          {
                            path: 'new',
                            component: FactorCreationComponent,
                            canActivate: [AuthGuard],
                            data: {
                              perms: {
                                only: ['domain_factor_create']
                              }
                            }
                          },
                          {
                            path: ':factorId',
                            component: FactorComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              factor: FactorResolver,
                            },
                            data: {
                              breadcrumb: {
                                label: "factor.name",
                              },
                              perms: {
                                only: ['domain_factor_read']
                              }
                            }
                          }
                        ]
                      },
                      {
                        path: 'audits',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Audit Log',
                            section: 'Security'
                          },
                          perms: {
                            only: ['domain_audit_list']
                          }
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: AuditsComponent,
                            canActivate: [AuthGuard],
                            data: {
                              perms: {
                                only: ['domain_audit_list']
                              }
                            }
                          },
                          {
                            path: 'settings',
                            children: [
                              {
                                path: '',
                                pathMatch: 'full',
                                component: AuditsSettingsComponent,
                                canActivate: [AuthGuard],
                                resolve: {
                                  reporters: ReportersResolver
                                },
                                data: {
                                  perms: {
                                    only: ['domain_reporter_list']
                                  }
                                }
                              },
                              {
                                path: ':reporterId',
                                component: ReporterComponent,
                                resolve: {
                                  reporter: ReporterResolver
                                },
                                data: {
                                  breadcrumb: {
                                    label: 'reporter.name'
                                  },
                                  perms: {
                                    only: ['domain_reporter_read']
                                  }
                                }
                              }
                            ]
                          },
                          {
                            path: ':auditId',
                            component: AuditComponent,
                            resolve: {
                              audit: AuditResolver
                            },
                            data: {
                              breadcrumb: {
                                label: "audit.id"
                              },
                              perms: {
                                only: ['domain_audit_read']
                              }
                            }
                          }
                        ]
                      },
                      {
                        path: 'account',
                        component: DomainSettingsAccountComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'User Accounts',
                            section: 'Security'
                          },
                          perms: {
                            only: ['domain_settings_read']
                          }
                        }
                      },
                      {
                        path: 'certificates',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Certificates',
                            section: 'Security'
                          },
                          perms: {
                            only: ['domain_certificate_list']
                          }
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsCertificatesComponent,
                            resolve: {
                              certificates: CertificatesResolver
                            }
                          },
                          {
                            path: 'new',
                            component: CertificateCreationComponent
                          },
                          {
                            path: ':certificateId',
                            component: CertificateComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              certificate: CertificateResolver
                            },
                            data: {
                              breadcrumb: {
                                label: 'certificate.name'
                              },
                              perms: {
                                only: ['domain_certificate_read']
                              }
                            }
                          }
                        ]
                      },
                      {
                        path: 'users',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Users',
                            section: 'User Management'
                          },
                          perms: {
                            only: ['domain_user_list']
                          }
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: UsersComponent,
                            resolve: {
                              users: UsersResolver
                            }
                          },
                          {
                            path: 'new',
                            component: UserCreationComponent,
                            canActivate: [AuthGuard],
                            data: {
                              perms: {
                                only: ['domain_user_create']
                              }
                            }
                          },
                          {
                            path: ':userId',
                            component: UserComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              user: UserResolver
                            },
                            data: {
                              breadcrumb: {
                                label: 'user.username'
                              },
                              perms: {
                                only: ['domain_user_read']
                              }
                            },
                            children: [
                              {
                                path: '',
                                redirectTo: 'profile',
                                pathMatch: 'full'
                              },
                              {
                                path: 'profile',
                                component: UserProfileComponent
                              },
                              {
                                path: 'applications',
                                children: [
                                  {
                                    path: '',
                                    pathMatch: 'full',
                                    component: UserApplicationsComponent,
                                    resolve: {
                                      consents: ConsentsResolver
                                    }
                                  },
                                  {
                                    path: ':appId',
                                    component: UserApplicationComponent,
                                    resolve: {
                                      application: ApplicationResolver,
                                      consents: ConsentsResolver
                                    },
                                    data: {
                                      breadcrumb: {
                                        label: 'application.name'
                                      }
                                    }
                                  }
                                ]
                              },
                              {
                                path: 'factors',
                                component: UserFactorsComponent,
                                resolve: {
                                  factors: EnrolledFactorsResolver
                                }
                              },
                              {
                                path: 'credentials',
                                children: [
                                  {
                                    path: '',
                                    pathMatch: 'full',
                                    component: UserCredentialsComponent,
                                    resolve: {credentials: UserCredentialsResolver}
                                  },
                                  {
                                    path: ':credentialId',
                                    component: UserCredentialComponent,
                                    resolve: {credential: UserCredentialResolver},
                                    data: {
                                      breadcrumb: {
                                        label: 'detail'
                                      }
                                    }
                                  }
                                ]
                              },
                              { path: 'history', component: UserHistoryComponent },
                              {path: 'roles', component: UserRolesComponent, resolve: {roles: UserRolesResolver}}
                            ]
                          },
                        ]
                      },
                      {
                        path: 'groups',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Groups',
                            section: 'User Management'
                          },
                          perms: {
                            only: ['domain_group_list']
                          }
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: GroupsComponent,
                            resolve: {
                              groups: GroupsResolver
                            }
                          },
                          {
                            path: 'new',
                            component: GroupCreationComponent,
                            canActivate: [AuthGuard],
                            data: {
                              perms: {
                                only: ['domain_group_create']
                              }
                            }
                          },
                          {
                            path: ':groupId',
                            component: GroupComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              group: GroupResolver
                            },
                            data: {
                              breadcrumb: {
                                label: 'group.name'
                              },
                              perms: {
                                only: ['domain_group_read']
                              }
                            },
                            children: [
                              {path: '', redirectTo: 'settings', pathMatch: 'full'},
                              {path: 'settings', component: GroupSettingsComponent},
                              {
                                path: 'members',
                                component: GroupMembersComponent,
                                resolve: {members: GroupMembersResolver}
                              },
                              {path: 'roles', component: GroupRolesComponent, resolve: {roles: GroupRolesResolver}}
                            ]
                          }
                        ]
                      },
                      {
                        path: 'roles',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Roles',
                            section: 'User Management',
                          },
                          perms: {
                            only: ['domain_role_list']
                          }
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsRolesComponent,
                            resolve: {
                              roles: RolesResolver
                            }
                          },
                          {
                            path: 'new',
                            component: RoleCreationComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              scopes: ScopesResolver
                            },
                            data: {
                              perms: {
                                only: ['domain_role_create']
                              }
                            }
                          },
                          {
                            path: ':roleId',
                            component: RoleComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              role: RoleResolver,
                              scopes: ScopesResolver
                            },
                            data: {
                              breadcrumb: {
                                label: 'role.name'
                              },
                              perms: {
                                only: ['domain_role_read']
                              }
                            }
                          }
                        ]
                      },
                      {
                        path: 'scim',
                        component: ScimComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'SCIM',
                            section: 'User Management'
                          },
                          perms: {
                            only: ['domain_scim_read']
                          }
                        }
                      },
                      {
                        path: 'scopes',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Scopes',
                            section: 'OAuth 2.0'
                          },
                          perms: {
                            only: ['domain_scope_list']
                          }
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsScopesComponent,
                            resolve: {
                              scopes: ScopesResolver
                            }
                          },
                          {
                            path: 'new',
                            component: ScopeCreationComponent,
                            canActivate: [AuthGuard],
                            data: {
                              perms: {
                                only: ['domain_scope_create']
                              }
                            }
                          },
                          {
                            path: ':scopeId',
                            component: ScopeComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              scope: ScopeResolver
                            },
                            data: {
                              breadcrumb: {
                                label: 'scope.name'
                              },
                              perms: {
                                only: ['domain_scope_read']
                              }
                            }
                          }
                        ]
                      },
                      {
                        path: 'extensionGrants',
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Extension Grants',
                            section: 'OAuth 2.0'
                          },
                          perms: {
                            only: ['domain_extension_grant_list']
                          }
                        },
                        children: [
                          {
                            path: '',
                            pathMatch: 'full',
                            component: DomainSettingsExtensionGrantsComponent,
                            resolve: {
                              extensionGrants: ExtensionGrantsResolver
                            }
                          },
                          {
                            path: 'new',
                            component: ExtensionGrantCreationComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              identityProviders: ProvidersResolver
                            },
                            data: {
                              perms: {
                                only: ['domain_extension_grant_create']
                              }
                            }
                          },
                          {
                            path: ':extensionGrantId',
                            component: ExtensionGrantComponent,
                            canActivate: [AuthGuard],
                            resolve: {
                              extensionGrant: ExtensionGrantResolver,
                              identityProviders: ProvidersResolver
                            },
                            data: {
                              breadcrumb: {
                                label: 'extensionGrant.name'
                              },
                              perms: {
                                only: ['domain_extension_grant_read']
                              }
                            }
                          },
                        ]
                      },
                      {
                        path: 'dcr',
                        component: DomainSettingsOpenidClientRegistrationComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'Client Registration',
                            section: 'Openid'
                          },
                          perms: {
                            only: ['domain_openid_read']
                          }
                        },
                        children: [
                          {path: '', redirectTo: 'settings', pathMatch: 'full'},
                          {
                            path: 'settings',
                            component: ClientRegistrationSettingsComponent
                          },
                          {
                            path: 'default-scope',
                            component: ClientRegistrationDefaultScopeComponent,
                            resolve: {scopes: ScopesResolver}
                          },
                          {
                            path: 'allowed-scope',
                            component: ClientRegistrationAllowedScopeComponent,
                            resolve: {scopes: ScopesResolver}
                          },
                          {
                            path: 'templates',
                            component: ClientRegistrationTemplatesComponent,
                            resolve: {apps: ApplicationsResolver}
                          }
                        ]
                      },
                      {
                        path: 'uma',
                        component: UmaComponent,
                        canActivate: [AuthGuard],
                        data: {
                          menu: {
                            label: 'UMA',
                            section: 'User-Managed Access'
                          },
                          perms: {
                            only: ['domain_uma_read']
                          }
                        }
                      }
                    ]
                  }
                ]
              }
            ]
          }
        ]
      }
    ]
  },
  {path: 'login', component: LoginComponent},
  {path: 'login/callback', component: LoginCallbackComponent},
  {path: 'logout', component: LogoutComponent},
  {path: 'logout/callback', component: LogoutCallbackComponent},
  {path: 'dummy', component: DummyComponent},
  {path: '404', component: NotFoundComponent},
  {path: '', component: HomeComponent},
  {path: '**', redirectTo: '404', pathMatch: 'full'}
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {
    paramsInheritanceStrategy: 'always',
    scrollPositionRestoration: 'top',
    relativeLinkResolution: 'corrected',
    onSameUrlNavigation: 'reload',
  })],
  exports: [RouterModule]
})
export class AppRoutingModule {
}

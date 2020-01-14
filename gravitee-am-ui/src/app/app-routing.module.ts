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
import {LoginComponent} from "./login/login.component";
import {LoginCallbackComponent} from "./login/callback/callback.component";
import {DomainsComponent} from "./settings/domains/domains.component";
import {DomainComponent} from "./domain/domain.component";
import {DomainDashboardComponent} from "./domain/dashboard/dashboard.component";
import {DomainSettingsComponent} from "./domain/settings/settings.component";
import {DomainSettingsGeneralComponent} from "./domain/settings/general/general.component";
import {DomainSettingsOpenidClientRegistrationComponent} from "./domain/settings/openid/client-registration/client-registration.component";
import {ClientRegistrationSettingsComponent} from "./domain/settings/openid/client-registration/settings/settings.component";
import {ClientRegistrationDefaultScopeComponent} from "./domain/settings/openid/client-registration/default-scope/default-scope.component";
import {ClientRegistrationAllowedScopeComponent} from "./domain/settings/openid/client-registration/allowed-scope/allowed-scope.component";
import {ClientRegistrationTemplatesComponent} from "./domain/settings/openid/client-registration/templates/templates.component";
import {DomainSettingsCertificatesComponent} from "./domain/settings/certificates/certificates.component";
import {DomainSettingsProvidersComponent} from "./domain/settings/providers/providers.component";
import {DomainSettingsRolesComponent} from "./domain/settings/roles/roles.component";
import {DomainSettingsScopesComponent} from "./domain/settings/scopes/scopes.component";
import {DomainSettingsFormsComponent} from "./domain/settings/forms/forms.component";
import {DomainSettingsFormComponent} from "./domain/settings/forms/form/form.component";
import {DomainSettingsLoginComponent} from "./domain/settings/login/login.component";
import {DomainSettingsEmailsComponent} from "./domain/settings/emails/emails.component";
import {DomainSettingsEmailComponent} from "./domain/settings/emails/email/email.component";
import {DomainSettingsExtensionGrantsComponent} from "./domain/settings/extension-grants/extension-grants.component";
import {DomainSettingsAccountComponent} from "./domain/settings/account/account.component";
import {DomainSettingsPoliciesComponent} from "./domain/settings/policies/policies.component";
import {DomainSettingsMembershipsComponent} from "./domain/settings/memberships/memberships.component";
import {DomainCreationComponent} from "./domain/creation/domain-creation.component";
import {ProviderCreationComponent} from "./domain/settings/providers/creation/provider-creation.component";
import {ProviderComponent} from "./domain/settings/providers/provider/provider.component";
import {LogoutCallbackComponent} from "./logout/callback/callback.component";
import {LogoutComponent} from "./logout/logout.component";
import {DomainsResolver} from "./resolvers/domains.resolver";
import {DomainResolver} from "./resolvers/domain.resolver";
import {DomainPermissionsResolver} from "./resolvers/domain-permissions.resolver";
import {ProvidersResolver} from "./resolvers/providers.resolver";
import {ProviderResolver} from "./resolvers/provider.resolver";
import {ProviderRolesComponent} from "./domain/settings/providers/provider/roles/roles.component";
import {ProviderSettingsComponent} from "./domain/settings/providers/provider/settings/settings.component";
import {ProviderMappersComponent} from "./domain/settings/providers/provider/mappers/mappers.component";
import {CertificatesResolver} from "./resolvers/certificates.resolver";
import {CertificateCreationComponent} from "./domain/settings/certificates/creation/certificate-creation.component";
import {CertificateComponent} from "./domain/settings/certificates/certificate/certificate.component";
import {CertificateResolver} from "./resolvers/certificate.resolver";
import {RolesResolver} from "./resolvers/roles.resolver";
import {RoleCreationComponent} from "./domain/settings/roles/creation/role-creation.component";
import {RoleComponent} from "./domain/settings/roles/role/role.component";
import {RoleResolver} from "./resolvers/role.resolver";
import {ScopeResolver} from "./resolvers/scope.resolver";
import {ScopesResolver} from "./resolvers/scopes.resolver";
import {ScopeCreationComponent} from "./domain/settings/scopes/creation/scope-creation.component";
import {ScopeComponent} from './domain/settings/scopes/scope/scope.component';
import {SettingsComponent} from "./settings/settings.component";
import {DummyComponent} from "./components/dummy/dummy.component";
import {UsersComponent} from "./domain/settings/users/users.component";
import {UsersResolver} from "./resolvers/users.resolver";
import {UserComponent} from "./domain/settings/users/user/user.component";
import {UserResolver} from "./resolvers/user.resolver";
import {UserCreationComponent} from "./domain/settings/users/creation/user-creation.component";
import {UserProfileComponent} from "./domain/settings/users/user/profile/profile.component";
import {UserApplicationsComponent} from "./domain/settings/users/user/applications/applications.component";
import {UserApplicationComponent} from "./domain/settings/users/user/applications/application/application.component";
import {UserRolesComponent} from "./domain/settings/users/user/roles/roles.component";
import {UserRolesResolver} from "./resolvers/user-roles.resolver";
import {ExtensionGrantCreationComponent} from "./domain/settings/extension-grants/creation/extension-grant-creation.component";
import {ExtensionGrantComponent} from "./domain/settings/extension-grants/extension-grant/extension-grant.component";
import {ExtensionGrantsResolver} from "./resolvers/extension-grants.resolver";
import {ExtensionGrantResolver} from "./resolvers/extension-grant.resolver";
import {ManagementComponent} from "./settings/management/management.component";
import {ManagementGeneralComponent} from "./settings/management/general/general.component";
import {FormResolver} from "./resolvers/form.resolver";
import {GroupsResolver} from "./resolvers/groups.resolver";
import {GroupsComponent} from "./domain/settings/groups/groups.component";
import {GroupCreationComponent} from "./domain/settings/groups/creation/group-creation.component";
import {GroupResolver} from "./resolvers/group.resolver";
import {GroupComponent} from "./domain/settings/groups/group/group.component";
import {GroupSettingsComponent} from "./domain/settings/groups/group/settings/settings.component";
import {GroupMembersComponent} from "./domain/settings/groups/group/members/members.component";
import {GroupRolesComponent} from "./domain/settings/groups/group/roles/roles.component";
import {GroupRolesResolver} from "./resolvers/group-roles.resolver";
import {ScimComponent} from "./domain/settings/scim/scim.component";
import {EmailResolver} from "./resolvers/email.resolver";
import {ConsentsResolver} from "./resolvers/consents.resolver";
import {AuditResolver} from "./resolvers/audit.resolver";
import {AuditsComponent} from "./domain/settings/audits/audits.component";
import {AuditComponent} from "./domain/settings/audits/audit/audit.component";
import {AuditsSettingsComponent} from "./domain/settings/audits/settings/settings.component";
import {ReportersResolver} from "./resolvers/reporters.resolver";
import {ReporterResolver} from "./resolvers/reporter.resolver";
import {ReporterComponent} from "./domain/settings/audits/settings/reporter/reporter.component";
import {TagsResolver} from "./resolvers/tags.resolver";
import {TagsComponent} from "./settings/management/tags/tags.component";
import {TagCreationComponent} from "./settings/management/tags/creation/tag-creation.component";
import {TagComponent} from "./settings/management/tags/tag/tag.component";
import {TagResolver} from "./resolvers/tag.resolver";
import {PoliciesResolver} from "./resolvers/policies.resolver";
import {GroupMembersResolver} from "./resolvers/group-members.resolver";
import {ApplicationsComponent} from "./domain/applications/applications.component";
import {ApplicationsResolver} from "./resolvers/applications.resolver";
import {ApplicationCreationComponent} from "./domain/applications/creation/application-creation.component";
import {ApplicationComponent} from "./domain/applications/application/application.component";
import {ApplicationResolver} from "./resolvers/application.resolver";
import {ApplicationPermissionsResolver} from "./resolvers/application-permissions.resolver";
import {ApplicationGeneralComponent} from "./domain/applications/application/general/general.component";
import {ApplicationIdPComponent} from "./domain/applications/application/idp/idp.component";
import {ApplicationDesignComponent} from "./domain/applications/application/design/design.component";
import {ApplicationFormsComponent} from "./domain/applications/application/design/forms/forms.component";
import {ApplicationFormComponent} from "./domain/applications/application/design/forms/form/form.component";
import {ApplicationEmailsComponent} from "./domain/applications/application/design/emails/emails.component";
import {ApplicationEmailComponent} from "./domain/applications/application/design/emails/email/email.component";
import {ApplicationAdvancedComponent} from "./domain/applications/application/advanced/advanced.component";
import {ApplicationAccountSettingsComponent} from "./domain/applications/application/advanced/account/account.component";
import {ApplicationOAuth2Component} from "./domain/applications/application/advanced/oauth2/oauth2.component";
import {ApplicationCertificatesComponent} from "./domain/applications/application/advanced/certificates/certificates.component";
import {ApplicationMetadataComponent} from "./domain/applications/application/advanced/metadata/metadata.component";
import {ApplicationMembershipsComponent} from "./domain/applications/application/advanced/memberships/memberships.component";
import {ManagementRolesComponent} from "./settings/management/roles/roles.component";
import {ManagementRoleComponent} from "./settings/management/roles/role/role.component";
import {MembershipsResolver} from "./resolvers/memberships.resolver";
import {SettingsResolver} from "./resolvers/settings.resolver";
import {AuthGuard} from "./guards/auth-guard.service";
import {HomeComponent} from "./home/home.component";

const routes: Routes = [
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
        component: DomainCreationComponent,
        canActivate: [AuthGuard],
        data: {
          perms: {
            only: ['management_domain_create']
          }
        }
      },
      {
        path: 'management',
        component: ManagementComponent,
        data: {
          menu: {
            label: 'Settings',
            icon: 'settings'
          },
          perms: {
            only: ['management_settings_read']
          }
        },
        children: [
          { path: '', redirectTo: 'general', pathMatch: 'full' },
          { path: 'general',
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
                only: ['management_settings_read']
              }
            }
          },
          {
            path: 'login',
            component: DomainSettingsFormComponent,
            canActivate: [AuthGuard],
            resolve: {
              form: FormResolver
            },
            data: {
              menu: {
                label: 'Login Page',
                section: 'Settings'
              },
              perms: {
                only: ['management_form_read']
              }
            }
          },
          { path: 'providers',
            component: DomainSettingsProvidersComponent,
            canActivate: [AuthGuard],
            resolve: {
              providers: ProvidersResolver
            },
            data: {
              menu: {
                label: 'Providers',
                section: 'Identities'
              },
              perms: {
                only: ['management_identity_provider_read']
              }
            }
          },
          { path: 'providers/new',
            component: ProviderCreationComponent,
            canActivate: [AuthGuard],
            data: {
              perms: {
                only: ['management_identity_provider_create']
              }
            }
          },
          { path: 'providers/:providerId',
            component: ProviderComponent,
            canActivate: [AuthGuard],
            resolve: {
              provider: ProviderResolver
            },
            data: {
              perms: {
                only: ['management_identity_provider_read']
              }
            },
            children: [
              { path: '', redirectTo: 'settings', pathMatch: 'full' },
              { path: 'settings', component: ProviderSettingsComponent },
              { path: 'mappers', component: ProviderMappersComponent },
              { path: 'roles', component: ProviderRolesComponent, resolve: { roles: RolesResolver} }
            ]
          },
          { path: 'audits',
            component: AuditsComponent,
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Audit Log',
                section: 'Security'
              },
              perms: {
                only: ['management_audit_read']
              }
            }
          },
          { path: 'audits/settings',
            component: AuditsSettingsComponent,
            canActivate: [AuthGuard],
            resolve: {
              reporters: ReportersResolver
            },
            data: {
              perms: {
                only: ['management_audit_read']
              }
            }
          },
          { path: 'audits/settings/:reporterId',
            component: ReporterComponent,
            canActivate: [AuthGuard],
            resolve: {
              reporter: ReporterResolver
            },
            data: {
              perms: {
                only: ['management_audit_read']
              }
            }
          },
          { path: 'audits/:auditId',
            component: AuditComponent,
            canActivate: [AuthGuard],
            resolve: {
              audit: AuditResolver
            },
            data: {
              perms: {
                only: ['management_audit_read']
              }
            }
          },
          { path: 'users',
            component: UsersComponent,
            canActivate: [AuthGuard],
            resolve: {
              users: UsersResolver
            },
            data: {
              menu: {
                label: 'Users',
                section: 'User Management'
              },
              perms: {
                only: ['management_user_read']
              }
            }
          },
          {
            path: 'users/:userId',
            component: UserComponent,
            canActivate: [AuthGuard],
            resolve: {
              user: UserResolver
            },
            data: {
              perms: {
                only: ['management_user_read']
              }
            },
            children: [
              { path: '', redirectTo: 'profile', pathMatch: 'full' },
              { path: 'profile', component: UserProfileComponent },
              { path: 'applications', component: UserApplicationsComponent, resolve: {consents: ConsentsResolver}},
              { path: 'applications/:clientId', component: UserApplicationComponent, resolve: {consents: ConsentsResolver}},
              { path: 'roles', component: UserRolesComponent, resolve: { roles : UserRolesResolver}}
            ]
          },
          { path: 'groups',
            component: GroupsComponent,
            canActivate: [AuthGuard],
            resolve: {
              groups: GroupsResolver
            },
            data: {
              menu: {
                label: 'Groups',
                section: 'User Management'
              },
              perms: {
                only: ['management_group_read']
              }
            }
          },
          { path: 'groups/new',
            component: GroupCreationComponent,
            canActivate: [AuthGuard],
            data: {
              perms: {
                only: ['management_group_create']
              }
            }
          },
          {
            path: 'groups/:groupId',
            component: GroupComponent,
            canActivate: [AuthGuard],
            resolve: {
              group: GroupResolver
            },
            data: {
              perms: {
                only: ['management_group_read']
              }
            },
            children: [
              { path: '', redirectTo: 'settings', pathMatch: 'full' },
              { path: 'settings', component: GroupSettingsComponent },
              { path: 'members', component: GroupMembersComponent, resolve: { members : GroupMembersResolver}},
              { path: 'roles', component: GroupRolesComponent, resolve: { roles : GroupRolesResolver}}
            ]
          },
          { path: 'roles',
            component: ManagementRolesComponent,
            canActivate: [AuthGuard],
            resolve: {
              roles: RolesResolver
            },
            data: {
              menu: {
                label: 'Roles',
                section: 'User Management',
              },
              perms: {
                only: ['management_role_read']
              }
            }
          },
          { path: 'roles/new',
            component: RoleCreationComponent,
            canActivate: [AuthGuard],
            data: {
              perms: {
                only: ['management_role_create']
              }
            }
          },
          {
            path: 'roles/:roleId',
            component: ManagementRoleComponent,
            canActivate: [AuthGuard],
            resolve: {
              role: RoleResolver
            },
            data: {
              perms: {
                only: ['management_role_read']
              }
            }
          },
          { path: 'tags',
            component: TagsComponent,
            canActivate: [AuthGuard],
            resolve: {
              tags: TagsResolver
            },
            data: {
              menu: {
                label: 'Sharding tags',
                section: 'Deployment'
              },
              perms: {
                only: ['management_shard_read']
              }
            }
          },
          { path: 'tags/new',
            component: TagCreationComponent,
            canActivate: [AuthGuard],
            data: {
              perms: {
                only: ['management_shard_create']
              }
            }
          },
          {
            path: 'tags/:tagId',
            component: TagComponent,
            canActivate: [AuthGuard],
            resolve: {
              tag: TagResolver
            },
            data: {
              perms: {
                only: ['management_shard_read']
              }
            }
          }
        ]
      }
    ]
  },
  { path: 'domains/:domainId',
    component: DomainComponent,
    resolve: {
      domain: DomainResolver,
      permissions: DomainPermissionsResolver
    },
    data: {
      menu: {
        displayFirstLevel: false
      }
    },
    children: [
      { path: '', component: DomainComponent },
      { path: 'dashboard',
        component: DomainDashboardComponent,
        canActivate: [AuthGuard],
        data: {
          menu: {
            label: 'Dashboard',
            icon: 'bar_chart',
          },
          perms: {
            only: ['domain_analytics_read']
          }
        }
      },
      { path: 'applications',
        component: ApplicationsComponent,
        resolve: {
          applications: ApplicationsResolver
        },
        data: {
          menu: {
            label: 'Applications',
            icon: 'devices',
          }
        }
      },
      { path: 'applications/new',
        component: ApplicationCreationComponent,
        canActivate: [AuthGuard],
        resolve: {
          domains: DomainsResolver
        },
        data: {
          perms: {
            only: ['domain_application_create']
          }
        }
      },
      { path: 'applications/:appId',
        component: ApplicationComponent,
        resolve: {
          application: ApplicationResolver,
          permissions: ApplicationPermissionsResolver
        },
        children: [
          { path: '', redirectTo: 'general', pathMatch: 'full' },
          { path: 'general', component: ApplicationGeneralComponent, resolve: { domain: DomainResolver } },
          { path: 'idp',
            component: ApplicationIdPComponent,
            canActivate: [AuthGuard],
            data: {
              perms: {
                only: ['application_identity_provider_read']
              }
            }
          },
          { path: 'design',
            component: ApplicationDesignComponent,
            children: [
              { path: 'forms',
                component: ApplicationFormsComponent,
                canActivate: [AuthGuard],
                resolve: { domain: DomainResolver },
                data: {
                  menu: {
                    label: 'Forms',
                    section: 'Design'
                  },
                  perms: {
                    only: ['application_form_read']
                  }
                }
              },
              { path: 'forms/form',
                component: ApplicationFormComponent,
                canActivate: [AuthGuard],
                resolve: { form: FormResolver },
                data: {
                  perms: {
                    only: ['application_form_read']
                  }
                }
              },
              { path: 'emails',
                component: ApplicationEmailsComponent,
                canActivate: [AuthGuard],
                resolve: { domain: DomainResolver },
                data: {
                  menu: {
                    label: 'Emails',
                    section: 'Design'
                  },
                  perms: {
                    only: ['application_email_template_read']
                  }
                }
              },
              { path: 'emails/email',
                component: ApplicationEmailComponent,
                canActivate: [AuthGuard],
                resolve: { email: EmailResolver},
                data: {
                  perms: {
                    only: ['application_email_template_read']
                  }
                }
              }
            ]
          },
          { path: 'settings',
            component: ApplicationAdvancedComponent,
            children: [
              { path: 'metadata',
                component: ApplicationMetadataComponent,
                canActivate: [AuthGuard],
                data: {
                  menu: {
                    label: 'Application metadata',
                    section: 'Settings'
                  },
                  perms: {
                    only: ['application_metadata_read']
                  }
                }
              },
              { path: 'oauth2',
                component: ApplicationOAuth2Component,
                canActivate: [AuthGuard],
                resolve: { domainGrantTypes: ExtensionGrantsResolver, scopes: ScopesResolver },
                data: {
                  menu: {
                    label: 'OAuth 2.0 / OIDC',
                    section: 'Settings'
                  },
                  perms: {
                    only: ['application_oauth2_read']
                  }
                }
              },
              { path: 'members',
                component: ApplicationMembershipsComponent,
                canActivate: [AuthGuard],
                resolve: {
                  members: MembershipsResolver
                },
                data: {
                  menu: {
                    label: 'User and group access',
                    section: 'Settings'
                  },
                  perms: {
                    only: ['application_member_read']
                  }
                }
              },
              { path: 'account',
                component: ApplicationAccountSettingsComponent,
                canActivate: [AuthGuard],
                data: {
                  menu: {
                    label: 'User Accounts',
                    section: 'Security'
                  },
                  perms: {
                    only: ['application_user_account_read']
                  }
                }
              },
              { path: 'certificates',
                component: ApplicationCertificatesComponent,
                canActivate: [AuthGuard],
                resolve : { certificates: CertificatesResolver },
                data: {
                  menu: {
                    label: 'Certificates',
                    section: 'Security'
                  },
                  perms: {
                    only: ['application_certificate_read']
                  }
                }
              }
            ]
          }
        ]
      },
      { path: 'settings',
        component: DomainSettingsComponent,
        canActivate: [AuthGuard],
        resolve: {
          domain: DomainResolver,
        },
        data: {
          menu: {
            label: 'Settings',
            icon: 'settings',
          },
          perms: {
            only: ['domain_settings_read']
          }
        },
        children: [
          { path: '', redirectTo: 'general', pathMatch: 'full' },
          { path: 'general',
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
          { path: 'members',
            component: DomainSettingsMembershipsComponent,
            canActivate: [AuthGuard],
            resolve: {
              members: MembershipsResolver
            },
            data: {
              menu: {
                label: 'User and group access',
                section: 'Settings'
              },
              perms: {
                only: ['domain_member_read']
              }
            }
          },
          { path: 'login',
            component: DomainSettingsLoginComponent,
            canActivate: [AuthGuard],
            resolve: {
              domain: DomainResolver
            },
            data: {
              menu: {
                label: 'Login',
                section: 'Settings'
              },
              perms: {
                only: ['domain_login_settings_read']
              }
            }
          },
          { path: 'forms',
            component: DomainSettingsFormsComponent,
            canActivate: [AuthGuard],
            resolve: { domain: DomainResolver },
            data: {
              menu: {
                label: 'Forms',
                section: 'Settings'
              },
              perms: {
                only: ['domain_form_read']
              }
            }
          },
          { path: 'forms/form',
            component: DomainSettingsFormComponent,
            resolve: {
              form: FormResolver
            },
          },
          { path: 'emails',
            component: DomainSettingsEmailsComponent,
            canActivate: [AuthGuard],
            resolve: { domain: DomainResolver },
            data: {
              menu: {
                label: 'Emails',
                section: 'Settings'
              },
              perms: {
                only: ['domain_email_template_read']
              }
            }
          },
          { path: 'emails/email',
            component: DomainSettingsEmailComponent,
            resolve: {
              email: EmailResolver
            },
          },
          { path: 'policies',
            component: DomainSettingsPoliciesComponent,
            canActivate: [AuthGuard],
            resolve: {
              policies: PoliciesResolver
            },
            data: {
              menu: {
                label: 'Extension Points',
                section: 'Design'
              },
              perms: {
                only: ['domain_extension_point_read']
              }
            }
          },
          { path: 'providers',
            component: DomainSettingsProvidersComponent,
            canActivate: [AuthGuard],
            resolve: {
              providers: ProvidersResolver
            },
            data: {
              menu: {
                label: 'Providers',
                section: 'Identities'
              },
              perms: {
                only: ['domain_identity_provider_read']
              }
            }
          },
          { path: 'providers/new',
            component: ProviderCreationComponent,
            resolve: {
              certificates: CertificatesResolver
            }
          },
          { path: 'providers/:providerId',
            component: ProviderComponent,
            resolve: {
              provider: ProviderResolver
            },
            children: [
              { path: '', redirectTo: 'settings', pathMatch: 'full' },
              { path: 'settings', component: ProviderSettingsComponent, resolve: { certificates: CertificatesResolver } },
              { path: 'mappers', component: ProviderMappersComponent },
              { path: 'roles', component: ProviderRolesComponent, resolve: { roles: RolesResolver } }
            ]
          },
          { path: 'audits',
            component: AuditsComponent,
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Audit Log',
                section: 'Security'
              },
              perms: {
                only: ['domain_audit_read']
              }
            }
          },
          { path: 'audits/settings',
            component: AuditsSettingsComponent,
            canActivate: [AuthGuard],
            resolve: {
              reporters: ReportersResolver
            },
            data: {
              perms: {
                only: ['domain_audit_update']
              }
            }
          },
          { path: 'audits/settings/:reporterId',
            component: ReporterComponent,
            resolve: {
              reporter: ReporterResolver
            }
          },
          { path: 'audits/:auditId',
            component: AuditComponent,
            resolve: {
              audit: AuditResolver
            }
          },
          { path: 'account',
            component: DomainSettingsAccountComponent,
            canActivate: [AuthGuard],
            resolve: {
              domain: DomainResolver
            },
            data: {
              menu: {
                label: 'User Accounts',
                section: 'Security'
              },
              perms: {
                only: ['domain_user_account_read']
              }
            }
          },
          { path: 'certificates',
            component: DomainSettingsCertificatesComponent,
            canActivate: [AuthGuard],
            resolve: {
              certificates: CertificatesResolver
            },
            data: {
              menu: {
                label: 'Certificates',
                section: 'Security'
              },
              perms: {
                only: ['domain_certificate_read']
              }
            }
          },
          { path: 'certificates/new',
            component: CertificateCreationComponent
          },
          {
            path: 'certificates/:certificateId',
            component: CertificateComponent,
            canActivate: [AuthGuard],
            resolve: {
              certificate: CertificateResolver
            },
            data: {
              perms: {
                only: ['domain_certificate_read']
              }
            }
          },
          { path: 'users',
            component: UsersComponent,
            canActivate: [AuthGuard],
            resolve: {
              users: UsersResolver
            },
            data: {
              menu: {
                label: 'Users',
                section: 'User Management'
              },
              perms: {
                only: ['domain_user_read']
              }
            }
          },
          { path: 'users/new',
            component: UserCreationComponent,
            canActivate: [AuthGuard],
            data: {
              perms: {
                only: ['domain_user_create']
              }
            }
          },
          {
            path: 'users/:userId',
            component: UserComponent,
            canActivate: [AuthGuard],
            resolve: {
              user: UserResolver
            },
            data: {
              perms: {
                only: ['domain_user_read']
              }
            },
            children: [
              { path: '', redirectTo: 'profile', pathMatch: 'full' },
              { path: 'profile', component: UserProfileComponent },
              { path: 'applications', component: UserApplicationsComponent, resolve: {consents: ConsentsResolver}},
              { path: 'applications/:clientId', component: UserApplicationComponent, resolve: {consents: ConsentsResolver}},
              { path: 'roles', component: UserRolesComponent, resolve: { roles : UserRolesResolver}}
            ]
          },
          { path: 'groups',
            component: GroupsComponent,
            canActivate: [AuthGuard],
            resolve: {
              groups: GroupsResolver
            },
            data: {
              menu: {
                label: 'Groups',
                section: 'User Management'
              },
              perms: {
                only: ['domain_group_read']
              }
            }
          },
          { path: 'groups/new',
            component: GroupCreationComponent,
            canActivate: [AuthGuard],
            data: {
              perms: {
                only: ['domain_group_create']
              }
            }
          },
          {
            path: 'groups/:groupId',
            component: GroupComponent,
            canActivate: [AuthGuard],
            resolve: {
              group: GroupResolver
            },
            data: {
              perms: {
                only: ['domain_group_read']
              }
            },
            children: [
              { path: '', redirectTo: 'settings', pathMatch: 'full' },
              { path: 'settings', component: GroupSettingsComponent },
              { path: 'members', component: GroupMembersComponent, resolve: { members : GroupMembersResolver}},
              { path: 'roles', component: GroupRolesComponent, resolve: { roles : GroupRolesResolver}}
            ]
          },
          { path: 'roles',
            component: DomainSettingsRolesComponent,
            canActivate: [AuthGuard],
            resolve: {
              roles: RolesResolver
            },
            data: {
              menu: {
                label: 'Roles',
                section: 'User Management',
              },
              perms: {
                only: ['domain_role_read']
              }
            }
          },
          { path: 'roles/new',
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
            path: 'roles/:roleId',
            component: RoleComponent,
            canActivate: [AuthGuard],
            resolve: {
              role: RoleResolver,
              scopes: ScopesResolver
            },
            data: {
              perms: {
                only: ['domain_role_read']
              }
            }
          },
          { path: 'scim',
            component: ScimComponent,
            canActivate: [AuthGuard],
            resolve: {
              domain: DomainResolver
            },
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
          { path: 'scopes',
            component: DomainSettingsScopesComponent,
            canActivate: [AuthGuard],
            resolve: {
              scopes: ScopesResolver
            },
            data: {
              menu: {
                label: 'Scopes',
                section: 'OAuth 2.0'
              },
              perms: {
                only: ['domain_scope_read']
              }
            }
          },
          { path: 'scopes/new',
            component: ScopeCreationComponent,
            canActivate: [AuthGuard],
            data: {
              perms: {
                only: ['domain_scope_create']
              }
            }
          },
          { path: 'scopes/:scopeId',
            component: ScopeComponent,
            canActivate: [AuthGuard],
            resolve: {
              scope: ScopeResolver
            },
            data: {
              perms: {
                only: ['domain_scope_read']
              }
            }
          },
          { path: 'extensionGrants',
            component: DomainSettingsExtensionGrantsComponent,
            canActivate: [AuthGuard],
            resolve: {
              extensionGrants: ExtensionGrantsResolver
            },
            data: {
              menu: {
                label: 'Extension Grants',
                section: 'OAuth 2.0'
              },
              perms: {
                only: ['domain_extension_grant_read']
              }
            }
          },
          { path: 'extensionGrants/new',
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
            path: 'extensionGrants/:extensionGrantId',
            component: ExtensionGrantComponent,
            canActivate: [AuthGuard],
            resolve: {
              extensionGrant: ExtensionGrantResolver,
              identityProviders: ProvidersResolver
            },
            data: {
              perms: {
                only: ['domain_extension_grant_read']
              }
            }
          },
          { path: 'openid/clientRegistration',
            component: DomainSettingsOpenidClientRegistrationComponent,
            canActivate: [AuthGuard],
            data: {
              menu: {
                label: 'Client Registration',
                section: 'Openid'
              },
              perms: {
                only: ['domain_dcr_read']
              }
            },
            children: [
              { path: '', redirectTo: 'settings', pathMatch: 'full' },
              { path: 'settings', component: ClientRegistrationSettingsComponent, resolve: {domain: DomainResolver} },
              { path: 'default-scope', component: ClientRegistrationDefaultScopeComponent, resolve: {domain: DomainResolver, scopes: ScopesResolver}},
              { path: 'allowed-scope', component: ClientRegistrationAllowedScopeComponent, resolve: {domain: DomainResolver, scopes: ScopesResolver}},
              { path: 'templates', component: ClientRegistrationTemplatesComponent, resolve: {domain: DomainResolver, apps: ApplicationsResolver}},
            ]
          }
        ]
      }
    ]
  },
  { path: 'login', component: LoginComponent },
  { path: 'login/callback', component: LoginCallbackComponent },
  { path: 'logout', component: LogoutComponent },
  { path: 'logout/callback', component: LogoutCallbackComponent },
  { path: 'dummy', component: DummyComponent },
  { path: 'h', component: HomeComponent },
  { path: 'h/domains/new',
    component: DomainCreationComponent,
    canActivate: [AuthGuard],
    data: {
      perms: {
        only: ['management_domain_create']
      }
    },
  },
  { path: '', redirectTo: 'h', pathMatch: 'full'}
];

@NgModule({
  imports: [ RouterModule.forRoot(routes) ],
  exports: [ RouterModule ]
})
export class AppRoutingModule {}

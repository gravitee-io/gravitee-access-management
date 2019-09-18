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
import {DomainCreationComponent} from "./settings/domains/creation/domain-creation.component";
import {ProviderCreationComponent} from "./domain/settings/providers/creation/provider-creation.component";
import {ProviderComponent} from "./domain/settings/providers/provider/provider.component";
import {LogoutCallbackComponent} from "./logout/callback/callback.component";
import {LogoutComponent} from "./logout/logout.component";
import {DomainsResolver} from "./resolvers/domains.resolver";
import {DomainResolver} from "./resolvers/domain.resolver";
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
import {DashboardComponent} from "./dashboard/dashboard.component";
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
import {AuditsResolver} from "./resolvers/audits.resolver";
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

const routes: Routes = [
  { path: 'dashboard',
    component: DashboardComponent,
    data: {
      menu: {
        label: 'Dashboard',
        icon: 'dashboard',
        firstLevel: true
      }
    }
  },
  { path: 'dashboard/applications',
    component: ApplicationsComponent,
    resolve: {
      applications: ApplicationsResolver
    },
    data: {
      menu: {
        label: 'Applications',
        icon: 'apps',
        firstLevel: true
      }
    },
  },
  { path: 'dashboard/applications/new',
    component: ApplicationCreationComponent,
    resolve: {
      domains: DomainsResolver
    },
    data: {
      menu: {
        displayFirstLevel: true,
        activeParentPath: 'dashboard/applications'
      }
    }
  },
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
        component: DomainCreationComponent
      },
      {
        path: 'management',
        component: ManagementComponent,
        data: {
          menu: {
            label: 'Settings',
            icon: 'settings'
          }
        },
        children: [
          { path: '', redirectTo: 'general', pathMatch: 'full' },
          { path: 'general',
            component: ManagementGeneralComponent,
            resolve: {
              domain: DomainResolver,
            },
            data: {
              menu: {
                label: 'General',
                section: 'Settings'
              }
            }
          },
          {
            path: 'login',
            component: DomainSettingsFormComponent,
            resolve: {
              form: FormResolver
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
              { path: 'roles', component: ProviderRolesComponent, resolve: { roles: RolesResolver} }
            ]
          },
          { path: 'audits',
            component: AuditsComponent,
            resolve: {
              audits: AuditsResolver,
            },
            data: {
              menu: {
                label: 'Audit Log',
                section: 'Security'
              }
            }
          },
          { path: 'audits/settings',
            component: AuditsSettingsComponent,
            resolve: {
              reporters: ReportersResolver
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
          { path: 'scopes',
            component: DomainSettingsScopesComponent,
            resolve: {
              scopes: ScopesResolver
            },
            data: {
              menu: {
                label: 'Scopes',
                section: 'Security'
              }
            }
          },
          { path: 'scopes/new',
            component: ScopeCreationComponent
          },
          { path: 'scopes/:scopeId',
            component: ScopeComponent,
            resolve: {
              scope: ScopeResolver
            }
          },
          { path: 'users', component: UsersComponent,
            resolve: {
              users: UsersResolver
            },
            data: {
              menu: {
                label: 'Users',
                section: 'User Management'
              }
            }
          },
          {
            path: 'users/:userId',
            component: UserComponent,
            resolve: {
              user: UserResolver
            },
            children: [
              { path: '', redirectTo: 'profile', pathMatch: 'full' },
              { path: 'profile', component: UserProfileComponent },
              { path: 'applications', component: UserApplicationsComponent, resolve: {consents: ConsentsResolver}},
              { path: 'applications/:clientId', component: UserApplicationComponent, resolve: {consents: ConsentsResolver}},
              { path: 'roles', component: UserRolesComponent, resolve: { roles : UserRolesResolver}}
            ]
          },
          { path: 'roles', component: DomainSettingsRolesComponent,
            resolve: {
              roles: RolesResolver
            },
            data: {
              menu: {
                label: 'Roles',
                section: 'User Management',
              }
            }
          },
          { path: 'roles/new',
            component: RoleCreationComponent,
            resolve: {
              scopes: ScopesResolver
            }
          },
          {
            path: 'roles/:roleId',
            component: RoleComponent,
            resolve: {
              role: RoleResolver,
              scopes: ScopesResolver
            }
          },
          { path: 'tags', component: TagsComponent,
            resolve: {
              tags: TagsResolver
            },
            data: {
              menu: {
                label: 'Sharding tags',
                section: 'Deployment'
              }
            }
          },
          { path: 'tags/new',
            component: TagCreationComponent
          },
          {
            path: 'tags/:tagId',
            component: TagComponent,
            resolve: {
              tag: TagResolver
            }
          }
        ]
      }
    ]
  },
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
            icon: 'dashboard',
          }
        }
      },
      { path: 'applications',
        component: ApplicationsComponent,
        resolve: {
          applications: ApplicationsResolver,
          domain: DomainResolver
        },
        data: {
          menu: {
            label: 'Applications',
            icon: 'apps',
          }
        }
      },
      { path: 'applications/new',
        component: ApplicationCreationComponent,
        resolve: {
          domains: DomainsResolver
        }
      },
      { path: 'applications/:appId',
        component: ApplicationComponent,
        resolve: {
          application: ApplicationResolver
        },
        children: [
          { path: '', redirectTo: 'general', pathMatch: 'full' },
          { path: 'general', component: ApplicationGeneralComponent, resolve: { domain: DomainResolver } },
          { path: 'idp', component: ApplicationIdPComponent },
          { path: 'design',
            component: ApplicationDesignComponent,
            children: [
              { path: '', redirectTo: 'forms', pathMatch: 'full' },
              { path: 'forms',
                component: ApplicationFormsComponent,
                resolve: { domain: DomainResolver },
                data: {
                  menu: {
                    label: 'Forms',
                    section: 'Design'
                  }
                }
              },
              { path: 'forms/form', component: ApplicationFormComponent, resolve: { form: FormResolver } },
              { path: 'emails',
                component: ApplicationEmailsComponent,
                resolve: { domain: DomainResolver },
                data: {
                  menu: {
                    label: 'Emails',
                    section: 'Design'
                  }
                }
              },
              { path: 'emails/email', component: ApplicationEmailComponent, resolve: { email: EmailResolver} }
            ]
          },
          { path: 'settings',
            component: ApplicationAdvancedComponent,
            children: [
              { path: '', redirectTo: 'metadata', pathMatch: 'full' },
              { path: 'metadata',
                component: ApplicationMetadataComponent,
                data: {
                  menu: {
                    label: 'Application metadata',
                    section: 'Settings'
                  }
                }
              },
              { path: 'oauth2',
                component: ApplicationOAuth2Component,
                resolve: { domainGrantTypes: ExtensionGrantsResolver, scopes: ScopesResolver },
                data: {
                  menu: {
                    label: 'OAuth 2.0 / OIDC',
                    section: 'Settings'
                  }
                }
              },
              { path: 'account',
                component: ApplicationAccountSettingsComponent,
                data: {
                  menu: {
                    label: 'User Accounts',
                    section: 'Security'
                  }
                }
              },
              { path: 'certificates',
                component: ApplicationCertificatesComponent,
                resolve : { certificates: CertificatesResolver },
                data: {
                  menu: {
                    label: 'Certificates',
                    section: 'Security'
                  }
                }
              }
            ]
          }
        ]
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
            resolve: {
              tags: TagsResolver
            },
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
              domain: DomainResolver
            },
            data: {
              menu: {
                label: 'Login',
                section: 'Settings'
              }
            }
          },
          { path: 'forms',
            component: DomainSettingsFormsComponent,
            resolve: { domain: DomainResolver },
            data: {
              menu: {
                label: 'Forms',
                section: 'Settings'
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
            resolve: { domain: DomainResolver },
            data: {
              menu: {
                label: 'Emails',
                section: 'Settings'
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
            resolve: {
              policies: PoliciesResolver
            },
            data: {
              menu: {
                label: 'Extension Points',
                section: 'Design'
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
            resolve: {
              audits: AuditsResolver,
            },
            data: {
              menu: {
                label: 'Audit Log',
                section: 'Security'
              }
            }
          },
          { path: 'audits/settings',
            component: AuditsSettingsComponent,
            resolve: {
              reporters: ReportersResolver
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
            resolve: {
              domain: DomainResolver
            },
            data: {
              menu: {
                label: 'User Accounts',
                section: 'Security'
              }
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
          { path: 'users', component: UsersComponent,
            resolve: {
              users: UsersResolver
            },
            data: {
              menu: {
                label: 'Users',
                section: 'User Management'
              }
            }
          },
          { path: 'users/new',
            component: UserCreationComponent
          },
          {
            path: 'users/:userId',
            component: UserComponent,
            resolve: {
              user: UserResolver
            },
            children: [
              { path: '', redirectTo: 'profile', pathMatch: 'full' },
              { path: 'profile', component: UserProfileComponent },
              { path: 'applications', component: UserApplicationsComponent, resolve: {consents: ConsentsResolver}},
              { path: 'applications/:clientId', component: UserApplicationComponent, resolve: {consents: ConsentsResolver}},
              { path: 'roles', component: UserRolesComponent, resolve: { roles : UserRolesResolver}}
            ]
          },
          { path: 'groups', component: GroupsComponent,
            resolve: {
              groups: GroupsResolver
            },
            data: {
              menu: {
                label: 'Groups',
                section: 'User Management'
              }
            }
          },
          { path: 'groups/new',
            component: GroupCreationComponent
          },
          {
            path: 'groups/:groupId',
            component: GroupComponent,
            resolve: {
              group: GroupResolver
            },
            children: [
              { path: '', redirectTo: 'settings', pathMatch: 'full' },
              { path: 'settings', component: GroupSettingsComponent },
              { path: 'members', component: GroupMembersComponent, resolve: { members : GroupMembersResolver}},
              { path: 'roles', component: GroupRolesComponent, resolve: { roles : GroupRolesResolver}}
            ]
          },
          { path: 'roles', component: DomainSettingsRolesComponent,
            resolve: {
              roles: RolesResolver
            },
            data: {
              menu: {
                label: 'Roles',
                section: 'User Management',
              }
            }
          },
          { path: 'roles/new',
            component: RoleCreationComponent,
            resolve: {
              scopes: ScopesResolver
            }
          },
          {
            path: 'roles/:roleId',
            component: RoleComponent,
            resolve: {
              role: RoleResolver,
              scopes: ScopesResolver
            }
          },
          { path: 'scim', component: ScimComponent,
            resolve: {
              domain: DomainResolver
            },
            data: {
              menu: {
                label: 'SCIM',
                section: 'User Management'
              }
            }
          },
          { path: 'scopes',
            component: DomainSettingsScopesComponent,
            resolve: {
              scopes: ScopesResolver
            },
            data: {
              menu: {
                label: 'Scopes',
                section: 'OAuth 2.0'
              }
            }
          },
          { path: 'scopes/new',
            component: ScopeCreationComponent
          },
          { path: 'scopes/:scopeId',
            component: ScopeComponent,
            resolve: {
              scope: ScopeResolver
            }
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
          { path: 'openid/clientRegistration',
            component: DomainSettingsOpenidClientRegistrationComponent,
            data: {
              menu: {
                label: 'Client Registration',
                section: 'Openid'
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
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' }
];

@NgModule({
  imports: [ RouterModule.forRoot(routes) ],
  exports: [ RouterModule ]
})
export class AppRoutingModule {}

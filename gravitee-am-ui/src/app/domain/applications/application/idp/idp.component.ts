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

import {Component, HostListener, Inject, OnInit} from '@angular/core';
import {SnackbarService} from "../../../../services/snackbar.service";
import {ActivatedRoute} from "@angular/router";
import {ProviderService} from "../../../../services/provider.service";
import {ApplicationService} from "../../../../services/application.service";
import {AuthService} from "../../../../services/auth.service";
import {MAT_DIALOG_DATA, MatDialog, MatDialogRef} from '@angular/material/dialog';
import {OrganizationService} from "../../../../services/organization.service";

@Component({
  selector: 'app-idp',
  templateUrl: './idp.component.html',
  styleUrls: ['./idp.component.scss']
})
export class ApplicationIdPComponent implements OnInit {
  private domainId: string;
  private identities: any;
  private currentIdentityProvidersSize: number;
  priorities: any[];
  loadIdentities = true;
  application: any;
  identityProviders: any[];
  socialIdentityProviders: any[];
  formChanged = false;
  readonly = false;

  constructor(private route: ActivatedRoute,
              private applicationService: ApplicationService,
              private snackbarService: SnackbarService,
              private providerService: ProviderService,
              private authService: AuthService,
              private dialog: MatDialog
  ) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.data['domain'].id;
    this.application = this.route.snapshot.data['application'];
    this.identities = this.route.snapshot.data['identities'];
    this.readonly = !this.authService.hasPermissions(['application_identity_provider_update']);
    const applicationIdentityProviders = this.application.identityProviders || [];
    this.providerService.findByDomain(this.domainId).subscribe(data => {
      this.identityProviders = this.setUpIdentityProviders(data.filter(idp => !idp.external), applicationIdentityProviders);
      this.socialIdentityProviders = this.setUpIdentityProviders(data.filter(idp => idp.external), applicationIdentityProviders);
      this.loadIdentities = false;
    });
  }

  private setUpIdentityProviders(identityProviders, applicationIdentityProviders) {
    return identityProviders.map(idp => {
      const appIdentity = applicationIdentityProviders.find(appIdp => appIdp.identity == idp.id);
      if (appIdentity) {
        idp.selected = true;
        idp.selectionRule = appIdentity.selectionRule;
        idp.priority = appIdentity.priority;
      } else {
        idp.selected = false;
        idp.selectionRule = "";
        idp.priority = identityProviders.indexOf(idp);
      }
      return idp
    });
  }

  update() {
    const applicationIdentityProviders = this.identityProviders.concat(this.socialIdentityProviders)
      .filter(idp => idp.selected)
      .map(idp => {
        return {'identity': idp.id, 'selectionRule': idp.selectionRule, 'priority': idp.priority}
      });
    this.applicationService.patch(this.domainId, this.application.id,
      {'identityProviders': applicationIdentityProviders}).subscribe(data => {
      this.application = data;
      this.formChanged = false;
      this.snackbarService.open('Application updated');
    });
  }

  selectIdentityProvider(event, identityProviderId, identityProviders) {
    const idp = identityProviders.find(idp => idp.id === identityProviderId);
    idp.selected = event.checked;
    this.formChanged = true;
  }

  hasIdentityProviders() {
    return this.identityProviders && this.identityProviders.length > 0;
  }

  hasSocialIdentityProviders() {
    return this.socialIdentityProviders && this.socialIdentityProviders.length > 0;
  }

  getIdentityProvider(type) {
    if (this.identities && this.identities[type]) {
      return this.identities[type];
    }
    return null;
  }

  getIdentityProviderTypeIcon(type) {
    const provider = this.getIdentityProvider(type);
    if (provider && provider.icon) {
      const name = provider.displayName ? provider.displayName : provider.name;
      return `<img width="24" height="24" src="${provider.icon}" alt="${name} image" title="${name}"/>`;
    }
    return `<span class="material-icons">storage</span>`;
  }

  displayType(type) {
    const provider = this.getIdentityProvider(type);
    if (provider) {
      return provider.displayName ? provider.displayName : provider.name;
    }
    return 'Custom';
  }

  isIdentityProviderSelected(identityProviderId, identityProviders) {
    const identityProvider = identityProviders.find(idp => idp.id === identityProviderId);
    return identityProvider !== undefined && identityProvider.selected;
  }

  lowerPriority(index, identityProviders) {
    const previousPriority = identityProviders[index].priority - 1;
    if (previousPriority >= 0) {
      this.applyPriority(identityProviders, index, previousPriority);
    }
  }

  higherPriority(index, identityProviders) {
    const nextPriority = identityProviders[index].priority + 1;
    if (nextPriority < identityProviders.length) {
      this.applyPriority(identityProviders, index, nextPriority);
    }
  }

  private applyPriority(identityProviders, index, priority) {
    const currentIdp = identityProviders[index];
    const idpWithPriorityToApply = identityProviders.find(idp => idp.priority == priority);
    if (idpWithPriorityToApply) {
      idpWithPriorityToApply.priority = currentIdp.priority;
    }
    currentIdp.priority = priority;
    identityProviders.sort((a, b) => a.priority > b.priority ? 1 : -1);
    this.formChanged = true;
  }

  add(identityProvider) {
    let selectionRule = identityProvider.selectionRule;
    if (!selectionRule && !this.readonly) {
      if (identityProvider.type === "google-am-idp") {
        selectionRule = "{#request.params['username'] matches '.+@gmail.com$'}"
      } else if (identityProvider.type === "azure-ad-am-idp") {
        selectionRule = "{#request.params['username'] matches '.+@microsoft.com$'}"
      } else {
        selectionRule = "{#request.params['username'] matches '.+'}"
      }
    }
    if (!this.readonly || selectionRule) {
      const dialogRef = this.dialog.open(CreateIdpSelectionRuleComponent, {
        width: '700px',
        data: {
          selectionRule: selectionRule,
          readonly: this.readonly
        }
      });
      dialogRef.afterClosed().subscribe(idpSelectionRule => {
        if (idpSelectionRule && !this.readonly) {
          identityProvider.selectionRule = idpSelectionRule.value;
          identityProvider.selected = true;
          this.formChanged = true;
        }
      });
    }
  }
}

@Component({
  selector: 'create-idp-selection-rule',
  templateUrl: './selection-rule/create/create.component.html',
})
export class CreateIdpSelectionRuleComponent {

  spelGrammar: any;
  selectionRule: string;

  constructor(public dialogRef: MatDialogRef<CreateIdpSelectionRuleComponent>,
              private organizationService: OrganizationService,
              @Inject(MAT_DIALOG_DATA) public data: any
  ) {
    this.selectionRule = data.selectionRule;
  }

  getGrammar() {
    if (this.spelGrammar != null) {
      return Promise.resolve(this.spelGrammar);
    }

    return this.organizationService.spelGrammar().toPromise().then((response) => {
      this.spelGrammar = response;
      return this.spelGrammar;
    });
  }

  @HostListener(':gv-expression-language:ready', ['$event.detail'])
  setGrammar({currentTarget}) {
    this.getGrammar().then((grammar) => {
      currentTarget.grammar = grammar;
      currentTarget.requestUpdate();
    });
  };

  change($event) {
    this.selectionRule = $event.target.value;
  }
}

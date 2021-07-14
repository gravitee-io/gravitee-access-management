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
import {Component, EventEmitter, OnInit, Output, ViewChild} from '@angular/core'
import {ActivatedRoute, Router} from "@angular/router";
import {ApplicationService} from "../../../../../../services/application.service";
import {SnackbarService} from "../../../../../../services/snackbar.service";
import {AuthService} from "../../../../../../services/auth.service";
import * as _ from "lodash";
import {MatDialog, MatDialogRef} from "@angular/material/dialog";
import {NgForm} from "@angular/forms";

@Component({
  selector: 'application-tokens',
  templateUrl: './application-tokens.component.html',
  styleUrls: ['./application-tokens.component.scss']
})

export class ApplicationTokensComponent implements OnInit {
  @ViewChild('claimsTable') table: any;
  private domainId: string;
  formChanged: boolean;
  application: any;
  applicationOauthSettings: any = {};
  readonly = false;
  editing: any = {};

  constructor(private route: ActivatedRoute,
              private router: Router,
              private applicationService: ApplicationService,
              private snackbarService: SnackbarService,
              private authService: AuthService,
              public dialog: MatDialog) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = this.route.snapshot.data['application'];
    this.applicationOauthSettings = (this.application.settings == null) ? {} : this.application.settings.oauth || {};
    this.readonly = !this.authService.hasPermissions(['application_openid_update']);
    this.initCustomClaims();
  }

  patch() {
    this.cleanCustomClaims();
    let oauthSettings: any = {};
    oauthSettings.tokenCustomClaims = this.applicationOauthSettings.tokenCustomClaims;
    oauthSettings.accessTokenValiditySeconds = this.applicationOauthSettings.accessTokenValiditySeconds;
    oauthSettings.refreshTokenValiditySeconds = this.applicationOauthSettings.refreshTokenValiditySeconds;
    oauthSettings.idTokenValiditySeconds = this.applicationOauthSettings.idTokenValiditySeconds;
    this.applicationService.patch(this.domainId, this.application.id, {'settings' : { 'oauth' : oauthSettings}}).subscribe(data => {
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { 'reload': true }});
    });
  }

  addClaim(claim) {
    if (claim) {
      if (!this.claimExits(claim)) {
        claim.id = Math.random().toString(36).substring(7);
        this.applicationOauthSettings.tokenCustomClaims.push(claim);
        this.applicationOauthSettings.tokenCustomClaims = [...this.applicationOauthSettings.tokenCustomClaims];
        this.table.groupHeader.collapseAllGroups();
        this.formChanged = true;
      } else {
        this.snackbarService.open('Claim already exists');
      }
    }
  }

  claimExits(claim): boolean {
    return _.find(this.applicationOauthSettings.tokenCustomClaims, function(el) {
      return el.tokenType === claim.tokenType && el.claimName === claim.claimName;
    });
  }

  updateClaim(tokenType, event, cell, rowIndex) {
    const claim = event.target.value;
    if (claim) {
      this.editing[rowIndex + '-' + cell] = false;
      const index = _.findIndex(this.applicationOauthSettings.tokenCustomClaims, {id: rowIndex});
      this.applicationOauthSettings.tokenCustomClaims[index][cell] = claim;
      this.applicationOauthSettings.tokenCustomClaims = [...this.applicationOauthSettings.tokenCustomClaims];
      this.formChanged = true;
    }
  }

  deleteClaim(tokenType, key, event) {
    event.preventDefault();
    _.remove(this.applicationOauthSettings.tokenCustomClaims, function(el: any) {
      return (el.tokenType === tokenType && el.claimName === key);
    });
    this.applicationOauthSettings.tokenCustomClaims = [...this.applicationOauthSettings.tokenCustomClaims];
    this.formChanged = true;
  }

  claimsIsEmpty() {
    return this.applicationOauthSettings.tokenCustomClaims.length === 0;
  }

  toggleExpandGroup(group) {
    this.table.groupHeader.toggleExpandGroup(group);
  }

  openDialog(event) {
    event.preventDefault();
    this.dialog.open(ClaimsInfoDialog, {});
  }

  modelChanged(model) {
    this.formChanged = true;
  }

  private cleanCustomClaims() {
    if (this.applicationOauthSettings.tokenCustomClaims.length > 0) {
      this.applicationOauthSettings.tokenCustomClaims.forEach(claim => {
        delete claim.id;
      })
    }
  }

  private initCustomClaims() {
    if (this.applicationOauthSettings.tokenCustomClaims.length > 0) {
      this.applicationOauthSettings.tokenCustomClaims.forEach(claim => {
        claim.id = Math.random().toString(36).substring(7);
      })
    }
  }
}

@Component({
  selector: 'app-create-claim',
  templateUrl: './claims/add-claim.component.html'
})
export class CreateClaimComponent {
  claim: any = {};
  tokenTypes: any[] = ['ID_TOKEN', 'ACCESS_TOKEN'];
  @Output() addClaimChange = new EventEmitter();
  @ViewChild('claimForm', { static: true }) form: NgForm;

  constructor() {}

  addClaim() {
    this.addClaimChange.emit(this.claim);
    this.claim = {};
    this.form.reset(this.claim);
  }
}

@Component({
  selector: 'claims-info-dialog',
  templateUrl: './dialog/claims-info.component.html',
})
export class ClaimsInfoDialog {
  constructor(public dialogRef: MatDialogRef<ClaimsInfoDialog>) {}
}

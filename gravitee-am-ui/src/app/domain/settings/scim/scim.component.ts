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
import {Component, OnInit} from '@angular/core';
import {SnackbarService} from "../../../services/snackbar.service";
import {ActivatedRoute} from "@angular/router";
import {DomainService} from "../../../services/domain.service";
import {AuthService} from "../../../services/auth.service";
import {MatDialog, MatDialogRef} from "@angular/material/dialog";

@Component({
  selector: 'app-scim',
  templateUrl: './scim.component.html',
  styleUrls: ['./scim.component.scss']
})
export class ScimComponent implements OnInit {
  domainId: string;
  domain: any = {};
  formChanged = false;
  editMode: boolean;

  constructor(private domainService: DomainService,
              private snackbarService: SnackbarService,
              private authService: AuthService,
              private route: ActivatedRoute,
              public dialog: MatDialog) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domain.scim = this.domain.scim || {};
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_scim_update']);
  }

  save() {
    this.domainService.patchScimSettings(this.domainId, this.domain).subscribe(data => {
      this.domain = data;
      this.formChanged = false;
      this.snackbarService.open('SCIM configuration updated');
    });
  }

  enableSCIM(event) {
    this.domain.scim['enabled'] = (event.checked);
    this.formChanged = true;
  }

  enableIdpSelection(event) {
    this.domain.scim['idpSelectionEnabled'] = (event.checked);
    this.formChanged = true;
  }

  isSCIMEnabled() {
    return this.domain.scim && this.domain.scim.enabled;
  }

  isIdpSelectionEnabled() {
    return this.isSCIMEnabled() && this.domain.scim.idpSelectionEnabled;
  }

  formChange() {
    this.formChanged = true;
  }

  openDialog(event) {
    event.preventDefault();
    this.dialog.open(IdpSelectionInfoDialog, {});
  }

  formInvalid() {
    if (this.isIdpSelectionEnabled()) {
      return !this.domain.scim.idpSelectionRule;
    }
    return false;
  }

}

@Component({
  selector: 'idp-selection-info-dialog',
  templateUrl: './dialog/scim-info.component.html',
})
export class IdpSelectionInfoDialog {
  constructor(public dialogRef: MatDialogRef<IdpSelectionInfoDialog>) {}
}

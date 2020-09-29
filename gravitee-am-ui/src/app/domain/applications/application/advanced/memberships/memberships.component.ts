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
import {Component, Inject, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {MAT_DIALOG_DATA, MatDialog, MatDialogRef} from '@angular/material/dialog';
import {ApplicationService} from '../../../../../services/application.service';
import {DialogService} from '../../../../../services/dialog.service';
import {SnackbarService} from '../../../../../services/snackbar.service';
import {AuthService} from '../../../../../services/auth.service';

@Component({
  selector: 'app-application-memberships',
  templateUrl: './memberships.component.html',
  styleUrls: ['./memberships.component.scss']
})
export class ApplicationMembershipsComponent implements OnInit {
  private domainId: string;
  private application: any;
  appId: string;
  members: any;
  createMode = false;
  editMode = false;
  deleteMode = false;

  constructor(private applicationService: ApplicationService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private authService: AuthService,
              private route: ActivatedRoute,
              public dialog: MatDialog) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.application = this.route.snapshot.data['application'];
    this.appId = this.application.id;
    this.members = this.route.snapshot.data['members'];
    this.createMode = this.authService.hasPermissions(['application_member_create']);
    this.editMode = this.authService.hasPermissions(['application_member_update']);
    this.deleteMode = this.authService.hasPermissions(['application_member_delete']);
  }

  openDialog(): void {
    const dialogRef = this.dialog.open(ApplicationMembershipsDialog, {
      panelClass: 'no-padding-dialog-container',
      minWidth: '100vw',
      height: '100vh',
      data: {
        domainId: this.domainId,
        application: this.application,
        members: this.members,
        createMode: this.createMode,
        editMode: this.editMode,
        deleteMode: this.deleteMode
      }
    });
    dialogRef.afterClosed().subscribe(result => {
      this.reloadMembers();
    });
  }

  isEditable() {
    return this.createMode || this.editMode || this.deleteMode;
  }

  private reloadMembers() {
    this.applicationService.members(this.domainId, this.application.id).subscribe(response => {
      this.members = response;
    })
  }
}

@Component({
  selector: 'app-application-memberships-dialog',
  templateUrl: '../../../../components/memberships/dialog/memberships-dialog.html',
  styleUrls: ['../../../../components/memberships/dialog/memberships-dialog.scss']
})
export class ApplicationMembershipsDialog {
  private domainId: string;
  private appId: string;
  resource: any;
  members = [];
  roleType = 'APPLICATION';
  createMode: boolean;
  editMode: boolean;
  deleteMode: boolean;

  constructor(private applicationService: ApplicationService,
              private snackbarService: SnackbarService,
              public dialogRef: MatDialogRef<ApplicationMembershipsDialog>,
              @Inject(MAT_DIALOG_DATA) public data: any) {
    this.domainId = data.domainId;
    this.resource = data.application;
    this.appId = data.application.id;
    this.members = data.members;
    this.createMode = data.createMode;
    this.editMode = data.editMode;
    this.deleteMode = data.deleteMode;
  }

  close(): void {
    this.dialogRef.close();
  }

  add(membership) {
    this.applicationService.addMember(this.domainId, this.appId, membership.memberId, membership.memberType, membership.role).subscribe(response => {
      this.snackbarService.open('Member added');
      this.reloadMembers();
    });
  }

  delete(membershipId) {
    this.applicationService.removeMember(this.domainId, this.appId, membershipId).subscribe(response => {
      this.snackbarService.open('Member deleted');
      this.reloadMembers();
    });
  }

  update(membership) {
    this.applicationService.addMember(this.domainId, this.appId, membership.memberId, membership.memberType, membership.role).subscribe(response => {
      this.snackbarService.open('Member updated');
      this.reloadMembers();
    });
  }

  private reloadMembers() {
    this.applicationService.members(this.domainId, this.appId).subscribe(response => {
      this.members = response;
    })
  }
}

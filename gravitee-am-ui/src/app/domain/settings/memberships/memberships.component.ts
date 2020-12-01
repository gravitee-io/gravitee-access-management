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
import {Component, Inject, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {MatDialog, MatDialogRef, MAT_DIALOG_DATA} from '@angular/material/dialog';
import {DomainService} from '../../../services/domain.service';
import {SnackbarService} from '../../../services/snackbar.service';
import {DialogService} from '../../../services/dialog.service';
import {AuthService} from '../../../services/auth.service';

@Component({
  selector: 'app-domain-settings-memberships',
  templateUrl: './memberships.component.html',
  styleUrls: ['./memberships.component.scss']
})
export class DomainSettingsMembershipsComponent implements OnInit {
  @ViewChild('membersTable', { static: false }) table: any;
  private domain: any;
  domainId: string;
  members: any;
  createMode = false;
  editMode = false;
  deleteMode = false;

  constructor(private domainService: DomainService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private authService: AuthService,
              private route: ActivatedRoute,
              public dialog: MatDialog) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.parent.parent.data['domain'];
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.members = this.route.snapshot.data['members'];
    this.createMode = this.authService.hasPermissions(['domain_member_create']);
    this.editMode = this.authService.hasPermissions(['domain_member_update']);
    this.deleteMode = this.authService.hasPermissions(['domain_member_delete']);
  }

  openDialog(): void {
    const dialogRef = this.dialog.open(DomainMembershipsDialog, {
      panelClass: 'no-padding-dialog-container',
      minWidth: '100vw',
      height: '100vh',
      data: {
        domain: this.domain,
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
    this.domainService.members(this.domainId).subscribe(response => {
      this.members = response;
    })
  }
}

@Component({
  selector: 'app-domain-memberships-dialog',
  templateUrl: '../../components/memberships/dialog/memberships-dialog.html',
  styleUrls: ['../../components/memberships/dialog/memberships-dialog.scss']
})
export class DomainMembershipsDialog {
  private domainId: string;
  resource: any;
  members = [];
  roleType = 'DOMAIN';
  createMode: boolean;
  editMode: boolean;
  deleteMode: boolean;

  constructor(private domainService: DomainService,
              private snackbarService: SnackbarService,
              public dialogRef: MatDialogRef<DomainMembershipsDialog>,
              @Inject(MAT_DIALOG_DATA) public data: any) {
    this.resource = data.domain;
    this.domainId = data.domain.id;
    this.members = data.members;
    this.createMode = data.createMode;
    this.editMode = data.editMode;
    this.deleteMode = data.deleteMode;
  }

  close(): void {
    this.dialogRef.close();
  }

  add(membership) {
    this.domainService.addMember(this.domainId, membership.memberId, membership.memberType, membership.role).subscribe(response => {
      this.snackbarService.open('Member added');
      this.reloadMembers();
    });
  }

  delete(membershipId) {
    this.domainService.removeMember(this.domainId, membershipId).subscribe(response => {
      this.snackbarService.open('Member deleted');
      this.reloadMembers();
    });
  }

  update(membership) {
    this.domainService.addMember(this.domainId, membership.memberId, membership.memberType, membership.role).subscribe(response => {
      this.snackbarService.open('Member updated');
      this.reloadMembers();
    });
  }

  private reloadMembers() {
    this.domainService.members(this.domainId).subscribe(response => {
      this.members = response;
    })
  }
}

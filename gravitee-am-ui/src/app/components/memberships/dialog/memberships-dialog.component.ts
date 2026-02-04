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
import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { DomainService } from '../../../services/domain.service';
import { ApplicationService } from '../../../services/application.service';
import { ProtectedResourceService } from '../../../services/protected-resource.service';
import { SnackbarService } from '../../../services/snackbar.service';

export type MembershipsDialogContext = 'DOMAIN' | 'APPLICATION' | 'PROTECTED_RESOURCE';

export interface MembershipsDialogData {
  context: MembershipsDialogContext;
  domainId: string;
  appId?: string;
  protectedResourceId?: string;
  resource: any;
  members: any[];
  roleType?: string;
  createMode: boolean;
  editMode: boolean;
  deleteMode: boolean;
}

@Component({
  selector: 'app-memberships-dialog',
  templateUrl: './memberships-dialog.html',
  styleUrls: ['./memberships-dialog.scss'],
  standalone: false,
})
export class MembershipsDialogComponent {
  private context: MembershipsDialogContext;
  private domainId: string;
  private appId?: string;
  private protectedResourceId?: string;
  resource: any;
  members: any[] = [];
  roleType: string;
  createMode: boolean;
  editMode: boolean;
  deleteMode: boolean;

  constructor(
    private domainService: DomainService,
    private applicationService: ApplicationService,
    private protectedResourceService: ProtectedResourceService,
    private snackbarService: SnackbarService,
    public dialogRef: MatDialogRef<MembershipsDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: MembershipsDialogData,
  ) {
    this.context = data.context;
    this.domainId = data.domainId;
    this.appId = data.appId;
    this.protectedResourceId = data.protectedResourceId ?? data.resource?.id;
    this.resource = data.resource;
    this.members = data.members ?? [];
    this.roleType = data.roleType ?? data.context;
    this.createMode = data.createMode;
    this.editMode = data.editMode;
    this.deleteMode = data.deleteMode;
  }

  close(): void {
    this.dialogRef.close();
  }

  add(membership) {
    this.getAddRequest(membership).subscribe(() => {
      this.snackbarService.open('Member added');
      this.reloadMembers();
    });
  }

  delete(membershipId) {
    this.getDeleteRequest(membershipId).subscribe(() => {
      this.snackbarService.open('Member deleted');
      this.reloadMembers();
    });
  }

  update(membership) {
    this.getUpdateRequest(membership).subscribe(() => {
      this.snackbarService.open('Member updated');
      this.reloadMembers();
    });
  }

  private reloadMembers() {
    this.getMembersRequest().subscribe((response) => {
      this.members = response;
    });
  }

  private getMembersRequest() {
    if (this.context === 'APPLICATION') {
      return this.applicationService.members(this.domainId, this.appId);
    }
    if (this.context === 'PROTECTED_RESOURCE') {
      return this.protectedResourceService.members(this.domainId, this.protectedResourceId);
    }
    return this.domainService.members(this.domainId);
  }

  private getAddRequest(membership) {
    if (this.context === 'APPLICATION') {
      return this.applicationService.addMember(this.domainId, this.appId, membership.memberId, membership.memberType, membership.role);
    }
    if (this.context === 'PROTECTED_RESOURCE') {
      return this.protectedResourceService.addMember(
        this.domainId,
        this.protectedResourceId,
        membership.memberId,
        membership.memberType,
        membership.role,
      );
    }
    return this.domainService.addMember(this.domainId, membership.memberId, membership.memberType, membership.role);
  }

  private getDeleteRequest(membershipId) {
    if (this.context === 'APPLICATION') {
      return this.applicationService.removeMember(this.domainId, this.appId, membershipId);
    }
    if (this.context === 'PROTECTED_RESOURCE') {
      return this.protectedResourceService.removeMember(this.domainId, this.protectedResourceId, membershipId);
    }
    return this.domainService.removeMember(this.domainId, membershipId);
  }

  private getUpdateRequest(membership) {
    if (this.context === 'APPLICATION') {
      return this.applicationService.addMember(this.domainId, this.appId, membership.memberId, membership.memberType, membership.role);
    }
    if (this.context === 'PROTECTED_RESOURCE') {
      return this.protectedResourceService.addMember(
        this.domainId,
        this.protectedResourceId,
        membership.memberId,
        membership.memberType,
        membership.role,
      );
    }
    return this.domainService.addMember(this.domainId, membership.memberId, membership.memberType, membership.role);
  }
}

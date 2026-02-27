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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthorizationBundleService } from '../../../../services/authorization-bundle.service';
import { PolicySetService } from '../../../../services/policy-set.service';
import { AuthorizationSchemaService } from '../../../../services/authorization-schema.service';
import { EntityStoreService } from '../../../../services/entity-store.service';
import { SnackbarService } from '../../../../services/snackbar.service';

@Component({
  selector: 'app-authorization-bundle-creation',
  templateUrl: './authorization-bundle-creation.component.html',
  styleUrls: ['./authorization-bundle-creation.component.scss'],
  standalone: false,
})
export class AuthorizationBundleCreationComponent implements OnInit {
  bundle: any = {
    engineType: 'cedar',
    policySetPinToLatest: true,
    schemaPinToLatest: true,
    entityStorePinToLatest: true,
  };
  domainId: string;

  policySets: any[] = [];
  schemas: any[] = [];
  entityStores: any[] = [];

  policySetVersions: any[] = [];
  schemaVersions: any[] = [];
  entityStoreVersions: any[] = [];

  constructor(
    private authorizationBundleService: AuthorizationBundleService,
    private policySetService: PolicySetService,
    private authorizationSchemaService: AuthorizationSchemaService,
    private entityStoreService: EntityStoreService,
    private snackbarService: SnackbarService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.loadComponents();
  }

  loadComponents() {
    this.policySetService.findByDomain(this.domainId).subscribe((ps) => (this.policySets = ps));
    this.authorizationSchemaService.findByDomain(this.domainId).subscribe((s) => (this.schemas = s));
    this.entityStoreService.findByDomain(this.domainId).subscribe((es) => (this.entityStores = es));
  }

  onPolicySetChange(policySetId: string) {
    this.bundle.policySetId = policySetId;
    if (policySetId) {
      const ps = this.policySets.find((p) => p.id === policySetId);
      if (ps) {
        this.bundle.policySetVersion = ps.latestVersion;
      }
      this.policySetService.getVersions(this.domainId, policySetId).subscribe((v) => {
        this.policySetVersions = v.sort((a: any, b: any) => b.version - a.version);
      });
    } else {
      this.policySetVersions = [];
    }
  }

  onSchemaChange(schemaId: string) {
    this.bundle.schemaId = schemaId;
    if (schemaId) {
      const s = this.schemas.find((sc) => sc.id === schemaId);
      if (s) {
        this.bundle.schemaVersion = s.latestVersion;
      }
      this.authorizationSchemaService.getVersions(this.domainId, schemaId).subscribe((v) => {
        this.schemaVersions = v.sort((a: any, b: any) => b.version - a.version);
      });
    } else {
      this.schemaVersions = [];
    }
  }

  onEntityStoreChange(entityStoreId: string) {
    this.bundle.entityStoreId = entityStoreId;
    if (entityStoreId) {
      const es = this.entityStores.find((e) => e.id === entityStoreId);
      if (es) {
        this.bundle.entityStoreVersion = es.latestVersion;
      }
      this.entityStoreService.getVersions(this.domainId, entityStoreId).subscribe((v) => {
        this.entityStoreVersions = v.sort((a: any, b: any) => b.version - a.version);
      });
    } else {
      this.entityStoreVersions = [];
    }
  }

  create() {
    this.authorizationBundleService.create(this.domainId, this.bundle).subscribe(() => {
      this.snackbarService.open('Authorization bundle created');
      this.router.navigate(['..'], { relativeTo: this.route });
    });
  }
}

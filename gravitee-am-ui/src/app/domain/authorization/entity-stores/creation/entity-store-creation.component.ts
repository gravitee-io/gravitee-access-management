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

import { EntityStoreService } from '../../../../services/entity-store.service';
import { SnackbarService } from '../../../../services/snackbar.service';

@Component({
  selector: 'app-entity-store-creation',
  templateUrl: './entity-store-creation.component.html',
  styleUrls: ['./entity-store-creation.component.scss'],
  standalone: false,
})
export class EntityStoreCreationComponent implements OnInit {
  entityStore: any = {};
  domainId: string;

  constructor(
    private entityStoreService: EntityStoreService,
    private snackbarService: SnackbarService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
  }

  create() {
    this.entityStoreService.create(this.domainId, this.entityStore).subscribe(() => {
      this.snackbarService.open('Entity store created');
      this.router.navigate(['..'], { relativeTo: this.route });
    });
  }
}

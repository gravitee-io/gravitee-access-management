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
import { ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTabsModule } from '@angular/material/tabs';
import { MatSelectModule } from '@angular/material/select';

import { OpenFGAService } from '../../../services/openfga.service';
import { SnackbarService } from '../../../services/snackbar.service';

interface OpenFGAStore {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

@Component({
  selector: 'app-openfga',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatStepperModule,
    MatTabsModule,
    MatSelectModule,
  ],
  templateUrl: './openfga.component.html',
  styleUrls: ['./openfga.component.scss']
})
export class OpenFGAComponent implements OnInit {
  domainId: string;

  // Connection state
  serverUrl = 'http://localhost:8080';
  isConnecting = false;
  isConnected = false;

  // Store management
  stores: OpenFGAStore[] = [];
  selectedStoreId = '';
  isLoadingStores = false;
  showCreateStore = false;
  newStoreName = '';
  isCreatingStore = false;

  // Authorization model
  authorizationModel = ``;
  isLoadingModel = false;

  // Tuples
  tuples = [];

  newTuple = {
    user: '',
    relation: '',
    object: ''
  };

  // Permission testing
  permissionTest = {
    user: '',
    relation: '',
    object: ''
  };

  permissionResult: any = null;
  isCheckingPermission = false;
  permissionHistory: any[] = [];

  constructor(
    private route: ActivatedRoute,
    private openFGAService: OpenFGAService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
  }

  connect() {
    if (!this.serverUrl) {
      this.snackbarService.open('Please enter a server URL');
      return;
    }

    this.isConnecting = true;

    this.openFGAService.connect(this.domainId, this.serverUrl).subscribe({
      next: (response) => {
        this.isConnected = response.status;
        this.isConnecting = false;
        this.snackbarService.open('Successfully connected to OpenFGA server');
        this.stores = response.stores;
      },
      error: (error) => {
        this.isConnecting = false;
        this.snackbarService.open('Failed to connect to OpenFGA server');
        console.error('Connection error:', error);
      }
    });
  }

  disconnect() {
    this.isConnected = false;
    this.stores = [];
    this.selectedStoreId = '';
    this.snackbarService.open('Disconnected from OpenFGA server');
  }

  loadStores() {
    this.isLoadingStores = true;

    this.openFGAService.listStores(this.domainId).subscribe({
      next: (stores) => {
        this.stores = stores;
        this.isLoadingStores = false;
      },
      error: (error) => {
        this.isLoadingStores = false;
        this.snackbarService.open('Failed to load stores');
        console.error('Load stores error:', error);
      }
    });
  }

  createStore() {
    if (!this.newStoreName.trim()) {
      this.snackbarService.open('Please enter a store name');
      return;
    }

    this.isCreatingStore = true;

    this.openFGAService.createStore(this.domainId, this.newStoreName.trim()).subscribe({
      next: (newStore) => {
        this.stores.push(newStore);
        this.selectedStoreId = newStore.id;
        this.newStoreName = '';
        this.showCreateStore = false;
        this.isCreatingStore = false;
        this.snackbarService.open('Store created successfully');
      },
      error: (error) => {
        this.isCreatingStore = false;
        this.snackbarService.open('Failed to create store');
        console.error('Create store error:', error);
      }
    });
  }

  cancelCreateStore() {
    this.showCreateStore = false;
    this.newStoreName = '';
  }

  onStoreSelected() {
    const selectedStore = this.stores.find(store => store.id === this.selectedStoreId);
    if (selectedStore) {
      this.snackbarService.open(`Selected store: ${selectedStore.name}`);
      this.loadTuples();
      this.loadAuthorizationModel();
    }
  }

  loadTuples() {
    if (!this.selectedStoreId) {
      return;
    }

    this.openFGAService.listTuples(this.domainId, this.selectedStoreId).subscribe({
      next: (tuples) => {
        this.tuples = tuples || [];
      },
      error: (error) => {
        this.snackbarService.open('Failed to load tuples');
        console.error('Load tuples error:', error);
      }
    });
  }

  loadAuthorizationModel() {
    if (!this.selectedStoreId) {
      return;
    }

    this.isLoadingModel = true;
    this.openFGAService.getAuthorizationModel(this.domainId, this.selectedStoreId).subscribe({
      next: (response) => {
        this.isLoadingModel = false;
        if (response && response.authorizationModel) {
          this.authorizationModel = JSON.stringify(response.authorizationModel, null, 2);
        } else {
          this.authorizationModel = '';
        }
      },
      error: (error) => {
        this.isLoadingModel = false;
        this.snackbarService.open('Failed to load authorization model');
        console.error('Load authorization model error:', error);
        this.authorizationModel = '';
      }
    });
  }

  // Check if we can proceed to authorization management
  get canManageAuthorization(): boolean {
    return this.isConnected && !!this.selectedStoreId;
  }

  // Helper method to get selected store name
  get selectedStoreName(): string {
    return this.stores.find(s => s.id === this.selectedStoreId)?.name || '';
  }

  updateAuthorizationModel() {
    if (!this.authorizationModel.trim()) {
      this.snackbarService.open('Please enter an authorization model');
      return;
    }

    this.openFGAService.updateAuthorizationModel(this.domainId, this.authorizationModel, this.selectedStoreId).subscribe({
      next: (response) => {
        this.snackbarService.open('Authorization model updated successfully');
      },
      error: (error) => {
        this.snackbarService.open('Failed to update authorization model');
        console.error('Update authorization model error:', error);
      }
    });
  }

  addTuple() {
    if (!this.newTuple.user || !this.newTuple.relation || !this.newTuple.object) {
      this.snackbarService.open('Please fill in all tuple fields');
      return;
    }

    if (!this.selectedStoreId) {
      this.snackbarService.open('Please select a store first');
      return;
    }

    this.openFGAService.addTuple(this.domainId, this.selectedStoreId, this.newTuple).subscribe({
      next: (response) => {
        this.tuples.push({ ...this.newTuple, grantDuration: '', grantTime: '' });
        // Reset form
        this.newTuple = {
          user: '',
          relation: '',
          object: ''
        };
        this.snackbarService.open('Tuple added successfully');
      },
      error: (error) => {
        this.snackbarService.open('Failed to add tuple');
        console.error('Add tuple error:', error);
      }
    });
  }

  removeTuple(index: number) {
    this.tuples.splice(index, 1);
    this.snackbarService.open('Tuple removed successfully');
  }


  checkPermission() {
    if (!this.permissionTest.user || !this.permissionTest.relation || !this.permissionTest.object) {
      this.snackbarService.open('Please fill in all permission test fields');
      return;
    }

    if (!this.selectedStoreId) {
      this.snackbarService.open('Please select a store first');
      return;
    }

    this.isCheckingPermission = true;
    this.permissionResult = null;

    const permissionRequest = {
      user: this.permissionTest.user,
      relation: this.permissionTest.relation,
      object: this.permissionTest.object
    };

    this.openFGAService.checkPermission(this.domainId, this.selectedStoreId, permissionRequest).subscribe({
      next: (response) => {
        this.isCheckingPermission = false;
        this.permissionResult = response;

        // Add to history
        const historyItem = {
          ...permissionRequest,
          result: response,
          timestamp: new Date()
        };
        this.permissionHistory.unshift(historyItem);

        // Keep only last 10 results
        if (this.permissionHistory.length > 10) {
          this.permissionHistory = this.permissionHistory.slice(0, 10);
        }

        const message = response.allowed ? 'Permission allowed' : 'Permission denied';
        this.snackbarService.open(message);
      },
      error: (error) => {
        this.isCheckingPermission = false;
        this.snackbarService.open('Failed to check permission');
        console.error('Check permission error:', error);
      }
    });
  }

  clearPermissionTest() {
    this.permissionTest = {
      user: '',
      relation: '',
      object: ''
    };
    this.permissionResult = null;
  }

  clearPermissionHistory() {
    this.permissionHistory = [];
    this.snackbarService.open('Permission history cleared');
  }

  useExistingTuple(tuple: any) {
    this.permissionTest = {
      user: tuple.user,
      relation: tuple.relation,
      object: tuple.object
    };
  }

  formatTuple(tuple: any): string {
    return `${tuple.user} ${tuple.relation} ${tuple.object}`;
  }
}

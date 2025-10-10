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
import { Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormControl, Validators } from '@angular/forms';
import { transformer } from '@openfga/syntax-transformer';
import { graphBuilder } from '@openfga/frontend-utils';
import { Subscription } from 'rxjs';
import { filter, finalize, switchMap, tap } from 'rxjs/operators';
import { Network } from 'vis-network';
import './openfga-mode';
import { HttpErrorResponse } from '@angular/common/http';

import { OpenFGAService } from '../../../services/openfga.service';
import { AuthorizationEngineService } from '../../../services/authorization-engine.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { OrganizationService } from '../../../services/organization.service';
import { DialogService } from '../../../services/dialog.service';

interface AuthorizationEngine {
  id?: string;
  name: string;
  type: string;
  configuration: string;
}

interface Plugin {
  id: string;
  name: string;
  displayName?: string;
  icon?: string;
}

interface StoreInfo {
  name?: string;
  storeId?: string;
}

interface Tuple {
  user: string;
  relation: string;
  object: string;
}

interface PermissionResult {
  allowed: boolean;
}

@Component({
  selector: 'app-openfga',
  standalone: false,
  templateUrl: './openfga.component.html',
  styleUrls: ['./openfga.component.scss'],
})
export class OpenFGAComponent implements OnInit, OnDestroy {
  @ViewChild('graphContainer', { static: false }) graphContainer?: ElementRef;
  domainId: string;
  engineId: string;
  authorizationEngine: AuthorizationEngine = { name: '', type: '', configuration: '' };
  plugin: Plugin | null = null;
  storeId: string;
  authorizationModelId: string;
  storeInfo: StoreInfo | null = null;

  configuration: Record<string, any> = {};
  originalConfiguration: string = '';
  originalName: string = '';
  configurationSchema: Record<string, any> = {};
  configurationIsValid = false;

  authorizationModelDsl = '';
  authorizationModelJson: any | null = null;
  modelGraph: graphBuilder.GraphDefinition | null = null;
  private networkInstance: Network | null = null;

  readonly codeMirrorOptions = {
    lineNumbers: true,
    theme: 'default',
    mode: 'text/x-openfga',
    readOnly: true,
    lineWrapping: true,
  };

  tuples: Tuple[] = [];
  isLoadingTuples = false;
  readonly displayedColumns: string[] = ['user', 'relation', 'object', 'actions'];
  readonly pageSize = 10;
  continuationToken: string | null = null;
  tokenHistory: string[] = [];
  prefetchedData: Tuple[] | null = null;
  prefetchedToken: string | null = null;
  hasMorePages = false;

  userControl = new FormControl('', [Validators.required, Validators.pattern(/.*:.+/)]);
  relationControl = new FormControl('', [Validators.required]);
  objectControl = new FormControl('', [Validators.required, Validators.pattern(/.*:.+/)]);

  testUserControl = new FormControl('', [Validators.required, Validators.pattern(/.*:.+/)]);
  testRelationControl = new FormControl('', [Validators.required]);
  testObjectControl = new FormControl('', [Validators.required, Validators.pattern(/.*:.+/)]);

  permissionResult: PermissionResult | null = null;
  isCheckingPermission = false;

  private authorizationEnginePlugins: Record<string, Plugin> = {};
  private subscriptions = new Subscription();

  constructor(
    private route: ActivatedRoute,
    private openFGAService: OpenFGAService,
    private authorizationEngineService: AuthorizationEngineService,
    private snackbarService: SnackbarService,
    private organizationService: OrganizationService,
    private dialogService: DialogService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.engineId = this.route.snapshot.params['engineId'];
    this.authorizationEnginePlugins = this.toPluginMap(this.route.snapshot.data['authorizationEnginePlugins']);

    this.load();
  }

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
    if (this.networkInstance) {
      this.networkInstance.destroy();
    }
  }

  load() {
    this.subscriptions.add(
      this.authorizationEngineService.get(this.domainId, this.engineId).subscribe({
        next: (engine) => {
          this.authorizationEngine = engine;
          this.plugin = this.authorizationEnginePlugins?.[engine.type];
          const config = JSON.parse(engine.configuration || '{}');
          this.storeId = config.storeId;
          this.authorizationModelId = config.authorizationModelId;

          this.configuration = { ...config };
          this.originalConfiguration = JSON.stringify(config);
          this.originalName = engine.name;

          this.loadAuthorizationEngineSchema(engine.type);
          this.loadAuthorizationModel();
          this.loadTuples();
        },
        error: () => {
          this.snackbarService.open('Failed to load authorization engine');
        },
      }),
    );
  }

  loadAuthorizationEngineSchema(engineType: string) {
    this.subscriptions.add(
      this.organizationService.authorizationEngineSchema(engineType).subscribe({
        next: (schema) => {
          this.configurationSchema = schema;
        },
        error: () => {
          this.snackbarService.open('Failed to load authorization engine schema');
        },
      }),
    );
  }

  loadAuthorizationModel() {
    this.subscriptions.add(
      this.openFGAService
        .getStore(this.domainId, this.engineId)
        .pipe(
          tap((store) => {
            this.storeInfo = store;
          }),
          switchMap(() => this.openFGAService.getAuthorizationModel(this.domainId, this.engineId)),
        )
        .subscribe({
          next: (response) => {
            if (response) {
              try {
                this.authorizationModelJson = response;
                this.authorizationModelDsl = transformer.transformJSONStringToDSL(JSON.stringify(response));

                if (this.authorizationModelJson) {
                  const graphBuilderInstance = new graphBuilder.AuthorizationModelGraphBuilder(this.authorizationModelJson, {
                    name: this.storeInfo?.name,
                    id: this.storeInfo?.storeId,
                  });
                  this.modelGraph = graphBuilderInstance.graph;

                  setTimeout(() => this.renderGraph(), 100);
                }
              } catch (_error) {
                this.snackbarService.open('Failed to transform JSON to DSL');
                this.authorizationModelDsl = response;
              }
            } else {
              this.authorizationModelDsl = '';
              this.authorizationModelJson = null;
              this.modelGraph = null;
            }
          },
          error: () => {
            this.snackbarService.open('Failed to load store or authorization model');
            this.authorizationModelDsl = '';
            this.authorizationModelJson = null;
            this.modelGraph = null;
          },
        }),
    );
  }

  loadTuples(continuationToken?: string) {
    this.isLoadingTuples = true;
    this.subscriptions.add(
      this.openFGAService.listTuples(this.domainId, this.engineId, this.pageSize, continuationToken).subscribe({
        next: (response) => {
          this.tuples = response.data || [];
          this.continuationToken = response.info?.continuationToken || null;
          this.isLoadingTuples = false;

          // Prefetch next page if current page is full
          if (this.tuples.length === this.pageSize && this.continuationToken) {
            this.prefetchNextPage(this.continuationToken);
          } else if (this.tuples.length < this.pageSize) {
            this.hasMorePages = false;
            this.prefetchedData = null;
            this.prefetchedToken = null;
          }
        },
        error: () => {
          this.isLoadingTuples = false;
          this.snackbarService.open('Failed to load tuples');
        },
      }),
    );
  }

  private prefetchNextPage(token: string) {
    this.subscriptions.add(
      this.openFGAService.listTuples(this.domainId, this.engineId, this.pageSize, token).subscribe({
        next: (response) => {
          this.prefetchedData = response.data || [];
          this.prefetchedToken = response.info?.continuationToken || null;
          this.hasMorePages = this.prefetchedData.length > 0;
        },
        error: () => {
          this.hasMorePages = false;
          this.prefetchedData = null;
          this.prefetchedToken = null;
        },
      }),
    );
  }

  nextPage() {
    if (!this.hasMorePages) {
      return;
    }

    // Use prefetched data if available
    if (this.prefetchedData !== null) {
      this.tokenHistory.push(this.continuationToken!);
      this.tuples = this.prefetchedData;
      this.continuationToken = this.prefetchedToken;

      const nextToken = this.prefetchedToken;
      this.prefetchedData = null;
      this.prefetchedToken = null;

      // Prefetch next page if current page is full
      if (this.tuples.length === this.pageSize && nextToken) {
        this.prefetchNextPage(nextToken);
      } else {
        this.hasMorePages = false;
      }
    } else if (this.continuationToken) {
      this.tokenHistory.push(this.continuationToken);
      this.loadTuples(this.continuationToken);
    }
  }

  previousPage() {
    this.tokenHistory.pop();
    const previousToken = this.tokenHistory.length > 0 ? this.tokenHistory[this.tokenHistory.length - 1] : undefined;

    // Clear prefetch when going back
    this.prefetchedData = null;
    this.prefetchedToken = null;

    this.loadTuples(previousToken);
  }

  hasNextPage(): boolean {
    return this.hasMorePages;
  }

  hasPreviousPage(): boolean {
    return this.tokenHistory.length > 0;
  }

  isAddTupleValid(): boolean {
    return this.userControl.valid && this.relationControl.valid && this.objectControl.valid;
  }

  addTuple() {
    if (!this.isAddTupleValid()) {
      return;
    }

    const tuple = {
      user: this.userControl.value || '',
      relation: this.relationControl.value || '',
      object: this.objectControl.value || '',
    };

    const exists = this.tuples.some((t) => t.user === tuple.user && t.relation === tuple.relation && t.object === tuple.object);

    if (exists) {
      this.snackbarService.open('This tuple already exists');
      return;
    }

    this.openFGAService.addTuple(this.domainId, this.engineId, tuple).subscribe({
      next: () => {
        this.tokenHistory = [];
        this.prefetchedData = null;
        this.prefetchedToken = null;
        this.hasMorePages = false;
        this.loadTuples();
        this.userControl.reset();
        this.relationControl.reset();
        this.objectControl.reset();
        this.snackbarService.open('Tuple added successfully');
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse) {
          if (error.status === 400) {
            this.snackbarService.open('Invalid tuple: please check the values');
          } else {
            this.snackbarService.open('Failed to add tuple');
          }
        } else {
          this.snackbarService.open('Unexpected error occurred');
        }
      },
    });
  }

  deleteTuple(tuple: Tuple) {
    this.dialogService
      .confirm('Delete Tuple', 'Are you sure you want to delete this tuple?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.openFGAService.deleteTuple(this.domainId, this.engineId, tuple)),
      )
      .subscribe({
        next: () => {
          this.tokenHistory = [];
          this.prefetchedData = null;
          this.prefetchedToken = null;
          this.hasMorePages = false;
          this.loadTuples();
          this.snackbarService.open('Tuple deleted successfully');
        },
        error: () => {
          this.snackbarService.open('Failed to delete tuple');
        },
      });
  }

  isCheckPermissionValid(): boolean {
    return this.testUserControl.valid && this.testRelationControl.valid && this.testObjectControl.valid;
  }

  checkPermission() {
    if (!this.isCheckPermissionValid()) {
      return;
    }

    this.isCheckingPermission = true;
    this.permissionResult = null;

    const permissionRequest = {
      user: this.testUserControl.value || '',
      relation: this.testRelationControl.value || '',
      object: this.testObjectControl.value || '',
    };

    this.openFGAService.checkPermission(this.domainId, this.engineId, permissionRequest).subscribe({
      next: (response) => {
        this.isCheckingPermission = false;
        this.permissionResult = response;
        const message = response.allowed ? 'Permission allowed' : 'Permission denied';
        this.snackbarService.open(message);
      },
      error: () => {
        this.isCheckingPermission = false;
        this.snackbarService.open('Failed to check permission');
      },
    });
  }

  isConfigurationDirty(): boolean {
    const configChanged = JSON.stringify(this.configuration) !== this.originalConfiguration;
    const nameChanged = this.authorizationEngine?.name !== this.originalName;
    return configChanged || nameChanged;
  }

  onConfigurationChanged(configurationWrapper: { isValid: boolean; configuration: Record<string, any> }) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.configuration = configurationWrapper.configuration;
  }

  isSaving = false;

  saveConfiguration() {
    if (!this.isConfigurationDirty() || this.isSaving) {
      return;
    }

    this.isSaving = true;

    const updatedEngine: AuthorizationEngine = {
      ...this.authorizationEngine,
      name: this.authorizationEngine.name,
      configuration: JSON.stringify(this.configuration),
    };

    this.subscriptions.add(
      this.authorizationEngineService
        .update(this.domainId, this.engineId, updatedEngine)
        .pipe(
          finalize(() => {
            this.isSaving = false;
          }),
        )
        .subscribe({
          next: (engine) => {
            this.authorizationEngine = engine;
            const config = JSON.parse(engine.configuration || '{}');
            this.configuration = { ...config };
            this.originalConfiguration = JSON.stringify(config);
            this.originalName = engine.name;
            this.storeId = config.storeId;
            this.authorizationModelId = config.authorizationModelId;
            this.snackbarService.open('Configuration saved successfully');

            this.tokenHistory = [];
            this.prefetchedData = null;
            this.prefetchedToken = null;
            this.hasMorePages = false;
            this.loadAuthorizationModel();
            this.loadTuples();
          },
        }),
    );
  }

  private renderGraph() {
    if (!this.modelGraph || !this.graphContainer) {
      return;
    }

    // Destroy previous instance
    if (this.networkInstance) {
      this.networkInstance.destroy();
    }

    // Prepare nodes and edges for vis-network
    const nodes = this.modelGraph.nodes.map((node) => ({
      id: node.id,
      label: node.label,
      group: node.group || 'default',
    }));

    const edges = this.modelGraph.edges.map((edge) => ({
      from: edge.from,
      to: edge.to,
      label: edge.label,
      dashes: edge.dashes || false,
      arrows: 'to',
    }));

    // vis-network options
    const options: any = {
      nodes: {
        shape: 'box',
        margin: 10,
        widthConstraint: {
          maximum: 200,
        },
        font: {
          size: 14,
        },
      },
      edges: {
        arrows: {
          to: { enabled: true, scaleFactor: 0.5 },
        },
        smooth: {
          enabled: true,
          type: 'cubicBezier',
          roundness: 0.5,
        },
      },
      physics: {
        enabled: true,
        barnesHut: {
          gravitationalConstant: -2000,
          springConstant: 0.001,
          springLength: 200,
        },
        stabilization: {
          iterations: 150,
        },
      },
      layout: {
        hierarchical: {
          enabled: true,
          direction: 'LR',
          sortMethod: 'directed',
        },
      },
    };

    // Create network
    this.networkInstance = new Network(this.graphContainer.nativeElement, { nodes, edges }, options);
  }

  private toPluginMap(plugins: Plugin[] | Record<string, Plugin>): Record<string, Plugin> {
    if (!Array.isArray(plugins)) {
      return plugins || {};
    }
    return plugins.reduce(
      (acc, plugin) => {
        if (plugin?.id) {
          acc[plugin.id] = plugin;
        }
        return acc;
      },
      {} as Record<string, Plugin>,
    );
  }
}

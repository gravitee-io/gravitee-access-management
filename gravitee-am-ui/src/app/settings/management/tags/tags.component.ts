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
import { TagService } from '../../../services/tag.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DialogService } from '../../../services/dialog.service';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-tags',
  templateUrl: './tags.component.html',
  styleUrls: ['./tags.component.scss'],
})
export class TagsComponent implements OnInit {
  private tags: any[];

  constructor(
    private tagService: TagService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit() {
    this.tags = this.route.snapshot.data.tags;
  }

  get isEmpty() {
    return !this.tags || this.tags.length === 0;
  }

  loadTags() {
    this.tagService.list().subscribe((response) => (this.tags = response));
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService.confirm('Delete Sharding Tag', 'Are you sure you want to delete this sharding tag ?').subscribe((res) => {
      if (res) {
        this.tagService.delete(id).subscribe((response) => {
          this.snackbarService.open('Sharding tag deleted');
          this.loadTags();
        });
      }
    });
  }
}

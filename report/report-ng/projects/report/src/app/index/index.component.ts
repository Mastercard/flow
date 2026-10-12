import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { FlowFilterService } from '../flow-filter.service';
import { IndexDataService } from '../index-data.service';
import { Entry, Index } from '../types';
import { Location } from '@angular/common';
import { PageEvent } from '@angular/material/paginator';

/**
 * This is the main component of the index view - shows the
 * list of flows, and the filtering controls
 */
@Component({
    selector: 'app-index',
    templateUrl: './index.component.html',
    styleUrls: ['./index.component.css'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class IndexComponent implements OnInit {

  constructor(
    private filterService: FlowFilterService,
    private location: Location,
    private indexData: IndexDataService,
    private title: Title) {
    filterService.onUpdate(() => {
      this.updateQuery(true);
    });
  }

  index: Index = this.indexData.get();
  timestamp: string = "";

  filteredFlows: Entry[] = [];
  pagedFlows: Entry[] = [];
  tags: Set<string> = new Set();

  readonly pageSizeOptions: number[] = [50, 100, 200];
  pageIndex: number = 0;
  pageSize: number = 50;

  error: string = "";
  error_data: any;

  ngOnInit(): void {
    if (this.indexData.isValid()) {
      this.sortTags();
      this.timestamp = new Date(this.index.meta.timestamp).toLocaleString();

      this.updateQuery(false);
      this.title.setTitle(this.index.meta.modelTitle
        + " | " + this.index.meta.testTitle
        + " @ " + this.timestamp);
    }
    else {
      this.error = "Failed to grok data as Index";
      this.error_data = this.indexData.raw();
    }
  }

  /**
   * Updates the query args in the URL bar
   */
  private updateQuery(resetPage: boolean): void {
    this.filteredFlows = [];
    this.index.entries.forEach(e => {
      if (this.filterService.passes(e)) {
        this.filteredFlows.push(e);
      }
    });
    this.tags.clear();
    this.filteredFlows.forEach(e => e.tags.forEach(t => this.tags.add(t)));

    if (resetPage) {
      this.pageIndex = 0;
    }
    this.updatePage();

    this.updateUrl();
  }

  onPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.updatePage();
  }

  private updatePage(): void {
    const lastPage = Math.max(0, Math.ceil(this.filteredFlows.length / this.pageSize) - 1);
    this.pageIndex = Math.min(Math.max(0, this.pageIndex), lastPage);
    const first = this.pageIndex * this.pageSize;
    this.pagedFlows = this.filteredFlows.slice(first, first + this.pageSize);
  }

  private updateUrl(): void {
    try {
      let path = this.location.path();
      const i = path.indexOf('?');
      if (i >= 0) {
        path = path.substring(0, i);
      }
      this.location.replaceState(path, this.filterService.toQuery());
    }
    catch (e) {
      // url params are a nice to have, but they don't work when serving from filesystem
    }
  }

  /**
   * Sorts all the tag arrays in the index
   */
  sortTags(): void {
    this.index.entries.forEach(e => e.tags.sort());
  }
}

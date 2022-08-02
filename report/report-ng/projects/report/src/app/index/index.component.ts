import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { FlowFilterService, Type } from '../flow-filter.service';
import { IndexDataService } from '../index-data.service';
import { Entry, Index } from '../types';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';

/**
 * This is the main component of the index view - shows the
 * list of flows, and the filtering controls
 */
@Component({
  selector: 'app-index',
  templateUrl: './index.component.html',
  styleUrls: ['./index.component.css']
})
export class IndexComponent implements OnInit {

  constructor(
    private filterService: FlowFilterService,
    private location: Location,
    private indexData: IndexDataService,
    private title: Title) {
    filterService.onUpdate(() => {
      this.updateQuery();
    });
  }

  index: Index = this.indexData.get();
  timestamp: string = "";

  filteredFlows: Entry[] = [];
  tags: Set<string> = new Set();

  error: string = "";
  error_data: any;

  ngOnInit(): void {
    if (this.indexData.isValid()) {
      this.sortTags();
      this.timestamp = new Date(this.index.meta.timestamp).toLocaleString();

      this.updateQuery();
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
  private updateQuery(): void {
    this.filteredFlows = [];
    this.index.entries.forEach(e => {
      if (this.filterService.passes(e)) {
        this.filteredFlows.push(e);
      }
    });
    this.tags.clear();
    this.filteredFlows.forEach(e => e.tags.forEach(t => this.tags.add(t)));

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

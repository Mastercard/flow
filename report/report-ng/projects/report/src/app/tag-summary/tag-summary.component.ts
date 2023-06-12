import { Component, Input, OnInit, SimpleChanges } from '@angular/core';
import { FlowFilterService } from '../flow-filter.service';
import { Entry } from '../types';
import { IconEmbedService } from '../icon-embed.service';

@Component({
  selector: 'app-tag-summary',
  templateUrl: './tag-summary.component.html',
  styleUrls: ['./tag-summary.component.css']
})
export class TagSummaryComponent implements OnInit {
  readonly RESULT_TAGS: string[] = ["PASS", "FAIL", "SKIP", "ERROR"];

  @Input() entries: Entry[] = [];
  summary: string = "";
  results: string[] = [];
  tags: string[] = [];
  tagCounts: { [key: string]: number; } = {};
  alphaSort: boolean = false;

  constructor(
    private filters: FlowFilterService,
    private icons: IconEmbedService,) {
    icons.register(
      "check_circle_outline", "help_outline", "error_outline",
      "new_releases", "sort_by_alpha");
  }

  ngOnInit(): void {
  }

  ngOnChanges(changes: SimpleChanges) {
    this.tagCounts = {};
    this.entries.forEach(e =>
      e.tags.forEach(tag =>
        this.tagCounts[tag] = (this.tagCounts[tag] || 0) + 1
      ));

    this.results = [];
    this.RESULT_TAGS.forEach(rt => {
      if (this.tagCounts[rt] != undefined) {
        this.results.push(rt);
      }
    });

    this.tags = Object.keys(this.tagCounts).filter(t => !this.filters.all().has(t));
    this.summary = this.tags.length + " tag" + (this.tags.length === 1 ? "" : "s")
      + " on "
      + this.entries.length + " flow" + (this.entries.length === 1 ? "" : "s");
    this.resort(this.alphaSort);
  }

  resort(alpha: boolean) {
    this.alphaSort = alpha;
    if (this.alphaSort) {
      this.tags.sort();
    }
    else {
      this.tags.sort((a, b) => {
        let d = this.tagCounts[b] - this.tagCounts[a];
        if (d === 0) {
          d = a.localeCompare(b);
        }
        return d;
      });
    }
  }

}

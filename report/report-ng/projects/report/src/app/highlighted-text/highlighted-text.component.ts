import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { MsgSearchService } from '../msg-search.service';

/**
 * Thanks to https://stackoverflow.com/a/59159052/494747
 */
@Component({
  selector: 'app-highlighted-text',
  templateUrl: './highlighted-text.component.html',
  styleUrls: ['./highlighted-text.component.css']
})
export class HighlightedTextComponent implements OnInit, OnChanges {
  @Input() content: string | undefined = "";
  toHighlight: string = "";
  public result: string[] = [];

  constructor(private searchService: MsgSearchService) {
    searchService.onClear(() => {
      this.toHighlight = '';
      this.refresh();
    });
    searchService.onSearch(term => {
      this.toHighlight = term;
      this.refresh();
    });
    this.toHighlight = searchService.getTerm();
  }

  ngOnInit(): void {
    this.refresh();
  }

  ngOnChanges(): void {
    this.refresh();
  }

  refresh() {
    this.result = [];
    if (this.toHighlight.length === 0) {
      this.result.push(this.content ?? '');
    }
    else {
      let nonMatches = (this.content ?? '').split(this.toHighlight);
      this.result.push(nonMatches.shift() ?? '');
      while (nonMatches.length > 0) {
        this.result.push(this.toHighlight);
        this.result.push(nonMatches.shift() ?? '');
      }
    }
  }
}

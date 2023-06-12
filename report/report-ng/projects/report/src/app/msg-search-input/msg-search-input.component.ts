import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';

import { MsgSearchService } from '../msg-search.service';
import { QueryService } from '../query.service';
import { IconEmbedService } from '../icon-embed.service';

@Component({
  selector: 'app-msg-search-input',
  templateUrl: './msg-search-input.component.html',
  styleUrls: ['./msg-search-input.component.css']
})
export class MsgSearchInputComponent implements OnInit {

  value: string;
  showInput: boolean = false;
  @ViewChild('input') input?: ElementRef;

  constructor(
    private searchService: MsgSearchService,
    private query: QueryService,
    private icons: IconEmbedService,) {
    this.value = query.get("search", "");
    searchService.search(this.value);
    this.showInput = this.value.length > 0;
    icons.register("clear", "search")
  }

  ngOnInit(): void {
  }

  toggle() {
    this.value = "";
    this.query.set("search", this.value);
    this.searchService.search(this.value);
    this.showInput = !this.showInput;
    if (this.showInput) {
      setTimeout(() => {
        this.input?.nativeElement.focus();
      }, 0);
    }
  }

  onSearchChange(newValue: string): void {
    this.value = newValue;
    this.query.set("search", this.value);
    this.searchService.search(this.value);
  }
}

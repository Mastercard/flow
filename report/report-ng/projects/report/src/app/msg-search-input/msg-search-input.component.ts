import { Component, ElementRef, OnInit, ViewChild, inject } from '@angular/core';

import { MsgSearchService } from '../msg-search.service';
import { QueryService } from '../query.service';
import { IconEmbedService } from '../icon-embed.service';

@Component({
  selector: 'app-msg-search-input',
  templateUrl: './msg-search-input.component.html',
  styleUrls: ['./msg-search-input.component.css']
})
export class MsgSearchInputComponent implements OnInit {
  private searchService = inject(MsgSearchService);
  private query = inject(QueryService);
  private icons = inject(IconEmbedService);


  value: string;
  showInput: boolean = false;
  @ViewChild('input') input?: ElementRef;

  constructor() {
    const searchService = this.searchService;
    const query = this.query;
    const icons = this.icons;

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

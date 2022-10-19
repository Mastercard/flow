import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';

import { MsgSearchService } from '../msg-search.service';

@Component({
  selector: 'app-msg-search-input',
  templateUrl: './msg-search-input.component.html',
  styleUrls: ['./msg-search-input.component.css']
})
export class MsgSearchInputComponent implements OnInit {

  value: string = "";
  showInput: boolean = false;
  @ViewChild('input') input?: ElementRef;

  constructor(private searchService: MsgSearchService) { }

  ngOnInit(): void {
  }

  toggle() {
    this.value = "";
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
    this.searchService.search(this.value);
  }
}

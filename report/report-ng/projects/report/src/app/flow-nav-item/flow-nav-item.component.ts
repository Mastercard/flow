import { Component, OnInit, Input } from '@angular/core';
import { Entry } from '../types';

@Component({
  selector: 'app-flow-nav-item',
  templateUrl: './flow-nav-item.component.html',
  styleUrls: ['./flow-nav-item.component.css']
})
export class FlowNavItemComponent implements OnInit {

  @Input() showResult: boolean = true;
  @Input() basePath: string = "";
  @Input() entry: Entry = { description: "", tags: [], detail: "" };

  isPass: boolean = false;
  isFail: boolean = false;
  isSkip: boolean = false;
  isError: boolean = false;

  lineClass: string = "";

  ngOnInit(): void {
    this.isPass = this.showResult && this.entry.tags.indexOf("PASS") != -1;
    this.isFail = this.showResult && this.entry.tags.indexOf("FAIL") != -1;
    this.isSkip = this.showResult && this.entry.tags.indexOf("SKIP") != -1;
    this.isError = this.showResult && this.entry.tags.indexOf("ERROR") != -1;

    if (this.isSkip) {
      this.lineClass = "skip";
    }
    else if (this.isPass) {
      this.lineClass = "pass";
    }
    else if (this.isFail) {
      this.lineClass = "fail";
    }
    else if (this.isError) {
      this.lineClass = "error";
    }

    if (this.basePath.length !== 0) {
      this.basePath += "/";
    }
  }

  tags(): string[] {
    return this.entry.tags.filter(t => {
      return this.showResult || (
        t !== "PASS"
        && t !== "FAIL"
        && t !== "SKIP"
        && t !== "ERROR");
    });
  }
}

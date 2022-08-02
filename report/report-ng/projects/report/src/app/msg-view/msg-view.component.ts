import { Component, OnInit, Input } from '@angular/core';
import { QueryService } from '../query.service';
import { DataDisplay, Options } from '../types';


@Component({
  selector: 'app-msg-view',
  templateUrl: './msg-view.component.html',
  styleUrls: ['./msg-view.component.css']
})
export class MsgViewComponent implements OnInit {

  @Input() options: Options = new Options();
  @Input() dataDisplay: DataDisplay = DataDisplay.Human;
  @Input() human?: string;
  @Input() base64?: string;
  utf8?: string = "";
  bytes: Uint8Array = new Uint8Array();
  content?: string = "";
  showError: boolean = false;
  showContent: boolean = false;
  showHex: boolean = false;

  constructor(private query: QueryService) {
  }

  ngOnInit(): void {
  }

  ngOnChanges(): void {

    if (this.dataDisplay !== DataDisplay.Human) {
      this.query.set("facet", this.dataDisplay.toString());
    }
    else {
      this.query.delete("facet");
    }

    this.utf8 = undefined;
    if (this.base64 != undefined) {
      let raw: string = atob(this.base64 ?? '');
      this.bytes = new Uint8Array(raw.length);
      for (let i = 0; i < raw.length; i++) {
        this.bytes[i] = raw.charCodeAt(i);
      }
      this.utf8 = new TextDecoder("utf-8").decode(this.bytes);
    }

    this.showError = false;
    this.showContent = false;
    this.showHex = false;

    if (this.options.dataDisplay === DataDisplay.Human) {
      this.showContent = this.human != undefined;
      this.content = this.human;
    }
    else if (this.options.dataDisplay === DataDisplay.UTF) {
      this.showContent = this.utf8 != undefined;
      this.content = this.utf8;
    }
    else if (this.options.dataDisplay === DataDisplay.Hex) {
      this.showHex = this.base64 != undefined;
    }

    this.showError = !this.showContent && !this.showHex;
  }
}

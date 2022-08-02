import { Component, OnInit, Input } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { QueryService } from '../query.service';
import { DataDisplay, DiffType, Display, Options } from '../types';

@Component({
  selector: 'app-view-options',
  templateUrl: './view-options.component.html',
  styleUrls: ['./view-options.component.css']
})
export class ViewOptionsComponent implements OnInit {

  @Input() options: Options = new Options();
  displayEnum = Display;
  dataDisplayEnum = DataDisplay;
  diffTypeEnum = DiffType;

  constructor(iconRegistry: MatIconRegistry, sanitizer: DomSanitizer) {
    iconRegistry.addSvgIconLiteral('hex', sanitizer.bypassSecurityTrustHtml(`
      <svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px">
        <text x="1" y="17"  font-family="monospace" font-size="20px">0x</text>
      </svg>`));
  }

  ngOnInit(): void {
  }

  displayTip(d: string): string {
    switch (d) {
      case "Expected":
        return "Show expected message";
      case "Diff":
        return "Compare expected and actual message";
      case "Actual":
        return "Show actual message";
      case "Basis":
        return "Compare against basis flow";
    }
    return "";
  }

  displayIcon(d: string): string {
    switch (d) {
      case "Expected":
        return "psychology";
      case "Diff":
        return "compare";
      case "Actual":
        return "visibility";
      case "Basis":
        return "foundation";
    }
    return "";
  }

  dataTip(d: string): string {
    switch (d) {
      case "Human":
        return "Human-readable display";
      case "UTF":
        return "Content bytes as text";
      case "Hex":
        return "Content bytes";
    }
    return "";
  }

  dataIcon(d: string): string {
    switch (d) {
      case "Human":
        return "person";
      case "UTF":
        return "text_format";
    }
    return "";
  }

  dataSvgIcon(d: string): string {
    switch (d) {
      case "Hex":
        return "hex";
    }
    return "";
  }

  diffTip(d: string): string {
    switch (d) {
      case "Full":
        return "Full messages";
      case "Asserted":
        return "Asserted messages";
    }
    return "";
  }

  diffIcon(d: string): string {
    switch (d) {
      case "Full":
        return "subject";
      case "Asserted":
        return "rule";
    }
    return "";
  }

}

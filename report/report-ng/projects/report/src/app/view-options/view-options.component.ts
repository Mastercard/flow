import { Component, OnInit, Input, inject } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { DataDisplay, DiffType, Display, Options } from '../types';
import { IconEmbedService } from '../icon-embed.service';

@Component({
  selector: 'app-view-options',
  templateUrl: './view-options.component.html',
  styleUrls: ['./view-options.component.css']
})
export class ViewOptionsComponent implements OnInit {
  private icons = inject(IconEmbedService);


  @Input() options: Options = new Options();
  displayEnum = Display;
  dataDisplayEnum = DataDisplay;
  diffTypeEnum = DiffType;

  constructor() {
    const icons = this.icons;

    icons.register(
      "psychology", "difference", "visibility", "foundation",
      "person", "text_format", "hex",
      "subject", "rule",
      "vertical_split", "horizontal_split");
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
        return "difference";
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

import { Component, Input, OnInit } from '@angular/core';
import { IconEmbedService } from '../icon-embed.service';

/**
 * The navigation menu
 */
@Component({
  selector: 'app-menu',
  templateUrl: './menu.component.html',
  styleUrls: ['./menu.component.css']
})
export class MenuComponent implements OnInit {

  items: Item[] = [
    {
      id: "index",
      href: "",
      text: "Index",
      icon: "format_list_bulleted"
    },
    {
      id: "diff",
      href: "diff",
      text: "Model diff",
      icon: "compare"
    },
  ];

  @Input() current: string = "";

  constructor(private icons: IconEmbedService,) {
    icons.register("menu", "format_list_bulleted", "compare");
  }

  ngOnInit(): void {
  }

  displayItems(): Item[] {
    return this.items.filter(i => i.id !== this.current);
  }
}

interface Item {
  id: string;
  href: string;
  text: string;
  icon: string;
}
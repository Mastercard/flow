import { Component, OnInit, Input } from '@angular/core';

@Component({
  selector: 'app-hexdump',
  templateUrl: './hexdump.component.html',
  styleUrls: ['./hexdump.component.css']
})
export class HexdumpComponent implements OnInit {

  @Input() bytes: Uint8Array = new Uint8Array();
  hoveredIndex?: number;

  constructor() { }

  ngOnInit(): void {
  }

  select(b?: number): void {
    this.hoveredIndex = b;
  }

  selectedIndex(): string {
    if (this.hoveredIndex != undefined && this.hoveredIndex >= 0 && this.hoveredIndex < this.bytes.length) {
      return this.zeropad(this.hoveredIndex.toString(10), 8);
    }
    return "        ";
  }

  selectedValue(radix: number, width: number): string {
    if (this.hoveredIndex != undefined) {
      return this.zeropad(this.bytes[this.hoveredIndex].toString(radix), width);
    }
    let s = "";
    while (s.length < width) {
      s += " ";
    }
    return s;
  }

  displacements(): number[] {
    let displacements: number[] = [];
    for (let i = 0; i < this.bytes.length; i += 16) {
      displacements.push(i);
    }
    return displacements;
  }

  offsets(d: number): number[] {
    let offsets: number[] = [];
    for (let i = 0; i < 16; i++) {
      offsets.push(d + i);
    }
    return offsets;
  }

  formatDisplacment(d: number): string {
    return this.zeropad(d.toString(16), 8);
  }

  formatHex(i: number): string {
    if (i >= this.bytes.length) {
      return "  ";
    }
    return this.zeropad(this.bytes[i].toString(16), 2);
  }

  formatPrintable(i: number): string {
    if (i >= this.bytes.length) {
      return " ";
    }
    return printable.get(this.bytes[i]) ?? '.';
  }

  classForValue(index: number): string {
    if (index >= this.bytes.length) {
      return "";
    }

    if (index == this.hoveredIndex) {
      return "hovered";
    }
    let selectedValue = this.bytes[this.hoveredIndex ?? -1];
    let value = this.bytes[index];

    if (selectedValue === value) {
      return "same_value";
    }

    return "";
  }

  selectedAscii(): string {
    if (this.hoveredIndex != undefined) {
      return names.get(this.bytes[this.hoveredIndex]) ?? printable.get(this.bytes[this.hoveredIndex]) ?? '???';
    }
    return '';
  }

  zeropad(s: string, w: number): string {
    while (s.length < w) {
      s = "0" + s;
    }
    return s;
  }
}

const names: Map<number, string> = new Map([
  [0, 'NUL'],
  [1, 'SOH'],
  [2, 'STX'],
  [3, 'ETX'],
  [4, 'EOT'],
  [5, 'ENQ'],
  [6, 'ACK'],
  [7, 'BEL'],
  [8, 'BS'],
  [9, 'HT'],
  [10, 'LF'],
  [11, 'VT'],
  [12, 'FF'],
  [13, 'CR'],
  [14, 'SO'],
  [15, 'SI'],
  [16, 'DLE'],
  [17, 'DC1'],
  [18, 'DC2'],
  [19, 'DC3'],
  [20, 'DC4'],
  [21, 'NAK'],
  [22, 'SYN'],
  [23, 'ETB'],
  [24, 'CAN'],
  [25, 'EM'],
  [26, 'SUB'],
  [27, 'ESC'],
  [28, 'FS'],
  [29, 'GS'],
  [30, 'RS'],
  [31, 'US'],
  [32, 'space'],
  [127, 'DEL'],
]);

const printable: Map<number, string> = new Map([
  [32, ' '],
  [33, '!'],
  [34, '"'],
  [35, '#'],
  [36, '$'],
  [37, '%'],
  [38, '&'],
  [39, '\''],
  [40, '('],
  [41, ')'],
  [42, '*'],
  [43, '+'],
  [44, ','],
  [45, '-'],
  [46, '.'],
  [47, '/'],
  [48, '0'],
  [49, '1'],
  [50, '2'],
  [51, '3'],
  [52, '4'],
  [53, '5'],
  [54, '6'],
  [55, '7'],
  [56, '8'],
  [57, '9'],
  [58, ':'],
  [59, ';'],
  [60, '<'],
  [61, '='],
  [62, '>'],
  [63, '?'],
  [64, '@'],
  [65, 'A'],
  [66, 'B'],
  [67, 'C'],
  [68, 'D'],
  [69, 'E'],
  [70, 'F'],
  [71, 'G'],
  [72, 'H'],
  [73, 'I'],
  [74, 'J'],
  [75, 'K'],
  [76, 'L'],
  [77, 'M'],
  [78, 'N'],
  [79, 'O'],
  [80, 'P'],
  [81, 'Q'],
  [82, 'R'],
  [83, 'S'],
  [84, 'T'],
  [85, 'U'],
  [86, 'V'],
  [87, 'W'],
  [88, 'X'],
  [89, 'Y'],
  [90, 'Z'],
  [91, '['],
  [92, '\\'],
  [93, ']'],
  [94, '^'],
  [95, '_'],
  [96, '`'],
  [97, 'a'],
  [98, 'b'],
  [99, 'c'],
  [100, 'd'],
  [101, 'e'],
  [102, 'f'],
  [103, 'g'],
  [104, 'h'],
  [105, 'i'],
  [106, 'j'],
  [107, 'k'],
  [108, 'l'],
  [109, 'm'],
  [110, 'n'],
  [111, 'o'],
  [112, 'p'],
  [113, 'q'],
  [114, 'r'],
  [115, 's'],
  [116, 't'],
  [117, 'u'],
  [118, 'v'],
  [119, 'w'],
  [120, 'x'],
  [121, 'y'],
  [122, 'z'],
  [123, '{'],
  [124, '|'],
  [125, '}'],
  [126, '~'],]
);


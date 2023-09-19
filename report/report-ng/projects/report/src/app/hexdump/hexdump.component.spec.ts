import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HexdumpComponent } from './hexdump.component';

describe('HexdumpComponent', () => {
  let component: HexdumpComponent;
  let fixture: ComponentFixture<HexdumpComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [HexdumpComponent]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(HexdumpComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display emptily', () => {
    expect(lines(fixture.nativeElement))
      .toEqual([
        "index:         value: 0x   0d    0b        ascii: ",
      ]);
  });


  it('should display bytes', () => {
    setBytes(fixture, [
      104, 101, 108, 108, 111,
      32,
      119, 111, 114, 108, 100, 33,
    ]);

    expect(lines(fixture.nativeElement))
      .toEqual([
        'index:         value: 0x   0d    0b        ascii: ',
        '00000000 68 65 6c 6c 6f 20 77 6f 72 6c 64 21             |hello world!    |',
      ]);
  });


  it('should display multiple lines', () => {
    setBytes(fixture, new Array<number>(50));

    expect(lines(fixture.nativeElement))
      .withContext("you get 16 charecters per line")
      .toEqual([
        'index:         value: 0x   0d    0b        ascii: ',
        '00000000 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 |................|',
        '00000010 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 |................|',
        '00000020 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 |................|',
        '00000030 00 00                                           |..              |',
      ]);
  });

  it('should explain hovered hex', () => {
    setBytes(fixture, [
      104, 101, 108, 108, 111,
      32,
      119, 111, 114, 108, 100, 33,
    ]);

    mouseEnter(fixture, '6c');

    expect(lines(fixture.nativeElement))
      .withContext("top line details the '6c' value")
      .toEqual([
        'index: 00000002value: 0x6c 0d108 0b01101100ascii: l',
        '00000000 68 65 6c 6c 6f 20 77 6f 72 6c 64 21             |hello world!    |',
      ]);

    expect(highlit(fixture.nativeElement))
      .withContext("The other instances of that value are highlighted")
      .toEqual(['6c', '6c', '6c', 'l', 'l', 'l',]);
  });

  it('should explain hovered char', () => {
    setBytes(fixture, [
      104, 101, 108, 108, 111,
      32,
      119, 111, 114, 108, 100, 33,
    ]);

    mouseEnter(fixture, ' ');

    expect(lines(fixture.nativeElement))
      .withContext("top line details the ' ' value")
      .toEqual([
        'index: 00000005value: 0x20 0d032 0b00100000ascii: space',
        '00000000 68 65 6c 6c 6f 20 77 6f 72 6c 64 21             |hello world!    |'
      ]);

    expect(highlit(fixture.nativeElement))
      .withContext("The other instances of that value are highlighted")
      .toEqual(['20', ' ']);
  });
});

// sets the component input data
function setBytes(fixture: ComponentFixture<HexdumpComponent>, values: number[]): void {
  fixture.componentInstance.bytes = new Uint8Array(values);
  fixture.detectChanges();
}

// Hovers the mouse over the first instance of a span value
function mouseEnter(fixture: ComponentFixture<HexdumpComponent>, content: string): void {
  let spans: HTMLElement[] = Array.from(fixture.nativeElement
    .querySelectorAll("span"));
  spans.find(e => e.textContent === content)!
    .dispatchEvent(new MouseEvent('mouseenter', {
      view: window,
      bubbles: true,
      cancelable: true
    }));
  fixture.detectChanges();
}

function lines(component: HTMLElement): string[] {
  let lines: string[] = [];

  // component display is spaced out with css padding so you can copy the
  // data - we'll have to regex out the sections so the test is readable
  let dataLine: RegExp = new RegExp(
    "([0-9a-f]{8})" + "([0-9a-f ]{2})".repeat(16) + " (\\|.{16}\\|)");

  component.querySelectorAll("div")
    .forEach(row => {
      let m = row.textContent!.match(dataLine);
      if (m === null) {
        lines.push(row.textContent!);
      }
      else {

        lines.push(m
          // first element is the entire match - we just want the capture groups
          .slice(1)
          .join(" "));
      }
    });

  return lines;
}

function highlit(component: HTMLElement): string[] {
  let spans: HTMLElement[] = Array.from(component
    .querySelectorAll("span"));
  return spans
    .filter(e => e.classList.contains("hovered")
      || e.classList.contains("same_value"))
    .map(s => s.textContent!);
}

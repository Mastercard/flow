import { ComponentFixture, TestBed, tick } from '@angular/core/testing';

import { Display, TextDiffComponent } from './text-diff.component';

describe('TextDiffComponent', () => {
  let component: TextDiffComponent;
  let fixture: ComponentFixture<TextDiffComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [TextDiffComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(TextDiffComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display unified diffs', () => {
    expect(test(component, fixture, {
      left: `unchanged
removed
unchanged
changed foo
unchanged`,
      right: `unchanged
unchanged
changed bar
unchanged
added`,
    }))
      .withContext("display 'unified' (default value)")
      .toEqual([
        ['1', '1', ' ', 'unchanged    '],
        ['2', ' ', '-', '{removed}    '],
        ['3', '2', ' ', 'unchanged    '],
        ['4', ' ', '-', 'changed {foo}'],
        [' ', '3', '+', 'changed [bar]'],
        ['5', '4', ' ', 'unchanged    '],
        [' ', '5', '+', '[added]      '],
      ]);
  });

  it('should display split diffs', () => {
    expect(test(component, fixture, {
      left: `unchanged
removed
unchanged
changed foo
unchanged`,
      right: `unchanged
unchanged
changed bar
unchanged
added`,
      display: 'split'
    }))
      .withContext("display 'split'")
      .toEqual([
        ['1', ' ', 'unchanged    ', '1', ' ', 'unchanged    '],
        ['2', '-', '{removed}    ', ' ', ' ', '             '],
        ['3', ' ', 'unchanged    ', '2', ' ', 'unchanged    '],
        ['4', '-', 'changed {foo}', '3', '+', 'changed [bar]'],
        ['5', ' ', 'unchanged    ', '4', ' ', 'unchanged    '],
        [' ', ' ', '             ', '5', '+', '[added]      '],
      ]);
  });

  it('should display grouped diffs', () => {
    expect(test(component, fixture, {
      left: `unchanged
changed foo
changed foo
unchanged`,
      right: `unchanged
changed bar
changed bar
unchanged`,
    }))
      .withContext("blocksize 0 (default value)")
      .toEqual([
        ['1', '1', ' ', 'unchanged    '],
        ['2', ' ', '-', 'changed {foo}'],
        ['3', ' ', '-', 'changed {foo}'],
        [' ', '2', '+', 'changed [bar]'],
        [' ', '3', '+', 'changed [bar]'],
        ['4', '4', ' ', 'unchanged    '],
      ]);
  });

  it('should display ungrouped diffs', () => {
    expect(test(component, fixture, {
      left: `unchanged
changed foo
changed foo
unchanged`,
      right: `unchanged
changed bar
changed bar
unchanged`,
      blockSize: 1
    }))
      .withContext("blocksize 1")
      .toEqual([
        ['1', '1', ' ', 'unchanged    '],
        ['2', ' ', '-', 'changed {foo}'],
        [' ', '2', '+', 'changed [bar]'],
        ['3', ' ', '-', 'changed {foo}'],
        [' ', '3', '+', 'changed [bar]'],
        ['4', '4', ' ', 'unchanged    '],
      ]);
  });

  it('should preserve empty lines', () => {
    expect(test(component, fixture, {
      left: "start\n\n\n\n\nend",
      right: "start\n\n\n\n\nend",
    }))
      .withContext("empty lines")
      .toEqual([
        ['1', '1', '', 'start'],
        ['2', '2', '', '     '],
        ['3', '3', '', '     '],
        ['4', '4', '', '     '],
        ['5', '5', '', '     '],
        ['6', '6', '', 'end  '],
      ]);
  });

  it('should display unified collapsed unchanged blocks', () => {
    let left = "head unchanged\n".repeat(5)
      + "changed foo\n"
      + "mid unchanged\n".repeat(5)
      + "changed foo\n"
      + "tail unchanged\n".repeat(5);
    let right = left.replace(/foo/g, "bar");

    expect(test(component, fixture, {
      left: left,
      right: right,
      context: 1,
    }))
      .withContext("collapsed unchanged blocks")
      .toEqual([
        ['1 ', '1 ', ' ', 'head unchanged'],
        ['       3 unchanged lines       '],
        ['5 ', '5 ', ' ', 'head unchanged'],
        ['6 ', '  ', '-', 'changed {foo} '],
        ['  ', '6 ', '+', 'changed [bar] '],
        ['7 ', '7 ', ' ', 'mid unchanged '],
        ['       3 unchanged lines       '],
        ['11', '11', ' ', 'mid unchanged '],
        ['12', '  ', '-', 'changed {foo} '],
        ['  ', '12', '+', 'changed [bar] '],
        ['13', '13', ' ', 'tail unchanged'],
        ['       3 unchanged lines       '],
        ['17', '17', ' ', 'tail unchanged'],
      ]);

    fixture.nativeElement.querySelectorAll(".collapsed")[1].click();

    expect(dumpTable(fixture.nativeElement.querySelector("table")))
      .withContext("expanded the middle block")
      .toEqual([
        ['1 ', '1 ', ' ', 'head unchanged'],
        ['       3 unchanged lines       '],
        ['5 ', '5 ', ' ', 'head unchanged'],
        ['6 ', '  ', '-', 'changed {foo} '],
        ['  ', '6 ', '+', 'changed [bar] '],
        ['7 ', '7 ', ' ', 'mid unchanged '],
        ['8 ', '8 ', ' ', 'mid unchanged '],
        ['9 ', '9 ', ' ', 'mid unchanged '],
        ['10', '10', ' ', 'mid unchanged '],
        ['11', '11', ' ', 'mid unchanged '],
        ['12', '  ', '-', 'changed {foo} '],
        ['  ', '12', '+', 'changed [bar] '],
        ['13', '13', ' ', 'tail unchanged'],
        ['       3 unchanged lines       '],
        ['17', '17', ' ', 'tail unchanged'],
      ]);
  });

  it('should display split collapsed unchanged blocks', () => {
    let left = "head unchanged\n".repeat(5)
      + "changed foo\n"
      + "mid unchanged\n".repeat(5)
      + "changed foo\n"
      + "tail unchanged\n".repeat(5);
    let right = left.replace(/foo/g, "bar");

    expect(test(component, fixture, {
      left: left,
      right: right,
      context: 1,
      display: 'split',
    }))
      .withContext("collapsed unchanged blocks")
      .toEqual([
        ['1 ', ' ', 'head unchanged', '1 ', ' ', 'head unchanged'],
        ['                  3 unchanged lines                   '],
        ['5 ', ' ', 'head unchanged', '5 ', ' ', 'head unchanged'],
        ['6 ', '-', 'changed {foo} ', '6 ', '+', 'changed [bar] '],
        ['7 ', ' ', 'mid unchanged ', '7 ', ' ', 'mid unchanged '],
        ['                  3 unchanged lines                   '],
        ['11', ' ', 'mid unchanged ', '11', ' ', 'mid unchanged '],
        ['12', '-', 'changed {foo} ', '12', '+', 'changed [bar] '],
        ['13', ' ', 'tail unchanged', '13', ' ', 'tail unchanged'],
        ['                  3 unchanged lines                   '],
        ['17', ' ', 'tail unchanged', '17', ' ', 'tail unchanged'],
      ]);

    fixture.nativeElement.querySelectorAll(".collapsed")[1].click();

    expect(dumpTable(fixture.nativeElement.querySelector("table")))
      .withContext("expanded the middle block")
      .toEqual([
        ['1 ', ' ', 'head unchanged', '1 ', ' ', 'head unchanged'],
        ['                  3 unchanged lines                   '],
        ['5 ', ' ', 'head unchanged', '5 ', ' ', 'head unchanged'],
        ['6 ', '-', 'changed {foo} ', '6 ', '+', 'changed [bar] '],
        ['7 ', ' ', 'mid unchanged ', '7 ', ' ', 'mid unchanged '],
        ['8 ', ' ', 'mid unchanged ', '8 ', ' ', 'mid unchanged '],
        ['9 ', ' ', 'mid unchanged ', '9 ', ' ', 'mid unchanged '],
        ['10', ' ', 'mid unchanged ', '10', ' ', 'mid unchanged '],
        ['11', ' ', 'mid unchanged ', '11', ' ', 'mid unchanged '],
        ['12', '-', 'changed {foo} ', '12', '+', 'changed [bar] '],
        ['13', ' ', 'tail unchanged', '13', ' ', 'tail unchanged'],
        ['                  3 unchanged lines                   '],
        ['17', ' ', 'tail unchanged', '17', ' ', 'tail unchanged'],
      ]);
  });

  it('can be configured to not collapse', () => {
    let left = "unchanged\n".repeat(5);
    let right = left;

    expect(test(component, fixture, {
      left: left,
      right: right,
      context: 0,
    }))
      .withContext("max collapse")
      .toEqual([
        ['5 unchanged lines']
      ]);

    expect(test(component, fixture, {
      left: left,
      right: right,
      context: -1,
    }))
      .withContext("no collapse")
      .toEqual([
        ['1', '1', '', 'unchanged'],
        ['2', '2', '', 'unchanged'],
        ['3', '3', '', 'unchanged'],
        ['4', '4', '', 'unchanged'],
        ['5', '5', '', 'unchanged'],
      ]);
  });
});

function test(
  component: TextDiffComponent,
  fixture: ComponentFixture<TextDiffComponent>,
  args: any): string[][] {

  if (args.left != undefined) {
    component.left = args.left;
  }
  if (args.right != undefined) {
    component.right = args.right;
  }
  if (args.display !== undefined) {
    component.display = args.display;
  }
  if (args.blockSize !== undefined) {
    component.blockSize = args.blockSize;
  }
  if (args.context !== undefined) {
    component.context = args.context;
  }

  component.ngOnChanges();
  fixture.detectChanges();
  return dumpTable(fixture.nativeElement.querySelector("table"));
}

function dumpTable(table: HTMLElement): string[][] {
  let rows: string[][] = [];

  // extract
  table.querySelectorAll("tr").forEach(row => {
    let rowData: string[] = [];
    row.querySelectorAll("td").forEach(cell => {
      let content = '';
      cell.querySelectorAll("span").forEach(span => {
        let isAdd = span.classList.contains("added");
        let isRem = span.classList.contains("removed");
        if (isAdd) {
          content += "[";
        }
        if (isRem) {
          content += "{";
        }
        content += span.textContent;
        if (isAdd) {
          content += "]";
        }
        if (isRem) {
          content += "}";
        }
      });
      if (content.length === 0) {
        content = cell.textContent?.trim() || '';
      }
      rowData.push(content);
    });
    rows.push(rowData);
  });

  if (rows.length > 1) {
    let columns = rows
      .map(row => row.length)
      .reduce((p, c) => Math.max(p, c));

    // pad
    let widths: number[] = new Array<number>(columns);
    widths.fill(0);

    rows.filter(row => row.length > 1)
      .forEach(row => row
        .forEach((cell, index) => widths[index] = Math.max(widths[index], cell.length)));
    let totalWidth = widths.reduce((p, c) => p + c) + 4 * (widths.length - 1);

    rows.forEach(row => {
      if (row.length === 1) {
        row[0] = row[0]
          .padStart(totalWidth / 2 + row[0].length / 2)
          .padEnd(totalWidth);
      }
      else {
        row.forEach((cell, index) => {
          row[index] = cell.padEnd(widths[index]);
        });
      }
    });
  }

  return rows;
}
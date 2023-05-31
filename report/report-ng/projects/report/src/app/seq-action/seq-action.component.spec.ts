import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SeqActionComponent } from './seq-action.component';
import { BasisFetchService } from '../basis-fetch.service';
import { empty_transmission } from '../types';

describe('SeqActionComponent', () => {
  let component: SeqActionComponent;
  let fixture: ComponentFixture<SeqActionComponent>;
  let mockBfs;

  beforeEach(async () => {
    mockBfs = jasmine.createSpyObj(['onLoad', 'message']);
    await TestBed.configureTestingModule({
      declarations: [SeqActionComponent],
      providers: [
        { provide: BasisFetchService, useValue: mockBfs },
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SeqActionComponent);
    component = fixture.componentInstance;
    component.entity = ["Source", "Sink"];
    component.action = {
      index: 0,
      from: 0,
      fromName: "sourcename",
      to: 1,
      toName: "sinkname",
      label: "This is the action label",
      tags: ["these", "are", "tags"],
      transmission: {
        full: {
          expect: "full expect",
          expectBytes: "",
          actual: "full actual",
          actualBytes: "",
        },
        asserted: {
          expect: "asserted expect",
          actual: "asserted actual",
        },
      },
    };
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component)
      .toBeTruthy();
  });

  it('should render label and tags', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector(".label")!.textContent)
      .toEqual("This is the action label");

    let tags: string[] = [];
    compiled.querySelectorAll(".tag").forEach(t => tags.push(t.textContent!));
    expect(tags)
      .toEqual(["these", "are", "tags"]);
  });

  it('should render coverage', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector(".coverage")!.textContent)
      .toEqual("47%");
  });

  it('should compute coverage', () => {
    const compiled = fixture.nativeElement as HTMLElement;

    let cases = [
      { full: undefined, asrt: 'abcde', expt: '' },
      { full: 'abcde', asrt: undefined, expt: '' },
      { full: 'abcde', asrt: 'abcde', expt: '100%' },
      { full: 'abcde', asrt: 'abcdz', expt: '80%' },
      { full: 'abcye', asrt: 'abcdz', expt: '60%' },
      { full: 'abcye', asrt: 'abxdz', expt: '40%' },
      { full: 'awcye', asrt: 'abxdz', expt: '20%' },
      { full: 'awcye', asrt: 'vbxdz', expt: '0%' },
    ];

    cases.forEach(c => {
      component.action.transmission.full.actual = c.full;
      component.action.transmission.asserted!.actual = c.asrt;
      component.ngOnInit();
      fixture.detectChanges();

      expect(compiled.querySelector(".coverage")!.textContent)
        .withContext("Comparing '" + c.full + "' and '" + c.asrt + "'")
        .toEqual(c.expt);
    });
  });
});

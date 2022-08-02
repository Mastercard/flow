import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SeqNoteComponent } from './seq-note.component';

describe('SeqNoteComponent', () => {
  let component: SeqNoteComponent;
  let fixture: ComponentFixture<SeqNoteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ SeqNoteComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SeqNoteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

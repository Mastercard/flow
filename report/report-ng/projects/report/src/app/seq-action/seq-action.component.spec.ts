import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SeqActionComponent } from './seq-action.component';

describe('SeqActionComponent', () => {
  let component: SeqActionComponent;
  let fixture: ComponentFixture<SeqActionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ SeqActionComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SeqActionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

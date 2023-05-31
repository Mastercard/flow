import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SeqSectionComponent } from './seq-section.component';

describe('SeqSectionComponent', () => {
  let component: SeqSectionComponent;
  let fixture: ComponentFixture<SeqSectionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [SeqSectionComponent]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SeqSectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

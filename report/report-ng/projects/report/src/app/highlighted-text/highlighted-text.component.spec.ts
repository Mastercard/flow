import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HighlightedTextComponent } from './highlighted-text.component';

describe('HighlightedTextComponent', () => {
  let component: HighlightedTextComponent;
  let fixture: ComponentFixture<HighlightedTextComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ HighlightedTextComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(HighlightedTextComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

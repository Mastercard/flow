import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TagFilterComponent } from './tag-filter.component';
import { RouterTestingModule } from '@angular/router/testing';
import { MatAutocomplete } from '@angular/material/autocomplete';

describe('TagFilterComponent', () => {
  let component: TagFilterComponent;
  let fixture: ComponentFixture<TagFilterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [TagFilterComponent, MatAutocomplete],
      imports: [RouterTestingModule]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TagFilterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

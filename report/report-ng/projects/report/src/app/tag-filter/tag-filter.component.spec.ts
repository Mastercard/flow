import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TagFilterComponent } from './tag-filter.component';
import { RouterTestingModule } from '@angular/router/testing';
import { MatLegacyAutocompleteModule as MatAutocompleteModule } from '@angular/material/legacy-autocomplete';
import { MatLegacyChipsModule as MatChipsModule } from '@angular/material/legacy-chips';
import { MatLegacyFormFieldModule as MatFormFieldModule } from '@angular/material/legacy-form-field';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { ReactiveFormsModule } from '@angular/forms';

describe('TagFilterComponent', () => {
  let component: TagFilterComponent;
  let fixture: ComponentFixture<TagFilterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [
        TagFilterComponent,
      ],
      imports: [
        MatAutocompleteModule,
        RouterTestingModule,
        MatChipsModule,
        MatFormFieldModule,
        BrowserAnimationsModule,
        DragDropModule,
        ReactiveFormsModule,
      ]
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

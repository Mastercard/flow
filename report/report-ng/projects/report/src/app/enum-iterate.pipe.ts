import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'enumIterate'
})
export class EnumIteratePipe implements PipeTransform {

  transform<O extends object, K extends keyof O = keyof O>(obj: O): K[] {
    return Object.keys(obj).filter(k => Number.isNaN(+k)) as K[];
  }


}


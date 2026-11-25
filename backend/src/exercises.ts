import { Controller, Get, NotFoundException, Param, Query } from '@nestjs/common';
import { EXERCISES } from './data';

@Controller('exercises')
export class ExercisesController {
  @Get()
  list(@Query('place') place?: string, @Query('group') group?: string, @Query('level') level?: string) {
    return EXERCISES.filter((e) => (!place || e.places.includes(place)) && (!group || e.group === group) && (!level || e.level === level));
  }
  @Get(':id')
  one(@Param('id') id: string) {
    const e = EXERCISES.find((x) => x.id === id);
    if (!e) throw new NotFoundException();
    return e;
  }
}

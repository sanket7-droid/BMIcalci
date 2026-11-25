import { Controller, Get, Module } from '@nestjs/common';
import { ExercisesController } from './exercises';
import { ProgramsController } from './programs';
import { DietController } from './diet';

@Controller()
class HealthController {
  @Get('health') health() { return { ok: true }; }
}

@Module({ controllers: [HealthController, ExercisesController, ProgramsController, DietController] })
export class AppModule {}

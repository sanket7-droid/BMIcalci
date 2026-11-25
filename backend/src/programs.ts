import { BadRequestException, Controller, Get, Query } from '@nestjs/common';
import { EXERCISES, Exercise } from './data';

type Goal = 'fat_loss' | 'muscle_gain' | 'fitness';
type Level = 'beginner' | 'intermediate';
const SPLITS: Record<number, { name: string; groups: string[] }[]> = {
  3: [{ name: 'Full Body A', groups: ['chest', 'back', 'legs', 'core'] }, { name: 'Full Body B', groups: ['shoulders', 'legs', 'arms', 'core'] }, { name: 'Full Body C', groups: ['chest', 'back', 'legs', 'arms'] }],
  4: [{ name: 'Upper', groups: ['chest', 'back', 'shoulders', 'arms'] }, { name: 'Lower', groups: ['legs', 'core'] }, { name: 'Upper', groups: ['back', 'chest', 'arms', 'shoulders'] }, { name: 'Lower', groups: ['legs', 'core'] }],
  5: [{ name: 'Push', groups: ['chest', 'shoulders', 'arms'] }, { name: 'Pull', groups: ['back', 'arms'] }, { name: 'Legs', groups: ['legs', 'core'] }, { name: 'Upper', groups: ['chest', 'back', 'shoulders'] }, { name: 'Lower + Core', groups: ['legs', 'core'] }],
  6: [{ name: 'Push', groups: ['chest', 'shoulders', 'arms'] }, { name: 'Pull', groups: ['back', 'arms'] }, { name: 'Legs', groups: ['legs', 'core'] }, { name: 'Push', groups: ['chest', 'shoulders', 'arms'] }, { name: 'Pull', groups: ['back', 'arms'] }, { name: 'Legs', groups: ['legs', 'core'] }],
};
const SCHEME: Record<Goal, { sets: number; reps: string; rest: number }> = {
  fat_loss: { sets: 3, reps: '15-20', rest: 30 },
  muscle_gain: { sets: 4, reps: '8-12', rest: 75 },
  fitness: { sets: 3, reps: '12-15', rest: 45 },
};

function pick(pool: Exercise[], n: number, seed: number): Exercise[] {
  const out: Exercise[] = pool.filter((e) => e.video).slice(0, 1); // exercises with a demo video come first
  for (let i = 0; i < pool.length && out.length < n; i++) out.push(pool[(seed + i * 7) % pool.length]);
  return [...new Map(out.map((e) => [e.id, e])).values()];
}

@Controller('programs')
export class ProgramsController {
  @Get()
  build(@Query('place') place = 'home', @Query('goal') goal: Goal = 'fitness', @Query('level') level: Level = 'beginner', @Query('days') daysQ = '3') {
    const days = Math.min(6, Math.max(3, Number(daysQ)));
    if (!['home', 'gym'].includes(place) || !SCHEME[goal]) throw new BadRequestException('bad params');
    const scheme = SCHEME[goal];
    const allowed = level === 'beginner' ? ['beginner'] : ['beginner', 'intermediate'];
    const plan = SPLITS[days].map((d, di) => ({
      day: di + 1, title: d.name,
      exercises: d.groups.flatMap((g, gi) => {
        const pool = EXERCISES.filter((e) => e.places.includes(place) && e.group === g && allowed.includes(e.level));
        const n = g === 'core' || g === 'arms' ? 2 : 3;
        return pick(pool, n, di * 3 + gi).map((e) => ({ exerciseId: e.id, name: e.name, group: g, image: e.images[0] ?? '', sets: scheme.sets, reps: scheme.reps, restSec: scheme.rest, video: e.video }));
      }),
    }));
    return { place, goal, level, daysPerWeek: days, plan };
  }
}

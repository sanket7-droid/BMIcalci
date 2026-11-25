import { Controller, Get, Query } from '@nestjs/common';

const M = (slot: string, name: string, detail: string, kcal: number, protein: number) => ({ slot, name, detail, kcal, protein });
const VEG = [
  M('breakfast', 'Poha + moong sprouts + curd', '1 plate poha, 1/2 cup sprouts, 1 cup dahi', 420, 18),
  M('pre', 'Banana + peanut butter toast', '1 banana, 1 slice brown bread, 1 tbsp PB (30-45 min before)', 280, 8),
  M('post', 'Paneer bhurji + 2 roti', '100g paneer, 2 roti, salad (within 1 hr after)', 520, 30),
  M('lunch', 'Dal + rice + sabzi + salad', '1 bowl dal, 1 cup rice, mixed sabzi', 600, 22),
  M('snack', 'Roasted chana + buttermilk', '1 handful chana, 1 glass chaas', 200, 10),
  M('dinner', 'Soya chunks curry + 2 roti', '50g soya chunks, 2 roti, sabzi', 520, 36),
];
const NONVEG = [
  M('breakfast', 'Egg bhurji + 2 toast', '3 eggs, 2 brown bread, tea without sugar', 450, 28),
  M('pre', 'Banana + black coffee', '1 banana, coffee (30-45 min before)', 130, 2),
  M('post', 'Chicken breast + rice', '150g grilled chicken, 1 cup rice, salad (within 1 hr after)', 560, 45),
  M('lunch', 'Fish/chicken curry + roti + dal', '1 piece curry, 2 roti, dal', 650, 40),
  M('snack', 'Boiled eggs + fruit', '2 eggs, 1 apple', 230, 14),
  M('dinner', 'Egg curry/tandoori chicken + sabzi', '150g chicken or 3 eggs, sabzi, 1 roti', 500, 40),
];
const NOTES = {
  pre: 'Light carbs + a little protein 30-60 min before. Avoid heavy fat/fibre.',
  post: 'Protein + carbs within 1-2 hours to repair muscle and refill energy.',
  water: 'Drink 2.5-3.5 L water through the day, 500 ml around the workout.',
};
const ADJ = { fat_loss: -400, muscle_gain: 300, fitness: 0 };

@Controller('diet')
export class DietController {
  @Get()
  plan(@Query('type') type = 'veg', @Query('goal') goal = 'fitness', @Query('maintenanceKcal') m?: string) {
    const meals = type === 'nonveg' ? NONVEG : VEG;
    const target = m ? Math.round(Number(m) + (ADJ[goal] ?? 0)) : null;
    return { type, goal, targetKcal: target, meals, notes: NOTES, disclaimer: 'General guidance, not medical advice.' };
  }
}

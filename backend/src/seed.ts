// Pushes data/exercises.json to Supabase (needs .env). Run: npm run seed
import 'dotenv/config';
import { createClient } from '@supabase/supabase-js';
import { EXERCISES } from './data';

(async () => {
  const url = process.env.SUPABASE_URL, key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!url || !key) throw new Error('Fill SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY in .env');
  const sb = createClient(url, key);
  const rows = EXERCISES.map((e) => ({ id: e.id, name: e.name, level: e.level, equipment: e.equipment, places: e.places, grp: e.group, primary_muscles: e.primary, secondary_muscles: e.secondary, steps: e.steps, images: e.images, video_url: e.video }));
  for (let i = 0; i < rows.length; i += 100) {
    const { error } = await sb.from('exercises').upsert(rows.slice(i, i + 100));
    if (error) throw error;
  }
  console.log('seeded', rows.length);
})();

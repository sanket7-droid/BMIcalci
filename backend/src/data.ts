import * as fs from 'fs';
import * as path from 'path';

export interface Exercise {
  id: string; name: string; level: string; equipment: string; places: string[]; group: string;
  primary: string[]; secondary: string[]; steps: string[]; images: string[]; video: string | null;
}
const file = path.join(__dirname, '..', 'data', 'exercises.json');
export const EXERCISES: Exercise[] = JSON.parse(fs.readFileSync(file, 'utf8'));
// Optional: attach rendered demo video URLs (id -> url) from data/videos.json
const vfile = path.join(__dirname, '..', 'data', 'videos.json');
if (fs.existsSync(vfile)) {
  const v = JSON.parse(fs.readFileSync(vfile, 'utf8'));
  EXERCISES.forEach((e) => (e.video = v[e.id] ?? null));
}

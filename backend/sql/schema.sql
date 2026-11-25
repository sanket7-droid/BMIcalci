-- Run in Supabase SQL editor
create table if not exists exercises (
  id text primary key, name text not null, level text, equipment text,
  places text[] not null, grp text not null, primary_muscles text[], secondary_muscles text[],
  steps text[], images text[], video_url text
);
create table if not exists diet_meals (
  id serial primary key, diet text not null check (diet in ('veg','nonveg')),
  slot text not null, name text not null, detail text, kcal int, protein int
);
create table if not exists workout_logs (
  id uuid primary key default gen_random_uuid(), device_id text not null,
  created_at timestamptz default now(), payload jsonb not null
);
alter table exercises enable row level security;
create policy "public read" on exercises for select using (true);

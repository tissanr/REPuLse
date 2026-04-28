-- REPuLse Supabase schema
-- Apply via: supabase db push  OR  paste into Supabase SQL editor

-- ── Profiles (mirrors auth.users) ────────────────────────────────────────────

create table if not exists public.profiles (
  id           uuid primary key references auth.users on delete cascade,
  display_name text,
  avatar_url   text,
  created_at   timestamptz default now()
);

alter table public.profiles enable row level security;

create policy "profiles readable by anyone"
  on public.profiles for select using (true);

create policy "profiles updatable by owner"
  on public.profiles for update
  using (auth.uid() = id)
  with check (auth.uid() = id);

-- Auto-create a profile row when a new auth user is created
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer as $$
begin
  insert into public.profiles (id, display_name, avatar_url)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'full_name', new.raw_user_meta_data->>'user_name'),
    new.raw_user_meta_data->>'avatar_url'
  );
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ── Snippets ──────────────────────────────────────────────────────────────────

create table if not exists public.snippets (
  id          uuid primary key default gen_random_uuid(),
  author_id   uuid references public.profiles(id) on delete set null,
  title       text not null,
  description text,
  code        text not null,
  tags        text[] not null default '{}',
  bpm         integer,
  star_count  integer not null default 0,
  usage_count integer not null default 0,
  created_at  timestamptz default now(),
  updated_at  timestamptz default now()
);

-- Updated_at trigger
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists snippets_updated_at on public.snippets;
create trigger snippets_updated_at
  before update on public.snippets
  for each row execute function public.set_updated_at();

-- Row-level security
alter table public.snippets enable row level security;

create policy "snippets readable by anyone"
  on public.snippets for select using (true);

create policy "snippets insertable by authed users"
  on public.snippets for insert
  with check (auth.uid() = author_id);

create policy "snippets updatable by author"
  on public.snippets for update
  using (auth.uid() = author_id);

create policy "snippets deletable by author"
  on public.snippets for delete
  using (auth.uid() = author_id);

-- ── Stars ─────────────────────────────────────────────────────────────────────

create table if not exists public.stars (
  user_id    uuid references public.profiles(id) on delete cascade,
  snippet_id uuid references public.snippets(id) on delete cascade,
  created_at timestamptz default now(),
  primary key (user_id, snippet_id)
);

alter table public.stars enable row level security;

create policy "stars readable by anyone"
  on public.stars for select using (true);

create policy "stars insertable by owner"
  on public.stars for insert
  with check (auth.uid() = user_id);

create policy "stars deletable by owner"
  on public.stars for delete
  using (auth.uid() = user_id);

-- Maintain star_count on snippets via trigger
create or replace function public.update_star_count()
returns trigger language plpgsql security definer as $$
begin
  if (tg_op = 'INSERT') then
    update public.snippets set star_count = star_count + 1 where id = new.snippet_id;
  elsif (tg_op = 'DELETE') then
    update public.snippets set star_count = greatest(0, star_count - 1) where id = old.snippet_id;
  end if;
  return null;
end;
$$;

drop trigger if exists stars_count_insert on public.stars;
create trigger stars_count_insert
  after insert on public.stars
  for each row execute function public.update_star_count();

drop trigger if exists stars_count_delete on public.stars;
create trigger stars_count_delete
  after delete on public.stars
  for each row execute function public.update_star_count();

-- ── Indexes ───────────────────────────────────────────────────────────────────

create index if not exists snippets_author_idx on public.snippets (author_id);
create index if not exists snippets_tags_idx   on public.snippets using gin (tags);
create index if not exists snippets_stars_idx  on public.snippets (star_count desc);
create index if not exists stars_snippet_idx   on public.stars (snippet_id);

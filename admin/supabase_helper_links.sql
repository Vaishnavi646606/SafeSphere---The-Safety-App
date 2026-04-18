-- SafeSphere helper-specific tracking links (per emergency contact)
-- Run in Supabase SQL editor

create table if not exists public.emergency_helper_links (
  id uuid primary key default gen_random_uuid(),
  token text not null unique,
  user_id uuid not null references public.users(id) on delete cascade,
  contact_slot smallint not null check (contact_slot between 1 and 3),
  contact_number text not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  is_active boolean not null default true,
  opened_at timestamptz,
  last_opened_at timestamptz,
  open_count integer not null default 0
);

alter table public.emergency_helper_links
  add column if not exists helper_lat double precision,
  add column if not exists helper_lng double precision,
  add column if not exists helper_accuracy real,
  add column if not exists helper_last_updated timestamptz,
  add column if not exists helper_distance_m double precision,
  add column if not exists auto_rescue_triggered boolean not null default false,
  add column if not exists auto_rescue_at timestamptz;

create index if not exists idx_helper_links_token on public.emergency_helper_links(token);
create index if not exists idx_helper_links_user_id on public.emergency_helper_links(user_id);
create index if not exists idx_helper_links_user_slot_active_expiry on public.emergency_helper_links(user_id, contact_slot, is_active, expires_at);
create index if not exists idx_helper_links_active_expiry on public.emergency_helper_links(is_active, expires_at);
create index if not exists idx_helper_links_user_helper_last_updated on public.emergency_helper_links(user_id, helper_last_updated desc);
create index if not exists idx_helper_links_auto_rescue on public.emergency_helper_links(auto_rescue_triggered, auto_rescue_at desc);

alter table public.emergency_helper_links enable row level security;

-- No anon policies required; server service-role APIs handle access.

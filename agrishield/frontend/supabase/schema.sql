create extension if not exists pgcrypto;

create table if not exists public.users (
  id uuid primary key default gen_random_uuid(),
  full_name text not null,
  email text not null unique,
  role text not null check (role in ('admin','regulator','manufacturer','distributor','dealer','farmer')),
  phone text,
  password_hash text not null,
  totp_required boolean not null default false,
  verified boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.verify_codes (
  email text primary key references public.users(email) on delete cascade,
  code text not null,
  expires_at timestamptz not null,
  used boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.reset_codes (
  code text primary key,
  email text not null references public.users(email) on delete cascade,
  expires_at timestamptz not null,
  used boolean not null default false,
  created_at timestamptz not null default now()
);

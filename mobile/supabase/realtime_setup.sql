-- GymGate — Supabase Realtime setup
-- ---------------------------------------------------------------------------
-- Run this once in the Supabase SQL editor (or via the CLI). It is idempotent:
-- re-running it is safe.
--
-- What the app expects (see lib/realtime/realtime_service.dart):
--   members  — INSERT + UPDATE + DELETE   (live add / edit / remove on screens)
--   plans    — INSERT + UPDATE + DELETE   (same)
--   attendance, payments — INSERT only    (rows are append-only)
--   seance   — UPDATE only                (only its price is ever edited)
--
-- Two things are needed per table:
--   1. membership in the `supabase_realtime` publication (enables the stream)
--   2. REPLICA IDENTITY FULL on the tables we watch for UPDATE / DELETE, so the
--      change payload carries the full old row (not just the primary key).
--      `seance` doesn't need this: Postgres always sends the full *new* row on
--      an UPDATE regardless of replica identity, and the app never reads the
--      old row for this table.
-- ---------------------------------------------------------------------------

-- 1. Add the tables to the realtime publication (skip any already in it).
do $$
declare
  t text;
begin
  foreach t in array array['members', 'plans', 'attendance', 'payments', 'seance']
  loop
    if not exists (
      select 1
      from pg_publication_tables
      where pubname = 'supabase_realtime'
        and schemaname = 'public'
        and tablename = t
    ) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end $$;

-- 2. Full old-row payloads for the tables we watch for UPDATE / DELETE.
--    (attendance / payments are INSERT-only for the app, so they don't need it,
--     but it is harmless to set.)
alter table public.members    replica identity full;
alter table public.plans      replica identity full;
alter table public.attendance replica identity full;
alter table public.payments   replica identity full;

-- ---------------------------------------------------------------------------
-- Verify:
--   select schemaname, tablename
--   from pg_publication_tables
--   where pubname = 'supabase_realtime';
-- ---------------------------------------------------------------------------

# CookieDough AdminBridge contract

The bridge is disabled unless `ADMIN_BRIDGE_ENABLED=true`. It accepts only the
typed commands listed below and intentionally has no console, RCON, SQL, shell,
or arbitrary RabbitMQ execution command.

## Environment

- `ADMIN_BRIDGE_ENABLED` (default `false`)
- `ADMIN_BRIDGE_SERVER_ID` (default `minecraft-1`)
- `RABBITMQ_URL`, or `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`,
  `RABBITMQ_PASSWORD`, and `RABBITMQ_VHOST`
- `ADMIN_BRIDGE_EXCHANGE` (default `cookiebuild.admin`)
- `ADMIN_BRIDGE_COMMAND_QUEUE` (default
  `cookiebuild.admin.<serverId>.commands`)
- `ADMIN_BRIDGE_COMMAND_TTL_MS` (default 300000, bounded to 10s..1h)
- `ADMIN_BRIDGE_SNAPSHOT_SECONDS` (default 5, bounded to 2..60s)
- `ADMIN_BRIDGE_RECONNECT_SECONDS` (default 5, bounded to 1..60s)

The durable topic exchange is bound to `commands.<serverId>` and
`commands.all`. The queue uses a broker-side message TTL. CookieDough also
checks `expires_at`, deduplicates `idempotency_key`, consumes with manual ACK,
and ACKs only after the correlated result has been publisher-confirmed.
Expired commands are dead-lettered through
`dead.commands.<serverId>` into the durable, non-consumed
`<commandQueue>.dead` queue. The bridge never executes or automatically
replays this diagnostic queue.

## Command envelope

```json
{
  "id": "7de6544f-7dca-431d-ae28-094f744b2f91",
  "type": "message_player",
  "target_type": "player",
  "target_id": "44787eb5-3f21-4565-af67-588c6bf0550f",
  "payload": { "message": "Hello" },
  "created_at": "2026-07-15T10:00:00Z",
  "expires_at": "2026-07-15T10:05:00Z",
  "idempotency_key": "message:44787eb5:20260715T1000"
}
```

Supported `type` values are `message_all`, `message_lobby`, `message_game`,
`message_player`, `kick`, `return_lobby`, `ban_temp`, `ban_permanent`, `mute`,
`unmute`, `unban`, `rally`, `close_admissions`, `reopen_admissions`, `safe_cancel`,
`maintenance`, `drain`, and `restart_ready`.

Player targets use the Minecraft UUID. Game targets accept the live game UUID
or exact gamemode name. `ban_temp` needs `duration_seconds`; `mute` accepts an
optional `duration_seconds`; moderation payloads can include `reason`,
`player_name`, `actor_id`, `actor_display_name`, and object `metadata`.
Messages to all/lobby, maintenance, drain, and restart readiness use
`target_type=server`; rally accepts `server` or `game` and carries `gamemode`
in its payload.

## Event envelopes

Events use `events.<serverId>.<type>` and this common envelope:

```json
{
  "event_id": "e1568198-e0b7-49ba-8682-5acfb3bb303d",
  "type": "command_result",
  "server_id": "minecraft-1",
  "occurred_at": "2026-07-15T10:00:01Z",
  "data": {}
}
```

`events.<serverId>.command_result` data contains `command_id`,
`idempotency_key`, `status` (`completed`, `failed`, or `expired`), object
`result`, and nullable `error`.

`events.<serverId>.snapshot` data contains bridge/maintenance/drain health,
online/max player counts, live players (UUID, name, state, world, ping, game),
and live games (UUID, name, state, admission state, population, capacity,
minimum, elapsed time, countdown).

Other emitted types are `bridge_started`, `player_joined`, `player_left`, and
`game_changed`.

## PostgreSQL tables expected from the website migration

CookieDough uses native, best-effort SQL so a missing migration never prevents
Paper from starting. The website owns the migration.

```sql
create table admin_commands (
  id uuid primary key,
  type text not null,
  target_type text not null,
  target_id text,
  payload jsonb not null default '{}'::jsonb,
  status text not null default 'pending'
    check (status in ('pending', 'running', 'succeeded', 'failed', 'expired', 'cancelled')),
  created_at timestamptz not null default now(),
  available_at timestamptz not null default now(),
  expires_at timestamptz not null,
  started_at timestamptz,
  completed_at timestamptz,
  result jsonb,
  error text,
  idempotency_key text not null unique
);

create table moderation_actions (
  id uuid primary key,
  player_id uuid not null,
  player_name text not null,
  action_type text not null check (action_type in ('ban', 'mute')),
  reason text not null,
  actor_id varchar(128) not null references admin_users(firebase_uid),
  actor_display_name text not null,
  starts_at timestamptz not null,
  expires_at timestamptz,
  revoked_at timestamptz,
  revoked_by varchar(128) references admin_users(firebase_uid),
  source_command_id uuid unique references admin_commands(id) on delete set null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  check (expires_at is null or expires_at > starts_at)
);

create index moderation_actions_active_player_idx
  on moderation_actions (player_id, action_type, expires_at)
  where revoked_at is null;
```

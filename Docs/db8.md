# db8

webOS's database service: `palm://com.palm.db`, and `palm://com.palm.tempdb`, the same API
kept in memory. Lunacy's is `AndroidLuna/src/main/java/org/webosarchive/lunacy/card/Db8.kt`,
on SQLite. Everything below was measured on the reference TouchPad (webOS CE 3.1.0) with
`Workbench/probe/db8probe.sh` and `db8watch.sh`, run there as the probe app. The same calls run
in Lunacy with `Workbench/probe/db8-lunacy.js`, and all 41 of them answer as the TouchPad did,
apart from ids, revisions and page tokens.

## Kinds

- `putKind` takes `{id, owner, indexes, extends, …}` and replies `{"returnValue":true}`.
  Registering a kind again replaces it.
- Only the owner may put or delete a kind: `-3963 "db: permission denied"`.
- An unknown kind: `-3970 "kind not registered: '<id>'"` (from `delKind`, `put`, `find` and
  the rest).
- **Packages register kinds at install.** An app or service folder's
  `configuration/db/kinds/<file>` holds `putKind` parameters, and
  `configuration/db/permissions/<file>` holds a `putPermissions` array, whose callers may end
  in `*` ("com.palm.service.calendar.*") and whose operations are `create`, `read`, `update`,
  `delete` (and `extend`). Lunacy's `Configurator` registers them at startup and after each
  install, as webOS's configurator did. `configuration/tempdb/` does the same for tempdb.
- Who may use a kind's objects: its owner, callers it grants, and (Lunacy's rule, not
  measured) callers named as the owner's package, such as an app and its service
  (`com.foo.app` and `com.foo.app.service`).

## Objects

- Ids are `"++"` and 14 characters from `+0-9A-Z_a-z`, e.g. `++OQr0ZzLoW+aYAl`, and sort in
  creation order. `_rev` comes from one counter for the whole database.
- `put {objects}` replies `{"returnValue":true,"results":[{"id","rev"}, …]}`.
  - No `_kind`: `-3969 "db: kind not specified"`.
  - An existing `_id` without `_rev`: `-3960 "db: _rev must be specified for existing objects"`.
  - A stale `_rev`: `-3961 "db: revision mismatch - expected <n>, got <m>"`.
  - With the current `_rev`, the object is replaced.
- `get {ids}` returns the objects, `_id`, `_kind` and `_rev` included; missing ids are left
  out, and deleted objects come back with `"_del": true`.
- `merge {objects}` merges props by `_id` and returns `{id, rev}` results. `merge {query,
  props}` returns `{"count": n}`.
- `del {ids}` marks objects `_del` and returns `{id, rev}`; with `"purge": true` they're
  removed and only `{id}` returns. `del {query}` returns `{"count": n}`.
- `reserveIds {count}` returns `{"ids": [...]}`. `batch {operations: [{method, params}]}`
  returns `{"responses": [...]}`.

## Queries

- `find {query: {from, where, orderBy, desc, limit, page, select, incDel}}` returns
  `{"results": [...]}`. The default order is by `_id`.
- **An index must serve the query**, or it fails with `-3965 "db: no index for query"`. The
  props compared with `=` come first in the index, in any order; then at most one prop with
  another operator; `orderBy` is that prop, or the next prop of the index.
- Operators: `=` (a list value means any of them), `!=`, `<`, `<=`, `>`, `>=`, `%`
  (prefix) and, in `search`, `?` (word prefix). An array prop matches if any element does.
- `limit` defaults to 500, the maximum. When the results fill the limit, `next` holds a page
  token for `page`, even when nothing is left.
- `select` returns only the named props (not even `_id`); `"count": true` adds the total.
- `filter` isn't allowed in `find` (`-3978 "db: filter not allowed in find"`); `search`
  allows it, and always adds `count`.
- `incDel` with an `orderBy` fails: `-3978 "db: query order not compatible with where clause"`.
- A query with no `from`: code 22, `invalid parameters: caller='<app>' error='required property
  not found - 'from' for property 'query''`.

## Watches

- `find` with `"watch": true` returns its results, then `{"returnValue":true,"fired":true}`
  when anything the query could return changes, and ends. Enyo's `DbService` sends `watch`
  alone, without `subscribe`.
- The `watch {query}` method replies `{"returnValue":true}`, then fires the same way.
- Watches are one-shot: apps query again after `fired`.

## Not yet

`getProperties` doesn't exist on the TouchPad either. Not built: `compact`, `dump` and
`load`, quotas, `purge` by window, `putPermissions` for types other than `db.kind`, db8's
collation beyond Java's `Collator`, and the system kinds other services fill (the media
indexer's `com.palm.media.*`, which Papyrus reads before its file picker).

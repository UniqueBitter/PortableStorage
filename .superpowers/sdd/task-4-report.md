# Task 4 Report: StorageDb + StorageDao (upsert/批量)

## Status
COMPLETE — all 3 tests pass, committed.

## Commit
`cfd7a00` — feat: StorageDb + StorageDao upsert/批量 (TDD, 真实sqlite)

## Files Delivered
- `src/main/java/com/portablestorage/db/StorageDb.java`
- `src/main/java/com/portablestorage/db/StorageDao.java`
- `src/test/java/com/portablestorage/db/StorageDaoTest.java`

## TDD Steps
1. Wrote failing test — confirmed FAIL (compilation error: classes not found)
2. Implemented `StorageDb.java` with WAL, table creation, index
3. Implemented `StorageDao.java` with upsert, upsertBatch, countOf
4. Ran `./gradlew spotlessApply --console=plain` — BUILD SUCCESSFUL (formatted 3 files)
5. Ran `./gradlew test --tests "com.portablestorage.db.StorageDaoTest" --console=plain` — BUILD SUCCESSFUL

## Test Output
```
> Task :test
BUILD SUCCESSFUL in 5s
12 actionable tasks: 3 executed, 9 up-to-date
```
3/3 tests passed:
- `upsertMergesSameKey` — two upserts of same key accumulate count (5+3=8)
- `differentNbtHashAreSeparate` — different nbt_hash values remain distinct rows
- `batchInsert` — batch of 3 items with same key accumulates (2+2+2=6)

## SQLite ON CONFLICT Syntax
`ON CONFLICT(player,item,meta,nbt_hash) DO UPDATE SET count=count+excluded.count` worked without issues — sqlite-jdbc 3.45.3.0 bundles SQLite ≥ 3.24 which supports this UPSERT syntax natively.

## Concerns
None. Implementation is straightforward. `setAutoCommit` state is always restored in `finally` block for batch operations.

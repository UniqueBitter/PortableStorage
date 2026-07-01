# Task 3 Report: StoredItem DTO + nbtHash

## Status
COMPLETE

## Commit Hash
c7e71db

## Test Output
```
BUILD SUCCESSFUL in 4s
12 actionable tasks: 3 executed, 9 up-to-date
```
2 tests passed: `nbtHashStableAndDistinct`, `withCountCopies`

## TDD Steps Followed
1. Wrote `StoredItemTest.java` first — confirmed BUILD FAILED (compilation error: symbol `StoredItem` not found).
2. Implemented `StoredItem.java` with all required fields, constructor, getters, `withCount()`, and `hashNbt()`.
3. Ran `./gradlew spotlessApply` — formatting applied (constructor args reflowed to single line with indent, SHA-1 chain reflowed).
4. Ran tests — BUILD SUCCESSFUL, 2/2 tests pass.
5. Committed both files on branch `impl`.

## Files
- `src/main/java/com/portablestorage/core/StoredItem.java`
- `src/test/java/com/portablestorage/core/StoredItemTest.java`

## Concerns
- None. No Minecraft imports were used. SHA-1 is from `java.security.MessageDigest` (Java 8 standard library).
- `byte[] nbt` field is stored by reference (not defensively copied) — consistent with the task spec which does not require deep immutability for the array field.

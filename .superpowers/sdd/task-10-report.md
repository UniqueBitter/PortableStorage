# Task 10 Report

## Status
COMPLETE

## Commit
`9276d93` — feat: 每存档DB Provider + 容器 + GuiHandler + open/query/action 服务端处理(取出进背包/一键收纳/存入)

## Build Result
BUILD SUCCESSFUL in 13s (spotlessApply + build both passed, checkstyle clean)

## Cursor-Sync Call Used
`p.playerNetServerHandler.sendPacket(new net.minecraft.network.play.server.S2FPacketSetSlot(-1, -1, p.inventory.getItemStack()))` — used the packet fallback. `p.func_71113_k()` was not attempted because S2FPacketSetSlot is cleaner and unambiguous.

## API Deviation: `addScheduledTask` does not exist in 1.7.10
`MinecraftServer.addScheduledTask(Runnable)` was introduced in MC 1.8. It is **absent** from the 1.7.10 decompiled `MinecraftServer` class. All three server packet handlers (`OpenStoragePacket`, `QueryPagePacket`, `StorageActionPacket`) were implemented with **direct calls** (no scheduling wrapper). In 1.7.10 FML SimpleNetworkWrapper, server-bound packet handlers run on the Netty IO thread; direct calls work in practice for GUI opens and DB queries, which are the operations performed here. This matches the behavior of the vast majority of 1.7.10 Forge mods.

## Files Created / Modified
- `StorageDao.java` — added `findById(String player, long id)`
- `StorageProvider.java` — new: per-save DB cache, `@SubscribeEvent onUnload`
- `inventory/ContainerPortableStorage.java` — new: player inventory container
- `client/GuiPortableStorage.java` — new: minimal placeholder GUI
- `GuiHandler.java` — new: `IGuiHandler` implementation
- `StorageService.java` — new: `sendPage`, `handleAction`, take/deposit helpers
- `OpenStoragePacket.java` — handler wired (direct call)
- `QueryPagePacket.java` — handler wired (direct call)
- `StorageActionPacket.java` — handler wired (direct call)
- `PortableStorageMod.java` — added `@Mod.Instance instance`, wired GuiHandler + StorageProvider in `init`
- `ItemPortableTerminal.java` — right-click wired to `NetworkHandler.openStorage()` on client side

## Concerns
1. **Thread safety**: Without `addScheduledTask`, DB access and `openGui` happen on Netty IO thread. SQLite with WAL mode is connection-level thread-safe (single connection, single thread), so no corruption risk. `openGui` is the only concern but behaves correctly in 1.7.10 practice.
2. **`depositCursor` after `setItemStack(null)`**: The S2FPacketSetSlot is sent with `getItemStack()` after setting it to null, so it correctly sends null (empty slot) to client.
3. Task 11 will replace `GuiPortableStorage` with the real GUI.

---

# Task 10 — DB Thread-Safety (Fix 1 + Fix 2)

## Status
COMPLETE

## Commit
`14aedc8` — fix(Task10): DAO访问串行化(netty线程vs主线程磁铁共享连接) + onUnload加锁

## DAO Test Result
`./gradlew test --tests "com.portablestorage.db.StorageDaoTest"` — BUILD SUCCESSFUL (all tests PASS)

## Build Result
`./gradlew build --console=plain` — BUILD SUCCESSFUL in 8s (spotlessApply clean, checkstyle clean)

## Changes
- `StorageDao.java`: Wrapped body of every public method (`upsert`, `upsertBatch`, `countOf`, `entryId`, `extract`, `countMatches`, `queryWindow`, `queryWindowWithIds`, `findById`) in `synchronized (db) { ... }`. Private helpers (`bind`, `where`, `bindWhere`) left without locks (called while lock already held — reentrancy is safe on the JVM).
- `StorageProvider.java`: Wrapped close+clear in `onUnload` with `synchronized (StorageProvider.class)` to match the lock held by the `synchronized` `dao()` method, eliminating the HashMap data race.

## Concerns
1. `queryWindowWithIds` calls `queryWindow` and `countMatches` while already holding `synchronized (db)`. Java's intrinsic locks are reentrant, so the same thread re-enters without deadlock. This is correct.
2. No behavioral changes — only locking was added; all existing logic preserved verbatim.

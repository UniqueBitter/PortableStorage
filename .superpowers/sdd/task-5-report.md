# Task 5 Report: DAO 取出/搜索窗口/计数

## Status
COMPLETE

## Commit
`556156a` — feat: DAO 取出/搜索窗口/计数, 拼音匹配 (TDD)

## Test Summary
6/6 tests PASS (3 existing + 3 new: `extractDecrementsAndDeletesAtZero`, `searchMatchesNameLorePinyin`, `windowPaginates`)

## Methods Added to StorageDao
- `entryId(player, item, meta, nbtHash)` → row id or -1
- `extract(player, id, amount)` → deducts min(amount, have); deletes row at zero; returns amount taken
- `where(keyword)` / `bindWhere(ps, player, keyword)` — private helpers for keyword WHERE clause
- `countMatches(player, keyword)` → COUNT(*) matching keyword in name/lore/pinyin/pinyin_in
- `queryWindow(player, keyword, offset, limit)` → paginated List<StoredItem> ORDER BY name, nbt=null

## Concerns
None. Spotless reformatted both files as expected (test file line-folded some assertions). All assertions verified correct: extract partial decrement, extract-to-zero delete, LIKE matching on all four columns, pagination boundary (offset=4 of 5 returns 1 item).

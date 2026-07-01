# Task 2 Report: PinyinUtil (中文→全拼/首字母)

## Status
DONE

## Commit Hash
50439e1

## Test Command
```
./gradlew test --tests "com.portablestorage.core.PinyinUtilTest" --console=plain
```

## Test Output
```
> Task :compileJava
Jabel: initialized

> Task :classes

> Task :compileTestJava
Jabel: initialized

> Task :testClasses
> Task :test

BUILD SUCCESSFUL in 2s
12 actionable tasks: 3 executed, 9 up-to-date
```

**3 tests passed, 0 failed.**

Tests:
- `fullPinyinOfChinese`: `PinyinUtil.fullPinyin("钻石")` → `"zuanshi"` ✓
- `initialsOfChinese`: `PinyinUtil.initials("钻石")` → `"zs"` ✓
- `keepsAsciiLowercased`: `PinyinUtil.fullPinyin("TNT")` → `"tnt"`, `PinyinUtil.initials("TNT")` → `"tnt"` ✓

## TDD Steps Followed
1. Wrote failing test `PinyinUtilTest.java` — build failed with "找不到符号 PinyinUtil" (class not found) as expected.
2. Confirmed failure.
3. Implemented `PinyinUtil.java` using pinyin4j `PinyinHelper.toHanyuPinyinStringArray`.
4. Ran `./gradlew spotlessApply` (reformatted both files), then ran tests — BUILD SUCCESSFUL, 3/3 passed.
5. Committed both files.

## Pinyin4j Output Verification
pinyin4j 2.5.1 returned the expected romanizations:
- 钻 → `zuan`
- 石 → `shi`

## Concerns
None. All expected values matched pinyin4j output exactly.

# PortableStorage 执行进度

计划: docs/superpowers/plans/2026-06-28-portable-storage.md
分支: impl

- Task 1: complete (review: 控制器自验, build+runClient 通过, jar生成, 游戏内加载确认)
- Task 2: complete (commit 50439e1, review clean; Minor: CJK 范围仅 U+4E00–9FFF, 罕见扩展区字符优雅降级)
- Task 3: complete (commit c7e71db, review clean; Minors: catch(Exception)过宽, byte[]按引用存, withCount测试未断言全字段)
- Task 4: complete (commit cfd7a00, review clean; 列/bind 对齐已核; Minor: 测试残留 .db-wal/-shm 临时文件)
- Task 5: complete (commit 556156a, review clean; 索引算术已核; id列由Task9 queryWindowWithIds补)
- Task 6: complete (commit 61611d0, review clean)
- Task 7: complete (commit 97c6eb3, review clean; 静态通配import经全量build验证checkstyle放行)
- 额外: 修复Task4/5通配import (commit 见 fix); 全量 ./gradlew build 绿
- Task 8: complete (commits adc38e4 + f66bb8a修复; review找出Critical: decode丢NBT, 已修为Option B重建+保留tag, 选B而非plan字面loadItemStackFromNBT因后者会把Count并入hash破坏堆叠; 另加注册名null防护+搜索名去色码)
- Task 9: complete (commit d38cf68, build绿; PageResult/Action序列化对称已亲验; handler为stub待Task10)
- Task 10: complete (commits 9276d93 + 14aedc8修复; Important: onUnload竞态+DAO跨线程, 已串行化synchronized(db); deviation: 1.7.10无addScheduledTask, handler直接在netty线程跑(已加锁防护))
- 冒烟测试(runClient, Task10后): 模组成功加载(preInit/init/4 mods, 无我方代码异常)。无关问题: headless OpenAL声音崩溃(环境); FML扫描sqlite-jdbc多版本类META-INF/versions/9报IllegalArgumentException(ASM5.0.3读不了J9类,dev无害)。
- **Task14待办**: shade时必须排除 META-INF/versions/** 与 module-info.class, 否则FML的ASM扫描可能在最终jar上报错/不加载。
- Task 11: complete (commit 4f9fa38, build绿, 无API适配; 控制器逻辑审: 点击索引/滚动clamp/volatile页面主线程应用 均OK; 视觉留待Task15用户验)
- Task 12: complete (commit 79f675e, build绿; 键位B/K+SidedProxy+ClientProxy, boilerplate符合spec)
- Task 13: complete (commit e34eb16, build绿; delayBeforeCanPickup编译OK; 控制器逻辑审: 磁铁不丢物(setDead前已全路由,溢出回存), 档位PlayerPersisted持久化; Minor: dao()每物每tick新建wrapper(轻量), Ack聊天在netty线程(无害))
- === 全部13个实现任务完成 ===
- Task 13(补记): review通过(逻辑审, 见上)
- Task 14: complete (commit 见上, 控制器直做+静态验证)。关键发现并修复3个会导致最终jar运行崩溃的打包问题:
  1) minimizeShadowedDependencies=true 把反射加载的sqlite驱动类删光 → 改false(120个sqlite类回归)
  2) META-INF/versions/9多版本类 → 排除(否则FML ASM5.0.3扫描自身jar抛IllegalArgumentException不加载)
  3) Class.forName("org.sqlite.JDBC")字符串不被重定位 → 改 Class.forName(org.sqlite.JDBC.class.getName()) 类字面量(已验证shaded jar里指向 com/portablestorage/shadow/org/sqlite/JDBC)
  另: preInit设org.sqlite.tmpdir到游戏目录(原生库可写)。
  注意: dev runClient用未shade的sqlite, 无法验证shaded路径; 真正验证在Task15装入真实整合包。
- 最终全分支审查(opus): 集成正确(动作码/包方向/物品流均OK)。发现并修复 C1(decode未知物品NPE→返回null+取出前探测) + I1(磁铁档位NBT跨线程→加锁)。commit c28d0c3, test+build绿。
- Task 15: 安装完成。最终jar(含全部修复)已装入真实整合包 mods/PortableStorage-1.0.0.jar。
  注: 编译/checkstyle/单测/dev加载/shaded jar结构 均已验证; GUI视觉/滚动手感/shaded-sqlite运行期/磁铁/存档 需用户实机验证(见交付清单)。
- === 全部15任务完成, 待用户实机验收 ===
- 实机崩溃#1: UnsatisfiedLinkError com.portablestorage.shadow.org.sqlite.core.NativeDB._open_utf8。
  根因: sqlite-jdbc是JNI库, shade重定位改了Java包名但原生.dll导出的JNI符号名仍是Java_org_sqlite_*, 对不上→链接失败。
  修复: relocateShadowedDependencies=false (用原始org.sqlite包名打包, 符号匹配)。commit aaaf197, 已重装。
- 实机崩溃#2: ClassNotFoundException org.slf4j.LoggerFactory @ org.sqlite.JDBC.<clinit>。
  根因: sqlite-jdbc 3.43+改用slf4j日志, MC1.7.10无slf4j。修复: 降到3.42.0.0(用java.util.logging, javap验证无slf4j引用)。commit见上, 已重装。
- 实机反馈轮1: ①UI丑(扁平灰块)→重绘MC立体格子+浅面板(含玩家背包/快捷栏槽位+美化滚动条); 参考了GTNH里AE2 terminal.png布局但用程序绘制(避免UV对齐与素材版权)。
  ②Shift左键不能存取→实现 transferStackInSlot 把背包物品存入仓库, StorageService加refresh()按last keyword/offset刷新不丢搜索。
  ③磁铁无效→去掉 delayBeforeCanPickup>0 跳过, 改即时拾取。
  (仓库已能正常打开/存取/显示, 说明shaded sqlite运行OK。)
- 实机反馈轮2: ①仓库里RPG物品只显示基础名(如"铁剑")丢了自定义名/时装/属性 → PageResultPacket增加nbt[][], queryWindow读nbt, GUI buildStack还原tag+用getTooltip显示完整tooltip(含元素之神长剑等自定义名与时装外观)。②磁铁默认半径8→32(代码默认+用户现有配置文件均改)。
- 实机反馈轮3: ①取出改AE光标式(左键取组/右键取1到鼠标, Shift左键进背包) ②AE排序按钮(名字/数量/类型, 服务端ORDER BY, 随分页/搜索保持; R键与搜索框冲突故用可点按钮) ③磁铁区分作者不可拾取物(32767无限延迟+invulnerable) ④左上角加标题"随身仓库"。
- 实机反馈轮4: 加入"自动补充"——快捷栏(0-8)堆叠用尽(非空→空)时自动从仓库取同种补满。RestockManager监听PlayerTickEvent, 用每玩家上tick快照检测用尽; 打开仓库界面时跳过(防与存入打架); Config.autoRestock默认true可关。
- 实机崩溃#3: 丢箭→磁铁tick中首次懒加载 com.portablestorage.core.MagnetRouter, 触发 InventoryTweaks ContainerTransformer 在 ClassReader.<init> NPE → NoClassDefFoundError 崩服。根因是InvTweaks的ASM变换器在玩家tick上下文懒加载类时出错(init上下文加载的类都正常)。修复: ①init里预加载磁铁/补充tick路径全部类; ②MagnetManager/RestockManager的onTick包try/catch(Throwable)兜底, 永不崩游戏(首次记一次日志)。
- 实机反馈轮5: ①排序持久化(存玩家PlayerPersisted NBT psSortMode 0..5; 打开界面发sort=-1请求记忆值, 服务端PageResult回传resolved sort, 客户端采用并更新按钮; 随存档/重启/重开都保留) ②抄AE: 排序拆成 sortBy(名字/数量/类型) + 方向(升/降) 两个按钮, sort编码=by*2+desc。
- 实机反馈轮6: ①一键收纳加锁定格功能——中键点背包格切换锁定(LockSlotPacket→toggleLock存psLockedSlots位掩码NBT, 持久化; PageResult带lockMask; GUI金边渲染; quickDepositAll跳过锁定位)。②搜索框加自动/手动切换(手动模式回车才搜)。③按钮全部移到AE风格左侧悬浮小按钮列(收/名数类/升降/自手, 带悬停tooltip), 玩家背包上移到top=116紧凑布局, ySize 222→200。

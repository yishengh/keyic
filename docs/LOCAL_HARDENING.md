# Keyic 本地完善清单（2026-10-07）

## 边界与基线

- 产品：离线 Android 密码管理器；保留现有登录、笔记、卡片、身份、TOTP、多保险库、附件和导入导出。
- 基线：`c27fcee`；开始时仅 `.idea/vcs.xml` 未跟踪，保留不动。未发现适用的 AGENTS.md 或项目 .cursor 规则。
- 已读取 README、备份格式说明、模块代码和指定 Cursor 主记录。历史最后要求隐私站及 About/帮助；当前代码已有 AboutHelpScreen、设置入口、双语帮助及隐私链接，网站本地也已有双语文案。
- 只开发、验证；不发布、部署、推送、改签名。网站仅只读核查。

## 本阶段验收清单

1. 数据库打开、迁移/降级失败不得删除数据库；编辑条目不丢附件；多库切换不得复用另一库会话。
2. `.keyic` 恢复和 KeePass 导入先校验，条目与附件作为一个事务提交，失败保留原数据；明确恢复替换语义，拒绝未知格式，损坏附件不得默默遗漏。
3. 自动备份写入失败保留上一份备份，备份/导出不误报成功；导入输出有界，敏感临时缓冲清理。
4. 检查密钥包装、生物识别失败回退、解锁错误和自动锁定；所有敏感活动保护截图；剪贴板清理不依赖短生命周期 UI。
5. 自动填充只按精确域名/包名匹配合适条目类型，并由用户确认目标，锁定/切库后过期请求不泄露；跨库选择结果正确返回。
6. 设置、帮助、空状态、错误与关键无障碍信息中英文一致；口令输入遮罩；隐私外链失败反馈。
7. 构建、单元测试、Android lint，尽可能通过隔离模拟器及合成数据验证加密备份往返、错误密码/损坏输入、附件保留、迁移、锁定和关键 UI。

## 网站后续调整（仅记录，不修改网站）

- 隐私文案应区分 SQLCipher 数据库加密与 AES-GCM 密钥包装/附件/备份，避免把所有静态存储统称为 AES-GCM。
- “Keyic 向文件夹写入密文”需直接限定为加密备份/KeePass；CSV 是明文。
- 补充自动备份只有在保险库已解锁且存储提供商可用时执行，以及保留多个备份文件的行为。

## 验证结果

### 已落地的修复

- 删除数据库打开失败后自动删库重建、降级破坏性迁移；失败保持锁定并保留原库。新建库只回滚本次新建内容，已存在的数据不覆盖。
- Room 条目写入改用 Upsert，避免 REPLACE 触发外键级联删除附件。会话代次驱动数据订阅，切库、恢复和附件操作固定数据库及密钥配对。
- `.keyic` 恢复校验格式及引用、分阶段写入附件、事务提交；错误密码、缺文件、数据库失败不误报成功，不替换原数据。CSV 批量导入也使用事务。
- 自动备份创建独立命名文件，失败删除本次未完成文件，保留旧副本。修正 KeePass Android XML/编码依赖及完整字段往返，拒绝重复附件名造成的静默覆盖。
- 使用单调时钟计时，外部文件选择不再无限免锁；自动填充活动加安全窗口与空闲锁；生物识别取消恢复外部 UI 状态，密码可回退。敏感口令输入遮罩、临时数组清理，错误提示不直接显示异常详情。
- 自动填充禁止模糊域名/包名匹配及按最近使用回退，阻止混合网页域名，按字段类型筛选；确认目的地后重新检查会话及条目。保存请求创建新条目，不模糊覆盖现有密码。
- 应用级剪贴板计时，用随机标记识别自己写入的内容，不保存用于比对的明文；不删除用户随后复制的其他内容。
- 移除合并依赖带入的 INTERNET/ACCESS_NETWORK_STATE 权限。修复 About 返回/外链失败提示、保存成功才退出编辑、资源更新与可访问名称，补全所有已支持语言的缺失字符串。

### 本地证据

- JDK：Android Studio JBR 21；项目原有 Gradle/AGP 配置。使用 `--max-workers=1`；lint 使用 `--no-configuration-cache` 避免首次环境中的 lint 服务异常。
- `:core:test :data:testDebugUnitTest`：26 项通过（core 23，data 3）。XML 结果位于各模块 `build/test-results/`。
- `:app:assembleDebug :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest`：调试应用与测试 APK 构建通过，无发布构建。
- `:app:lintDebug`：0 错误、90 警告。报告 `app/build/reports/lint-results-debug.html`；没有通过禁用安全检查或新增 lint baseline 掩盖问题。警告主要为未用资源、依赖版本建议、排版和 Compose 建议；第三方 shaded jar 的 TrustAllX509TrustManager 警告仍保留，应用未使用网络且 APK 无 INTERNET 权限，未宣称第三方库已完成安全审计。
- 独立模拟器 `KeyicValidation`，Android 15/API35，数据全为合成。数据设备测试 13 项通过：v1/v2/v3 迁移、拒绝降级、备份 schema1/2/3 与未知版本、全类型字段和附件往返、错误口令及篡改、事务故障回滚、缺失附件失败、错误数据库密钥不删库、修改主密码、跨库过期导入拒绝、KeePass 往返、真实 SAF 写入失败保留旧副本。记录 `build/device-data-final.log`。
- Android 15 界面设备测试 6 项通过：创建条目 → 锁定 → 错误口令拒绝 → 正确解锁后数据保留；中英文设置/About 返回；剪贴板清理及外来内容保留；APK 无网络权限/系统备份；前台空闲及后台锁定。含原有包名测试，记录 `build/device-app-final.log`。
- 独立 Android 8/API26 模拟器 `KeyicValidation26`：同一数据设备测试 13 项全部通过，包含修复后的 KeePass Android 8 编码库兼容性。记录 `build/device-data-api26.log`。
- Android 8 界面测试：完整运行中 5 项通过，剪贴板用例因旧系统清空行为与新系统不同而失败；修正测试以核对 API26–27 的空内容覆盖（未更改正式安全逻辑），定向复测 1 项通过。证据 `build/device-app-api26.log` 和 `build/device-app-api26-clipboard.log`。累计 6 项均已验证，没有把原失败运行记为全通过。
- 最终调试 APK：`app/build/outputs/apk/debug/app-debug.apk`，SHA-256 `860D235A2AA3D0F391BA222421C4778948B4089A9F9D7AEC555EEBD6A52FE158`。构建总日志 `build/acceptance-build.log`；最后的测试 APK 重编译日志 `build/ui-test-package.log`。
- 初次连接测试受到共享 ADB 重启及主机 UTP 环境错误影响；这些运行不计成功。最终使用明确设备序列号的 `adb install` / `am instrument`，不清除用户设备数据、不重启共享 ADB。

### 明确的剩余边界

- 尚未在带真实指纹/面容硬件的实体设备上验证生物识别成功、密钥失效和厂商后台策略；模拟器不能替代这些验证。
- 自动填充匹配有单元测试覆盖，过期会话校验已审查；没有宣称完成所有浏览器、第三方应用或真实金融表单的端到端兼容验证。离线应用无法验证网站与原生应用的所有权关系，界面明确要求用户核对目标。
- 迁移测试按现有迁移代码重建旧版结构；仓库没有真实历史用户数据库样本，未接触用户保险库。
- SAF 通过本地 DocumentsProvider 测试写入与故障，未访问真实云盘或用户同步目录。旧备份由用户管理，暂不自动清理；恢复后可能保留无引用的加密附件文件以避免错误删除，后续可另行设计安全回收。
- 剪贴板在应用退到后台时受 Android 权限限制，回前台重试；进程被结束后不能保证自定义定时清除。帮助已说明，不降低系统限制。
- `.keyic` / KeePass / CSV 文件读取有上限；大型压缩 KeePass 的解压资源消耗仍依赖第三方库，本阶段未完成恶意压缩输入的全面模糊测试。推荐 `.keyic` 完整备份，CSV 不是无损备份。
- 无障碍名称和资源检查已做，未做完整 TalkBack 真机审查；新增日/韩/西/法翻译未经过母语人工校对。
- 隐私网站未修改、未部署；所需变更仅记录在本文件。没有推送、发布、付费操作或变更发布签名身份。原有 `.idea/vcs.xml` 保持未跟踪。


### 复现命令（PowerShell）

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest :core:test :data:testDebugUnitTest :app:lintDebug --max-workers=1 --no-configuration-cache --console=plain
```

仅在专用合成数据模拟器中安装/运行测试，不能对用户正在使用的 Keyic 安装直接执行应用测试（测试会创建并切换合成保险库）。先用 `adb -s <serial> emu avd name` 核实设备，再分别安装 APK：

- `data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk`
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

运行 `adb -s <serial> shell am instrument -w com.yishenghuang.keyic.data.test/androidx.test.runner.AndroidJUnitRunner` 和 `adb -s <serial> shell am instrument -w com.yishenghuang.keyic.test/androidx.test.runner.AndroidJUnitRunner`。断言包含 SQLCipher 真正重开、解密读取与注入写入失败，不仅检查接口返回成功。

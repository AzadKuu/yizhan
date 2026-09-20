# 驿站（Yizhan）

Paper 1.21.x 跨服物资运输插件。把物品从一个服务器的容器，经 MySQL 中继，**单向**运送到另一个服务器的容器。

物品不会立刻到达：发货后进入在途状态，经过可配置的缓冲时间，才投递到目标驿站，模拟驿站发件的感觉。

## 特性

- **虚拟容器**：驿站是一个绑定在方块上的虚拟容器，内容存在数据库里，不占用原版箱子
- **命令绑定位置**：`/yizhan bind` 把准星指向的方块注册为驿站
- **单向路由 + 缓冲时间**：`发送站 -> 接收站`，缓冲时间可按路由或按驿站配置
- **MySQL 中继**：HikariCP 连接池，事务 + version 乐观锁 + 投递状态 CAS，多子服同时运行不会重复投递或复制物品
- **非原版物品拦截**：按 `custom_data` / PersistentDataContainer 的命名空间做黑名单校验，默认拦截 Nexo、ItemsAdder 等自定义物品
- **收件箱容量保护**：发货前预检「目标收件箱已占 + 在途包裹」是否会超容量，不足直接拦下并提示清理；发出后目标被塞满则一直重试投递并通知发件人，超过 `shipment.discard-after-hours`（默认 24 小时）仍投不进则把物品暂存到丢弃仓库，管理员用 `/yz discarded` 领取，不会丢件
- **邮箱暂存不丢件**：收件人邮箱满时，放不下的邮件进入待领取队列，清理邮箱后点邮箱界面的「重新领取」即可补入，不会消失
- **发货与到货通知**：发货、投递完成、目标满等待、超时丢弃都会给相关玩家发消息；玩家不在线时消息入库，下次在任意子服上线自动补发
- **玩家邮箱**：每个玩家用 `/yizhan mailbox bind` 绑定自己的邮箱方块，右键即可打开独立的邮箱，物品按 UUID 存库，天然跨服
- **跨服发奖**：`/yizhan mail send` / `/yizhan mail give` 可把物品直接投递到任意玩家（含离线、含其他子服）的邮箱；`mail give` 支持自定义通知消息，方便命令方块发奖时附带活动说明
- **每日奖励**：玩家每天首次登录时，自动把奖励投递到自己的邮箱（原版材质 + 登记模板物品两种来源叠加）
- **快递费**：路由可配置快递费，发货界面有独立费用槽，放入匹配 `currency-key`（可选 `currency-value`）的货币物品才能发货

## 环境要求

| 项目 | 要求 |
|---|---|
| 服务端 | Paper 1.21.x |
| Java | JDK 21 |
| 数据库 | MySQL 5.7+ / 8.x，多个子服共用一个库 |
| 构建 | Maven 3.8+，JDK 21 |

> 建议各子服的 Paper 版本保持一致。物品以 Paper 的 NBT 二进制格式存储，跨大版本可能存在兼容差异。

## 构建

```bash
mvn -B -DskipTests package
```

产物：`target/Yizhan-1.0.0.jar`

如果本机 `mvn` 默认使用 JDK 17，需要先指定 JDK 21：

```powershell
$env:JAVA_HOME = "C:\Program Files\Zulu\zulu-21"; mvn -B -DskipTests package
```

```bash
export JAVA_HOME=/path/to/jdk-21 && mvn -B -DskipTests package
```

## 安装

1. 创建数据库（表由插件启动时自动创建）：

   ```sql
   CREATE DATABASE yizhan DEFAULT CHARACTER SET utf8mb4;
   ```

2. 把 `Yizhan-1.0.0.jar` 放入每个子服的 `plugins/` 目录。

3. 启动一次生成配置，然后编辑 `plugins/Yizhan/config.yml`：
   - 填写 `database` 连接信息（所有子服填同一套）
   - **每个子服把 `server-id` 改成不同的值**，例如 `survival-1`、`survival-2`

4. 重启服务器，控制台出现 `Yizhan 已启用, server-id=...` 即为成功。

## 快速开始

假设 `survival-1` 有一个发货站，`survival-2` 有一个收货站：

```text
# 在 survival-1，准星指向要绑定的方块
/yizhan bind station_a send

# 在 survival-2，准星指向要绑定的方块
/yizhan bind station_b receive

# 建立单向路由，缓冲 300 秒（在任意子服执行）
/yizhan route station_a station_b 300
```

之后在 `survival-1` 右键 `station_a` 的方块：

1. 打开发货区，放入物品
2. 点击底部的「点击发货」按钮
3. 包裹进入在途状态，立刻收到「发货成功」提示
4. 300 秒后自动投递到 `survival-2` 的 `station_b` 收件箱，发出「包裹已到达」提示（发件人离线则下次上线补发）
5. 在 `survival-2` 右键 `station_b`，领取收件箱里的物品

## 命令

主命令 `/yizhan`，别名 `/yz`、`/yizhanstation`。

| 命令 | 说明 |
|---|---|
| `/yizhan bind <名称> <send\|receive\|both>` | 把准星指向的方块（6 格内）绑定为驿站 |
| `/yizhan unbind <名称>` | 解绑驿站，并清空其收件箱 |
| `/yizhan route <起点> <终点> [缓冲秒] [快递费]` | 建立或更新单向路由，省略秒数则用默认缓冲 |
| `/yizhan route remove <起点> <终点>` | 删除路由 |
| `/yizhan fee <起点> <终点> <数量>` | 设置路由的快递费（需 `yizhan.route`） |
| `/yizhan buffer <驿站> <秒>` | 单独设置某个驿站的缓冲时间 |
| `/yizhan open <名称>` | 远程打开驿站容器 |
| `/yizhan list` | 列出所有驿站 |
| `/yizhan info <名称>` | 查看驿站详情 |
| `/yizhan mailbox <bind\|unbind\|info>` | 绑定/解绑/查看自己的邮箱方块（需 `yizhan.mail`） |
| `/yizhan mail send <玩家> [数量]` | 把主手物品发到目标玩家邮箱，省略数量则发整组 |
| `/yizhan mail give <玩家> <物品ID\|代号> <数量> [消息]` | 发送指定物品到目标玩家邮箱；`<物品ID>` 可填原版材质名或 `/yz item add` 登记的代号；可选 `[消息]` 自定义通知内容（支持 `&` 颜色码和 `%amount%` `%item%` 占位符），不填则用 `messages.mail-received` |
| `/yizhan dailyreward add` | 把主手物品登记为每日奖励模板（支持 Nexo 等自定义物品，需 `yizhan.admin`） |
| `/yizhan dailyreward list` | 查看已登记的每日奖励模板 |
| `/yizhan dailyreward remove <槽位>` | 移除某个登记的每日奖励模板 |
| `/yizhan dailyreward clear` | 清空全部登记的每日奖励模板 |
| `/yizhan item add <代号>` | 手持物品登记为可按代号发放的模板（需 `yizhan.admin`） |
| `/yizhan item list` | 列出已登记的物品模板代号 |
| `/yizhan item remove <代号>` | 删除某个物品模板 |
| `/yizhan discarded` | 打开丢弃物品仓库 GUI，取出投递超时被暂存的物品（需 `yizhan.admin`） |
| `/yizhan debugitem` | 诊断主手物品：打印 PDC / `custom_data` 键值对、CustomModelData 与拦截判定（需 `yizhan.admin`） |
| `/yizhan reload` | 重载配置文件 |

驿站名称只允许字母、数字、下划线和短横线，长度 1-32。

`mail send` / `mail give` 的玩家参数支持**玩家名或 UUID**。玩家名只能解析到本子服有记录的玩家；如果目标玩家从未进过本子服，请直接填 UUID。

## 玩家邮箱

每个玩家绑定自己的邮箱方块，各自独立：

1. 玩家对准方块执行 `/yizhan mailbox bind`，把这个方块设为自己在该子服的邮箱方块（每台子服需各自绑定）
2. 右键该方块，打开的是**自己的**邮箱（45 格）
3. 邮箱内容按玩家 UUID 存库，因此所有子服共用同一份数据 —— 在 A 服收到的邮件，到 B 服打开同样能看到
4. 关闭界面时自动保存

投递到邮箱的途径有三种：`/yizhan mail send`、`/yizhan mail give`、每日奖励。

## 每日奖励

玩家每天首次登录时，自动把奖励投递到自己的邮箱（离线期间不补发）。奖励有两个来源，**会叠加发放**：

1. **配置原版材质**：在 `daily-reward.items` 里按 `material` + `amount` 配，适合原版物品
2. **登记模板物品**：管理员手持真实物品执行 `/yizhan dailyreward add`，插件把该物品完整序列化存库，每天照发一份。适合 Nexo / ItemsAdder 等自定义物品（无法用材质名描述）

登记的模板物品存在 `yz_daily_rewards` 表，用 `/yizhan dailyreward list` 查看、`remove <槽位>` 删除、`clear` 清空。两个来源都为空时不发也不标记领取。

## 快递费

快递费**按数量计费**，金额配置在路由上：

```text
/yizhan fee station_a station_b 5        # 这条路由每次发货需要 5 个货币物品
/yizhan route station_a station_b 300 5  # 建路由时一并设置
```

发货界面的**费用槽（底部左起第 2 格）**用于放货币物品。判定货币的方式：

- 物品的 PDC / `custom_data` 键名等于 `ship-fee.currency-key`（默认 `currency`）
- 若 `ship-fee.currency-value` 留空，只要键名匹配即视为货币（任何带该键的物品都算）
- 若 `ship-fee.currency-value` 填了值，则**键和值都必须匹配**才算货币

Nexo 等插件常让多个物品共用同一个键、靠值区分（例如 `nexo:item_id` 键下分别是 `coin`、`gem`…），这种情况下 `currency-value` 必须填，否则会把所有同类物品都误判为货币。用 `/yizhan debugitem` 可查看手持货币物品的实际键值。例如：

```text
/give @p minecraft:gold_nugget[minecraft:custom_data={currency:1}]
```

发货时从费用槽扣除固定个数，不足则禁止发货并提示。**费用槽里的物品不参与运输**，未发货就关闭界面会原样退回背包；发货后剩余的数量也会退回。

## 权限

| 权限 | 默认 | 说明 |
|---|---|---|
| `yizhan.bind` | op | 绑定 / 解绑驿站 |
| `yizhan.route` | op | 管理路由与缓冲时间 |
| `yizhan.open` | true | 打开驿站容器 |
| `yizhan.mail` | true | 右键邮箱方块打开自己的邮箱、绑定自己的邮箱方块 |
| `yizhan.mail.send` | op | 用 `/yizhan mail` 给其他玩家邮箱发物品 |
| `yizhan.admin` | op | 重载配置、管理每日奖励等管理操作 |

## 配置说明

```yaml
server-id: "survival-1"          # 本子服标识，每个子服必须不同
debug: false                     # 开启后输出拦截/发货等诊断日志到控制台
poll-interval-seconds: 3         # 投递轮询间隔（秒）
default-buffer-seconds: 300      # 默认缓冲时间（秒）
allow-cancel-shipment: false     # 预留项，当前版本未在界面提供取消入口
default-station-size: 27         # 新建驿站的虚拟容器容量（9 的倍数，最大 45）
max-station-size: 45             # 预留项，当前版本仅做上限换算，未参与校验
language:
  prefix: "&8[&6驿站&8] &r"      # 消息前缀，支持 & 颜色代码
messages:
  ship-start: "&a发货成功 &7#%id% &7目的地 &f%to% &7预计 &f%buffer%&7后到达"
  ship-arrived: "&a你的包裹 &7#%id% &a已到达 &f%station% &a，请前往领取"
  ship-waiting: "&e目标驿站 &f%station% &e已满，包裹 &7#%id% &e正在等待空位，请提醒接收方清理收件箱"
  ship-discarded: "&c目标驿站 &f%station% &c已满超过 &f%hours% &c小时，包裹 &7#%id% &c投递失败已暂存，请联系管理员领取"
database:
  host: "127.0.0.1"
  port: 3306
  database: "yizhan"
  user: "root"
  password: "change_me"
  table-prefix: "yz_"            # 表名前缀
  pool-size: 6                   # HikariCP 连接池大小
  use-ssl: false
  connection-timeout-ms: 10000
item-filter:
  blocked-namespaces:            # 命中这些命名空间的自定义数据即拦截
    - "nexo"
    - "itemsadder"
    - "ia"
  blocked-keys: []               # 精确拦截，例如 "nexo:item_id"
  allowed-items: []              # 白名单，优先级最高；按 key + value 精确放行，例如见下方
  blocked-materials: []          # 原版物品黑名单，按材质名，例如 ["paper","diamond_sword"]
  block-custom-model-data: false # 是否额外拦截带 CustomModelData 的物品
mailbox:
  size: 45                       # 每个玩家独立邮箱的容量（9-45，会向上取整到整行）
daily-reward:
  enabled: false                 # 每天首次登录时自动投递到玩家邮箱（离线期间不补发）
  message: "&a每日奖励已发放到你的邮箱"
  items: []                      # 原版物品，形如 [{material: "diamond", amount: 1}]；自定义物品用 /yizhan dailyreward add 登记
ship-fee:
  currency-key: "currency"       # 快递费货币的识别键名（PDC / custom_data）
  currency-value: ""             # 留空只按键名判定；填值后必须键和值都匹配（Nexo 共用键名靠值区分时必填）
shipment:
  discard-after-hours: 24        # 目标驿站满后一直重试，超过该小时数仍投不进则物品暂存到丢弃仓库（最小 1）
```

**缓冲时间优先级**：路由设置 > 驿站设置 > `default-buffer-seconds`。

> 升级提示：服务器上**已存在**的 `config.yml` 不会自动补齐新增配置段（代码有内置默认值，运行不受影响）。如需调整 `mailbox`、`daily-reward`、`ship-fee`，请手动把对应段落补进配置文件，然后 `/yizhan reload`。

**消息占位符**：`ship-start` 支持 `%id%`、`%to%`、`%buffer%`；`ship-arrived` 支持 `%id%`、`%station%`；`ship-waiting` 支持 `%id%`、`%station%`；`ship-discarded` 支持 `%id%`、`%station%`、`%hours%`。所有模板都支持 `&` 颜色代码。

## 物品过滤

插件读取物品 `ItemMeta` 的 PersistentDataContainer（对应 1.20.5+ 的 `minecraft:custom_data` 组件），逐个检查 key：

- key 的命名空间命中 `blocked-namespaces` 时拦截
- key 的完整字符串命中 `blocked-keys` 时拦截
- 物品材质名命中 `blocked-materials` 时拦截（原版物品也可拦，如 `paper`、`diamond_sword`）
- `block-custom-model-data: true` 且物品带 CustomModelData 时拦截

**白名单优先**：`allowed-items` 中的条目**优先于以上全部黑名单规则**，命中即放行。每个条目由 `key` 和 `value` 组成，两者都相等（key 忽略大小写，value 区分大小写）才生效。它对 PersistentDataContainer 与 `custom_data` 两条匹配路径都适用：

```yaml
item-filter:
  blocked-namespaces:
    - "nexo"
  allowed-items:
    - key: "nexo:item_id"   # 黑名单会拦下所有 Nexo 物品
      value: "coin"         # 但只放行这一个
```

拦截发生的时机有两处，双重校验：

1. 物品放入发货区的瞬间
2. 点击「发货」时的全量扫描

默认放行，只有配置中列出的标记才会被拒绝。

**排查物品键值**：手持目标物品执行 `/yizhan debugitem`，会直接打印该物品的 PDC 键值对与 `custom_data` 键值对（形如 `nexo:item_id = "coin"`）、CustomModelData 和拦截判定。把命名空间补进 `blocked-namespaces` 即可拦截，或把 `key` / `value` 填进 `allowed-items` 精确豁免。开启 `debug: true` 后，拦截与发货事件也会写入控制台日志。

## 数据表

插件启动时自动创建（表名带 `table-prefix`）：

| 表 | 用途 |
|---|---|
| `yz_stations` | 驿站定义：位置、模式、容量、缓冲、version |
| `yz_routes` | 单向路由：起点、终点、可选缓冲、快递费 |
| `yz_shipments` | 发货单：状态、出发时间、到达时间、发件人 |
| `yz_shipment_items` | 在途包裹内的物品快照 |
| `yz_station_items` | 驿站收件箱内的物品 |
| `yz_notifications` | 玩家待发送通知，用于离线 / 跨服补发 |
| `yz_mailbox_items` | 各玩家邮箱内的物品，按 `player_uuid` + `slot` 组织 |
| `yz_daily_claims` | 每日奖励领取记录，按玩家记录最后领取日期 |
| `yz_daily_rewards` | 登记的每日奖励模板物品（`/yizhan dailyreward add` 存入），按 `slot` 组织 |
| `yz_mailbox_overflow` | 邮箱满时暂存的待领取邮件，按写入顺序（FIFO）补入邮箱 |
| `yz_discarded_items` | 投递超时丢弃的物品暂存，管理员用 `/yz discarded` 领取 |
| `yz_item_templates` | 可按代号发放的自定义物品模板，`/yz item add` 登记，`/yz mail give <玩家> <代号> <数量>` 发放 |
| `yz_player_mailbox_blocks` | 各玩家在各子服的邮箱方块绑定位置 |

物品以 `ItemStack#serializeAsBytes()` 序列化后存 `LONGBLOB`。

## 运行机制

**发货**：玩家点击「发货」时，插件创建一条 `yz_shipments` 记录（`arrive_at = 当前时间 + 缓冲秒`），并把发货区物品写入 `yz_shipment_items`。物品随后从发货区移除。

**投递**：每个子服有一个异步定时任务，按 `poll-interval-seconds` 扫描 `status='IN_TRANSIT'` 且 `arrive_at` 已到的发货单。投递前做状态 CAS：

```sql
UPDATE yz_shipments SET status='DELIVERED' WHERE id=? AND status='IN_TRANSIT'
```

只有受影响行数为 1 的子服才真正执行入库，因此多个子服同时运行也只会投递一次。

**收件箱写入**：发货前先预检 —— 「目标驿站收件箱已占槽位 + 在途包裹堆数 + 本次堆数」超过驿站容量时直接拒绝发货并提示清理。投递时再做一次空槽校验并做状态 CAS；若此刻仍放不下（并发发货或发货后被塞满），本次投递回滚、保留在途状态，给发件人推送一次「目标驿站已满」提示（只推一次，不刷屏），下一个轮询周期继续重试。超过 `shipment.discard-after-hours`（默认 24 小时）仍未投进，终止发货单（置 `DISCARDED`）并把物品暂存到 `yz_discarded_items` 表，管理员用 `/yz discarded` 打开 GUI 取出。

**并发保护**：收件箱取出物品后，关闭界面时按 `version` 乐观锁回写；若期间被其他操作修改，会提示重新打开，不会覆盖别人的数据。

**通知**：发货时直接给操作玩家发送「发货成功」消息。投递成功后把「包裹已到达」写入 `yz_notifications`；若发件人此刻恰好在本子服在线则立即发送，否则保留为未送达，玩家下次在任意子服上线时（延迟 1 秒）自动补发并标记已送达。通知领取使用 `SELECT ... FOR UPDATE` + 标记，多个子服同时上线不会重复发送。

**邮箱投递**：`mail send` / `mail give` / 每日奖励都走同一条路径 —— 把物品写入目标玩家 UUID 的 `yz_mailbox_items`，并用 `yz_mailbox_overflow` 兜底：邮箱满时放不下的邮件按写入顺序暂存，**不再丢弃**；玩家清理出空位后点邮箱界面的「重新领取」即可补入。暂存件数会写进邮件通知里。

**每日奖励**：玩家登录后 1 秒触发。先汇总奖励物品（`yz_daily_rewards` 里登记的模板物品 + `daily-reward.items` 配置的原版材质），为空则不标记领取；否则用 `yz_daily_claims` 记录最后领取日期并做当日去重（`INSERT IGNORE` + `UPDATE ... WHERE claim_date<>?`），因此多子服重复登录也只会发一次，**离线期间不补发**。

## 界面说明

| 界面 | 槽位 | 功能 |
|---|---|---|
| 选择页（both 模式） | 11 / 15 / 22 | 发货 / 收件箱 / 关闭 |
| 发货区 | 0-44 内为物品区 | 放入待发货物品 |
| 发货区 | 45 | 关闭 |
| 发货区 | 46 | 快递费槽（放入匹配 `currency-key` / `currency-value` 的货币物品） |
| 发货区 | 47 | 切换到收件箱（仅 both 模式） |
| 发货区 | 48 | 切换目的地（多路由时） |
| 发货区 | 49 | 点击发货 |
| 发货区 | 53 | 信息（在途发货单、快递费要求等） |
| 收件箱 | 0-44 内为物品区 | 取出物品，禁止放入 |
| 收件箱 | 49 | 全部领取 |
| 收件箱 | 53 | 信息（容量占用、在途到达数） |
| 邮箱 | 0 至 `mailbox.size`-1 | 取出 / 放入物品 |
| 邮箱 | 底部第 1 格 | 关闭 |
| 邮箱 | 底部第 5 格 | 全部领取 |
| 邮箱 | 底部第 7 格 | 重新领取（把暂存的邮件按序补入邮箱空位） |
| 邮箱 | 底部第 9 格 | 信息（容量占用） |

未点击「发货」就关闭界面，或切换到收件箱时，发货区内的物品会原样退回玩家背包（背包满则掉落原地）。

## 已知限制

- **发货区不持久化**：未点击发货的物品只存在于界面里，此时服务器崩溃会丢失。物品一旦点击发货写入数据库，就不会丢。
- **界面读取为同步查询**：打开收件箱或邮箱时会同步读一次数据库，数据量大时可能有短暂卡顿。
- **邮箱不实时刷新**：界面打开期间收到的新邮件不会即时出现（保存已改为按槽增量更新，不会再被覆盖丢失），关闭后重新打开即可看到，届时也会收到邮件通知。
- **邮箱暂存需手动补领**：邮箱满时收到的邮件暂存在 `yz_mailbox_overflow`，需要在邮箱界面点「重新领取」补入；不自动补入，避免打开界面时物品突然出现。
- `allow-cancel-shipment` 与 `max-station-size` 为预留配置，当前版本尚未生效。
- 驿站方块与邮箱方块会拦截原版右键交互，建议绑定在普通方块（或专用装饰方块）上，避免与原版容器功能混淆。
- 邮箱方块被破坏后不会自动解绑，需要重新 `/yz mailbox bind` 到新位置。

## 目录结构

```text
src/main/java/com/azadkuu/yizhan/
  YizhanPlugin.java          插件入口
  command/                   命令与 Tab 补全
  config/                    配置读取
  model/                     驿站、路由、发货单等数据模型
  storage/                   存储接口与 MySQL 实现
  service/                   物品过滤、发货逻辑、邮箱与每日奖励、到货通知
  gui/                       虚拟容器界面
  listener/                  方块交互、容器事件、上线补发通知
  task/                      投递轮询任务
  util/                      物品序列化、消息工具
src/main/resources/
  plugin.yml
  config.yml
```

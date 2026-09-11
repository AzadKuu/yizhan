# 驿站（Yizhan）

Paper 1.21.x 跨服物资运输插件。把物品从一个服务器的容器，经 MySQL 中继，**单向**运送到另一个服务器的容器。

物品不会立刻到达：发货后进入在途状态，经过可配置的缓冲时间，才投递到目标驿站，模拟驿站发件的感觉。

## 特性

- **虚拟容器**：驿站是一个绑定在方块上的虚拟容器，内容存在数据库里，不占用原版箱子
- **命令绑定位置**：`/yizhan bind` 把准星指向的方块注册为驿站
- **单向路由 + 缓冲时间**：`发送站 -> 接收站`，缓冲时间可按路由或按驿站配置
- **MySQL 中继**：HikariCP 连接池，事务 + version 乐观锁 + 投递状态 CAS，多子服同时运行不会重复投递或复制物品
- **非原版物品拦截**：按 `custom_data` / PersistentDataContainer 的命名空间做黑名单校验，默认拦截 Nexo、ItemsAdder 等自定义物品
- **收件箱容量保护**：目标收件箱放不下时，投递会延后到下一个轮询周期重试，不会丢件
- **发货与到货通知**：发货和投递完成都会给操作玩家发消息；发件人不在线时消息入库，下次在任意子服上线自动补发

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
| `/yizhan route <起点> <终点> [缓冲秒]` | 建立或更新单向路由，省略秒数则用默认缓冲 |
| `/yizhan route remove <起点> <终点>` | 删除路由 |
| `/yizhan buffer <驿站> <秒>` | 单独设置某个驿站的缓冲时间 |
| `/yizhan open <名称>` | 远程打开驿站容器 |
| `/yizhan list` | 列出所有驿站 |
| `/yizhan info <名称>` | 查看驿站详情 |
| `/yizhan debugitem` | 诊断主手物品：打印 PDC / `custom_data` 键值对、CustomModelData 与拦截判定（需 `yizhan.admin`） |
| `/yizhan reload` | 重载配置文件 |

驿站名称只允许字母、数字、下划线和短横线，长度 1-32。

## 权限

| 权限 | 默认 | 说明 |
|---|---|---|
| `yizhan.bind` | op | 绑定 / 解绑驿站 |
| `yizhan.route` | op | 管理路由与缓冲时间 |
| `yizhan.open` | true | 打开驿站容器 |
| `yizhan.admin` | op | 重载配置等管理操作 |

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
  block-custom-model-data: false # 是否额外拦截带 CustomModelData 的物品
```

**缓冲时间优先级**：路由设置 > 驿站设置 > `default-buffer-seconds`。

**消息占位符**：`ship-start` 支持 `%id%`、`%to%`、`%buffer%`；`ship-arrived` 支持 `%id%`、`%station%`。两个模板都支持 `&` 颜色代码。

## 物品过滤

插件读取物品 `ItemMeta` 的 PersistentDataContainer（对应 1.20.5+ 的 `minecraft:custom_data` 组件），逐个检查 key：

- key 的命名空间命中 `blocked-namespaces` 时拦截
- key 的完整字符串命中 `blocked-keys` 时拦截
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
| `yz_routes` | 单向路由：起点、终点、可选缓冲 |
| `yz_shipments` | 发货单：状态、出发时间、到达时间、发件人 |
| `yz_shipment_items` | 在途包裹内的物品快照 |
| `yz_station_items` | 驿站收件箱内的物品 |
| `yz_notifications` | 玩家待发送通知，用于离线 / 跨服补发 |

物品以 `ItemStack#serializeAsBytes()` 序列化后存 `LONGBLOB`。

## 运行机制

**发货**：玩家点击「发货」时，插件创建一条 `yz_shipments` 记录（`arrive_at = 当前时间 + 缓冲秒`），并把发货区物品写入 `yz_shipment_items`。物品随后从发货区移除。

**投递**：每个子服有一个异步定时任务，按 `poll-interval-seconds` 扫描 `status='IN_TRANSIT'` 且 `arrive_at` 已到的发货单。投递前做状态 CAS：

```sql
UPDATE yz_shipments SET status='DELIVERED' WHERE id=? AND status='IN_TRANSIT'
```

只有受影响行数为 1 的子服才真正执行入库，因此多个子服同时运行也只会投递一次。

**收件箱写入**：投递前检查目标驿站是否有足够空槽；槽位不足则回滚本次投递，保留在途状态，等下一个轮询周期重试。

**并发保护**：收件箱取出物品后，关闭界面时按 `version` 乐观锁回写；若期间被其他操作修改，会提示重新打开，不会覆盖别人的数据。

**通知**：发货时直接给操作玩家发送「发货成功」消息。投递成功后把「包裹已到达」写入 `yz_notifications`；若发件人此刻恰好在本子服在线则立即发送，否则保留为未送达，玩家下次在任意子服上线时（延迟 1 秒）自动补发并标记已送达。通知领取使用 `SELECT ... FOR UPDATE` + 标记，多个子服同时上线不会重复发送。

## 界面说明

| 界面 | 槽位 | 功能 |
|---|---|---|
| 选择页（both 模式） | 11 / 15 / 22 | 发货 / 收件箱 / 关闭 |
| 发货区 | 0-44 内为物品区 | 放入待发货物品 |
| 发货区 | 45 | 关闭 |
| 发货区 | 47 | 切换到收件箱（仅 both 模式） |
| 发货区 | 48 | 切换目的地（多路由时） |
| 发货区 | 49 | 点击发货 |
| 发货区 | 53 | 信息（在途发货单等） |
| 收件箱 | 0-44 内为物品区 | 取出物品，禁止放入 |
| 收件箱 | 49 | 全部领取 |
| 收件箱 | 53 | 信息（容量占用、在途到达数） |

未点击「发货」就关闭界面，或切换到收件箱时，发货区内的物品会原样退回玩家背包（背包满则掉落原地）。

## 已知限制

- **发货区不持久化**：未点击发货的物品只存在于界面里，此时服务器崩溃会丢失。物品一旦点击发货写入数据库，就不会丢。
- **界面读取为同步查询**：打开收件箱时会同步读一次数据库，数据量大时可能有短暂卡顿。
- `allow-cancel-shipment` 与 `max-station-size` 为预留配置，当前版本尚未生效。
- 驿站方块会拦截原版右键交互，建议绑定在普通方块（或专用装饰方块）上，避免与原版容器功能混淆。

## 目录结构

```text
src/main/java/com/azadkuu/yizhan/
  YizhanPlugin.java          插件入口
  command/                   命令与 Tab 补全
  config/                    配置读取
  model/                     驿站、路由、发货单等数据模型
  storage/                   存储接口与 MySQL 实现
  service/                   物品过滤、发货逻辑、到货通知
  gui/                       虚拟容器界面
  listener/                  方块交互、容器事件、上线补发通知
  task/                      投递轮询任务
  util/                      物品序列化、消息工具
src/main/resources/
  plugin.yml
  config.yml
```

# pay-service 模块说明

> 支付微服务，端口 8086，数据库 `hm-pay`。负责支付单生成、余额支付、支付状态管理。通过 OpenFeign 远程调用 `user-service` 扣减余额；余额扣减成功并更新支付单状态后，通过 RocketMQ 向 `trade-service` 发送支付成功消息。

---

## 1. 文件夹结构

```
pay-service/
├── pom.xml                                    # Maven 配置，依赖 hm-common、hm-api
└── src/main/
    ├── java/com/hmall/pay/
    │   ├── PayApplication.java                # 启动类（@EnableFeignClients）
    │   ├── controller/
    │   │   └── PayController.java             # 支付单 REST 接口
    │   ├── domain/
    │   │   ├── dto/
    │   │   │   ├── PayApplyDTO.java           # 生成支付单请求（@NotNull @Min 校验）
    │   │   │   └── PayOrderFormDTO.java       # 余额支付请求（支付单ID + 支付密码）
    │   │   ├── po/
    │   │   │   └── PayOrder.java              # 支付单数据库实体
    │   │   └── vo/
    │   │       └── PayOrderVO.java            # 支付单查询响应 VO
    │   ├── enums/
    │   │   ├── PayChannel.java                # 支付渠道枚举（wxPay、aliPay、balance）
    │   │   ├── PayStatus.java                 # 支付状态枚举
    │   │   └── PayType.java                   # 支付方式枚举（JSAPI、小程序、APP、扫码、余额）
    │   ├── mapper/
    │   │   └── PayOrderMapper.java            # MyBatis-Plus Mapper
    │   ├── mq/
    │   │   └── PaySuccessMessageProducer.java # RocketMQ 支付成功消息生产者
    │   ├── service/
    │   │   └── IPayOrderService.java          # Service 接口
    │   └── service/impl/
    │       └── PayOrderServiceImpl.java       # 支付单生成 + 余额支付核心逻辑
    └── resources/
        └── application.yml                    # 端口、数据源、Nacos 配置
```

---

## 2. 接口清单

### 支付单接口（`/pay-orders`）

| 方法 | HTTP | 路径 | 参数 | 说明 |
|------|------|------|------|------|
| `applyPayOrder` | POST | `/pay-orders` | `PayApplyDTO` Body | 生成支付单（目前仅支持余额支付） |
| `tryPayOrderByBalance` | POST | `/pay-orders/{id}` | `id` 路径参数 + `PayOrderFormDTO` Body | 使用余额完成支付 |
| `queryPayOrders` | GET | `/pay-orders` | 无 | 查询所有支付单列表 |

---

## 3. 核心业务逻辑

### 3.1 生成支付单（`applyPayOrder`）

**入口**：`PayController.applyPayOrder` → `PayOrderServiceImpl.applyPayOrder`

1. **支付方式校验**：Controller 层校验 `payType` 必须为 `BALANCE(5)`，否则抛出 `BizIllegalException`
2. **幂等性校验**（`checkIdempotent`）：
   - 根据 `bizOrderNo`（业务订单号）查询是否已存在支付单
   - **不存在**：创建新支付单，生成 `payOrderNo`（雪花算法），状态设为 `WAIT_BUYER_PAY`
   - **已支付成功**（`TRADE_SUCCESS`）：抛出异常"订单已经支付！"
   - **已关闭**（`TRADE_CLOSED`）：抛出异常"订单已关闭"
   - **支付渠道变更**：重置支付单数据，保留原 ID 和 `payOrderNo`
   - **未支付且渠道一致**：直接返回旧支付单

3. **初始化支付单**（`buildPayOrder`）：
   - 支付超时时间：`当前时间 + 120 分钟`
   - 状态：`WAIT_BUYER_PAY(1)`
   - `bizUserId`：当前登录用户（`UserContext.getUser()`）

4. **返回**：支付单 ID

### 3.2 余额支付（`tryPayOrderByBalance`）

**事务控制：`@Transactional`**

**执行流程**：

1. **查询支付单**：根据 ID 查询，校验状态必须为 `WAIT_BUYER_PAY`
2. **扣减余额**：远程调用 `UserClient.deductMoney(pw, amount)`
   - `user-service` 校验支付密码并执行 `UPDATE user SET balance = balance - amount`
3. **更新支付单状态**：调用 `markPayOrderSuccess`
   - 使用 **乐观锁**：`UPDATE pay_order SET status = 3 WHERE id = ? AND status IN (0, 1)`
   - 若更新失败（影响行数为 0），说明支付单状态已被修改，抛出 `BizIllegalException`
4. **发送支付成功消息**：调用 `PaySuccessMessageProducer.sendPaySuccessMessage(bizOrderNo)`
   - 消息体为业务订单 ID 字符串，由 `trade-service` 消费后更新订单状态

---

## 4. 远程调用

| 调用方 | 被调用方 | 接口 | 用途 |
|--------|----------|------|------|
| `PayOrderServiceImpl` | `user-service` | `UserClient.deductMoney` | 扣减用户余额 |

---

## 5. 数据库实体 `PayOrder`

表名：`pay_order`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 支付单 ID（雪花算法 ASSIGN_ID） |
| `bizOrderNo` | Long | 业务订单号（关联 order 表） |
| `payOrderNo` | Long | 支付单号（雪花算法） |
| `bizUserId` | Long | 支付用户 ID |
| `payChannelCode` | String | 支付渠道编码（wxPay/aliPay/balance） |
| `amount` | Integer | 支付金额（分） |
| `payType` | Integer | 支付方式（1-H5, 2-小程序, 3-公众号, 4-扫码, 5-余额） |
| `status` | Integer | 0-未提交, 1-待支付, 2-已关闭, 3-支付成功 |
| `expandJson` | String | 拓展字段 |
| `resultCode` / `resultMsg` | String | 第三方返回信息 |
| `paySuccessTime` | LocalDateTime | 支付成功时间 |
| `payOverTime` | LocalDateTime | 支付超时时间 |
| `qrCodeUrl` | String | 支付二维码链接 |
| `isDelete` | Boolean | 逻辑删除 |

---

## 6. 枚举说明

### `PayStatus`

| 枚举值 | value | 说明 |
|--------|-------|------|
| `NOT_COMMIT` | 0 | 未提交 |
| `WAIT_BUYER_PAY` | 1 | 待支付 |
| `TRADE_CLOSED` | 2 | 已关闭（超时或取消） |
| `TRADE_SUCCESS` | 3 | 支付成功 |
| `TRADE_FINISHED` | 3 | 支付完成（与 SUCCESS 同值） |

### `PayType`

| 枚举值 | value | 说明 |
|--------|-------|------|
| `JSAPI` | 1 | 网页支付 |
| `MINI_APP` | 2 | 小程序支付 |
| `APP` | 3 | APP 支付 |
| `NATIVE` | 4 | 扫码支付 |
| `BALANCE` | 5 | 余额支付（当前唯一支持） |

### `PayChannel`

| 枚举值 | desc |
|--------|------|
| `wxPay` | 微信支付 |
| `aliPay` | 支付宝支付 |
| `balance` | 余额支付 |

---

## 7. 配置说明

`application.yml` 关键项：

| 配置 | 值 | 说明 |
|------|-----|------|
| `server.port` | 8086 | 服务端口 |
| `spring.application.name` | `pay-service` | Nacos 注册名 |
| `spring.datasource.url` | `jdbc:mysql://127.0.0.1:3306/hm-pay` | 支付独立数据库 |
| `spring.cloud.nacos.server-addr` | `192.168.150.102` | Nacos 地址 |

---

## 8. 关键依赖

| 依赖 | 说明 |
|------|------|
| `hm-common` | 公共模块 |
| `hm-api` | Feign API 模块（UserClient 等） |
| `spring-boot-starter-web` | Web 容器 |
| `mysql-connector-java` | MySQL 驱动 |
| `mybatis-plus-boot-starter` | ORM 框架 |
| `spring-cloud-starter-alibaba-nacos-discovery` | Nacos 服务注册 |

---

## 9. RocketMQ 支付成功消息

### 9.1 生产者

`PaySuccessMessageProducer` 是 Spring 管理的 RocketMQ 生产者：

- Bean 初始化时创建 `DefaultMQProducer` 并连接 NameServer。
- `sendPaySuccessMessage(Long orderId)` 将业务订单 ID 转为 UTF-8 字符串发送。
- Bean 销毁时关闭 producer。

### 9.2 消息契约

| 项 | 值 |
|------|------|
| NameServer | `192.168.150.102:9876` |
| Producer Group | `pay-service-producer-group` |
| Topic | `trade_pay_success_topic` |
| Tag | `pay_success` |
| Body | 业务订单 ID 字符串，例如 `2051126207307116545` |

### 9.3 支付链路变化

余额支付成功后的订单状态通知不再通过 `TradeClient.markOrderPaySuccess` 同步调用完成，而是由 `pay-service` 发送 MQ 消息，`trade-service` 消费后调用自身的 `markOrderPaySuccess(orderId)`。

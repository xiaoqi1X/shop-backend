# trade-service 模块说明

> 交易/订单微服务，端口 8085，数据库 `hm-trade`。负责订单创建、订单查询。创建订单时通过 OpenFeign 远程调用 `item-service` 查询商品和扣减库存，调用 `cart-service` 清理购物车。

---

## 1. 文件夹结构

```
trade-service/
├── pom.xml                                    # Maven 配置，依赖 hm-common、hm-api
└── src/main/
    ├── java/
    │   ├── ProducerExample.java               # RocketMQ 生产者示例（独立 main）
    │   ├── ConsumerExample.java               # RocketMQ 消费者示例（独立 main）
    │   └── com/hmall/trade/
    │       ├── TradeApplication.java          # 启动类（@EnableFeignClients）
    │       ├── controller/
    │       │   └── OrderController.java       # 订单 REST 接口
    │       ├── domain/
    │       │   ├── dto/
    │       │   │   └── OrderFormDTO.java      # 下单表单（地址ID、支付类型、商品列表）
    │       │   ├── po/
    │       │   │   ├── Order.java             # 订单主表实体
    │       │   │   ├── OrderDetail.java       # 订单详情表实体
    │       │   │   └── OrderLogistics.java    # 订单物流表实体
    │       │   └── vo/
    │       │       └── OrderVO.java           # 订单查询响应 VO
    │       ├── mapper/
    │       │   ├── OrderMapper.java
    │       │   ├── OrderDetailMapper.java
    │       │   └── OrderLogisticsMapper.java
    │       ├── service/
    │       │   ├── IOrderService.java
    │       │   ├── IOrderDetailService.java
    │       │   └── IOrderLogisticsService.java
    │       └── service/impl/
    │           ├── OrderServiceImpl.java      # 订单创建核心逻辑
    │           ├── OrderDetailServiceImpl.java
    │           └── OrderLogisticsServiceImpl.java
    └── resources/
        └── application.yml                    # 端口、数据源、Nacos 配置
```

---

## 2. 接口清单

### 订单接口（`/orders`）

| 方法 | HTTP | 路径 | 参数 | 说明 |
|------|------|------|------|------|
| `queryOrderById` | GET | `/orders/{id}` | `id` 路径参数 | 根据 ID 查询订单详情 |
| `createOrder` | POST | `/orders` | `OrderFormDTO` Body | 创建订单（事务控制） |

---

## 3. 核心业务逻辑

### 3.1 创建订单（`OrderServiceImpl.createOrder`）

**事务控制：`@Transactional`**

**执行流程**：

1. **解析订单表单**
   - 从 `OrderFormDTO` 获取商品列表 `List<OrderDetailDTO>`（含 `itemId` 和 `num`）
   - 构建 `itemId → num` 的 Map

2. **查询商品信息**
   - 远程调用 `ItemClient.queryItemByIds(itemIds)` 批量获取商品
   - 校验商品是否全部存在

3. **计算订单总价**
   - 遍历商品列表，累加 `item.getPrice()`
   - 设置 `paymentType`、`userId`（从 `UserContext` 获取）、`status = 1`（未付款）
   - 保存订单主表（`order`）

4. **保存订单详情**
   - 根据商品信息构建 `List<OrderDetail>`
   - 批量插入 `order_detail` 表

5. **扣减库存**
   - 远程调用 `ItemClient.deductStock(detailDTOS)`
   - 若失败抛出 `RuntimeException`，触发事务回滚

6. **清理购物车**
   - 远程调用 `CartClient.deleteCartItemByIds(itemIds)`
   - 删除当前用户购物车中已购买的商品

**返回**：订单 ID

---

## 4. 远程调用

| 调用方 | 被调用方 | 接口 | 用途 |
|--------|----------|------|------|
| `OrderServiceImpl` | `item-service` | `ItemClient.queryItemByIds` | 查询商品名称、价格、规格 |
| `OrderServiceImpl` | `item-service` | `ItemClient.deductStock` | 扣减商品库存 |
| `OrderServiceImpl` | `cart-service` | `CartClient.deleteCartItemByIds` | 清理购物车已购商品 |

---

## 5. 数据库实体

### `Order`（主表）

表名：`` `order` ``（反引号转义）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 订单 ID（雪花算法 ASSIGN_ID） |
| `totalFee` | Integer | 总金额（分） |
| `paymentType` | Integer | 1-支付宝，2-微信，3-余额 |
| `userId` | Long | 下单用户 ID |
| `status` | Integer | 1-未付款，2-已付款未发货，3-已发货未确认，4-交易成功，5-订单关闭，6-交易结束已评价 |
| `create_time` / `pay_time` / `consign_time` / `end_time` / `close_time` / `comment_time` / `update_time` | LocalDateTime | 各阶段时间 |

### `OrderDetail`（详情表）

表名：`order_detail`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 详情 ID（自增） |
| `orderId` | Long | 所属订单 ID |
| `itemId` | Long | 商品 ID |
| `num` | Integer | 购买数量 |
| `name` / `spec` / `price` / `image` | String/Integer | 商品快照信息 |

### `OrderLogistics`（物流表）

表名：`order_logistics`

| 字段 | 类型 | 说明 |
|------|------|------|
| `orderId` | Long | 订单 ID（INPUT，与订单一对一） |
| `logisticsNumber` | String | 物流单号 |
| `logisticsCompany` | String | 物流公司 |
| `contact` / `mobile` / `province` / `city` / `town` / `street` | String | 收件人信息 |

---

## 6. 配置说明

`application.yml` 关键项：

| 配置 | 值 | 说明 |
|------|-----|------|
| `server.port` | 8085 | 服务端口 |
| `spring.application.name` | `trade-service` | Nacos 注册名 |
| `spring.datasource.url` | `jdbc:mysql://127.0.0.1:3306/hm-trade` | 交易独立数据库 |
| `spring.cloud.nacos.server-addr` | `192.168.150.102` | Nacos 地址 |

---

## 7. 关键依赖

| 依赖 | 说明 |
|------|------|
| `hm-common` | 公共模块 |
| `hm-api` | Feign API 模块（ItemClient、CartClient） |
| `spring-boot-starter-web` | Web 容器 |
| `mysql-connector-java` | MySQL 驱动 |
| `mybatis-plus-boot-starter` | ORM 框架 |
| `spring-cloud-starter-alibaba-nacos-discovery` | Nacos 服务注册 |

---

## 8. RocketMQ 示例

`ProducerExample.java` 和 `ConsumerExample.java` 是独立的 RocketMQ 收发消息示例（非 Spring 集成）：
- **Producer**：向 `TestTopic` 发送 `TagA` 消息
- **Consumer**：订阅 `TestTopic`，并发消费并打印消息内容
- NameServer 地址：`192.168.150.102:9876`

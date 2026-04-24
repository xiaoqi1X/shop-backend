# hm-service 模块说明

> 旧单体核心服务，端口 8080，数据库 `hmall`。在微服务拆分前，所有业务（用户、商品、购物车、订单、支付、地址）均集中在此服务中。当前作为过渡或遗留服务存在，部分功能已被独立微服务替代。

---

## 1. 文件夹结构

```
hm-service/
├── pom.xml                                    # Maven 配置，依赖 hm-common、Spring Security、Redis
├── Dockerfile
└── src/main/
    ├── java/com/hmall/
    │   ├── HMallApplication.java              # 启动类
    │   ├── config/
    │   │   ├── AuthProperties.java            # 鉴权路径配置（hm.auth）
    │   │   ├── JwtProperties.java             # JWT 秘钥配置（hm.jwt）
    │   │   ├── MvcConfig.java                 # MVC 配置（拦截器注册）
    │   │   └── SecurityConfig.java            # 密码编码器 + KeyPair Bean
    │   ├── controller/                        # REST 控制器层
    │   │   ├── AddressController.java         # 收货地址接口
    │   │   ├── CartController.java            # 购物车接口
    │   │   ├── HelloController.java           # 测试/hello接口
    │   │   ├── ItemController.java            # 商品管理接口
    │   │   ├── OrderController.java           # 订单管理接口
    │   │   ├── PayController.java             # 支付单接口
    │   │   ├── SearchController.java          # 商品搜索接口
    │   │   └── UserController.java            # 用户登录、余额扣减接口
    │   ├── domain/
    │   │   ├── dto/                           # 请求/传输 DTO
    │   │   │   ├── AddressDTO.java
    │   │   │   ├── CartFormDTO.java
    │   │   │   ├── ItemDTO.java
    │   │   │   ├── LoginFormDTO.java
    │   │   │   ├── OrderDetailDTO.java
    │   │   │   ├── OrderFormDTO.java
    │   │   │   ├── PayApplyDTO.java
    │   │   │   └── PayOrderFormDTO.java
    │   │   ├── po/                            # 数据库实体（MyBatis-Plus）
    │   │   │   ├── Address.java
    │   │   │   ├── Cart.java
    │   │   │   ├── Item.java
    │   │   │   ├── Order.java
    │   │   │   ├── OrderDetail.java
    │   │   │   ├── OrderLogistics.java
    │   │   │   ├── PayOrder.java
    │   │   │   └── User.java
    │   │   ├── query/
    │   │   │   └── ItemPageQuery.java         # 商品分页查询条件
    │   │   └── vo/                            # 响应 VO
    │   │       ├── CartVO.java
    │   │       ├── OrderVO.java
    │   │       ├── PayOrderVO.java
    │   │       └── UserLoginVO.java
    │   ├── enums/                             # 业务枚举
    │   │   ├── PayChannel.java
    │   │   ├── PayStatus.java
    │   │   ├── PayType.java
    │   │   └── UserStatus.java
    │   ├── interceptor/
    │   │   └── LoginInterceptor.java          # JWT Token 拦截器
    │   ├── mapper/                            # MyBatis-Plus Mapper 接口
    │   │   ├── AddressMapper.java
    │   │   ├── CartMapper.java
    │   │   ├── ItemMapper.java
    │   │   ├── OrderDetailMapper.java
    │   │   ├── OrderLogisticsMapper.java
    │   │   ├── OrderMapper.java
    │   │   ├── PayOrderMapper.java
    │   │   └── UserMapper.java
    │   ├── service/                           # Service 接口层
    │   │   ├── IAddressService.java
    │   │   ├── ICartService.java
    │   │   ├── IItemService.java
    │   │   ├── IOrderDetailService.java
    │   │   ├── IOrderLogisticsService.java
    │   │   ├── IOrderService.java
    │   │   ├── IPayOrderService.java
    │   │   └── IUserService.java
    │   ├── service/impl/                      # Service 实现层
    │   │   ├── AddressServiceImpl.java
    │   │   ├── CartServiceImpl.java
    │   │   ├── ItemServiceImpl.java
    │   │   ├── OrderDetailServiceImpl.java
    │   │   ├── OrderLogisticsServiceImpl.java
    │   │   ├── OrderServiceImpl.java
    │   │   ├── PayOrderServiceImpl.java
    │   │   └── UserServiceImpl.java
    │   └── utils/
    │       └── JwtTool.java                   # JWT 生成与解析（同 Gateway）
    └── resources/
        ├── application.yaml                   # 服务配置（端口、数据源、JWT、Knife4j）
        ├── hmall.jks                          # RSA 秘钥库
        └── mapper/                            # 自定义 XML Mapper
            ├── CartMapper.xml
            ├── ItemMapper.xml
            ├── OrderDetailMapper.xml
            ├── OrderLogisticsMapper.xml
            ├── OrderMapper.xml
            ├── PayOrderMapper.xml
            ├── TradeClient.xml
            └── UserMapper.xml
```

---

## 2. 接口清单

### 2.1 用户接口（`/users`）

| 方法 | HTTP | 路径 | 说明 |
|------|------|------|------|
| `login` | POST | `/users/login` | 用户名密码登录，返回 JWT Token 和用户信息 |
| `deductMoney` | PUT | `/users/money/deduct` | 余额扣减（需支付密码） |

### 2.2 商品接口（`/items`）

| 方法 | HTTP | 路径 | 说明 |
|------|------|------|------|
| `queryItemByPage` | GET | `/items/page` | 分页查询商品列表 |
| `queryItemByIds` | GET | `/items` | 根据 ID 批量查询商品 |
| `queryItemById` | GET | `/items/{id}` | 根据 ID 查询单个商品 |
| `saveItem` | POST | `/items` | 新增商品 |
| `updateItemStatus` | PUT | `/items/status/{id}/{status}` | 修改商品上下架状态 |
| `updateItem` | PUT | `/items` | 更新商品信息（不允许改状态） |
| `deleteItemById` | DELETE | `/items/{id}` | 删除商品 |
| `deductStock` | PUT | `/items/stock/deduct` | 批量扣减库存 |

### 2.3 购物车接口（`/carts`）

| 方法 | HTTP | 路径 | 说明 |
|------|------|------|------|
| `addItem2Cart` | POST | `/carts` | 添加商品到购物车（存在则数量+1） |
| `updateCart` | PUT | `/carts` | 更新购物车条目 |
| `deleteCartItem` | DELETE | `/carts/{id}` | 删除单条购物车商品 |
| `queryMyCarts` | GET | `/carts` | 查询当前用户购物车列表 |
| `deleteCartItemByIds` | DELETE | `/carts` | 批量删除购物车商品 |

### 2.4 订单接口（`/orders`）

| 方法 | HTTP | 路径 | 说明 |
|------|------|------|------|
| `queryOrderById` | GET | `/orders/{id}` | 根据 ID 查询订单 |
| `createOrder` | POST | `/orders` | 创建订单（含扣库存、清购物车） |
| `markOrderPaySuccess` | PUT | `/orders/{orderId}` | 标记订单已支付 |

### 2.5 支付接口（`/pay-orders`）

| 方法 | HTTP | 路径 | 说明 |
|------|------|------|------|
| `applyPayOrder` | POST | `/pay-orders` | 生成支付单（幂等） |
| `tryPayOrderByBalance` | POST | `/pay-orders/{id}` | 余额支付 |

### 2.6 地址接口（`/addresses`）

| 方法 | HTTP | 路径 | 说明 |
|------|------|------|------|
| `findAddressById` | GET | `/addresses/{addressId}` | 根据 ID 查询地址（校验归属） |
| `findMyAddresses` | GET | `/addresses` | 查询当前用户所有地址 |

### 2.7 搜索接口（`/search`）

| 方法 | HTTP | 路径 | 说明 |
|------|------|------|------|
| `search` | GET | `/search/list` | 商品搜索（关键词、品牌、类目、价格区间） |

---

## 3. 核心业务逻辑

### 3.1 用户登录（`UserServiceImpl`）

1. 根据用户名查询用户
2. 校验用户状态（`FROZEN` 则禁止登录）
3. `BCryptPasswordEncoder` 校验密码
4. 生成 JWT Token（RS256，有效期 30 分钟）
5. 返回 `UserLoginVO`（userId、username、balance、token）

### 3.2 创建订单（`OrderServiceImpl`）

**事务控制：`@Transactional`**

1. 解析订单表单，获取商品 ID 和购买数量
2. 批量查询商品信息，校验商品是否存在
3. 计算订单总价（price × num 累加）
4. 保存订单主表（`order`）
5. 构建并保存订单详情（`order_detail`）
6. 清理购物车中已购买的商品
7. 扣减商品库存

### 3.3 购物车添加（`CartServiceImpl`）

1. 获取当前登录用户
2. 判断该商品是否已在购物车：
   - 已存在：更新数量 +1
   - 不存在：校验购物车条目是否超过 10 个上限，然后新增

### 3.4 余额支付（`PayOrderServiceImpl`）

**事务控制：`@Transactional`**

1. 查询支付单，校验状态是否为 `WAIT_BUYER_PAY`
2. 调用 `UserService.deductMoney` 扣减余额
3. 更新支付单状态为 `TRADE_SUCCESS`（带乐观锁：状态必须为未支付）
4. 更新订单状态为已付款

**支付单幂等性（`checkIdempotent`）**：
- 同一业务订单号（`bizOrderNo`）只能存在一个支付单
- 若已支付成功或已关闭，则拒绝重复申请
- 若支付渠道变更，重置支付单数据

### 3.5 库存扣减（`ItemServiceImpl`）

使用 MyBatis-Plus 的 `executeBatch` 批量执行 `updateStock`：
- 通过 SQL 语句直接扣减库存（`stock = stock - num`）
- 若任一商品库存不足，整个批次失败，抛出 `BizIllegalException`

---

## 4. 鉴权机制

### `LoginInterceptor`

位置：`com.hmall.interceptor.LoginInterceptor`

1. 从请求头 `authorization` 提取 Token
2. 调用 `JwtTool.parseToken` 解析用户 ID
3. 存入 `UserContext`（ThreadLocal）
4. 请求结束后清理 ThreadLocal

### 免登录路径

配置于 `application.yaml`：
- `/search/**`
- `/users/login`
- `/items/**`
- `/hi`

---

## 5. 数据库

- **URL**：`jdbc:mysql://127.0.0.1:3306/hmall`
- **包含表**：`user`、`item`、`cart`、`order`、`order_detail`、`order_logistics`、`pay_order`、`address`

---

## 6. 关键依赖

| 依赖 | 说明 |
|------|------|
| `hm-common` | 公共模块 |
| `spring-boot-starter-web` | Web 容器 |
| `spring-security-crypto` / `spring-security-rsa` | BCrypt + RSA 秘钥 |
| `mysql-connector-java` | MySQL 驱动 |
| `mybatis-plus-boot-starter` | ORM 框架 |
| `spring-boot-starter-data-redis` | Redis |
| `knife4j-openapi2` | API 文档（通过 hm-common 引入） |

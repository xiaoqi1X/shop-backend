# cart-service 模块说明

> 购物车微服务，端口 8082，数据库 `hm-cart`。负责用户购物车的增删改查。依赖 `hm-api` 通过 `ItemClient` 远程获取商品实时价格和库存。

---

## 1. 文件夹结构

```
cart-service/
├── pom.xml                                    # Maven 配置，依赖 hm-common、hm-api
└── src/main/
    ├── java/com/hmall/cart/
    │   ├── cartApplication.java               # 启动类（@EnableFeignClients 开启 Feign）
    │   ├── controller/
    │   │   └── CartController.java            # 购物车 REST 接口
    │   ├── domain/
    │   │   ├── dto/
    │   │   │   └── CartFormDTO.java           # 添加购物车表单
    │   │   ├── po/
    │   │   │   └── Cart.java                  # 购物车数据库实体
    │   │   └── vo/
    │   │       └── CartVO.java                # 购物车列表响应 VO（含实时价格库存）
    │   ├── mapper/
    │   │   └── CartMapper.java                # MyBatis-Plus Mapper + 自定义 updateNum
    │   ├── service/
    │   │   └── ICartService.java              # Service 接口
    │   └── service/impl/
    │       └── CartServiceImpl.java           # Service 实现
    └── resources/
        └── application.yaml                   # 端口、数据源、Nacos、Knife4j 配置
```

---

## 2. 接口清单

### 购物车接口（`/carts`）

| 方法 | HTTP | 路径 | 参数 | 说明 |
|------|------|------|------|------|
| `addItem2Cart` | POST | `/carts` | `CartFormDTO` Body | 添加商品到购物车 |
| `updateCart` | PUT | `/carts` | `Cart` Body | 更新购物车条目 |
| `deleteCartItem` | DELETE | `/carts/{id}` | `id` 路径参数 | 删除单条购物车条目 |
| `queryMyCarts` | GET | `/carts` | 无 | 查询当前用户购物车列表 |
| `deleteCartItemByIds` | DELETE | `/carts` | `ids` 查询参数 | 批量删除购物车商品 |

---

## 3. 核心业务逻辑

### 3.1 添加商品到购物车（`addItem2Cart`）

1. 从 `UserContext` 获取当前登录用户 ID
2. 判断该商品是否已在用户购物车中：
   - **已存在**：调用 `CartMapper.updateNum`，数量 `+1`
   - **不存在**：
     1. 校验购物车条目数量是否已达上限（10 个）
     2. 将 `CartFormDTO` 转换为 `Cart` PO，设置 `userId`
     3. 保存到数据库

### 3.2 查询购物车列表（`queryMyCarts`）

1. 查询当前用户所有购物车条目
2. 转换为 `CartVO`
3. **远程调用 `ItemClient.queryItemByIds`** 批量获取商品实时信息：
   - `newPrice`：最新售价
   - `status`：商品状态（上架/下架）
   - `stock`：最新库存
4. 将商品信息回填到 `CartVO` 返回

> 购物车列表展示的价格和库存是实时的，不是加入购物车时的快照。

### 3.3 批量删除（`removeByItemIds`）

根据 `userId` + `itemId` IN 条件批量删除，用于订单创建成功后清理已购买商品。

---

## 4. 数据库实体 `Cart`

表名：`cart`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 购物车条目 ID（自增） |
| `userId` | Long | 用户 ID |
| `itemId` | Long | 商品 ID |
| `num` | Integer | 购买数量（默认 1，添加时递增） |
| `name` | String | 商品标题（加入时的快照） |
| `spec` | String | 规格（加入时的快照） |
| `price` | Integer | 单价（分，加入时的快照） |
| `image` | String | 商品图片（加入时的快照） |
| `create_time` / `update_time` | LocalDateTime | 创建/更新时间 |

---

## 5. 远程调用

| 调用方 | 被调用方 | 接口 | 用途 |
|--------|----------|------|------|
| `CartServiceImpl` | `item-service` | `ItemClient.queryItemByIds` | 查询购物车商品实时价格和库存 |

启动类配置：

```java
@EnableFeignClients(value = "com.hmall.api", defaultConfiguration = DefaultFeignConfig.class)
```

---

## 6. 配置说明

`application.yaml` 关键项：

| 配置 | 值 | 说明 |
|------|-----|------|
| `server.port` | 8082 | 服务端口 |
| `spring.application.name` | `cart-service` | Nacos 注册名 |
| `spring.datasource.url` | `jdbc:mysql://127.0.0.1:3306/hm-cart` | 购物车独立数据库 |
| `spring.cloud.nacos.discovery.server-addr` | `192.168.150.102:8848` | Nacos 地址 |

---

## 7. 关键依赖

| 依赖 | 说明 |
|------|------|
| `hm-common` | 公共模块 |
| `hm-api` | Feign API 模块（ItemClient） |
| `spring-boot-starter-web` | Web 容器 |
| `mysql-connector-java` | MySQL 驱动 |
| `mybatis-plus-boot-starter` | ORM 框架 |
| `spring-cloud-starter-alibaba-nacos-discovery` | Nacos 服务注册 |
| `spring-cloud-openfeign-core` | OpenFeign 核心 |

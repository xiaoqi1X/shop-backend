# item-service 模块说明

> 商品微服务，端口 8081，数据库 `hm-item`。负责商品信息管理、商品搜索、库存扣减。被 `cart-service`、`trade-service` 等通过 `ItemClient`（hm-api）远程调用。

---

## 1. 文件夹结构

```
item-service/
├── pom.xml                                    # Maven 配置
└── src/main/
    ├── java/com/hmall/item/
    │   ├── itemApplication.java               # 启动类（@MapperScan 扫描 mapper）
    │   ├── controller/
    │   │   ├── ItemController.java            # 商品 CRUD + 库存扣减接口
    │   │   └── SearchController.java          # 商品搜索接口
    │   ├── domain/
    │   │   ├── dto/
    │   │   │   ├── ItemDTO.java               # 商品传输对象
    │   │   │   └── OrderDetailDTO.java        # 订单明细条目（库存扣减用）
    │   │   ├── po/
    │   │   │   └── Item.java                  # 商品数据库实体
    │   │   └── query/
    │   │       └── ItemPageQuery.java         # 商品搜索查询条件
    │   ├── mapper/
    │   │   └── ItemMapper.java                # MyBatis-Plus Mapper + 自定义 updateStock
    │   ├── service/
    │   │   └── IItemService.java              # Service 接口
    │   └── service/impl/
    │       └── ItemServiceImpl.java           # Service 实现
    └── resources/
        └── application.yaml                   # 端口、数据源、Nacos、Knife4j 配置
```

---

## 2. 接口清单

### 2.1 商品管理接口（`/items`）

| 方法 | HTTP | 路径 | 参数 | 说明 |
|------|------|------|------|------|
| `queryItemByPage` | GET | `/items/page` | `PageQuery` | 分页查询商品，默认按 `update_time` 降序 |
| `queryItemByIds` | GET | `/items` | `ids` 查询参数 | 根据 ID 批量查询商品 |
| `queryItemById` | GET | `/items/{id}` | `id` 路径参数 | 查询单个商品详情 |
| `saveItem` | POST | `/items` | `ItemDTO` Body | 新增商品 |
| `updateItemStatus` | PUT | `/items/status/{id}/{status}` | `id`、`status` | 修改商品状态（1-正常，2-下架，3-删除） |
| `updateItem` | PUT | `/items` | `ItemDTO` Body | 更新商品信息（status 字段强制忽略） |
| `deleteItemById` | DELETE | `/items/{id}` | `id` | 删除商品 |
| `deductStock` | PUT | `/items/stock/deduct` | `List<OrderDetailDTO>` Body | 批量扣减库存 |

### 2.2 搜索接口（`/search`）

| 方法 | HTTP | 路径 | 参数 | 说明 |
|------|------|------|------|------|
| `search` | GET | `/search/list` | `ItemPageQuery` | 多条件商品搜索 |

**搜索条件**：
- `key`：商品名称关键字（模糊匹配）
- `brand`：品牌（精确匹配）
- `category`：类目（精确匹配）
- `minPrice` / `maxPrice`：价格区间
- 仅返回 `status = 1`（正常上架）的商品

---

## 3. 核心业务逻辑

### 3.1 库存扣减（`ItemServiceImpl.deductStock`）

使用 MyBatis-Plus 的 `executeBatch` 批量执行：

1. 遍历 `List<OrderDetailDTO>`（含 `itemId` 和 `num`）
2. 对每条记录执行 Mapper 中的 `updateStock` SQL：
   ```sql
   UPDATE item SET stock = stock - #{num} WHERE id = #{itemId}
   ```
3. 若库存不足，SQL 执行异常或返回影响行数为 0，则整个批次失败
4. 抛出 `BizIllegalException`（"库存不足！"），触发上游事务回滚

> **注意**：此扣减方式未使用乐观锁（版本号），依赖数据库的行锁和异常回滚保证一致性。

### 3.2 批量查询商品（`queryItemByIds`）

- 使用 MyBatis-Plus `listByIds` 批量查询
- 通过 `BeanUtils.copyList` 转换为 `List<ItemDTO>`
- 被多个服务通过 `ItemClient` 远程调用，用于获取商品名称、价格、库存等信息

---

## 4. 数据库实体 `Item`

表名：`item`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 商品 ID（自增） |
| `name` | String | SKU 名称 |
| `price` | Integer | 价格（分） |
| `stock` | Integer | 库存数量 |
| `image` | String | 商品图片 URL |
| `category` | String | 类目名称 |
| `brand` | String | 品牌名称 |
| `spec` | String | 规格 |
| `sold` | Integer | 销量 |
| `commentCount` | Integer | 评论数 |
| `isAD` | Boolean | 是否推广广告 |
| `status` | Integer | 1-正常，2-下架，3-删除 |
| `create_time` / `update_time` | LocalDateTime | 创建/更新时间 |

---

## 5. 配置说明

`application.yaml` 关键项：

| 配置 | 值 | 说明 |
|------|-----|------|
| `server.port` | 8081 | 服务端口 |
| `spring.application.name` | `item-service` | Nacos 注册名 |
| `spring.datasource.url` | `jdbc:mysql://127.0.0.1:3306/hm-item` | 商品独立数据库 |
| `spring.cloud.nacos.discovery.server-addr` | `192.168.150.102:8848` | Nacos 地址 |

---

## 6. 关键依赖

| 依赖 | 说明 |
|------|------|
| `hm-common` | 公共模块（分页、响应封装、工具类） |
| `spring-boot-starter-web` | Web 容器 |
| `mysql-connector-java` | MySQL 驱动 |
| `mybatis-plus-boot-starter` | ORM 框架 |
| `spring-cloud-starter-alibaba-nacos-discovery` | Nacos 服务注册 |

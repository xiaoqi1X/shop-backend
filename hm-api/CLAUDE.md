# hm-api 模块说明

> Feign API 模块。集中定义跨服务调用的 OpenFeign 客户端接口、共享 DTO 以及 Fallback 降级逻辑。被所有需要远程调用的业务模块依赖。

---

## 1. 文件夹结构

```
hm-api/
├── pom.xml                                    # Maven 配置，依赖 hm-common、OpenFeign、LoadBalancer
└── src/main/java/com/hmall/api/
    ├── client/
    │   ├── CartClient.java                    # 购物车服务 Feign 客户端
    │   ├── ItemClient.java                    # 商品服务 Feign 客户端（含熔断降级）
    │   ├── TradeClient.java                   # 交易服务 Feign 客户端
    │   ├── UserClient.java                    # 用户服务 Feign 客户端
    │   └── fallback/
    │       └── ItemClientFallback.java        # ItemClient 降级工厂（FallbackFactory）
    ├── config/
    │   └── DefaultFeignConfig.java            # Feign 全局配置：请求头透传 + Fallback Bean
    └── dto/
        ├── ItemDTO.java                       # 商品信息传输对象
        └── OrderDetailDTO.java                # 订单明细条目传输对象
```

---

## 2. FeignClient 接口清单

### 2.1 `ItemClient` → `item-service`

位置：`com.hmall.api.client.ItemClient`

| 方法 | HTTP | 路径 | 参数 | 说明 |
|------|------|------|------|------|
| `queryItemByIds(Collection<Long> ids)` | GET | `/items` | `ids` 查询参数 | 根据 ID 批量查询商品信息 |
| `deductStock(List<OrderDetailDTO> items)` | PUT | `/items/stock/deduct` | Body | 批量扣减商品库存 |

- 配置了 `fallback = ItemClientFallback.class`，在 `item-service` 不可用时触发降级。
- `@Component` 注解使其能被组件扫描。

### 2.2 `CartClient` → `cart-service`

位置：`com.hmall.api.client.CartClient`

| 方法 | HTTP | 路径 | 参数 | 说明 |
|------|------|------|------|------|
| `deleteCartItemByIds(Collection<Long> ids)` | DELETE | `/carts` | `ids` 查询参数 | 根据条目 ID 批量删除购物车商品 |

### 2.3 `TradeClient` → `trade-service`

位置：`com.hmall.api.client.TradeClient`

| 方法 | HTTP | 路径 | 参数 | 说明 |
|------|------|------|------|------|
| `markOrderPaySuccess(Long orderId)` | PUT | `/orders/{orderId}` | `orderId` 路径参数 | 标记订单支付成功 |

### 2.4 `UserClient` → `user-service`

位置：`com.hmall.api.client.UserClient`

| 方法 | HTTP | 路径 | 参数 | 说明 |
|------|------|------|------|------|
| `deductMoney(String pw, Integer amount)` | PUT | `/users/money/deduct` | `pw`、`amount` 查询参数 | 扣减用户余额 |

---

## 3. Fallback 降级策略

### `ItemClientFallback`

位置：`com.hmall.api.client.fallback.ItemClientFallback`

实现 `FallbackFactory<ItemClient>`，根据异常原因生成降级实例：

| 方法 | 降级行为 | 设计意图 |
|------|----------|----------|
| `queryItemByIds` | 记录错误日志，返回空列表 | 查询类失败不阻断主流程，允许无数据返回 |
| `deductStock` | 记录错误日志，抛出 `BizIllegalException` | 库存扣减是写操作，失败必须触发事务回滚，不能静默忽略 |

---

## 4. 全局配置 `DefaultFeignConfig`

位置：`com.hmall.api.config.DefaultFeignConfig`

业务模块在 `@EnableFeignClients(defaultConfiguration = DefaultFeignConfig.class)` 中引用此配置。

### 4.1 用户信息透传拦截器

```java
@Bean
public RequestInterceptor userInfoRequestInterceptor()
```

- 从 `UserContext`（ThreadLocal）读取当前登录用户 ID
- 放入 Feign 请求头 `user-info`，保证用户身份在微服务调用链中持续传递
- 若用户未登录（userId == null），则跳过

### 4.2 Fallback Bean 注册

```java
@Bean
public ItemClientFallback itemClientFallback()
```

将降级工厂实例化为 Spring Bean，供 Feign 熔断器使用。

---

## 5. DTO 说明

| DTO | 位置 | 用途 |
|-----|------|------|
| `ItemDTO` | `com.hmall.api.dto.ItemDTO` | 商品信息的标准化传输结构（id、name、price、stock、image、category、brand、spec、sold、commentCount、isAD、status） |
| `OrderDetailDTO` | `com.hmall.api.dto.OrderDetailDTO` | 订单明细传输结构（itemId、num），用于库存扣减和订单构建 |

---

## 6. 使用方式

业务模块在启动类添加：

```java
@EnableFeignClients(defaultConfiguration = DefaultFeignConfig.class, basePackages = "com.hmall.api.client")
```

然后直接注入使用：

```java
@Service
public class SomeService {
    @Autowired
    private ItemClient itemClient;

    public void doSomething() {
        List<ItemDTO> items = itemClient.queryItemByIds(List.of(1L, 2L));
    }
}
```

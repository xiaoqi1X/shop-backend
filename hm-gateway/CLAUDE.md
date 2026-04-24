# hm-gateway 模块说明

> Spring Cloud Gateway 网关服务，端口 8080。作为所有前端流量的统一入口，负责路由转发、负载均衡、JWT 鉴权，并将解析后的用户 ID 通过请求头透传给下游微服务。

---

## 1. 文件夹结构

```
hm-gateway/
├── pom.xml                                    # Maven 配置
└── src/main/
    ├── java/com/hmall/gateway/
    │   ├── GatewayApplication.java            # 启动类
    │   ├── config/
    │   │   ├── AuthProperties.java            # 鉴权路径配置属性（hm.auth）
    │   │   ├── JwtProperties.java             # JWT 秘钥配置属性（hm.jwt）
    │   │   └── SecurityConfig.java            # 密码编码器 + KeyPair 秘钥对 Bean
    │   ├── filter/
    │   │   └── AuthGlobalFilter.java          # 全局鉴权过滤器（JWT 校验 + user-info 透传）
    │   └── util/
    │       └── JwtTool.java                   # JWT 生成与解析工具（RS256）
    └── resources/
        ├── application.yml                    # 路由规则 + Nacos + JWT 配置
        └── hmall.jks                          # RSA 秘钥库文件
```

---

## 2. 路由配置

配置文件：`application.yml`

| 路由 ID | 断言路径（Path） | 目标服务 | 说明 |
|---------|------------------|----------|------|
| `item` | `/items/**`, `/search/**` | `lb://item-service` | 商品查询、搜索 |
| `cart` | `/carts/**` | `lb://cart-service` | 购物车操作 |
| `user` | `/users/**`, `/addresses/**` | `lb://user-service` | 用户、地址管理 |
| `trade` | `/orders/**` | `lb://trade-service` | 订单交易 |
| `pay` | `/pay-orders/**` | `lb://pay-service` | 支付相关 |

> `lb://` 表示从 Nacos 注册中心拉取服务实例列表，通过 LoadBalancer 做负载均衡。

---

## 3. JWT 鉴权机制

### 3.1 秘钥配置（`JwtProperties`）

配置项前缀：`hm.jwt`

| 属性 | 说明 | 默认值 |
|------|------|--------|
| `location` | 秘钥库文件路径 | `classpath:hmall.jks` |
| `alias` | 秘钥别名 | `hmall` |
| `password` | 秘钥库密码 | `hmall123` |
| `tokenTTL` | Token 有效期 | `30m` |

`SecurityConfig` 读取上述配置，通过 `KeyStoreKeyFactory` 加载 `KeyPair`（RSA 公私钥对）。

### 3.2 JWT 工具类（`JwtTool`）

位置：`com.hmall.gateway.util.JwtTool`

使用 **Hutool JWT** + **RS256** 算法：

- **`createToken(Long userId, Duration ttl)`**：生成 JWT，Payload 中存入 `user` 字段（用户 ID），设置过期时间。
- **`parseToken(String token)`**：解析并校验 JWT：
  1. Token 是否为空
  2. 签名是否有效（`jwt.verify()`）
  3. 是否过期（`JWTValidator.validateDate()`）
  4. Payload 中 `user` 字段是否存在且为合法数字
  
  任一校验失败均抛出 `UnauthorizedException`。

### 3.3 全局鉴权过滤器（`AuthGlobalFilter`）

位置：`com.hmall.gateway.filter.AuthGlobalFilter`

实现 `GlobalFilter` + `Ordered`，`order = 0`，优先执行。

**执行流程**：

1. 获取当前请求路径
2. 匹配 `hm.auth.excludePaths`（免登录路径）：
   - `/search/**`
   - `/users/login`
   - `/items/**`
   - 匹配成功则直接放行
3. 从请求头 `authorization` 提取 Token
4. 调用 `JwtTool.parseToken(token)` 解析用户 ID
5. 若解析失败（Token 无效/过期），返回 HTTP 401
6. 将 `userId` 写入请求头 `user-info`，重新构建 `ServerWebExchange`
7. 放行到下游服务

**下游服务如何获取用户**：通过 `UserInfoInterceptor` 读取 `user-info` 请求头，存入 `UserContext`（ThreadLocal）。

---

## 4. 配置属性说明

### `AuthProperties`

配置项前缀：`hm.auth`

| 属性 | 类型 | 说明 |
|------|------|------|
| `includePaths` | `List<String>` | （当前未使用）需要拦截的路径 |
| `excludePaths` | `List<String>` | 免登录路径列表，支持 Ant 通配符匹配 |

---

## 5. 关键依赖

| 依赖 | 说明 |
|------|------|
| `spring-cloud-starter-gateway` | Spring Cloud Gateway 核心 |
| `spring-cloud-starter-alibaba-nacos-discovery` | Nacos 服务注册与发现 |
| `spring-cloud-starter-loadbalancer` | 负载均衡器 |
| `hm-common` | 公共模块（异常类、工具类） |
| `spring-security-crypto` | `BCryptPasswordEncoder`、`KeyStoreKeyFactory` |
| `hutool-jwt` | JWT 生成与解析（通过 hm-common 的 hutool-all 引入） |

---

## 6. 启动与访问

1. 确保 Nacos（`192.168.150.102:8848`）已启动
2. 启动 `GatewayApplication`
3. 前端通过 Nginx 代理到 `http://localhost:8080`，或直接访问 Gateway

Gateway 作为流量入口，必须先于业务服务启动，否则前端请求无法正确路由。

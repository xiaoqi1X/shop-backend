# user-service 模块说明

> 用户微服务，端口 8084，数据库 `hm-user`。负责用户登录（JWT 签发）、余额扣减、收货地址管理。被 `pay-service` 通过 `UserClient`（hm-api）远程调用扣减余额。

---

## 1. 文件夹结构

```
use-service/                                    # 目录名，Maven artifactId 为 user-service
├── pom.xml
└── src/main/
    ├── java/com/hmall/user/
    │   ├── UserApplication.java                 # 启动类（@EnableFeignClients 开启 Feign）
    │   ├── config/
    │   │   ├── JwtProperties.java               # JWT 秘钥配置属性
    │   │   └── SecurityConfig.java              # 密码编码器 + KeyPair Bean
    │   ├── controller/
    │   │   ├── UserController.java              # 用户登录、余额扣减接口
    │   │   └── AddressController.java           # 收货地址查询接口
    │   ├── domain/
    │   │   ├── dto/
    │   │   │   ├── AddressDTO.java              # 地址传输对象
    │   │   │   └── LoginFormDTO.java            # 登录表单（@NotNull 校验）
    │   │   ├── po/
    │   │   │   ├── Address.java                 # 地址数据库实体
    │   │   │   └── User.java                    # 用户数据库实体
    │   │   └── vo/
    │   │       └── UserLoginVO.java             # 登录响应（token、userId、username、balance）
    │   ├── enums/
    │   │   └── UserStatus.java                  # 用户状态枚举（NORMAL、FROZEN）
    │   ├── mapper/
    │   │   ├── AddressMapper.java               # 地址 Mapper
    │   │   └── UserMapper.java                  # 用户 Mapper + 自定义 updateMoney
    │   ├── service/
    │   │   ├── IAddressService.java
    │   │   └── IUserService.java
    │   ├── service/impl/
    │   │   ├── AddressServiceImpl.java
    │   │   └── UserServiceImpl.java             # 登录、扣款核心逻辑
    │   └── util/
    │       └── JwtTool.java                     # JWT 生成与解析（RS256）
    └── resources/
        ├── application.yml                      # 服务配置
        └── hmall.jks                            # RSA 秘钥库
```

---

## 2. 接口清单

### 2.1 用户接口（`/users`）

| 方法 | HTTP | 路径 | 参数 | 说明 |
|------|------|------|------|------|
| `login` | POST | `/users/login` | `LoginFormDTO` Body | 用户名密码登录，返回 JWT Token |
| `deductMoney` | PUT | `/users/money/deduct` | `pw`、`amount` 查询参数 | 余额扣减（需支付密码） |

### 2.2 地址接口（`/addresses`）

| 方法 | HTTP | 路径 | 参数 | 说明 |
|------|------|------|------|------|
| `findAddressById` | GET | `/addresses/{addressId}` | `addressId` | 根据 ID 查询地址（校验归属当前用户） |
| `findMyAddresses` | GET | `/addresses` | 无 | 查询当前用户所有地址 |

---

## 3. 核心业务逻辑

### 3.1 用户登录（`UserServiceImpl.login`）

1. 根据用户名查询用户（`lambdaQuery().eq(User::getUsername, username).one()`）
2. 校验用户是否存在
3. 校验用户状态：
   - `FROZEN` → 抛出 `ForbiddenException`（"用户被冻结"）
4. `BCryptPasswordEncoder.matches()` 校验密码
5. 调用 `JwtTool.createToken(userId, ttl)` 生成 JWT（RS256，有效期 30 分钟）
6. 封装 `UserLoginVO` 返回

```java
UserLoginVO {
    token;      // JWT Token
    userId;     // 用户 ID
    username;   // 用户名
    balance;    // 账户余额（分）
}
```

### 3.2 余额扣减（`UserServiceImpl.deductMoney`）

1. 从 `UserContext` 获取当前登录用户 ID
2. 查询用户并校验支付密码（同样使用 `BCryptPasswordEncoder`）
3. 调用 `UserMapper.updateMoney` 执行 SQL：
   ```sql
   UPDATE user SET balance = balance - ${totalFee} WHERE id = #{userId}
   ```
4. 若余额不足，SQL 执行异常，抛出 `RuntimeException`（"扣款失败，可能是余额不足！"）

> **注意**：`totalFee` 使用 `${}` 直接拼接，存在 SQL 注入风险；余额扣减未使用乐观锁，高并发下可能出现超扣。

---

## 4. 数据库实体

### `User`

表名：`user`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 用户 ID（自增） |
| `username` | String | 用户名 |
| `password` | String | 密码（BCrypt 加密） |
| `phone` | String | 注册手机号 |
| `status` | UserStatus | NORMAL(1) / FROZEN(2) |
| `balance` | Integer | 账户余额（分） |
| `create_time` / `update_time` | LocalDateTime | 创建/更新时间 |

### `Address`

表名：`address`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 地址 ID（自增） |
| `userId` | Long | 所属用户 ID |
| `province` / `city` / `town` | String | 省/市/区 |
| `mobile` | String | 手机号 |
| `street` | String | 详细地址 |
| `contact` | String | 联系人 |
| `isDefault` | Integer | 是否默认地址（1-是，0-否） |
| `notes` | String | 备注 |

---

## 5. 配置说明

`application.yml` 关键项：

| 配置 | 值 | 说明 |
|------|-----|------|
| `server.port` | 8084 | 服务端口 |
| `spring.application.name` | `user-service` | Nacos 注册名 |
| `spring.datasource.url` | `jdbc:mysql://127.0.0.1:3306/hm-user` | 用户独立数据库 |
| `spring.cloud.nacos.discovery.server-addr` | `192.168.150.102:8848` | Nacos 地址 |
| `hm.jwt.tokenTTL` | `30m` | Token 有效期 |

---

## 6. 关键依赖

| 依赖 | 说明 |
|------|------|
| `hm-common` | 公共模块 |
| `hm-api` | Feign API 模块（Remote 调用备用） |
| `spring-boot-starter-web` | Web 容器 |
| `spring-security-crypto` / `spring-security-rsa` | BCrypt + RSA 秘钥 |
| `mysql-connector-java` | MySQL 驱动 |
| `mybatis-plus-boot-starter` | ORM 框架 |
| `spring-cloud-starter-alibaba-nacos-discovery` | Nacos 服务注册 |

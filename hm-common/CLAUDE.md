# hm-common 模块说明

> 公共基础模块，被所有业务模块依赖。封装了通用实体、异常体系、工具类、自动配置等基础设施，避免代码重复。

---

## 1. 文件夹结构

```
hm-common/
├── pom.xml                                    # Maven 配置，无 parent-boot，仅声明依赖
└── src/main/
    ├── java/com/hmall/common/
    │   ├── advice/
    │   │   └── CommonExceptionAdvice.java     # 全局异常处理（@RestControllerAdvice）
    │   ├── config/
    │   │   ├── JsonConfig.java                # Jackson 序列化配置（Long → String）
    │   │   ├── MvcConfig.java                 # MVC 拦截器注册（UserInfoInterceptor）
    │   │   └── MyBatisConfig.java             # MyBatis-Plus 分页插件配置
    │   ├── domain/
    │   │   ├── PageDTO.java                   # 通用分页返回对象
    │   │   ├── PageQuery.java                 # 通用分页查询条件
    │   │   └── R.java                         # 通用 REST 响应封装
    │   ├── exception/
    │   │   ├── BadRequestException.java       # 400 请求参数异常
    │   │   ├── BizIllegalException.java       # 500 业务非法异常
    │   │   ├── CommonException.java           # 异常基类（code + message）
    │   │   ├── DbException.java               # 500 数据库操作异常
    │   │   ├── ForbiddenException.java        # 403 权限禁止异常
    │   │   └── UnauthorizedException.java     # 401 未授权异常
    │   ├── interceptors/
    │   │   └── UserInfoInterceptor.java       # 从请求头解析 user-id 写入 ThreadLocal
    │   └── utils/
    │       ├── BeanUtils.java                 # 对象拷贝扩展（支持 Convert 回调）
    │       ├── CollUtils.java                 # 集合工具扩展
    │       ├── Convert.java                   # 对象转换回调接口
    │       ├── CookieBuilder.java             # Cookie 构建器（URL 编码防中文乱码）
    │       ├── UserContext.java               # 基于 ThreadLocal 的用户上下文
    │       └── WebUtils.java                  # Web 请求/响应工具
    └── resources/META-INF/
        ├── spring.factories                   # Spring Boot 自动配置入口
        └── spring-configuration-metadata.json # 配置元数据
```

---

## 2. 核心功能

### 2.1 通用响应对象 `R<T>`

位置：`com.hmall.common.domain.R`

所有 REST 接口统一返回的结构：

```json
{
  "code": 200,
  "msg": "OK",
  "data": {}
}
```

| 方法 | 说明 |
|------|------|
| `R.ok()` / `R.ok(data)` | 成功响应，HTTP 200 |
| `R.error(msg)` | 服务器异常，HTTP 500 |
| `R.error(code, msg)` | 自定义状态码 |
| `R.error(CommonException)` | 基于业务异常构造响应 |
| `success()` | 判断 `code == 200` |

### 2.2 分页封装

**`PageQuery`** — 请求端分页参数（页码、页大小、排序字段、是否升序）：
- 默认 `pageNo=1`，`pageSize=20`
- `toMpPage(...)` 系列方法：转换为 MyBatis-Plus 的 `Page<T>` 对象
- 支持手动指定 `OrderItem`、前端传参排序、或默认按 `create_time` 降序

**`PageDTO<T>`** — 返回端分页结果（total、pages、list）：
- 提供 `of(Page)`、`of(Page, mapper)`、`of(Page, Class)`、`of(Page, Class, Convert)` 多种工厂方法
- 自动处理空结果，避免返回 `null`

### 2.3 异常体系

所有异常均继承 `CommonException`（RuntimeException），携带 `code` 字段：

| 异常类 | HTTP Code | 使用场景 |
|--------|-----------|----------|
| `BadRequestException` | 400 | 请求参数错误 |
| `UnauthorizedException` | 401 | 未登录/Token 失效 |
| `ForbiddenException` | 403 | 权限不足 |
| `BizIllegalException` | 500 | 业务规则非法 |
| `DbException` | 500 | 数据库操作失败 |

**`CommonExceptionAdvice`** 负责统一拦截并转换为 `ResponseEntity<R<Void>>`：
- 捕获参数校验异常 `MethodArgumentNotValidException`，拼接所有校验错误信息
- 捕获绑定异常 `BindException`、`NestedServletException`
- 兜底 `Exception.class`，返回 "服务器内部异常"

### 2.4 用户上下文传递

在微服务调用链中，用户 ID 通过请求头 `user-info` 透传：

1. **Gateway 侧**：JWT 解析成功后，将用户 ID 放入 `user-info` 请求头转发到下游。
2. **服务侧**：`UserInfoInterceptor` 拦截所有请求，读取 `user-info` 头，写入 `UserContext`（ThreadLocal）。
3. **Feign 调用**：OpenFeign 拦截器从 `UserContext` 读取用户 ID，再次写入请求头。
4. **请求结束**：`afterCompletion` 清理 ThreadLocal，防止线程池复用导致数据泄漏。

```java
Long userId = UserContext.getUser();   // 获取当前登录用户ID
UserContext.setUser(userId);           // 设置（通常由拦截器完成）
UserContext.removeUser();              // 清理
```

### 2.5 自动配置

通过 `META-INF/spring.factories` 向 Spring Boot 注册以下配置类：

| 配置类 | 条件 | 功能 |
|--------|------|------|
| `MyBatisConfig` | 类路径存在 `MybatisPlusInterceptor` | 注册 MyBatis-Plus 分页插件，限制单页最大 1000 条 |
| `JsonConfig` | 类路径存在 `ObjectMapper` | Jackson 将 `Long`、`BigInteger` 序列化为字符串，避免前端精度丢失 |
| `MvcConfig` | 类路径存在 `DispatcherServlet` | 注册 `UserInfoInterceptor` 到拦截器链 |

### 2.6 工具类说明

| 工具类 | 说明 |
|--------|------|
| `BeanUtils` | 扩展 Hutool 的 `BeanUtil`，增加 `Convert<R,T>` 回调支持，用于字段名不一致时的自定义转换 |
| `CollUtils` | 扩展 Hutool 的 `CollectionUtil`，提供空集合单例、String 列表转 Integer/Long 列表、join 拼接等 |
| `WebUtils` | 基于 `RequestContextHolder` 获取当前 Request/Response；提供 header 读写、参数拼接、URI 提取、远程 IP 获取 |
| `CookieBuilder` | 流式 API 构建 Cookie，自动对 value 做 URL 编码/解码，支持 domain 自动推断 |

---

## 3. 关键依赖

| 依赖 | 说明 |
|------|------|
| `hutool-all` | 国产 Java 工具集 |
| `mybatis-plus-core/extension` | MP 分页和基础类（provided） |
| `spring-webmvc` | Web 基础设施（provided） |
| `hibernate-validator` | 参数校验注解 |
| `knife4j-openapi2-spring-boot-starter` | API 文档注解支持 |
| `caffeine` | 本地缓存 |
| `rocketmq-client` | RocketMQ 客户端 |
| `spring-amqp/spring-rabbit` | RabbitMQ 相关（provided） |

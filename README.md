# 黑马商城后端

这是黑马商城的后端微服务工程，覆盖商品、购物车、用户、交易、支付、网关和秒杀等业务。工程采用 Maven 多模块结构，基于 Spring Boot、Spring Cloud、Nacos、OpenFeign、MyBatis-Plus、RocketMQ、Redis 和 MySQL 构建。

## 技术栈

- Java 11
- Spring Boot 2.7.12
- Spring Cloud 2021.0.3
- Spring Cloud Alibaba 2021.0.4.0
- Nacos 注册与配置
- OpenFeign 服务调用
- MyBatis-Plus 持久层
- MySQL 8
- RocketMQ 消息队列
- Redis 缓存与秒杀库存扣减

## 模块说明

| 模块 | 说明 | 端口 |
| --- | --- | --- |
| `hm-gateway` | 网关服务，负责路由转发和 JWT 鉴权 | 8080 |
| `item-service` | 商品与搜索服务 | 8081 |
| `cart-service` | 购物车服务 | 8082 |
| `use-service` | 用户与地址服务 | 8084 |
| `trade-service` | 普通订单服务 | 8085 |
| `pay-service` | 支付订单服务 | 8086 |
| `seckill-service` | 秒杀活动、秒杀下单和异步结果推送服务 | 8087 |
| `hm-api` | OpenFeign 接口与共享 API 契约 | - |
| `hm-common` | 通用工具、异常、模型和配置 | - |

## 运行环境

- JDK 11，推荐使用 `D:\Developtools\.jdks\corretto-11.0.31`。
- Maven 3.x。
- Nacos：`192.168.150.102:8848`。
- MySQL：`127.0.0.1:3306`。
- RocketMQ NameServer：`192.168.150.102:9876`。
- Redis：`192.168.150.102:6379`。

Windows 下构建前建议先设置 JDK：

```powershell
$env:JAVA_HOME='D:\Developtools\.jdks\corretto-11.0.31'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

不要使用 JDK 21 验证本项目，当前 Lombok 1.18.20 在该环境下存在兼容问题。

## 构建

构建全部模块：

```powershell
mvn clean package -DskipTests
```

运行测试：

```powershell
mvn test
```

只构建部分服务及其依赖：

```powershell
mvn -pl hm-gateway,trade-service,seckill-service -am package -DskipTests
```

## 启动

在完整项目目录 `G:\hmall` 下，优先使用 `docs/scripts` 中的服务脚本管理后端进程：

```powershell
powershell -ExecutionPolicy Bypass -File docs\scripts\start-backend-services.ps1 -Services hm-gateway,use-service,seckill-service
powershell -ExecutionPolicy Bypass -File docs\scripts\status-backend-services.ps1
powershell -ExecutionPolicy Bypass -File docs\scripts\stop-backend-services.ps1
```

也可以在本仓库内通过 Maven 启动单个模块：

```powershell
mvn -pl hm-gateway -am spring-boot:run
```

## 网关路由

网关入口为 `http://localhost:8080`。

| 请求路径 | 目标服务 |
| --- | --- |
| `/items/**`、`/search/**` | `item-service` |
| `/carts/**` | `cart-service` |
| `/users/**`、`/addresses/**` | `use-service` |
| `/orders/**` | `trade-service` |
| `/seckill/**`、`/ws/seckill/**` | `seckill-service` |
| `/pay-orders/**` | `pay-service` |

免登录路径包括 `/search/**`、`/users/login`、`/items/**` 和 `/seckill/items`。

## 秒杀说明

秒杀服务运行在 `seckill-service`，核心链路包括 Redis 活动与库存预热、Redis 配额预扣减、RocketMQ 请求缓冲、异步数据库最终落库，以及 WebSocket 进度和最终结果推送。

## 注意事项

- `target/`、运行日志、IDE 文件等生成内容不应提交。
- 后端与前端是两个独立 Git 仓库，需要分别提交和推送。
- 本仓库仅包含后端代码，前端静态页面位于独立的 `shop-frontend` 仓库。

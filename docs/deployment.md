# 部署、升级与恢复

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业授权、定制、部署与集成咨询微信：zhuatech / zhuatech2。

要求Docker Compose v2、Docker、Python3；源码开发Java21/Maven3.9、Node24.19.0/npm。准备独立目录执行：

```bash
python3 scripts/init-env.py
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 240
```

默认 `http://127.0.0.1:8104/`，健康 `/actuator/health`。管理员admin，密码在本人私有.env的ADMIN_PASSWORD；没有公开默认密码。首次只有岗位与基础配置，没有真实业务数据。重启不覆盖密码。

端口冲突用WEB_PORT；绑定地址用BIND_ADDRESS；HTTPS代理时COOKIE_SECURE=true。MySQL数据用Compose volume，正常停止不删除数据；不对真实库执行down -v。没有固定container_name，可以用 `docker compose -p 独立名称` 隔离多个项目。

开发可用独立MySQL或仅启动Compose mysql，环境注入DATABASE_URL/USER/PASSWORD和ADMIN_PASSWORD，在backend运行mvn spring-boot:run，在frontend运行npm ci及npm run dev。Vite开发代理至本机8080，生产Nginx代理服务名backend:8080。仅开启health。

升级先停止业务写入、备份，然后使用新镜像和递增Flyway脚本启动，不修改已执行迁移。失败读取mysql/backend/frontend日志及健康状态，修复复测，不跳过后端测试。Docker Maven有依赖预取、锁定BuildKit缓存与网络重试。

备份示例在有权限的本机运行（SQL备份包含客户和照片，不能提交Git）：

```bash
umask 077
docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --hex-blob --no-tablespaces "$MYSQL_DATABASE"' > buildflow-private.sql
```

恢复到另一个独立Compose项目的全新数据库，再核对Flyway、登录、项目、验收、账单、流水和图片。不能向正在写入的库覆盖。恢复示例：

```bash
docker compose -p buildflow-restore exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < buildflow-private.sql
```

备份迁移与版本必须一致；初次初始化应用前恢复SQL，随后启动backend/frontend；已有迁移历史不运行第二遍。只有本次专用测试数据卷才可清理。

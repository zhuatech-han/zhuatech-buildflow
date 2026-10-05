# API与接口范围

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业授权、定制、部署与集成咨询微信：zhuatech / zhuatech2。

同源前缀 `/api`，会话Cookie为HttpOnly、SameSite=Strict，写操作须CSRF请求头。

- GET `/auth/csrf`返回header/token；POST `/auth/login`，GET `/auth/me`，POST `/auth/logout`、`/auth/password`。
- GET/POST `/projects`，GET/PUT/DELETE `/projects/{id}`；POST `/projects/{id}/works`，PUT/DELETE `/projects/{id}/works/{wid}`；POST `/projects/{id}/members`、`/state/activate`、`/state/close`。
- POST `/projects/{id}/claims`、`/variations`、`/costs`、`/bills`；POST `/projects/{id}/{type}/{recordId}/review`，type限claims/variations/costs/bills。
- POST `/projects/{id}/bills/{bid}/release`、`/money`；POST multipart `/projects/{id}/claims/{cid}/attachments`，GET `/attachments/{id}`。
- GET `/catalog`、`/reports`、`/reports.csv`、`/audit`。
- GET/POST `/admin/{type}`，PUT/DELETE `/admin/{type}/{id}`，type限users/roles/permissions/menus/departments/dictionaries/settings。内建权限、菜单和参数不支持新增删除。
- 匿名GET `/actuator/health`。不暴露配置与环境端点。

除新建项目和照片上传外，工程写入需携带最近GET详情中的project.revision；旧版本409 STALE_REVISION。资金requestKey应每次新交易使用UUID；网络重试保持原键与原载荷，返回原记录。传amount、quantity等十进制字符串，不能传密码哈希、自己指定结算金额或状态。客户端不能指定审核人。

401会话失效，403权限/数据范围/CSRF，400输入，404不存在，409状态、并发版本、唯一/引用约束，413数量/体积上限。统一响应code不包含SQL、凭据或客户材料。列表单类上限10,000条，本版客户端搜索、排序、分页10条；没有大规模容量保证。

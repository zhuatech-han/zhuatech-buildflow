# 数据结构与迁移

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业授权、定制、部署与集成咨询微信：zhuatech / zhuatech2。

MySQL8.4默认数据库 `zhuatech_buildflow`。`backend/src/main/resources/db/migration/V1__build_schema.sql` 创建完整结构。Flyway管理数据库，Hibernate只验证字段，不自动改表。首次使用全新库，已有库只新增递增迁移，禁止修改已执行V1或用repair掩盖差异。

基础表：department、access_role、permission、role_permission、nav_menu、account、audit_event、system_setting、dictionary_entry。

业务表：build_project（合同部门、责任人、客户绑定、日期和版本），project_member（项目现场成员，唯一组合），work_item（清单数量、冻结单价、分包承诺、其他预算），progress_claim（本次工程量及独立验收），variation（数量及预算增减、确认凭据和审批），cost_entry（直接成本及审批），billing（双方向结算、保留款和释放凭据/人员/时间），money_entry（线下收付关联、全局幂等键和指纹），site_attachment（图片实际内容）。

共18张应用表，加flyway_schema_history一张。各业务项目外键/索引，角色权限外键，工程量及金额约束，账号用户名、项目编号、项目清单编号、成员组合及资金提交键唯一；数据库禁止删被引用档案。工程量decimal(18,4)、金额decimal(18,2)，时间戳UTC、合同日期为人工工程日历日期。现版施工日期检查按UTC当天，不提供跨时区企业日历；界面时间戳按浏览器语言本地显示。

其他预算和工程量会在批准变更时变化，原值可以按当前值减批准variation的累计增减复原；单价从激活后保持不变。账单金额与原资金流水不可覆盖。照片内容随数据库备份恢复，无额外对象存储依赖。

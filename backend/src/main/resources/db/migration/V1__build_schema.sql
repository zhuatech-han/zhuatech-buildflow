-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE account (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  username varchar(60) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role_id bigint NOT NULL,
  department_id bigint NOT NULL,
  enabled boolean NOT NULL,
  FOREIGN KEY (role_id) REFERENCES access_role(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(200) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
enabled boolean NOT NULL DEFAULT TRUE,
  UNIQUE (type, code)
);




CREATE TABLE build_project (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 code varchar(60) NOT NULL UNIQUE,
 name varchar(120) NOT NULL,
 customer varchar(120) NOT NULL,
 site varchar(300) NOT NULL,
 department_id bigint NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id),
 manager_id bigint NOT NULL,
 FOREIGN KEY(manager_id) REFERENCES account(id),
 client_id bigint NULL,
 FOREIGN KEY(client_id) REFERENCES account(id),
 start_date date NOT NULL,
 end_date date NOT NULL,
 status varchar(20) NOT NULL,
 revision bigint NOT NULL
);

CREATE TABLE project_member (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 project_id bigint NOT NULL,
 FOREIGN KEY(project_id) REFERENCES build_project(id),
 account_id bigint NOT NULL,
 FOREIGN KEY(account_id) REFERENCES account(id),
 UNIQUE(project_id,account_id)
);

CREATE TABLE work_item (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 project_id bigint NOT NULL,
 FOREIGN KEY(project_id) REFERENCES build_project(id),
 code varchar(60) NOT NULL,
 name varchar(120) NOT NULL,
 unit varchar(30) NOT NULL,
 quantity decimal(18,4) NOT NULL,
 price decimal(18,2) NOT NULL,
 sub_price decimal(18,2) NOT NULL,
 vendor varchar(120) NOT NULL,
 cost_budget decimal(18,2) NOT NULL,
 due_date date NOT NULL,
 UNIQUE(project_id,code),
 CHECK(quantity > 0 AND price >= 0 AND sub_price >= 0 AND cost_budget >= 0),
 INDEX ix_work_item_project(project_id)
);

CREATE TABLE progress_claim (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 project_id bigint NOT NULL,
 FOREIGN KEY(project_id) REFERENCES build_project(id),
 work_id bigint NOT NULL,
 FOREIGN KEY(work_id) REFERENCES work_item(id),
 quantity decimal(18,4) NOT NULL,
 note varchar(1000) NOT NULL,
 work_date date NOT NULL,
 status varchar(20) NOT NULL,
 creator_id bigint NOT NULL,
 FOREIGN KEY(creator_id) REFERENCES account(id),
 reviewer_id bigint NULL,
 FOREIGN KEY(reviewer_id) REFERENCES account(id),
 review_note varchar(1000) NOT NULL,
 created_at timestamp(6) NOT NULL,
 CHECK(quantity > 0),
 INDEX ix_progress_claim_project(project_id)
);

CREATE TABLE variation (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 project_id bigint NOT NULL,
 FOREIGN KEY(project_id) REFERENCES build_project(id),
 work_id bigint NOT NULL,
 FOREIGN KEY(work_id) REFERENCES work_item(id),
 quantity_delta decimal(18,4) NOT NULL,
 budget_delta decimal(18,2) NOT NULL,
 reason varchar(1000) NOT NULL,
 reference varchar(120) NOT NULL,
 status varchar(20) NOT NULL,
 creator_id bigint NOT NULL,
 FOREIGN KEY(creator_id) REFERENCES account(id),
 reviewer_id bigint NULL,
 FOREIGN KEY(reviewer_id) REFERENCES account(id),
 review_note varchar(1000) NOT NULL,
 created_at timestamp(6) NOT NULL,
 INDEX ix_variation_project(project_id)
);

CREATE TABLE cost_entry (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 project_id bigint NOT NULL,
 FOREIGN KEY(project_id) REFERENCES build_project(id),
 work_id bigint NOT NULL,
 FOREIGN KEY(work_id) REFERENCES work_item(id),
 category varchar(60) NOT NULL,
 amount decimal(18,2) NOT NULL,
 reference varchar(120) NOT NULL,
 note varchar(1000) NOT NULL,
 status varchar(20) NOT NULL,
 creator_id bigint NOT NULL,
 FOREIGN KEY(creator_id) REFERENCES account(id),
 reviewer_id bigint NULL,
 FOREIGN KEY(reviewer_id) REFERENCES account(id),
 review_note varchar(1000) NOT NULL,
 created_at timestamp(6) NOT NULL,
 INDEX ix_cost_entry_project(project_id)
);

ALTER TABLE cost_entry ADD original_id bigint NULL;
ALTER TABLE cost_entry ADD FOREIGN KEY (original_id) REFERENCES cost_entry(id);

CREATE TABLE billing (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 project_id bigint NOT NULL,
 FOREIGN KEY(project_id) REFERENCES build_project(id),
 work_id bigint NOT NULL,
 FOREIGN KEY(work_id) REFERENCES work_item(id),
 kind varchar(20) NOT NULL,
 quantity decimal(18,4) NOT NULL,
 gross decimal(18,2) NOT NULL,
 retention decimal(18,2) NOT NULL,
 released boolean NOT NULL,
 due_date date NOT NULL,
 reference varchar(120) NOT NULL,
 status varchar(20) NOT NULL,
 creator_id bigint NOT NULL,
 FOREIGN KEY(creator_id) REFERENCES account(id),
 reviewer_id bigint NULL,
 FOREIGN KEY(reviewer_id) REFERENCES account(id),
 review_note varchar(1000) NOT NULL,
 created_at timestamp(6) NOT NULL,
 CHECK(quantity > 0 AND gross > 0 AND retention >= 0 AND retention <= gross),
 INDEX ix_billing_project(project_id)
);

ALTER TABLE billing ADD release_reference varchar(120) NULL;
ALTER TABLE billing ADD release_actor_id bigint NULL;
ALTER TABLE billing ADD released_at timestamp(6) NULL;
ALTER TABLE billing ADD FOREIGN KEY (release_actor_id) REFERENCES account(id);

CREATE TABLE money_entry (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 project_id bigint NOT NULL,
 FOREIGN KEY(project_id) REFERENCES build_project(id),
 bill_id bigint NOT NULL,
 FOREIGN KEY(bill_id) REFERENCES billing(id),
 kind varchar(20) NOT NULL,
 amount decimal(18,2) NOT NULL,
 original_id bigint NULL,
 FOREIGN KEY(original_id) REFERENCES money_entry(id),
 reference varchar(120) NOT NULL,
 note varchar(1000) NOT NULL,
 request_key varchar(80) NOT NULL UNIQUE,
 fingerprint varchar(64) NOT NULL,
 actor_id bigint NOT NULL,
 FOREIGN KEY(actor_id) REFERENCES account(id),
 created_at timestamp(6) NOT NULL,
 CHECK(amount > 0),
 INDEX ix_money_entry_project(project_id)
);

CREATE TABLE site_attachment (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 project_id bigint NOT NULL,
 FOREIGN KEY(project_id) REFERENCES build_project(id),
 claim_id bigint NOT NULL,
 FOREIGN KEY(claim_id) REFERENCES progress_claim(id),
 filename varchar(120) NOT NULL,
 media_type varchar(30) NOT NULL,
 creator_id bigint NOT NULL,
 FOREIGN KEY(creator_id) REFERENCES account(id),
 created_at timestamp(6) NOT NULL,
 content mediumblob NOT NULL,
 INDEX ix_site_attachment_project(project_id)
);

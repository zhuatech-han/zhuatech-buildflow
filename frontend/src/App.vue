<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from "vue";
import {
  LayoutDashboard,
  FolderKanban,
  Wallet,
  ChartNoAxesCombined,
  Settings,
  History,
  Plus,
  ArrowLeft,
  RefreshCw,
  X,
  LogOut,
  Printer,
  ChevronRight,
} from "@lucide/vue";
import { api, resetCsrf, downloadReport, upload } from "./api.js";
import { forms, names, errors } from "./schema.js";
import { money, pageRows, searchText } from "./format.js";
const billPreview = ref(null);
const lang = ref(localStorage.getItem("buildflow-lang") || "zh"),
  user = ref(null),
  view = ref("dashboard"),
  projects = ref([]),
  detail = ref(null),
  catalog = ref({}),
  report = ref([]),
  audit = ref([]),
  adminType = ref("users"),
  adminRows = ref([]),
  adminCatalog = reactive({}),
  busy = ref(false),
  error = ref(""),
  toast = ref(""),
  search = ref(""),
  page = ref(1),
  status = ref(""),
  desc = ref(true),
  tab = ref("works"),
  dialog = ref(null),
  draft = reactive({}),
  login = reactive({ username: "", password: "" }),
  uploading = ref(false);
const icons = {
  dashboard: LayoutDashboard,
  projects: FolderKanban,
  finance: Wallet,
  reports: ChartNoAxesCombined,
  admin: Settings,
  audit: History,
};
const t = (zh, en) => (lang.value === "en" ? en : zh);
const has = (p) => user.value?.permissions.includes(p);
const label = (v) => (names[v] ? t(...names[v]) : v);
const scopeLabel = (value) => {
  const option = forms.roles
    .find((field) => field.key === "scope")
    .options.find((item) => item[0] === value);
  return option ? t(option[1], option[2]) : value;
};
const permissionLabel = (code) => {
  const value =
    adminCatalog.permissions?.find((item) => item.code === code)?.name || code;
  const [zh, en] = value.split(" / ");
  return t(zh, en || zh);
};
const currency = computed(
  () =>
    catalog.value.settings?.find((s) => s.code === "currency")?.value || "CNY",
);
const cash = (v) => money(v, currency.value, lang.value);
const project = computed(() => detail.value?.project);
const financial = computed(
  () => has("finance") || has("cost") || has("report"),
);
const entries = computed(() => {
  if (!detail.value) return [];
  if (tab.value === "bills")
    return (
      detail.value.bills?.map((x) => ({
        ...x.bill,
        net: x.net,
        balance: x.balance,
        due: x.due,
      })) || []
    );
  return detail.value[tab.value] || [];
});
const visibleProjects = computed(() =>
  projects.value.filter(
    (p) => !status.value || p.project.status === status.value,
  ),
);
const currentRows = computed(() => {
  let rows =
    view.value === "admin"
      ? adminRows.value
      : view.value === "audit"
        ? audit.value
        : project.value
          ? entries.value
          : visibleProjects.value.map((x) => ({ ...x.project, ...x }));
  return rows;
});
const filtered = computed(() =>
  currentRows.value.filter((x) =>
    searchText(x).includes(search.value.toLowerCase()),
  ),
);
const rows = computed(() =>
  pageRows(currentRows.value, search.value, page.value, 10, desc.value),
);
const pages = computed(() =>
  Math.max(1, Math.ceil(filtered.value.length / 10)),
);
const tabs = computed(() =>
  [
    ["works", "合同清单", "Work items"],
    ["claims", "施工与验收", "Progress"],
    ["variations", "增减项", "Changes"],
    ["costs", "实际成本", "Costs"],
    ["bills", "分期结算", "Billing"],
    ["money", "收付流水", "Entries"],
    ["members", "现场成员", "Site team"],
  ].filter((x) => detail.value?.[x[0]]),
);
const totals = computed(() => ({
  active: projects.value.filter((p) => p.project.status === "ACTIVE").length,
  pending:
    detail.value?.claims?.filter((c) => c.status === "PENDING").length || 0,
}));
watch([view, tab, adminType, status, search, desc], () => (page.value = 1));
watch(lang, async () => {
  localStorage.setItem("buildflow-lang", lang.value);
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
});
/** 安全反馈保留失败表单；不吞掉业务错误。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    await fn();
  } catch (e) {
    error.value = errors[e.message]
      ? t(...errors[e.message])
      : t(
          "操作未完成，请检查输入后重试。",
          "Action failed; check inputs and retry.",
        ) +
        " (" +
        e.message +
        ")";
    if (e.message === "UNAUTHENTICATED") {
      user.value = null;
      resetCsrf();
    }
  } finally {
    busy.value = false;
  }
}
/** 获取当前岗位菜单及实时项目目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function refresh() {
  user.value = await api("/auth/me");
  catalog.value = await api("/catalog");
  projects.value = has("project.read") ? await api("/projects") : [];
  if (project.value) detail.value = await api("/projects/" + project.value.id);
  if (view.value === "reports") report.value = await api("/reports");
  if (view.value === "audit") audit.value = await api("/audit");
  if (view.value === "admin") await loadAdmin();
}
async function signIn() {
  await run(async () => {
    user.value = await api("/auth/login", "POST", login);
    login.password = "";
    resetCsrf();
    view.value = user.value.menus[0]?.code || "dashboard";
    await refresh();
  });
}
async function signOut() {
  await run(async () => {
    await api("/auth/logout", "POST");
    user.value = null;
    detail.value = null;
    resetCsrf();
  });
}
async function navigate(code) {
  await run(async () => {
    view.value = code;
    detail.value = null;
    search.value = "";
    await refresh();
  });
}
async function openProject(id) {
  await run(async () => {
    detail.value = await api("/projects/" + id);
    tab.value = "works";
    search.value = "";
  });
}
async function loadAdmin() {
  for (const key of ["users", "roles", "departments", "permissions"])
    adminCatalog[key] = await api("/admin/" + key);
  adminRows.value = await api("/admin/" + adminType.value);
}
/** 选项过滤在界面提供便利，服务端仍验证部门、岗位与项目。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function options(field) {
  let a = [];
  if (Array.isArray(field.options))
    return field.options.map((v) => ({ id: v[0], name: t(v[1], v[2]) }));
  const key = field.options;
  if (["managers", "workers", "clients"].includes(key)) {
    const permission = {
      managers: "project.write",
      workers: "site",
      clients: "client",
    }[key];
    a = (catalog.value.accounts || []).filter(
      (x) =>
        x.permissions.includes(permission) &&
        (!draft.departmentId || Number(draft.departmentId) === x.departmentId),
    );
  } else if (key === "works") a = detail.value?.works || [];
  else if (key === "costCategories")
    a = (catalog.value.dictionaries || [])
      .filter((x) => x.type === "cost")
      .map((x) => ({
        id: x.code,
        name: lang.value === "en" ? x.nameEn : x.name,
      }));
  else a = adminCatalog[key] || catalog.value[key] || [];
  return a.map((x) => ({
    id: key === "permissions" ? x.code : x.id,
    name: x.name || x.displayName || x.code,
  }));
}
/** 弹窗统一录入与明确确认，更新前保留项目版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function edit(
  type,
  path,
  title,
  values = {},
  method = "POST",
  hint = "",
) {
  Object.keys(draft).forEach((k) => delete draft[k]);
  for (const f of forms[type] || [])
    draft[f.key] =
      f.type === "checkbox"
        ? f.key !== "remove"
        : f.type === "permissions"
          ? []
          : f.type === "date"
            ? new Date().toISOString().slice(0, 10)
            : "";
  Object.assign(draft, values);
  dialog.value = {
    type,
    path,
    title,
    method,
    hint,
    revision: project.value?.revision,
    requestKey: crypto.randomUUID(),
  };
  await nextTick();
  document.querySelector("dialog input,dialog select,dialog button")?.focus();
}
async function submit() {
  await run(async () => {
    const body = { ...draft };
    if (dialog.value.revision !== undefined)
      body.revision = dialog.value.revision;
    if (dialog.value.type === "money")
      body.requestKey = dialog.value.requestKey;
    for (const f of forms[dialog.value.type] || []) {
      if (f.optional && body[f.key] === "") body[f.key] = null;
      if (
        ["select", "number"].includes(f.type) &&
        body[f.key] !== null &&
        f.key.endsWith("Id")
      )
        body[f.key] = Number(body[f.key]);
    }
    await api(dialog.value.path, dialog.value.method, body);
    if (dialog.value.type === "password") {
      user.value = null;
      resetCsrf();
    } else await refresh();
    dialog.value = null;
    toast.value = t("已保存", "Saved");
  });
}
const base = () => "/projects/" + project.value.id;
function reviewRow(row) {
  edit(
    "review",
    base() + "/" + tab.value + "/" + row.id + "/review",
    t("独立审核", "Independent review"),
    { approve: true },
  );
}
async function adminEdit(row) {
  await run(async () => {
    await loadAdmin();
    await edit(
      adminType.value,
      "/admin/" + adminType.value + (row ? "/" + row.id : ""),
      t(row ? "修改资料" : "新增资料", row ? "Edit record" : "New record"),
      row ? { ...row, password: "" } : { enabled: true },
      row ? "PUT" : "POST",
    );
  });
}
function memberName(id) {
  return (
    catalog.value.accounts?.find((x) => x.id === id)?.name ||
    adminCatalog.users?.find((x) => x.id === id)?.displayName ||
    "#" + id
  );
}
function workName(id) {
  return detail.value?.works?.find((x) => x.id === id)?.name || "#" + id;
}
async function uploadFile(event, row) {
  const file = event.target.files?.[0];
  if (!file) return;
  uploading.value = true;
  await run(async () => {
    await upload(base() + "/claims/" + row.id + "/attachments", file);
    await refresh();
  });
  uploading.value = false;
  event.target.value = "";
}
/** 结算预览只包含当前岗位可见的所选账单，打印不携带其他结算与成本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function printBill(row) {
  billPreview.value = { ...row };
  await nextTick();
  document.querySelector(".bill-preview button")?.focus();
}
function printPreview() {
  window.print();
}
watch(user, (value) => {
  if (!value) billPreview.value = null;
});
onMounted(async () => {
  try {
    await refresh();
  } catch {
    user.value = null;
  }
});
</script>
<template>
  <div v-if="!user" class="login-page">
    <section class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" /><span
        >BUILD<span class="brand-accent">FLOW</span></span
      >
      <h1>{{ t("工程项目成本与结算", "Construction job costing") }}</h1>
      <p>
        {{
          t(
            "合同清单 · 现场验收 · 项目结算",
            "Contracts · Progress · Settlement",
          )
        }}
      </p>
      <div class="login-lines">
        <span>01 {{ t("批准清单与增减项", "Approved scope & changes") }}</span
        ><span
          >02
          {{ t("验收工程量与分期结算", "Accepted quantities & billing") }}</span
        ><span
          >03
          {{ t("核对收付款与项目成本", "Payment records & job costs") }}</span
        >
      </div>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >知华科技 · ZhuaTech</a
      >
    </section>
    <section class="login-form">
      <button class="lang-button" @click="lang = lang === 'zh' ? 'en' : 'zh'">
        {{ lang === "zh" ? "English" : "中文" }}
      </button>
      <form @submit.prevent="signIn">
        <h2>{{ t("登录工作台", "Sign in") }}</h2>
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="login.username"
            required
            autocomplete="username"
            maxlength="60" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="login.password"
            type="password"
            required
            autocomplete="current-password"
            maxlength="128"
        /></label>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <button class="primary wide" :disabled="busy">
          {{ busy ? t("登录中…", "Signing in…") : t("登录", "Sign in") }}
        </button>
        <p class="license">
          {{
            t(
              "公开源码学习版 · 商用须书面授权",
              "Non-commercial source · Commercial use requires authorization",
            )
          }}
        </p>
      </form>
      <footer>
        上海如静知华信息科技有限公司<br />www.zhuatech.cn ·
        {{ t("咨询微信", "WeChat") }} zhuatech / zhuatech2
      </footer>
    </section>
  </div>
  <div v-else class="app-shell">
    <aside>
      <a
        class="brand"
        href="https://www.zhuatech.cn/"
        target="_blank"
        rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技" /><strong
          >BuildFlow</strong
        ></a
      ><span class="sidebar-label">{{
        t("工程运营", "PROJECT OPERATIONS")
      }}</span>
      <nav>
        <button
          v-for="m in user.menus"
          :key="m.code"
          :class="{ selected: view === m.code }"
          @click="navigate(m.code)"
        >
          <component :is="icons[m.code] || FolderKanban" :size="18" /><span>{{
            lang === "en" ? m.nameEn : m.name
          }}</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <small>{{ t("公开源码学习版", "Non-commercial source") }}</small
        ><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >{{ t("官网与商业咨询", "Website & commercial inquiry") }} ↗</a
        ><small>zhuatech / zhuatech2</small>
      </div>
    </aside>
    <main>
      <header>
        <div class="breadcrumb">
          BuildFlow <ChevronRight :size="14" />
          {{
            project
              ? project.code
              : t(
                  ...({
                    dashboard: ["工作台", "Workspace"],
                    projects: ["工程项目", "Projects"],
                    finance: ["收付款", "Settlement"],
                    reports: ["项目成本", "Job costing"],
                    admin: ["账号与设置", "Administration"],
                    audit: ["操作记录", "Audit"],
                  }[view] || [view, view]),
                )
          }}
        </div>
        <div class="header-actions">
          <button @click="lang = lang === 'zh' ? 'en' : 'zh'">
            {{ lang === "zh" ? "EN" : "中文" }}</button
          ><span>{{ user.displayName }}</span
          ><button
            :aria-label="t('刷新', 'Refresh')"
            :disabled="busy"
            @click="run(refresh)"
          >
            <RefreshCw :size="16" /></button
          ><button
            @click="
              edit(
                'password',
                '/auth/password',
                t('修改密码', 'Change password'),
              )
            "
          >
            {{ t("密码", "Password") }}</button
          ><button :aria-label="t('退出', 'Sign out')" @click="signOut">
            <LogOut :size="16" />
          </button>
        </div>
      </header>
      <div class="workspace">
        <div v-if="error" class="error" role="alert">
          {{ error
          }}<button :aria-label="t('关闭提示', 'Dismiss')" @click="error = ''">
            ×
          </button>
        </div>
        <div v-if="toast" class="toast" role="status">
          {{ toast
          }}<button :aria-label="t('关闭提示', 'Dismiss')" @click="toast = ''">
            ×
          </button>
        </div>
        <template v-if="project"
          ><div class="page-heading">
            <div>
              <button
                class="back"
                @click="
                  detail = null;
                  search = '';
                "
              >
                <ArrowLeft :size="16" />{{ t("全部项目", "All projects") }}
              </button>
              <h1>
                {{ project.name
                }}<span class="status" :data-status="project.status">{{
                  label(project.status)
                }}</span>
              </h1>
              <p>
                {{ project.code }} · {{ project.customer }} · {{ project.site }}
              </p>
            </div>
            <div class="actions">
              <button
                v-if="has('project.write') && project.status === 'DRAFT'"
                @click="
                  edit(
                    'projectEdit',
                    '/projects/' + project.id,
                    t('修改草稿合同', 'Edit draft contract'),
                    project,
                    'PUT',
                  )
                "
              >
                {{ t("修改合同", "Edit contract") }}
              </button>
              <button
                v-if="has('project.write') && project.status === 'DRAFT'"
                class="danger"
                @click="
                  edit(
                    'confirm',
                    '/projects/' + project.id,
                    t('删除草稿合同', 'Delete draft contract'),
                    {},
                    'DELETE',
                    t(
                      '仅无清单、无成员的草稿可删除。',
                      'Only drafts with no work items or members can be deleted.',
                    ),
                  )
                "
              >
                {{ t("删除草稿", "Delete draft") }}
              </button>
              <button
                v-if="has('project.write') && project.status === 'DRAFT'"
                class="primary"
                @click="
                  edit(
                    'confirm',
                    base() + '/state/activate',
                    t('激活合同并冻结单价', 'Activate and freeze contract'),
                    {},
                    'POST',
                    t(
                      '激活后清单数量与预算通过增减项审批调整。',
                      'After activation, changes require approval.',
                    ),
                  )
                "
              >
                {{ t("激活合同", "Activate") }}</button
              ><button
                v-if="has('project.write') && project.status === 'ACTIVE'"
                @click="
                  edit(
                    'confirm',
                    base() + '/state/close',
                    t('结清完工', 'Close settled project'),
                    {},
                    'POST',
                    t(
                      '需要全量验收、结算、保留款释放及账款清零。',
                      'Requires full acceptance, billing, retention release and zero balances.',
                    ),
                  )
                "
              >
                {{ t("结清完工", "Close project") }}
              </button>
            </div>
          </div>
          <div v-if="detail.summary" class="metrics">
            <div>
              <span>{{ t("批准合同额", "Approved contract") }}</span
              ><strong>{{ cash(detail.summary.contract) }}</strong>
            </div>
            <div>
              <span>{{ t("已验收产值", "Accepted value") }}</span
              ><strong>{{ cash(detail.summary.earned) }}</strong>
            </div>
            <div>
              <span>{{ t("已确认项目成本", "Recognized project cost") }}</span
              ><strong>{{ cash(detail.summary.actualCost) }}</strong>
            </div>
            <div>
              <span>{{ t("已确认毛利", "Recognized margin") }}</span
              ><strong
                :class="{
                  negative: Number(detail.summary.recognizedMargin) < 0,
                }"
                >{{ cash(detail.summary.recognizedMargin) }}</strong
              >
            </div>
          </div>
          <div class="project-dates">
            {{ t("计划工期", "Schedule") }} {{ project.startDate }} —
            {{ project.endDate }}
            <span
              >{{ t("项目经理", "Manager") }}
              {{ memberName(project.managerId) }}</span
            >
          </div>
          <div class="tabs">
            <button
              v-for="item in tabs"
              :key="item[0]"
              :class="{ selected: tab === item[0] }"
              @click="
                tab = item[0];
                search = '';
              "
            >
              {{ t(item[1], item[2])
              }}<span>{{ detail[item[0]]?.length || 0 }}</span>
            </button>
          </div>
          <div class="toolbar">
            <input
              v-model="search"
              :aria-label="t('搜索记录', 'Search records')"
              :placeholder="t('搜索当前记录', 'Search records')"
            /><button @click="desc = !desc">
              {{
                desc
                  ? t("最新在前", "Newest first")
                  : t("最早在前", "Oldest first")
              }}
            </button>
            <div class="spacer"></div>
            <button
              v-if="
                tab === 'works' &&
                has('project.write') &&
                project.status === 'DRAFT'
              "
              class="primary"
              @click="
                edit(
                  'work',
                  base() + '/works',
                  t('新增工程清单', 'New work item'),
                  {
                    price: '0.00',
                    subPrice: '0.00',
                    costBudget: '0.00',
                    dueDate: project.endDate,
                  },
                )
              "
            >
              <Plus :size="16" />{{ t("新增清单", "Add item") }}</button
            ><button
              v-if="
                tab === 'claims' && has('site') && project.status === 'ACTIVE'
              "
              class="primary"
              @click="
                edit(
                  'claim',
                  base() + '/claims',
                  t('申报完成工程量', 'Submit progress claim'),
                )
              "
            >
              <Plus :size="16" />{{ t("现场申报", "Submit progress") }}</button
            ><button
              v-if="
                tab === 'variations' &&
                has('project.write') &&
                project.status === 'ACTIVE'
              "
              class="primary"
              @click="
                edit(
                  'variation',
                  base() + '/variations',
                  t('申请增减项', 'New change order'),
                  { quantityDelta: '0', budgetDelta: '0.00' },
                )
              "
            >
              {{ t("申请增减项", "New change") }}</button
            ><button
              v-if="
                tab === 'costs' && has('cost') && project.status === 'ACTIVE'
              "
              class="primary"
              @click="
                edit(
                  'cost',
                  base() + '/costs',
                  t('提交成本凭据', 'Submit direct cost'),
                )
              "
            >
              {{ t("提交成本", "Submit cost") }}</button
            ><button
              v-if="
                tab === 'bills' && has('cost') && project.status === 'ACTIVE'
              "
              class="primary"
              @click="
                edit(
                  'bill',
                  base() + '/bills',
                  t('申请分期结算', 'Submit progress bill'),
                  { retentionPercent: '0.00', kind: 'CUSTOMER' },
                )
              "
            >
              {{ t("申请结算", "New bill") }}</button
            ><button
              v-if="
                tab === 'members' &&
                has('project.write') &&
                project.status !== 'CLOSED'
              "
              @click="
                edit(
                  'member',
                  base() + '/members',
                  t('管理现场成员', 'Manage site member'),
                )
              "
            >
              {{ t("管理成员", "Manage members") }}
            </button>
          </div>
          <div class="table-wrap">
            <table v-if="tab === 'works'">
              <thead>
                <tr>
                  <th>{{ t("清单 / 工程项目", "Item / Work") }}</th>
                  <th>{{ t("合同量", "Contract qty") }}</th>
                  <th>{{ t("已验收 / 待审核", "Accepted / Pending") }}</th>
                  <th v-if="!has('site') || financial || has('client')">
                    {{ t("客户单价", "Customer rate") }}
                  </th>
                  <th v-if="financial">
                    {{ t("分包 / 其他预算", "Subcontract / Budget") }}
                  </th>
                  <th>{{ t("计划完成", "Due date") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="w in rows" :key="w.id">
                  <td>
                    <strong>{{ w.name }}</strong
                    ><small>{{ w.code }}</small>
                  </td>
                  <td>{{ w.quantity }} {{ w.unit }}</td>
                  <td>
                    {{ w.accepted }} / {{ w.pending }}
                    <div class="progress">
                      <i
                        :style="{
                          width:
                            Math.min(
                              100,
                              (Number(w.accepted) / Number(w.quantity)) * 100,
                            ) + '%',
                        }"
                      ></i>
                    </div>
                  </td>
                  <td v-if="!has('site') || financial || has('client')">
                    {{ cash(w.price) }}
                  </td>
                  <td v-if="financial">
                    {{ w.vendor || "—"
                    }}<small
                      >{{ cash(w.subPrice) }} / {{ cash(w.costBudget) }}</small
                    >
                  </td>
                  <td>{{ w.dueDate }}</td>
                  <td>
                    <div
                      v-if="has('project.write') && project.status === 'DRAFT'"
                      class="actions"
                    >
                      <button
                        @click="
                          edit(
                            'work',
                            base() + '/works/' + w.id,
                            t('修改清单', 'Edit item'),
                            w,
                            'PUT',
                          )
                        "
                      >
                        {{ t("修改", "Edit") }}</button
                      ><button
                        class="danger"
                        @click="
                          edit(
                            'confirm',
                            base() + '/works/' + w.id,
                            t('删除草稿项', 'Delete draft item'),
                            {},
                            'DELETE',
                          )
                        "
                      >
                        {{ t("删除", "Delete") }}
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            <table v-else-if="tab === 'bills'">
              <thead>
                <tr>
                  <th>{{ t("结算单", "Bill") }}</th>
                  <th>{{ t("工程量 / 状态", "Quantity / Status") }}</th>
                  <th>{{ t("应结算 / 保留款", "Gross / Retention") }}</th>
                  <th>{{ t("净收付 / 尚欠", "Net paid / Balance") }}</th>
                  <th>{{ t("到期日", "Due") }}</th>
                  <th>{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="b in rows" :key="b.id" :data-bill="b.id">
                  <td>
                    <strong>#{{ b.id }} · {{ label(b.kind) }}</strong
                    ><small>{{ workName(b.workId) }} · {{ b.reference }}</small>
                  </td>
                  <td>
                    {{ b.quantity
                    }}<small
                      ><span class="status" :data-status="b.status">{{
                        label(b.status)
                      }}</span></small
                    >
                  </td>
                  <td>
                    {{ cash(b.gross)
                    }}<small
                      >{{ cash(b.retention) }} ·
                      {{
                        b.released
                          ? t("已释放", "Released")
                          : t("未释放", "Held")
                      }}</small
                    >
                  </td>
                  <td>
                    {{ cash(b.net) }}<small>{{ cash(b.balance) }}</small>
                  </td>
                  <td>{{ b.dueDate }}</td>
                  <td>
                    <div class="actions">
                      <button
                        v-if="
                          has('finance') &&
                          b.status === 'PENDING' &&
                          b.creatorId !== user.id
                        "
                        @click="reviewRow(b)"
                      >
                        {{ t("审核", "Review") }}</button
                      ><button
                        v-if="
                          has('finance') &&
                          b.status === 'APPROVED' &&
                          project.status === 'ACTIVE'
                        "
                        @click="
                          edit(
                            'money',
                            base() + '/bills/' + b.id + '/money',
                            t(
                              '登记已完成线下交易',
                              'Record completed offline transaction',
                            ),
                            { kind: 'PAYMENT', amount: String(b.balance) },
                            'POST',
                            t(
                              '这里只登记已完成交易，不会实际转账。',
                              'Records only; no funds are transferred.',
                            ),
                          )
                        "
                      >
                        {{ t("登记收付", "Record payment") }}</button
                      ><button
                        v-if="
                          has('finance') &&
                          b.status === 'APPROVED' &&
                          !b.released &&
                          project.status === 'ACTIVE'
                        "
                        @click="
                          edit(
                            'release',
                            base() + '/bills/' + b.id + '/release',
                            t('释放约定保留款', 'Release agreed retention'),
                          )
                        "
                      >
                        {{ t("释放保留款", "Release") }}</button
                      ><button
                        :aria-label="t('打印结算单', 'Print bill')"
                        @click="printBill(b)"
                      >
                        <Printer :size="16" />
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            <table v-else-if="tab === 'members'">
              <thead>
                <tr>
                  <th>{{ t("成员", "Member") }}</th>
                  <th>{{ t("账号编号", "Account ID") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="m in rows" :key="m.id">
                  <td>{{ memberName(m.accountId) }}</td>
                  <td>#{{ m.accountId }}</td>
                </tr>
              </tbody>
            </table>
            <table v-else>
              <thead>
                <tr>
                  <th>{{ t("记录 / 工程项", "Record / Work") }}</th>
                  <th>{{ t("内容", "Details") }}</th>
                  <th>{{ t("状态 / 时间", "Status / Time") }}</th>
                  <th>{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="x in rows" :key="x.id">
                  <td>
                    <strong
                      >#{{ x.id }} ·
                      {{
                        tab === "money" ? label(x.kind) : workName(x.workId)
                      }}</strong
                    ><small>{{ x.reference || x.workDate || "" }}</small>
                  </td>
                  <td>
                    <template v-if="tab === 'claims'"
                      >{{ x.quantity }} · {{ x.note }}</template
                    ><template v-else-if="tab === 'variations'"
                      >{{ x.quantityDelta }} / {{ cash(x.budgetDelta)
                      }}<small>{{ x.reason }}</small></template
                    ><template v-else-if="tab === 'costs'"
                      >{{ cash(x.amount) }} · {{ x.category
                      }}<small>{{ x.note }}</small></template
                    ><template v-else
                      >{{ cash(x.amount) }} · #{{ x.billId
                      }}<small
                        >{{ x.note }} ·
                        {{
                          x.originalId
                            ? t("原流水 #", "Original #") + x.originalId
                            : ""
                        }}</small
                      ></template
                    ><small v-if="x.reviewNote">{{ x.reviewNote }}</small>
                    <div v-if="tab === 'claims'" class="attachment-list">
                      <a
                        v-for="a in detail.attachments.filter(
                          (a) => a.claimId === x.id,
                        )"
                        :key="a.id"
                        :href="'/api/attachments/' + a.id"
                        target="_blank"
                        rel="noopener"
                        ><img
                          :src="'/api/attachments/' + a.id"
                          :alt="a.filename"
                      /></a>
                    </div>
                  </td>
                  <td>
                    <span
                      v-if="x.status"
                      class="status"
                      :data-status="x.status"
                      >{{ label(x.status) }}</span
                    ><small>{{
                      new Date(x.createdAt).toLocaleString(
                        lang === "en" ? "en-GB" : "zh-CN",
                      )
                    }}</small>
                  </td>
                  <td>
                    <button
                      v-if="
                        x.status === 'PENDING' &&
                        x.creatorId !== user.id &&
                        ((tab === 'costs' && has('finance')) ||
                          (['claims', 'variations'].includes(tab) &&
                            has('review')))
                      "
                      @click="reviewRow(x)"
                    >
                      {{ t("审核", "Review") }}</button
                    ><label
                      v-if="
                        tab === 'claims' &&
                        x.status === 'PENDING' &&
                        x.creatorId === user.id &&
                        has('site')
                      "
                      class="file-button"
                      >{{
                        uploading
                          ? t("上传中…", "Uploading…")
                          : t("添加现场照片", "Add site photo")
                      }}<input
                        type="file"
                        accept="image/png,image/jpeg"
                        :disabled="uploading"
                        @change="uploadFile($event, x)"
                    /></label>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!rows.length" class="empty">
              {{ t("暂无记录", "No records") }}
            </div>
          </div>
        </template>
        <template v-else-if="view === 'reports'"
          ><div class="page-heading">
            <div>
              <h1>{{ t("项目成本", "Job costing") }}</h1>
              <p>
                {{
                  t(
                    "批准预算、验收产值与已确认项目成本",
                    "Approved budgets, accepted work and recognized project costs",
                  )
                }}
              </p>
            </div>
            <button @click="run(downloadReport)">
              {{ t("导出CSV", "Export CSV") }}
            </button>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("项目", "Project") }}</th>
                  <th>{{ t("合同额 / 总预算", "Contract / Budget") }}</th>
                  <th>{{ t("验收产值 / 成本", "Earned / Cost") }}</th>
                  <th>{{ t("已确认毛利", "Recognized margin") }}</th>
                  <th>{{ t("未收 / 未付", "Receivable / Payable") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="s in report" :key="s.project.id">
                  <td>
                    <button class="link" @click="openProject(s.project.id)">
                      {{ s.project.name }}</button
                    ><small>{{ s.project.code }}</small>
                  </td>
                  <td>
                    {{ cash(s.contract) }}<small>{{ cash(s.budget) }}</small>
                  </td>
                  <td>
                    {{ cash(s.earned) }}<small>{{ cash(s.actualCost) }}</small>
                  </td>
                  <td :class="{ negative: Number(s.recognizedMargin) < 0 }">
                    {{ cash(s.recognizedMargin) }}
                  </td>
                  <td>
                    {{ cash(s.receivable) }}<small>{{ cash(s.payable) }}</small>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!report.length" class="empty">
              {{ t("暂无项目", "No projects") }}
            </div>
          </div></template
        >
        <template v-else-if="view === 'admin'"
          ><div class="page-heading">
            <div>
              <h1>{{ t("账号与系统设置", "Administration") }}</h1>
            </div>
            <button
              v-if="!['menus', 'settings', 'permissions'].includes(adminType)"
              class="primary"
              @click="adminEdit(null)"
            >
              <Plus :size="16" />{{ t("新增", "New") }}
            </button>
          </div>
          <div class="tabs">
            <button
              v-for="a in [
                ['users', '账号', 'Accounts'],
                ['roles', '角色', 'Roles'],
                ['permissions', '权限', 'Permissions'],
                ['menus', '菜单', 'Menus'],
                ['departments', '部门', 'Departments'],
                ['dictionaries', '字典', 'Dictionaries'],
                ['settings', '参数', 'Settings'],
              ]"
              :key="a[0]"
              :class="{ selected: adminType === a[0] }"
              @click="
                run(async () => {
                  adminType = a[0];
                  search = '';
                  await loadAdmin();
                })
              "
            >
              {{ t(a[1], a[2]) }}
            </button>
          </div>
          <div class="toolbar">
            <input
              v-model="search"
              :aria-label="t('搜索资料', 'Search records')"
              :placeholder="t('搜索资料', 'Search records')"
            />
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>{{ t("名称 / 账号", "Name / Account") }}</th>
                  <th>{{ t("设置与范围", "Settings / Scope") }}</th>
                  <th>{{ t("状态", "Status") }}</th>
                  <th>{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="a in rows" :key="a.id">
                  <td>{{ a.id }}</td>
                  <td>
                    <strong>{{ a.displayName || a.name || a.code }}</strong
                    ><small>{{ a.username || a.code || a.type }}</small>
                  </td>
                  <td>
                    {{
                      a.value ||
                      (a.scope ? scopeLabel(a.scope) : "") ||
                      (a.permissionCode
                        ? permissionLabel(a.permissionCode)
                        : "") ||
                      ""
                    }}<small v-if="a.roleId"
                      >{{
                        adminCatalog.roles.find((x) => x.id === a.roleId)?.name
                      }}
                      ·
                      {{
                        adminCatalog.departments.find(
                          (x) => x.id === a.departmentId,
                        )?.name
                      }}</small
                    ><small v-if="a.permissions">{{
                      a.permissions.map(permissionLabel).join(" · ")
                    }}</small>
                  </td>
                  <td>
                    {{
                      a.enabled === undefined
                        ? "—"
                        : a.enabled
                          ? t("启用", "Enabled")
                          : t("停用", "Disabled")
                    }}
                  </td>
                  <td>
                    <div class="actions">
                      <button @click="adminEdit(a)">
                        {{ t("修改", "Edit") }}</button
                      ><button
                        v-if="
                          !['menus', 'permissions', 'settings'].includes(
                            adminType,
                          )
                        "
                        class="danger"
                        @click="
                          edit(
                            'confirm',
                            '/admin/' + adminType + '/' + a.id,
                            t('删除资料', 'Delete record'),
                            {},
                            'DELETE',
                            t(
                              '已被引用的资料不能删除。',
                              'Referenced records cannot be deleted.',
                            ),
                          )
                        "
                      >
                        {{ t("删除", "Delete") }}
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div></template
        >
        <template v-else-if="view === 'audit'"
          ><div class="page-heading">
            <h1>{{ t("操作记录", "Audit trail") }}</h1>
          </div>
          <div class="toolbar">
            <input
              v-model="search"
              :aria-label="t('搜索记录', 'Search audit')"
              :placeholder="t('搜索记录', 'Search audit')"
            />
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("操作人", "Actor") }}</th>
                  <th>{{ t("动作", "Action") }}</th>
                  <th>{{ t("对象", "Object") }}</th>
                  <th>{{ t("时间", "Time") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="a in rows" :key="a.id">
                  <td>{{ a.actor }}</td>
                  <td>{{ a.action }}</td>
                  <td>{{ a.objectId }}</td>
                  <td>{{ new Date(a.createdAt).toLocaleString() }}</td>
                </tr>
              </tbody>
            </table>
          </div></template
        >
        <template v-else
          ><div class="page-heading">
            <div>
              <h1>
                {{
                  view === "dashboard"
                    ? t("工程工作台", "Project workspace")
                    : view === "finance"
                      ? t("收付款", "Settlement")
                      : t("工程项目", "Projects")
                }}
              </h1>
              <p>
                {{
                  t(
                    "合同、现场与结算进展",
                    "Contracts, site progress and settlement",
                  )
                }}
              </p>
            </div>
            <button
              v-if="has('project.write')"
              class="primary"
              @click="
                edit('project', '/projects', t('新建工程项目', 'New project'), {
                  departmentId: user.departmentId,
                  managerId: user.id,
                })
              "
            >
              <Plus :size="16" />{{ t("新建项目", "New project") }}
            </button>
          </div>
          <div v-if="view === 'dashboard'" class="metrics">
            <div>
              <span>{{ t("可见项目", "Visible projects") }}</span
              ><strong>{{ projects.length }}</strong>
            </div>
            <div>
              <span>{{ t("施工中", "Active") }}</span
              ><strong>{{ totals.active }}</strong>
            </div>
            <div>
              <span>{{ t("草稿合同", "Draft contracts") }}</span
              ><strong>{{
                projects.filter((p) => p.project.status === "DRAFT").length
              }}</strong>
            </div>
            <div>
              <span>{{ t("已结清完工", "Closed") }}</span
              ><strong>{{
                projects.filter((p) => p.project.status === "CLOSED").length
              }}</strong>
            </div>
          </div>
          <div class="toolbar">
            <input
              v-model="search"
              :aria-label="t('搜索项目', 'Search projects')"
              :placeholder="t('项目、客户或地址', 'Project, customer or site')"
            /><select
              v-model="status"
              :aria-label="t('项目状态', 'Project status')"
            >
              <option value="">{{ t("全部状态", "All statuses") }}</option>
              <option
                v-for="s in ['DRAFT', 'ACTIVE', 'CLOSED']"
                :key="s"
                :value="s"
              >
                {{ label(s) }}
              </option></select
            ><button @click="desc = !desc">
              {{
                desc
                  ? t("最新在前", "Newest first")
                  : t("最早在前", "Oldest first")
              }}
            </button>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("工程项目", "Project") }}</th>
                  <th>{{ t("客户 / 地址", "Customer / Site") }}</th>
                  <th>{{ t("计划工期", "Schedule") }}</th>
                  <th>{{ t("状态", "Status") }}</th>
                  <th v-if="financial">
                    {{ t("合同额 / 未收款", "Contract / Receivable") }}
                  </th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in rows" :key="p.id">
                  <td>
                    <button class="link" @click="openProject(p.id)">
                      {{ p.name }}</button
                    ><small>{{ p.code }}</small>
                  </td>
                  <td>
                    {{ p.customer }}<small>{{ p.site }}</small>
                  </td>
                  <td>
                    {{ p.startDate }}<small>{{ p.endDate }}</small>
                  </td>
                  <td>
                    <span class="status" :data-status="p.status">{{
                      label(p.status)
                    }}</span>
                  </td>
                  <td v-if="financial">
                    {{ cash(p.contract)
                    }}<small>{{ cash(p.receivable) }}</small>
                  </td>
                  <td>
                    <button
                      :aria-label="t('查看项目', 'Open project')"
                      @click="openProject(p.id)"
                    >
                      <ChevronRight :size="16" />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!rows.length" class="empty">
              {{ t("暂无项目", "No projects") }}
            </div>
          </div></template
        >
        <div v-if="view !== 'reports'" class="pagination">
          <span>{{ filtered.length }} {{ t("条记录", "records") }}</span
          ><button :disabled="page <= 1" @click="page--">
            {{ t("上一页", "Previous") }}</button
          ><span>{{ page }} / {{ pages }}</span
          ><button :disabled="page >= pages" @click="page++">
            {{ t("下一页", "Next") }}
          </button>
        </div>
      </div>
    </main>
  </div>
  <dialog
    v-if="dialog"
    open
    :aria-label="dialog.title"
    @keydown.esc="!busy && (dialog = null)"
  >
    <form @submit.prevent="submit">
      <div class="dialog-heading">
        <h2>{{ dialog.title }}</h2>
        <button
          type="button"
          :aria-label="t('关闭', 'Close')"
          :disabled="busy"
          @click="dialog = null"
        >
          <X :size="20" />
        </button>
      </div>
      <p v-if="dialog.hint" class="hint">{{ dialog.hint }}</p>
      <div class="form-grid">
        <label
          v-for="f in forms[dialog.type]"
          :key="f.key"
          :class="{
            full: ['textarea', 'permissions'].includes(f.type),
            check: f.type === 'checkbox',
          }"
          ><span>{{ t(f.zh, f.en) }}</span
          ><input
            v-if="f.type === 'checkbox'"
            v-model="draft[f.key]"
            type="checkbox" /><textarea
            v-else-if="f.type === 'textarea'"
            v-model="draft[f.key]"
            rows="3"
            maxlength="1000"
            :required="!f.optional"
          ></textarea
          ><select
            v-else-if="f.type === 'select'"
            v-model="draft[f.key]"
            :required="!f.optional"
          >
            <option value="">{{ t("请选择", "Select") }}</option>
            <option v-for="o in options(f)" :key="o.id" :value="o.id">
              {{ o.name }}
            </option>
          </select>
          <div v-else-if="f.type === 'permissions'" class="permissions">
            <label v-for="p in adminCatalog.permissions" :key="p.code"
              ><input
                v-model="draft.permissions"
                type="checkbox"
                :value="p.code"
              />{{ p.name }}</label
            >
          </div>
          <input
            v-else
            v-model="draft[f.key]"
            :type="
              ['quantity', 'money', 'number'].includes(f.type)
                ? 'number'
                : f.type
            "
            :step="
              f.type === 'quantity'
                ? '0.0001'
                : f.type === 'money'
                  ? '0.01'
                  : '1'
            "
            :required="
              !f.optional &&
              !(dialog.type === 'users' && draft.id && f.key === 'password')
            "
            :maxlength="f.type === 'password' ? 72 : 300"
            :autocomplete="f.type === 'password' ? 'new-password' : 'off'"
        /></label>
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div class="dialog-actions">
        <button type="button" :disabled="busy" @click="dialog = null">
          {{ t("取消", "Cancel") }}</button
        ><button class="primary" :disabled="busy">
          {{
            busy
              ? t("提交中…", "Saving…")
              : t(
                  dialog.type === "confirm" ? "确认" : "保存",
                  dialog.type === "confirm" ? "Confirm" : "Save",
                )
          }}
        </button>
      </div>
    </form>
  </dialog>
  <dialog
    v-if="billPreview && project"
    open
    class="bill-preview"
    :aria-label="t('结算单预览', 'Bill preview')"
    @keydown.esc="billPreview = null"
  >
    <div class="dialog-heading print-controls">
      <h2>{{ t("结算单预览", "Bill preview") }}</h2>
      <button
        :aria-label="t('关闭预览', 'Close preview')"
        @click="billPreview = null"
      >
        <X :size="20" />
      </button>
    </div>
    <article class="bill-document">
      <div class="bill-title">
        <h1>{{ t("工程结算单", "Progress bill") }}</h1>
        <strong>#{{ billPreview.id }} · {{ label(billPreview.kind) }}</strong>
      </div>
      <dl class="bill-meta">
        <div>
          <dt>{{ t("项目", "Project") }}</dt>
          <dd>{{ project.name }} · {{ project.code }}</dd>
        </div>
        <div>
          <dt>{{ t("客户", "Customer") }}</dt>
          <dd>{{ project.customer }}</dd>
        </div>
        <div>
          <dt>{{ t("地址", "Site") }}</dt>
          <dd>{{ project.site }}</dd>
        </div>
        <div>
          <dt>{{ t("结算凭据", "Reference") }}</dt>
          <dd>{{ billPreview.reference }}</dd>
        </div>
        <div>
          <dt>{{ t("状态", "Status") }}</dt>
          <dd>{{ label(billPreview.status) }}</dd>
        </div>
        <div>
          <dt>{{ t("到期日", "Due date") }}</dt>
          <dd>{{ billPreview.dueDate }}</dd>
        </div>
      </dl>
      <table class="bill-lines">
        <thead>
          <tr>
            <th>{{ t("工程项目", "Work item") }}</th>
            <th>{{ t("本期工程量", "Period quantity") }}</th>
            <th>{{ t("结算金额", "Gross amount") }}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>{{ workName(billPreview.workId) }}</td>
            <td>
              {{ billPreview.quantity }}
              {{ detail.works.find((x) => x.id === billPreview.workId)?.unit }}
            </td>
            <td>{{ cash(billPreview.gross) }}</td>
          </tr>
        </tbody>
      </table>
      <dl class="bill-totals">
        <div>
          <dt>{{ t("约定保留款", "Agreed retention") }}</dt>
          <dd>
            {{ cash(billPreview.retention) }} ·
            {{
              billPreview.released
                ? t("已释放", "Released")
                : t("未释放", "Held")
            }}
          </dd>
        </div>
        <div>
          <dt>{{ t("当前应收／应付", "Currently due") }}</dt>
          <dd>{{ cash(billPreview.due) }}</dd>
        </div>
        <div>
          <dt>{{ t("净收付", "Net paid") }}</dt>
          <dd>{{ cash(billPreview.net) }}</dd>
        </div>
        <div>
          <dt>{{ t("尚欠金额", "Outstanding") }}</dt>
          <dd>{{ cash(billPreview.balance) }}</dd>
        </div>
      </dl>
      <p v-if="billPreview.released" class="bill-note">
        {{ t("保留款释放凭据", "Retention release reference") }}：{{
          billPreview.releaseReference
        }}
      </p>
      <p class="bill-note">
        {{
          t(
            "工程结算记录，不是税务发票。",
            "Construction settlement record; not a tax invoice.",
          )
        }}
      </p>
    </article>
    <div class="dialog-actions print-controls">
      <button @click="billPreview = null">{{ t("关闭", "Close") }}</button
      ><button class="primary" @click="printPreview">
        {{ t("打印／保存PDF", "Print / save PDF") }}
      </button>
    </div>
  </dialog>
  <div v-if="dialog || billPreview" class="backdrop"></div>
</template>

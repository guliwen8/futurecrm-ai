// ===== STATE =====
const S = {
  token: localStorage.getItem("futurecrm_token"),
  user: JSON.parse(localStorage.getItem("futurecrm_user") || "null"),
  selectedCustomerId: null,
  selectedOrderId: null,
  editingFollowId: null,
  editingContactId: null
};

const $ = (s) => document.querySelector(s);
const $$ = (s) => Array.from(document.querySelectorAll(s));

// ===== API =====
async function api(path, opts = {}) {
  const res = await fetch(path, {
    ...opts,
    headers: {
      "Content-Type": "application/json",
      ...(S.token ? { "X-Auth-Token": S.token } : {}),
      ...(opts.headers || {})
    }
  });
  const payload = await res.json();
  if (!payload.success) throw new Error(payload.message || "请求失败");
  return payload.data;
}

function fd(form) { return Object.fromEntries(new FormData(form).entries()); }

function showEl(id) { $("#" + id).classList.remove("hidden"); }
function hideEl(id) { $("#" + id).classList.add("hidden"); }

// ===== AUTH =====
function afterLogin(data) {
  S.token = data.token; S.user = data;
  localStorage.setItem("futurecrm_token", S.token);
  localStorage.setItem("futurecrm_user", JSON.stringify(S.user));
  hideEl("loginView"); showEl("mainView");
  $("#currentUser").textContent = `${S.user.realName} · ${S.user.role}`;
  if (S.user.role === "ADMIN") { showEl("users"); $$(".admin-only").forEach(b => b.classList.remove("hidden")); }
  navigateTo("dashboard");
}

$("#loginForm").addEventListener("submit", async e => {
  e.preventDefault();
  $("#loginError").textContent = "";
  try { afterLogin(await api("/api/auth/login", { method: "POST", body: JSON.stringify(fd(e.target)) })); }
  catch (err) { $("#loginError").textContent = err.message; }
});

$("#logoutBtn").addEventListener("click", async () => {
  try { await api("/api/auth/logout", { method: "POST", body: "{}" }); } catch (_) {}
  localStorage.removeItem("futurecrm_token"); localStorage.removeItem("futurecrm_user");
  S.token = null; S.user = null;
  showEl("loginView"); hideEl("mainView");
});

// ===== NAVIGATION =====
function navigateTo(viewName) {
  $$(".nav").forEach(b => b.classList.remove("active"));
  const btn = document.querySelector(`.nav[data-view="${viewName}"]`);
  if (btn) btn.classList.add("active");
  $$(".view").forEach(v => v.classList.add("hidden"));
  const view = $("#" + viewName);
  if (view) view.classList.remove("hidden");

  const loaders = { dashboard: loadDashboard, customers: loadCustomers, orders: loadOrders, receipts: loadReceipts, users: loadUsers };
  if (loaders[viewName]) loaders[viewName]();
}

$$(".nav").forEach(b => b.addEventListener("click", () => navigateTo(b.dataset.view)));

// ===== DASHBOARD =====
async function loadDashboard() {
  const d = await api("/api/dashboard");
  const overdueData = await api("/api/receipts/overdue").catch(() => []);
  const stats = await api("/api/system/stats").catch(() => ({}));

  const metrics = [
    ["客户总数", stats.totalCustomers || d.customerCount],
    ["本周新增", d.newCustomersThisWeek],
    ["今日待跟进", d.todayFollowCount],
    ["未完成订单", d.openOrderCount],
    ["本月订单金额", "¥" + Number(d.monthOrderAmount || 0).toFixed(2)],
    ["逾期收款", stats.overdueReceipts || overdueData.length]
  ];
  $("#metrics").innerHTML = metrics.map(([l, v]) =>
    `<article class="metric"><span class="muted">${l}</span><strong>${v}</strong></article>`).join("");

  $("#recentFollows").innerHTML = d.recentFollows?.length
    ? d.recentFollows.map(f => `<article class="list-item"><div class="row-main"><strong>${f.customer_name || "?"}</strong><span class="muted">${f.follow_time || ""}</span></div><p>${(f.content||"").substring(0,60)}</p></article>`).join("")
    : `<article class="list-item muted">暂无跟进记录</article>`;

  $("#overdueReceipts").innerHTML = overdueData.length
    ? overdueData.map(r => `<article class="list-item"><div class="row-main"><strong>${r.customer_name || "?"}</strong><span class="tag danger">${r.status}</span></div><p>应收 ¥${Number(r.receivable_amount||0).toFixed(2)} | 预计 ${r.expected_date||"-"}</p></article>`).join("")
    : `<article class="list-item muted">暂无逾期收款</article>`;

  $("#todayAdvice").innerHTML = d.aiTodayAdvice?.map(t => `<article class="list-item">${t}</article>`).join("") || "";
}
$("#refreshDashboard").addEventListener("click", loadDashboard);

// ===== CUSTOMERS =====
async function loadCustomers() {
  const kw = $("#customerKeyword").value, st = $("#customerStatus").value;
  const params = new URLSearchParams();
  if (kw) params.set("keyword", kw); if (st) params.set("status", st);
  const data = await api("/api/customers?" + params);
  $("#customerList").innerHTML = data.length ? data.map(c => `
    <article class="table-row">
      <div class="row-main">
        <div>
          <strong>${c.name}</strong>
          <p class="muted">${c.industry||"?"} · ${c.phone||"无电话"} · ${c.owner_name||"未分配"} · ${c.stage||"?"}</p>
        </div>
        <div class="row-actions">
          <span class="tag">${c.status}</span>
          <button onclick="openCustomerDetail(${c.id})">详情</button>
          <button class="ghost small" onclick="editCustomer(${c.id})">编辑</button>
          <button class="ghost small danger" onclick="deleteCustomer(${c.id},'${c.name}')">删除</button>
        </div>
      </div>
    </article>`).join("") : `<article class="table-row muted">暂无客户，先新增一个吧。</article>`;
}
$("#searchCustomers").addEventListener("click", loadCustomers);

$("#newCustomerBtn").addEventListener("click", () => {
  $("#customerForm").reset();
  $("#customerForm").id.value = "";
  $("#customerDialog").showModal();
});
$("#cancelCustomer").addEventListener("click", () => $("#customerDialog").close());

async function editCustomer(id) {
  const c = await api(`/api/customers/${id}`);
  const form = $("#customerForm");
  form.reset();
  Object.entries(c).forEach(([k, v]) => {
    const el = form.querySelector(`[name="${k}"]`);
    if (el && v != null) el.value = v;
  });
  $("#customerDialog").showModal();
}

async function deleteCustomer(id, name) {
  if (!confirm(`确认删除客户【${name}】？此操作不可恢复。`)) return;
  try {
    await api(`/api/customers/${id}`, { method: "DELETE" });
    await loadCustomers();
    await loadDashboard();
  } catch (err) { alert("删除失败：" + err.message); }
}

$("#customerForm").addEventListener("submit", async e => {
  e.preventDefault();
  const data = fd(e.target), id = data.id;
  try {
    if (id) { await api(`/api/customers/${id}`, { method: "PUT", body: JSON.stringify(data) }); }
    else { await api("/api/customers", { method: "POST", body: JSON.stringify(data) }); }
    $("#customerDialog").close();
    await loadCustomers();
    await loadDashboard();
  } catch (err) { alert(err.message); }
});

// ===== CUSTOMER DETAIL =====
window.openCustomerDetail = async (id) => {
  S.selectedCustomerId = id;
  const customer = await api(`/api/customers/${id}`);
  const timeline = await api(`/api/customers/${id}/timeline`);
  $("#detailTitle").textContent = customer.name;

  // Follow tab
  renderFollows(timeline.followRecords);
  resetFollowForm();
  $("#followForm").customer_id.value = id;

  // Contacts tab
  renderContacts(timeline.contacts);

  // AI tab
  $("#customerAiResult").textContent = "点击上方按钮生成 AI 洞察。";
  S.editingFollowId = null; S.editingContactId = null;

  // Switch to follows tab by default
  switchDetailTab("tab-follows");
  $("#customerDetailDialog").showModal();
};

function switchDetailTab(tabId) {
  $$("#detailTabs .tab").forEach(t => t.classList.remove("active"));
  document.querySelector(`#detailTabs .tab[data-tab="${tabId}"]`)?.classList.add("active");
  $$(".tab-content").forEach(tc => tc.classList.add("hidden"));
  $("#" + tabId)?.classList.remove("hidden");
}
$$("#detailTabs .tab").forEach(t => t.addEventListener("click", () => switchDetailTab(t.dataset.tab)));

// Follows
function renderFollows(records) {
  $("#followList").innerHTML = records?.length
    ? records.map(f => `<article class="list-item">
        <div class="row-main"><strong>${f.user_name||""}</strong><span class="muted">${f.follow_time||""} · ${f.follow_type||""}</span></div>
        <p>${f.content||""}</p>
        ${f.next_action ? `<p class="muted next">下一步：${f.next_action}</p>` : ""}
        <div class="row-mini"><button class="ghost small" onclick="editFollow(${f.id})">编辑</button><button class="ghost small danger" onclick="deleteFollow(${f.id})">删除</button></div>
      </article>`).join("")
    : `<article class="list-item muted">暂无跟进记录</article>`;
}

function resetFollowForm() {
  const f = $("#followForm");
  f.follow_id.value = ""; f.content.value = ""; f.next_action.value = ""; f.next_follow_time.value = "";
  $("#cancelFollowEdit").classList.add("hidden");
  f.querySelector("button[type=submit]").textContent = "保存跟进";
}

async function editFollow(id) {
  const records = await api(`/api/customers/${S.selectedCustomerId}/follow-records`);
  const record = records.find(r => r.id === id);
  if (!record) return alert("未找到该跟进记录");
  const f = $("#followForm");
  f.follow_id.value = record.id;
  f.content.value = record.content || "";
  f.next_action.value = record.next_action || "";
  f.next_follow_time.value = record.next_follow_time || "";
  f.querySelector("button[type=submit]").textContent = "更新跟进";
  $("#cancelFollowEdit").classList.remove("hidden");
}

async function deleteFollow(id) {
  if (!confirm("确认删除此跟进记录？")) return;
  await api(`/api/follow-records/${id}`, { method: "DELETE" });
  await openCustomerDetail(S.selectedCustomerId);
}

$("#cancelFollowEdit").addEventListener("click", resetFollowForm);

$("#followForm").addEventListener("submit", async e => {
  e.preventDefault();
  const data = fd(e.target);
  const cid = data.customer_id;
  const fid = data.follow_id;
  delete data.customer_id; delete data.follow_id;
  try {
    if (fid) { await api(`/api/follow-records/${fid}`, { method: "PUT", body: JSON.stringify(data) }); }
    else { await api(`/api/customers/${cid}/follow-records`, { method: "POST", body: JSON.stringify(data) }); }
    resetFollowForm();
    await openCustomerDetail(cid);
    await loadDashboard();
  } catch (err) { alert(err.message); }
});

// Contacts
function renderContacts(contacts) {
  $("#contactList").innerHTML = contacts?.length
    ? contacts.map(c => `<article class="list-item">
        <div class="row-main"><strong>${c.name}</strong><span class="muted">${c.position||""} ${c.is_decision_maker ? "· 关键决策人" : ""}</span></div>
        <p class="muted">${c.mobile||""} ${c.wechat ? "· 微信:"+c.wechat : ""} ${c.email||""}</p>
        <div class="row-mini"><button class="ghost small" onclick="editContact(${c.id})">编辑</button><button class="ghost small danger" onclick="deleteContact(${c.id})">删除</button></div>
      </article>`).join("")
    : `<article class="list-item muted">暂无联系人</article>`;
}

function resetContactForm() {
  const f = $("#contactForm");
  f.contact_id.value = ""; f.reset();
  S.editingContactId = null;
  $("#cancelContactEdit").classList.add("hidden");
  f.querySelector("button[type=submit]").textContent = "保存联系人";
}

async function editContact(id) {
  const contacts = await api(`/api/customers/${S.selectedCustomerId}/contacts`);
  const c = contacts.find(co => co.id === id);
  if (!c) return alert("未找到该联系人");
  const f = $("#contactForm");
  f.contact_id.value = c.id;
  f.name.value = c.name || ""; f.position.value = c.position || "";
  f.mobile.value = c.mobile || ""; f.wechat.value = c.wechat || "";
  f.is_decision_maker.checked = c.is_decision_maker === 1;
  f.querySelector("button[type=submit]").textContent = "更新联系人";
  $("#cancelContactEdit").classList.remove("hidden");
}

async function deleteContact(id) {
  if (!confirm("确认删除此联系人？")) return;
  await api(`/api/contacts/${id}`, { method: "DELETE" });
  await openCustomerDetail(S.selectedCustomerId);
}

$("#cancelContactEdit").addEventListener("click", resetContactForm);

$("#contactForm").addEventListener("submit", async e => {
  e.preventDefault();
  const data = fd(e.target), cid = S.selectedCustomerId, coid = data.contact_id;
  delete data.customer_id; delete data.contact_id;
  data.is_decision_maker = $("#contactForm").is_decision_maker.checked ? "1" : "0";
  try {
    if (coid) { await api(`/api/contacts/${coid}`, { method: "PUT", body: JSON.stringify(data) }); }
    else { await api(`/api/customers/${cid}/contacts`, { method: "POST", body: JSON.stringify(data) }); }
    resetContactForm();
    await openCustomerDetail(cid);
  } catch (err) { alert(err.message); }
});

// AI tab
async function callAi(path, body = {}) {
  if (!S.selectedCustomerId) return;
  $("#customerAiResult").textContent = "AI 正在分析...";
  try {
    const r = await api(path, { method: "POST", body: JSON.stringify({ customer_id: S.selectedCustomerId, ...body }) });
    $("#customerAiResult").textContent = r.content;
  } catch (err) { $("#customerAiResult").textContent = "AI 请求失败：" + err.message; }
}
$("#profileAi").addEventListener("click", () => callAi("/api/ai/customer-profile"));
$("#suggestAi").addEventListener("click", () => callAi("/api/ai/follow-suggestion"));
$("#scriptAi").addEventListener("click", () => callAi("/api/ai/sales-script", { channel: "微信" }));
$("#summarizeFollow").addEventListener("click", async () => {
  const content = $("#followForm").content.value;
  if (!content.trim()) return alert("请先填写跟进内容");
  try {
    const r = await api("/api/ai/follow-summary", { method: "POST", body: JSON.stringify({ content }) });
    $("#customerAiResult").textContent = r.content;
    switchDetailTab("tab-ai");
  } catch (err) { alert(err.message); }
});

$("#closeDetail").addEventListener("click", () => $("#customerDetailDialog").close());

// ===== ORDERS =====
async function loadOrders() {
  const kw = $("#orderKeyword").value, st = $("#orderStatus").value;
  const params = new URLSearchParams();
  if (kw) params.set("keyword", kw); if (st) params.set("status", st);
  const data = await api("/api/orders?" + params);
  $("#orderList").innerHTML = data.length ? data.map(o => `
    <article class="table-row">
      <div class="row-main">
        <div>
          <strong>${o.order_no}</strong>
          <p class="muted">${o.customer_name||"?"} · ${o.order_date||""} · ¥${Number(o.amount||0).toFixed(2)}</p>
        </div>
        <div class="row-actions">
          <span class="tag">${o.status}</span>
          <button onclick="openOrderDetail(${o.id})">详情</button>
          <button class="ghost small" onclick="editOrder(${o.id})">编辑</button>
          <button class="ghost small danger" onclick="deleteOrder(${o.id},'${o.order_no}')">删除</button>
        </div>
      </div>
    </article>`).join("") : `<article class="table-row muted">暂无订单</article>`;
}
$("#searchOrders").addEventListener("click", loadOrders);

$("#newOrderBtn").addEventListener("click", () => {
  $("#orderForm").reset();
  $("#orderForm").id.value = "";
  $("#orderForm").order_date.value = new Date().toISOString().split("T")[0];
  $("#orderDialog").showModal();
});
$("#cancelOrder").addEventListener("click", () => $("#orderDialog").close());

async function editOrder(id) {
  const o = await api(`/api/orders/${id}`);
  const form = $("#orderForm");
  form.reset();
  Object.entries(o).forEach(([k, v]) => {
    if (k === "items") return;
    const el = form.querySelector(`[name="${k}"]`);
    if (el && v != null) el.value = v;
  });
  $("#orderDialog").showModal();
}

async function deleteOrder(id, orderNo) {
  if (!confirm(`确认删除订单【${orderNo}】？`)) return;
  await api(`/api/orders/${id}`, { method: "DELETE" });
  await loadOrders();
}

$("#orderForm").addEventListener("submit", async e => {
  e.preventDefault();
  const data = fd(e.target), id = data.id;
  try {
    if (id) { await api(`/api/orders/${id}`, { method: "PUT", body: JSON.stringify(data) }); }
    else { await api("/api/orders", { method: "POST", body: JSON.stringify(data) }); }
    $("#orderDialog").close();
    await loadOrders();
    await loadDashboard();
  } catch (err) { alert(err.message); }
});

// Order detail
window.openOrderDetail = async (id) => {
  S.selectedOrderId = id;
  const o = await api(`/api/orders/${id}`);
  $("#orderDetailTitle").textContent = o.order_no;
  const itemsHtml = o.items?.length
    ? o.items.map(i => `<p>${i.item_name} x${i.quantity} @¥${Number(i.unit_price||0).toFixed(2)} = ¥${Number(i.amount||0).toFixed(2)}</p>`).join("")
    : "无明细";
  $("#orderInfo").innerHTML = `
    <article class="list-item"><strong>订单编号：</strong>${o.order_no}</article>
    <article class="list-item"><strong>客户：</strong>${o.customer_name||"-"}</article>
    <article class="list-item"><strong>联系人：</strong>${o.contact_name||"-"}</article>
    <article class="list-item"><strong>业务员：</strong>${o.owner_name||"-"}</article>
    <article class="list-item"><strong>订单日期：</strong>${o.order_date||"-"}</article>
    <article class="list-item"><strong>金额：</strong>¥${Number(o.amount||0).toFixed(2)} | 已收 ¥${Number(o.paid_amount||0).toFixed(2)}</article>
    <article class="list-item"><strong>状态：</strong><span class="tag">${o.status}</span></article>
    <article class="list-item"><strong>送货地址：</strong>${o.delivery_address||"-"}</article>
    <article class="list-item"><strong>备注：</strong>${o.remark||"-"}</article>
    <article class="list-item"><strong>明细：</strong>${itemsHtml}</article>`;
  $("#orderRiskResult").textContent = "";
  $("#orderDetailDialog").showModal();
};
$("#closeOrderDetail").addEventListener("click", () => $("#orderDetailDialog").close());

$("#orderRiskBtn").addEventListener("click", async () => {
  if (!S.selectedOrderId) return;
  $("#orderRiskResult").textContent = "AI 正在分析订单风险...";
  try {
    const r = await api("/api/ai/order-risk", { method: "POST", body: JSON.stringify({ order_id: S.selectedOrderId }) });
    $("#orderRiskResult").textContent = r.content;
  } catch (err) { $("#orderRiskResult").textContent = "检查失败：" + err.message; }
});

// ===== RECEIPTS =====
async function loadReceipts() {
  const st = $("#receiptStatus").value;
  const url = st ? `/api/receipts?status=${st}` : "/api/receipts";
  const data = await api(url);
  const statusLabel = { UNPAID: "未收款", PARTIAL: "部分收款", PAID: "已收款", OVERDUE: "逾期" };
  $("#receiptList").innerHTML = data.length ? data.map(r => `
    <article class="table-row">
      <div class="row-main">
        <div>
          <strong>${r.customer_name||"?"}</strong>
          <p class="muted">订单 ${r.order_no||"-"} · 应收 ¥${Number(r.receivable_amount||0).toFixed(2)} · 实收 ¥${Number(r.received_amount||0).toFixed(2)} · 预计 ${r.expected_date||"-"}</p>
        </div>
        <div class="row-actions">
          <span class="tag ${r.status==='OVERDUE'?'danger':''}">${statusLabel[r.status]||r.status}</span>
          <button class="ghost small" onclick="editReceipt(${r.id})">编辑</button>
          <button class="ghost small danger" onclick="deleteReceipt(${r.id})">删除</button>
        </div>
      </div>
    </article>`).join("") : `<article class="table-row muted">暂无收款记录</article>`;
}
$("#searchReceipts").addEventListener("click", loadReceipts);

$("#newReceiptBtn").addEventListener("click", () => {
  $("#receiptForm").reset();
  $("#receiptForm").id.value = "";
  $("#receiptDialog").showModal();
});
$("#cancelReceipt").addEventListener("click", () => $("#receiptDialog").close());

async function editReceipt(id) {
  const r = await api(`/api/receipts/${id}`);
  const form = $("#receiptForm");
  form.reset();
  Object.entries(r).forEach(([k, v]) => {
    const el = form.querySelector(`[name="${k}"]`);
    if (el && v != null) el.value = v;
  });
  $("#receiptDialog").showModal();
}

async function deleteReceipt(id) {
  if (!confirm("确认删除此收款记录？")) return;
  await api(`/api/receipts/${id}`, { method: "DELETE" });
  await loadReceipts();
}

$("#receiptForm").addEventListener("submit", async e => {
  e.preventDefault();
  const data = fd(e.target), id = data.id;
  try {
    if (id) { await api(`/api/receipts/${id}`, { method: "PUT", body: JSON.stringify(data) }); }
    else { await api("/api/receipts", { method: "POST", body: JSON.stringify(data) }); }
    $("#receiptDialog").close();
    await loadReceipts();
    await loadDashboard();
  } catch (err) { alert(err.message); }
});

// ===== USERS (ADMIN ONLY) =====
async function loadUsers() {
  const data = await api("/api/users");
  const roles = { ADMIN: "管理员", MANAGER: "管理者", SALES: "销售", FINANCE: "财务" };
  $("#userList").innerHTML = data.map(u => `
    <article class="table-row">
      <div class="row-main">
        <div>
          <strong>${u.real_name}</strong>
          <p class="muted">${u.username} · ${roles[u.role]||u.role} · ${u.email||""} ${u.phone||""}</p>
        </div>
        <div class="row-actions">
          <span class="tag ${u.status==='ACTIVE'?'':'danger'}">${u.status==='ACTIVE'?'启用':'禁用'}</span>
          <button class="ghost small" onclick="editUser(${u.id})">编辑</button>
          <button class="ghost small" onclick="resetUserPassword(${u.id},'${u.username}')">重置密码</button>
          <button class="ghost small" onclick="toggleUserStatus(${u.id},'${u.status}')">${u.status==='ACTIVE'?'禁用':'启用'}</button>
        </div>
      </div>
    </article>`).join("");
}

$("#newUserBtn").addEventListener("click", () => {
  $("#userForm").reset();
  $("#userForm").id.value = "";
  $("#userDialog").showModal();
});
$("#cancelUser").addEventListener("click", () => $("#userDialog").close());

async function editUser(id) {
  const u = await api(`/api/users/${id}`);
  const form = $("#userForm");
  form.reset();
  Object.entries(u).forEach(([k, v]) => {
    const el = form.querySelector(`[name="${k}"]`);
    if (el && v != null) el.value = v;
  });
  form.password.value = "";
  $("#userDialog").showModal();
}

async function resetUserPassword(id, username) {
  if (!confirm(`确认重置用户【${username}】的密码为 123456？`)) return;
  await api(`/api/users/${id}/password`, { method: "PUT", body: JSON.stringify({ password: "123456" }) });
  alert("密码已重置为 123456");
}

async function toggleUserStatus(id, currentStatus) {
  const newStatus = currentStatus === "ACTIVE" ? "INACTIVE" : "ACTIVE";
  const action = newStatus === "ACTIVE" ? "启用" : "禁用";
  if (!confirm(`确认${action}此用户？`)) return;
  await api(`/api/users/${id}/status`, { method: "PUT", body: JSON.stringify({ status: newStatus }) });
  await loadUsers();
}

$("#userForm").addEventListener("submit", async e => {
  e.preventDefault();
  const data = fd(e.target), id = data.id;
  try {
    if (id) { await api(`/api/users/${id}`, { method: "PUT", body: JSON.stringify(data) }); }
    else { await api("/api/users", { method: "POST", body: JSON.stringify(data) }); }
    $("#userDialog").close();
    await loadUsers();
  } catch (err) { alert(err.message); }
});

// ===== AI ASSISTANT =====
$("#askAiBtn").addEventListener("click", async () => {
  const q = $("#aiQuestion").value;
  if (!q.trim()) return;
  $("#aiAnswer").textContent = "AI 正在思考...";
  try {
    const r = await api("/api/ai/chat", { method: "POST", body: JSON.stringify({ question: q }) });
    $("#aiAnswer").textContent = r.content;
  } catch (err) { $("#aiAnswer").textContent = "AI 请求失败：" + err.message; }
});

// ===== INIT =====
(async function init() {
  if (S.token) {
    try {
      const me = await api("/api/auth/me");
      S.user = me;
      localStorage.setItem("futurecrm_user", JSON.stringify(S.user));
      afterLogin({ token: S.token, ...me });
    } catch (_) {
      localStorage.removeItem("futurecrm_token");
      localStorage.removeItem("futurecrm_user");
      showEl("loginView"); hideEl("mainView");
    }
  } else {
    showEl("loginView"); hideEl("mainView");
  }
})();

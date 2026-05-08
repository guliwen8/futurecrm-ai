const state = {
  token: localStorage.getItem("futurecrm_token"),
  user: JSON.parse(localStorage.getItem("futurecrm_user") || "null"),
  selectedCustomerId: null
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(state.token ? {"X-Auth-Token": state.token} : {}),
      ...(options.headers || {})
    }
  });
  const payload = await response.json();
  if (!payload.success) throw new Error(payload.message || "请求失败");
  return payload.data;
}

function formData(form) {
  return Object.fromEntries(new FormData(form).entries());
}

function showMain() {
  $("#loginView").classList.add("hidden");
  $("#mainView").classList.remove("hidden");
  $("#currentUser").textContent = state.user ? `${state.user.realName} · ${state.user.role}` : "";
  loadDashboard();
  loadCustomers();
  loadOrders();
}

function showLogin() {
  $("#loginView").classList.remove("hidden");
  $("#mainView").classList.add("hidden");
}

$("#loginForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  $("#loginError").textContent = "";
  try {
    const data = await api("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(formData(event.target))
    });
    state.token = data.token;
    state.user = data;
    localStorage.setItem("futurecrm_token", state.token);
    localStorage.setItem("futurecrm_user", JSON.stringify(state.user));
    showMain();
  } catch (error) {
    $("#loginError").textContent = error.message;
  }
});

$("#logoutBtn").addEventListener("click", async () => {
  try {
    await api("/api/auth/logout", {method: "POST", body: "{}"});
  } catch (_) {
  }
  localStorage.removeItem("futurecrm_token");
  localStorage.removeItem("futurecrm_user");
  state.token = null;
  state.user = null;
  showLogin();
});

$$(".nav").forEach(button => {
  button.addEventListener("click", () => {
    $$(".nav").forEach(item => item.classList.remove("active"));
    button.classList.add("active");
    $$(".view").forEach(view => view.classList.add("hidden"));
    $("#" + button.dataset.view).classList.remove("hidden");
  });
});

async function loadDashboard() {
  const data = await api("/api/dashboard");
  const metrics = [
    ["客户总数", data.customerCount],
    ["本周新增", data.newCustomersThisWeek],
    ["今日待跟进", data.todayFollowCount],
    ["未完成订单", data.openOrderCount],
    ["本月订单金额", Number(data.monthOrderAmount || 0).toFixed(2)]
  ];
  $("#metrics").innerHTML = metrics.map(([label, value]) => `
    <article class="metric"><span class="muted">${label}</span><strong>${value}</strong></article>
  `).join("");
  $("#recentFollows").innerHTML = data.recentFollows.length
    ? data.recentFollows.map(item => `
      <article class="list-item">
        <div class="row-main"><strong>${item.customer_name || "未知客户"}</strong><span class="muted">${item.follow_time || ""}</span></div>
        <p>${item.content || ""}</p>
      </article>
    `).join("")
    : `<article class="list-item muted">暂无跟进记录</article>`;
  $("#todayAdvice").innerHTML = data.aiTodayAdvice.map(item => `<article class="list-item">${item}</article>`).join("");
}

$("#refreshDashboard").addEventListener("click", loadDashboard);

async function loadCustomers() {
  const params = new URLSearchParams();
  if ($("#customerKeyword").value) params.set("keyword", $("#customerKeyword").value);
  if ($("#customerStatus").value) params.set("status", $("#customerStatus").value);
  const data = await api("/api/customers?" + params.toString());
  $("#customerList").innerHTML = data.length ? data.map(item => `
    <article class="table-row">
      <div class="row-main">
        <div>
          <strong>${item.name}</strong>
          <p class="muted">${item.industry || "未填写行业"} · ${item.phone || "未填写电话"} · ${item.owner_name || "未分配"}</p>
        </div>
        <div>
          <span class="tag">${item.status}</span>
          <button onclick="openCustomerDetail(${item.id})">详情</button>
        </div>
      </div>
    </article>
  `).join("") : `<article class="table-row muted">暂无客户，先新增一个吧。</article>`;
}

$("#searchCustomers").addEventListener("click", loadCustomers);
$("#newCustomerBtn").addEventListener("click", () => {
  $("#customerForm").reset();
  $("#customerDialog").showModal();
});
$("#cancelCustomer").addEventListener("click", () => $("#customerDialog").close());

$("#customerForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const data = formData(event.target);
  try {
    await api("/api/customers", {method: "POST", body: JSON.stringify(data)});
    $("#customerDialog").close();
    await loadCustomers();
    await loadDashboard();
  } catch (error) {
    alert(error.message);
  }
});

window.openCustomerDetail = async (id) => {
  state.selectedCustomerId = id;
  const customer = await api(`/api/customers/${id}`);
  const timeline = await api(`/api/customers/${id}/timeline`);
  $("#detailTitle").textContent = customer.name;
  $("#followForm").customer_id.value = id;
  $("#followList").innerHTML = timeline.followRecords.length
    ? timeline.followRecords.map(item => `
      <article class="list-item">
        <div class="row-main"><strong>${item.user_name || ""}</strong><span class="muted">${item.follow_time || ""}</span></div>
        <p>${item.content || ""}</p>
        ${item.next_action ? `<p class="muted">下一步：${item.next_action}</p>` : ""}
      </article>
    `).join("")
    : `<article class="list-item muted">暂无跟进记录</article>`;
  $("#customerAiResult").textContent = "点击上方按钮生成 AI 洞察。";
  $("#customerDetailDialog").showModal();
};

$("#closeDetail").addEventListener("click", () => $("#customerDetailDialog").close());

$("#followForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const data = formData(event.target);
  try {
    await api(`/api/customers/${data.customer_id}/follow-records`, {method: "POST", body: JSON.stringify(data)});
    await openCustomerDetail(data.customer_id);
    await loadDashboard();
  } catch (error) {
    alert(error.message);
  }
});

$("#summarizeFollow").addEventListener("click", async () => {
  const content = $("#followForm").content.value;
  if (!content.trim()) return alert("请先填写跟进内容");
  const result = await api("/api/ai/follow-summary", {method: "POST", body: JSON.stringify({content})});
  $("#followForm").ai_summary = result.content;
  $("#customerAiResult").textContent = result.content;
});

async function customerAi(path, body = {}) {
  if (!state.selectedCustomerId) return;
  $("#customerAiResult").textContent = "AI 正在分析...";
  try {
    const result = await api(path, {
      method: "POST",
      body: JSON.stringify({customer_id: state.selectedCustomerId, ...body})
    });
    $("#customerAiResult").textContent = result.content;
  } catch (error) {
    $("#customerAiResult").textContent = `AI 请求失败：${error.message}`;
  }
}

$("#profileAi").addEventListener("click", () => customerAi("/api/ai/customer-profile"));
$("#suggestAi").addEventListener("click", () => customerAi("/api/ai/follow-suggestion"));
$("#scriptAi").addEventListener("click", () => customerAi("/api/ai/sales-script", {channel: "微信"}));

async function loadOrders() {
  const data = await api("/api/orders");
  $("#orderList").innerHTML = data.length ? data.map(item => `
    <article class="table-row">
      <div class="row-main">
        <div>
          <strong>${item.order_no}</strong>
          <p class="muted">${item.customer_name || "未知客户"} · ${item.order_date || ""}</p>
        </div>
        <div>
          <span class="tag">${item.status}</span>
          <strong>${Number(item.amount || 0).toFixed(2)}</strong>
        </div>
      </div>
    </article>
  `).join("") : `<article class="table-row muted">暂无订单。第一版已接好 API，可从接口或下一步页面表单新增。</article>`;
}

$("#newOrderBtn").addEventListener("click", () => {
  alert("订单新增 API 已完成，页面表单会在下一步补齐。");
});

$("#askAiBtn").addEventListener("click", async () => {
  const question = $("#aiQuestion").value;
  if (!question.trim()) return;
  $("#aiAnswer").textContent = "AI 正在思考...";
  try {
    const result = await api("/api/ai/chat", {method: "POST", body: JSON.stringify({question})});
    $("#aiAnswer").textContent = result.content;
  } catch (error) {
    $("#aiAnswer").textContent = `AI 请求失败：${error.message}`;
  }
});

if (state.token) {
  showMain();
} else {
  showLogin();
}

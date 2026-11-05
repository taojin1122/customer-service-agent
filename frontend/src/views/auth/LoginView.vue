<template>
  <div class="login-page">
    <!-- 背景层：三团低透明度彩色柔光 + 细网格。
         浅色下不能靠"深色底 + 高光"制造层次，只能靠颜色的低透明度叠加，
         所以透明度必须压得很低（0.10~0.20），否则会变成水彩画 -->
    <div class="bg-glow bg-glow--blue" aria-hidden="true"></div>
    <div class="bg-glow bg-glow--violet" aria-hidden="true"></div>
    <div class="bg-glow bg-glow--cyan" aria-hidden="true"></div>
    <div class="bg-grid" aria-hidden="true"></div>

    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">易</span>
        <span class="brand-name">智能客服 Agent</span>
      </div>
      <span class="brand-tag">MULTI-TENANT SAAS</span>
    </header>

    <main class="stage">
      <section class="intro">
        <p class="eyebrow">CUSTOMER SERVICE INTELLIGENCE</p>
        <h1>
          让每一次对话<br />
          <span class="grad">都有据可依</span>
        </h1>
        <p class="lead">
          意图识别、知识检索、工具调用全链路可见。会话与知识库按租户、按坐席双重收敛，
          不是黑盒，也不靠猜。
        </p>

        <!-- 数字来自代码事实：6 类意图见 IntentClassifier.KEYWORDS，
             4 个入口见 AGENTS.md §9.1，100% 留痕指每条消息都落 cs_message -->
        <ul class="facts">
          <li>
            <span class="fact-num">6</span>
            <span class="fact-label">类业务意图</span>
          </li>
          <li>
            <span class="fact-num">4</span>
            <span class="fact-label">端协同</span>
          </li>
          <li>
            <span class="fact-num">100<span class="unit">%</span></span>
            <span class="fact-label">对话留痕</span>
          </li>
        </ul>
      </section>

      <section class="panel">
        <div class="panel-inner">
          <header class="panel-head">
            <h2>{{ isPlatformMode ? '平台运营登录' : '登录控制台' }}</h2>
            <p>{{ isPlatformMode ? '仅限平台运营与运维人员' : '使用租户管理员或坐席账号' }}</p>
          </header>

          <form novalidate @submit.prevent="onSubmit">
            <div class="field">
              <label for="username">用户名</label>
              <input
                id="username"
                v-model.trim="form.username"
                type="text"
                autocomplete="username"
                placeholder="请输入用户名"
                :disabled="loading"
              />
            </div>

            <div class="field">
              <label for="password">密码</label>
              <input
                id="password"
                v-model="form.password"
                type="password"
                autocomplete="current-password"
                placeholder="请输入密码"
                :disabled="loading"
              />
            </div>

            <!-- role="alert"：只变红边框时读屏用户完全不知道发生了什么 -->
            <p v-if="error" ref="errorRef" class="form-error" role="alert" tabindex="-1">
              {{ error }}
            </p>

            <button type="submit" class="submit" :disabled="loading">
              <span>{{ loading ? '验证中…' : '进入控制台' }}</span>
            </button>
          </form>

          <footer class="panel-foot">
            <router-link v-if="!isPlatformMode" to="/platform/login">平台运营登录</router-link>
            <router-link v-else to="/login">返回租户登录</router-link>
          </footer>
        </div>
      </section>
    </main>

    <footer class="foot">
      <span>© 2026 智能客服平台</span>
      <span class="foot-dot" aria-hidden="true"></span>
      <span>v1.0.0</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Role } from '@/constants/role'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isPlatformMode = computed(() => route.path.startsWith('/platform/login'))

// 用户名全库唯一，只凭用户名 + 密码即可登录，不再需要租户编码
const form = ref({ username: '', password: '' })
const error = ref('')
const loading = ref(false)
const errorRef = ref<HTMLElement | null>(null)

function fail(message: string) {
  error.value = message
  // 提交失败后把焦点移到错误提示：键盘与读屏用户需要立刻知道失败原因，
  // 否则焦点还停在按钮上，看起来像"点了没反应"
  nextTick(() => errorRef.value?.focus())
}

async function onSubmit() {
  error.value = ''
  if (!form.value.username) {
    return fail('请输入用户名')
  }
  if (!form.value.password) {
    return fail('请输入密码')
  }

  loading.value = true
  try {
    const user = isPlatformMode.value
      ? await auth.loginAsPlatform({ username: form.value.username, password: form.value.password })
      : await auth.loginAsTenant({ ...form.value })

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const fallback = user.platform
      ? '/platform/tenants'
      : user.role === Role.ADMIN
        ? '/tenant/overview'
        : '/console'

    await router.replace(redirect || fallback)
  } catch (e) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    fail(err.response?.data?.message || err.message || '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/**
 * 登录页：明亮基调的品牌页。
 *
 * 定位见 MASTER.md §2.4——它只被看几秒，目标是第一印象，因此允许柔光、
 * 装饰渐变与入场动画；后台工作台仍然禁用这些（理由是长时间阅读会疲劳、且拖慢渲染）。
 *
 * 用组件内局部变量而不是 tokens.css 里的全局变量：登录页是固定的品牌视觉，
 * 不该跟随 [data-theme] 切换，也不该反向影响工作台。
 */
.login-page {
  --lp-bg: #f6f8fd;
  --lp-text: #0f172a;
  --lp-text-dim: #475569;
  --lp-text-faint: #64748b;
  --lp-blue: #2563eb;
  --lp-indigo: #6366f1;
  --lp-violet: #7c3aed;

  position: relative;
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 100%;
  overflow: hidden;
  background: var(--lp-bg);
  color: var(--lp-text);
}

/* ========== 背景层 ========== */

.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
  /* 提示合成层，避免 blur 每帧重算导致动画掉帧 */
  will-change: transform;
}

.bg-glow--blue {
  top: -22%;
  left: -10%;
  width: 620px;
  height: 620px;
  background: radial-gradient(circle, rgba(37, 99, 235, 0.2), transparent 66%);
  animation: drift-a 24s ease-in-out infinite alternate;
}

.bg-glow--violet {
  top: -16%;
  right: -14%;
  width: 560px;
  height: 560px;
  background: radial-gradient(circle, rgba(124, 58, 237, 0.16), transparent 64%);
  animation: drift-b 28s ease-in-out infinite alternate;
}

.bg-glow--cyan {
  bottom: -26%;
  left: 34%;
  width: 520px;
  height: 520px;
  background: radial-gradient(circle, rgba(6, 182, 212, 0.14), transparent 66%);
  animation: drift-c 32s ease-in-out infinite alternate;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(15, 23, 42, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, 0.045) 1px, transparent 1px);
  background-size: 56px 56px;
  /* 遮罩让网格从中心向外淡出：满屏等强网格会显得廉价，淡出才有纵深 */
  mask-image: radial-gradient(ellipse 78% 62% at 50% 32%, #000 18%, transparent 76%);
  -webkit-mask-image: radial-gradient(ellipse 78% 62% at 50% 32%, #000 18%, transparent 76%);
  pointer-events: none;
}

@keyframes drift-a {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(56px, 34px, 0) scale(1.1);
  }
}

@keyframes drift-b {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(-44px, 30px, 0) scale(1.08);
  }
}

@keyframes drift-c {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(30px, -40px, 0) scale(1.12);
  }
}

/* ========== 顶部 ========== */

.topbar {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 40px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(140deg, var(--lp-blue), var(--lp-violet));
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.32);
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.brand-tag {
  padding: 4px 10px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  color: var(--lp-text-faint);
  font-family: var(--font-number);
  font-size: 10px;
  letter-spacing: 0.18em;
}

/* ========== 主区 ========== */

.stage {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 72px;
  align-items: center;
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 24px 40px 48px;
}

/* ---------- 左侧文案 ---------- */

.eyebrow {
  margin-bottom: 20px;
  color: var(--lp-text-faint);
  font-family: var(--font-number);
  font-size: 11px;
  letter-spacing: 0.24em;
}

.intro h1 {
  margin-bottom: 20px;
  font-size: clamp(32px, 3.4vw, 46px);
  font-weight: 700;
  line-height: 1.22;
  letter-spacing: -0.02em;
}

.grad {
  background: linear-gradient(96deg, #2563eb 0%, #6366f1 46%, #7c3aed 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.lead {
  max-width: 420px;
  color: var(--lp-text-dim);
  font-size: 14px;
  line-height: 1.85;
}

.facts {
  display: flex;
  gap: 40px;
  margin-top: 40px;
  padding-top: 24px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.facts li {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.fact-num {
  background: linear-gradient(135deg, #2563eb, #6366f1);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-family: var(--font-number);
  font-size: 26px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}

.fact-num .unit {
  font-size: 15px;
  font-weight: 500;
}

.fact-label {
  color: var(--lp-text-faint);
  font-size: 12px;
}

/* ---------- 右侧卡片 ---------- */

.panel {
  display: flex;
  justify-content: flex-end;
}

.panel-inner {
  width: 100%;
  max-width: 400px;
  padding: 32px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #fcfdff 100%);
  /* 双层阴影：一层贴边细影定轮廓，一层大扩散投影营造浮起感。
     浅色卡片只用大投影会显得"糊"，只用小阴影又浮不起来 */
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 20px 48px rgba(15, 23, 42, 0.09);
  animation: panel-rise 480ms cubic-bezier(0.16, 1, 0.3, 1) both;
}

@keyframes panel-rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.panel-head {
  margin-bottom: 24px;
}

.panel-head h2 {
  margin-bottom: 6px;
  font-size: 19px;
  font-weight: 600;
}

.panel-head p {
  color: var(--lp-text-dim);
  font-size: 13px;
}

.field {
  margin-bottom: 16px;
}

.field label {
  display: block;
  margin-bottom: 7px;
  color: #334155;
  font-size: 12.5px;
  font-weight: 500;
}

.field input {
  width: 100%;
  height: 42px;
  padding: 0 13px;
  border: 1px solid #d7dfea;
  border-radius: 9px;
  background: #fff;
  color: var(--lp-text);
  font-size: 14px;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;
}

.field input::placeholder {
  color: #94a3b8;
}

.field input:hover:not(:disabled) {
  border-color: #b6c2d4;
}

/* 聚焦用柔光而非加粗边框：粗边框会改变输入框视觉高度，导致下方元素轻微跳动 */
.field input:focus {
  outline: none;
  border-color: var(--lp-blue);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
}

.field input:disabled {
  background: #f8fafc;
  cursor: not-allowed;
}

.form-error {
  margin-bottom: 14px;
  padding: 9px 12px;
  border: 1px solid rgba(220, 38, 38, 0.18);
  border-radius: 8px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 13px;
}

.form-error:focus {
  outline: 2px solid #dc2626;
  outline-offset: 1px;
}

.submit {
  position: relative;
  width: 100%;
  height: 44px;
  margin-top: 4px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6 0%, #4f46e5 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.02em;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(59, 130, 246, 0.32);
  transition:
    box-shadow 200ms ease,
    transform 200ms ease,
    filter 200ms ease;
}

/* 悬停只提亮与加光晕，不做位移：位移会让按钮看起来在抖，连续悬停时很廉价 */
.submit:hover:not(:disabled) {
  filter: brightness(1.06);
  box-shadow: 0 14px 30px rgba(79, 70, 229, 0.38);
}

.submit:active:not(:disabled) {
  transform: translateY(1px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.3);
}

.submit:disabled {
  filter: saturate(0.6) brightness(1.02);
  cursor: not-allowed;
  box-shadow: none;
}

.panel-foot {
  margin-top: 20px;
  text-align: center;
  font-size: 12.5px;
}

.panel-foot a {
  color: var(--lp-text-faint);
  transition: color 150ms ease;
}

.panel-foot a:hover {
  color: var(--lp-blue);
}

/* ========== 底部 ========== */

.foot {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 20px;
  color: #94a3b8;
  font-size: 11.5px;
}

.foot-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: currentColor;
}

/* ========== 响应式 ========== */

/* 中等宽度先压缩左栏而不是直接隐藏：保留文案但优先保证表单可读 */
@media (max-width: 1080px) {
  .stage {
    gap: 40px;
    padding: 16px 32px 40px;
  }

  .intro h1 {
    font-size: 32px;
  }

  .facts {
    gap: 28px;
  }
}

/* 窄屏只留表单：登录是"马上要进去"的动作，文案再多也只会把按钮挤出首屏 */
@media (max-width: 860px) {
  .stage {
    grid-template-columns: 1fr;
    justify-items: center;
    padding: 16px 20px 32px;
  }

  .intro {
    display: none;
  }

  .panel {
    justify-content: center;
  }

  .topbar {
    padding: 18px 20px;
  }
}
</style>

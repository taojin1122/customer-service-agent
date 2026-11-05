import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'

/**
 * 退出登录的唯一实现。
 *
 * 为什么抽出来：工作台侧栏与租户端布局都要这个按钮，之前两处各写了一份。
 * 那份重复里最关键的不是 DOM，而是**执行顺序**——先清业务缓存、再清令牌、最后跳转。
 * 顺序反了，清缓存过程中残留的请求会带着已失效的令牌打到后端触发 401，
 * 与这里的 replace 撞在一起，表现为页面闪两次。
 * 这种"顺序即正确性"的逻辑复制一份出去，等于把隐患复制一份。
 *
 * 依赖 useRouter，所以只能在 setup 内调用（composable 的常规约束）。
 */
export function useLogout() {
  const router = useRouter()
  const auth = useAuthStore()
  const chat = useChatStore()

  async function logout() {
    chat.reset()
    auth.logout()
    await router.replace('/login')
  }

  return { logout }
}

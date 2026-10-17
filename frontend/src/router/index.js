import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useOrgStore } from '@/stores/org'
import { TOKEN_KEY } from '@/utils/request'

const routes = [
  {
    // 根路径：由全局守卫解析组织上下文后跳转（lastOrgId 优先 / 无组织引导创建）
    path: '/',
    name: 'root',
    component: () => import('@/views/RootView.vue'),
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    // 无组织用户引导页（PRD FR-A02：无组织则引导创建）
    path: '/welcome',
    name: 'welcome',
    component: () => import('@/views/WelcomeView.vue'),
  },
  {
    // 工作台布局（T4.2）：侧边栏 + 顶栏，路由按组织上下文组织
    path: '/org/:orgId',
    component: () => import('@/views/workspace/WorkspaceLayout.vue'),
    children: [
      { path: '', redirect: (to) => `/org/${to.params.orgId}/projects` },
      { path: 'projects', name: 'projects', component: () => import('@/views/projects/ProjectsView.vue') },
      { path: 'members', name: 'members', component: () => import('@/views/members/MembersView.vue') },
      {
        // 项目详情（任务 / 看板 / 项目成员）
        path: 'projects/:projectId',
        component: () => import('@/views/projects/ProjectDetailView.vue'),
        children: [
          { path: '', redirect: (to) => `/org/${to.params.orgId}/projects/${to.params.projectId}/tasks` },
          { path: 'tasks', name: 'project-tasks', component: () => import('@/views/tasks/TasksView.vue') },
          { path: 'kanban', name: 'project-kanban', component: () => import('@/views/kanban/KanbanView.vue') },
          { path: 'members', name: 'project-members', component: () => import('@/views/projects/ProjectMembersView.vue') },
        ],
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const hasToken = !!localStorage.getItem(TOKEN_KEY)

  // 未登录访问受保护页面 → 登录页（携带回跳地址）
  if (!to.meta.public && !hasToken) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 已登录访问登录页 → 进入工作台入口
  if (to.path === '/login' && hasToken) {
    return { path: '/' }
  }

  // 根路径：解析组织上下文（lastOrgId 优先，其次第一个所属组织；无组织引导创建）
  if (to.path === '/') {
    const orgStore = useOrgStore()
    try {
      const orgs = await orgStore.loadOrgs()
      const orgId = orgStore.resolveOrg(orgs)
      return orgId ? { path: `/org/${orgId}/projects` } : { path: '/welcome' }
    } catch (e) {
      // 组织列表拉取失败（如组织服务未就绪）：已登录用户停留引导页
      return { path: '/welcome' }
    }
  }
  return true
})

export default router

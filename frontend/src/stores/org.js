import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as orgApi from '@/api/org'
import { createOrg as apiCreateOrg } from '@/api/org'
import router from '@/router'

const LAST_ORG_KEY = 'tf_last_org'

/** 组织上下文（T4.3）：仅列出所属组织；切换后重置上下文并持久化 lastOrgId */
export const useOrgStore = defineStore('org', () => {
  const orgs = ref([])
  const loaded = ref(false)

  const currentOrg = computed(() => {
    const orgId = router.currentRoute.value.params.orgId
    return orgs.value.find((o) => o.id === orgId) || null
  })

  /** 拉取我所属的组织（仅状态正常组织由服务端过滤） */
  async function loadOrgs(force = false) {
    if (loaded.value && !force) return orgs.value
    orgs.value = await orgApi.listMyOrgs()
    loaded.value = true
    return orgs.value
  }

  function getLastOrgId() {
    return localStorage.getItem(LAST_ORG_KEY) || ''
  }

  /** 解析入口组织：lastOrgId 优先，其次第一个所属组织 */
  function resolveOrg(orgList) {
    const last = getLastOrgId()
    if (last && orgList.some((o) => o.id === last)) return last
    return orgList[0]?.id || ''
  }

  /** 切换组织：记录 lastOrgId，重置上下文并跳转到该组织的项目列表 */
  async function switchOrg(orgId) {
    localStorage.setItem(LAST_ORG_KEY, orgId)
    await router.push(`/org/${orgId}/projects`)
  }

  /** 创建组织后自动切换到新组织（FR-O01） */
  async function createOrg(name) {
    const data = await apiCreateOrg({ name })
    await loadOrgs(true)
    await switchOrg(data.id)
    return data
  }

  function reset() {
    orgs.value = []
    loaded.value = false
  }

  return { orgs, loaded, currentOrg, loadOrgs, resolveOrg, switchOrg, createOrg, reset, getLastOrgId }
})

import request from '@/utils/request'

/** 健康检查：GET /api/ping */
export const ping = () => request.get('/ping')

/** 参数校验/未知异常演示：POST /api/ping/echo */
export const echo = (data, params) => request.post('/ping/echo', data, { params })

/** 错误码行为演示：GET /api/ping/demo/{scene} */
export const demo = (scene) => request.get(`/ping/demo/${scene}`)

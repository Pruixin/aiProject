import request from '@/utils/request'

// 后台健身方案管理接口封装：列表、详情、删除。
export function getFitnessPlanList(params) {
  return request({
    url: '/fitness-plan/page',
    method: 'get',
    params,
  })
}

export function getFitnessPlanById(id) {
  return request({
    url: `/fitness-plan/${id}`,
    method: 'get',
  })
}

export function deleteFitnessPlan(id) {
  return request({
    url: `/fitness-plan/${id}`,
    method: 'delete',
  })
}

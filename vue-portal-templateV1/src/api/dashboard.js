import pyRequest from '../utils/pyRequest'

// 数据大屏 API 直接访问 Python 服务，不经过 Java 后端转发。
export function fetchFitnessData() {
  return pyRequest.get('/data/fitness')
}

export function fetchDietData() {
  return pyRequest.get('/data/diet')
}

export function fetchGymPlanData() {
  return pyRequest.get('/data/gym')
}

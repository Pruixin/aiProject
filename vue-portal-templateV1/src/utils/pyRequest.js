import axios from 'axios'
import { ElMessage } from 'element-plus'

const pyRequest = axios.create({
  baseURL: '/py',
  timeout: 15000,
})

pyRequest.interceptors.response.use(
  (response) => response.data,
  (error) => {
    ElMessage.error(error?.message || '数据服务异常')
    return Promise.reject(error)
  },
)

export default pyRequest


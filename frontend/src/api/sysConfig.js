import request from '@/utils/request'

export function getSysConfigList() {
  return request({
    url: '/sys-configs',
    method: 'get'
  })
}

export function getSysConfig(key) {
  return request({
    url: `/sys-configs/${key}`,
    method: 'get'
  })
}

export function getSessionTimeoutConfig() {
  return request({
    url: '/sys-configs/session-timeout',
    method: 'get'
  })
}

export function createSysConfig(data) {
  return request({
    url: '/sys-configs',
    method: 'post',
    data
  })
}

export function updateSysConfig(id, data) {
  return request({
    url: `/sys-configs/${id}`,
    method: 'put',
    data
  })
}

export function deleteSysConfig(id) {
  return request({
    url: `/sys-configs/${id}`,
    method: 'delete'
  })
}

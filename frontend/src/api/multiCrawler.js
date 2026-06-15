import request from '@/utils/request'

export function getPlatformConfigs() {
  return request({
    url: '/multi-crawler/platform-configs',
    method: 'get'
  })
}

export function getPlatformConfig(code) {
  return request({
    url: `/multi-crawler/sources/${code}/config`,
    method: 'get'
  })
}

export function updatePlatformConfig(code, data) {
  return request({
    url: `/multi-crawler/sources/${code}/config`,
    method: 'put',
    data
  })
}

export function enablePlatform(code) {
  return request({
    url: `/multi-crawler/sources/${code}/enable`,
    method: 'post'
  })
}

export function disablePlatform(code) {
  return request({
    url: `/multi-crawler/sources/${code}/disable`,
    method: 'post'
  })
}

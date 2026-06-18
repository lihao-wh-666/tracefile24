import request from '@/utils/request'
import axios from 'axios'

export function getLogArchiveList(params) {
  return request({
    url: '/log-archives',
    method: 'get',
    params
  })
}

export function getLogArchiveById(id) {
  return request({
    url: `/log-archives/${id}`,
    method: 'get'
  })
}

export function createLogArchive(startTime, endTime, logType, remark) {
  const params = { startTime, endTime, logType }
  if (remark) params.remark = remark
  return request({
    url: '/log-archives',
    method: 'post',
    params
  })
}

export function deleteLogArchive(id) {
  return request({
    url: `/log-archives/${id}`,
    method: 'delete'
  })
}

export async function downloadLogArchive(id) {
  const token = localStorage.getItem('hot_event_token')
  const response = await axios.get(`/api/log-archives/${id}/download`, {
    headers: { Authorization: 'Bearer ' + token },
    responseType: 'blob'
  })
  return response
}

export function getLogArchiveStatistics() {
  return request({
    url: '/log-archives/statistics',
    method: 'get'
  })
}

export function triggerAutoArchive() {
  return request({
    url: '/log-archives/auto',
    method: 'post'
  })
}

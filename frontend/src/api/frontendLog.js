import request from '@/utils/request'
import axios from 'axios'

export function reportLog(logData) {
  return axios.post('/api/frontend-logs/report', logData, {
    timeout: 10000
  })
}

export function reportLogsBatch(logs) {
  return axios.post('/api/frontend-logs/batch', logs, {
    timeout: 15000
  })
}

export function getFrontendLogs(params) {
  return request({
    url: '/frontend-logs',
    method: 'get',
    params
  })
}

export function getFrontendLogById(id) {
  return request({
    url: `/frontend-logs/${id}`,
    method: 'get'
  })
}

export function getFrontendLogLevels() {
  return request({
    url: '/frontend-logs/levels',
    method: 'get'
  })
}

export function getFrontendLogStatistics(params) {
  return request({
    url: '/frontend-logs/statistics',
    method: 'get',
    params
  })
}

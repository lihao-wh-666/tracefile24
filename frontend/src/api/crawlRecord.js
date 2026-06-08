import request from '@/utils/request'

export function getCrawlRecordList(params) {
  return request({
    url: '/crawl-records',
    method: 'get',
    params
  })
}

export function getRecentCrawlRecords(params) {
  return request({
    url: '/crawl-records/recent',
    method: 'get',
    params
  })
}

export function getCrawlStatistics(days) {
  return request({
    url: '/crawl-records/statistics',
    method: 'get',
    params: { days }
  })
}

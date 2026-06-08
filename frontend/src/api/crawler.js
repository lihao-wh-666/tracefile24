import request from '@/utils/request'

export function crawlAllSources() {
  return request({
    url: '/crawler/crawl-all',
    method: 'post'
  })
}

export function crawlSource(source) {
  return request({
    url: `/crawler/crawl/${source}`,
    method: 'post'
  })
}

export function getCrawlerSources() {
  return request({
    url: '/crawler/sources',
    method: 'get'
  })
}

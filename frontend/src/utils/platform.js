export const PLATFORM_NAME_MAP = {
  weibo: '微博',
  zhihu: '知乎',
  baidu: '百度',
  wechat: '微信公众号',
  xiaohongshu: '小红书',
  douyin: '抖音',
  bilibili: 'B站',
  local_forum: '本地论坛',
  government: '政务平台'
}

export const PLATFORM_TYPE_MAP = {
  social_media: '社交媒体',
  short_video: '短视频',
  bbs: '论坛',
  government: '政务',
  news: '新闻资讯',
  blog: '博客',
  ecommerce: '电商'
}

export const PLATFORM_COLORS = [
  '#ff4d4f',
  '#1890ff',
  '#52c41a',
  '#faad14',
  '#f5222d',
  '#722ed1',
  '#13c2c2',
  '#fa8c16',
  '#a0d911'
]

export function getPlatformName(code) {
  return PLATFORM_NAME_MAP[code] || code
}

export function getPlatformTypeName(type) {
  if (!type) return type
  const t = type.toLowerCase().replace(/_/g, '_')
  return PLATFORM_TYPE_MAP[t] || type
}

export function getPlatformColor(index) {
  return PLATFORM_COLORS[index % PLATFORM_COLORS.length]
}

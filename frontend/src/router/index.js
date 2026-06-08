import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '数据概览' }
  },
  {
    path: '/events',
    name: 'Events',
    component: () => import('@/views/Events.vue'),
    meta: { title: '热点事件' }
  },
  {
    path: '/events/:id',
    name: 'EventDetail',
    component: () => import('@/views/EventDetail.vue'),
    meta: { title: '事件详情' }
  },
  {
    path: '/crawl-records',
    name: 'CrawlRecords',
    component: () => import('@/views/CrawlRecords.vue'),
    meta: { title: '抓取记录' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 热点事件检测系统` : '热点事件检测系统'
  next()
})

export default router

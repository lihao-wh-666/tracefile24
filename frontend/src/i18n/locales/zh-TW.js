export default {
  app: {
    title: '熱點事件檢測系統',
    subtitle: '多平台數據採集',
    copyright: '© 2024 熱點事件檢測系統'
  },
  nav: {
    dashboard: '數據概覽',
    events: '熱點事件',
    crawlRecords: '抓取記錄',
    users: '使用者管理',
    sysConfig: '系統管理'
  },
  common: {
    search: '搜尋',
    view: '查看',
    delete: '刪除',
    edit: '編輯',
    save: '儲存',
    cancel: '取消',
    confirm: '確定',
    close: '關閉',
    loading: '載入中...',
    success: '操作成功',
    error: '操作失敗',
    warning: '警告',
    all: '全部',
    noData: '暫無資料',
    operation: '操作',
    rank: '排名',
    title: '標題',
    source: '來源',
    category: '分類',
    hotValue: '熱度',
    crawlTime: '抓取時間',
    uncategorized: '未分類',
    rising: '飆升',
    language: '語言'
  },
  events: {
    page: '熱點事件',
    searchPlaceholder: '搜尋熱點事件...',
    deleteConfirm: '確定要刪除這條熱點事件嗎？',
    deleteSuccess: '刪除成功',
    deleteFailed: '刪除失敗',
    fetchFailed: '獲取熱點事件列表失敗',
    tenThousand: '萬',
    back: '返回',
    firstSeen: '首次出現',
    description: '事件描述',
    noDescription: '暫無描述',
    viewSource: '查看原文',
    notFound: '事件不存在',
    fetchDetailFailed: '獲取事件詳情失敗'
  },
  dashboard: {
    page: '數據概覽',
    totalEvents: '事件總數',
    todayEvents: '今日新增',
    activeSources: '活躍來源',
    hotTrends: '熱度趨勢',
    sourceDistribution: '來源分佈',
    categoryDistribution: '分類分佈',
    topEvents: '熱門事件',
    recentEvents: '最新事件',
    last24Hours: '近24小時',
    last7Days: '近7天',
    last30Days: '近30天'
  },
  crawlRecords: {
    page: '抓取記錄',
    platform: '平台',
    status: '狀態',
    startTime: '開始時間',
    endTime: '結束時間',
    itemCount: '採集數量',
    running: '執行中',
    completed: '已完成',
    failed: '失敗',
    pending: '待執行'
  },
  login: {
    title: '登入',
    username: '使用者名稱',
    password: '密碼',
    loginButton: '登入',
    usernamePlaceholder: '請輸入使用者名稱',
    passwordPlaceholder: '請輸入密碼',
    loginSuccess: '登入成功',
    loginFailed: '登入失敗',
    usernameRequired: '請輸入使用者名稱',
    passwordRequired: '請輸入密碼',
    welcomeBack: '歡迎回來，請登入您的帳號',
    sessionTimeout: '會話已超時',
    sessionTimeoutMessage: '由於長時間未操作，您的會話已安全終止。為了您的帳戶安全，請重新登入。',
    adminTip: '管理員帳號：admin / admin123',
    userTip: '普通使用者帳號：user1~user3 / user123',
    success: '登入成功',
    failed: '登入失敗'
  },
  user: {
    profile: '個人中心',
    logout: '退出登入',
    logoutConfirm: '確定要退出登入嗎？',
    logoutTitle: '退出確認',
    logoutSuccess: '已退出登入',
    admin: '管理員',
    noEmail: '未設定郵箱'
  },
  crawl: {
    startAll: '立即抓取',
    noEnabledPlatforms: '沒有啟用狀態的平台，請先在系統管理-平台設定中啟用需要採集的平台',
    crawlSuccess: '採集完成！共採集 {count} 個啟用平台: {platforms}',
    crawlFailed: '抓取失敗: {error}'
  },
  language: {
    zhCN: '簡體中文',
    zhTW: '繁體中文',
    en: 'English',
    switchLanguage: '切換語言'
  },
  session: {
    timeoutWarning: '會話即將過期',
    timeoutMessage: '您的會話將在 {minutes} 分鐘後過期，是否繼續？',
    continueSession: '繼續',
    logoutNow: '立即退出'
  },
  users: {
    page: '使用者管理',
    username: '使用者名稱',
    email: '郵箱',
    role: '角色',
    status: '狀態',
    createTime: '建立時間',
    enabled: '啟用',
    disabled: '停用',
    addUser: '新增使用者',
    editUser: '編輯使用者'
  },
  sysConfig: {
    page: '系統管理',
    platformConfig: '平台設定',
    sessionConfig: '會話設定',
    messageConfig: '訊息設定',
    i18nConfig: '多語言設定'
  }
}

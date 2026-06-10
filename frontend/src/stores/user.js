import { defineStore } from 'pinia'
import { login, logout, getCurrentUser } from '@/api/auth'

const TOKEN_KEY = 'hot_event_token'
const USER_KEY = 'hot_event_user'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN',
    username: (state) => state.user?.nickname || state.user?.username || ''
  },

  actions: {
    async login(loginForm) {
      const res = await login(loginForm)
      this.token = res.token
      this.user = res.user
      localStorage.setItem(TOKEN_KEY, res.token)
      localStorage.setItem(USER_KEY, JSON.stringify(res.user))
      return res
    },

    async logout() {
      try {
        await logout()
      } catch (e) {
      } finally {
        this.token = ''
        this.user = null
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(USER_KEY)
      }
    },

    async fetchCurrentUser() {
      try {
        const user = await getCurrentUser()
        this.user = user
        localStorage.setItem(USER_KEY, JSON.stringify(user))
        return user
      } catch (e) {
        this.token = ''
        this.user = null
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(USER_KEY)
        throw e
      }
    },

    updateUser(user) {
      this.user = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    }
  }
})

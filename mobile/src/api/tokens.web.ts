// Web preview only (`npm run web`): secure storage doesn't exist in a browser, so this uses localStorage.
// Same API as tokens.ts, which the phone apps use.
import type { TokenPair } from './tokens'

export type { TokenPair }

const REFRESH_KEY = 'aiCoach.refreshToken'
let accessToken: string | null = null

export const tokens = {
  access: () => accessToken,

  async refresh(): Promise<string | null> {
    try {
      return localStorage.getItem(REFRESH_KEY)
    } catch {
      return null
    }
  },

  async save(pair: TokenPair) {
    accessToken = pair.accessToken
    try {
      localStorage.setItem(REFRESH_KEY, pair.refreshToken)
    } catch {
      // Storage blocked: the session just ends on reload
    }
  },

  async clear() {
    accessToken = null
    try {
      localStorage.removeItem(REFRESH_KEY)
    } catch {
      // Nothing stored
    }
  },
}

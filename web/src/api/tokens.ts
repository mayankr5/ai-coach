const REFRESH_KEY = 'aiCoach.refreshToken'

// The short-lived access token stays in memory only; the refresh token survives a reload.
// TODO(security): move the refresh token to an httpOnly cookie so scripts can't read it.
let accessToken: string | null = null

export interface TokenPair {
  accessToken: string
  refreshToken: string
}

export const tokens = {
  access: () => accessToken,

  refresh(): string | null {
    try {
      return localStorage.getItem(REFRESH_KEY)
    } catch {
      return null
    }
  },

  save(pair: TokenPair) {
    accessToken = pair.accessToken
    try {
      localStorage.setItem(REFRESH_KEY, pair.refreshToken)
    } catch {
      // Storage blocked (private mode): the session just ends on reload
    }
  },

  clear() {
    accessToken = null
    try {
      localStorage.removeItem(REFRESH_KEY)
    } catch {
      // Nothing stored
    }
  },
}

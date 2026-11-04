import * as SecureStore from 'expo-secure-store'

const REFRESH_KEY = 'aiCoach.refreshToken'

// The short-lived access token stays in memory only; the refresh token is kept in the
// iOS Keychain / Android Keystore, so the session survives the app being closed.
let accessToken: string | null = null

export interface TokenPair {
  accessToken: string
  refreshToken: string
}

export const tokens = {
  access: () => accessToken,

  async refresh(): Promise<string | null> {
    try {
      return await SecureStore.getItemAsync(REFRESH_KEY)
    } catch {
      return null
    }
  },

  async save(pair: TokenPair) {
    accessToken = pair.accessToken
    await SecureStore.setItemAsync(REFRESH_KEY, pair.refreshToken)
  },

  async clear() {
    accessToken = null
    await SecureStore.deleteItemAsync(REFRESH_KEY).catch(() => undefined)
  },
}

import type { UseQueryResult } from '@tanstack/react-query'
import { StyleSheet, Text } from 'react-native'
import { isNetworkError } from '../api/client'
import { colors, radius, space } from '../theme'

const TIME = new Intl.DateTimeFormat('en-GB', { weekday: 'short', hour: 'numeric', minute: '2-digit' })

/**
 * Shown when fresh data couldn't be loaded but saved data is on screen (usually: no connection), with when that
 * data was loaded, so the user knows how current it is.
 */
export function SavedDataNote({ queries }: { queries: UseQueryResult[] }) {
  const stale = queries.filter(q => q.isError && q.data !== undefined)
  if (stale.length === 0) return null
  const offline = stale.some(q => isNetworkError(q.error))
  const savedAt = Math.min(...stale.map(q => q.dataUpdatedAt))
  return (
    <Text accessibilityRole="alert" style={styles.note}>
      {offline ? 'Offline' : 'Couldn’t refresh'} · showing data from {TIME.format(savedAt)}
    </Text>
  )
}

const styles = StyleSheet.create({
  note: {
    paddingVertical: space.sm, paddingHorizontal: space.md, borderRadius: radius.md, backgroundColor: '#fffbeb',
    color: colors.warning, fontSize: 13,
  },
})

import * as Notifications from 'expo-notifications'
import { router } from 'expo-router'
import { useEffect } from 'react'
import { AppState } from 'react-native'
import { refreshReminders } from '../reminders'

/**
 * While logged in: keeps the check-in reminders planned (on start and whenever the app comes back), and opens
 * the screen a tapped reminder points to.
 */
export function Reminders() {
  const response = Notifications.useLastNotificationResponse()

  useEffect(() => {
    void refreshReminders()
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') void refreshReminders()
    })
    return () => subscription.remove()
  }, [])

  useEffect(() => {
    const url = response?.notification.request.content.data?.url
    if (response?.actionIdentifier !== Notifications.DEFAULT_ACTION_IDENTIFIER || typeof url !== 'string') return
    router.push(url as never)
    void Notifications.clearLastNotificationResponseAsync()
  }, [response])

  return null
}

import AsyncStorage from '@react-native-async-storage/async-storage'
import * as Notifications from 'expo-notifications'
import { AndroidImportance } from 'expo-notifications'
import { Platform } from 'react-native'
import { api, call } from './api/client'
import { addDays, today, toIsoDate } from './dates'

/**
 * The daily check-in reminder. It is a setting of this phone (not the account) and works without a server:
 * reminders for the next {@link DAYS} days are scheduled on the phone and re-planned whenever the app opens or
 * the user checks in, so a day can be skipped once every goal is checked in. If the app isn't opened for a week,
 * the reminders stop on purpose.
 */
export interface ReminderSettings {
  enabled: boolean
  hour: number
  minute: number
}

export const DEFAULT_REMINDER: ReminderSettings = { enabled: false, hour: 20, minute: 0 }

const KEY = 'aiCoach.reminder'
const DAYS = 7
const CHANNEL = 'reminders'

export async function loadReminder(): Promise<ReminderSettings> {
  try {
    const saved = await AsyncStorage.getItem(KEY)
    return saved ? { ...DEFAULT_REMINDER, ...JSON.parse(saved) } : DEFAULT_REMINDER
  } catch {
    return DEFAULT_REMINDER
  }
}

export async function saveReminder(settings: ReminderSettings) {
  await AsyncStorage.setItem(KEY, JSON.stringify(settings))
}

/** Asks for permission only when the user turns the reminder on; false if they said no (now or before). */
export async function allowNotifications(): Promise<boolean> {
  if (Platform.OS === 'android') {
    // Android 13+ only shows the permission prompt once a channel exists
    await Notifications.setNotificationChannelAsync(CHANNEL, {
      name: 'Check-in reminders', importance: AndroidImportance.DEFAULT,
    })
  }
  const current = await Notifications.getPermissionsAsync()
  if (current.granted) return true
  if (!current.canAskAgain) return false
  return (await Notifications.requestPermissionsAsync()).granted
}

/** What the reminders need to know about the user's goals right now. */
export interface GoalsToday {
  /** Every active goal has a check-in for today, so today's reminder isn't needed. */
  allCheckedIn: boolean
  /** Where tapping a reminder leads: the goal when there is only one, otherwise the goals list. */
  url: string
}

export async function goalsToday(): Promise<GoalsToday> {
  const goals = (await call(api.GET('/api/v1/goals', { params: { query: { status: 'ACTIVE', size: 50 } } }))).content
  const day = toIsoDate(today())
  const lastCheckIns = await Promise.all(goals.map(goal => call(api.GET('/api/v1/goals/{goalId}/check-ins',
    { params: { path: { goalId: goal.id }, query: { size: 1 } } }))))
  return {
    allCheckedIn: goals.length > 0 && lastCheckIns.every(page => page.content[0]?.date === day),
    url: goals.length === 1 ? `/goals/${goals[0].id}` : '/',
  }
}

/** Replaces the scheduled reminders with ones for the next {@link DAYS} days; none when turned off. */
export async function scheduleReminders(settings: ReminderSettings, goals: GoalsToday, now = new Date()) {
  await Notifications.cancelAllScheduledNotificationsAsync()
  if (!settings.enabled || !(await Notifications.getPermissionsAsync()).granted) return

  for (let i = 0; i < DAYS; i++) {
    const day = addDays(today(), i)
    const date = new Date(day.getFullYear(), day.getMonth(), day.getDate(), settings.hour, settings.minute)
    if (date <= now || (i === 0 && goals.allCheckedIn)) continue
    await Notifications.scheduleNotificationAsync({
      content: {
        title: 'Time to check in',
        body: 'How did today go? The coach adjusts tomorrow’s plan.',
        data: { url: goals.url },
      },
      trigger: { type: Notifications.SchedulableTriggerInputTypes.DATE, date, channelId: CHANNEL },
    })
  }
}

/**
 * Re-plans the reminders from the saved setting and the goals' check-ins. Without a connection the reminders
 * already scheduled stay as they are.
 */
export async function refreshReminders() {
  const settings = await loadReminder()
  if (!settings.enabled) return
  try {
    await scheduleReminders(settings, await goalsToday())
  } catch {
    // Offline: keep what is scheduled
  }
}

/** On logout: no reminders for an account that isn't logged in on this phone any more. */
export async function clearReminders() {
  await AsyncStorage.removeItem(KEY).catch(() => undefined)
  await Notifications.cancelAllScheduledNotificationsAsync().catch(() => undefined)
}

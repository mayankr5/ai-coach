import AsyncStorage from '@react-native-async-storage/async-storage'
import { fireEvent, screen, userEvent, waitFor } from '@testing-library/react-native'
import * as Notifications from 'expo-notifications'
import { router } from 'expo-router'
import { Alert, type AlertButton } from 'react-native'
import SettingsScreen from '../app/settings'
import { Reminders } from '../components/Reminders'
import { today, toIsoDate } from '../dates'
import { goalsToday, loadReminder, scheduleReminders } from '../reminders'
import { json, loggedIn, pathOf, renderScreen } from '../test/render'

type Scheduled = { content: { data: { url: string } }; trigger: { date: Date } }
const scheduled = () => jest.requireMock<{ mockScheduled: Scheduled[] }>('expo-notifications').mockScheduled
const day = toIsoDate(today())
const morning = () => new Date(today().getFullYear(), today().getMonth(), today().getDate(), 7, 0)
const on = { enabled: true, hour: 20, minute: 30 }

/** Flips the reminder switch on, once the saved setting has loaded. */
async function turnOn() {
  const toggle = await screen.findByRole('switch', { name: 'Remind me to check in' })
  await waitFor(() => expect(toggle).toBeEnabled())
  await fireEvent(toggle, 'valueChange', true)
}

/** Active goals and their last check-in dates. */
function goals(lastCheckIns: Record<number, string | null>) {
  return loggedIn(request => {
    const path = pathOf(request)
    if (path === '/api/v1/goals') {
      return json({ content: Object.keys(lastCheckIns).map(id => ({ id: Number(id), title: `Goal ${id}`,
        deadline: '2099-01-01', status: 'ACTIVE', createdAt: '2026-11-01T00:00:00Z' })), page: 0, size: 50,
        totalElements: 1, totalPages: 1 })
    }
    const id = Number(path.split('/')[4])
    const date = lastCheckIns[id]
    return json({ content: date ? [{ id: 1, date, note: null, coachMessage: null, replanSource: 'AI', upcomingTasks: [] }]
      : [], page: 0, size: 1, totalElements: date ? 1 : 0, totalPages: 1 })
  })
}

describe('scheduleReminders', () => {
  it('plans the next 7 days at the chosen time', async () => {
    await scheduleReminders(on, { allCheckedIn: false, url: '/goals/15' }, morning())

    expect(scheduled()).toHaveLength(7)
    expect(scheduled()[0].trigger.date.getHours()).toBe(20)
    expect(scheduled()[0].trigger.date.getMinutes()).toBe(30)
    expect(toIsoDate(scheduled()[0].trigger.date)).toBe(day)
    expect(scheduled()[0].content.data.url).toBe('/goals/15')
  })

  it('skips today once every goal is checked in', async () => {
    await scheduleReminders(on, { allCheckedIn: true, url: '/' }, morning())

    expect(scheduled()).toHaveLength(6)
    expect(toIsoDate(scheduled()[0].trigger.date)).not.toBe(day)
  })

  it('removes them all when turned off or not allowed', async () => {
    await scheduleReminders(on, { allCheckedIn: false, url: '/' }, morning())
    await scheduleReminders({ ...on, enabled: false }, { allCheckedIn: false, url: '/' }, morning())
    expect(scheduled()).toHaveLength(0)

    jest.mocked(Notifications.getPermissionsAsync).mockResolvedValueOnce({ granted: false } as never)
    await scheduleReminders(on, { allCheckedIn: false, url: '/' }, morning())
    expect(scheduled()).toHaveLength(0)
  })
})

describe('goalsToday', () => {
  it('needs every active goal checked in today, and links the only goal', async () => {
    await goals({ 15: day, 16: '2026-01-01' })
    expect(await goalsToday()).toEqual({ allCheckedIn: false, url: '/' })
  })

  it('opens the goal when there is just one', async () => {
    await goals({ 15: day })
    expect(await goalsToday()).toEqual({ allCheckedIn: true, url: '/goals/15' })
  })
})

describe('SettingsScreen', () => {
  beforeEach(() => jest.spyOn(Alert, 'alert').mockImplementation(() => undefined))

  it('asks for permission and plans reminders when turned on', async () => {
    await goals({ 15: null })
    await renderScreen(<SettingsScreen />)

    await turnOn()

    await waitFor(() => expect(scheduled().length).toBeGreaterThanOrEqual(6))
    expect(Notifications.requestPermissionsAsync).not.toHaveBeenCalled()
    expect(await loadReminder()).toEqual({ enabled: true, hour: 20, minute: 0 })
    expect(screen.getByText(/Every day at/)).toBeOnTheScreen()
  })

  it('explains when notifications are turned off for the app', async () => {
    jest.mocked(Notifications.getPermissionsAsync).mockResolvedValueOnce({ granted: false, canAskAgain: true } as never)
    jest.mocked(Notifications.requestPermissionsAsync).mockResolvedValueOnce({ granted: false } as never)
    await goals({ 15: null })
    await renderScreen(<SettingsScreen />)

    await turnOn()

    expect(await screen.findByText(/Notifications are turned off for AI Coach/)).toBeOnTheScreen()
    expect(scheduled()).toHaveLength(0)
    expect((await loadReminder()).enabled).toBe(false)
  })

  it('logs out once confirmed, stopping reminders', async () => {
    await AsyncStorage.setItem('aiCoach.reminder', JSON.stringify(on))
    const api = await goals({ 15: null })
    const user = userEvent.setup()
    await renderScreen(<SettingsScreen />)

    await user.press(await screen.findByRole('button', { name: 'Log out' }))
    const buttons = jest.mocked(Alert.alert).mock.lastCall![2] as AlertButton[]
    buttons.find(b => b.text === 'Log out')!.onPress!()

    await waitFor(() => expect(api.requests().some(r => pathOf(r) === '/api/v1/auth/logout')).toBe(true))
    await waitFor(async () => expect(await AsyncStorage.getItem('aiCoach.reminder')).toBeNull())
    expect(Notifications.cancelAllScheduledNotificationsAsync).toHaveBeenCalled()
  })
})

describe('Reminders', () => {
  it('opens the screen a tapped reminder points to', async () => {
    await goals({ 15: null })
    jest.mocked(Notifications.useLastNotificationResponse).mockReturnValue({
      actionIdentifier: Notifications.DEFAULT_ACTION_IDENTIFIER,
      notification: { request: { content: { data: { url: '/goals/15' } } } },
    } as never)

    await renderScreen(<Reminders />)

    expect(router.push).toHaveBeenCalledWith('/goals/15')
  })
})

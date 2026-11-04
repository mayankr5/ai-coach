import { screen, userEvent, waitFor } from '@testing-library/react-native'
import { router, useLocalSearchParams } from 'expo-router'
import { Alert, type AlertButton } from 'react-native'
import CutScreen from '../app/goals/[id]/cut'
import { BackOnTrack } from '../components/goal/BackOnTrack'
import { json, loggedIn, pathOf, renderScreen } from '../test/render'

const progress = (minutesBehind: number) => ({
  goalId: 15, totalTasks: 20, doneTasks: 1, partialTasks: 0, skippedTasks: 0, percentComplete: 5, completionRate: 0.7,
  healthPercent: minutesBehind ? 71 : 100, minutesBehind, nextMilestone: { id: 3, title: 'Basics', targetDate: '2026-11-14' },
})
const options = {
  minutesBehind: 243, healthPercent: 71, milestone: { id: 3, title: 'Basics', targetDate: '2026-11-14' },
  extend: { days: 5, milestoneDate: '2026-11-19', deadline: '2027-01-09' },
  moreTime: { minutesPerDay: 84, currentMinutesPerDay: 60, realistic: true },
}
const suggestion = {
  minutesBehind: 183, minutesFreed: 198, tasks: [
    { taskId: 7, title: 'Build a CLI project', minutes: 150, scheduledDate: '2026-11-09', reason: 'Extra practice' },
    { taskId: 8, title: 'Stream API basics', minutes: 48, scheduledDate: '2026-11-10', reason: 'Learn it on the fly' },
  ],
}

/** The goal's adjustment endpoints, answered from {@code overrides} first. */
function backend(overrides: Record<string, () => Response> = {}) {
  return loggedIn(request => {
    const key = `${request.method} ${pathOf(request).replace('/api/v1/goals/15', '')}`
    if (overrides[key]) return overrides[key]()
    if (key === 'GET /progress') return json(progress(243))
    if (key === 'GET /adjustments') return json(options)
    if (key === 'POST /adjustments/cut-scope/suggest') return json(suggestion)
    if (request.method === 'POST') return json({ ...options, minutesBehind: 0, extend: null, moreTime: null })
    return json({}, 404)
  })
}
const posts = (api: Awaited<ReturnType<typeof backend>>) =>
  api.requests().filter(r => r.method === 'POST' && pathOf(r).includes('/adjustments'))

/** Presses the dialog button named {@code text} of the last alert shown. */
function answerAlert(text: string) {
  const buttons = jest.mocked(Alert.alert).mock.lastCall![2] as AlertButton[]
  buttons.find(b => b.text === text)!.onPress?.()
}

describe('BackOnTrack', () => {
  beforeEach(() => jest.spyOn(Alert, 'alert').mockImplementation(() => undefined))

  it('shows nothing while on track', async () => {
    const api = await backend({ 'GET /progress': () => json(progress(0)) })
    await renderScreen(<BackOnTrack goalId={15} />)

    await waitFor(() => expect(api.requests().some(r => pathOf(r).endsWith('/progress'))).toBe(true))
    expect(screen.queryByText('Get back on track')).not.toBeOnTheScreen()
    expect(api.requests().some(r => pathOf(r).endsWith('/adjustments'))).toBe(false)
  })

  it('moves the dates only once confirmed', async () => {
    const api = await backend()
    const user = userEvent.setup()
    await renderScreen(<BackOnTrack goalId={15} />)

    expect(await screen.findByText('Move it by 5 days')).toBeOnTheScreen()
    await user.press(screen.getByRole('button', { name: 'Move dates' }))
    expect(Alert.alert).toHaveBeenCalledWith('Move the dates?', expect.stringContaining('Thu 19 Nov'), expect.any(Array))
    answerAlert('Cancel')
    expect(posts(api)).toHaveLength(0)

    await user.press(screen.getByRole('button', { name: 'Move dates' }))
    answerAlert('Move')

    await waitFor(() => expect(posts(api)).toHaveLength(1))
    expect(pathOf(posts(api)[0])).toBe('/api/v1/goals/15/adjustments/extend')
    expect(await posts(api)[0].json()).toEqual({ days: 5 })
  })

  it('adds daily time once confirmed, warning when it is a big jump', async () => {
    const api = await backend({ 'GET /adjustments': () => json({ ...options, moreTime: { ...options.moreTime, realistic: false } }) })
    const user = userEvent.setup()
    await renderScreen(<BackOnTrack goalId={15} />)

    expect(await screen.findByText(/may be hard to keep up/)).toBeOnTheScreen()
    await user.press(screen.getByRole('button', { name: 'Add time' }))
    answerAlert('Add time')

    await waitFor(() => expect(posts(api)).toHaveLength(1))
    expect(await posts(api)[0].json()).toEqual({ minutesPerDay: 84 })
  })

  it('opens the drop-tasks sheet', async () => {
    await backend()
    const user = userEvent.setup()
    await renderScreen(<BackOnTrack goalId={15} />)

    await user.press(await screen.findByRole('button', { name: 'Suggest' }))

    expect(router.push).toHaveBeenCalledWith('/goals/15/cut')
  })
})

describe('CutScreen', () => {
  beforeEach(() => {
    jest.spyOn(Alert, 'alert').mockImplementation(() => undefined)
    jest.mocked(useLocalSearchParams).mockReturnValue({ id: '15' })
  })

  it('drops only the tasks left ticked, then closes', async () => {
    const api = await backend()
    const user = userEvent.setup()
    await renderScreen(<CutScreen />)

    expect(await screen.findByText('Learn it on the fly')).toBeOnTheScreen()
    expect(screen.getByText('Frees 198 of the 183 minutes needed.')).toBeOnTheScreen()
    await user.press(screen.getByRole('checkbox', { name: 'Stream API basics' }))
    expect(screen.getByText('Frees 150 of the 183 minutes needed.')).toBeOnTheScreen()

    await user.press(screen.getByRole('button', { name: 'Drop 1 task' }))
    answerAlert('Drop')

    await waitFor(() => expect(router.back).toHaveBeenCalled())
    const cut = posts(api).find(r => pathOf(r) === '/api/v1/goals/15/adjustments/cut-scope')!
    expect(await cut.json()).toEqual({ taskIds: [7] })
    expect(posts(api).filter(r => pathOf(r).endsWith('/suggest'))).toHaveLength(1)
  })

  it('offers a retry when the coach is unavailable', async () => {
    await backend({ 'POST /adjustments/cut-scope/suggest': () => json({ title: 'Bad Gateway' }, 502) })
    await renderScreen(<CutScreen />)

    expect(await screen.findByText(/couldn’t suggest anything right now/)).toBeOnTheScreen()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeOnTheScreen()
  })
})

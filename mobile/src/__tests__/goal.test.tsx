import { screen, userEvent, waitFor } from '@testing-library/react-native'
import { useLocalSearchParams } from 'expo-router'
import { goalKeys } from '../api/goals'
import GoalScreen from '../app/goals/[id]/index'
import { addDays, today, toIsoDate } from '../dates'
import { json, loggedIn, pathOf, renderScreen, testQueryClient } from '../test/render'

const day = toIsoDate(today())
const goal = {
  id: 15, title: 'Get a Java backend job', description: null, deadline: '2099-01-04', minutesPerDay: 60,
  extraMinutesPerDay: 15, bufferDay: null, experienceLevel: 'BEGINNER', status: 'ACTIVE', milestones: [],
}
const task = (id: number, title: string, minutes: number, date = day) =>
  ({ id, goalId: 15, milestoneId: 3, title, scheduledDate: date, estimatedMinutes: minutes, status: 'PENDING', sequence: id })
const plan = { milestones: [{ id: 3, title: 'Basics', targetDate: '2099-01-01', position: 1, tasks: [task(1, 'Install JDK', 30)] }] }
const progress = (minutesBehind: number) => ({
  goalId: 15, totalTasks: 20, doneTasks: 1, partialTasks: 1, skippedTasks: 0, percentComplete: 5, completionRate: 0.72,
  healthPercent: minutesBehind ? 71 : 100, minutesBehind, nextMilestone: { id: 3, title: 'Basics', targetDate: '2099-01-01' },
})
const checkIns = (content: unknown[]) => json({ content, page: 0, size: 1, totalElements: content.length, totalPages: 1 })

type Routes = Record<string, (request: Request) => Response>

/** The goal's endpoints, answered from {@code overrides} first. */
function backend(overrides: Routes = {}) {
  return loggedIn(request => {
    const key = `${request.method} ${pathOf(request).replace('/api/v1/goals/15', '') || '/'}`
    if (overrides[key]) return overrides[key](request)
    switch (key) {
      case 'GET /': return json(goal)
      case 'GET /plan': return json(plan)
      case 'GET /progress': return json(progress(0))
      case 'GET /check-ins': return checkIns([])
      case 'GET /tasks': {
        const from = new URL(request.url).searchParams.get('from')
        return json(from === day ? [task(1, 'Install JDK', 30), task(2, 'Run first app', 24)]
          : [task(5, 'Learn classes', 54, toIsoDate(addDays(today(), 2)))])
      }
      default: return json({}, 404)
    }
  })
}

const posts = (api: Awaited<ReturnType<typeof backend>>, path: string) =>
  api.requests().filter(r => r.method === 'POST' && pathOf(r) === `/api/v1/goals/15${path}`)

describe('GoalScreen', () => {
  beforeEach(() => jest.mocked(useLocalSearchParams).mockReturnValue({ id: '15' }))

  it('offers to generate a plan, and explains when the AI is busy', async () => {
    const api = await backend({
      'GET /plan': () => json({ milestones: [] }),
      'POST /plan': () => json({ title: 'Bad Gateway' }, 502),
    })
    const user = userEvent.setup()
    await renderScreen(<GoalScreen />)

    await user.press(await screen.findByRole('button', { name: 'Generate my plan' }))

    expect(await screen.findByText(/couldn’t make a plan right now/)).toBeOnTheScreen()
    expect(posts(api, '/plan')).toHaveLength(1)
  })

  it('checks in once every task is marked', async () => {
    const api = await backend({
      'POST /check-ins': () => json({ id: 9, date: day, note: null, coachMessage: 'Nice start.', replanSource: 'AI',
        upcomingTasks: [] }),
    })
    const user = userEvent.setup()
    await renderScreen(<GoalScreen />)

    expect(await screen.findByText('54 minutes planned. How did it go?')).toBeOnTheScreen()
    await user.press(screen.getByRole('radio', { name: 'Done: Install JDK' }))
    expect(screen.getByText('Mark every task to check in (1 of 2).')).toBeOnTheScreen()
    expect(screen.getByRole('button', { name: 'Check in' })).toBeDisabled()

    await user.press(screen.getByRole('radio', { name: 'Partly: Run first app' }))
    await user.type(screen.getByLabelText('Note for the coach'), 'Maven broke')
    await user.press(screen.getByRole('button', { name: 'Check in' }))

    await waitFor(() => expect(posts(api, '/check-ins')).toHaveLength(1))
    expect(await posts(api, '/check-ins')[0].json()).toEqual({
      date: day, note: 'Maven broke', tasks: [{ taskId: 1, status: 'DONE' }, { taskId: 2, status: 'PARTIAL' }],
    })
  })

  it('shows the coach’s reply, the week and how far behind the plan is', async () => {
    await backend({
      'GET /check-ins': () => checkIns([{ id: 9, date: day, note: null, coachMessage: 'Finish the setup first.',
        replanSource: 'AI', upcomingTasks: [] }]),
      'GET /progress': () => json(progress(183)),
    })
    await renderScreen(<GoalScreen />)

    expect(await screen.findByText('Finish the setup first.')).toBeOnTheScreen()
    expect(await screen.findByRole('button', { name: 'Update check-in' })).toBeOnTheScreen()
    expect(await screen.findByText('Learn classes')).toBeOnTheScreen()
    expect(screen.getAllByText('Nothing planned')).toHaveLength(6)
    expect(await screen.findByText('183 min behind')).toBeOnTheScreen()
    expect(screen.getByLabelText('Current milestone: Basics')).toBeOnTheScreen()
  })

  it('shows saved data with a note when offline', async () => {
    const offline = () => { throw new TypeError('Network request failed') }
    await backend({ 'GET /': offline, 'GET /plan': offline, 'GET /progress': offline, 'GET /check-ins': offline })
    const saved = testQueryClient()
    saved.setQueryData(goalKeys.detail(15), goal)
    saved.setQueryData([...goalKeys.detail(15), 'plan'], plan)

    await renderScreen(<GoalScreen />, saved)

    expect(await screen.findByText(/^Offline · showing data from/)).toBeOnTheScreen()
    expect(screen.getAllByText('Get a Java backend job').length).toBeGreaterThan(0)
  })
})

import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, mockApi, renderPage } from '../../test/render'
import { BackOnTrack } from './BackOnTrack'

const progress = (minutesBehind: number) => ({
  goalId: 1, totalTasks: 10, doneTasks: 2, partialTasks: 0, skippedTasks: 1, completionRate: 0.6,
  healthPercent: minutesBehind ? 78 : 100, minutesBehind,
  nextMilestone: { id: 3, title: 'Basics', targetDate: '2026-11-14' },
})
const options = {
  minutesBehind: 183, healthPercent: 78, milestone: { id: 3, title: 'Basics', targetDate: '2026-11-14' },
  extend: { days: 4, milestoneDate: '2026-11-18', deadline: '2027-01-08' },
  moreTime: { minutesPerDay: 84, currentMinutesPerDay: 60, realistic: true },
}
const suggestion = {
  minutesBehind: 183, minutesFreed: 198, tasks: [
    { taskId: 7, title: 'Build a CLI project', minutes: 150, scheduledDate: '2026-11-09', reason: 'Extra practice' },
    { taskId: 8, title: 'Stream API basics', minutes: 48, scheduledDate: '2026-11-10', reason: 'Learn it on the fly' },
  ],
}

/** Routes by path; POST bodies are recorded by mockApi. */
function backend(overrides: Record<string, () => Response> = {}) {
  return mockApi(request => {
    const path = new URL(request.url).pathname
    const key = `${request.method} ${path.replace('/api/v1/goals/1', '')}`
    if (overrides[key]) return overrides[key]()
    if (key === 'GET /progress') return json(progress(183))
    if (key === 'GET /adjustments') return json(options)
    if (key === 'POST /adjustments/cut-scope/suggest') return json(suggestion)
    if (request.method === 'POST') return json({ ...options, minutesBehind: 0, extend: null, moreTime: null })
    return json({}, 404)
  })
}

const posts = (api: ReturnType<typeof mockApi>) => api.requests().filter(r => r.method === 'POST')

describe('BackOnTrack', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows nothing while on track', async () => {
    const api = mockApi(() => json(progress(0)))
    const { container } = renderPage(<BackOnTrack goalId={1} />)

    await vi.waitFor(() => expect(api.requests()).toHaveLength(1))
    expect(container).toBeEmptyDOMElement()
  })

  it('moves the dates only once confirmed', async () => {
    const api = backend()
    renderPage(<BackOnTrack goalId={1} />)
    const user = userEvent.setup()

    expect(await screen.findByText('Move it by 4 days')).toBeInTheDocument()
    expect(screen.getByText(/Due Wed 18 Nov/)).toBeInTheDocument()
    expect(screen.getByText('84 min a day')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Move dates' }))
    expect(posts(api)).toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Confirm' }))
    await vi.waitFor(() => expect(posts(api)).toHaveLength(1))
    expect(new URL(posts(api)[0].url).pathname).toBe('/api/v1/goals/1/adjustments/extend')
    expect(await posts(api)[0].json()).toEqual({ days: 4 })
  })

  it('warns when the extra time is a big jump', async () => {
    backend({ 'GET /adjustments': () => json({ ...options, moreTime: { ...options.moreTime, realistic: false } }) })
    renderPage(<BackOnTrack goalId={1} />)

    expect(await screen.findByText(/may be hard to keep up/)).toBeInTheDocument()
  })

  it('drops only the tasks left ticked', async () => {
    const api = backend()
    renderPage(<BackOnTrack goalId={1} />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Suggest' }))
    expect(await screen.findByText('Learn it on the fly')).toBeInTheDocument()
    expect(screen.getByText('Frees 198 of the 183 minutes needed.')).toBeInTheDocument()

    await user.click(screen.getByRole('checkbox', { name: /Stream API basics/ }))
    expect(screen.getByText('Frees 150 of the 183 minutes needed.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Drop 1 task' }))

    await vi.waitFor(() => expect(posts(api)).toHaveLength(2))
    expect(new URL(posts(api)[1].url).pathname).toBe('/api/v1/goals/1/adjustments/cut-scope')
    expect(await posts(api)[1].json()).toEqual({ taskIds: [7] })
  })

  it('offers a retry when the coach is unavailable', async () => {
    backend({ 'POST /adjustments/cut-scope/suggest': () => json({ detail: 'AI unavailable' }, 502) })
    renderPage(<BackOnTrack goalId={1} />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Suggest' }))
    expect(await screen.findByText(/couldn’t suggest anything right now/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})

import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { today, toIsoDate } from '../../dates'
import { json, mockApi, renderPage } from '../../test/render'
import { TodayCard } from './TodayCard'

const day = toIsoDate(today())
const task = (id: number, title: string, minutes: number) =>
  ({ id, goalId: 1, milestoneId: 3, title, scheduledDate: day, estimatedMinutes: minutes, status: 'PENDING', sequence: id })

describe('TodayCard', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('checks in once every task is marked', async () => {
    const api = mockApi(request => request.method === 'POST'
      ? json({ id: 9, date: day, note: 'Maven broke', coachMessage: 'Finish the setup first tomorrow (Thu 5 Nov).',
        replanSource: 'AI', upcomingTasks: [] })
      : json([task(1, 'Install JDK', 30), task(2, 'Run first app', 24)]))
    renderPage(<TodayCard goalId={1} checkedIn={false} />)
    const user = userEvent.setup()

    await screen.findByText('Install JDK')
    expect(screen.getByText('54 minutes planned. How did it go?')).toBeInTheDocument()
    const checkIn = screen.getByRole('button', { name: 'Check in' })
    await user.click(within(screen.getByRole('group', { name: 'Result for Install JDK' })).getByText('Done'))
    expect(checkIn).toBeDisabled()
    expect(screen.getByText('Mark every task to check in (1 of 2).')).toBeInTheDocument()

    await user.click(within(screen.getByRole('group', { name: 'Result for Run first app' })).getByText('Partly'))
    await user.type(screen.getByLabelText(/Note for the coach/), 'Maven broke')
    await user.click(checkIn)

    await vi.waitFor(() => expect(api.requests().some(r => r.method === 'POST')).toBe(true))
    const post = api.requests().find(r => r.method === 'POST')!
    expect(await post.json()).toEqual({
      date: day, note: 'Maven broke', tasks: [{ taskId: 1, status: 'DONE' }, { taskId: 2, status: 'PARTIAL' }],
    })
  })

  it('lets a note through on a day with nothing planned, as an update once checked in', async () => {
    mockApi(() => json([]))
    renderPage(<TodayCard goalId={1} checkedIn />)
    const user = userEvent.setup()

    expect(await screen.findByText(/Nothing planned today/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Update check-in' })).toBeDisabled()
    await user.type(screen.getByLabelText(/Note for the coach/), 'Caught up on reading')

    expect(screen.getByRole('button', { name: 'Update check-in' })).toBeEnabled()
  })
})

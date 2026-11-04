import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, mockApi, renderPage } from '../test/render'
import { NewGoalPage } from './NewGoalPage'

describe('NewGoalPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates the goal and opens it', async () => {
    const api = mockApi(() => json({ id: 42, title: 'Get a Java job' }, 201))
    renderPage(<NewGoalPage />, { path: '/goals/new' })
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('What do you want to achieve?'), 'Get a Java job')
    await user.type(screen.getByLabelText('Deadline'), '2027-01-04')
    await user.clear(screen.getByLabelText('Minutes per day'))
    await user.type(screen.getByLabelText('Minutes per day'), '90')
    await user.type(screen.getByLabelText(/Extra minutes/), '5')
    await user.selectOptions(screen.getByLabelText(/Weekly buffer day/), 'SATURDAY')
    await user.click(screen.getByRole('button', { name: 'Create goal' }))

    expect(await screen.findByText('At /goals/42')).toBeInTheDocument()
    expect(await api.requests()[0].json()).toEqual({
      title: 'Get a Java job', deadline: '2027-01-04', minutesPerDay: 90, extraMinutesPerDay: 5,
      bufferDay: 'SATURDAY', experienceLevel: 'BEGINNER',
    })
  })

  it('allows extra time up to half the daily time', async () => {
    renderPage(<NewGoalPage />, { path: '/goals/new' })
    const user = userEvent.setup()

    await user.clear(screen.getByLabelText('Minutes per day'))
    await user.type(screen.getByLabelText('Minutes per day'), '45')

    expect(screen.getByLabelText(/Extra minutes/)).toHaveAttribute('max', '22')
    expect(screen.getByText('(up to 22)')).toBeInTheDocument()
  })

  it('shows the server message next to the field', async () => {
    mockApi(() => json({ title: 'Bad Request', errors: { deadline: 'must be a future date' } }, 400))
    renderPage(<NewGoalPage />, { path: '/goals/new' })
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('What do you want to achieve?'), 'Learn Go')
    await user.type(screen.getByLabelText('Deadline'), '2030-01-01')
    await user.click(screen.getByRole('button', { name: 'Create goal' }))

    expect(await screen.findByText('must be a future date')).toBeInTheDocument()
    expect(screen.getByLabelText('Deadline')).toHaveAttribute('aria-invalid', 'true')
  })
})

import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { addDays, today, toIsoDate } from '../dates'
import { json, mockApi, renderPage } from '../test/render'
import { GoalsPage } from './GoalsPage'

const page = (content: unknown[]) => json({ content, page: 0, size: 50, totalElements: content.length, totalPages: 1 })

describe('GoalsPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('lists goals with the days left', async () => {
    mockApi(() => page([{ id: 7, title: 'Learn Spring', deadline: toIsoDate(addDays(today(), 10)), status: 'ACTIVE',
      createdAt: '2026-11-04T05:00:00Z' }]))
    renderPage(<GoalsPage />)

    expect(await screen.findByRole('link', { name: /Learn Spring/ })).toHaveAttribute('href', '/goals/7')
    expect(screen.getByText(/10 days left/)).toBeInTheDocument()
  })

  it('offers to create the first goal, and filters by status', async () => {
    const api = mockApi(() => page([]))
    renderPage(<GoalsPage />)

    expect(await screen.findByText('No active goals yet')).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Archived' }))

    expect(await screen.findByText('Nothing here')).toBeInTheDocument()
    expect(new URL(api.requests().at(-1)!.url).searchParams.get('status')).toBe('ARCHIVED')
  })
})

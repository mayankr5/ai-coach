import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, mockApi, renderPage } from '../../test/render'
import { GeneratePlan } from './GeneratePlan'

describe('GeneratePlan', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('explains when the AI is unavailable', async () => {
    mockApi(() => json({ title: 'Bad Gateway', detail: 'No AI model could generate a plan' }, 502))
    renderPage(<GeneratePlan goalId={1} />)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Generate my plan' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('it may be busy')
    expect(screen.getByRole('button', { name: 'Generate my plan' })).toBeEnabled()
  })
})

import { screen, userEvent } from '@testing-library/react-native'
import { router } from 'expo-router'
import GoalsScreen from '../app/index'
import { json, loggedIn, pathOf, renderScreen } from '../test/render'

const goal = (id: number, title: string) =>
  ({ id, title, deadline: '2099-01-04', status: 'ACTIVE', createdAt: '2026-11-01T10:00:00Z' })
const page = (content: unknown[]) => json({ content, page: 0, size: 50, totalElements: content.length, totalPages: 1 })
const statusOf = (request: Request) => new URL(request.url).searchParams.get('status')

describe('GoalsScreen', () => {
  it('lists active goals and opens one', async () => {
    await loggedIn(request => page(statusOf(request) === 'ACTIVE' ? [goal(7, 'Get a Java backend job')] : []))
    const user = userEvent.setup()
    await renderScreen(<GoalsScreen />)

    await user.press(await screen.findByText('Get a Java backend job'))

    expect(screen.getByText(/Deadline .* 4 Jan 2099/)).toBeOnTheScreen()
    expect(router.push).toHaveBeenCalledWith('/goals/7')
  })

  it('switches to completed goals', async () => {
    const api = await loggedIn(request => page(statusOf(request) === 'ACTIVE' ? [goal(7, 'Learn Java')] : []))
    const user = userEvent.setup()
    await renderScreen(<GoalsScreen />)
    await screen.findByText('Learn Java')

    await user.press(screen.getByRole('tab', { name: 'Completed' }))

    expect(await screen.findByText('Nothing here')).toBeOnTheScreen()
    expect(api.requests().filter(r => pathOf(r) === '/api/v1/goals').map(statusOf)).toContain('COMPLETED')
  })

  it('offers to create the first goal', async () => {
    await loggedIn(() => page([]))
    const user = userEvent.setup()
    await renderScreen(<GoalsScreen />)

    await user.press(await screen.findByRole('button', { name: 'Create your first goal' }))

    expect(router.push).toHaveBeenCalledWith('/goals/new')
  })

  it('opens settings from the account button', async () => {
    await loggedIn(() => page([]))
    const user = userEvent.setup()
    await renderScreen(<GoalsScreen />)

    await user.press(await screen.findByRole('button', { name: 'Settings' }))

    expect(router.push).toHaveBeenCalledWith('/settings')
  })
})

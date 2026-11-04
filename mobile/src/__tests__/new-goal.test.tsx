import { screen, userEvent, waitFor } from '@testing-library/react-native'
import { router } from 'expo-router'
import NewGoalScreen from '../app/goals/new'
import { addMonths, today, toIsoDate } from '../dates'
import { json, loggedIn, pathOf, renderScreen } from '../test/render'

const created = { id: 9, title: 'Learn Java', description: null, deadline: '2027-02-05', minutesPerDay: 75,
  extraMinutesPerDay: 30, bufferDay: 'SATURDAY', experienceLevel: 'BEGINNER', status: 'ACTIVE', milestones: [] }
const radio = (name: string) => screen.getByRole('radio', { name })
const goalPosts = (api: Awaited<ReturnType<typeof loggedIn>>) =>
  api.requests().filter(r => pathOf(r) === '/api/v1/goals' && r.method === 'POST')

describe('NewGoalScreen', () => {
  it('creates the goal and opens it', async () => {
    const api = await loggedIn(() => json(created, 201))
    const user = userEvent.setup()
    await renderScreen(<NewGoalScreen />)

    await user.type(screen.getByLabelText('What do you want to achieve?'), 'Learn Java')
    await user.press(radio('Other minutes a day'))
    await user.type(screen.getByLabelText('Your minutes per day'), '75')
    await user.press(radio('30 extra minutes'))
    await user.press(radio('Saturday'))
    await user.press(screen.getByRole('button', { name: 'Create goal' }))

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith('/goals/9'))
    expect(await goalPosts(api)[0].json()).toEqual({
      title: 'Learn Java', deadline: toIsoDate(addMonths(today(), 3)), minutesPerDay: 75, extraMinutesPerDay: 30,
      bufferDay: 'SATURDAY', experienceLevel: 'BEGINNER',
    })
  })

  it('checks the title and minutes before sending', async () => {
    const api = await loggedIn(() => json(created, 201))
    const user = userEvent.setup()
    await renderScreen(<NewGoalScreen />)

    await user.press(radio('Other minutes a day'))
    await user.type(screen.getByLabelText('Your minutes per day'), '5')
    await user.press(screen.getByRole('button', { name: 'Create goal' }))

    expect(screen.getByText('Say what you want to achieve')).toBeOnTheScreen()
    expect(screen.getByText('Between 10 and 600 minutes')).toBeOnTheScreen()
    expect(goalPosts(api)).toHaveLength(0)
  })

  it('shows the server’s errors under the fields', async () => {
    await loggedIn(() => json({ title: 'Bad Request', errors: { deadline: 'must be a future date' } }, 400))
    const user = userEvent.setup()
    await renderScreen(<NewGoalScreen />)

    await user.type(screen.getByLabelText('What do you want to achieve?'), 'Learn Java')
    await user.press(screen.getByRole('button', { name: 'Create goal' }))

    expect(await screen.findByText('must be a future date')).toBeOnTheScreen()
    expect(router.replace).not.toHaveBeenCalled()
  })

  it('drops extra time that less daily time no longer allows', async () => {
    await loggedIn(() => json(created, 201))
    const user = userEvent.setup()
    await renderScreen(<NewGoalScreen />)

    await user.press(radio('30 extra minutes'))
    expect(screen.getByText(/up to 30/)).toBeOnTheScreen()

    await user.press(radio('30 minutes a day'))

    expect(screen.getByText(/up to 15/)).toBeOnTheScreen()
    expect(radio('0 extra minutes')).toBeChecked()
    expect(screen.queryByRole('radio', { name: '30 extra minutes' })).not.toBeOnTheScreen()
  })
})

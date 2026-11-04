import { screen, userEvent } from '@testing-library/react-native'
import { json, mockApi, pathOf, renderScreen } from '../test/render'
import RegisterScreen from '../app/register'

describe('RegisterScreen', () => {
  it('sends the phone’s time zone and shows field errors', async () => {
    const api = mockApi(request => pathOf(request) === '/api/v1/auth/register'
      ? json({ title: 'Bad Request', errors: { email: 'is already registered' } }, 400)
      : json({}, 401))
    const user = userEvent.setup()
    await renderScreen(<RegisterScreen />)

    expect(await screen.findByText('Asia/Kolkata')).toBeOnTheScreen()
    await user.type(screen.getByLabelText('Name'), 'Asha')
    await user.type(screen.getByLabelText('Email'), 'a@example.com')
    await user.type(screen.getByLabelText('Password'), 'password123')
    await user.press(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('is already registered')).toBeOnTheScreen()
    const register = api.requests().find(r => pathOf(r) === '/api/v1/auth/register')!
    expect(await register.json()).toEqual(
      { displayName: 'Asha', email: 'a@example.com', password: 'password123', timezone: 'Asia/Kolkata' })
  })
})

import { screen, userEvent } from '@testing-library/react-native'
import { Text } from 'react-native'
import LoginScreen from '../app/login'
import { useAuth } from '../auth/useAuth'
import { json, mockApi, pathOf, renderScreen } from '../test/render'

const asha = { id: 1, email: 'a@example.com', displayName: 'Asha', timezone: 'Asia/Kolkata' }
const pair = { accessToken: 'access', refreshToken: 'refresh', expiresInSeconds: 900 }

/** Shows who is logged in, as the layout would by switching screens. */
function LoggedInAs() {
  const { user } = useAuth()
  return user ? <Text>Logged in as {user.displayName}</Text> : null
}

describe('LoginScreen', () => {
  it('logs in and loads the user', async () => {
    const api = mockApi(request => {
      if (pathOf(request) === '/api/v1/auth/login') return json(pair)
      if (pathOf(request) === '/api/v1/users/me') return json(asha)
      return json({}, 401)
    })
    const user = userEvent.setup()
    await renderScreen(<><LoginScreen /><LoggedInAs /></>)

    await user.type(await screen.findByLabelText('Email'), ' a@example.com ')
    await user.type(screen.getByLabelText('Password'), 'password123')
    await user.press(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByText('Logged in as Asha')).toBeOnTheScreen()
    const login = api.requests().find(r => pathOf(r) === '/api/v1/auth/login')!
    expect(await login.json()).toEqual({ email: 'a@example.com', password: 'password123' })
  })

  it('says so when the password is wrong', async () => {
    mockApi(() => json({ title: 'Unauthorized' }, 401))
    const user = userEvent.setup()
    await renderScreen(<LoginScreen />)

    await user.type(await screen.findByLabelText('Email'), 'a@example.com')
    await user.type(screen.getByLabelText('Password'), 'nope')
    await user.press(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByText('Wrong email or password.')).toBeOnTheScreen()
  })

  it('waits for both fields', async () => {
    mockApi(() => json({}, 401))
    const user = userEvent.setup()
    await renderScreen(<LoginScreen />)

    await user.type(await screen.findByLabelText('Email'), 'a@example.com')

    expect(screen.getByRole('button', { name: 'Log in' })).toBeDisabled()
  })
})

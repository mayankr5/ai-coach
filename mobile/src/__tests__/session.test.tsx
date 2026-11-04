import AsyncStorage from '@react-native-async-storage/async-storage'
import { screen } from '@testing-library/react-native'
import { Text } from 'react-native'
import { tokens } from '../api/tokens'
import { useAuth } from '../auth/useAuth'
import { asha, json, mockApi, renderScreen } from '../test/render'

function Session() {
  const { user, restoring } = useAuth()
  if (restoring) return <Text>Restoring</Text>
  return <Text>{user ? `Logged in as ${user.displayName}` : 'Logged out'}</Text>
}

describe('Saved session', () => {
  beforeEach(async () => {
    await tokens.save({ accessToken: 'old', refreshToken: 'saved-refresh' })
    await AsyncStorage.setItem('aiCoach.user', JSON.stringify(asha))
  })

  it('opens offline as the saved user', async () => {
    mockApi(() => { throw new TypeError('Network request failed') })
    await renderScreen(<Session />)

    expect(await screen.findByText('Logged in as Asha')).toBeOnTheScreen()
    expect(await tokens.refresh()).toBe('saved-refresh')
  })

  it('ends, with everything saved, when the server rejects it', async () => {
    mockApi(() => json({ title: 'Unauthorized' }, 401))
    await renderScreen(<Session />)

    expect(await screen.findByText('Logged out')).toBeOnTheScreen()
    expect(await tokens.refresh()).toBeNull()
    expect(await AsyncStorage.getItem('aiCoach.user')).toBeNull()
  })
})

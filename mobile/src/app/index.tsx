import { StyleSheet, Text, View } from 'react-native'
import { useAuth } from '../auth/useAuth'
import { Button, Screen } from '../components/ui'
import { colors, space } from '../theme'

// Placeholder home: the goals list replaces it in the next step
export default function HomeScreen() {
  const { user, logout } = useAuth()
  return (
    <Screen>
      <View style={styles.container}>
        <Text style={styles.title}>Hi {user?.displayName}</Text>
        <Text style={styles.muted}>Your goals will show up here.</Text>
        <Button title="Log out" variant="secondary" onPress={logout} />
      </View>
    </Screen>
  )
}

const styles = StyleSheet.create({
  container: { gap: space.md },
  title: { fontSize: 22, fontWeight: '700', color: colors.text },
  muted: { color: colors.textMuted },
})

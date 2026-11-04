import { Link } from 'expo-router'
import { useState } from 'react'
import { StyleSheet, Text, View } from 'react-native'
import { ApiError, messageOf } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { Button, ErrorMessage, Field, Screen } from '../components/ui'
import { colors, space } from '../theme'

export default function LoginScreen() {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // On success the session starts and the layout switches to the logged-in screens
  async function submit() {
    setSubmitting(true)
    setError(null)
    try {
      await login(email.trim(), password)
    } catch (e) {
      setError(e instanceof ApiError && e.status === 401 ? 'Wrong email or password.' : messageOf(e))
      setSubmitting(false)
    }
  }

  return (
    <Screen>
      <View style={styles.container}>
        <Text style={styles.brand}>AI COACH</Text>
        <Text style={styles.title}>Log in</Text>
        <Text style={styles.subtitle}>Your plan, adjusted every day to what you actually did.</Text>
        <View style={styles.form}>
          {error && <ErrorMessage>{error}</ErrorMessage>}
          <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" autoComplete="email"
            keyboardType="email-address" textContentType="emailAddress" returnKeyType="next" />
          <Field label="Password" value={password} onChangeText={setPassword} secureTextEntry
            autoComplete="current-password" textContentType="password" returnKeyType="go" onSubmitEditing={submit} />
          <Button title={submitting ? 'Logging in…' : 'Log in'} onPress={submit} busy={submitting}
            disabled={!email || !password} />
        </View>
        <Text style={styles.footer}>
          New here?{' '}
          <Link href="/register" style={styles.link}>Create an account</Link>
        </Text>
      </View>
    </Screen>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', gap: space.sm },
  brand: { color: colors.primary, fontWeight: '700', letterSpacing: 1, fontSize: 13 },
  title: { fontSize: 28, fontWeight: '700', color: colors.text },
  subtitle: { fontSize: 15, color: colors.textMuted },
  form: { marginTop: space.xl, gap: space.lg },
  footer: { marginTop: space.xl, textAlign: 'center', color: colors.textMuted },
  link: { color: colors.primary, fontWeight: '600' },
})

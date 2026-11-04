import { useState } from 'react'
import { StyleSheet, Text, View } from 'react-native'
import { ApiError, messageOf } from '../api/client'
import { deviceTimeZone } from '../auth/AuthProvider'
import { useAuth } from '../auth/useAuth'
import { Button, ErrorMessage, Field, Screen } from '../components/ui'
import { colors, space } from '../theme'

export default function RegisterScreen() {
  const { register } = useAuth()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  async function submit() {
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      await register(name.trim(), email.trim(), password)
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length > 0) {
        setFieldErrors(e.fieldErrors)
      } else {
        setError(messageOf(e))
      }
      setSubmitting(false)
    }
  }

  return (
    <Screen>
      <View style={styles.form}>
        {error && <ErrorMessage>{error}</ErrorMessage>}
        <Field label="Name" value={name} onChangeText={setName} autoComplete="name" textContentType="name"
          maxLength={100} error={fieldErrors.displayName} />
        <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" autoComplete="email"
          keyboardType="email-address" textContentType="emailAddress" error={fieldErrors.email} />
        <Field label="Password" hint="(at least 8 characters)" value={password} onChangeText={setPassword}
          secureTextEntry autoComplete="new-password" textContentType="newPassword" maxLength={72}
          error={fieldErrors.password} />
        <Text style={styles.note}>
          Your days start and end in <Text style={styles.strong}>{deviceTimeZone()}</Text>.
        </Text>
        <Button title={submitting ? 'Creating account…' : 'Create account'} onPress={submit} busy={submitting}
          disabled={!name || !email || password.length < 8} />
      </View>
    </Screen>
  )
}

const styles = StyleSheet.create({
  form: { gap: space.lg },
  note: { fontSize: 13, color: colors.textMuted },
  strong: { fontWeight: '600', color: colors.text },
})

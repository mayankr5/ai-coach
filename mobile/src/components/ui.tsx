import type { ReactNode } from 'react'
import {
  ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View,
  type TextInputProps,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { colors, radius, space } from '../theme'

type Variant = 'primary' | 'secondary'

export function Button({ title, onPress, variant = 'primary', disabled, busy }: {
  title: string; onPress: () => void; variant?: Variant; disabled?: boolean; busy?: boolean
}) {
  const primary = variant === 'primary'
  return (
    <Pressable accessibilityRole="button" accessibilityState={{ disabled: disabled || busy, busy }}
      disabled={disabled || busy} onPress={onPress}
      style={({ pressed }) => [styles.button, primary ? styles.primary : styles.secondary,
        pressed && (primary ? styles.primaryPressed : styles.secondaryPressed), (disabled || busy) && styles.disabled]}>
      {busy && <ActivityIndicator color={primary ? colors.onPrimary : colors.primary} />}
      <Text style={[styles.buttonText, { color: primary ? colors.onPrimary : colors.text }]}>{title}</Text>
    </Pressable>
  )
}

export function Field({ label, hint, error, ...props }: { label: string; hint?: string; error?: string } & TextInputProps) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>
        {label}{hint && <Text style={styles.hint}> {hint}</Text>}
      </Text>
      <TextInput accessibilityLabel={label} placeholderTextColor={colors.textFaint}
        style={[styles.input, error ? styles.inputError : null]} {...props} />
      {error && <Text style={styles.fieldError}>{error}</Text>}
    </View>
  )
}

export function ErrorMessage({ children }: { children: ReactNode }) {
  return <Text accessibilityRole="alert" style={styles.error}>{children}</Text>
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <View accessibilityRole="progressbar" accessibilityLabel={label} style={styles.spinner}>
      <ActivityIndicator color={colors.primary} />
      <Text style={styles.muted}>{label}…</Text>
    </View>
  )
}

/** A scrolling screen that keeps focused inputs above the keyboard. */
export function Screen({ children }: { children: ReactNode }) {
  return (
    <SafeAreaView style={styles.screen} edges={['top', 'bottom']}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">{children}</ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  screen: { flex: 1, backgroundColor: colors.background },
  content: { flexGrow: 1, padding: space.lg },
  button: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space.sm,
    minHeight: 48, paddingHorizontal: space.lg, borderRadius: radius.md,
  },
  primary: { backgroundColor: colors.primary },
  primaryPressed: { backgroundColor: colors.primaryPressed },
  secondary: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border },
  secondaryPressed: { backgroundColor: colors.background },
  disabled: { opacity: 0.6 },
  buttonText: { fontSize: 16, fontWeight: '600' },
  field: { gap: space.xs },
  label: { fontSize: 14, fontWeight: '500', color: colors.text },
  hint: { fontWeight: '400', color: colors.textFaint },
  input: {
    minHeight: 48, paddingHorizontal: space.md, borderRadius: radius.md, borderWidth: 1, borderColor: colors.border,
    backgroundColor: colors.surface, fontSize: 16, color: colors.text,
  },
  inputError: { borderColor: colors.danger },
  fieldError: { fontSize: 13, color: colors.danger },
  error: {
    padding: space.md, borderRadius: radius.md, backgroundColor: colors.dangerBackground, color: colors.danger,
    fontSize: 14,
  },
  spinner: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space.sm, padding: space.xxl },
  muted: { color: colors.textMuted, fontSize: 14 },
})

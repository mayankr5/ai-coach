import * as Haptics from 'expo-haptics'
import { Platform } from 'react-native'

// A light tick for choices and a success buzz for finished actions; browsers (web preview) have neither
const enabled = Platform.OS !== 'web'

export const haptics = {
  select: () => enabled && void Haptics.selectionAsync().catch(() => undefined),
  success: () => enabled
    && void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => undefined),
}

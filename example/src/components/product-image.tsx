import Ionicons from '@expo/vector-icons/Ionicons';
import { Image, type ImageProps } from 'expo-image';
import { useState } from 'react';
import { View } from 'react-native';

import { useColors } from 'src/utils/colors';

interface Props {
  source: ImageProps['source'];
  /** Stable per-product identity so expo-image resets cleanly as FlashList recycles cells. */
  recyclingKey: string;
  transition?: number;
}

/**
 * Product photo that is guaranteed to load safely regardless of the source image:
 *
 * - `allowDownscaling` lets expo-image downsample the decoded bitmap to the
 *   container size, so even an oversized source can't OOM (a full-res 2560px photo
 *   would otherwise decode to a ~26MB bitmap and crash low-memory Android devices).
 * - `recyclingKey` keeps peak memory low in recycled lists — cell reuse resets the
 *   view instead of leaving stale decodes stacked.
 * - `onError` falls back to a neutral placeholder so a single failed image degrades
 *   to an icon rather than a blank card.
 *
 * Fill a sized parent (`aspectRatio` or fixed width/height) so downscaling has a
 * target to scale to.
 */
export default function ProductImage({ source, recyclingKey, transition = 200 }: Props) {
  const colors = useColors();
  // Track the key we failed on rather than a bare boolean, so recycling to a new
  // product automatically clears the failed state without an effect.
  const [failedKey, setFailedKey] = useState<string | null>(null);

  if (failedKey === recyclingKey) {
    return (
      <View className="h-full w-full items-center justify-center bg-surface-2">
        <Ionicons name="fast-food-outline" size={28} color={colors.muted} />
      </View>
    );
  }

  return (
    <Image
      source={source}
      style={{ width: '100%', height: '100%' }}
      contentFit="cover"
      transition={transition}
      recyclingKey={recyclingKey}
      allowDownscaling
      onError={() => setFailedKey(recyclingKey)}
    />
  );
}

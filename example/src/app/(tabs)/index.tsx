import { FlashList } from '@shopify/flash-list';
import { useRouter } from 'expo-router';
import { useCallback, useMemo, useState } from 'react';
import { ScrollView, useWindowDimensions, View } from 'react-native';

import CategoryChip from 'src/components/category-chip';
import FloatingCartBar from 'src/components/floating-cart-bar';
import ProductCard from 'src/components/product-card';
import ScreenHeader from 'src/components/screen-header';
import { useCart } from 'src/providers/cart';
import { categories, products } from 'src/data/products';
import type { Category, Product } from 'src/types';

type Filter = 'All' | Category;
const FILTERS: Filter[] = ['All', ...categories];

export default function Shop() {
  const { items, addToCart } = useCart();
  const router = useRouter();
  const { width, height } = useWindowDimensions();
  const [filter, setFilter] = useState<Filter>('All');

  // Scale card size with the physical screen so bigger devices (kiosks) show FEWER,
  // LARGER items — which means fewer image bitmaps decoded at once, the dominant
  // memory cost. We derive columns from a target card width rather than hardcoding
  // breakpoints: `max(w,h)` is rotation-invariant so the card SIZE stays stable when
  // the device rotates, while the column COUNT reacts to the live width.
  // Rough result: phone → 2, tablet → 3, kiosk → 3 (taller cards ⇒ fewer visible rows).
  const numberOfColumns = useMemo(() => {
    const targetCardWidth = Math.min(420, Math.max(190, Math.max(width, height) * 0.19));
    return Math.max(2, Math.round(width / targetCardWidth));
  }, [width, height]);

  // Large kiosk displays have a huge viewport, so FlashList mounts many image
  // cells at once and — with an unbounded recycle pool — retains every scrolled-off
  // cell (each holding a live bitmap). Peak memory then scales with screen size and
  // OOMs on memory-constrained kiosks. Bounding the pool to a few rows' worth keeps
  // the count of live images flat everywhere; it scales with columns so phones and
  // tablets (2–3 cols) are effectively unchanged.
  const recyclePoolSize = useMemo(() => numberOfColumns * 3, [numberOfColumns]);

  const data = useMemo(() => (filter === 'All' ? products : products.filter((p) => p.category === filter)), [filter]);

  const { count, total } = useMemo(
    () => ({
      count: items.reduce((sum, item) => sum + item.quantity, 0),
      total: items.reduce((sum, item) => sum + item.product.price * item.quantity, 0),
    }),
    [items]
  );

  const onAddToCart = useCallback((product: Product) => addToCart(product), [addToCart]);

  return (
    <View className="flex-1 bg-background">
      <ScreenHeader title="Menu" />
      <View>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 12, gap: 8 }}
        >
          {FILTERS.map((option) => (
            <CategoryChip key={option} label={option} selected={filter === option} onPress={() => setFilter(option)} />
          ))}
        </ScrollView>
      </View>

      <FlashList
        key={numberOfColumns}
        numColumns={numberOfColumns}
        data={data}
        keyExtractor={(item) => String(item.id)}
        drawDistance={150}
        maxItemsInRecyclePool={recyclePoolSize}
        contentContainerStyle={{ paddingHorizontal: 12, paddingTop: 4, paddingBottom: 96 }}
        renderItem={({ item, index }) => <ProductCard product={item} index={index} onAdd={() => onAddToCart(item)} />}
      />

      <FloatingCartBar count={count} total={total} onPress={() => router.navigate('/checkout')} />
    </View>
  );
}

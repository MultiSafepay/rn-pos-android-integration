import { NativeTabs } from 'expo-router/unstable-native-tabs';
import { useMemo } from 'react';

import { useCart } from 'src/providers/cart';
import { brand } from 'src/utils/colors';

export default function TabLayout() {
  const { items } = useCart();

  const numberOfItems = useMemo(() => items.reduce((total, item) => total + item.quantity, 0), [items]);

  return (
    <NativeTabs tintColor={brand}>
      <NativeTabs.Trigger name="index">
        <NativeTabs.Trigger.Icon sf="bag.fill" md="storefront" />
        <NativeTabs.Trigger.Label>Shop</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="checkout">
        <NativeTabs.Trigger.Icon sf="cart.fill" md="shopping_cart" />
        <NativeTabs.Trigger.Label>Cart</NativeTabs.Trigger.Label>
        {numberOfItems > 0 ? <NativeTabs.Trigger.Badge>{String(numberOfItems)}</NativeTabs.Trigger.Badge> : null}
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="settings">
        <NativeTabs.Trigger.Icon sf="gearshape.fill" md="settings" />
        <NativeTabs.Trigger.Label>Settings</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
    </NativeTabs>
  );
}

import IonIcons from '@expo/vector-icons/Ionicons';
import { Tabs } from 'expo-router';
import { useMemo } from 'react';
import { useCart } from 'src/providers/cart';
import Colors from 'src/utils/colors';

export default function TabLayout() {
  const { items } = useCart();

  const numberOfItems = useMemo(() => {
    return items.reduce((total, item) => total + item.quantity, 0);
  }, [items]);

  return (
    <Tabs
      initialRouteName="index"
      screenOptions={{
        headerTintColor: Colors.primary,
        headerTitleStyle: {
          fontWeight: 'bold',
          fontSize: 24,
        },
        tabBarActiveTintColor: Colors.primary,
        tabBarBadgeStyle: {
          backgroundColor: Colors.primary,
        },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Shop',
          tabBarIcon: ({ color }) => <IonIcons size={28} name="storefront-outline" color={color} />,
        }}
      />
      <Tabs.Screen
        name="checkout"
        options={{
          title: 'Checkout',
          tabBarIcon: ({ color }) => <IonIcons size={28} name="cart-outline" color={color} />,
          tabBarBadge: items.length > 0 ? numberOfItems : undefined,
        }}
      />
      <Tabs.Screen
        name="settings"
        options={{
          title: 'Settings',
          tabBarIcon: ({ color }) => <IonIcons size={28} name="settings-outline" color={color} />,
        }}
      />
    </Tabs>
  );
}

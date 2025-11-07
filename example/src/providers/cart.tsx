import { createContext, useState, FC, PropsWithChildren, useContext, useCallback } from 'react';
import { CartItem, Product } from 'src/types';

// Define the type for the cart context
interface CartContextType {
  items: CartItem[];
  addToCart: (product: Product) => void;
  removeFromCart: (product: Product) => void;
  clearCart: () => void;
  transactionId?: string;
  setTransactionId: (transactionId: string | undefined) => void;
}

// Create the cart context
export const CartContext = createContext<CartContextType>({
  items: [],
  addToCart: (_: Product) => {},
  removeFromCart: (_: Product) => {},
  clearCart: () => {},
  transactionId: undefined,
  setTransactionId: () => {},
});

// Create the CartProvider component
const CartProvider: FC<PropsWithChildren> = ({ children }) => {
  const [items, setItems] = useState<CartItem[]>([]);
  const [transactionId, setTransactionIdState] = useState<string | undefined>(undefined);

  const addToCart = useCallback((product: Product) => {
    setItems((prevItems) => {
      const existingProductIndex = prevItems.findIndex((item) => item.product.id === product.id);

      if (existingProductIndex >= 0) {
        // If the product already exists in the cart, increase its quantity
        const updatedProducts = [...prevItems];
        updatedProducts[existingProductIndex].quantity += 1;
        return updatedProducts;
      } else {
        // If the product does not exist in the cart, add it with quantity 1
        return [...prevItems, { product, quantity: 1 }];
      }
    });
  }, []);

  const removeFromCart = useCallback((product: Product) => {
    setItems((prevItems) => {
      const existingProductIndex = prevItems.findIndex((item) => item.product.id === product.id);

      if (existingProductIndex >= 0) {
        // If the product already exists in the cart, decrease its quantity
        const updatedProducts = [...prevItems];
        updatedProducts[existingProductIndex].quantity -= 1;

        // If quantity becomes zero, remove the product from the cart
        if (updatedProducts[existingProductIndex].quantity <= 0) {
          updatedProducts.splice(existingProductIndex, 1);
        }

        return updatedProducts;
      } else {
        // If the product does not exist in the cart, do nothing
        return prevItems;
      }
    });
  }, []);

  const clearCart = useCallback(() => {
    setItems([]);
    setTransactionIdState(undefined);
  }, []);

  const setTransactionId = useCallback((value: string | undefined) => {
    setTransactionIdState(value);
  }, []);

  return (
    <CartContext.Provider value={{ items, addToCart, removeFromCart, clearCart, transactionId, setTransactionId }}>
      {children}
    </CartContext.Provider>
  );
};

export default CartProvider;

export const useCart = () => {
  return useContext(CartContext);
};

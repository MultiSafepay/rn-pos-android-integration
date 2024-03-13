import {
  createContext,
  useState,
  FC,
  PropsWithChildren,
  useContext,
} from "react";
import { Product } from "src/types";

// Define the type for the cart context
interface CartContextType {
  products: Product[];
  addToCart: (product: Product) => void;
}

// Create the cart context
export const CartContext = createContext<CartContextType>({
  products: [],
  addToCart: (_: Product) => {},
});

// Create the CartProvider component
const CartProvider: FC<PropsWithChildren> = ({ children }) => {
  const [products, setProducts] = useState<Product[]>([]);

  // Function to add a product to the cart
  const addToCart = (product: Product) => {
    setProducts((prevProducts) => [...prevProducts, product]);
  };

  return (
    <CartContext.Provider value={{ products, addToCart }}>
      {children}
    </CartContext.Provider>
  );
};

export default CartProvider;

export const useCart = () => {
  return useContext(CartContext);
};

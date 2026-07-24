// Consumers only need `.format(value)`, so a plain object is a drop-in for a real
// Intl.NumberFormat when we have to fall back.
type CurrencyFormatter = Pick<Intl.NumberFormat, 'format'>;

const CURRENCY = 'EUR';
const language = 'en';

let currencyFormatter: CurrencyFormatter | undefined;
let langCurrency: string | undefined;

// Manual EUR formatter for engines where Intl currency formatting is missing or
// throws — notably Hermes on Android 7 (API 24), where Intl is backed by
// android.icu right at its minimum API level and has proven unreliable. Produces
// the same shape as Intl for `en` (e.g. "€1,234.50").
const createFallbackFormatter = (): CurrencyFormatter => ({
  format: (value: number) => {
    const [whole, decimals] = Math.abs(value).toFixed(2).split('.');
    const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    return `${value < 0 ? '-' : ''}€${grouped}.${decimals}`;
  },
});

const createCurrencyFormatter = (locale: string): CurrencyFormatter => {
  try {
    const formatter = new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: CURRENCY,
      minimumFractionDigits: 2,
    });
    // Some engines construct fine but throw when formatting — probe once so the
    // failure surfaces here (inside the try) rather than mid-render.
    formatter.format(1);
    return formatter;
  } catch {
    return createFallbackFormatter();
  }
};

export const getCurrentCurrencyFormatter = (): CurrencyFormatter =>
  currencyFormatter ?? (currencyFormatter = createCurrencyFormatter(language));

export const useCurrencyFormatter = (): CurrencyFormatter => {
  if (!currencyFormatter || langCurrency !== language) {
    langCurrency = language;
    currencyFormatter = createCurrencyFormatter(language);
  }
  return currencyFormatter;
};
